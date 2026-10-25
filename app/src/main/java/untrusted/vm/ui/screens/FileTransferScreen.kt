package untrusted.vm.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import untrusted.vm.core.AppImporter
import untrusted.vm.core.HostAppItem
import untrusted.vm.core.VmInstance
import untrusted.vm.ui.theme.*
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileTransferScreen(
    instance: VmInstance,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val appImporter = remember { AppImporter(context, instance.filesystem) }
    var hostApps by remember { mutableStateOf<List<HostAppItem>>(emptyList()) }
    var transferFiles by remember { mutableStateOf<List<File>>(emptyList()) }
    var selectedTab by remember { mutableStateOf(0) }
    var statusMessage by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        hostApps = appImporter.getInstalledHostApps()
        transferFiles = instance.fileTransferStation.listSharedTransferFiles()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("File Transfer Station", color = TextPrimary, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkSurface)
            )
        },
        containerColor = DarkBackground
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = DarkSurfaceVariant,
                contentColor = NeonCyan
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Import Host Apps (${hostApps.size})") }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = {
                        transferFiles = instance.fileTransferStation.listSharedTransferFiles()
                        selectedTab = 1
                    },
                    text = { Text("VM Files (${transferFiles.size})") }
                )
            }

            if (statusMessage.isNotEmpty()) {
                Surface(
                    color = NeonCyan.copy(alpha = 0.15f),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        statusMessage,
                        color = NeonCyan,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

            if (selectedTab == 0) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(hostApps) { app ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = DarkSurface),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth().border(1.dp, DarkCardBorder, RoundedCornerShape(10.dp))
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(app.appName, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                    Text(app.packageName, color = TextMuted, fontSize = 11.sp)
                                    Text("v${app.versionName} • ${(app.apkFile.length() / (1024 * 1024))} MB", color = TextSecondary, fontSize = 11.sp)
                                }

                                Button(
                                    onClick = {
                                        val success = appImporter.importAppToGuest(app)
                                        statusMessage = if (success) {
                                            "Imported ${app.appName} to /data/app/${app.packageName}.apk"
                                        } else {
                                            "Failed to import ${app.appName}"
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Install in VM", color = DarkBackground, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            } else {
                if (transferFiles.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            "Transfer folder is empty.\nFiles placed in /sdcard/VMOSfiletransferstation appear here.",
                            color = TextMuted,
                            fontSize = 13.sp
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(transferFiles) { file ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth().border(1.dp, DarkCardBorder, RoundedCornerShape(10.dp))
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(file.name, color = TextPrimary, fontWeight = FontWeight.Medium, fontSize = 13.sp)
                                        Text("${file.length() / 1024} KB", color = TextMuted, fontSize = 11.sp)
                                    }

                                    IconButton(onClick = {
                                        val exportDest = File(context.getExternalFilesDir(null), file.name)
                                        val ok = instance.fileTransferStation.exportFileToHost(
                                            "/sdcard/VMOSfiletransferstation/${file.name}",
                                            exportDest
                                        )
                                        statusMessage = if (ok) "Exported ${file.name} to Host" else "Export failed"
                                    }) {
                                        Icon(Icons.Default.Download, contentDescription = "Export to Host", tint = NeonGreen)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
