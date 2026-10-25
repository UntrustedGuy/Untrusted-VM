package untrusted.vm.core

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * File Transfer Station bridging Host and Guest filesystems.
 * Equivalent to VMOS Pro's /sdcard/VMOSfiletransferstation:
 * Enables importing APKs, scripts, and documents into the guest rootfs
 * and exporting guest artifacts back to host storage.
 */
class FileTransferStation(
    private val filesystem: VmFilesystem
) {
    /**
     * Imports an external host file into the VM guest storage.
     * Destination defaults to guest /sdcard/Download or /data/local/tmp
     */
    fun importFileToGuest(sourceHostFile: File, guestRelativePath: String): Boolean {
        if (!sourceHostFile.exists() || !sourceHostFile.canRead()) return false

        val targetGuestPath = if (guestRelativePath.startsWith("/")) {
            guestRelativePath
        } else {
            "/sdcard/Download/$guestRelativePath"
        }

        val destinationFile = filesystem.resolveGuestFile(targetGuestPath)
        destinationFile.parentFile?.mkdirs()

        return copyFile(sourceHostFile, destinationFile)
    }

    /**
     * Exports a file from the VM guest rootfs to the host storage.
     */
    fun exportFileToHost(guestPath: String, targetHostFile: File): Boolean {
        val sourceGuestFile = filesystem.resolveGuestFile(guestPath)
        if (!sourceGuestFile.exists() || !sourceGuestFile.canRead()) return false

        targetHostFile.parentFile?.mkdirs()
        return copyFile(sourceGuestFile, targetHostFile)
    }

    /**
     * Lists files currently in the shared guest transfer folder.
     */
    fun listSharedTransferFiles(): List<File> {
        val transferDir = filesystem.resolveGuestFile("/sdcard/VMOSfiletransferstation")
        if (!transferDir.exists()) {
            transferDir.mkdirs()
        }
        return transferDir.listFiles()?.toList() ?: emptyList()
    }

    private fun copyFile(src: File, dst: File): Boolean {
        return try {
            FileInputStream(src).use { input ->
                FileOutputStream(dst).use { output ->
                    input.copyTo(output)
                }
            }
            true
        } catch (e: Exception) {
            false
        }
    }
}
