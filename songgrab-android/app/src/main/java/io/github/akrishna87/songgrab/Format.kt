package io.github.akrishna87.songgrab

/** What to save the audio as. */
enum class Format(val ext: String, val mime: String, val label: String, val hint: String) {
    MP3("mp3", "audio/mpeg", "MP3", "Plays everywhere"),
    M4A("m4a", "audio/mp4", "M4A", "Faster, original quality"),
    ;

    companion object {
        fun of(name: String?): Format = entries.firstOrNull { it.name == name } ?: MP3
    }
}
