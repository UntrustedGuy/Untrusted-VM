package untrusted.vm.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.RemoteCallbackList
import untrusted.vm.IVmCallback
import untrusted.vm.IVmService
import untrusted.vm.core.RootController
import untrusted.vm.core.VmConfig
import untrusted.vm.core.VmFilesystem
import untrusted.vm.core.VmState
import java.io.File

/**
 * Base multi-process service running in an isolated Android process.
 * Hosts the guest virtual machine container and simulated root environment.
 */
abstract class BaseVmService : Service() {

    abstract val processSlot: VmProcessSlot

    private var vmId: Int = 1
    private var vmConfig: VmConfig? = null
    private var vmFilesystem: VmFilesystem? = null
    private var rootController: RootController? = null
    private var vmEngine: untrusted.vm.core.VmEngine? = null
    private var currentState: VmState = VmState.STOPPED

    private val callbacks = RemoteCallbackList<IVmCallback>()

    private val binder = object : IVmService.Stub() {
        override fun getVmId(): Int = vmId

        override fun getVmState(): Int = currentState.code

        override fun startVm(configJson: String): Boolean {
            return try {
                val config = VmConfig.fromJson(configJson)
                this@BaseVmService.vmId = config.id
                this@BaseVmService.vmConfig = config
                val fs = VmFilesystem(config.id, File(filesDir, "instances/vm_${config.id}"))
                fs.initializeSkeleton()
                this@BaseVmService.vmFilesystem = fs

                val rc = RootController(config.id, fs.rootDir)
                rc.setRootState(config.isRootEnabled)
                this@BaseVmService.rootController = rc

                val engine = untrusted.vm.core.VmEngine(config)
                engine.syncNativeRootOption()
                this@BaseVmService.vmEngine = engine

                updateState(VmState.RUNNING, "VM #${config.id} container started")
                broadcastLog("Container initialized on ${processSlot.processName}")
                broadcastLog("Root state: ${if (config.isRootEnabled) "ENABLED" else "DISABLED"}")
                true
            } catch (e: Exception) {
                e.printStackTrace()
                updateState(VmState.ERROR, e.message ?: "Failed to start VM")
                false
            }
        }

        override fun stopVm(): Boolean {
            updateState(VmState.STOPPED, "VM container stopped")
            broadcastLog("Container stopped")
            return true
        }

        override fun isRootEnabled(): Boolean {
            return rootController?.isRootActive() ?: false
        }

        override fun setRootEnabled(enabled: Boolean): Boolean {
            val rc = rootController ?: return false
            val success = rc.setRootState(enabled)
            if (success) {
                val updatedConfig = vmConfig?.withRootEnabled(enabled)
                vmConfig = updatedConfig
                if (updatedConfig != null) {
                    vmEngine?.updateConfig(updatedConfig)
                }
                vmEngine?.syncNativeRootOption()
                broadcastRootChanged(enabled)
                broadcastLog("Simulated root set to $enabled")
            }
            return success
        }

        override fun executeShellCommand(command: String, runAsRoot: Boolean): String {
            val eng = vmEngine
            if (eng != null) {
                return eng.executeShell(command, runAsRoot).output
            }
            val rc = rootController
            return rc?.executeGuestCommand(command, runAsRoot)
                ?: "Error: VM not initialized"
        }

        override fun registerCallback(callback: IVmCallback) {
            callbacks.register(callback)
        }

        override fun unregisterCallback(callback: IVmCallback) {
            callbacks.unregister(callback)
        }
    }

    override fun onCreate() {
        super.onCreate()
        startInForeground()
    }

    override fun onBind(intent: Intent?): IBinder = binder

    private fun updateState(newState: VmState, msg: String) {
        currentState = newState
        val count = callbacks.beginBroadcast()
        for (i in 0 until count) {
            try {
                callbacks.getBroadcastItem(i).onStateChanged(newState.code, msg)
            } catch (e: Exception) {
                // Ignore dead client
            }
        }
        callbacks.finishBroadcast()
    }

    private fun broadcastLog(line: String) {
        val count = callbacks.beginBroadcast()
        for (i in 0 until count) {
            try {
                callbacks.getBroadcastItem(i).onLogOutput(line)
            } catch (e: Exception) {
                // Ignore
            }
        }
        callbacks.finishBroadcast()
    }

    private fun broadcastRootChanged(enabled: Boolean) {
        val count = callbacks.beginBroadcast()
        for (i in 0 until count) {
            try {
                callbacks.getBroadcastItem(i).onRootStatusChanged(enabled)
            } catch (e: Exception) {
                // Ignore
            }
        }
        callbacks.finishBroadcast()
    }

    private fun startInForeground() {
        val channelId = "untrusted_vm_service_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Untrusted VM Engine",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }

        val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, channelId)
                .setContentTitle("Untrusted VM Sandbox")
                .setContentText("Virtual machine process active (${processSlot.processName})")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle("Untrusted VM Sandbox")
                .setContentText("Virtual machine process active")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .build()
        }
        startForeground(1000 + processSlot.ordinal, notification)
    }
}

class VmService01 : BaseVmService() {
    override val processSlot: VmProcessSlot = VmProcessSlot.SLOT_1
}

class VmService02 : BaseVmService() {
    override val processSlot: VmProcessSlot = VmProcessSlot.SLOT_2
}

class VmService03 : BaseVmService() {
    override val processSlot: VmProcessSlot = VmProcessSlot.SLOT_3
}

class VmService04 : BaseVmService() {
    override val processSlot: VmProcessSlot = VmProcessSlot.SLOT_4
}
