package untrusted.vm.core

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * Represents a single virtual machine instance.
 * Encapsulates its isolated filesystem, configuration, root controller, hardware HAL,
 * audio bridge, virtual ADB network, GPS penetrator, and step simulator.
 */
class VmInstance(
    initialConfig: VmConfig,
    baseStorageDir: File,
    appContext: Context? = null
) {
    val id: Int = initialConfig.id
    val filesystem = VmFilesystem(id, File(baseStorageDir, "instances/vm_$id"))
    val rootController = RootController(id, filesystem.rootDir, appContext)
    val suPolicyManager = SuPolicyManager(id)
    val engine = VmEngine(initialConfig)
    val fileTransferStation = FileTransferStation(filesystem)
    val networkManager = VmNetworkManager(id, filesystem)
    val stepSimulator = StepCounterSimulator(id, filesystem)

    val halBoxManager = if (appContext != null) {
        HalBoxManager(appContext, id, engine)
    } else null

    val gpsPenetrator = if (appContext != null && halBoxManager != null) {
        GpsPenetrator(appContext, id, filesystem, halBoxManager)
    } else null

    val audioBridge = if (appContext != null) {
        AudioBridgeManager(appContext, id, filesystem)
    } else null

    private val _config = MutableStateFlow(initialConfig)
    val config: StateFlow<VmConfig> = _config.asStateFlow()

    private val _state = MutableStateFlow(VmState.STOPPED)
    val state: StateFlow<VmState> = _state.asStateFlow()

    private val _logFlow = MutableStateFlow<List<String>>(emptyList())
    val logFlow: StateFlow<List<String>> = _logFlow.asStateFlow()

    init {
        filesystem.initializeSkeleton()
        engine.setRootfsPath(filesystem.rootfsDir.absolutePath)
        engine.initNativeSandbox(
            filesystem.rootfsDir.absolutePath,
            initialConfig.resolutionWidth,
            initialConfig.resolutionHeight
        )
        // Set LD_PRELOAD path for uid spoofing (libvm_preload.so) when root is enabled.
        if (initialConfig.isRootEnabled && appContext != null) {
            val nativeDir = appContext.applicationInfo.nativeLibraryDir
            if (nativeDir != null) {
                val preloadLib = File("$nativeDir/libvm_preload.so")
                if (preloadLib.exists()) {
                    engine.setPreloadPath(preloadLib.absolutePath)
                    appendLog("LD_PRELOAD set to ${preloadLib.absolutePath} for VM #$id")
                }
            }
        }
        // Synchronize initial root state with config
        rootController.setRootState(initialConfig.isRootEnabled)
        engine.syncNativeRootOption()
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
            engine.updateConfig(updatedConfig)
            engine.syncNativeRootOption()
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
        engine.setRootfsPath(filesystem.rootfsDir.absolutePath)
        engine.initNativeSandbox(
            filesystem.rootfsDir.absolutePath,
            _config.value.resolutionWidth,
            _config.value.resolutionHeight
        )
        rootController.setRootState(_config.value.isRootEnabled)
        engine.syncNativeRootOption()

        // Refresh LD_PRELOAD path when root is enabled (in case toggled after init).
        if (_config.value.isRootEnabled && appContext != null) {
            val nativeDir = appContext.applicationInfo.nativeLibraryDir
            if (nativeDir != null) {
                val preloadLib = File("$nativeDir/libvm_preload.so")
                if (preloadLib.exists()) {
                    engine.setPreloadPath(preloadLib.absolutePath)
                }
            }
        }

        // Start su-daemon (daemonsu) for per-app root requests when root is enabled.
        // This mirrors VMOS Pro's su daemon that listens on a UNIX domain socket.
        if (_config.value.isRootEnabled && RootOverlayProvider.isRootInstalled(filesystem.rootfsDir)) {
            val socketPath = RootOverlayProvider.suSocketPath(id)
            val daemonStart = "nohup /system/xbin/daemonsu >/dev/null 2>&1 &"
            val res = engine.executeShell(daemonStart, true)
            appendLog("su-daemon started for VM #$id (socket=$socketPath): ${res.output.trim()}")
        } else if (_config.value.isRootEnabled) {
            appendLog("Root enabled but overlay not installed; su binary unavailable for VM #$id.")
        }

        // Start Hardware subsystems
        halBoxManager?.start()
        audioBridge?.start()
        networkManager.startAdbBridge(5555)

        appendLog("Guest filesystem mounted: ${filesystem.rootfsDir.absolutePath}")
        appendLog("Guest root: ${if (_config.value.isRootEnabled) "ACTIVE (/system/xbin/su + daemonsu)" else "DISABLED"}")
        _state.value = VmState.RUNNING
        appendLog("Untrusted VM #$id is now RUNNING.")
        return true

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

        // Stop subsystems
        audioBridge?.stop()
        networkManager.stopAdbBridge()
        halBoxManager?.stop()
        gpsPenetrator?.disablePassThrough()
        gpsPenetrator?.disableMockLocation()
        stepSimulator.stop()

        _state.value = VmState.STOPPED
        appendLog("Untrusted VM #$id has STOPPED.")
        return true
    }

    /**
     * Clean RAM / Cache drop for VM (VMOS Pro Quick Clean feature).
     */
    fun cleanMemory(): Boolean {
        appendLog("Dropping VM caches & cleaning memory...")
        executeCommand("echo 3 > /proc/sys/vm/drop_caches", true)
        return true
    }

    /**
     * Executes a command inside this instance's environment.
     */
    fun executeCommand(command: String, asRoot: Boolean): String {
        appendLog("CMD [${if (asRoot) "root" else "user"}]: $command")
        val result = engine.executeShell(command, asRoot)
        appendLog("OUT: ${result.output}")
        return result.output
    }

    @Synchronized
    fun updateConfig(newConfig: VmConfig) {
        val oldRoot = _config.value.isRootEnabled
        _config.value = newConfig
        engine.updateConfig(newConfig)
        engine.syncNativeRootOption()
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
    fun appendLog(line: String) {
        val current = _logFlow.value.toMutableList()
        current.add("[${System.currentTimeMillis()}] $line")
        if (current.size > 100) {
            current.removeAt(0)
        }
        _logFlow.value = current
    }
}
