package untrusted.vm

import android.app.Application
import untrusted.vm.core.VmManager

class UntrustedVmApp : Application() {

    lateinit var vmManager: VmManager
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        vmManager = VmManager.getInstance(this)
    }

    companion object {
        lateinit var instance: UntrustedVmApp
            private set
    }
}
