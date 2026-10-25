package untrusted.vm.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import untrusted.vm.core.DeviceProfile
import untrusted.vm.core.VmInstance
import untrusted.vm.core.XposedPluginManager
import untrusted.vm.ui.theme.*
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceSettingsScreen(
    instance: VmInstance,
    onBack: () -> Unit
) {
    val config by instance.config.collectAsState()
    val profileFile = File(instance.filesystem.rootDir, "device_profile.json")
    val initialProfile = remember {
        if (profileFile.exists()) DeviceProfile.fromJson(profileFile.readText()) else DeviceProfile.PRESET_PIXEL_8
    }

    var manufacturer by remember { mutableStateOf(initialProfile.manufacturer) }
    var model by remember { mutableStateOf(initialProfile.model) }
    var brand by remember { mutableStateOf(initialProfile.brand) }
    var imei by remember { mutableStateOf(initialProfile.imei) }
    var macAddress by remember { mutableStateOf(initialProfile.macAddress) }
    var androidId by remember { mutableStateOf(initialProfile.androidId) }

    val xposedManager = remember { XposedPluginManager(instance.filesystem) }
    var isXposedEnabled by remember { mutableStateOf(xposedManager.isXposedEnabled()) }
    var saveMessage by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Device Identity & Settings", color = TextPrimary, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val newProfile = DeviceProfile(
                            manufacturer = manufacturer,
                            brand = brand,
                            model = model,
                            imei = imei,
                            macAddress = macAddress,
                            androidId = androidId
                        )
                        profileFile.writeText(newProfile.toJson())
                        newProfile.applyToBuildProp(
                            instance.filesystem.resolveGuestFile("/system/build.prop"),
                            config.androidVersion,
                            config.isRootEnabled
                        )
                        xposedManager.setXposedEnabled(isXposedEnabled)
                        saveMessage = "Settings & Device Profile Saved!"
                    }) {
                        Icon(Icons.Default.Save, contentDescription = "Save", tint = NeonCyan)
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (saveMessage.isNotEmpty()) {
                Text(saveMessage, color = NeonGreen, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            }

            // Presets Selection
            Text("DEVICE PRESETS", color = NeonCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        val p = DeviceProfile.PRESET_PIXEL_8
                        manufacturer = p.manufacturer
                        brand = p.brand
                        model = p.model
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceVariant),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Pixel 8", fontSize = 12.sp)
                }

                Button(
                    onClick = {
                        val p = DeviceProfile.PRESET_SAMSUNG_S24
                        manufacturer = p.manufacturer
                        brand = p.brand
                        model = p.model
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceVariant),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Galaxy S24", fontSize = 12.sp)
                }

                Button(
                    onClick = {
                        val p = DeviceProfile.PRESET_XIAOMI_14
                        manufacturer = p.manufacturer
                        brand = p.brand
                        model = p.model
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceVariant),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Xiaomi 14", fontSize = 12.sp)
                }
            }

            // Identity Fields
            Text("HARDWARE ATTRIBUTES", color = NeonCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)

            OutlinedTextField(
                value = manufacturer,
                onValueChange = { manufacturer = it },
                label = { Text("Manufacturer") },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedBorderColor = NeonCyan,
                    unfocusedBorderColor = DarkCardBorder
                )
            )

            OutlinedTextField(
                value = model,
                onValueChange = { model = it },
                label = { Text("Model Name") },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedBorderColor = NeonCyan,
                    unfocusedBorderColor = DarkCardBorder
                )
            )

            // Telephony & Network Spoofing
            Text("TELEPHONY & NETWORK IDENTITY", color = NeonCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)

            OutlinedTextField(
                value = imei,
                onValueChange = { imei = it },
                label = { Text("Virtual IMEI") },
                trailingIcon = {
                    IconButton(onClick = { imei = DeviceProfile.generateRandomImei() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Randomize", tint = NeonCyan)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedBorderColor = NeonCyan,
                    unfocusedBorderColor = DarkCardBorder
                )
            )

            OutlinedTextField(
                value = macAddress,
                onValueChange = { macAddress = it },
                label = { Text("Virtual Wi-Fi MAC") },
                trailingIcon = {
                    IconButton(onClick = { macAddress = DeviceProfile.generateRandomMac() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Randomize", tint = NeonCyan)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedBorderColor = NeonCyan,
                    unfocusedBorderColor = DarkCardBorder
                )
            )

            // Xposed / SandHook Framework Switch
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().border(1.dp, DarkCardBorder, RoundedCornerShape(12.dp))
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Xposed / SandHook Framework", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                        Text("Enables in-memory ART method hooking for guest modules (/data/plugins/)", color = TextMuted, fontSize = 12.sp)
                    }
                    Switch(
                        checked = isXposedEnabled,
                        onCheckedChange = { isXposedEnabled = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = NeonPurple, checkedTrackColor = NeonPurple.copy(alpha = 0.5f))
                    )
                }
            }
        }
    }
}
