package untrusted.vm.rom

import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.file.Files

class SparseImageTest {
    private lateinit var tmp: File
    @Before fun setUp() { tmp = Files.createTempDirectory("sp").toFile() }
    @After fun tearDown() { SafePaths.deleteTree(tmp) }

    @Test fun decodeRoundTripsARealImage() {
        val raw = TestData.resource("tiny.ext4", tmp)
        val sparse = File(tmp, "tiny.simg").also { TestData.toSparse(raw, it) }
        assertTrue(sparse.length() < raw.length())  // zero blocks were dropped
        val out = File(tmp, "decoded.raw")
        val size = sparse.inputStream().use { SparseImage.decode(it, out, 1L shl 30) }
        assertEquals(raw.length(), size)
        assertTrue("decoded image differs", raw.readBytes().contentEquals(out.readBytes()))
    }

    @Test fun copyRawKeepsContentAndLength() {
        val raw = TestData.resource("tiny.ext4", tmp)
        val out = File(tmp, "copy.raw")
        val size = raw.inputStream().use { SparseImage.copyRaw(it, out, 1L shl 30) }
        assertEquals(raw.length(), size)
        assertTrue(raw.readBytes().contentEquals(out.readBytes()))
    }

    @Test fun fillChunkIsExpanded() {
        // 1 block of 4096 filled with 0xAB 0xCD 0xAB 0xCD...
        fun le32(v: Long) = byteArrayOf(v.toByte(), (v shr 8).toByte(), (v shr 16).toByte(), (v shr 24).toByte())
        val bos = java.io.ByteArrayOutputStream()
        bos.write(byteArrayOf(0x3A, 0xFF.toByte(), 0x26, 0xED.toByte(), 1, 0, 0, 0, 28, 0, 12, 0))
        bos.write(le32(4096)); bos.write(le32(1)); bos.write(le32(1)); bos.write(le32(0))
        bos.write(byteArrayOf(0xC2.toByte(), 0xCA.toByte(), 0, 0)); bos.write(le32(1)); bos.write(le32(16))
        bos.write(byteArrayOf(0xAB.toByte(), 0xCD.toByte(), 0xAB.toByte(), 0xCD.toByte()))
        val out = File(tmp, "fill.raw")
        SparseImage.decode(ByteArrayInputStream(bos.toByteArray()), out, 1L shl 20)
        val b = out.readBytes()
        assertEquals(4096, b.size)
        assertEquals(0xAB.toByte(), b[0]); assertEquals(0xCD.toByte(), b[1]); assertEquals(0xCD.toByte(), b[4095])
    }

    @Test fun rejectsTruncatedAndOversizedAndGarbage() {
        val raw = TestData.resource("tiny.ext4", tmp)
        val sparse = File(tmp, "t.simg").also { TestData.toSparse(raw, it) }
        val bytes = sparse.readBytes()
        try { SparseImage.decode(ByteArrayInputStream(bytes.copyOf(bytes.size / 2)), File(tmp, "a.raw"), 1L shl 30); fail() }
        catch (e: java.io.IOException) {}
        try { SparseImage.decode(ByteArrayInputStream(bytes), File(tmp, "b.raw"), 1000); fail() }
        catch (e: SparseImage.SparseFormatException) { assertTrue(e.message!!.contains("larger")) }
        try { SparseImage.decode(ByteArrayInputStream(ByteArray(64)), File(tmp, "c.raw"), 1L shl 30); fail() }
        catch (e: SparseImage.SparseFormatException) {}
    }

    @Test fun cancellationStopsDecoding() {
        val raw = TestData.resource("tiny.ext4", tmp)
        val sparse = File(tmp, "t.simg").also { TestData.toSparse(raw, it) }
        try { sparse.inputStream().use { SparseImage.decode(it, File(tmp, "x.raw"), 1L shl 30) { true } }; fail() }
        catch (e: SparseImage.CancelledException) {}
    }
}
