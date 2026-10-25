package untrusted.vm.core

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class XposedPlugin(
    val id: String,
    val name: String,
    val apkPath: String,
    val enabled: Boolean
)

/**
 * Manages Xposed / SandHook framework plugins.
 * Equivalent to VMOS Pro's vmfwplugin / romex framework:
 * Manages /data/plugins/conf.json to enable or disable in-memory ART method hooks.
 */
class XposedPluginManager(
    private val filesystem: VmFilesystem
) {
    private val pluginsDir = filesystem.resolveGuestFile("/data/plugins")
    private val confFile = File(pluginsDir, "conf.json")

    init {
        if (!pluginsDir.exists()) {
            pluginsDir.mkdirs()
        }
    }

    fun isXposedEnabled(): Boolean {
        val prop = filesystem.resolveGuestFile("/system/build.prop")
        if (!prop.exists()) return false
        return prop.readText(Charsets.UTF_8).contains("persist.xposed.enable=1")
    }

    fun setXposedEnabled(enabled: Boolean) {
        val prop = filesystem.resolveGuestFile("/system/build.prop")
        if (!prop.exists()) return

        var text = prop.readText(Charsets.UTF_8)
        text = if (text.contains("persist.xposed.enable=")) {
            text.replace(Regex("persist\\.xposed\\.enable=[01]"), "persist.xposed.enable=${if (enabled) 1 else 0}")
        } else {
            text + "\npersist.xposed.enable=${if (enabled) 1 else 0}\n"
        }
        prop.writeText(text, Charsets.UTF_8)
    }

    fun getInstalledPlugins(): List<XposedPlugin> {
        if (!confFile.exists()) return emptyList()

        return try {
            val json = JSONObject(confFile.readText(Charsets.UTF_8))
            val array = json.optJSONArray("modules") ?: JSONArray()
            val list = mutableListOf<XposedPlugin>()
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                list.add(
                    XposedPlugin(
                        id = item.optString("id"),
                        name = item.optString("name"),
                        apkPath = item.optString("path"),
                        enabled = item.optBoolean("enabled", true)
                    )
                )
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun setPluginEnabled(pluginId: String, enabled: Boolean) {
        val current = getInstalledPlugins().toMutableList()
        val index = current.indexOfFirst { it.id == pluginId }
        if (index != -1) {
            current[index] = current[index].copy(enabled = enabled)
            savePlugins(current)
        }
    }

    private fun savePlugins(plugins: List<XposedPlugin>) {
        val root = JSONObject()
        val array = JSONArray()
        for (p in plugins) {
            val item = JSONObject()
            item.put("id", p.id)
            item.put("name", p.name)
            item.put("path", p.apkPath)
            item.put("enabled", p.enabled)
            array.put(item)
        }
        root.put("modules", array)
        confFile.writeText(root.toString(2), Charsets.UTF_8)
    }
}
