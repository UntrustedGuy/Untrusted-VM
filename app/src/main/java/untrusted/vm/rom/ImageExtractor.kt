package untrusted.vm.rom

import org.tukaani.xz.XZInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.io.RandomAccessFile

/**
 * ROM images -> file tree. Pipeline:  [xz] -> [Android sparse | raw] -> temporary raw ext4
 * (holes kept, so it only uses disk for real data) -> unprivileged ext4 walk into destDir.
 * Roughly 40% of progress is decoding, 60% is extracting.
 */
internal object ImageExtractor {

    private const val DECODE_SHARE = 0.4f
    private const val XZ_MEMORY_LIMIT_KIB = 256 * 1024
    const val MAX_RAW_BYTES = 16L * 1024 * 1024 * 1024

    private class ProgressStream(
        input: InputStream,
        private val total: Long,
        private val report: (Float) -> Unit
    ) : FilterInputStream(input) {
        private var count = 0L
        private var last = 0L
        private fun tick(n: Int) {
            if (n <= 0) return
            count += n
            val now = System.nanoTime()
            if (now - last > 250_000_000L) {
                last = now
                report((count.toFloat() / total).coerceIn(0f, 1f))
            }
        }
        override fun read(): Int { val b = super.read(); if (b >= 0) tick(1); return b }
        override fun read(b: ByteArray, off: Int, len: Int): Int { val n = super.read(b, off, len); tick(n); return n }
    }

    fun extract(
        archive: File,
        destDir: File,
        tmpDir: File,
        kind: RomExtractor.Kind,
        ops: FsOps,
        maxBytes: Long,
        maxEntries: Int,
        isCancelled: () -> Boolean,
        onProgress: (Float) -> Unit
    ): RomExtractor.Outcome {
        tmpDir.mkdirs()
        val raw = File(tmpDir, "image-${System.nanoTime()}.raw")
        try {
            val total = maxOf(1L, archive.length())
            FileInputStream(archive).use { fileIn ->
                val counted = ProgressStream(BufferedInputStream(fileIn, 1 shl 16), total) { onProgress(it * DECODE_SHARE) }
                val decoded: InputStream =
                    if (kind == RomExtractor.Kind.XZ) XZInputStream(counted, XZ_MEMORY_LIMIT_KIB) else counted
                val buffered = BufferedInputStream(decoded, 1 shl 20)
                buffered.mark(16)
                val head = ByteArray(4)
                var got = 0
                while (got < 4) {
                    val n = buffered.read(head, got, 4 - got)
                    if (n < 0) break
                    got += n
                }
                buffered.reset()
                if (got == 4 && SparseImage.hasMagic(head)) {
                    SparseImage.decode(buffered, raw, MAX_RAW_BYTES, isCancelled)
                } else {
                    SparseImage.copyRaw(buffered, raw, MAX_RAW_BYTES, isCancelled)
                }
            }
            onProgress(DECODE_SHARE)

            val stats = RandomAccessFile(raw, "r").use { r ->
                Ext4Reader(r).extractTo(destDir, ops, maxBytes, maxEntries, isCancelled) {
                    onProgress(DECODE_SHARE + (1f - DECODE_SHARE) * it)
                }
            }
            raw.delete()

            normaliseLayout(destDir)?.let { return RomExtractor.Outcome.Fail(it) }
            return RomExtractor.Outcome.Ok(stats.files + stats.symlinks, stats.bytes, "")
        } catch (e: SparseImage.CancelledException) {
            return RomExtractor.Outcome.Cancelled
        } catch (e: Ext4Reader.CancelledException) {
            return RomExtractor.Outcome.Cancelled
        } catch (e: IOException) {
            val msg = e.message ?: e.javaClass.simpleName
            return RomExtractor.Outcome.Fail(
                if (msg.contains("No space left", ignoreCase = true)) {
                    "Not enough free storage. Extracting an image needs roughly 3-5x its download size."
                } else {
                    "Could not read the ROM image: $msg"
                }
            )
        } finally {
            raw.delete()
        }
    }

    /** system-as-root images already have system/. Plain /system images get wrapped into it. Returns an error or null. */
    private fun normaliseLayout(root: File): String? {
        val system = File(root, "system")
        if (system.isDirectory) return null
        val looksLikeSystem = File(root, "build.prop").isFile || File(root, "bin").isDirectory
        if (!looksLikeSystem) return "No Android system found in the image (no system/ directory)."
        val children = root.listFiles() ?: return "Could not read the extracted image."
        if (!system.mkdir()) return "Could not arrange the extracted image."
        for (c in children) if (!c.renameTo(File(system, c.name))) return "Could not arrange the extracted image."
        return null
    }
}
