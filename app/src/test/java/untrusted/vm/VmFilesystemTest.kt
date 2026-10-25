package untrusted.vm

import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import untrusted.vm.core.VmFilesystem
import java.io.File
import java.nio.file.Files

class VmFilesystemTest {

    private lateinit var tempDir: File
    private lateinit var filesystem: VmFilesystem

    @Before
    fun setUp() {
        tempDir = Files.createTempDirectory("untrusted_fs_test_").toFile()
        filesystem = VmFilesystem(1, tempDir)
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun testInitializeSkeleton() {
        val success = filesystem.initializeSkeleton()
        assertTrue(success)

        assertTrue(filesystem.rootfsDir.exists())
        assertTrue(filesystem.systemDir.exists())
        assertTrue(filesystem.dataDir.exists())
        assertTrue(filesystem.sdcardDir.exists())
        assertTrue(File(filesystem.systemDir, "bin").exists())
        assertTrue(File(filesystem.systemDir, "xbin").exists())
        assertTrue(File(filesystem.dataDir, "app").exists())
        assertTrue(File(filesystem.dataDir, "data").exists())
    }

    @Test
    fun testWipeDataKeepsSystemIntact() {
        filesystem.initializeSkeleton()

        // Create dummy system file
        val sysFile = File(filesystem.systemDir, "dummy_system.prop")
        sysFile.writeText("test=1")

        // Create dummy data file
        val dataFile = File(filesystem.dataDir, "app/test_app.apk")
        dataFile.writeText("dummy apk data")

        assertTrue(sysFile.exists())
        assertTrue(dataFile.exists())

        // Wipe data
        val wiped = filesystem.wipeData()
        assertTrue(wiped)

        // System file preserved
        assertTrue(sysFile.exists())
        // Data directory recreated clean
        assertTrue(filesystem.dataDir.exists())
        assertFalse(dataFile.exists())
    }

    @Test
    fun testDeleteInstanceFiles() {
        filesystem.initializeSkeleton()
        assertTrue(tempDir.exists())

        val deleted = filesystem.deleteInstanceFiles()
        assertTrue(deleted)
        assertFalse(tempDir.exists())
    }

    @Test
    fun testResolveGuestFileCannotEscapeRootfs() {
        filesystem.initializeSkeleton()
        val root = filesystem.rootfsDir.canonicalFile
        for (evil in listOf("../../etc/passwd", "/../../../x", "system/../../../../y", "..")) {
            val resolved = filesystem.resolveGuestFile(evil).canonicalFile
            assertTrue("escaped for $evil: $resolved",
                resolved == root || resolved.path.startsWith(root.path + File.separator))
        }
        assertEquals(File(filesystem.rootfsDir, "sdcard/Download"), filesystem.resolveGuestFile("/sdcard/Download"))
        assertEquals(filesystem.rootfsDir, filesystem.resolveGuestFile("/"))
    }

    @Test
    fun testSymlinksAreNeverFollowedBySizeOrDelete() {
        filesystem.initializeSkeleton()
        val outside = Files.createTempDirectory("untrusted_outside_").toFile()
        File(outside, "keep.txt").writeText("keep me")
        val system = File(filesystem.rootfsDir, "system").apply { mkdirs() }
        java.nio.file.Files.createSymbolicLink(File(system, "escape").toPath(), outside.toPath())
        java.nio.file.Files.createSymbolicLink(File(system, "cycle").toPath(), system.toPath()) // would recurse forever if followed

        filesystem.calculateTotalSizeBytes() // must terminate
        assertTrue(filesystem.deleteInstanceFiles())
        assertEquals("keep me", File(outside, "keep.txt").readText())
        outside.deleteRecursively()
    }
}
