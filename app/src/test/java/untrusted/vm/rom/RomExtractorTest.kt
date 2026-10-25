package untrusted.vm.rom

import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class RomExtractorTest {
    private lateinit var tmp: File
    @Before fun setUp() { tmp = Files.createTempDirectory("rx").toFile() }
    @After fun tearDown() { SafePaths.deleteTree(tmp) }

    private fun zip(name: String, vararg entries: Pair<String, String>): File {
        val f = File(tmp, name)
        ZipOutputStream(f.outputStream()).use { z ->
            for ((n, c) in entries) { z.putNextEntry(ZipEntry(n)); z.write(c.toByteArray()); z.closeEntry() }
        }
        return f
    }

    @Test fun extractsTopLevelSystemTree() {
        val a = zip("rom.zip", "system/build.prop" to "ro.build.version.release=7.1.2", "system/bin/sh" to "x", "data/readme" to "y")
        val out = File(tmp, "out")
        val r = RomExtractor.extract(a, out)
        assertTrue(r is RomExtractor.Outcome.Ok)
        assertEquals("ro.build.version.release=7.1.2", File(out, "system/build.prop").readText())
        assertTrue(File(out, "data/readme").exists())
        assertTrue(File(out, "system/bin/sh").canExecute())
    }

    @Test fun stripsSingleWrapperDirectory() {
        val a = zip("w.zip", "MyRom/system/build.prop" to "k=v", "MyRom/vendor/x" to "1")
        val out = File(tmp, "out")
        val r = RomExtractor.extract(a, out) as RomExtractor.Outcome.Ok
        assertEquals("MyRom/", r.strippedPrefix)
        assertTrue(File(out, "system/build.prop").exists())
        assertTrue(File(out, "vendor/x").exists())
    }

    @Test fun rejectsZipSlip() {
        val a = zip("evil.zip", "system/ok" to "1", "../../escaped.txt" to "pwned")
        val out = File(tmp, "nest/out")
        val r = RomExtractor.extract(a, out)
        assertTrue(r is RomExtractor.Outcome.Fail)
        assertFalse(File(tmp, "escaped.txt").exists())
        assertFalse(File(tmp.parentFile, "escaped.txt").exists())
    }

    @Test fun rejectsAbsoluteEntryNames() {
        val a = zip("abs.zip", "system/ok" to "1", "/tmp/untrusted_abs_pwn" to "x")
        assertTrue(RomExtractor.extract(a, File(tmp, "out")) is RomExtractor.Outcome.Fail)
        assertFalse(File("/tmp/untrusted_abs_pwn").exists())
    }

    @Test fun explainsImageBasedRoms() {
        val a = zip("img.zip", "system.img" to "RAWDATA", "vendor.img" to "RAWDATA")
        val r = RomExtractor.extract(a, File(tmp, "out")) as RomExtractor.Outcome.Fail
        assertTrue(r.reason.contains(".img"))
    }

    @Test fun rejectsArchiveWithoutSystemDir() {
        val a = zip("none.zip", "foo/bar" to "1")
        assertTrue(RomExtractor.extract(a, File(tmp, "out")) is RomExtractor.Outcome.Fail)
    }

    @Test fun enforcesActualByteLimit() {
        val a = zip("big.zip", "system/blob" to "x".repeat(10_000))
        val r = RomExtractor.extract(a, File(tmp, "out"), maxBytes = 1_000)
        assertTrue(r is RomExtractor.Outcome.Fail)
    }

    @Test fun rejectsNonZip() {
        val f = File(tmp, "junk.zip").apply { writeText("this is not a zip") }
        assertTrue(RomExtractor.extract(f, File(tmp, "out")) is RomExtractor.Outcome.Fail)
    }
}
