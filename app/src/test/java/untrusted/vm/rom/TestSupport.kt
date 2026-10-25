package untrusted.vm.rom

import org.tukaani.xz.LZMA2Options
import org.tukaani.xz.XZOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.attribute.PosixFilePermission

/** java.nio-backed FsOps for JVM tests (Android uses AndroidFsOps). */
object JvmFsOps : FsOps {
    private val bits = listOf(
        PosixFilePermission.OTHERS_EXECUTE, PosixFilePermission.OTHERS_WRITE, PosixFilePermission.OTHERS_READ,
        PosixFilePermission.GROUP_EXECUTE, PosixFilePermission.GROUP_WRITE, PosixFilePermission.GROUP_READ,
        PosixFilePermission.OWNER_EXECUTE, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_READ
    )

    override fun symlink(target: String, link: File) {
        Files.createSymbolicLink(link.toPath(), java.nio.file.Paths.get(target))
    }

    override fun readLink(link: File): String? =
        if (Files.isSymbolicLink(link.toPath())) Files.readSymbolicLink(link.toPath()).toString() else null

    override fun mode(file: File): Int {
        val perms = Files.getPosixFilePermissions(file.toPath(), LinkOption.NOFOLLOW_LINKS)
        return bits.foldIndexed(0) { i, acc, p -> if (p in perms) acc or (1 shl i) else acc }
    }

    override fun chmod(file: File, mode: Int) {
        Files.setPosixFilePermissions(file.toPath(), bits.filterIndexed { i, _ -> mode and (1 shl i) != 0 }.toSet())
    }
}

object TestData {
    fun resource(name: String, into: File): File {
        val dest = File(into, name)
        val stream = TestData::class.java.getResourceAsStream("/untrusted/vm/rom/$name")
            ?: error("missing test resource $name")
        stream.use { i -> dest.outputStream().use { o -> i.copyTo(o) } }
        return dest
    }

    fun resourceText(name: String): String =
        TestData::class.java.getResourceAsStream("/untrusted/vm/rom/$name")!!.bufferedReader().use { it.readText() }

    /** Encodes a raw image as an Android sparse image (RAW chunks for data, DONT_CARE for zero blocks). */
    fun toSparse(raw: File, out: File, blk: Int = 4096) {
        fun le16(v: Int) = byteArrayOf(v.toByte(), (v shr 8).toByte())
        fun le32(v: Long) = byteArrayOf(v.toByte(), (v shr 8).toByte(), (v shr 16).toByte(), (v shr 24).toByte())
        val blocks = (raw.length() / blk).toInt()
        require(raw.length() % blk == 0L)
        val chunks = ArrayList<Triple<Int, Int, ByteArray?>>() // type, blockCount, data
        RandomAccessFile(raw, "r").use { r ->
            val buf = ByteArray(blk)
            var i = 0
            while (i < blocks) {
                r.seek(i.toLong() * blk); r.readFully(buf)
                val zero = buf.all { it.toInt() == 0 }
                var j = i + 1
                val data = java.io.ByteArrayOutputStream()
                if (!zero) data.write(buf)
                while (j < blocks) {
                    r.seek(j.toLong() * blk); r.readFully(buf)
                    if (buf.all { it.toInt() == 0 } != zero) break
                    if (!zero) data.write(buf)
                    j++
                }
                chunks.add(Triple(if (zero) 0xCAC3 else 0xCAC1, j - i, if (zero) null else data.toByteArray()))
                i = j
            }
        }
        out.outputStream().use { o ->
            o.write(byteArrayOf(0x3A, 0xFF.toByte(), 0x26, 0xED.toByte()))
            o.write(le16(1)); o.write(le16(0)); o.write(le16(28)); o.write(le16(12))
            o.write(le32(blk.toLong())); o.write(le32(blocks.toLong())); o.write(le32(chunks.size.toLong())); o.write(le32(0))
            for ((type, n, data) in chunks) {
                o.write(le16(type)); o.write(le16(0)); o.write(le32(n.toLong()))
                o.write(le32(12L + (data?.size ?: 0)))
                if (data != null) o.write(data)
            }
        }
    }

    fun xz(input: File, out: File) {
        XZOutputStream(out.outputStream(), LZMA2Options(1)).use { x -> input.inputStream().use { it.copyTo(x) } }
    }
}

/** Parsed tiny.expected manifest: ground truth generated from the source tree, not from our reader. */
class Manifest(text: String) {
    class FileEntry(val size: Long, val sha: String, val exec: Boolean)
    val files = HashMap<String, FileEntry>()
    val dirs = HashSet<String>()
    val links = HashMap<String, String>()
    val special = HashSet<String>()

    init {
        for (line in text.lines().filter { it.isNotBlank() }) {
            val p = line.split('\t')
            when (p[0]) {
                "F" -> files[p[1]] = FileEntry(p[2].toLong(), p[3], p[4] == "x")
                "D" -> dirs.add(p[1])
                "L" -> links[p[1]] = p[2]
                "S" -> special.add(p[1])
            }
        }
    }

    /** Returns a list of mismatches (empty = tree is exactly as expected). */
    fun verify(root: File, ops: FsOps = JvmFsOps): List<String> {
        val problems = ArrayList<String>()
        for ((path, f) in files) {
            val file = File(root, path)
            if (!file.isFile) { problems.add("missing file $path"); continue }
            if (file.length() != f.size) problems.add("size of $path: ${file.length()} != ${f.size}")
            else if (sha256Hex(file) != f.sha) problems.add("content of $path differs")
            if (f.exec != file.canExecute()) problems.add("exec bit of $path")
        }
        for (d in dirs) if (!File(root, d).isDirectory) problems.add("missing dir $d")
        for ((path, target) in links) {
            val got = ops.readLink(File(root, path))
            if (got != target) problems.add("symlink $path -> $got (expected $target)")
        }
        for (s in special) if (File(root, s).exists() || ops.readLink(File(root, s)) != null) problems.add("special file extracted: $s")
        if (File(root, "lost+found").exists()) problems.add("lost+found extracted")
        // nothing extra
        val expectedCount = files.size + dirs.size + links.size
        var actual = 0
        root.walkTopDown().onEnter { ops.readLink(it) == null }.forEach { if (it != root) actual++ }
        if (actual != expectedCount) problems.add("entry count $actual != expected $expectedCount")
        return problems
    }
}
