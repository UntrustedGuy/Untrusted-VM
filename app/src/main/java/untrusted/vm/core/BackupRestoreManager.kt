package untrusted.vm.core

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Manages Full VM Snapshot Backup and Restore operations.
 * Equivalent to VMOS Pro's BackupsService and RecoveryService:
 * Compresses the VM rootfs into standalone .uvm (ZIP) backup files
 * and restores them into fresh or existing VM slots.
 */
class BackupRestoreManager(
    private val filesystem: VmFilesystem
) {
    /**
     * Creates a compressed backup archive of the entire VM instance.
     */
    fun createBackup(destinationZipFile: File, onProgress: (Int) -> Unit = {}): Boolean {
        return try {
            destinationZipFile.parentFile?.mkdirs()
            val totalFiles = countFiles(filesystem.rootDir)
            var processedFiles = 0

            FileOutputStream(destinationZipFile).use { fos ->
                ZipOutputStream(fos).use { zos ->
                    zipDirectory(filesystem.rootDir, filesystem.rootDir, zos) {
                        processedFiles++
                        if (totalFiles > 0) {
                            onProgress((processedFiles * 100) / totalFiles)
                        }
                    }
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Restores a VM instance from a backup archive.
     */
    fun restoreFromBackup(backupZipFile: File, onProgress: (Int) -> Unit = {}): Boolean {
        if (!backupZipFile.exists() || !backupZipFile.canRead()) return false

        return try {
            filesystem.deleteInstanceFiles()
            filesystem.rootDir.mkdirs()

            FileInputStream(backupZipFile).use { fis ->
                ZipInputStream(fis).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        val outFile = File(filesystem.rootDir, entry.name)

                        // Path traversal guard
                        if (!outFile.canonicalPath.startsWith(filesystem.rootDir.canonicalPath)) {
                            throw SecurityException("Zip traversal detected: ${entry.name}")
                        }

                        if (entry.isDirectory) {
                            outFile.mkdirs()
                        } else {
                            outFile.parentFile?.mkdirs()
                            FileOutputStream(outFile).use { fos ->
                                zis.copyTo(fos)
                            }
                        }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun zipDirectory(
        rootDir: File,
        currentDir: File,
        zos: ZipOutputStream,
        onFileAdded: () -> Unit
    ) {
        val files = currentDir.listFiles() ?: return
        val buffer = ByteArray(8192)

        for (file in files) {
            val relativePath = file.toRelativeString(rootDir).replace('\\', '/')
            if (file.isDirectory) {
                val entry = ZipEntry(if (relativePath.endsWith("/")) relativePath else "$relativePath/")
                zos.putNextEntry(entry)
                zos.closeEntry()
                zipDirectory(rootDir, file, zos, onFileAdded)
            } else {
                val entry = ZipEntry(relativePath)
                zos.putNextEntry(entry)
                FileInputStream(file).use { fis ->
                    var len: Int
                    while (fis.read(buffer).also { len = it } > 0) {
                        zos.write(buffer, 0, len)
                    }
                }
                zos.closeEntry()
                onFileAdded()
            }
        }
    }

    private fun countFiles(dir: File): Int {
        var count = 0
        dir.walkTopDown().forEach { if (it.isFile) count++ }
        return count
    }
}
