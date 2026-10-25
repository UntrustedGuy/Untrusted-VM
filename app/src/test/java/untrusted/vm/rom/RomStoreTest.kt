package untrusted.vm.rom

import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class RomStoreTest {
    private lateinit var tmp: File
    private lateinit var store: RomStore
    @Before fun setUp() { tmp = Files.createTempDirectory("rs").toFile(); store = RomStore(File(tmp, "roms"), JvmFsOps) }
    @After fun tearDown() { SafePaths.deleteTree(tmp) }

    private fun zip(vararg entries: Pair<String, String>): File {
        val f = File(tmp, "r${System.nanoTime()}.zip")
        ZipOutputStream(f.outputStream()).use { z ->
            for ((n, c) in entries) { z.putNextEntry(ZipEntry(n)); z.write(c.toByteArray()); z.closeEntry() }
        }
        return f
    }
    private fun meta(id: String) = RomStore.InstalledRom(id, "Test $id", "7.1.2", 25, "arm64-v8a", true, false, "00", "test")

    @Test fun installListGetRemove() {
        val r = store.install(meta("a1"), zip("system/build.prop" to "ro.build.version.release=7.1.2\nro.build.version.sdk=25"))
        assertTrue(r is RomStore.InstallResult.Ok)
        assertEquals(listOf("a1"), store.list().map { it.id })
        assertEquals("Test a1", store.get("a1")?.name)
        assertEquals("25", store.readBuildProp("a1")["ro.build.version.sdk"])
        assertTrue(store.remove("a1"))
        assertTrue(store.list().isEmpty())
    }

    @Test fun failedInstallLeavesNothingBehind() {
        val bad = zip("../evil" to "x", "system/a" to "1")
        assertTrue(store.install(meta("bad"), bad) is RomStore.InstallResult.Fail)
        assertTrue(store.list().isEmpty())
        val leftovers = File(tmp, "roms").listFiles()!!.map { it.name }
        assertTrue("staging dirs leaked: $leftovers", leftovers.isEmpty())
    }

    @Test fun reinstallReplacesAtomically() {
        store.install(meta("r"), zip("system/v" to "one"))
        store.install(meta("r"), zip("system/v" to "two"))
        assertEquals("two", File(store.baseDir("r"), "system/v").readText())
        assertEquals(1, store.list().size)
    }

    @Test fun installsFromImageAndKeepsSymlinksWhenApplied() {
        val img = TestData.resource("tiny.ext4", tmp)
        val xz = File(tmp, "g.img.xz").also { TestData.xz(img, it) }
        assertTrue(store.install(meta("gsi"), xz) is RomStore.InstallResult.Ok)
        assertEquals("8.1.0", store.readBuildProp("gsi")["ro.build.version.release"])

        val rootfs = File(tmp, "rootfs")
        assertTrue(store.copyBaseInto("gsi", rootfs))
        val problems = Manifest(TestData.resourceText("tiny.expected")).verify(rootfs)
        assertTrue("applied tree differs: $problems", problems.isEmpty())
    }

    @Test fun removeAndReapplyNeverFollowSymlinksOutOfTheRom() {
        // A hostile ROM: a symlink pointing at a directory outside the ROM store.
        val outside = File(tmp, "precious").apply { mkdirs() }
        File(outside, "keep.txt").writeText("do not delete")
        store.install(meta("evil"), zip("system/build.prop" to "k=v"))
        JvmFsOps.symlink(outside.absolutePath, File(store.baseDir("evil"), "system/escape"))

        val rootfs = File(tmp, "rootfs")
        assertTrue(store.copyBaseInto("evil", rootfs))
        assertEquals(outside.absolutePath, JvmFsOps.readLink(File(rootfs, "system/escape")))   // copied as a link, not followed
        assertTrue(store.copyBaseInto("evil", rootfs))                                         // re-apply wipes system/ safely
        assertTrue(store.remove("evil"))
        assertEquals("do not delete", File(outside, "keep.txt").readText())
    }

    @Test fun rejectsInvalidIds() {
        assertTrue(store.install(meta("../x"), zip("system/a" to "1")) is RomStore.InstallResult.Fail)
    }

    @Test fun copyBaseKeepsExistingUserData() {
        store.install(meta("c"), zip("system/build.prop" to "new", "data/preload" to "rom-data"))
        val rootfs = File(tmp, "rootfs")
        File(rootfs, "system").mkdirs(); File(rootfs, "system/old.txt").writeText("old")
        File(rootfs, "data").mkdirs(); File(rootfs, "data/user.db").writeText("precious")

        assertTrue(store.copyBaseInto("c", rootfs))

        assertEquals("new", File(rootfs, "system/build.prop").readText())
        assertFalse("old system files must be replaced", File(rootfs, "system/old.txt").exists())
        assertEquals("precious", File(rootfs, "data/user.db").readText())
        assertFalse("rom data must not clobber existing /data", File(rootfs, "data/preload").exists())
    }

    @Test fun copyBaseIntoFreshInstanceIncludesRomData() {
        store.install(meta("f"), zip("system/x" to "1", "data/preload" to "rom-data"))
        val rootfs = File(tmp, "fresh")
        assertTrue(store.copyBaseInto("f", rootfs))
        assertEquals("rom-data", File(rootfs, "data/preload").readText())
    }
}
