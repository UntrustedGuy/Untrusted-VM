package untrusted.vm.rom

import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.RandomAccessFile
import java.nio.file.Files

class Ext4ReaderTest {
    private lateinit var tmp: File
    @Before fun setUp() { tmp = Files.createTempDirectory("e4").toFile() }
    @After fun tearDown() { SafePaths.deleteTree(tmp) }

    private fun extract(img: File, out: File, maxBytes: Long = 1L shl 30): Ext4Reader.Stats =
        RandomAccessFile(img, "r").use { Ext4Reader(it).extractTo(out, JvmFsOps, maxBytes, 100_000) }

    @Test fun extractsExactlyTheSourceTree() {
        val img = TestData.resource("tiny.ext4", tmp)
        val out = File(tmp, "out")
        val stats = extract(img, out)
        val problems = Manifest(TestData.resourceText("tiny.expected")).verify(out)
        assertTrue("tree differs from source: $problems", problems.isEmpty())
        assertEquals(2, stats.skipped)   // fifo + character device
        assertEquals(2, stats.symlinks)  // one fast symlink, one slow (> 60 byte) symlink
    }

    @Test fun preservesFastAndSlowSymlinkTargets() {
        val out = File(tmp, "out")
        extract(TestData.resource("tiny.ext4", tmp), out)
        assertEquals("sh", JvmFsOps.readLink(File(out, "system/bin/toolbox")))
        val slow = JvmFsOps.readLink(File(out, "system/etc/long_link"))!!
        assertTrue(slow.length > 60 && slow.startsWith("/system/etc/xxxx"))
    }

    @Test fun handlesHolesAndMultiBlockDirectories() {
        val out = File(tmp, "out")
        extract(TestData.resource("tiny.ext4", tmp), out)
        val hole = File(out, "system/lib/libhole.so")
        assertEquals(200_000L, hole.length())
        val bytes = hole.readBytes()
        assertTrue("hole must read back as zeros", (3000 until 199_992).all { bytes[it].toInt() == 0 })
        assertEquals("TAILDATA", String(bytes, 199_992, 8))
        assertEquals(220, File(out, "system/many").list()!!.size)
    }

    @Test fun enforcesByteLimit() {
        try {
            extract(TestData.resource("tiny.ext4", tmp), File(tmp, "out"), maxBytes = 10_000)
            fail("expected FormatException")
        } catch (e: Ext4Reader.FormatException) {
            assertTrue(e.message!!.contains("size limit"))
        }
    }

    @Test fun rejectsNonEmptyDestination() {
        val out = File(tmp, "out").apply { mkdirs() }
        File(out, "stale").writeText("x")
        try { extract(TestData.resource("tiny.ext4", tmp), out); fail() } catch (e: Ext4Reader.FormatException) {}
    }

    @Test fun rejectsBadMagic() {
        val f = File(tmp, "junk.img").apply { writeBytes(ByteArray(8192)) }
        try { extract(f, File(tmp, "out")); fail() } catch (e: Ext4Reader.FormatException) {
            assertTrue(e.message!!.contains("magic"))
        }
    }

    @Test fun rejectsTruncatedImage() {
        val whole = TestData.resource("tiny.ext4", tmp)
        val cut = File(tmp, "cut.img").apply { writeBytes(whole.readBytes().copyOf(60_000)) }
        try { extract(cut, File(tmp, "out")); fail() } catch (e: Ext4Reader.FormatException) {}
    }

    @Test fun survivesCorruptedMetadataWithoutHangingOrEscaping() {
        // Flip bytes across the first 200 KB (superblock, group table, inode table, dirs).
        val original = TestData.resource("tiny.ext4", tmp).readBytes()
        val rnd = java.util.Random(1234)
        repeat(40) { round ->
            val data = original.copyOf()
            repeat(30) { data[1024 + rnd.nextInt(200_000)] = rnd.nextInt(256).toByte() }
            val img = File(tmp, "fuzz$round.img").apply { writeBytes(data) }
            val out = File(tmp, "fuzzout$round")
            try { extract(img, out) } catch (e: java.io.IOException) { /* a clean failure is fine */ }
            // whatever happened, nothing may exist outside `out`
            val escaped = tmp.listFiles()!!.map { it.name }.filter {
                !it.startsWith("fuzz") && it != "tiny.ext4"
            }
            assertTrue("unexpected files in tmp: $escaped", escaped.isEmpty())
        }
    }
}
