package untrusted.vm.rom

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean

fun sha256Hex(file: File): String {
    val md = MessageDigest.getInstance("SHA-256")
    FileInputStream(file).use { input ->
        val buf = ByteArray(64 * 1024)
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            md.update(buf, 0, n)
        }
    }
    return md.digest().joinToString("") { "%02x".format(it) }
}

/**
 * Resumable, checksum-verified downloader. Writes to `<dest>.part`, resumes with an HTTP Range
 * request, and only renames to [dest] after the SHA-256 matches. Blocking: call from a worker thread.
 */
class RomDownloader(
    private val allowCleartext: Boolean = false, // tests only; production stays https-only
    private val connectTimeoutMs: Int = 15_000,
    private val readTimeoutMs: Int = 30_000
) {
    sealed class Result {
        data class Success(val file: File, val sha256: String) : Result()
        data class Failure(val message: String) : Result()
        object Cancelled : Result()
    }

    private sealed class Step {
        object Done : Step()
        object RestartFromScratch : Step()
        object Cancelled : Step()
        data class Fail(val message: String) : Step()
    }

    fun download(
        url: String,
        dest: File,
        expectedSha256: String,
        expectedSize: Long = 0L,
        cancel: AtomicBoolean = AtomicBoolean(false),
        onProgress: (done: Long, total: Long) -> Unit = { _, _ -> }
    ): Result {
        val u = try { URL(url) } catch (e: Exception) { return Result.Failure("bad url: ${e.message}") }
        val okProto = u.protocol == "https" || (allowCleartext && u.protocol == "http")
        if (!okProto) return Result.Failure("refusing non-https url")

        dest.absoluteFile.parentFile?.mkdirs()
        val part = File(dest.path + ".part")

        var restarts = 0
        while (true) {
            when (val step = fetch(u, part, expectedSize, cancel, onProgress)) {
                Step.Done -> break
                Step.Cancelled -> return Result.Cancelled
                is Step.Fail -> return Result.Failure(step.message)
                Step.RestartFromScratch -> {
                    part.delete()
                    if (++restarts > 1) return Result.Failure("server rejected the download twice")
                }
            }
        }

        if (expectedSize > 0 && part.length() != expectedSize) {
            val got = part.length()
            part.delete()
            return Result.Failure("size mismatch: expected $expectedSize bytes, got $got")
        }
        val actual = sha256Hex(part)
        if (!actual.equals(expectedSha256, ignoreCase = true)) {
            part.delete()
            return Result.Failure("checksum mismatch: expected ${expectedSha256.lowercase()}, got $actual")
        }
        if (dest.exists() && !dest.delete()) return Result.Failure("cannot replace ${dest.name}")
        if (!part.renameTo(dest)) return Result.Failure("cannot move download into place")
        return Result.Success(dest, actual)
    }

    private fun fetch(
        u: URL,
        part: File,
        expectedSize: Long,
        cancel: AtomicBoolean,
        onProgress: (Long, Long) -> Unit
    ): Step {
        val existing = if (part.exists()) part.length() else 0L
        val conn = try { u.openConnection() as HttpURLConnection } catch (e: Exception) {
            return Step.Fail("cannot open connection: ${e.message}")
        }
        try {
            conn.connectTimeout = connectTimeoutMs
            conn.readTimeout = readTimeoutMs
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("User-Agent", "UntrustedVM/1.0")
            if (existing > 0) conn.setRequestProperty("Range", "bytes=$existing-")

            val append: Boolean
            var total: Long
            when (val code = conn.responseCode) {
                200 -> {
                    append = false
                    total = conn.contentLengthLong
                }
                206 -> {
                    append = true
                    total = conn.getHeaderField("Content-Range")?.substringAfter('/', "")?.toLongOrNull() ?: -1L
                }
                416 -> {
                    // Our .part is already the whole file, or is stale/corrupt.
                    return if (expectedSize > 0 && existing == expectedSize) Step.Done else Step.RestartFromScratch
                }
                else -> return Step.Fail("HTTP $code")
            }
            if (total <= 0) total = expectedSize

            var done = if (append) existing else 0L
            var lastReport = 0L
            FileOutputStream(part, append).use { out ->
                conn.inputStream.use { input ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        if (cancel.get()) return Step.Cancelled
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        val now = System.nanoTime()
                        if (now - lastReport > 250_000_000L) {
                            lastReport = now
                            onProgress(done, total)
                        }
                    }
                }
            }
            onProgress(done, total)
            if (total > 0 && part.length() != total) {
                return Step.Fail("connection ended early (${part.length()} of $total bytes); retry to resume")
            }
            return Step.Done
        } catch (e: Exception) {
            return Step.Fail("download failed: ${e.message ?: e.javaClass.simpleName} (retry to resume)")
        } finally {
            conn.disconnect()
        }
    }
}
