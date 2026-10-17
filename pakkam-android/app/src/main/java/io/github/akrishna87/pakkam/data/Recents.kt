package io.github.akrishna87.pakkam.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class RecentFile(
    val uri: String,
    val name: String,
    val size: Long,
    val pages: Int,
    val lastPage: Int,
    val opened: Long,
    val starred: Boolean,
    val bookmarks: List<Int> = emptyList(),
)

/** The documents opened lately, with where the reader stopped and their bookmarks. */
class Recents(context: Context) {
    private val prefs = context.getSharedPreferences("recents", Context.MODE_PRIVATE)

    fun all(): List<RecentFile> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i -> fromJson(arr.getJSONObject(i)) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun get(uri: String): RecentFile? = all().firstOrNull { it.uri == uri }

    fun put(file: RecentFile) {
        val list = all().filter { it.uri != file.uri }.toMutableList()
        list.add(0, file)
        // Starred files are kept however old they are.
        val trimmed = list.filterIndexed { i, f -> f.starred || i < MAX }
        save(trimmed)
    }

    fun update(uri: String, change: (RecentFile) -> RecentFile) {
        save(all().map { if (it.uri == uri) change(it) else it })
    }

    fun remove(uri: String) = save(all().filter { it.uri != uri })

    fun clear() = save(all().filter { it.starred })

    private fun save(list: List<RecentFile>) {
        val arr = JSONArray()
        list.forEach { arr.put(toJson(it)) }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    private fun toJson(f: RecentFile) = JSONObject().apply {
        put("uri", f.uri)
        put("name", f.name)
        put("size", f.size)
        put("pages", f.pages)
        put("lastPage", f.lastPage)
        put("opened", f.opened)
        put("starred", f.starred)
        put("bookmarks", JSONArray(f.bookmarks))
    }

    private fun fromJson(o: JSONObject): RecentFile? {
        val uri = o.optString("uri").takeIf { it.isNotEmpty() } ?: return null
        val marks = o.optJSONArray("bookmarks")
        return RecentFile(
            uri = uri,
            name = o.optString("name", "Document.pdf"),
            size = o.optLong("size", -1),
            pages = o.optInt("pages", 0),
            lastPage = o.optInt("lastPage", 0),
            opened = o.optLong("opened", 0),
            starred = o.optBoolean("starred", false),
            bookmarks = if (marks == null) emptyList() else (0 until marks.length()).map { marks.getInt(it) },
        )
    }

    companion object {
        private const val KEY = "files"
        private const val MAX = 60
    }
}
