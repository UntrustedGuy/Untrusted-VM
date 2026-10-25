package untrusted.vm.core

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import untrusted.vm.IVmCallback
import untrusted.vm.IVmService
import untrusted.vm.service.VmProcessSlot
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * Central orchestrator managing multiple Untrusted VM instances.
 * Provides per-instance root toggle, lifecycle control, service binding, cloning, and ROM provisioning.
 */
class VmManager private constructor(val appContext: Context) {

    private val baseStorageDir: File = appContext.filesDir
    private val instancesDir: File = File(baseStorageDir, "instances")

    val romManager = RomManager(appContext)

    private val _instances = MutableStateFlow<List<VmInstance>>(emptyList())
    val instances: StateFlow<List<VmInstance>> = _instances.asStateFlow()

    private val _selectedInstance = MutableStateFlow<VmInstance?>(null)
    val selectedInstance: StateFlow<VmInstance?> = _selectedInstance.asStateFlow()

    private val serviceConnections = mutableMapOf<Int, ServiceConnection>()
    private val serviceBinders = mutableMapOf<Int, IVmService>()

    init {
        loadExistingInstances()
    }

    private fun loadExistingInstances() {
        if (!instancesDir.exists()) {
            instancesDir.mkdirs()
        }

        val loaded = mutableListOf<VmInstance>()
        val dirs = instancesDir.listFiles { f -> f.isDirectory && f.name.startsWith("vm_") }
        if (dirs != null && dirs.isNotEmpty()) {
            for (dir in dirs) {
                val configFile = File(dir, "config.json")
                if (configFile.exists()) {
                    try {
                        val json = configFile.readText(Charsets.UTF_8)
                        val config = VmConfig.fromJson(json)
                        loaded.add(VmInstance(config, baseStorageDir, appContext))
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }

        // If no instances exist yet, provision Default VM 1 (similar to VMOS initial experience)
        if (loaded.isEmpty()) {
            val defaultConfig = VmConfig(
                id = 1,
                name = "Untrusted VM 1",
                androidVersion = "7.1.2",
                isRootEnabled = true, // Root toggle feature available out of the box
                resolutionWidth = 720,
                resolutionHeight = 1280,
                dpi = 320,
                allocatedRamMb = 2048
            )
            val defaultInstance = VmInstance(defaultConfig, baseStorageDir, appContext)
            saveInstanceConfig(defaultInstance)
            loaded.add(defaultInstance)
        }

        _instances.value = loaded.sortedBy { it.id }
        _selectedInstance.value = loaded.firstOrNull()
    }

    fun selectInstance(id: Int) {
        _selectedInstance.value = _instances.value.firstOrNull { it.id == id }
    }

    fun getVmInstance(id: Int): VmInstance? {
        return _instances.value.firstOrNull { it.id == id }
    }

    /**
     * Creates a new VM instance with specified configuration, including per-instance root.
     */
    @Synchronized
    fun createInstance(
        name: String,
        androidVersion: String = "7.1.2",
        isRootEnabled: Boolean = false,
        resolutionWidth: Int = 720,
        resolutionHeight: Int = 1280,
        dpi: Int = 320,
        ramMb: Int = 2048
    ): VmInstance {
        val nextId = (_instances.value.maxOfOrNull { it.id } ?: 0) + 1
        val config = VmConfig(
            id = nextId,
            name = name.ifBlank { "Untrusted VM $nextId" },
            androidVersion = androidVersion,
            isRootEnabled = isRootEnabled,
            resolutionWidth = resolutionWidth,
            resolutionHeight = resolutionHeight,
            dpi = dpi,
            allocatedRamMb = ramMb
        )
        val instance = VmInstance(config, baseStorageDir, appContext)
        saveInstanceConfig(instance)

        val updated = _instances.value.toMutableList()
        updated.add(instance)
        _instances.value = updated.sortedBy { it.id }

        if (_selectedInstance.value == null) {
            _selectedInstance.value = instance
        }
        return instance
    }

    /**
     * Clones an existing VM instance (full duplication of config, disk, and apps).
     */
    @Synchronized
    fun cloneInstance(sourceId: Int, newName: String? = null): VmInstance? {
        val sourceInstance = getVmInstance(sourceId) ?: return null
        val nextId = (_instances.value.maxOfOrNull { it.id } ?: 0) + 1
        val srcDir = sourceInstance.filesystem.rootDir
        val dstDir = File(instancesDir, "vm_$nextId")

        // 1. Copy directory recursively
        copyDirectory(srcDir, dstDir)

        // 2. Load and adapt config for new ID
        val srcConfig = sourceInstance.config.value
        val clonedConfig = VmConfig(
            id = nextId,
            name = newName ?: "${srcConfig.name} (Clone)",
            androidVersion = srcConfig.androidVersion,
            isRootEnabled = srcConfig.isRootEnabled,
            resolutionWidth = srcConfig.resolutionWidth,
            resolutionHeight = srcConfig.resolutionHeight,
            dpi = srcConfig.dpi,
            allocatedRamMb = srcConfig.allocatedRamMb
        )

        val newInstance = VmInstance(clonedConfig, baseStorageDir, appContext)
        saveInstanceConfig(newInstance)

        val updated = _instances.value.toMutableList()
        updated.add(newInstance)
        _instances.value = updated.sortedBy { it.id }

        return newInstance
    }

    /**
     * Toggles root access for a specific instance.
     */
    fun toggleInstanceRoot(id: Int, enabled: Boolean): Boolean {
        val instance = _instances.value.firstOrNull { it.id == id } ?: return false
        val success = instance.setRootEnabled(enabled)
        if (success) {
            saveInstanceConfig(instance)
            // If service process is bound, notify it
            serviceBinders[id]?.let { binder ->
                try {
                    binder.setRootEnabled(enabled)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        return success
    }

    /**
     * Starts the specified VM instance and binds to its dedicated service process.
     */
    fun startInstance(id: Int): Boolean {
        val instance = _instances.value.firstOrNull { it.id == id } ?: return false
        val started = instance.start()
        if (started) {
            bindProcessSlot(id, instance)
        }
        return started
    }

    /**
     * Stops the specified VM instance and unbinds from its service process.
     */
    fun stopInstance(id: Int): Boolean {
        val instance = _instances.value.firstOrNull { it.id == id } ?: return false
        unbindProcessSlot(id)
        return instance.stop()
    }

    /**
     * Deletes a VM instance and cleans up all its filesystem storage.
     */
    @Synchronized
    fun deleteInstance(id: Int): Boolean {
        val instance = _instances.value.firstOrNull { it.id == id } ?: return false
        stopInstance(id)
        val deleted = instance.filesystem.deleteInstanceFiles()
        if (deleted) {
            val updated = _instances.value.filter { it.id != id }
            _instances.value = updated
            if (_selectedInstance.value?.id == id) {
                _selectedInstance.value = updated.firstOrNull()
            }
        }
        return deleted
    }

    private fun bindProcessSlot(id: Int, instance: VmInstance) {
        val slot = VmProcessSlot.forInstanceId(id)
        val intent = Intent(appContext, slot.serviceClass)

        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                val binder = IVmService.Stub.asInterface(service)
                serviceBinders[id] = binder
                try {
                    binder.registerCallback(object : IVmCallback.Stub() {
                        override fun onStateChanged(state: Int, message: String?) {
                            instance.appendLog("Service state: $state - $message")
                        }
                        override fun onLogOutput(line: String?) {
                            if (line != null) instance.appendLog(line)
                        }
                        override fun onRootStatusChanged(isRootEnabled: Boolean) {
                            instance.appendLog("Remote root status: $isRootEnabled")
                        }
                    })
                    binder.startVm(instance.config.value.toJson())
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                serviceBinders.remove(id)
            }
        }

        serviceConnections[id] = connection
        appContext.bindService(intent, connection, Context.BIND_AUTO_CREATE)
    }

    private fun unbindProcessSlot(id: Int) {
        serviceBinders[id]?.let { binder ->
            try {
                binder.stopVm()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        serviceConnections.remove(id)?.let { conn ->
            try {
                appContext.unbindService(conn)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        serviceBinders.remove(id)
    }

    private fun saveInstanceConfig(instance: VmInstance) {
        try {
            val configFile = File(instance.filesystem.rootDir, "config.json")
            configFile.writeText(instance.config.value.toJson(), Charsets.UTF_8)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun copyDirectory(source: File, destination: File) {
        if (!source.exists()) return
        if (source.isDirectory) {
            destination.mkdirs()
            source.listFiles()?.forEach { file ->
                copyDirectory(file, File(destination, file.name))
            }
        } else {
            destination.parentFile?.mkdirs()
            FileInputStream(source).use { inStream ->
                FileOutputStream(destination).use { outStream ->
                    inStream.copyTo(outStream)
                }
            }
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: VmManager? = null

        fun getInstance(context: Context): VmManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: VmManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
