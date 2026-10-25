package untrusted.vm.rom

import java.io.File

/**
 * Path hardening helpers. Anything coming from an archive, a catalog, or a guest
 * process must go through here before it touches the host filesystem.
 */
object SafePaths {

    /**
     * chroot-style resolution of a guest path under [base]. ".." can never climb above [base];
     * it simply stops at the root, exactly like `/..` == `/` inside a chroot. Never throws.
     */
    @JvmStatic
    fun clampGuestPath(base: File, guestPath: String?): File {
        if (guestPath.isNullOrEmpty()) return base
        val out = ArrayList<String>()
        for (raw in guestPath.split('/')) {
            val seg = raw.replace("\u0000", "")
            when (seg) {
                "", "." -> Unit
                ".." -> if (out.isNotEmpty()) out.removeAt(out.size - 1)
                else -> out.add(seg)
            }
        }
        return if (out.isEmpty()) base else File(base, out.joinToString("/"))
    }

    /**
     * Normalises an archive entry name. Returns null when the name is hostile:
     * absolute, drive-lettered, NUL-containing or containing a ".." segment.
     * Backslashes are treated as separators because zips made on Windows use them.
     */
    @JvmStatic
    fun normalizeEntryName(name: String): String? {
        if (name.indexOf('\u0000') >= 0) return null
        val n = name.replace('\\', '/')
        if (n.startsWith("/")) return null
        if (n.length >= 2 && n[1] == ':') return null
        val segs = n.split('/').filter { it.isNotEmpty() && it != "." }
        if (segs.any { it == ".." }) return null
        return segs.joinToString("/")
    }

    /**
     * Resolves [relative] under [base] and verifies via canonical paths (which follows
     * symlinks) that the result is still inside [base]. Returns null if it escapes.
     */
    @JvmStatic
    fun resolveInside(base: File, relative: String): File? {
        val norm = normalizeEntryName(relative) ?: return null
        val dest = if (norm.isEmpty()) base else File(base, norm)
        val baseCanon = base.canonicalPath
        val destCanon = dest.canonicalPath
        return if (destCanon == baseCanon || destCanon.startsWith(baseCanon + File.separator)) dest else null
    }

    /**
     * True if [f] is a symlink to something that exists. (Dangling links report false, which is
     * fine for deletion: a dangling link is not a directory, so it is simply unlinked.)
     */
    @JvmStatic
    fun isSymlink(f: File): Boolean {
        val abs = f.absoluteFile
        val parent = abs.parentFile ?: return false
        return abs.canonicalPath != File(parent.canonicalFile, abs.name).path
    }

    /** Recursive delete that never descends into symlinked directories. ROM trees are full of symlinks. */
    @JvmStatic
    fun deleteTree(f: File): Boolean {
        if (f.isDirectory && !isSymlink(f)) {
            f.listFiles()?.forEach { deleteTree(it) }
        }
        return f.delete() || !f.exists()
    }
}
