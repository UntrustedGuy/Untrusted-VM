package untrusted.vm.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import untrusted.vm.core.VmInstance
import untrusted.vm.service.FloatingVmService
import untrusted.vm.ui.components.FloatingVmControls
import untrusted.vm.ui.components.RootBadge
import untrusted.vm.ui.components.VmSurfaceView
import untrusted.vm.ui.theme.*

/**
 * Fullscreen live virtual machine display screen.
 * Hosts the hardware-accelerated VmSurfaceView rendering guest frames at 60 FPS
 * and provides navigation controls including Floating Window overlay toggle.
 */
@Composable
fun VmDisplayScreen(
    instance: VmInstance,
    onBackToDashboard: () -> Unit,
    onOpenTerminal: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    val config by instance.config.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Guest Status & Resolution Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkSurfaceVariant)
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${config.name} (${config.resolutionWidth}x${config.resolutionHeight})",
                    color = TextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                RootBadge(isRootEnabled = config.isRootEnabled)
            }

            // Real Hardware-Accelerated Virtual Surface
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                AndroidView(
                    factory = { ctx ->
                        VmSurfaceView(ctx, instance).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // Overlay Navigation Controls (Home, Back, Terminal, Floating Window, Settings)
        FloatingVmControls(
            onBackClick = onBackToDashboard,
            onHomeClick = {
                // Send standard Android KEYCODE_HOME (3) to the guest container
                instance.engine.injectKey(3, 0)
                instance.engine.injectKey(3, 1)
            },
            onTerminalClick = onOpenTerminal,
            onFloatingWindowClick = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${context.packageName}")
                    )
                    context.startActivity(intent)
                } else {
                    val intent = Intent(context, FloatingVmService::class.java).apply {
                        action = FloatingVmService.ACTION_START_FLOATING
                        putExtra(FloatingVmService.EXTRA_VM_ID, instance.id)
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(intent)
                    } else {
                        context.startService(intent)
                    }
                    onBackToDashboard()
                }
            },
            onSettingsClick = onOpenSettings,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp)
        )
    }
}
