package untrusted.vm.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import untrusted.vm.core.GpsPenetrator
import untrusted.vm.core.VmInstance
import untrusted.vm.ui.theme.*

/**
 * GPS Location Penetrator & Step Counter Simulation Screen.
 * Equivalent to VMOS Pro's GpsPenetrator / LocationManagerUtil / DialogSettingsStepScaleNumBinding.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationSettingsScreen(
    instance: VmInstance,
    onBack: () -> Unit
) {
    val scrollState = rememberScrollState()
    val gpsPenetrator = instance.gpsPenetrator
    val stepSim = instance.stepSimulator

    var isPassThrough by remember { mutableStateOf(gpsPenetrator?.isPassThroughEnabled == true) }
    var isMockLocation by remember { mutableStateOf(gpsPenetrator?.isMockEnabled == true) }
    var latitudeInput by remember { mutableStateOf(gpsPenetrator?.mockLatitude?.toString() ?: "37.7749") }
    var longitudeInput by remember { mutableStateOf(gpsPenetrator?.mockLongitude?.toString() ?: "-122.4194") }

    var isStepSimRunning by remember { mutableStateOf(false) }
    var stepMultiplier by remember { mutableStateOf(stepSim.stepMultiplier) }
    var currentSteps by remember { mutableStateOf(stepSim.currentSteps) }

    var statusMessage by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Location & Sensor Simulation", color = TextPrimary) },
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

            // Real Host GPS Pass-Through
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, if (isPassThrough) NeonGreen else DarkCardBorder, RoundedCornerShape(16.dp)),
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
                            Icon(Icons.Default.MyLocation, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Real GPS Pass-Through", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Streams your host device's actual GPS satellites & coordinates to the guest VM via NMEA sentences.", color = TextSecondary, fontSize = 12.sp)
                    }
                    Switch(
                        checked = isPassThrough,
                        onCheckedChange = { enable ->
                            isPassThrough = enable
                            if (enable) {
                                isMockLocation = false
                                gpsPenetrator?.enablePassThrough()
                                statusMessage = "GPS Pass-through active (syncing host location)"
                            } else {
                                gpsPenetrator?.disablePassThrough()
                                statusMessage = "GPS Pass-through disabled"
                            }
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = DarkBackground, checkedTrackColor = NeonGreen)
                    )
                }
            }

            // Mock / Virtual Location Spoofing
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, if (isMockLocation) NeonPurple else DarkCardBorder, RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = DarkSurface)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.LocationOn, contentDescription = null, tint = NeonPurple, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Mock Location Spoofer", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
                        }
                        Switch(
                            checked = isMockLocation,
                            onCheckedChange = { enable ->
                                isMockLocation = enable
                                if (enable) {
                                    isPassThrough = false
                                    val lat = latitudeInput.toDoubleOrNull() ?: 37.7749
                                    val lng = longitudeInput.toDoubleOrNull() ?: -122.4194
                                    gpsPenetrator?.enableMockLocation(lat, lng)
                                    statusMessage = "Mock GPS coordinates active: ($lat, $lng)"
                                } else {
                                    gpsPenetrator?.disableMockLocation()
                                    statusMessage = "Mock GPS disabled"
                                }
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = DarkBackground, checkedTrackColor = NeonPurple)
                        )
                    }

                    Text("Preset Cities:", color = TextSecondary, fontSize = 12.sp)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        val presets = listOf("Tokyo", "London", "Paris", "San Francisco")
                        presets.forEach { city ->
                            val coords = GpsPenetrator.PRESET_LOCATIONS[city] ?: Pair(0.0, 0.0)
                            OutlinedButton(
                                onClick = {
                                    latitudeInput = coords.first.toString()
                                    longitudeInput = coords.second.toString()
                                    if (isMockLocation) {
                                        gpsPenetrator?.enableMockLocation(coords.first, coords.second)
                                    }
                                    statusMessage = "Coordinates loaded: $city"
                                },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonCyan)
                            ) {
                                Text(city, fontSize = 10.sp, maxLines = 1)
                            }
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = latitudeInput,
                            onValueChange = { latitudeInput = it },
                            label = { Text("Latitude") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = NeonPurple,
                                unfocusedBorderColor = DarkCardBorder,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            )
                        )

                        OutlinedTextField(
                            value = longitudeInput,
                            onValueChange = { longitudeInput = it },
                            label = { Text("Longitude") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = NeonPurple,
                                unfocusedBorderColor = DarkCardBorder,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            )
                        )
                    }
                }
            }

            // Step Counter / Pedometer Simulator
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, if (isStepSimRunning) NeonAmber else DarkCardBorder, RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = DarkSurface)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.DirectionsWalk, contentDescription = null, tint = NeonAmber, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Step Counter Spoofer", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
                        }
                        Switch(
                            checked = isStepSimRunning,
                            onCheckedChange = { enable ->
                                isStepSimRunning = enable
                                if (enable) {
                                    stepSim.start(stepMultiplier)
                                    statusMessage = "Step simulator running at ${stepMultiplier}x speed"
                                } else {
                                    stepSim.stop()
                                    statusMessage = "Step simulator stopped"
                                }
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = DarkBackground, checkedTrackColor = NeonAmber)
                        )
                    }

                    Text("Simulates periodic walking steps into the guest sensor sysfs for fitness & tracking apps.", color = TextSecondary, fontSize = 12.sp)

                    Text("Speed Multiplier:", color = TextSecondary, fontSize = 12.sp)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(1, 2, 5, 10).forEach { mul ->
                            val isSelected = stepMultiplier == mul
                            OutlinedButton(
                                onClick = {
                                    stepMultiplier = mul
                                    stepSim.setMultiplier(mul)
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (isSelected) NeonAmber.copy(alpha = 0.15f) else DarkSurfaceVariant,
                                    contentColor = if (isSelected) NeonAmber else TextSecondary
                                )
                            ) {
                                Text("${mul}x", fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            stepSim.resetSteps()
                            currentSteps = 0L
                            statusMessage = "Step counter reset to 0"
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Reset Step Count")
                    }
                }
            }
        }
    }
}
