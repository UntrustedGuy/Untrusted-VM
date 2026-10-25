package untrusted.vm.core

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import java.io.File

data class HostAppItem(
    val appName: String,
    val packageName: String,
    val versionName: String,
    val apkFile: File,
    val icon: Drawable?,
    val isSystemApp: Boolean
)

/**
 * Host Application Importer.
 * Equivalent to VMOS Pro's CategoryToolAppAdapter:
 * Lists user apps installed on the host device, extracts their APK files,
 * and stages them in the guest VM's /data/app/ or /sdcard/Download/.
 */
class AppImporter(
    private val context: Context,
    private val filesystem: VmFilesystem
) {
    private val packageManager: PackageManager = context.packageManager

    /**
     * Lists non-system apps installed on the host phone.
     */
    fun getInstalledHostApps(): List<HostAppItem> {
        val installed = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
        val appList = mutableListOf<HostAppItem>()

        for (info in installed) {
            val isSystem = (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            val sourceApk = File(info.publicSourceDir ?: info.sourceDir)
            if (!sourceApk.exists() || !sourceApk.canRead()) continue

            val appName = packageManager.getApplicationLabel(info).toString()
            val versionName = try {
                val pInfo = packageManager.getPackageInfo(info.packageName, 0)
                pInfo.versionName ?: "1.0"
            } catch (e: Exception) {
                "1.0"
            }

            appList.add(
                HostAppItem(
                    appName = appName,
                    packageName = info.packageName,
                    versionName = versionName,
                    apkFile = sourceApk,
                    icon = try { packageManager.getApplicationIcon(info) } catch (e: Exception) { null },
                    isSystemApp = isSystem
                )
            )
        }

        return appList.sortedWith(compareBy({ it.isSystemApp }, { it.appName }))
    }

    /**
     * Imports a host app's APK into the guest VM.
     * Places the APK in /data/app/<package_name>.apk ready for in-guest execution.
     */
    fun importAppToGuest(app: HostAppItem): Boolean {
        val targetApk = filesystem.resolveGuestFile("/data/app/${app.packageName}.apk")
        targetApk.parentFile?.mkdirs()

        return try {
            app.apkFile.inputStream().use { input ->
                targetApk.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
