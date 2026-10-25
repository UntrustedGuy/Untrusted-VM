package untrusted.vm.rom

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** One downloadable ROM as described by a catalog. */
data class RomEntry(
    val id: String,
    val name: String,
    val androidVersion: String,
    val apiLevel: Int,
    val abi: String,
    val url: String,
    val sha256: String,
    val sizeBytes: Long,
    val rootPreinstalled: Boolean = false,
    val hasGms: Boolean = false,
    val description: String = ""
)

/**
 * Parses and validates a ROM catalog. The app ships NO built-in download URLs: the user
 * points it at a catalog they trust. Because of that, validation is strict, and every
 * remote entry must carry a SHA-256 so a tampered download is always rejected.
 *
 * Accepted shapes: {"version":1,"roms":[...]} or a bare [...] array.
 */
object RomCatalog {

    data class ParseResult(val entries: List<RomEntry>, val errors: List<String>)

    const val MAX_CATALOG_BYTES = 2 * 1024 * 1024
    val ABIS = setOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")

    private val ID_RE = Regex("^[a-z0-9][a-z0-9_.-]{0,63}$")
    private val SHA_RE = Regex("^[0-9a-fA-F]{64}$")

    @JvmStatic
    fun isValidId(id: String): Boolean = ID_RE.matches(id)

    fun parse(json: String): ParseResult {
        val text = json.trim()
        val arr: JSONArray = try {
            if (text.startsWith("[")) {
                JSONArray(text)
            } else {
                JSONObject(text).optJSONArray("roms")
                    ?: return ParseResult(emptyList(), listOf("catalog has no \"roms\" array"))
            }
        } catch (e: JSONException) {
            return ParseResult(emptyList(), listOf("invalid JSON: ${e.message}"))
        }

        val entries = ArrayList<RomEntry>()
        val errors = ArrayList<String>()
        val seen = HashSet<String>()

        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i)
            if (o == null) {
                errors.add("#$i: not an object")
                continue
            }
            val id = o.optString("id", "")
            val label = if (id.isEmpty()) "#$i" else id
            val url = o.optString("url", "")
            val sha = o.optString("sha256", "")
            val abi = o.optString("abi", "")
            val size = o.optLong("sizeBytes", -1L)

            val problem = when {
                !ID_RE.matches(id) -> "invalid id (use a-z 0-9 _ . -)"
                !seen.add(id) -> "duplicate id"
                o.optString("name", "").isBlank() -> "missing name"
                !url.startsWith("https://") -> "url must be https"
                !SHA_RE.matches(sha) -> "sha256 must be 64 hex characters"
                abi !in ABIS -> "unknown abi '$abi'"
                size < 0 -> "sizeBytes must be >= 0"
                o.optInt("apiLevel", 0) <= 0 -> "apiLevel must be > 0"
                else -> null
            }
            if (problem != null) {
                errors.add("$label: $problem")
                continue
            }
            entries.add(
                RomEntry(
                    id = id,
                    name = o.optString("name"),
                    androidVersion = o.optString("androidVersion", ""),
                    apiLevel = o.optInt("apiLevel", 0),
                    abi = abi,
                    url = url,
                    sha256 = sha.lowercase(),
                    sizeBytes = size,
                    rootPreinstalled = o.optBoolean("rootPreinstalled", false),
                    hasGms = o.optBoolean("hasGms", false),
                    description = o.optString("description", "")
                )
            )
        }
        return ParseResult(entries, errors)
    }
}
