package untrusted.vm.rom

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.RandomAccessFile

/**
 * Android sparse image ("simg") support, plus a hole-preserving raw copy.
 * Both write into a RandomAccessFile and leave all-zero regions unwritten, so the temporary
 * raw image only occupies the disk space of its real data.
 */
object SparseImage {

    class SparseFormatException(message: String) : IOException(message)

    private const val CHUNK_RAW = 0xCAC1
    private const val CHUNK_FILL = 0xCAC2
    private const val CHUNK_DONT_CARE = 0xCAC3
    private const val CHUNK_CRC32 = 0xCAC4

    @JvmStatic
    fun hasMagic(b: ByteArray): Boolean =
        b.size >= 4 && b[0] == 0x3A.toByte() && b[1] == 0xFF.toByte() && b[2] == 0x26.toByte() && b[3] == 0xED.toByte()

    private fun le16(b: ByteArray, o: Int) = (b[o].toInt() and 0xFF) or ((b[o + 1].toInt() and 0xFF) shl 8)
    private fun le32(b: ByteArray, o: Int): Long =
        (le16(b, o).toLong()) or (le16(b, o + 2).toLong() shl 16)

    private fun readFully(input: InputStream, buf: ByteArray, len: Int = buf.size) {
        var off = 0
        while (off < len) {
            val n = input.read(buf, off, len - off)
            if (n < 0) throw SparseFormatException("sparse image is truncated")
            off += n
        }
    }

    private fun skipFully(input: InputStream, count: Long) {
        var left = count
        val scratch = ByteArray(8192)
        while (left > 0) {
            val n = input.read(scratch, 0, minOf(left, scratch.size.toLong()).toInt())
            if (n < 0) throw SparseFormatException("sparse image is truncated")
            left -= n
        }
    }

    /** Streams [length] bytes from [input] into [raf] at its current position, skipping all-zero buffers. */
    private fun copyKeepingHoles(input: InputStream, raf: RandomAccessFile, length: Long, buf: ByteArray, isCancelled: () -> Boolean) {
        var left = length
        while (left > 0) {
            if (isCancelled()) throw CancelledException()
            val n = input.read(buf, 0, minOf(left, buf.size.toLong()).toInt())
            if (n < 0) throw SparseFormatException("image data is truncated")
            var zero = true
            for (i in 0 until n) if (buf[i].toInt() != 0) { zero = false; break }
            if (zero) raf.seek(raf.filePointer + n) else raf.write(buf, 0, n)
            left -= n
        }
    }

    class CancelledException : IOException("cancelled")

    /** Plain (non-sparse) image: copy while keeping holes. Returns the image size. */
    @JvmStatic
    fun copyRaw(input: InputStream, dest: File, maxBytes: Long, isCancelled: () -> Boolean = { false }): Long {
        RandomAccessFile(dest, "rw").use { raf ->
            raf.setLength(0)
            val buf = ByteArray(1 shl 20)
            var total = 0L
            while (true) {
                if (isCancelled()) throw CancelledException()
                val n = input.read(buf)
                if (n < 0) break
                total += n
                if (total > maxBytes) throw SparseFormatException("image is larger than the allowed limit")
                var zero = true
                for (i in 0 until n) if (buf[i].toInt() != 0) { zero = false; break }
                if (zero) raf.seek(raf.filePointer + n) else raf.write(buf, 0, n)
            }
            raf.setLength(total)
            return total
        }
    }

    /** Decodes an Android sparse image stream into a raw image file. Returns the raw size. */
    @JvmStatic
    fun decode(input: InputStream, dest: File, maxRawBytes: Long, isCancelled: () -> Boolean = { false }): Long {
        val header = ByteArray(28)
        readFully(input, header)
        if (!hasMagic(header)) throw SparseFormatException("not a sparse image")
        val major = le16(header, 4)
        val fileHdr = le16(header, 8)
        val chunkHdr = le16(header, 10)
        val blk = le32(header, 12)
        val totalBlks = le32(header, 16)
        val totalChunks = le32(header, 20)
        if (major != 1 || fileHdr < 28 || chunkHdr < 12) throw SparseFormatException("unsupported sparse image version")
        if (blk < 512 || blk > 65536 || blk % 4 != 0L) throw SparseFormatException("bad sparse block size")
        val rawSize = totalBlks * blk
        if (rawSize > maxRawBytes) throw SparseFormatException("image is larger than the allowed limit")
        skipFully(input, (fileHdr - 28).toLong())

        RandomAccessFile(dest, "rw").use { raf ->
            raf.setLength(0)
            val buf = ByteArray(1 shl 20)
            val chdr = ByteArray(chunkHdr)
            var block = 0L
            for (c in 0 until totalChunks) {
                if (isCancelled()) throw CancelledException()
                readFully(input, chdr)
                val type = le16(chdr, 0)
                val sz = le32(chdr, 4)
                val totalSz = le32(chdr, 8)
                if (block + sz > totalBlks) throw SparseFormatException("sparse chunk runs past the end of the image")
                val bytes = sz * blk
                raf.seek(block * blk)
                when (type) {
                    CHUNK_RAW -> {
                        if (totalSz != chunkHdr + bytes) throw SparseFormatException("corrupt RAW chunk")
                        copyKeepingHoles(input, raf, bytes, buf, isCancelled)
                    }
                    CHUNK_FILL -> {
                        val pat = ByteArray(4)
                        readFully(input, pat)
                        if (pat.any { it.toInt() != 0 }) {
                            val fillBuf = ByteArray(minOf(bytes, (1 shl 20).toLong()).toInt() / 4 * 4)
                            for (i in fillBuf.indices) fillBuf[i] = pat[i % 4]
                            var left = bytes
                            while (left > 0) {
                                val n = minOf(left, fillBuf.size.toLong()).toInt()
                                raf.write(fillBuf, 0, n)
                                left -= n
                            }
                        }
                    }
                    CHUNK_DONT_CARE -> Unit
                    CHUNK_CRC32 -> skipFully(input, 4)
                    else -> throw SparseFormatException("unknown sparse chunk type 0x${type.toString(16)}")
                }
                block += sz
            }
            raf.setLength(rawSize)
        }
        return rawSize
    }
}
