package untrusted.vm.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AirplanemodeActive
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import untrusted.vm.core.VmInstance
import untrusted.vm.core.VmNetworkManager
import untrusted.vm.ui.theme.*

/**
 * VM Network, Virtual ADB & Proxy Settings Screen.
 * Equivalent to VMOS Pro's DialogSettingsModifyProxyBinding & persist.adb.tcp.port bridge.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetworkSettingsScreen(
    instance: VmInstance,
    onBack: () -> Unit
) {
    val scrollState = rememberScrollState()
    val netManager = instance.networkManager

    var isAdbEnabled by remember { mutableStateOf(true) }
    var adbPort by remember { mutableStateOf(VmNetworkManager.DEFAULT_ADB_PORT.toString()) }

    var isProxyEnabled by remember { mutableStateOf(netManager.proxyConfig.enabled) }
    var proxyType by remember { mutableStateOf(netManager.proxyConfig.type) }
    var proxyHost by remember { mutableStateOf(netManager.proxyConfig.host) }
    var proxyPort by remember { mutableStateOf(netManager.proxyConfig.port.toString()) }

    var isOfflineMode by remember { mutableStateOf(netManager.isOfflineMode) }
    var statusMessage by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Network & Virtual ADB", color = TextPrimary) },
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
            if (statusMessage.isNotEmpty()) {
                Surface(
                    color = NeonGreen.copy(alpha = 0.15f),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        statusMessage,
                        color = NeonGreen,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

            // Virtual ADB Server / TCP Bridge
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, if (isAdbEnabled) NeonCyan else DarkCardBorder, RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = DarkSurface)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Code, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Virtual ADB TCP Bridge", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
                        }
                        Switch(
                            checked = isAdbEnabled,
                            onCheckedChange = { enable ->
                                isAdbEnabled = enable
                                val port = adbPort.toIntOrNull() ?: VmNetworkManager.DEFAULT_ADB_PORT
                                if (enable) {
                                    netManager.startAdbBridge(port)
                                    statusMessage = "Virtual ADB listening on 127.0.0.1:$port"
                                } else {
                                    netManager.stopAdbBridge()
                                    statusMessage = "Virtual ADB stopped"
                                }
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = DarkBackground, checkedTrackColor = NeonCyan)
                        )
                    }

                    Text("Enables internal ADB debugging. Connect via your terminal or host shell using:\n'adb connect 127.0.0.1:$adbPort'", color = TextSecondary, fontSize = 12.sp)

                    OutlinedTextField(
                        value = adbPort,
                        onValueChange = { adbPort = it },
                        label = { Text("ADB Port (Default: 5555)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonCyan,
                            unfocusedBorderColor = DarkCardBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )
                }
            }

            // Proxy Configuration
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, if (isProxyEnabled) NeonPurple else DarkCardBorder, RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = DarkSurface)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.VpnKey, contentDescription = null, tint = NeonPurple, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Network Proxy", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
                        }
                        Switch(
                            checked = isProxyEnabled,
                            onCheckedChange = { isProxyEnabled = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = DarkBackground, checkedTrackColor = NeonPurple)
                        )
                    }

                    Text("Proxy Type:", color = TextSecondary, fontSize = 12.sp)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("HTTP", "SOCKS5").forEach { type ->
                            val isSelected = proxyType.equals(type, ignoreCase = true)
                            OutlinedButton(
                                onClick = { proxyType = type },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (isSelected) NeonPurple.copy(alpha = 0.15f) else DarkSurfaceVariant,
                                    contentColor = if (isSelected) NeonPurple else TextSecondary
                                )
                            ) {
                                Text(type, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = proxyHost,
                            onValueChange = { proxyHost = it },
                            label = { Text("Proxy Host / IP") },
                            placeholder = { Text("192.168.1.100") },
                            modifier = Modifier.weight(2f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = NeonPurple,
                                unfocusedBorderColor = DarkCardBorder,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            )
                        )

                        OutlinedTextField(
                            value = proxyPort,
                            onValueChange = { proxyPort = it },
                            label = { Text("Port") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = NeonPurple,
                                unfocusedBorderColor = DarkCardBorder,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            )
                        )
                    }

                    Button(
                        onClick = {
                            val port = proxyPort.toIntOrNull() ?: 8080
                            val cfg = VmNetworkManager.ProxyConfig(
                                enabled = isProxyEnabled,
                                type = proxyType,
                                host = proxyHost,
                                port = port
                            )
                            netManager.updateProxy(cfg)
                            statusMessage = if (isProxyEnabled) "Proxy configuration saved: $proxyType $proxyHost:$port" else "Proxy disabled"
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NeonPurple),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = TextPrimary)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Save Proxy Settings")
                    }
                }
            }

            // Offline Isolation Mode
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, if (isOfflineMode) NeonRed else DarkCardBorder, RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = DarkSurface)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AirplanemodeActive, contentDescription = null, tint = NeonRed, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Offline / Airplane Mode", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Cuts off all guest outbound sockets and network interfaces for strict privacy and isolation.", color = TextSecondary, fontSize = 12.sp)
                    }
                    Switch(
                        checked = isOfflineMode,
                        onCheckedChange = { enable ->
                            isOfflineMode = enable
                            netManager.setOfflineMode(enable)
                            statusMessage = if (enable) "Guest network isolated (Airplane Mode)" else "Guest network re-enabled"
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = DarkBackground, checkedTrackColor = NeonRed)
                    )
                }
            }
        }
    }
}
