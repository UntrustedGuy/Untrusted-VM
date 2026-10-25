package untrusted.vm

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import untrusted.vm.core.VmInstance
import untrusted.vm.core.VmManager
import untrusted.vm.ui.screens.DashboardScreen
import untrusted.vm.ui.screens.TerminalScreen
import untrusted.vm.ui.screens.VmDisplayScreen
import untrusted.vm.ui.screens.VmSettingsScreen
import untrusted.vm.ui.theme.DarkBackground
import untrusted.vm.ui.theme.UntrustedVmTheme

enum class Screen {
    DASHBOARD,
    DISPLAY,
    SETTINGS,
    TERMINAL
}

class MainActivity : ComponentActivity() {

    private lateinit var vmManager: VmManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        vmManager = (application as UntrustedVmApp).vmManager

        setContent {
            UntrustedVmTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = DarkBackground
                ) {
                    var currentScreen by remember { mutableStateOf(Screen.DASHBOARD) }
                    var activeInstanceId by remember { mutableStateOf<Int?>(null) }
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
                                    onBackClick = { currentScreen = Screen.DASHBOARD }
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
                    }
                }
            }
        }
    }
}
