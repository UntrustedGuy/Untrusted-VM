package untrusted.vm

import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import untrusted.vm.core.VmConfig
import untrusted.vm.core.VmEngine
import untrusted.vm.core.VmInstance
import java.io.File
import java.nio.file.Files

class VmEngineTest {

    private lateinit var tempDir: File

    @Before
    fun setUp() {
        tempDir = Files.createTempDirectory("untrusted_engine_test_").toFile()
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun testRootEnabledIdReportsUidZero() {
        val config = VmConfig(1, "Rooted VM", "7.1.2", true, 720, 1280, 320, 2048, 4, false, false, 1000L)
        val instance = VmInstance(config, tempDir)
        val engine = instance.engine

        val result = engine.executeShell("id", requestRoot = false)
        assertEquals(0, result.exitCode)
        assertTrue(result.output.contains("uid=10100(u0_a100)"))

        val rootResult = engine.executeShell("id", requestRoot = true)
        assertEquals(0, rootResult.exitCode)
        assertTrue(rootResult.output.contains("uid=0(root)"))
        assertTrue(rootResult.output.contains("context=u:r:su:s0"))
    }

    @Test
    fun testRootDisabledIdBlocksRoot() {
        val config = VmConfig(2, "Unrooted VM", "7.1.2", false, 720, 1280, 320, 2048, 4, false, false, 1000L)
        val instance = VmInstance(config, tempDir)
        val engine = instance.engine

        val denied = engine.executeShell("id", requestRoot = true)
        assertNotEquals(0, denied.exitCode)
        assertTrue(denied.output.contains("Permission denied: Root access is disabled"))
    }

    @Test
    fun testWhichSuBehavior() {
        val rootedInstance = VmInstance(VmConfig(1, "Rooted", "7.1.2", true, 720, 1280, 320, 2048, 4, false, false, 1000L), tempDir)
        val rootedEngine = rootedInstance.engine
        val rootedWhich = rootedEngine.executeShell("which su", requestRoot = false)
        assertEquals(0, rootedWhich.exitCode)
        assertEquals("/system/xbin/su", rootedWhich.output)

        val unrootedInstance = VmInstance(VmConfig(2, "Unrooted", "7.1.2", false, 720, 1280, 320, 2048, 4, false, false, 1000L), tempDir)
        val unrootedEngine = unrootedInstance.engine
        val unrootedWhich = unrootedEngine.executeShell("which su", requestRoot = false)
        assertNotEquals(0, unrootedWhich.exitCode)
        assertTrue(unrootedWhich.output.isEmpty())
    }

    @Test
    fun testSuVersionBehavior() {
        val rootedInstance = VmInstance(VmConfig(1, "Rooted", "7.1.2", true, 720, 1280, 320, 2048, 4, false, false, 1000L), tempDir)
        val rootedEngine = rootedInstance.engine
        val suVer = rootedEngine.executeShell("su -v", requestRoot = false)
        assertEquals(0, suVer.exitCode)
        assertTrue(suVer.output.contains("UntrustedVM-su"))

        val unrootedInstance = VmInstance(VmConfig(2, "Unrooted", "7.1.2", false, 720, 1280, 320, 2048, 4, false, false, 1000L), tempDir)
        val unrootedEngine = unrootedInstance.engine
        val unrootedSuVer = unrootedEngine.executeShell("su -v", requestRoot = false)
        assertEquals(127, unrootedSuVer.exitCode)
    }

    @Test
    fun testGetpropReflectsRootState() {
        val rootedInstance = VmInstance(VmConfig(1, "Rooted", "7.1.2", true, 720, 1280, 320, 2048, 4, false, false, 1000L), tempDir)
        val rootedEngine = rootedInstance.engine
        val tags = rootedEngine.executeShell("getprop ro.build.tags", requestRoot = false)
        assertEquals("test-keys", tags.output)

        val debuggable = rootedEngine.executeShell("getprop ro.debuggable", requestRoot = false)
        assertEquals("1", debuggable.output)

        val unrootedInstance = VmInstance(VmConfig(2, "Unrooted", "7.1.2", false, 720, 1280, 320, 2048, 4, false, false, 1000L), tempDir)
        val unrootedEngine = unrootedInstance.engine
        val unrootedTags = unrootedEngine.executeShell("getprop ro.build.tags", requestRoot = false)
        assertEquals("release-keys", unrootedTags.output)

        val unrootedDebuggable = unrootedEngine.executeShell("getprop ro.debuggable", requestRoot = false)
        assertEquals("0", unrootedDebuggable.output)
    }

    @Test
    fun testCommandWordBoundariesAndEdgeCases() {
        val unrootedInstance = VmInstance(VmConfig(2, "Unrooted", "7.1.2", false, 720, 1280, 320, 2048, 4, false, false, 1000L), tempDir)
        val engine = unrootedInstance.engine

        // Non-su commands starting with "su"
        val surfaceRes = engine.executeShell("surfaceflinger", requestRoot = false)
        assertEquals(0, surfaceRes.exitCode)
        assertFalse(surfaceRes.output.contains("su: inaccessible"))

        val whichSurface = engine.executeShell("which surface", requestRoot = false)
        assertEquals(1, whichSurface.exitCode)
        assertNotEquals("/system/xbin/su", whichSurface.output)

        val emptyRes = engine.executeShell("   ", requestRoot = false)
        assertEquals(0, emptyRes.exitCode)
        assertEquals("", emptyRes.output)
    }
}
