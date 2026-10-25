package untrusted.vm.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import untrusted.vm.core.BackupRestoreManager
import untrusted.vm.core.VmInstance
import untrusted.vm.core.VmManager
import untrusted.vm.ui.components.RootBadge
import untrusted.vm.ui.theme.*
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VmSettingsScreen(
    instance: VmInstance,
    vmManager: VmManager,
    onBackClick: () -> Unit,
    onOpenSuManagement: () -> Unit = {},
    onOpenDeviceSettings: () -> Unit = {},
    onOpenFileTransfer: () -> Unit = {},
    onOpenDisplaySettings: () -> Unit = {},
    onOpenLocationSettings: () -> Unit = {},
    onOpenNetworkSettings: () -> Unit = {},
    onOpenRomManager: () -> Unit = {}
) {
    val context = LocalContext.current
    val config by instance.config.collectAsState()
    var isRootEnabled by remember(config.isRootEnabled) { mutableStateOf(config.isRootEnabled) }
    val scrollState = rememberScrollState()

    val backupManager = remember { BackupRestoreManager(instance.filesystem) }
    var actionStatus by remember { mutableStateOf("") }
    var isBackingUp by remember { mutableStateOf(false) }

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
            if (actionStatus.isNotEmpty()) {
                Surface(
                    color = NeonGreen.copy(alpha = 0.15f),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        actionStatus,
                        color = NeonGreen,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

            // Root Configuration Section
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

                    if (isRootEnabled) {
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = onOpenSuManagement,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonGreen)
                        ) {
                            Icon(Icons.Default.Security, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Manage Superuser Permissions & Audit Log")
                        }
                    }
                }
            }

            // VM Tools & Subsystems
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, DarkCardBorder, RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = DarkSurface)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "VMOS Pro Features & Tools",
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary
                    )

                    OutlinedButton(
                        onClick = onOpenDisplaySettings,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonCyan)
                    ) {
                        Icon(Icons.Default.AspectRatio, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Display Resolution & Refresh Rate (${config.resolutionWidth}x${config.resolutionHeight})")
                    }

                    OutlinedButton(
                        onClick = onOpenDeviceSettings,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonCyan)
                    ) {
                        Icon(Icons.Default.PhoneAndroid, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Device Model, IMEI & Hardware Spoofing")
                    }

                    OutlinedButton(
                        onClick = onOpenLocationSettings,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonAmber)
                    ) {
                        Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("GPS Location Penetrator & Step Counter")
                    }

                    OutlinedButton(
                        onClick = onOpenNetworkSettings,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonPurple)
                    ) {
                        Icon(Icons.Default.SettingsEthernet, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Network, Virtual ADB & Proxy Settings")
                    }

                    OutlinedButton(
                        onClick = onOpenFileTransfer,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonPurple)
                    ) {
                        Icon(Icons.Default.FolderShared, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("File Transfer Station & Host App Importer")
                    }

                    OutlinedButton(
                        onClick = onOpenRomManager,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonGreen)
                    ) {
                        Icon(Icons.Default.Layers, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("ROM Store & Custom OS Image Manager")
                    }
                }
            }

            // Instance Clone & Snapshot Management
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, DarkCardBorder, RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = DarkSurface)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Instance Management & Clone",
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary
                    )

                    OutlinedButton(
                        onClick = {
                            val cloned = vmManager.cloneInstance(instance.id)
                            actionStatus = if (cloned != null) "Successfully cloned to ${cloned.config.value.name}!" else "Clone failed"
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonCyan)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Clone This Virtual Machine")
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                isBackingUp = true
                                val backupDir = File(context.getExternalFilesDir(null), "backups")
                                backupDir.mkdirs()
                                val backupFile = File(backupDir, "vm_${instance.id}_backup_${System.currentTimeMillis()}.uvm")
                                Thread {
                                    val ok = backupManager.createBackup(backupFile)
                                    isBackingUp = false
                                    actionStatus = if (ok) "Snapshot backup saved: ${backupFile.name}" else "Backup failed"
                                }.start()
                            },
                            enabled = !isBackingUp,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonCyan)
                        ) {
                            Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (isBackingUp) "Backing up..." else "Backup VM")
                        }

                        OutlinedButton(
                            onClick = {
                                instance.filesystem.wipeData()
                                actionStatus = "Guest data (/data and /cache) wiped clean"
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonAmber)
                        ) {
                            Text("Wipe Data")
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Button(
                        onClick = {
                            vmManager.deleteInstance(instance.id)
                            onBackClick()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NeonRed),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, tint = TextPrimary)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Delete Virtual Machine")
                    }
                }
            }
        }
    }
}
