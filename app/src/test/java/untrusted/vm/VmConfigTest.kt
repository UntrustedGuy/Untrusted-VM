package untrusted.vm

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import untrusted.vm.core.VmConfig

class VmConfigTest {

    @Test
    fun testDefaultConfigValues() {
        val config = VmConfig(id = 1, name = "Untrusted VM 1")
        assertEquals(1, config.id)
        assertEquals("Untrusted VM 1", config.name)
        assertEquals("7.1.2", config.androidVersion)
        assertFalse(config.isRootEnabled)
        assertEquals(720, config.resolutionWidth)
        assertEquals(1280, config.resolutionHeight)
        assertEquals(320, config.dpi)
        assertEquals(2048, config.allocatedRamMb)
    }

    @Test
    fun testJsonSerializationWithRootEnabled() {
        val config = VmConfig(
            id = 2,
            name = "Rooted Sandbox",
            androidVersion = "9.0",
            isRootEnabled = true,
            resolutionWidth = 1080,
            resolutionHeight = 1920,
            allocatedRamMb = 4096
        )

        val jsonStr = config.toJson()
        val jsonObj = JSONObject(jsonStr)
        assertTrue(jsonObj.getBoolean("isRootEnabled"))
        assertEquals("Rooted Sandbox", jsonObj.getString("name"))

        val parsed = VmConfig.fromJson(jsonStr)
        assertEquals(config.id, parsed.id)
        assertEquals(config.name, parsed.name)
        assertEquals(config.androidVersion, parsed.androidVersion)
        assertTrue(parsed.isRootEnabled)
        assertEquals(1080, parsed.resolutionWidth)
        assertEquals(1920, parsed.resolutionHeight)
        assertEquals(4096, parsed.allocatedRamMb)
    }

    @Test
    fun testJsonSerializationWithRootDisabled() {
        val config = VmConfig(
            id = 3,
            name = "Clean Stock VM",
            isRootEnabled = false
        )

        val jsonStr = config.toJson()
        val jsonObj = JSONObject(jsonStr)
        assertFalse(jsonObj.getBoolean("isRootEnabled"))

        val parsed = VmConfig.fromJson(jsonStr)
        assertFalse(parsed.isRootEnabled)
    }

    @Test
    fun testEdgeCasesInJsonParsing() {
        val minimalJson = "{}"
        val parsed = VmConfig.fromJson(minimalJson)
        assertEquals(1, parsed.id)
        assertFalse(parsed.isRootEnabled)
        assertEquals("VM 1", parsed.name)
    }
}
