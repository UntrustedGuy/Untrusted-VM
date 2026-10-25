package untrusted.vm.ui.screens

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import untrusted.vm.core.VmInstance
import untrusted.vm.core.VmManager
import untrusted.vm.core.VmState
import untrusted.vm.rom.RomStore
import untrusted.vm.ui.theme.*
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * ROM Store & Image Manager.
 *  - Catalog: the user supplies an https catalog URL (see docs/rom-catalog.md); entries are
 *    downloaded with resume and SHA-256 verification.
 *  - Local import: any ROM zip (a zip of the file tree) from device storage.
 *  - Installed ROMs can be applied to this VM (which must be stopped) or deleted.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RomManagerScreen(
    instance: VmInstance,
    vmManager: VmManager,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val romManager = vmManager.romManager
    val scope = rememberCoroutineScope()
    val cancel = remember { AtomicBoolean(false) }
    DisposableEffect(Unit) { onDispose { cancel.set(true) } }

    val vmState by instance.state.collectAsState()
    val vmConfig by instance.config.collectAsState()

    var catalogUrl by remember { mutableStateOf(romManager.catalogUrl) }
    var catalog by remember { mutableStateOf(romManager.loadCachedCatalog()) }
    var installed by remember { mutableStateOf(romManager.store.list()) }
    var busy by remember { mutableStateOf(false) }
    var stage by remember { mutableStateOf("") }
    var progress by remember { mutableStateOf(0f) }
    var status by remember { mutableStateOf("") }
    var statusIsError by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<RomStore.InstalledRom?>(null) }

    // Runs [block] on a worker thread; it returns null on success or an error message.
    fun runTask(successMessage: String, block: () -> String?) {
        if (busy) return
        busy = true
        cancel.set(false)
        status = ""
        scope.launch(Dispatchers.IO) {
            val error = try { block() } catch (e: Exception) { e.message ?: "Unexpected error" }
            installed = romManager.store.list()
            statusIsError = error != null
            status = error ?: successMessage
            stage = ""
            progress = 0f
            busy = false
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val name = displayNameOf(context, uri)
            runTask("Imported $name") {
                val tmp = File(context.cacheDir, "import-${System.nanoTime()}.zip")
                try {
                    stage = "Copying file"
                    val input = context.contentResolver.openInputStream(uri) ?: return@runTask "Cannot read that file."
                    input.use { i -> tmp.outputStream().use { o -> i.copyTo(o) } }
                    romManager.installLocalArchive(tmp, name) { s, p -> stage = s; progress = p }
                } finally {
                    tmp.delete()
                }
            }
        }
    }

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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (status.isNotEmpty()) {
                val tint = if (statusIsError) NeonRed else NeonGreen
                Surface(color = tint.copy(alpha = 0.15f), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
                    Text(status, color = tint, fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(10.dp))
                }
            }

            if (busy) {
                RomCard {
                    Text(if (stage.isEmpty()) "Working..." else stage, color = TextPrimary, fontSize = 13.sp)
                    LinearProgressIndicator(progress = progress.coerceIn(0f, 1f), modifier = Modifier.fillMaxWidth())
                    OutlinedButton(
                        onClick = { cancel.set(true) },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonAmber)
                    ) { Text("Cancel (downloads can resume later)") }
                }
            }

            Text(
                "This VM: Android ${vmConfig.androidVersion}" +
                    if (vmConfig.baseRomId.isNotEmpty()) " • ROM ${vmConfig.baseRomId}" else " • no ROM applied",
                color = TextSecondary, fontSize = 12.sp
            )

            // ---- Catalog ----
            Text("ROM Catalog", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
            RomCard {
                Text(
                    "Paste the https URL of a ROM catalog you trust. Nothing is built in, and every download is checksum-verified.",
                    color = TextSecondary, fontSize = 12.sp
                )
                OutlinedTextField(
                    value = catalogUrl,
                    onValueChange = { catalogUrl = it },
                    label = { Text("Catalog URL (https://...)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )
                Button(
                    onClick = {
                        romManager.catalogUrl = catalogUrl
                        runTask("Catalog updated") {
                            val r = romManager.refreshCatalog()
                            catalog = r.entries
                            when {
                                r.fromCache && r.errors.isNotEmpty() -> r.errors.first()
                                r.errors.isNotEmpty() -> "Loaded ${r.entries.size} ROMs; skipped ${r.errors.size} invalid: ${r.errors.first()}"
                                else -> null
                            }
                        }
                    },
                    enabled = !busy,
                    colors = ButtonDefaults.buttonColors(containerColor = NeonViolet),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Save & Refresh Catalog") }
            }

            val compatible = catalog.filter { romManager.isCompatible(it) }
            if (catalog.isNotEmpty() && compatible.size < catalog.size) {
                Text("${catalog.size - compatible.size} catalog ROM(s) hidden: built for a different CPU.", color = TextMuted, fontSize = 11.sp)
            }
            compatible.forEach { entry ->
                val already = installed.any { it.id == entry.id }
                RomCard {
                    RomHeader(entry.name, "Android ${entry.androidVersion} • API ${entry.apiLevel} • ${entry.abi}", formatBytes(entry.sizeBytes))
                    if (entry.description.isNotBlank()) Text(entry.description, color = TextSecondary, fontSize = 12.sp)
                    Badges(entry.rootPreinstalled, entry.hasGms)
                    Button(
                        onClick = {
                            runTask("Installed ${entry.name}") {
                                romManager.downloadAndInstall(entry, cancel) { s, p -> stage = s; progress = p }
                            }
                        },
                        enabled = !busy,
                        colors = ButtonDefaults.buttonColors(containerColor = NeonPurple),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.CloudDownload, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (already) "Re-download & Reinstall" else "Download & Install")
                    }
                }
            }

            // ---- Installed ----
            Text("Installed ROMs", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
            if (installed.isEmpty()) {
                Text("Nothing installed yet. Download one from a catalog or import a ROM zip below.", color = TextSecondary, fontSize = 12.sp)
            }
            installed.forEach { rom ->
                RomCard {
                    RomHeader(rom.name, "Android ${rom.androidVersion.ifBlank { "?" }} • API ${rom.apiLevel} • ${rom.abi.ifBlank { "?" }}", rom.source.take(24))
                    Badges(rom.rootPreinstalled, rom.hasGms)
                    if (vmState != VmState.STOPPED) {
                        Text("Stop this VM to apply a ROM.", color = NeonAmber, fontSize = 11.sp)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = {
                                runTask("Applied ${rom.name} to this VM") {
                                    romManager.applyToInstance(rom.id, instance) { s, p -> stage = s; progress = p }
                                }
                            },
                            enabled = !busy && vmState == VmState.STOPPED,
                            colors = ButtonDefaults.buttonColors(containerColor = NeonGreen.copy(alpha = 0.85f)),
                            modifier = Modifier.weight(1f)
                        ) { Text("Apply to this VM", color = DarkBackground) }
                        OutlinedButton(
                            onClick = { pendingDelete = rom },
                            enabled = !busy,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonRed)
                        ) { Text("Delete") }
                    }
                }
            }

            // ---- Local import ----
            RomCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null, tint = NeonCyan)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Import a ROM zip", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                }
                Text(
                    "A zip of the ROM's file tree (system/, vendor/, ...). Raw .img files can't be used without root. " +
                        "Free space needed is roughly 2-3x the zip size while importing.",
                    color = TextSecondary, fontSize = 12.sp
                )
                OutlinedButton(
                    onClick = { picker.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")) },
                    enabled = !busy,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonCyan),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Choose ROM zip...") }
            }
        }
    }

    pendingDelete?.let { rom ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete ${rom.name}?") },
            text = { Text("Removes the downloaded ROM from storage. VMs that already applied it keep their copy.") },
            confirmButton = {
                TextButton(onClick = {
                    pendingDelete = null
                    runTask("Deleted ${rom.name}") {
                        if (romManager.store.remove(rom.id)) null else "Could not delete ${rom.name}."
                    }
                }) { Text("Delete", color = NeonRed) }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Keep") } }
        )
    }
}

@Composable
private fun RomCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, DarkCardBorder, RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = DarkSurface)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

@Composable
private fun RomHeader(title: String, subtitle: String, trailing: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            Text(subtitle, color = NeonCyan, fontSize = 11.sp)
        }
        Text(trailing, color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun Badges(rootPreinstalled: Boolean, hasGms: Boolean) {
    if (!rootPreinstalled && !hasGms) return
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (rootPreinstalled) {
            Surface(color = NeonGreen.copy(alpha = 0.15f), shape = RoundedCornerShape(4.dp)) {
                Text("Pre-Rooted", color = NeonGreen, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
            }
        }
        if (hasGms) {
            Surface(color = NeonCyan.copy(alpha = 0.15f), shape = RoundedCornerShape(4.dp)) {
                Text("GMS Included", color = NeonCyan, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
            }
        }
    }
}

private fun formatBytes(b: Long): String = when {
    b >= 1L shl 30 -> "%.1f GB".format(b / (1024.0 * 1024 * 1024))
    b >= 1L shl 20 -> "%.0f MB".format(b / (1024.0 * 1024))
    b > 0 -> "%d KB".format(b / 1024)
    else -> ""
}

private fun displayNameOf(context: Context, uri: Uri): String {
    val name: String? = try {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    } catch (_: Exception) {
        null
    }
    return name ?: "local-rom.zip"
}
