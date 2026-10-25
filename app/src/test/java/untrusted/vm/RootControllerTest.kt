package untrusted.vm

import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import untrusted.vm.core.RootController
import java.io.File
import java.nio.file.Files

class RootControllerTest {

    private lateinit var tempDir: File
    private lateinit var rootController: RootController

    @Before
    fun setUp() {
        tempDir = Files.createTempDirectory("untrusted_vm_test_").toFile()
        rootController = RootController(1, tempDir)
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun testInitialStateUnrooted() {
        assertFalse(rootController.isRootActive())
    }

    @Test
    fun testEnableRootProvisionsSuBinaryAndBuildProp() {
        val success = rootController.setRootState(true)
        assertTrue(success)
        assertTrue(rootController.isRootActive())

        val suFile = File(tempDir, "rootfs/system/xbin/su")
        assertTrue(suFile.exists())
        assertTrue(suFile.canExecute())

        val daemonsuFile = File(tempDir, "rootfs/system/xbin/daemonsu")
        assertTrue(daemonsuFile.exists())

        val buildProp = File(tempDir, "rootfs/system/build.prop")
        assertTrue(buildProp.exists())
        val propText = buildProp.readText()
        assertTrue(propText.contains("ro.build.tags=test-keys"))
        assertTrue(propText.contains("ro.debuggable=1"))
        assertTrue(propText.contains("ro.secure=0"))
    }

    @Test
    fun testDisableRootRemovesSuBinaryAndCleansBuildProp() {
        // Enable first
        rootController.setRootState(true)
        assertTrue(rootController.isRootActive())

        // Now disable
        val success = rootController.setRootState(false)
        assertTrue(success)
        assertFalse(rootController.isRootActive())

        val suFile = File(tempDir, "rootfs/system/xbin/su")
        assertFalse(suFile.exists())

        val daemonsuFile = File(tempDir, "rootfs/system/xbin/daemonsu")
        assertFalse(daemonsuFile.exists())

        val buildProp = File(tempDir, "rootfs/system/build.prop")
        assertTrue(buildProp.exists())
        val propText = buildProp.readText()
        assertTrue(propText.contains("ro.build.tags=release-keys"))
        assertTrue(propText.contains("ro.debuggable=0"))
        assertTrue(propText.contains("ro.secure=1"))
    }

    @Test
    fun testCommandExecutionWithRootEnabledVsDisabled() {
        // When root is disabled
        rootController.setRootState(false)
        val denied = rootController.executeGuestCommand("id", true)
        assertTrue(denied.contains("Error: Root is disabled"))

        // When root is enabled
        rootController.setRootState(true)
        val rootOut = rootController.executeGuestCommand("id", true)
        assertTrue(rootOut.contains("uid=0(root)"))

        // Non-root command
        val userOut = rootController.executeGuestCommand("id", false)
        assertTrue(userOut.contains("uid=10100(u0_a100)"))
    }
}
