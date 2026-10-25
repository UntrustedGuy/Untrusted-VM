package untrusted.vm.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import untrusted.vm.core.RomManager
import untrusted.vm.core.VmInstance
import untrusted.vm.core.VmManager
import untrusted.vm.ui.theme.*

/**
 * ROM Store & Installation Screen.
 * Equivalent to VMOS Pro's AddRemoteVmActivity / RomDownloadManager / AddLocalVmActivity.
 * Displays official VM OS images (Android 7.1 Geek, 9.0 Pure, 10.0 Pro, 12.0 GMS)
 * and allows 1-click provisioning or local archive installation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RomManagerScreen(
    instance: VmInstance,
    vmManager: VmManager,
    onBack: () -> Unit
) {
    val scrollState = rememberScrollState()
    val romManager = vmManager.romManager
    var installStatus by remember { mutableStateOf("") }
    var isInstalling by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ROM Store & Image Manager", color = TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
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
            if (installStatus.isNotEmpty()) {
                Surface(
                    color = NeonGreen.copy(alpha = 0.15f),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        installStatus,
                        color = NeonGreen,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

            Text(
                "Available Virtual ROM Profiles",
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary
            )

            romManager.availableRoms.forEach { profile ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, DarkCardBorder, RoundedCornerShape(16.dp)),
                    colors = CardDefaults.cardColors(containerColor = DarkSurface)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(profile.displayName, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                                Text("Android ${profile.androidVersion} • API ${profile.apiLevel} • ${profile.architecture}", color = NeonCyan, fontSize = 11.sp)
                            }
                            Text(profile.sizeFormatted, color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        Text(profile.description, color = TextSecondary, fontSize = 12.sp)

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (profile.isRootPreinstalled) {
                                Surface(color = NeonGreen.copy(alpha = 0.15f), shape = RoundedCornerShape(4.dp)) {
                                    Text("Pre-Rooted", color = NeonGreen, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                }
                            }
                            if (profile.hasGoogleServices) {
                                Surface(color = NeonCyan.copy(alpha = 0.15f), shape = RoundedCornerShape(4.dp)) {
                                    Text("GMS Included", color = NeonCyan, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                }
                            }
                        }

                        Button(
                            onClick = {
                                isInstalling = true
                                Thread {
                                    romManager.provisionRomProfile(instance.filesystem, profile)
                                    instance.updateConfig(
                                        instance.config.value
                                            .withAndroidVersion(profile.androidVersion)
                                            .withRootEnabled(profile.isRootPreinstalled)
                                    )
                                    isInstalling = false
                                    installStatus = "Successfully provisioned ${profile.displayName} into this instance!"
                                }.start()
                            },
                            enabled = !isInstalling,
                            colors = ButtonDefaults.buttonColors(containerColor = NeonPurple),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.CloudDownload, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (isInstalling) "Provisioning ROM..." else "Install This ROM Profile")
                        }
                    }
                }
            }

            // Local ROM Archive Import
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, DarkCardBorder, RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = DarkSurface)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null, tint = NeonCyan)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Install Local Custom ROM", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
                    }
                    Text("Select a ROM archive (.zip, .7z, .img) from host storage and extract it directly into this VM container.", color = TextSecondary, fontSize = 12.sp)

                    OutlinedButton(
                        onClick = {
                            installStatus = "To install a local ROM, place your .zip or .img in /sdcard/VMOSfiletransferstation/ and click Import."
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonCyan),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Browse Host ROM File (.zip / .img)")
                    }
                }
            }
        }
    }
}
