package untrusted.vm.rom

import org.junit.Assert.*
import org.junit.Test

class RomCatalogTest {
    private val sha = "a".repeat(64)
    private fun entry(id: String = "a12", extra: String = "") = """
        {"id":"$id","name":"Android 12","androidVersion":"12","apiLevel":31,"abi":"arm64-v8a",
         "url":"https://example.com/a12.zip","sha256":"$sha","sizeBytes":1000 $extra}"""

    @Test fun parsesObjectAndArrayForms() {
        val a = RomCatalog.parse("""{"version":1,"roms":[${entry()}]}""")
        val b = RomCatalog.parse("[${entry()}]")
        assertEquals(1, a.entries.size); assertTrue(a.errors.isEmpty())
        assertEquals(a.entries, b.entries)
        assertEquals("a12", a.entries[0].id)
        assertEquals(31, a.entries[0].apiLevel)
    }

    @Test fun lowercasesSha() {
        val r = RomCatalog.parse("[${entry().replace(sha, "A".repeat(64))}]")
        assertEquals(sha, r.entries[0].sha256)
    }

    @Test fun rejectsInsecureOrIncompleteEntriesButKeepsGoodOnes() {
        val bad = listOf(
            entry("http1").replace("https://", "http://"),
            entry("nosha").replace(sha, ""),
            entry("shortsha").replace(sha, "abc"),
            entry("badabi").replace("arm64-v8a", "mips"),
            entry("../evil"),
            entry("UPPER"),
            entry("noapi").replace("\"apiLevel\":31", "\"apiLevel\":0")
        )
        val r = RomCatalog.parse("[${(bad + entry("good")).joinToString(",")}]")
        assertEquals(listOf("good"), r.entries.map { it.id })
        assertEquals(bad.size, r.errors.size)
    }

    @Test fun rejectsDuplicateIds() {
        val r = RomCatalog.parse("[${entry("dup")},${entry("dup")}]")
        assertEquals(1, r.entries.size)
        assertEquals(1, r.errors.size)
    }

    @Test fun garbageInputDoesNotThrow() {
        assertTrue(RomCatalog.parse("not json").errors.isNotEmpty())
        assertTrue(RomCatalog.parse("{}").errors.isNotEmpty())
        assertTrue(RomCatalog.parse("[1,2,3]").entries.isEmpty())
    }
}
