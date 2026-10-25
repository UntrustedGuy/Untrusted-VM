package untrusted.vm.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import untrusted.vm.core.VmInstance
import untrusted.vm.core.VmManager
import untrusted.vm.ui.components.RootBadge
import untrusted.vm.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VmSettingsScreen(
    instance: VmInstance,
    vmManager: VmManager,
    onBackClick: () -> Unit
) {
    val config by instance.config.collectAsState()
    var isRootEnabled by remember(config.isRootEnabled) { mutableStateOf(config.isRootEnabled) }
    var selectedResolution by remember { mutableStateOf("${config.resolutionWidth}x${config.resolutionHeight}") }
    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "${config.name} Settings", color = TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBackground)
            )
        },
        containerColor = DarkBackground
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Root Configuration Section (The core feature requested by user)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, if (isRootEnabled) NeonGreen else DarkCardBorder, RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = DarkSurface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Simulated Root Access",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = TextPrimary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                RootBadge(isRootEnabled = isRootEnabled)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Per-instance guest root inspired by VMOS Pro",
                                style = MaterialTheme.typography.bodyMedium,
                                color = NeonCyan
                            )
                        }
                        Switch(
                            checked = isRootEnabled,
                            onCheckedChange = { newState ->
                                isRootEnabled = newState
                                vmManager.toggleInstanceRoot(instance.id, newState)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = DarkBackground,
                                checkedTrackColor = NeonGreen
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = DarkCardBorder)
                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = if (isRootEnabled) {
                            "Status: ACTIVE\n" +
                            "• Provisions simulated /system/xbin/su inside this instance\n" +
                            "• Sets ro.build.tags=test-keys and ro.debuggable=1\n" +
                            "• Host device remains 100% unrooted stock Android (no Shizuku)\n" +
                            "• Guest apps can execute 'su' commands seamlessly"
                        } else {
                            "Status: DISABLED\n" +
                            "• /system/xbin/su is masked/removed from guest PATH\n" +
                            "• Sets ro.build.tags=release-keys and ro.debuggable=0\n" +
                            "• Passes guest rootbeer and SafetyNet integrity checks"
                        },
                        color = if (isRootEnabled) NeonGreen.copy(alpha = 0.9f) else TextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )
                }
            }

            // Display & Resolution
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, DarkCardBorder, RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = DarkSurface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Display Resolution",
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val resolutions = listOf("720x1280", "1080x1920", "1440x2560")
                        for (res in resolutions) {
                            val isSelected = selectedResolution == res
                            OutlinedButton(
                                onClick = { selectedResolution = res },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (isSelected) NeonCyan.copy(alpha = 0.15f) else DarkSurfaceVariant,
                                    contentColor = if (isSelected) NeonCyan else TextSecondary
                                )
                            ) {
                                Text(res, fontSize = 11.sp, maxLines = 1)
                            }
                        }
                    }
                }
            }

            // Storage & Maintenance
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, DarkCardBorder, RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = DarkSurface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Instance Storage",
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Storage Path: /instances/vm_${instance.id}/",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { instance.filesystem.wipeData() },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonAmber)
                        ) {
                            Text("Wipe Guest Data")
                        }

                        Button(
                            onClick = {
                                vmManager.deleteInstance(instance.id)
                                onBackClick()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = NeonRed)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, tint = TextPrimary)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Delete VM")
                        }
                    }
                }
            }
        }
    }
}
