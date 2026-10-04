package io.github.akrishna87.songgrab

/** What to save: a song (MP3 or M4A), or the whole video (MP4). */
enum class Format(val ext: String, val mime: String, val label: String, val hint: String, val isVideo: Boolean = false) {
    MP3("mp3", "audio/mpeg", "MP3", "Plays everywhere"),
    M4A("m4a", "audio/mp4", "M4A", "Original quality"),
    MP4("mp4", "video/mp4", "Video", "MP4 with sound", isVideo = true),
    ;

    companion object {
        fun of(name: String?): Format = entries.firstOrNull { it.name == name } ?: MP3
    }
}

/** The tallest picture to save a video at. Smaller saves space and data. */
enum class Quality(val height: Int, val label: String) {
    P480(480, "480p"),
    P720(720, "720p"),
    P1080(1080, "1080p"),
    ;

    companion object {
        fun of(name: String?): Quality = entries.firstOrNull { it.name == name } ?: P720
    }
}
