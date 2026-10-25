package untrusted.vm.rom

import org.json.JSONObject
import java.io.File

/**
 * On-disk registry of installed base ROMs:  <romsDir>/<id>/{base/, meta.json}
 * Installs are atomic: extraction happens in a staging dir that is renamed into place only
 * after it fully succeeds, so a crash or bad archive never leaves a half-installed ROM.
 */
class RomStore(
    private val romsDir: File,
    private val fsOps: FsOps,
    private val tmpDir: File = File(romsDir, ".tmp")
) {

    data class InstalledRom(
        val id: String,
        val name: String,
        val androidVersion: String,
        val apiLevel: Int,
        val abi: String,
        val rootPreinstalled: Boolean,
        val hasGms: Boolean,
        val sha256: String,
        val source: String,
        val installedAt: Long = System.currentTimeMillis()
    ) {
        fun toJson(): String = JSONObject().apply {
            put("id", id); put("name", name); put("androidVersion", androidVersion)
            put("apiLevel", apiLevel); put("abi", abi); put("rootPreinstalled", rootPreinstalled)
            put("hasGms", hasGms); put("sha256", sha256); put("source", source)
            put("installedAt", installedAt)
        }.toString()

        companion object {
            fun fromJson(s: String): InstalledRom {
                val o = JSONObject(s)
                return InstalledRom(
                    id = o.getString("id"),
                    name = o.optString("name", o.getString("id")),
                    androidVersion = o.optString("androidVersion", ""),
                    apiLevel = o.optInt("apiLevel", 0),
                    abi = o.optString("abi", ""),
                    rootPreinstalled = o.optBoolean("rootPreinstalled", false),
                    hasGms = o.optBoolean("hasGms", false),
                    sha256 = o.optString("sha256", ""),
                    source = o.optString("source", ""),
                    installedAt = o.optLong("installedAt", 0L)
                )
            }
        }
    }

    sealed class InstallResult {
        data class Ok(val rom: InstalledRom, val files: Int, val bytes: Long) : InstallResult()
        data class Fail(val reason: String) : InstallResult()
    }

    init {
        romsDir.mkdirs()
        SafePaths.deleteTree(tmpDir) // leftovers from a crashed install
    }

    private fun dirOf(id: String): File {
        require(RomCatalog.isValidId(id)) { "invalid rom id: $id" }
        return File(romsDir, id)
    }

    fun baseDir(id: String): File = File(dirOf(id), "base")

    fun list(): List<InstalledRom> =
        (romsDir.listFiles { f -> f.isDirectory && !f.name.startsWith(".") } ?: emptyArray())
            .mapNotNull { dir ->
                try { InstalledRom.fromJson(File(dir, "meta.json").readText()) } catch (_: Exception) { null }
            }
            .sortedBy { it.name.lowercase() }

    fun get(id: String): InstalledRom? = list().firstOrNull { it.id == id }

    fun remove(id: String): Boolean = dirOf(id).let { !it.exists() || SafePaths.deleteTree(it) }

    /** Reads the handful of build.prop keys we care about from an installed ROM. */
    fun readBuildProp(id: String): Map<String, String> {
        val f = File(baseDir(id), "system/build.prop")
        if (!f.isFile) return emptyMap()
        return f.readLines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") && it.contains('=') }
            .associate { it.substringBefore('=').trim() to it.substringAfter('=').trim() }
    }

    fun install(
        meta: InstalledRom,
        archive: File,
        isCancelled: () -> Boolean = { false },
        onProgress: (Float) -> Unit = {}
    ): InstallResult {
        if (!RomCatalog.isValidId(meta.id)) return InstallResult.Fail("invalid rom id")
        val staging = File(romsDir, ".staging-${meta.id}-${System.nanoTime()}")
        try {
            staging.mkdirs()
            val outcome = RomExtractor.extractAny(
                archive, File(staging, "base"), tmpDir, fsOps,
                isCancelled = isCancelled, onProgress = onProgress
            )
            when (val r = outcome) {
                is RomExtractor.Outcome.Fail -> return InstallResult.Fail(r.reason)
                RomExtractor.Outcome.Cancelled -> return InstallResult.Fail("Cancelled")
                is RomExtractor.Outcome.Ok -> {
                    File(staging, "meta.json").writeText(meta.toJson())
                    val finalDir = dirOf(meta.id)
                    val old = File(romsDir, ".old-${meta.id}-${System.nanoTime()}")
                    if (finalDir.exists() && !finalDir.renameTo(old)) {
                        return InstallResult.Fail("cannot replace the existing ${meta.id}")
                    }
                    if (!staging.renameTo(finalDir)) {
                        if (old.exists()) old.renameTo(finalDir) // roll back
                        return InstallResult.Fail("cannot move ROM into place")
                    }
                    SafePaths.deleteTree(old)
                    return InstallResult.Ok(meta, r.files, r.bytes)
                }
            }
        } catch (e: Exception) {
            return InstallResult.Fail("install failed: ${e.message ?: e.javaClass.simpleName}")
        } finally {
            if (staging.exists()) SafePaths.deleteTree(staging)
        }
    }

    /**
     * Copies the installed ROM's tree into an instance rootfs, preserving symlinks and modes.
     * Top-level dirs listed in [keepIfExists] (user data) are never overwritten once the
     * instance has them. Full copy for now; copy-on-write layering arrives with the engine.
     */
    fun copyBaseInto(
        id: String,
        rootfsDir: File,
        keepIfExists: Set<String> = setOf("data", "sdcard", "storage"),
        isCancelled: () -> Boolean = { false },
        onProgress: (Float) -> Unit = {}
    ): Boolean {
        val base = baseDir(id)
        if (!base.isDirectory) return false
        rootfsDir.mkdirs()
        val tops = base.listFiles() ?: return false
        val total = maxOf(1, countEntries(base))
        var done = 0

        fun copyNode(src: File, dst: File): Boolean {
            if (isCancelled()) return false
            val link = fsOps.readLink(src)
            when {
                link != null -> fsOps.symlink(link, dst)
                src.isDirectory -> {
                    if (!dst.mkdirs() && !dst.isDirectory) return false
                    for (c in src.listFiles() ?: return false) if (!copyNode(c, File(dst, c.name))) return false
                    val m = fsOps.mode(src)
                    if (m >= 0) fsOps.chmod(dst, m or 0x1C0)
                }
                else -> {
                    src.copyTo(dst, overwrite = true)
                    val m = fsOps.mode(src)
                    if (m >= 0) fsOps.chmod(dst, m or 0x180)
                }
            }
            if (++done % 256 == 0) onProgress((done.toFloat() / total).coerceIn(0f, 1f))
            return true
        }

        for (top in tops) {
            val target = File(rootfsDir, top.name)
            val exists = target.exists() || fsOps.readLink(target) != null
            if (top.name in keepIfExists && exists) continue
            if (exists && !SafePaths.deleteTree(target)) return false
            if (!copyNode(top, target)) return false
        }
        onProgress(1f)
        return true
    }

    private fun countEntries(dir: File): Int {
        var n = 0
        for (c in dir.listFiles() ?: return 0) {
            n++
            if (c.isDirectory && fsOps.readLink(c) == null) n += countEntries(c)
        }
        return n
    }
}
