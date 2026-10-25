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
 * Not supported yet: raw .img files (they're ext4/sparse images and can't be mounted
 * without root), and symlinks inside the archive (java.util.zip doesn't expose them).
 */
object RomExtractor {

    sealed class Outcome {
        data class Ok(val files: Int, val bytes: Long, val strippedPrefix: String) : Outcome()
        data class Fail(val reason: String) : Outcome()
    }

    const val DEFAULT_MAX_BYTES = 12L * 1024 * 1024 * 1024
    const val DEFAULT_MAX_ENTRIES = 300_000

    fun extract(
        archive: File,
        destDir: File,
        maxBytes: Long = DEFAULT_MAX_BYTES,
        maxEntries: Int = DEFAULT_MAX_ENTRIES,
        onProgress: (Float) -> Unit = {}
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
