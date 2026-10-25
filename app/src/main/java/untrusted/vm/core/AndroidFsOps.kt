package untrusted.vm.core

import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import untrusted.vm.rom.FsOps
import java.io.File
import java.io.IOException

/** [FsOps] backed by android.system.Os (available since API 21). */
class AndroidFsOps : FsOps {
    override fun symlink(target: String, link: File) {
        try { Os.symlink(target, link.path) } catch (e: ErrnoException) { throw IOException("symlink ${link.name}: ${e.message}", e) }
    }

    override fun readLink(link: File): String? = try {
        val st = Os.lstat(link.path)
        if (OsConstants.S_ISLNK(st.st_mode)) Os.readlink(link.path) else null
    } catch (e: ErrnoException) { null }

    override fun mode(file: File): Int = try { Os.lstat(file.path).st_mode and 0xFFF } catch (e: ErrnoException) { -1 }

    override fun chmod(file: File, mode: Int) {
        try { Os.chmod(file.path, mode) } catch (e: ErrnoException) { throw IOException("chmod ${file.name}: ${e.message}", e) }
    }
}
