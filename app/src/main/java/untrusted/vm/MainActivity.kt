package untrusted.vm

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import untrusted.vm.core.VmInstance
import untrusted.vm.core.VmManager
import untrusted.vm.service.FloatingVmService
import untrusted.vm.ui.screens.*
import untrusted.vm.ui.theme.DarkBackground
import untrusted.vm.ui.theme.UntrustedVmTheme

enum class Screen {
    DASHBOARD,
    DISPLAY,
    SETTINGS,
    TERMINAL,
    SU_MANAGEMENT,
    DEVICE_SETTINGS,
    FILE_TRANSFER,
    DISPLAY_SETTINGS,
    LOCATION_SETTINGS,
    NETWORK_SETTINGS,
    ROM_MANAGER
}

class MainActivity : ComponentActivity() {

    private lateinit var vmManager: VmManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        vmManager = (application as UntrustedVmApp).vmManager

        var initialVmId: Int? = null
        if (intent.hasExtra(FloatingVmService.EXTRA_VM_ID)) {
            initialVmId = intent.getIntExtra(FloatingVmService.EXTRA_VM_ID, 1)
        }

        setContent {
            UntrustedVmTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = DarkBackground
                ) {
                    var currentScreen by remember {
                        mutableStateOf(if (initialVmId != null) Screen.DISPLAY else Screen.DASHBOARD)
                    }
                    var activeInstanceId by remember { mutableStateOf(initialVmId) }
                    val instances by vmManager.instances.collectAsState()
                    val activeInstance = instances.firstOrNull { it.id == activeInstanceId }

                    when (currentScreen) {
                        Screen.DASHBOARD -> {
                            DashboardScreen(
                                vmManager = vmManager,
                                onOpenVmDisplay = { id ->
                                    activeInstanceId = id
                                    currentScreen = Screen.DISPLAY
                                },
                                onOpenVmSettings = { id ->
                                    activeInstanceId = id
                                    currentScreen = Screen.SETTINGS
                                }
                            )
                        }
                        Screen.DISPLAY -> {
                            if (activeInstance != null) {
                                VmDisplayScreen(
                                    instance = activeInstance,
                                    onBackToDashboard = { currentScreen = Screen.DASHBOARD },
                                    onOpenTerminal = { currentScreen = Screen.TERMINAL },
                                    onOpenSettings = { currentScreen = Screen.SETTINGS }
                                )
                            } else {
                                currentScreen = Screen.DASHBOARD
                            }
                        }
                        Screen.SETTINGS -> {
                            if (activeInstance != null) {
                                VmSettingsScreen(
                                    instance = activeInstance,
                                    vmManager = vmManager,
                                    onBackClick = { currentScreen = Screen.DASHBOARD },
                                    onOpenSuManagement = { currentScreen = Screen.SU_MANAGEMENT },
                                    onOpenDeviceSettings = { currentScreen = Screen.DEVICE_SETTINGS },
                                    onOpenFileTransfer = { currentScreen = Screen.FILE_TRANSFER },
                                    onOpenDisplaySettings = { currentScreen = Screen.DISPLAY_SETTINGS },
                                    onOpenLocationSettings = { currentScreen = Screen.LOCATION_SETTINGS },
                                    onOpenNetworkSettings = { currentScreen = Screen.NETWORK_SETTINGS },
                                    onOpenRomManager = { currentScreen = Screen.ROM_MANAGER }
                                )
                            } else {
                                currentScreen = Screen.DASHBOARD
                            }
                        }
                        Screen.TERMINAL -> {
                            if (activeInstance != null) {
                                TerminalScreen(
                                    instance = activeInstance,
                                    onBackClick = { currentScreen = Screen.DISPLAY }
                                )
                            } else {
                                currentScreen = Screen.DASHBOARD
                            }
                        }
                        Screen.SU_MANAGEMENT -> {
                            if (activeInstance != null) {
                                SuManagementScreen(
                                    instance = activeInstance,
                                    onBack = { currentScreen = Screen.SETTINGS }
                                )
                            } else {
                                currentScreen = Screen.DASHBOARD
                            }
                        }
                        Screen.DEVICE_SETTINGS -> {
                            if (activeInstance != null) {
                                DeviceSettingsScreen(
                                    instance = activeInstance,
                                    onBack = { currentScreen = Screen.SETTINGS }
                                )
                            } else {
                                currentScreen = Screen.DASHBOARD
                            }
                        }
                        Screen.FILE_TRANSFER -> {
                            if (activeInstance != null) {
                                FileTransferScreen(
                                    instance = activeInstance,
                                    onBack = { currentScreen = Screen.SETTINGS }
                                )
                            } else {
                                currentScreen = Screen.DASHBOARD
                            }
                        }
                        Screen.DISPLAY_SETTINGS -> {
                            if (activeInstance != null) {
                                DisplaySettingsScreen(
                                    instance = activeInstance,
                                    onBack = { currentScreen = Screen.SETTINGS }
                                )
                            } else {
                                currentScreen = Screen.DASHBOARD
                            }
                        }
                        Screen.LOCATION_SETTINGS -> {
                            if (activeInstance != null) {
                                LocationSettingsScreen(
                                    instance = activeInstance,
                                    onBack = { currentScreen = Screen.SETTINGS }
                                )
                            } else {
                                currentScreen = Screen.DASHBOARD
                            }
                        }
                        Screen.NETWORK_SETTINGS -> {
                            if (activeInstance != null) {
                                NetworkSettingsScreen(
                                    instance = activeInstance,
                                    onBack = { currentScreen = Screen.SETTINGS }
                                )
                            } else {
                                currentScreen = Screen.DASHBOARD
                            }
                        }
                        Screen.ROM_MANAGER -> {
                            if (activeInstance != null) {
                                RomManagerScreen(
                                    instance = activeInstance,
                                    vmManager = vmManager,
                                    onBack = { currentScreen = Screen.SETTINGS }
                                )
                            } else {
                                currentScreen = Screen.DASHBOARD
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
    }
}
