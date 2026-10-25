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
        val config = VmConfig(id = 1, name = "Rooted VM", isRootEnabled = true)
        val instance = VmInstance(config, tempDir)
        val engine = VmEngine(instance)

        val result = engine.executeShell("id", requestRoot = false)
        assertEquals(0, result.exitCode)
        assertTrue(result.output.contains("uid=10150(u0_a150)"))

        val rootResult = engine.executeShell("id", requestRoot = true)
        assertEquals(0, rootResult.exitCode)
        assertTrue(rootResult.output.contains("uid=0(root)"))
        assertTrue(rootResult.output.contains("context=u:r:su:s0"))
    }

    @Test
    fun testRootDisabledIdBlocksRoot() {
        val config = VmConfig(id = 2, name = "Unrooted VM", isRootEnabled = false)
        val instance = VmInstance(config, tempDir)
        val engine = VmEngine(instance)

        val denied = engine.executeShell("id", requestRoot = true)
        assertNotEquals(0, denied.exitCode)
        assertTrue(denied.output.contains("Permission denied: Root access is disabled"))
    }

    @Test
    fun testWhichSuBehavior() {
        val rootedInstance = VmInstance(VmConfig(id = 1, name = "Rooted", isRootEnabled = true), tempDir)
        val rootedEngine = VmEngine(rootedInstance)
        val rootedWhich = rootedEngine.executeShell("which su", requestRoot = false)
        assertEquals(0, rootedWhich.exitCode)
        assertEquals("/system/xbin/su", rootedWhich.output)

        val unrootedInstance = VmInstance(VmConfig(id = 2, name = "Unrooted", isRootEnabled = false), tempDir)
        val unrootedEngine = VmEngine(unrootedInstance)
        val unrootedWhich = unrootedEngine.executeShell("which su", requestRoot = false)
        assertNotEquals(0, unrootedWhich.exitCode)
        assertTrue(unrootedWhich.output.isEmpty())
    }

    @Test
    fun testSuVersionBehavior() {
        val rootedInstance = VmInstance(VmConfig(id = 1, name = "Rooted", isRootEnabled = true), tempDir)
        val rootedEngine = VmEngine(rootedInstance)
        val suVer = rootedEngine.executeShell("su -v", requestRoot = false)
        assertEquals(0, suVer.exitCode)
        assertTrue(suVer.output.contains("UntrustedVM-su"))

        val unrootedInstance = VmInstance(VmConfig(id = 2, name = "Unrooted", isRootEnabled = false), tempDir)
        val unrootedEngine = VmEngine(unrootedInstance)
        val unrootedSuVer = unrootedEngine.executeShell("su -v", requestRoot = false)
        assertEquals(127, unrootedSuVer.exitCode)
    }

    @Test
    fun testGetpropReflectsRootState() {
        val rootedInstance = VmInstance(VmConfig(id = 1, name = "Rooted", isRootEnabled = true), tempDir)
        val rootedEngine = VmEngine(rootedInstance)
        val tags = rootedEngine.executeShell("getprop ro.build.tags", requestRoot = false)
        assertEquals("test-keys", tags.output)

        val debuggable = rootedEngine.executeShell("getprop ro.debuggable", requestRoot = false)
        assertEquals("1", debuggable.output)

        val unrootedInstance = VmInstance(VmConfig(id = 2, name = "Unrooted", isRootEnabled = false), tempDir)
        val unrootedEngine = VmEngine(unrootedInstance)
        val unrootedTags = unrootedEngine.executeShell("getprop ro.build.tags", requestRoot = false)
        assertEquals("release-keys", unrootedTags.output)

        val unrootedDebuggable = unrootedEngine.executeShell("getprop ro.debuggable", requestRoot = false)
        assertEquals("0", unrootedDebuggable.output)
    }

    @Test
    fun testEmptyAndWhitespaceCommands() {
        val instance = VmInstance(VmConfig(id = 1, name = "Test", isRootEnabled = true), tempDir)
        val engine = VmEngine(instance)
        val res = engine.executeShell("   ", requestRoot = false)
        assertEquals(0, res.exitCode)
        assertEquals("", res.output)
    }
}
