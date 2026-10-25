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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import untrusted.vm.core.SuPolicyManager
import untrusted.vm.core.VmInstance
import untrusted.vm.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SuManagementScreen(
    instance: VmInstance,
    onBack: () -> Unit
) {
    val suPolicyManager = instance.suPolicyManager
    val config by instance.config.collectAsState()
    var selectedTab by remember { mutableStateOf(0) }
    var policies by remember { mutableStateOf(suPolicyManager.allPolicies) }
    var logs by remember { mutableStateOf(suPolicyManager.recentLogs) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Superuser Manager", color = TextPrimary, fontWeight = FontWeight.Bold)
                        Text(
                            "${config.name} (${if (config.isRootEnabled) "Root ACTIVE" else "Root DISABLED"})",
                            color = if (config.isRootEnabled) NeonGreen else TextMuted,
                            fontSize = 12.sp
                        )
                    }
                },
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
                    text = { Text("App Permissions (${policies.size})") }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = {
                        logs = suPolicyManager.recentLogs
                        selectedTab = 1
                    },
                    text = { Text("Audit Log (${logs.size})") }
                )
            }

            if (selectedTab == 0) {
                if (policies.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            "No applications have requested Superuser permissions yet.",
                            color = TextMuted,
                            fontSize = 14.sp
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(policies.entries.toList()) { entry ->
                            val packageName = entry.key
                            val policy = entry.value

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
                                        Text(packageName, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                                        Text(
                                            "Policy: ${policy.name}",
                                            color = when (policy) {
                                                SuPolicyManager.Policy.ALLOW -> NeonGreen
                                                SuPolicyManager.Policy.DENY -> NeonRed
                                                SuPolicyManager.Policy.PROMPT -> NeonCyan
                                            },
                                            fontSize = 12.sp
                                        )
                                    }

                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(
                                            onClick = {
                                                suPolicyManager.setPolicy(packageName, SuPolicyManager.Policy.ALLOW)
                                                policies = suPolicyManager.allPolicies
                                            },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = if (policy == SuPolicyManager.Policy.ALLOW) NeonGreen else DarkSurfaceVariant
                                            ),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text("Allow", fontSize = 11.sp, color = if (policy == SuPolicyManager.Policy.ALLOW) DarkBackground else TextPrimary)
                                        }

                                        Button(
                                            onClick = {
                                                suPolicyManager.setPolicy(packageName, SuPolicyManager.Policy.DENY)
                                                policies = suPolicyManager.allPolicies
                                            },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = if (policy == SuPolicyManager.Policy.DENY) NeonRed else DarkSurfaceVariant
                                            ),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text("Deny", fontSize = 11.sp, color = TextPrimary)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                if (logs.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Audit log is empty.", color = TextMuted)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(logs) { log ->
                            val dateStr = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(log.timestamp))
                            Card(
                                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth().border(1.dp, DarkCardBorder, RoundedCornerShape(8.dp))
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(log.packageName, color = TextPrimary, fontWeight = FontWeight.Medium, fontSize = 13.sp)
                                        Text(
                                            "Action: ${log.action} | Time: $dateStr",
                                            color = if (log.action.contains("ALLOW")) NeonGreen else NeonRed,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 11.sp
                                        )
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
