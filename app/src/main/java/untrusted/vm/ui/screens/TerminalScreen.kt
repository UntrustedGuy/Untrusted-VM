package untrusted.vm.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import untrusted.vm.core.VmEngine
import untrusted.vm.core.VmInstance
import untrusted.vm.ui.components.RootBadge
import untrusted.vm.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(
    instance: VmInstance,
    onBackClick: () -> Unit
) {
    val config by instance.config.collectAsState()
    val engine = remember(instance) { VmEngine(instance) }
    var inputCommand by remember { mutableStateOf("") }
    val consoleLines = remember {
        mutableStateListOf(
            "Untrusted VM Terminal [Guest Sandbox]",
            "Instance #${instance.id} (${config.name})",
            "Root Option: ${if (config.isRootEnabled) "ENABLED (su active)" else "DISABLED"}",
            "Type commands below or tap test shortcuts to verify root status.",
            "------------------------------------------------"
        )
    }

    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    fun runCmd(cmd: String, asRoot: Boolean = false) {
        if (cmd.isBlank()) return
        val prefix = if (asRoot || config.isRootEnabled) "root@vm# " else "u0_a150@vm$ "
        consoleLines.add(prefix + cmd)
        val result = engine.executeShell(cmd, requestRoot = asRoot)
        for (line in result.output.lines()) {
            consoleLines.add(line)
        }
        coroutineScope.launch {
            listState.animateScrollToItem(consoleLines.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "VM #${instance.id} Console", color = TextPrimary)
                        Spacer(modifier = Modifier.width(8.dp))
                        RootBadge(isRootEnabled = config.isRootEnabled)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                },
                actions = {
                    IconButton(onClick = { consoleLines.clear() }) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = "Clear", tint = TextSecondary)
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
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            // Quick Test Shortcuts Row
            val scrollState = rememberScrollState()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(scrollState)
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val shortcuts = listOf("id", "which su", "su -v", "getprop", "uname -a", "ls -la /system/xbin")
                for (sc in shortcuts) {
                    SuggestionChip(
                        onClick = { runCmd(sc, asRoot = false) },
                        label = { Text(sc, fontFamily = FontFamily.Monospace, fontSize = 11.sp) },
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = DarkSurfaceVariant,
                            labelColor = NeonCyan
                        ),
                        border = SuggestionChipDefaults.suggestionChipBorder(
                            enabled = true,
                            borderColor = DarkCardBorder
                        )
                    )
                }
            }

            // Terminal Console Output Area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color(0xFF04060A), RoundedCornerShape(12.dp))
                    .border(1.dp, DarkCardBorder, RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(consoleLines) { line ->
                        val textColor = when {
                            line.startsWith("root@") -> NeonGreen
                            line.startsWith("u0_a") -> NeonCyan
                            line.startsWith("Error:") || line.startsWith("Permission denied") -> NeonRed
                            line.startsWith("Untrusted VM") -> NeonPurple
                            else -> TextPrimary
                        }
                        Text(
                            text = line,
                            color = textColor,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Command Input Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = inputCommand,
                    onValueChange = { inputCommand = it },
                    placeholder = { Text("Enter guest command (e.g. su, id)", color = TextMuted) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonCyan,
                        unfocusedBorderColor = DarkCardBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = {
                        val cmd = inputCommand.trim()
                        if (cmd.isNotEmpty()) {
                            runCmd(cmd)
                            inputCommand = ""
                        }
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .background(NeonCyan, RoundedCornerShape(12.dp))
                ) {
                    Icon(Icons.Default.Send, contentDescription = "Run", tint = DarkBackground)
                }
            }
        }
    }
}
