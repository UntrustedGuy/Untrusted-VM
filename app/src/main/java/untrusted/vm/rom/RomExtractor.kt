package untrusted.vm.rom

import java.io.File
import java.io.FileOutputStream
import java.util.Collections
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

/**
 * Extracts a ROM archive (a zip of the file tree: system/, vendor/, ...) into [destDir].
 *
 * Hardened against hostile archives: path traversal ("zip-slip"), absolute paths, symlink
 * escapes, entry-count bombs and decompression bombs (the byte cap counts bytes actually
 * written, not what the zip header claims).
 *
 * Zips can't carry symlinks here (java.util.zip doesn't expose them); ROMs that need them
 * ship as ext4 images, handled by [extractAny] / ImageExtractor.
 */
object RomExtractor {

    sealed class Outcome {
        data class Ok(val files: Int, val bytes: Long, val strippedPrefix: String) : Outcome()
        data class Fail(val reason: String) : Outcome()
        object Cancelled : Outcome()
    }

    enum class Kind { ZIP, XZ, SPARSE, EXT4, UNKNOWN }

    /** Identify a ROM file by its magic bytes (file names lie; this doesn't). */
    fun sniff(file: File): Kind {
        java.io.RandomAccessFile(file, "r").use { r ->
            val head = ByteArray(6)
            val n = r.read(head)
            if (n >= 4 && head[0] == 0x50.toByte() && head[1] == 0x4B.toByte() &&
                (head[2] == 3.toByte() || head[2] == 5.toByte())) return Kind.ZIP
            if (n >= 6 && head[0] == 0xFD.toByte() && head[1] == 0x37.toByte() && head[2] == 0x7A.toByte() &&
                head[3] == 0x58.toByte() && head[4] == 0x5A.toByte() && head[5] == 0.toByte()) return Kind.XZ
            if (n >= 4 && SparseImage.hasMagic(head)) return Kind.SPARSE
            if (r.length() > 1082) {
                val m = ByteArray(2)
                r.seek(1080)
                r.readFully(m)
                if (m[0] == 0x53.toByte() && m[1] == 0xEF.toByte()) return Kind.EXT4
            }
        }
        return Kind.UNKNOWN
    }

    /**
     * Extracts any supported ROM container: a zip of the file tree, a raw or sparse ext4 image,
     * or either of those xz-compressed (e.g. a GSI ".img.xz"). Image extraction uses [tmpDir]
     * for a temporary raw image.
     */
    fun extractAny(
        archive: File,
        destDir: File,
        tmpDir: File,
        ops: FsOps,
        maxBytes: Long = DEFAULT_MAX_BYTES,
        maxEntries: Int = DEFAULT_MAX_ENTRIES,
        isCancelled: () -> Boolean = { false },
        onProgress: (Float) -> Unit = {}
    ): Outcome {
        val kind = try { sniff(archive) } catch (e: Exception) {
            return Outcome.Fail("cannot read the ROM file: ${e.message}")
        }
        return when (kind) {
            Kind.ZIP -> extract(archive, destDir, maxBytes, maxEntries, onProgress, isCancelled)
            Kind.XZ, Kind.SPARSE, Kind.EXT4 ->
                ImageExtractor.extract(archive, destDir, tmpDir, kind, ops, maxBytes, maxEntries, isCancelled, onProgress)
            Kind.UNKNOWN -> Outcome.Fail("Unrecognised ROM format. Expected a .zip, .img or .img.xz file.")
        }
    }

    const val DEFAULT_MAX_BYTES = 12L * 1024 * 1024 * 1024
    const val DEFAULT_MAX_ENTRIES = 300_000

    fun extract(
        archive: File,
        destDir: File,
        maxBytes: Long = DEFAULT_MAX_BYTES,
        maxEntries: Int = DEFAULT_MAX_ENTRIES,
        onProgress: (Float) -> Unit = {},
        isCancelled: () -> Boolean = { false }
    ): Outcome {
        val zip = try {
            ZipFile(archive)
        } catch (e: Exception) {
            return Outcome.Fail("not a readable zip archive: ${e.message}")
        }
        zip.use { z ->
            val raw: List<ZipEntry> = Collections.list(z.entries())
            if (raw.size > maxEntries) return Outcome.Fail("archive has too many entries (${raw.size})")

            val named = ArrayList<Pair<ZipEntry, String>>(raw.size)
            for (e in raw) {
                val n = SafePaths.normalizeEntryName(e.name)
                    ?: return Outcome.Fail("unsafe path in archive: ${e.name}")
                if (n.isNotEmpty()) named.add(e to n)
            }

            val prefix = detectPrefix(named.map { it.second })
                ?: return Outcome.Fail(layoutProblem(named.map { it.second }))

            val declared = named.filter { !it.first.isDirectory }.sumOf { maxOf(0L, it.first.size) }
            if (declared > maxBytes) return Outcome.Fail("ROM is larger than the ${maxBytes / (1024 * 1024)} MB limit")

            destDir.mkdirs()
            var written = 0L
            var files = 0
            for ((entry, full) in named) {
                if (isCancelled()) return Outcome.Cancelled
                val rel = when {
                    prefix.isEmpty() -> full
                    full == prefix.trimEnd('/') -> continue // the wrapper directory itself
                    full.startsWith(prefix) -> full.substring(prefix.length)
                    else -> continue
                }
                val dest = SafePaths.resolveInside(destDir, rel)
                    ?: return Outcome.Fail("path escapes the ROM folder: ${entry.name}")

                if (entry.isDirectory) {
                    dest.mkdirs()
                    continue
                }
                dest.parentFile?.mkdirs()
                z.getInputStream(entry).use { input ->
                    FileOutputStream(dest).use { out ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            written += n
                            if (written > maxBytes) return Outcome.Fail("ROM expands beyond the size limit (possible zip bomb)")
                            out.write(buf, 0, n)
                        }
                    }
                }
                if (isExecutableLocation(rel)) dest.setExecutable(true, false)
                files++
                if (declared > 0) onProgress((written.toFloat() / declared).coerceIn(0f, 1f))
            }
            onProgress(1f)
            return Outcome.Ok(files, written, prefix)
        }
    }

    /** "" if system/ is at the top, "<dir>/" if the whole ROM sits in one wrapper dir, null if neither. */
    private fun detectPrefix(names: List<String>): String? {
        fun hasSystem(p: String) = names.any { it == "${p}system" || it.startsWith("${p}system/") }
        if (hasSystem("")) return ""
        val tops = names.map { it.substringBefore('/') }.toSet()
        if (tops.size == 1) {
            val p = tops.first() + "/"
            if (hasSystem(p)) return p
        }
        return null
    }

    private fun layoutProblem(names: List<String>): String =
        if (names.any { it.endsWith(".img", ignoreCase = true) })
            "This looks like an image-based ROM (.img). Images can't be mounted without root. " +
                "Provide the ROM as a zip of its file tree (system/, vendor/, ...)."
        else
            "No system/ directory found at the top of the archive (or inside a single wrapper folder)."

    private fun isExecutableLocation(rel: String): Boolean =
        rel.split('/').dropLast(1).any { it == "bin" || it == "xbin" }
}
