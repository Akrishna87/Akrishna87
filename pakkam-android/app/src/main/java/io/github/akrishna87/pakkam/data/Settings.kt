package io.github.akrishna87.pakkam.data

import android.content.Context

enum class PaperTheme(val label: String) { DAY("Day"), NIGHT("Night"), SEPIA("Sepia") }

enum class ViewMode(val label: String) { CONTINUOUS("Continuous scroll"), SINGLE("Page by page") }

/** Reading preferences, remembered between documents. */
class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var paperTheme: PaperTheme
        get() = enumValueOrNull<PaperTheme>(prefs.getString("paper", null)) ?: PaperTheme.DAY
        set(v) = prefs.edit().putString("paper", v.name).apply()

    var viewMode: ViewMode
        get() = enumValueOrNull<ViewMode>(prefs.getString("view", null)) ?: ViewMode.CONTINUOUS
        set(v) = prefs.edit().putString("view", v.name).apply()

    var keepScreenOn: Boolean
        get() = prefs.getBoolean("screenOn", false)
        set(v) = prefs.edit().putBoolean("screenOn", v).apply()

    var penColor: Int
        get() = prefs.getInt("penColor", 0xFFD32F2F.toInt())
        set(v) = prefs.edit().putInt("penColor", v).apply()

    var penWidth: Float
        get() = prefs.getFloat("penWidth", 2f)
        set(v) = prefs.edit().putFloat("penWidth", v).apply()

    var highlightColor: Int
        get() = prefs.getInt("hlColor", 0xFFFFEB3B.toInt())
        set(v) = prefs.edit().putInt("hlColor", v).apply()

    var textColor: Int
        get() = prefs.getInt("textColor", 0xFF000000.toInt())
        set(v) = prefs.edit().putInt("textColor", v).apply()

    var speechRate: Float
        get() = prefs.getFloat("speechRate", 1f)
        set(v) = prefs.edit().putFloat("speechRate", v).apply()

    private inline fun <reified T : Enum<T>> enumValueOrNull(name: String?): T? =
        name?.let { n -> enumValues<T>().firstOrNull { it.name == n } }
}
