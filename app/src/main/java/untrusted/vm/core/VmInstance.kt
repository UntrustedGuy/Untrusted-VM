package untrusted.vm.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * Represents a single virtual machine instance.
 * Encapsulates its isolated filesystem, configuration, root controller, and state.
 */
class VmInstance(
    initialConfig: VmConfig,
    baseStorageDir: File
) {
    val id: Int = initialConfig.id
    val filesystem = VmFilesystem(id, File(baseStorageDir, "instances/vm_$id"))
    val rootController = RootController(id, filesystem.rootDir)
    val suPolicyManager = SuPolicyManager(id)

    private val _config = MutableStateFlow(initialConfig)
    val config: StateFlow<VmConfig> = _config.asStateFlow()

    private val _state = MutableStateFlow(VmState.STOPPED)
    val state: StateFlow<VmState> = _state.asStateFlow()

    private val _logFlow = MutableStateFlow<List<String>>(emptyList())
    val logFlow: StateFlow<List<String>> = _logFlow.asStateFlow()

    init {
        filesystem.initializeSkeleton()
        // Synchronize initial root state with config
        rootController.setRootState(initialConfig.isRootEnabled)
        appendLog("Instance #$id initialized. Root: ${if (initialConfig.isRootEnabled) "ENABLED" else "DISABLED"}")
    }

    /**
     * Toggles simulated root for this instance.
     * Mirrors VMOS Pro's per-instance root option.
     */
    @Synchronized
    fun setRootEnabled(enabled: Boolean): Boolean {
        val success = rootController.setRootState(enabled)
        if (success) {
            val updatedConfig = _config.value.withRootEnabled(enabled)
            _config.value = updatedConfig
            saveConfig()
            appendLog("Root status changed to: ${if (enabled) "ENABLED (su active)" else "DISABLED (su masked)"}")
        } else {
            appendLog("Failed to change root status.")
        }
        return success
    }

    /**
     * Starts the virtual machine container.
     */
    @Synchronized
    fun start(): Boolean {
        if (_state.value == VmState.RUNNING || _state.value == VmState.STARTING) {
            return false
        }
        _state.value = VmState.STARTING
        appendLog("Starting Untrusted VM #$id (${_config.value.name})...")

        // Verify rootfs and sync root state
        filesystem.initializeSkeleton()
        rootController.setRootState(_config.value.isRootEnabled)

        appendLog("Guest filesystem mounted: ${filesystem.rootfsDir.absolutePath}")
        appendLog("Simulated root: ${if (_config.value.isRootEnabled) "ACTIVE (/system/xbin/su ready)" else "DISABLED"}")
        _state.value = VmState.RUNNING
        appendLog("Untrusted VM #$id is now RUNNING.")
        return true
    }

    /**
     * Stops the virtual machine container.
     */
    @Synchronized
    fun stop(): Boolean {
        if (_state.value == VmState.STOPPED || _state.value == VmState.STOPPING) {
            return false
        }
        _state.value = VmState.STOPPING
        appendLog("Stopping Untrusted VM #$id...")
        _state.value = VmState.STOPPED
        appendLog("Untrusted VM #$id has STOPPED.")
        return true
    }

    /**
     * Executes a command inside this instance's environment.
     */
    fun executeCommand(command: String, asRoot: Boolean): String {
        val effectiveRoot = asRoot && _config.value.isRootEnabled
        appendLog("CMD [${if (effectiveRoot) "root" else "user"}]: $command")
        val result = rootController.executeGuestCommand(command, effectiveRoot)
        appendLog("OUT: $result")
        return result
    }

    fun updateConfig(newConfig: VmConfig) {
        val oldRoot = _config.value.isRootEnabled
        _config.value = newConfig
        saveConfig()
        if (oldRoot != newConfig.isRootEnabled) {
            rootController.setRootState(newConfig.isRootEnabled)
        }
    }

    private fun saveConfig() {
        try {
            val configFile = File(filesystem.rootDir, "config.json")
            configFile.writeText(_config.value.toJson(), Charsets.UTF_8)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @Synchronized
    private fun appendLog(line: String) {
        val current = _logFlow.value.toMutableList()
        current.add("[${System.currentTimeMillis()}] $line")
        if (current.size > 100) {
            current.removeAt(0)
        }
        _logFlow.value = current
    }
}
