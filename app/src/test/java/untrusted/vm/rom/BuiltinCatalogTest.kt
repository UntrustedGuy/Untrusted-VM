package untrusted.vm.rom

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class BuiltinCatalogTest {
    private val json get() = File("src/main/assets/roms_builtin.json").readText()

    @Test fun shippedCatalogIsValid() {
        val r = RomCatalog.parse(json)
        assertTrue("catalog errors: ${r.errors}", r.errors.isEmpty())
        assertEquals(5, r.entries.size)
        assertEquals(r.entries.size, r.entries.map { it.id }.toSet().size)
    }

    @Test fun entriesPointAtPinnedGithubReleasesWithRealChecksums() {
        for (e in RomCatalog.parse(json).entries) {
            assertTrue(e.url, e.url.startsWith("https://github.com/phhusson/treble_experimentations/releases/download/v"))
            assertTrue(e.url, e.url.endsWith(".img.xz"))
            assertTrue(e.sha256.length == 64 && e.sha256 != "0".repeat(64))
            assertTrue(e.sizeBytes > 100_000_000L)
            assertEquals("arm64-v8a", e.abi)
        }
    }
}
