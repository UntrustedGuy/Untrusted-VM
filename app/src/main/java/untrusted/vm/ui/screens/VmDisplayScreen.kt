package untrusted.vm.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import untrusted.vm.core.VmInstance
import untrusted.vm.ui.components.FloatingVmControls
import untrusted.vm.ui.components.RootBadge
import untrusted.vm.ui.theme.*

@Composable
fun VmDisplayScreen(
    instance: VmInstance,
    onBackToDashboard: () -> Unit,
    onOpenTerminal: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val config by instance.config.collectAsState()
    val state by instance.state.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        // Virtual Machine Display Frame
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Guest Status Bar
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

            // Virtual Screen Canvas / Viewport
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color(0xFF0F172A))
                    .border(1.dp, DarkCardBorder),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Untrusted VM #${instance.id}",
                        style = androidx.compose.material3.MaterialTheme.typography.headlineMedium,
                        color = NeonCyan
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Android ${config.androidVersion} Sandbox Container",
                        style = androidx.compose.material3.MaterialTheme.typography.bodyLarge,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = if (config.isRootEnabled) "Simulated Root Active (/system/xbin/su)" else "Standard Userland (Non-root)",
                        color = if (config.isRootEnabled) NeonGreen else TextMuted,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp
                    )
                }
            }
        }

        // Overlay Floating Navigation Bar
        FloatingVmControls(
            onBackClick = onBackToDashboard,
            onHomeClick = { /* Send HOME key event to guest */ },
            onTerminalClick = onOpenTerminal,
            onSettingsClick = onOpenSettings,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp)
        )
    }
}
