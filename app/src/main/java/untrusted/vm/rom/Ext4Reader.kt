package untrusted.vm.rom

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.io.RandomAccessFile

/**
 * Minimal, read-only, unprivileged ext4 reader that extracts a whole image into a directory.
 * Android system images are ext4, and mounting one needs root, so we parse it ourselves.
 *
 * Supports extents and classic block maps, 32/64-bit group descriptors, htree directories
 * (read linearly), fast and slow symlinks, sparse files and uninitialised extents.
 * Skips device nodes, fifos and sockets. Ignores xattrs, ACLs and SELinux labels.
 *
 * Built to survive a hostile image: every offset is bounds-checked, directory cycles and
 * duplicate names are rejected, names with '/' or NUL are rejected, and symlinks are only
 * ever created, never followed.
 */
class Ext4Reader(private val raf: RandomAccessFile) {

    class FormatException(message: String) : IOException(message)
    class CancelledException : IOException("cancelled")

    data class Stats(val files: Int, val dirs: Int, val symlinks: Int, val skipped: Int, val bytes: Long)

    private class Inode(val mode: Int, val size: Long, val flags: Int, val iblock: ByteArray)
    private class Extent(val logical: Long, var len: Long, val phys: Long, val uninit: Boolean)
    private class DirEntry(val name: String, val ino: Long)

    private val imageLen = raf.length()
    private val blockSize: Int
    private val inodesPerGroup: Long
    private val inodesCount: Long
    private val inodeSize: Int
    private val groupCount: Int
    private val inodeTables: LongArray
    private val usedBytes: Long

    init {
        val sb = ByteArray(1024)
        readAt(1024, sb, 1024)
        if (le16(sb, 56) != 0xEF53) throw FormatException("not an ext4 filesystem (bad magic)")
        val logBs = le32(sb, 24)
        if (logBs !in 0..6) throw FormatException("unsupported block size")
        blockSize = 1024 shl logBs.toInt()
        val incompat = le32(sb, 96)
        val is64 = incompat and 0x80 != 0L
        if (incompat and 0x10 != 0L) throw FormatException("meta_bg ext4 layout is not supported")
        if (incompat and 0x8000 != 0L) throw FormatException("inline_data ext4 images are not supported")
        if (incompat and 0x1 != 0L) throw FormatException("compressed ext4 images are not supported")

        val blocksCount = le32(sb, 4) or (if (is64) le32(sb, 336) shl 32 else 0L)
        val freeBlocks = le32(sb, 12) or (if (is64) le32(sb, 344) shl 32 else 0L)
        val firstDataBlock = le32(sb, 20)
        val blocksPerGroup = le32(sb, 32)
        inodesPerGroup = le32(sb, 40)
        inodesCount = le32(sb, 0)
        inodeSize = if (le32(sb, 76) == 0L) 128 else le16(sb, 88)
        val descSize = if (is64) maxOf(32, le16(sb, 254)) else 32
        if (blocksPerGroup <= 0 || inodesPerGroup <= 0 || inodeSize < 128 || inodeSize > blockSize) {
            throw FormatException("corrupt ext4 superblock")
        }
        if (blocksCount * blockSize > imageLen + blockSize.toLong() * 1024) {
            throw FormatException("image file is smaller than the filesystem it describes (truncated?)")
        }
        val groups = (blocksCount - firstDataBlock + blocksPerGroup - 1) / blocksPerGroup
        if (groups < 1 || groups > (1 shl 20)) throw FormatException("corrupt group count")
        groupCount = groups.toInt()
        usedBytes = maxOf(1L, (blocksCount - freeBlocks) * blockSize)

        val gdtOffset = (firstDataBlock + 1) * blockSize
        val gdt = ByteArray(groupCount * descSize)
        readAt(gdtOffset, gdt, gdt.size)
        inodeTables = LongArray(groupCount) { g ->
            val o = g * descSize
            le32(gdt, o + 8) or (if (descSize >= 64) le32(gdt, o + 0x28) shl 32 else 0L)
        }
    }

    // ---- low level -------------------------------------------------------------------------

    private fun le16(b: ByteArray, o: Int) = (b[o].toInt() and 0xFF) or ((b[o + 1].toInt() and 0xFF) shl 8)
    private fun le32(b: ByteArray, o: Int): Long = le16(b, o).toLong() or (le16(b, o + 2).toLong() shl 16)

    private fun readAt(offset: Long, buf: ByteArray, len: Int) {
        if (offset < 0 || offset + len > imageLen) throw FormatException("read past the end of the image (truncated or corrupt)")
        raf.seek(offset)
        raf.readFully(buf, 0, len)
    }

    private fun readInode(ino: Long): Inode {
        if (ino < 1 || ino > inodesCount) throw FormatException("inode $ino out of range")
        val group = ((ino - 1) / inodesPerGroup).toInt()
        if (group >= groupCount) throw FormatException("inode $ino has no block group")
        val index = (ino - 1) % inodesPerGroup
        val b = ByteArray(128)
        readAt(inodeTables[group] * blockSize + index * inodeSize, b, 128)
        val mode = le16(b, 0)
        val size = le32(b, 4) or (if (mode and 0xF000 == 0x8000) le32(b, 108) shl 32 else 0L)
        return Inode(mode, size, le32(b, 32).toInt(), b.copyOfRange(40, 100))
    }

    // ---- block mapping ---------------------------------------------------------------------

    private fun extentsOf(inode: Inode): List<Extent> {
        val out = ArrayList<Extent>()
        if (inode.flags and 0x80000 != 0) {
            parseExtentNode(inode.iblock, 0, out, 6)
        } else {
            blockMapExtents(inode, out)
        }
        out.sortBy { it.logical }
        return out
    }

    private fun parseExtentNode(buf: ByteArray, off: Int, out: MutableList<Extent>, guard: Int) {
        if (guard < 0) throw FormatException("extent tree is too deep")
        if (le16(buf, off) != 0xF30A) throw FormatException("bad extent header")
        val entries = le16(buf, off + 2)
        val depth = le16(buf, off + 6)
        if (off + 12 + entries * 12 > buf.size) throw FormatException("extent node overflows its block")
        for (i in 0 until entries) {
            val e = off + 12 + i * 12
            if (depth == 0) {
                val rawLen = le16(buf, e + 4)
                val uninit = rawLen > 32768
                val len = (if (uninit) rawLen - 32768 else rawLen).toLong()
                val phys = le32(buf, e + 8) or (le16(buf, e + 6).toLong() shl 32)
                out.add(Extent(le32(buf, e), len, phys, uninit))
            } else {
                val leaf = le32(buf, e + 4) or (le16(buf, e + 8).toLong() shl 32)
                val node = ByteArray(blockSize)
                readAt(leaf * blockSize, node, blockSize)
                parseExtentNode(node, 0, out, guard - 1)
            }
        }
    }

    private fun blockMapExtents(inode: Inode, out: MutableList<Extent>) {
        val nblocks = (inode.size + blockSize - 1) / blockSize
        fun add(logical: Long, phys: Long) {
            if (phys == 0L || logical >= nblocks) return
            val last = out.lastOrNull()
            if (last != null && last.logical + last.len == logical && last.phys + last.len == phys) last.len++
            else out.add(Extent(logical, 1, phys, false))
        }
        val ptrsPerBlock = blockSize / 4
        var logical = 0L
        for (i in 0 until 12) { add(logical, le32(inode.iblock, i * 4)); logical++ }

        fun walk(block: Long, level: Int) {
            var span = 1L
            repeat(level) { span *= ptrsPerBlock }
            if (logical >= nblocks) return
            if (block == 0L) { logical += span; return }
            val data = ByteArray(blockSize)
            readAt(block * blockSize, data, blockSize)
            for (i in 0 until ptrsPerBlock) {
                if (logical >= nblocks) return
                val p = le32(data, i * 4)
                if (level == 1) { add(logical, p); logical++ } else walk(p, level - 1)
            }
        }
        for (level in 1..3) walk(le32(inode.iblock, (11 + level) * 4), level)
    }

    // ---- data ------------------------------------------------------------------------------

    private val zeros = ByteArray(64 * 1024)

    private fun writeZeros(out: OutputStream, count: Long) {
        var left = count
        while (left > 0) {
            val n = minOf(left, zeros.size.toLong()).toInt()
            out.write(zeros, 0, n)
            left -= n
        }
    }

    private fun streamFile(inode: Inode, out: OutputStream, isCancelled: () -> Boolean) {
        var pos = 0L
        val buf = ByteArray(1 shl 20)
        for (e in extentsOf(inode)) {
            val start = e.logical * blockSize
            if (start >= inode.size) break
            if (start < pos) throw FormatException("overlapping extents")
            if (start > pos) { writeZeros(out, start - pos); pos = start }
            var left = minOf(e.len * blockSize, inode.size - start)
            if (e.uninit) {
                writeZeros(out, left)
            } else {
                var phys = e.phys * blockSize
                while (left > 0) {
                    if (isCancelled()) throw CancelledException()
                    val n = minOf(left, buf.size.toLong()).toInt()
                    readAt(phys, buf, n)
                    out.write(buf, 0, n)
                    phys += n
                    left -= n
                }
            }
            pos = start + minOf(e.len * blockSize, inode.size - start)
        }
        if (pos < inode.size) writeZeros(out, inode.size - pos)
    }

    private fun readSmall(inode: Inode, limit: Int): ByteArray {
        if (inode.size > limit) throw FormatException("metadata object is unreasonably large")
        val bos = java.io.ByteArrayOutputStream(inode.size.toInt())
        streamFile(inode, bos) { false }
        return bos.toByteArray()
    }

    private fun readSymlinkTarget(inode: Inode): String {
        val bytes = if (inode.flags and 0x80000 == 0 && inode.size < 60) {
            inode.iblock.copyOfRange(0, inode.size.toInt())
        } else {
            readSmall(inode, 4096)
        }
        if (bytes.any { it.toInt() == 0 }) throw FormatException("symlink target contains NUL")
        return String(bytes, Charsets.UTF_8)
    }

    private fun readDir(inode: Inode): List<DirEntry> {
        val data = readSmall(inode, 256 * 1024 * 1024)
        val out = ArrayList<DirEntry>()
        var p = 0
        while (p + 8 <= data.size) {
            val ino = le32(data, p)
            val recLen = le16(data, p + 4)
            val nameLen = data[p + 6].toInt() and 0xFF
            if (recLen < 8 || recLen % 4 != 0 || p + recLen > data.size) {
                p = (p / blockSize + 1) * blockSize // corrupt entry: resume at the next block
                continue
            }
            if (ino != 0L && nameLen > 0 && 8 + nameLen <= recLen) {
                out.add(DirEntry(String(data, p + 8, nameLen, Charsets.UTF_8), ino))
            }
            p += recLen
        }
        return out
    }

    // ---- extraction ------------------------------------------------------------------------

    fun extractTo(
        destRoot: File,
        ops: FsOps,
        maxBytes: Long,
        maxEntries: Int,
        isCancelled: () -> Boolean = { false },
        onProgress: (Float) -> Unit = {}
    ): Stats {
        destRoot.mkdirs()
        if (!destRoot.list().isNullOrEmpty()) throw FormatException("destination folder is not empty")

        var written = 0L
        var entries = 0
        var files = 0; var dirs = 0; var links = 0; var skipped = 0
        var lastReport = 0L
        val visited = HashSet<Long>()
        val stack = ArrayDeque<Pair<Long, File>>()
        visited.add(2L)
        stack.addLast(2L to destRoot)

        while (stack.isNotEmpty()) {
            val (dirIno, dirFile) = stack.removeLast()
            val dirInode = readInode(dirIno)
            if (dirInode.mode and 0xF000 != 0x4000) throw FormatException("inode $dirIno is not a directory")
            val seen = HashSet<String>()
            for (de in readDir(dirInode)) {
                if (isCancelled()) throw CancelledException()
                val name = de.name
                if (name == "." || name == "..") continue
                if (dirFile == destRoot && name == "lost+found") continue
                if (name.indexOf('/') >= 0 || name.indexOf('\u0000') >= 0) throw FormatException("illegal file name in image")
                if (!seen.add(name)) throw FormatException("duplicate entry '$name' in a directory")
                if (++entries > maxEntries) throw FormatException("image has too many entries")

                val inode = readInode(de.ino)
                val child = File(dirFile, name)
                when (inode.mode and 0xF000) {
                    0x4000 -> {
                        if (!visited.add(de.ino)) throw FormatException("directory loop in image")
                        if (!child.mkdir()) throw FormatException("cannot create directory '$name'")
                        ops.chmod(child, (inode.mode and 0x1FF) or 0x1C0)
                        stack.addLast(de.ino to child)
                        dirs++
                    }
                    0x8000 -> {
                        if (written + inode.size > maxBytes) throw FormatException("ROM expands beyond the size limit")
                        FileOutputStream(child).use { out ->
                            val buffered = java.io.BufferedOutputStream(out, 256 * 1024)
                            streamFile(inode, buffered, isCancelled)
                            buffered.flush()
                        }
                        ops.chmod(child, (inode.mode and 0x1FF) or 0x180)
                        written += inode.size
                        files++
                    }
                    0xA000 -> {
                        ops.symlink(readSymlinkTarget(inode), child)
                        links++
                    }
                    else -> skipped++
                }
                val now = System.nanoTime()
                if (now - lastReport > 250_000_000L) {
                    lastReport = now
                    onProgress((written.toFloat() / usedBytes).coerceIn(0f, 1f))
                }
            }
        }
        onProgress(1f)
        return Stats(files, dirs, links, skipped, written)
    }
}
