package untrusted.vm.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import untrusted.vm.core.VmInstance
import untrusted.vm.ui.theme.*

/**
 * Display, Resolution & Refresh Rate Settings Screen.
 * Equivalent to VMOS Pro's DialogSettingsResolutionBinding & DialogSettingsFpsBinding.
 * Configures:
 * 1. Standard Resolution Presets (720x1280 HD, 1080x1920 FHD, 1440x2560 2K, 1080x2400 FHD+)
 * 2. Custom Resolution (Width x Height x DPI)
 * 3. Refresh Rate Switcher (30Hz, 60Hz, 90Hz, 120Hz)
 * 4. Immersive Fullscreen / Cutout Notch handling
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DisplaySettingsScreen(
    instance: VmInstance,
    onBack: () -> Unit
) {
    val config by instance.config.collectAsState()
    val scrollState = rememberScrollState()

    var customWidth by remember { mutableStateOf(config.resolutionWidth.toString()) }
    var customHeight by remember { mutableStateOf(config.resolutionHeight.toString()) }
    var customDpi by remember { mutableStateOf(config.dpi.toString()) }

    var selectedFps by remember { mutableStateOf(60) }
    var isCutoutImmersion by remember { mutableStateOf(true) }
    var saveStatus by remember { mutableStateOf("") }

    val presetResolutions = listOf(
        Triple("720x1280", 720, 1280),
        Triple("1080x1920", 1080, 1920),
        Triple("1080x2400", 1080, 2400),
        Triple("1440x2560", 1440, 2560)
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Display & Refresh Rate", color = TextPrimary) },
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
            if (saveStatus.isNotEmpty()) {
                Surface(
                    color = NeonGreen.copy(alpha = 0.15f),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        saveStatus,
                        color = NeonGreen,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

            // Resolution Presets
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, DarkCardBorder, RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = DarkSurface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Standard Resolution Presets", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        presetResolutions.take(2).forEach { (label, w, h) ->
                            val isSelected = config.resolutionWidth == w && config.resolutionHeight == h
                            OutlinedButton(
                                onClick = {
                                    customWidth = w.toString()
                                    customHeight = h.toString()
                                    instance.updateConfig(config.withResolution(w, h))
                                    saveStatus = "Resolution updated to $label"
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (isSelected) NeonCyan.copy(alpha = 0.15f) else DarkSurfaceVariant,
                                    contentColor = if (isSelected) NeonCyan else TextSecondary
                                )
                            ) {
                                Text(label, fontSize = 12.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        presetResolutions.drop(2).forEach { (label, w, h) ->
                            val isSelected = config.resolutionWidth == w && config.resolutionHeight == h
                            OutlinedButton(
                                onClick = {
                                    customWidth = w.toString()
                                    customHeight = h.toString()
                                    instance.updateConfig(config.withResolution(w, h))
                                    saveStatus = "Resolution updated to $label"
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (isSelected) NeonCyan.copy(alpha = 0.15f) else DarkSurfaceVariant,
                                    contentColor = if (isSelected) NeonCyan else TextSecondary
                                )
                            ) {
                                Text(label, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            // Custom Resolution & DPI
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, DarkCardBorder, RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = DarkSurface)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Custom Resolution & Density", style = MaterialTheme.typography.titleLarge, color = TextPrimary)

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = customWidth,
                            onValueChange = { customWidth = it },
                            label = { Text("Width (px)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = NeonCyan,
                                unfocusedBorderColor = DarkCardBorder,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            )
                        )

                        OutlinedTextField(
                            value = customHeight,
                            onValueChange = { customHeight = it },
                            label = { Text("Height (px)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = NeonCyan,
                                unfocusedBorderColor = DarkCardBorder,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            )
                        )
                    }

                    OutlinedTextField(
                        value = customDpi,
                        onValueChange = { customDpi = it },
                        label = { Text("Screen Density (DPI, e.g. 320, 420, 480)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonCyan,
                            unfocusedBorderColor = DarkCardBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )

                    Button(
                        onClick = {
                            val w = customWidth.toIntOrNull() ?: config.resolutionWidth
                            val h = customHeight.toIntOrNull() ?: config.resolutionHeight
                            val d = customDpi.toIntOrNull() ?: config.dpi
                            instance.updateConfig(config.withResolution(w, h).withDpi(d))
                            saveStatus = "Applied custom resolution: ${w}x${h} @ ${d} DPI"
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = DarkBackground)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Apply Custom Resolution", color = DarkBackground, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Refresh Rate (FPS)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, DarkCardBorder, RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = DarkSurface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Refresh Rate (FPS)", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Controls guest display frame pacing and maximum rendering frequency.", color = TextSecondary, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val fpsOptions = listOf(30, 60, 90, 120)
                        fpsOptions.forEach { fps ->
                            val isSelected = selectedFps == fps
                            OutlinedButton(
                                onClick = {
                                    selectedFps = fps
                                    saveStatus = "Target refresh rate set to ${fps}Hz"
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (isSelected) NeonGreen.copy(alpha = 0.15f) else DarkSurfaceVariant,
                                    contentColor = if (isSelected) NeonGreen else TextSecondary
                                )
                            ) {
                                Text("${fps}Hz", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Cutout & Immersion Mode
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, DarkCardBorder, RoundedCornerShape(16.dp)),
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
                        Text("Display Cutout Immersion", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Render around display camera notches and cutouts (short edge mode)", color = TextSecondary, fontSize = 12.sp)
                    }
                    Switch(
                        checked = isCutoutImmersion,
                        onCheckedChange = { isCutoutImmersion = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = DarkBackground,
                            checkedTrackColor = NeonCyan
                        )
                    )
                }
            }
        }
    }
}
