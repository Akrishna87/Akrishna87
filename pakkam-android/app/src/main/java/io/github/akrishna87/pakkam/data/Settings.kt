package io.github.akrishna87.pakkam.data

import android.content.Context

enum class PaperTheme(val label: String) { DAY("Day"), NIGHT("Night"), SEPIA("Sepia") }

enum class ViewMode(val label: String) { CONTINUOUS("Continuous scroll"), SINGLE("Page by page") }

/** Page colours for Reading mode, like an ebook reader's. */
enum class ReadTheme(val label: String, val background: Long, val text: Long, val dark: Boolean) {
    WHITE("White", 0xFFFFFFFF, 0xFF1C1C1C, false),
    SEPIA("Sepia", 0xFFF5ECD9, 0xFF4B3A2A, false),
    GREEN("Green", 0xFFDCEBD5, 0xFF223322, false),
    BLACK("Black", 0xFF000000, 0xFFCFCFCF, true),
}

/** How Reading mode sets the text. */
data class ReadStyle(
    val sizeSp: Float = 19f,
    val serif: Boolean = true,
    val spacing: Float = 1.45f,
    /** 0 narrow, 1 normal, 2 wide. */
    val margin: Int = 1,
    val theme: ReadTheme = ReadTheme.WHITE,
    val justify: Boolean = true,
) {
    val marginDp get() = when (margin) { 0 -> 14; 2 -> 44; else -> 26 }
}

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

    var readStyle: ReadStyle
        get() = ReadStyle(
            sizeSp = prefs.getFloat("readSize", 19f),
            serif = prefs.getBoolean("readSerif", true),
            spacing = prefs.getFloat("readSpacing", 1.45f),
            margin = prefs.getInt("readMargin", 1),
            theme = enumValueOrNull<ReadTheme>(prefs.getString("readTheme", null)) ?: ReadTheme.WHITE,
            justify = prefs.getBoolean("readJustify", true),
        )
        set(v) = prefs.edit()
            .putFloat("readSize", v.sizeSp)
            .putBoolean("readSerif", v.serif)
            .putFloat("readSpacing", v.spacing)
            .putInt("readMargin", v.margin)
            .putString("readTheme", v.theme.name)
            .putBoolean("readJustify", v.justify)
            .apply()

    /** Where Reading mode stopped in a document, as a position in its running text. */
    fun readPosition(uri: String): Int = prefs.getInt("readPos:$uri", 0)

    fun setReadPosition(uri: String, offset: Int) = prefs.edit().putInt("readPos:$uri", offset).apply()

    private inline fun <reified T : Enum<T>> enumValueOrNull(name: String?): T? =
        name?.let { n -> enumValues<T>().firstOrNull { it.name == n } }
}
