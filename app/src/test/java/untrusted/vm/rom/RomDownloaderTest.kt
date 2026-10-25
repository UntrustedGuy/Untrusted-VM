package untrusted.vm.rom

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import java.net.InetSocketAddress
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class RomDownloaderTest {
    private lateinit var tmp: File
    private lateinit var server: HttpServer
    private val payload = ByteArray(300_000) { (it * 31 % 251).toByte() }
    private val payloadSha: String by lazy {
        val f = File.createTempFile("payload", ".bin"); f.writeBytes(payload); val s = sha256Hex(f); f.delete(); s
    }
    private val rangeRequests = AtomicInteger(0)
    @Volatile private var supportRange = true
    @Volatile private var truncateAt = -1

    private val dl = RomDownloader(allowCleartext = true, connectTimeoutMs = 2000, readTimeoutMs = 2000)
    private fun url() = "http://127.0.0.1:${server.address.port}/rom.zip"

    @Before fun setUp() {
        tmp = Files.createTempDirectory("dl").toFile()
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/rom.zip") { ex: HttpExchange -> handle(ex) }
        server.start()
    }
    @After fun tearDown() { server.stop(0); tmp.deleteRecursively() }

    private fun handle(ex: HttpExchange) {
        val range = ex.requestHeaders.getFirst("Range")
        if (range != null && supportRange) {
            rangeRequests.incrementAndGet()
            val start = range.removePrefix("bytes=").removeSuffix("-").toInt()
            if (start >= payload.size) { ex.sendResponseHeaders(416, -1); ex.close(); return }
            ex.responseHeaders.add("Content-Range", "bytes $start-${payload.size - 1}/${payload.size}")
            ex.sendResponseHeaders(206, (payload.size - start).toLong())
            ex.responseBody.use { it.write(payload, start, payload.size - start) }
        } else {
            val n = if (truncateAt >= 0) truncateAt else payload.size
            ex.sendResponseHeaders(200, payload.size.toLong())
            try { ex.responseBody.use { it.write(payload, 0, n) } } catch (_: Exception) {}
        }
        ex.close()
    }

    @Test fun downloadsAndVerifies() {
        val dest = File(tmp, "rom.zip")
        val r = dl.download(url(), dest, payloadSha, payload.size.toLong())
        assertTrue(r.toString(), r is RomDownloader.Result.Success)
        assertArrayEquals2(payload, dest.readBytes())
        assertFalse(File(tmp, "rom.zip.part").exists())
    }

    @Test fun rejectsChecksumMismatchAndDeletesFile() {
        val dest = File(tmp, "rom.zip")
        val r = dl.download(url(), dest, "0".repeat(64), payload.size.toLong())
        assertTrue(r is RomDownloader.Result.Failure)
        assertTrue((r as RomDownloader.Result.Failure).message.contains("checksum"))
        assertFalse(dest.exists()); assertFalse(File(tmp, "rom.zip.part").exists())
    }

    @Test fun resumesFromPartialFile() {
        val dest = File(tmp, "rom.zip")
        File(tmp, "rom.zip.part").writeBytes(payload.copyOfRange(0, 100_000))
        val r = dl.download(url(), dest, payloadSha, payload.size.toLong())
        assertTrue(r.toString(), r is RomDownloader.Result.Success)
        assertEquals(1, rangeRequests.get())
        assertArrayEquals2(payload, dest.readBytes())
    }

    @Test fun restartsWhenServerIgnoresRange() {
        supportRange = false
        val dest = File(tmp, "rom.zip")
        File(tmp, "rom.zip.part").writeBytes(payload.copyOfRange(0, 50_000))
        val r = dl.download(url(), dest, payloadSha, payload.size.toLong())
        assertTrue(r.toString(), r is RomDownloader.Result.Success)
        assertArrayEquals2(payload, dest.readBytes())
    }

    @Test fun corruptStalePartIsDiscarded() {
        val dest = File(tmp, "rom.zip")
        File(tmp, "rom.zip.part").writeBytes(ByteArray(payload.size + 10)) // longer than the file -> 416
        val r = dl.download(url(), dest, payloadSha, payload.size.toLong())
        assertTrue(r.toString(), r is RomDownloader.Result.Success)
        assertArrayEquals2(payload, dest.readBytes())
    }

    @Test fun truncatedTransferKeepsPartForResume() {
        supportRange = false; truncateAt = 120_000
        val dest = File(tmp, "rom.zip")
        val r = dl.download(url(), dest, payloadSha, payload.size.toLong())
        assertTrue(r is RomDownloader.Result.Failure)
        assertFalse(dest.exists())
        assertTrue(File(tmp, "rom.zip.part").exists())
    }

    @Test fun cancelStopsDownload() {
        val c = AtomicBoolean(true)
        val r = dl.download(url(), File(tmp, "rom.zip"), payloadSha, payload.size.toLong(), cancel = c)
        assertTrue(r is RomDownloader.Result.Cancelled)
        assertFalse(File(tmp, "rom.zip").exists())
    }

    @Test fun refusesCleartextInProduction() {
        val strict = RomDownloader()
        val r = strict.download(url(), File(tmp, "rom.zip"), payloadSha)
        assertTrue(r is RomDownloader.Result.Failure)
    }

    @Test fun httpErrorIsReported() {
        val r = dl.download("http://127.0.0.1:${server.address.port}/missing", File(tmp, "x"), payloadSha)
        assertTrue(r is RomDownloader.Result.Failure)
    }

    private fun assertArrayEquals2(a: ByteArray, b: ByteArray) = assertTrue("byte arrays differ", a.contentEquals(b))
}
