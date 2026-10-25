package untrusted.vm.core

import android.content.Context
import android.os.Build
import android.util.Log
import untrusted.vm.rom.RomCatalog
import untrusted.vm.rom.RomDownloader
import untrusted.vm.rom.RomEntry
import untrusted.vm.rom.RomStore
import untrusted.vm.rom.sha256Hex
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean

/**
 * ROM catalog + installation orchestrator (Android layer).
 *
 * A small curated catalog ships in assets/roms_builtin.json; the user can add their own
 * [catalogUrl] (format in docs/rom-catalog.md) or import a ROM file. Every remote ROM is
 * SHA-256 verified; every archive is extracted through hardened code in the `rom` package.
 */
class RomManager(private val context: Context) {

    companion object {
        private const val TAG = "RomManager"
    }

    private val romsDir = File(context.filesDir, "roms").apply { mkdirs() }
    private val downloadsDir = File(context.cacheDir, "rom-downloads").apply { mkdirs() }
    private val catalogUrlFile = File(romsDir, "catalog_url.txt")
    private val catalogCacheFile = File(romsDir, "catalog.json")

    val store = RomStore(romsDir, AndroidFsOps())
    private val downloader = RomDownloader()

    data class CatalogFetch(val entries: List<RomEntry>, val errors: List<String>, val fromCache: Boolean)

    /** Where to fetch the catalog from. Empty until the user sets one. */
    var catalogUrl: String
        get() = try { catalogUrlFile.readText().trim() } catch (_: Exception) { "" }
        set(value) { catalogUrlFile.writeText(value.trim()) }

    /** Curated catalog shipped inside the APK (assets/roms_builtin.json). */
    fun builtinCatalog(): List<RomEntry> = try {
        RomCatalog.parse(context.assets.open("roms_builtin.json").bufferedReader().use { it.readText() }).entries
    } catch (e: Exception) {
        Log.w(TAG, "built-in catalog unreadable", e)
        emptyList()
    }

    /** Built-in entries plus the user's catalog; on an id clash the user's entry wins. */
    private fun merged(user: List<RomEntry>): List<RomEntry> {
        val userIds = user.map { it.id }.toSet()
        return builtinCatalog().filter { it.id !in userIds } + user
    }

    /** Built-in + last cached user catalog. Never touches the network. */
    fun loadCachedCatalog(): List<RomEntry> = merged(
        try { RomCatalog.parse(catalogCacheFile.readText()).entries } catch (_: Exception) { emptyList() }
    )

    /** Blocking; call off the main thread. Falls back to the last good cached catalog on failure. */
    fun refreshCatalog(): CatalogFetch {
        val url = catalogUrl
        if (url.isEmpty()) return CatalogFetch(loadCachedCatalog(), emptyList(), true)
        if (!url.startsWith("https://")) {
            return CatalogFetch(loadCachedCatalog(), listOf("The catalog URL must start with https://"), true)
        }
        return try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 15_000
            conn.readTimeout = 20_000
            conn.setRequestProperty("User-Agent", "UntrustedVM/1.0")
            try {
                if (conn.responseCode != 200) error("HTTP ${conn.responseCode}")
                val text = conn.inputStream.use { input ->
                    val buf = java.io.ByteArrayOutputStream()
                    val chunk = ByteArray(16 * 1024)
                    while (true) {
                        val n = input.read(chunk)
                        if (n < 0) break
                        buf.write(chunk, 0, n)
                        if (buf.size() > RomCatalog.MAX_CATALOG_BYTES) error("catalog is too large")
                    }
                    buf.toString("UTF-8")
                }
                val parsed = RomCatalog.parse(text)
                if (parsed.entries.isNotEmpty()) catalogCacheFile.writeText(text)
                CatalogFetch(merged(parsed.entries), parsed.errors, false)
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            Log.w(TAG, "catalog refresh failed", e)
            CatalogFetch(loadCachedCatalog(), listOf("Could not fetch catalog: ${e.message}"), true)
        }
    }

    /** True if this device can run the ROM's ABI (a 64-bit phone usually also lists armeabi-v7a). */
    fun isCompatible(entry: RomEntry): Boolean = Build.SUPPORTED_ABIS.contains(entry.abi)

    /**
     * Download -> verify -> extract -> register. Returns null on success or a human-readable error.
     * [onStage] receives (stage label, 0..1 progress within the stage). Blocking.
     */
    fun downloadAndInstall(entry: RomEntry, cancel: AtomicBoolean, onStage: (String, Float) -> Unit): String? {
        val archive = File(downloadsDir, "${entry.id}.rom")
        onStage("Downloading", 0f)
        when (val r = downloader.download(
            url = entry.url, dest = archive, expectedSha256 = entry.sha256, expectedSize = entry.sizeBytes,
            cancel = cancel,
            onProgress = { done, total -> onStage("Downloading", if (total > 0) done.toFloat() / total else 0f) }
        )) {
            is RomDownloader.Result.Failure -> return r.message
            RomDownloader.Result.Cancelled -> return "Cancelled (progress kept; start again to resume)"
            is RomDownloader.Result.Success -> Unit
        }
        onStage("Extracting", 0f)
        val meta = RomStore.InstalledRom(
            id = entry.id, name = entry.name, androidVersion = entry.androidVersion, apiLevel = entry.apiLevel,
            abi = entry.abi, rootPreinstalled = entry.rootPreinstalled, hasGms = entry.hasGms,
            sha256 = entry.sha256, source = entry.url
        )
        val result = store.install(meta, archive, { cancel.get() }) { onStage("Extracting", it) }
        archive.delete()
        return (result as? RomStore.InstallResult.Fail)?.reason
    }

    /**
     * Installs a ROM file the user already has (.zip, .img or .img.xz). Metadata comes from the ROM's own
     * system/build.prop. Returns null on success or an error message. Blocking.
     */
    fun installLocalArchive(archive: File, displayName: String, cancel: AtomicBoolean, onStage: (String, Float) -> Unit): String? {
        onStage("Checking file", 0f)
        val sha = sha256Hex(archive)
        val id = "local-" + sha.take(12)
        val provisional = RomStore.InstalledRom(
            id = id, name = displayName.removeSuffix(".xz").removeSuffix(".img").removeSuffix(".zip").ifBlank { id }, androidVersion = "", apiLevel = 0,
            abi = "", rootPreinstalled = false, hasGms = false, sha256 = sha, source = "local file"
        )
        onStage("Extracting", 0f)
        val result = store.install(provisional, archive, { cancel.get() }) { onStage("Extracting", it) }
        if (result is RomStore.InstallResult.Fail) return result.reason

        // Fill metadata from the ROM itself rather than asking the user to type it.
        val prop = store.readBuildProp(id)
        val sdk = prop["ro.build.version.sdk"]?.toIntOrNull() ?: 0
        val filled = provisional.copy(
            androidVersion = prop["ro.build.version.release"] ?: "unknown",
            apiLevel = sdk,
            abi = prop["ro.product.cpu.abi"] ?: "unknown"
        )
        File(romsDir, "$id/meta.json").writeText(filled.toJson())
        return null
    }

    /**
     * Copies an installed ROM into [instance]'s rootfs (the VM must be stopped) and records it
     * as the instance's base ROM. User data already in the instance is preserved. Blocking.
     */
    fun applyToInstance(romId: String, instance: VmInstance, cancel: AtomicBoolean, onStage: (String, Float) -> Unit): String? {
        val rom = store.get(romId) ?: return "ROM is not installed"
        if (instance.state.value != VmState.STOPPED) return "Stop the VM before changing its ROM."
        onStage("Applying ${rom.name}", 0f)
        val ok = store.copyBaseInto(romId, instance.filesystem.rootfsDir, isCancelled = { cancel.get() }) { onStage("Applying ${rom.name}", it) }
        if (!ok) return if (cancel.get()) "Cancelled" else "Failed to copy the ROM into this instance."
        instance.updateConfig(
            instance.config.value
                .withAndroidVersion(rom.androidVersion.ifBlank { instance.config.value.androidVersion })
                .withBaseRom(rom.id)
        )
        return null
    }
}
