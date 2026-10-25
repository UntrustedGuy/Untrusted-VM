package untrusted.vm.core

import org.json.JSONObject

/**
 * Configuration for an Untrusted VM instance.
 * Features per-instance simulated root toggle inspired by VMOS Pro.
 */
data class VmConfig @JvmOverloads constructor(
    val id: Int,
    val name: String = "Untrusted VM $id",
    val androidVersion: String = "7.1.2",
    val isRootEnabled: Boolean = false,
    val resolutionWidth: Int = 720,
    val resolutionHeight: Int = 1280,
    val dpi: Int = 320,
    val allocatedRamMb: Int = 2048,
    val cpuCores: Int = 4,
    val enableGoogleServices: Boolean = false,
    val autoStart: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val baseRomId: String = ""
) {
    fun withRootEnabled(newRootState: Boolean): VmConfig = copy(isRootEnabled = newRootState)
    fun withResolution(width: Int, height: Int): VmConfig = copy(resolutionWidth = width, resolutionHeight = height)
    fun withDpi(newDpi: Int): VmConfig = copy(dpi = newDpi)
    fun withAndroidVersion(newVersion: String): VmConfig = copy(androidVersion = newVersion)
    fun withBaseRom(romId: String): VmConfig = copy(baseRomId = romId)

    fun isEnableGoogleServices(): Boolean = enableGoogleServices
    fun isAutoStart(): Boolean = autoStart

    fun toJson(): String {
        val json = JSONObject()
        json.put("id", id)
        json.put("name", name)
        json.put("androidVersion", androidVersion)
        json.put("isRootEnabled", isRootEnabled)
        json.put("resolutionWidth", resolutionWidth)
        json.put("resolutionHeight", resolutionHeight)
        json.put("dpi", dpi)
        json.put("allocatedRamMb", allocatedRamMb)
        json.put("cpuCores", cpuCores)
        json.put("enableGoogleServices", enableGoogleServices)
        json.put("autoStart", autoStart)
        json.put("createdAt", createdAt)
        json.put("baseRomId", baseRomId)
        return json.toString()
    }

    companion object {
        @JvmStatic
        fun fromJson(jsonStr: String): VmConfig {
            val json = JSONObject(jsonStr)
            val id = json.optInt("id", 1)
            val name = json.optString("name", "VM $id")
            return VmConfig(
                id = id,
                name = name,
                androidVersion = json.optString("androidVersion", "7.1.2"),
                isRootEnabled = json.optBoolean("isRootEnabled", false),
                resolutionWidth = json.optInt("resolutionWidth", 720),
                resolutionHeight = json.optInt("resolutionHeight", 1280),
                dpi = json.optInt("dpi", 320),
                allocatedRamMb = json.optInt("allocatedRamMb", 2048),
                cpuCores = json.optInt("cpuCores", 4),
                enableGoogleServices = json.optBoolean("enableGoogleServices", false),
                autoStart = json.optBoolean("autoStart", false),
                createdAt = json.optLong("createdAt", System.currentTimeMillis()),
                baseRomId = json.optString("baseRomId", "")
            )
        }
    }
}
