package untrusted.vm.rom

import java.io.File

/**
 * The few filesystem operations java.io can't do (symlinks and unix modes).
 * Android provides these through android.system.Os (see core/AndroidFsOps); JVM unit
 * tests use a java.nio implementation. Keeping this an interface keeps the rom package
 * free of Android dependencies so it can be tested on a plain JVM.
 */
interface FsOps {
    fun symlink(target: String, link: File)

    /** Target of the symlink at [link], or null if [link] is not a symlink (never follows it). */
    fun readLink(link: File): String?

    /** Permission bits (0..0xFFF) of [file] without following symlinks, or -1 if unknown. */
    fun mode(file: File): Int

    fun chmod(file: File, mode: Int)
}
