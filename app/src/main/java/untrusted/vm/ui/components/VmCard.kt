package untrusted.vm.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import untrusted.vm.core.VmConfig
import untrusted.vm.core.VmState
import untrusted.vm.ui.theme.*

@Composable
fun VmCard(
    config: VmConfig,
    state: VmState,
    onStartClick: () -> Unit,
    onStopClick: () -> Unit,
    onOpenDisplayClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onRootToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, DarkCardBorder, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header Row: Name, Android Version badge, and Root Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = config.name,
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Android ${config.androidVersion} • ${config.allocatedRamMb} MB RAM",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
                RootBadge(isRootEnabled = config.isRootEnabled)
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Status and Root Toggle Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkSurfaceVariant, RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // State Indicator
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val statusDotColor = when (state) {
                        VmState.RUNNING -> NeonGreen
                        VmState.STARTING -> NeonAmber
                        VmState.STOPPING -> NeonAmber
                        VmState.ERROR -> NeonRed
                        else -> TextMuted
                    }
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(statusDotColor, RoundedCornerShape(5.dp))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = state.label,
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                // Root Toggle Switch (VMOS Pro inspired per-instance switch)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Root",
                        color = if (config.isRootEnabled) NeonGreen else TextSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Switch(
                        checked = config.isRootEnabled,
                        onCheckedChange = { onRootToggle(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = DarkBackground,
                            checkedTrackColor = NeonGreen,
                            uncheckedThumbColor = TextMuted,
                            uncheckedTrackColor = DarkSurface
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Action Buttons Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (state == VmState.RUNNING) {
                    Button(
                        onClick = onOpenDisplayClick,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = NeonCyan)
                    ) {
                        Text("Open VM", color = DarkBackground, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = onStopClick,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonRed)
                    ) {
                        Text("Stop")
                    }
                } else {
                    Button(
                        onClick = onStartClick,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = NeonCyan)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = DarkBackground)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Start VM", color = DarkBackground, fontWeight = FontWeight.Bold)
                    }
                }

                IconButton(
                    onClick = onSettingsClick,
                    modifier = Modifier.background(DarkSurfaceVariant, RoundedCornerShape(10.dp))
                ) {
                    Icon(Icons.Default.Settings, contentDescription = "Settings", tint = TextPrimary)
                }
            }
        }
    }
}
