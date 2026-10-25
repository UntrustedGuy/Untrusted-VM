package untrusted.vm.core

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

/**
 * ROM Catalog & Installation Manager.
 * Equivalent to VMOS Pro's RomDownloadHelper / RomDownloadManager / AddLocalVmActivity.
 * 1. Predefined Virtual ROM Profiles (Android 7.1 Geek, 9.0 Pure, 10.0 Pro, 12.0 GMS).
 * 2. Local ROM archive installation (.zip / .7z / .img / .rom extraction to instance rootfs).
 * 3. Boot Animation Manager (custom bootanimation.zip).
 */
class RomManager(
    private val context: Context
) {
    companion object {
        private const val TAG = "RomManager"
    }

    data class RomProfile(
        val id: String,
        val displayName: String,
        val androidVersion: String,
        val apiLevel: Int,
        val architecture: String, // "arm64-v8a", "armeabi-v7a"
        val isRootPreinstalled: Boolean,
        val hasGoogleServices: Boolean,
        val description: String,
        val sizeFormatted: String
    )

    val availableRoms: List<RomProfile> = listOf(
        RomProfile(
            id = "rom_7_1_geek",
            displayName = "Android 7.1 Geek Edition",
            androidVersion = "7.1.2",
            apiLevel = 25,
            architecture = "arm64-v8a",
            isRootPreinstalled = true,
            hasGoogleServices = false,
            description = "Lightweight performance ROM with built-in Superuser and Xposed compatibility.",
            sizeFormatted = "380 MB"
        ),
        RomProfile(
            id = "rom_9_0_pure",
            displayName = "Android 9.0 Pure AOSP",
            androidVersion = "9.0.0",
            apiLevel = 28,
            architecture = "arm64-v8a",
            isRootPreinstalled = false,
            hasGoogleServices = false,
            description = "Clean, bloatware-free stock Android 9 Pie ROM with optimal battery life.",
            sizeFormatted = "540 MB"
        ),
        RomProfile(
            id = "rom_10_0_pro",
            displayName = "Android 10.0 Pro Standard",
            androidVersion = "10.0.0",
            apiLevel = 29,
            architecture = "arm64-v8a",
            isRootPreinstalled = true,
            hasGoogleServices = true,
            description = "Full-featured Android 10 container with Google Play Store profile and Superuser.",
            sizeFormatted = "720 MB"
        ),
        RomProfile(
            id = "rom_12_0_gms",
            displayName = "Android 12.0 Modern GMS",
            androidVersion = "12.0.0",
            apiLevel = 31,
            architecture = "arm64-v8a",
            isRootPreinstalled = false,
            hasGoogleServices = true,
            description = "Modern Android 12 environment with Material You theme and Play Services support.",
            sizeFormatted = "950 MB"
        )
    )

    /**
     * Installs a ROM profile into the target instance rootfs.
     */
    fun provisionRomProfile(targetFilesystem: VmFilesystem, profile: RomProfile) {
        val rootDir = targetFilesystem.rootDir
        rootDir.mkdirs()

        // 1. Initialize skeleton directories
        targetFilesystem.initializeSkeleton()

        // 2. Write custom build.prop reflecting the ROM profile
        val buildProp = targetFilesystem.resolveGuestFile("/system/build.prop")
        val content = buildString {
            appendLine("ro.build.version.release=${profile.androidVersion}")
            appendLine("ro.build.version.sdk=${profile.apiLevel}")
            appendLine("ro.product.cpu.abi=${profile.architecture}")
            appendLine("ro.product.name=UntrustedVM_${profile.androidVersion}")
            appendLine("ro.product.model=Untrusted VM ${profile.androidVersion}")
            appendLine("ro.product.brand=Untrusted")
            appendLine("ro.product.manufacturer=UntrustedVM")
            appendLine("ro.build.type=userdebug")
            appendLine("ro.build.tags=test-keys")
            appendLine("ro.debuggable=1")
            appendLine("persist.sys.untrusted.rom=${profile.id}")
        }
        buildProp.writeText(content)

        // 3. Configure default boot animation
        setupDefaultBootAnimation(targetFilesystem)

        Log.i(TAG, "Provisioned ROM profile ${profile.displayName} into instance at ${rootDir.absolutePath}")
    }

    /**
     * Extracts an external ROM zip / archive into the instance rootfs.
     */
    fun installLocalRomArchive(targetFilesystem: VmFilesystem, archiveFile: File, onProgress: (Float) -> Unit = {}): Boolean {
        if (!archiveFile.exists()) return false

        return try {
            val totalBytes = archiveFile.length()
            var extractedBytes = 0L

            ZipInputStream(FileInputStream(archiveFile)).use { zis ->
                var entry: ZipEntry? = zis.nextEntry
                while (entry != null) {
                    val destFile = targetFilesystem.resolveGuestFile(entry.name)
                    if (entry.isDirectory) {
                        destFile.mkdirs()
                    } else {
                        destFile.parentFile?.mkdirs()
                        FileOutputStream(destFile).use { fos ->
                            val buffer = ByteArray(8192)
                            var len: Int
                            while (zis.read(buffer).also { len = it } > 0) {
                                fos.write(buffer, 0, len)
                                extractedBytes += len
                            }
                        }
                    }
                    if (totalBytes > 0) {
                        onProgress((extractedBytes.toFloat() / totalBytes).coerceIn(0f, 1f))
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error installing local ROM archive", e)
            false
        }
    }

    /**
     * Sets a custom boot animation zip.
     */
    fun installCustomBootAnimation(targetFilesystem: VmFilesystem, bootAnimZip: File): Boolean {
        if (!bootAnimZip.exists()) return false
        val dest = targetFilesystem.resolveGuestFile("/system/media/bootanimation.zip")
        dest.parentFile?.mkdirs()
        return try {
            bootAnimZip.copyTo(dest, overwrite = true)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy custom boot animation", e)
            false
        }
    }

    private fun setupDefaultBootAnimation(targetFilesystem: VmFilesystem) {
        val dest = targetFilesystem.resolveGuestFile("/system/media/bootanimation.zip")
        if (!dest.exists()) {
            dest.parentFile?.mkdirs()
            try {
                // Create placeholder boot animation marker
                dest.writeBytes(byteArrayOf(0x50, 0x4B, 0x05, 0x06, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0))
            } catch (_: Exception) {}
        }
    }
}
