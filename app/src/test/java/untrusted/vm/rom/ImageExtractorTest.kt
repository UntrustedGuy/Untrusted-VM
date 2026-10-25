package untrusted.vm.rom

import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class ImageExtractorTest {
    private lateinit var tmp: File
    private val work get() = File(tmp, "work")
    @Before fun setUp() { tmp = Files.createTempDirectory("ie").toFile() }
    @After fun tearDown() { SafePaths.deleteTree(tmp) }

    private fun run(archive: File, cancelled: () -> Boolean = { false }): Pair<RomExtractor.Outcome, File> {
        val out = File(tmp, "out-${System.nanoTime()}")
        return RomExtractor.extractAny(archive, out, work, JvmFsOps, isCancelled = cancelled) to out
    }

    private fun assertExtractsToExpected(archive: File) {
        val (outcome, out) = run(archive)
        assertTrue(outcome.toString(), outcome is RomExtractor.Outcome.Ok)
        val problems = Manifest(TestData.resourceText("tiny.expected")).verify(out)
        assertTrue("$problems", problems.isEmpty())
        assertTrue("temporary raw image must be cleaned up", work.listFiles().isNullOrEmpty())
    }

    @Test fun rawExt4() = assertExtractsToExpected(TestData.resource("tiny.ext4", tmp))

    @Test fun sparseExt4() {
        val raw = TestData.resource("tiny.ext4", tmp)
        assertExtractsToExpected(File(tmp, "s.img").also { TestData.toSparse(raw, it) })
    }

    @Test fun xzCompressedSparse() {
        val raw = TestData.resource("tiny.ext4", tmp)
        val sparse = File(tmp, "s.img").also { TestData.toSparse(raw, it) }
        assertExtractsToExpected(File(tmp, "s.img.xz").also { TestData.xz(sparse, it) })
    }

    @Test fun xzCompressedRaw() {
        val raw = TestData.resource("tiny.ext4", tmp)
        assertExtractsToExpected(File(tmp, "r.img.xz").also { TestData.xz(raw, it) })
    }

    @Test fun sniffIgnoresFileNames() {
        val raw = TestData.resource("tiny.ext4", tmp)
        val renamed = File(tmp, "totally-a.zip").also { raw.copyTo(it) }
        assertEquals(RomExtractor.Kind.EXT4, RomExtractor.sniff(renamed))
        assertEquals(RomExtractor.Kind.UNKNOWN, RomExtractor.sniff(File(tmp, "n.bin").apply { writeBytes(ByteArray(5000)) }))
    }

    @Test fun plainSystemImageIsWrappedIntoSystemDir() {
        val (outcome, out) = run(TestData.resource("sysroot.ext4", tmp))
        assertTrue(outcome.toString(), outcome is RomExtractor.Outcome.Ok)
        assertTrue(File(out, "system/build.prop").isFile)
        assertTrue(File(out, "system/bin/sh").canExecute())
        assertEquals("sh", JvmFsOps.readLink(File(out, "system/bin/ls")))
        assertFalse(File(out, "build.prop").exists())
    }

    @Test fun unknownFormatGetsAHelpfulError() {
        val (outcome, _) = run(File(tmp, "junk").apply { writeText("hello world, definitely not a rom") })
        assertTrue((outcome as RomExtractor.Outcome.Fail).reason.contains(".img.xz"))
    }

    @Test fun corruptXzFailsCleanlyAndLeavesNoTempFiles() {
        val raw = TestData.resource("tiny.ext4", tmp)
        val xz = File(tmp, "r.img.xz").also { TestData.xz(raw, it) }
        val bytes = xz.readBytes(); for (i in 200 until 260) bytes[i] = (bytes[i] + 1).toByte()
        val (outcome, _) = run(File(tmp, "bad.img.xz").apply { writeBytes(bytes) })
        assertTrue(outcome.toString(), outcome is RomExtractor.Outcome.Fail)
        assertTrue(work.listFiles().isNullOrEmpty())
    }

    @Test fun cancellationIsReported() {
        val (outcome, _) = run(TestData.resource("tiny.ext4", tmp)) { true }
        assertTrue(outcome.toString(), outcome === RomExtractor.Outcome.Cancelled)
        assertTrue(work.listFiles().isNullOrEmpty())
    }
}
