package untrusted.vm.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import untrusted.vm.ui.components.RootBadge
import untrusted.vm.ui.theme.*

@Composable
fun CreateVmDialog(
    onDismiss: () -> Unit,
    onCreate: (name: String, androidVersion: String, isRootEnabled: Boolean, ramMb: Int) -> Unit
) {
    var name by remember { mutableStateOf("Untrusted VM") }
    var selectedVersion by remember { mutableStateOf("7.1.2") }
    var isRootEnabled by remember { mutableStateOf(true) }
    var ramMb by remember { mutableStateOf(2048f) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = DarkSurface,
            border = androidx.compose.foundation.BorderStroke(1.dp, DarkCardBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Text(
                    text = "Create Virtual Machine",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary
                )
                Text(
                    text = "Configure isolated guest environment",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Name Input
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("VM Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonCyan,
                        unfocusedBorderColor = DarkCardBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Android ROM Version Selector
                Text(
                    text = "Guest Android OS Version & Edition",
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val versions = listOf(
                        Pair("7.1 Geek", "7.1.2"),
                        Pair("9.0 Pure", "9.0.0"),
                        Pair("10.0 Pro", "10.0.0"),
                        Pair("12.0 GMS", "12.0.0")
                    )
                    for ((label, ver) in versions) {
                        val isSelected = selectedVersion == ver
                        OutlinedButton(
                            onClick = { selectedVersion = ver },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (isSelected) NeonCyan.copy(alpha = 0.15f) else DarkSurfaceVariant,
                                contentColor = if (isSelected) NeonCyan else TextSecondary
                            ),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isSelected) NeonCyan else DarkCardBorder
                            )
                        ) {
                            Text(label, fontSize = 11.sp, maxLines = 1)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Root Option Toggle (VMOS Pro inspired)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DarkSurfaceVariant, RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Simulated Root Access",
                                color = TextPrimary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            RootBadge(isRootEnabled = isRootEnabled)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Provisions simulated guest /system/xbin/su without host root or Shizuku.",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                    }
                    Switch(
                        checked = isRootEnabled,
                        onCheckedChange = { isRootEnabled = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = DarkBackground,
                            checkedTrackColor = NeonGreen
                        )
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // RAM Allocation
                Text(
                    text = "Allocated RAM: ${ramMb.toInt()} MB",
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                Slider(
                    value = ramMb,
                    onValueChange = { ramMb = it },
                    valueRange = 1024f..6144f,
                    steps = 4,
                    colors = SliderDefaults.colors(
                        thumbColor = NeonCyan,
                        activeTrackColor = NeonCyan,
                        inactiveTrackColor = DarkCardBorder
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Dialog Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = TextSecondary)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            onCreate(name, selectedVersion, isRootEnabled, ramMb.toInt())
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NeonCyan)
                    ) {
                        Text("Create VM", color = DarkBackground, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
