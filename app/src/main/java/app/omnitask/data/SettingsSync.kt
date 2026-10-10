package app.omnitask.data

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject

/**
 * Keeps the app's settings in the vault as `หลังบ้าน/Omni/omni-settings.json`, so they survive clearing the app
 * and follow the vault to another phone. The phone's own preferences stay the working copy; the file
 * wins when it was saved more recently than this phone last synced.
 */
object SettingsSync {

    const val PATH = "${VaultText.OMNI_DIR}/omni-settings.json"

    /** Where the file was before the Omni folder moved into the back-office folder; read when the new one is not there. */
    const val LEGACY_PATH = "Omni/omni-settings.json"

    /** The settings file text: the new place, else the old one. */
    fun read(repo: VaultRepository, vault: Uri): String? = repo.readPath(vault, PATH) ?: repo.readPath(vault, LEGACY_PATH)

    private const val PREFS = "omnitask"
    private const val KEY_SYNCED_AT = "settings.syncedAt"

    /** Device-specific or derived keys that must not travel: the folder grant, the scheduled alarm ids and the calendar ids (they differ per phone). */
    private val LOCAL = setOf("vault", "alarmIds", KEY_SYNCED_AT, CalendarReader.KEY_HIDDEN)

    private fun prefs(context: Context): SharedPreferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun toJson(context: Context, now: Long = System.currentTimeMillis()): String {
        val values = JSONObject()
        prefs(context).all.toSortedMap().forEach { (key, value) ->
            if (key in LOCAL || value == null) return@forEach
            values.put(
                key,
                when (value) {
                    is Set<*> -> JSONObject().put("set", JSONArray(value.map { it.toString() }.sorted()))
                    is Int -> JSONObject().put("int", value)
                    is Long -> JSONObject().put("long", value)
                    is Float -> JSONObject().put("float", value.toDouble())
                    is Boolean -> JSONObject().put("bool", value)
                    else -> JSONObject().put("string", value.toString())
                },
            )
        }
        return JSONObject().put("version", 1).put("savedAt", now).put("settings", values).toString(2)
    }

    /** Writes the file and records the time, so this phone's own save is not read back as newer. */
    fun save(context: Context, repo: VaultRepository, vault: Uri) {
        val now = System.currentTimeMillis()
        val json = toJson(context, now)
        // Same settings as the file already holds: leave it alone, so two phones don't keep rewriting it.
        val existing = read(repo, vault)?.let { runCatching { JSONObject(it) }.getOrNull() }
        if (existing != null && existing.optJSONObject("settings")?.toString() == JSONObject(json).optJSONObject("settings")?.toString()) {
            prefs(context).edit().putLong(KEY_SYNCED_AT, existing.optLong("savedAt", now)).apply()
            return
        }
        repo.writePath(vault, PATH, json)
        prefs(context).edit().putLong(KEY_SYNCED_AT, now).apply()
    }

    /** Copies an older build left by saving the file as Markdown: `omni-settings.json.md`, `omni-settings.json (3).md`... */
    private val STRAY = Regex("""omni-settings\.json(?: \(\d+\))?\.md""")

    /**
     * Leaves one settings file, at [PATH]. The copies above, in either Omni folder, and a file still at [LEGACY_PATH]
     * are folded in: the most recently saved of them all is kept (each save held every setting, so the newest is
     * complete), and the rest are deleted, only once [PATH] is there.
     */
    fun tidy(repo: VaultRepository, vault: Uri) {
        val dirs = listOf(PATH, LEGACY_PATH).map { it.substringBeforeLast('/') }
        val extra = dirs.flatMap { dir -> repo.namesIn(vault, dir).filter { STRAY.matches(it) }.map { "$dir/$it" } } +
            listOfNotNull(LEGACY_PATH.takeIf { repo.readPath(vault, it) != null })
        if (extra.isEmpty()) return
        val current = repo.readPath(vault, PATH)
        val savedAt = { text: String -> runCatching { JSONObject(text).optLong("savedAt", 0) }.getOrDefault(-1L) }
        val newest = (extra.mapNotNull { repo.readPath(vault, it) } + listOfNotNull(current)).maxByOrNull(savedAt)
        if (newest != null && newest != current && savedAt(newest) >= 0) repo.writePath(vault, PATH, newest)
        if (repo.readPath(vault, PATH) != null) extra.forEach { repo.deletePath(vault, it) }
    }

    /** Applies the file if it is newer than this phone's last sync. Returns true when anything changed. */
    fun load(context: Context, repo: VaultRepository, vault: Uri): Boolean {
        val text = read(repo, vault) ?: return false
        val root = runCatching { JSONObject(text) }.getOrNull() ?: return false
        val savedAt = root.optLong("savedAt", 0)
        val p = prefs(context)
        if (savedAt <= p.getLong(KEY_SYNCED_AT, 0)) return false
        val settings = root.optJSONObject("settings") ?: return false
        val edit = p.edit()
        settings.keys().forEach { key ->
            if (key in LOCAL) return@forEach
            val v = settings.optJSONObject(key) ?: return@forEach
            when {
                v.has("set") -> v.optJSONArray("set")?.let { a -> edit.putStringSet(key, (0 until a.length()).map { a.optString(it) }.toSet()) }
                v.has("int") -> edit.putInt(key, v.optInt("int"))
                v.has("long") -> edit.putLong(key, v.optLong("long"))
                v.has("float") -> edit.putFloat(key, v.optDouble("float").toFloat())
                v.has("bool") -> edit.putBoolean(key, v.optBoolean("bool"))
                v.has("string") -> edit.putString(key, v.optString("string"))
            }
        }
        edit.putLong(KEY_SYNCED_AT, savedAt).commit()
        return true
    }
}
