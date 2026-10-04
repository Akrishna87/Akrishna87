package io.github.akrishna87.songgrab

import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException
import com.yausername.youtubedl_android.YoutubeDLRequest
import org.json.JSONObject
import java.io.File

/** Runs yt-dlp to fetch one song's audio, with its title, artist and cover art. */
object Grabber {

    data class Info(val id: String, val title: String, val artist: String, val durationSec: Int, val height: Int = 0)

    class Result(val audio: File, val thumbnail: File?, val info: Info)

    sealed interface Event {
        data class Found(val info: Info) : Event
        data class Progress(val percent: Float) : Event
        data object Converting : Event

        /** yt-dlp started downloading another stream; a video comes as picture (1) then sound (2). */
        data class Part(val number: Int) : Event
    }

    private const val INFO = "SGINFO "
    private const val FILE = "SGFILE "
    private val percent = Regex("""^\[download]\s+(\d+(?:\.\d+)?)%""")
    private val postProcessors = listOf("[ExtractAudio]", "[Merger]", "[Metadata]", "[EmbedThumbnail]", "[ThumbnailsConvertor]", "[FixupM4a]")

    /**
     * yt-dlp's options: for a song, the best audio converted to [format]; for a video, the best
     * H.264 picture up to [quality] with AAC sound, merged into an MP4 every phone can play.
     * Either is tagged with a tidied title and artist and the video's thumbnail as cover art. "Artist - Song (Official Video)" becomes
     * artist "Artist", title "Song"; YouTube Music's own track and artist names win when present.
     * The app reads the `SGINFO` and `SGFILE` lines that yt-dlp prints back.
     */
    fun options(format: Format, dir: File, quality: Quality = Quality.P720): List<String> = buildList {
        addAll(listOf("--no-playlist", "--no-quiet", "--no-simulate", "--newline", "--no-mtime"))
        when (format) {
            Format.MP3 -> addAll(listOf("-f", "bestaudio/best", "-x", "--audio-format", "mp3", "--audio-quality", "0"))
            Format.M4A -> addAll(listOf("-f", "bestaudio[ext=m4a]/bestaudio/best", "-x", "--audio-format", "m4a"))
            Format.MP4 -> addAll(
                listOf("-f", "bv*+ba/b", "-S", "res:${quality.height},vcodec:h264,acodec:m4a", "--merge-output-format", "mp4"),
            )
        }
        addAll(listOf("--parse-metadata", "title:(?P<meta_artist>.+?) - (?P<meta_title>.+)"))
        addAll(listOf("--parse-metadata", "%(artist,creator|)s:(?P<meta_artist>.+)"))
        addAll(listOf("--parse-metadata", "%(track|)s:(?P<meta_title>.+)"))
        addAll(
            listOf(
                "--replace-in-metadata", "title,meta_title",
                """\s*[\(\[][^\)\]]*(?i:official|video|audio|lyric|visuali[sz]er|\bHD\b|\b4K\b|\bMV\b)[^\)\]]*[\)\]]""", "",
            ),
        )
        addAll(listOf("--replace-in-metadata", "uploader", " - Topic$", ""))
        addAll(listOf("--embed-metadata", "--embed-thumbnail", "--write-thumbnail", "--convert-thumbnails", "jpg"))
        addAll(listOf("--print", "before_dl:$INFO%(.{id,title,meta_title,meta_artist,artist,creator,uploader,duration,height})j"))
        addAll(listOf("--print", "after_move:$FILE%(filepath)j"))
        addAll(listOf("-o", File(dir, "%(id)s.%(ext)s").absolutePath))
    }

    /** Downloads [url] into [dir]. [processId] lets [cancel] stop it. Blocks until done. */
    fun grab(url: String, format: Format, quality: Quality, dir: File, processId: String, onEvent: (Event) -> Unit): Result {
        val request = YoutubeDLRequest(url)
        val opts = options(format, dir, quality)
        request.addCommands(opts)
        var info: Info? = null
        var path: String? = null
        var parts = 0
        YoutubeDL.getInstance().execute(request, processId, false) { _, _, line ->
            when {
                line.startsWith(INFO) -> parseInfo(line.removePrefix(INFO))?.let {
                    info = it
                    onEvent(Event.Found(it))
                }
                line.startsWith(FILE) -> path = runCatching { JSONObject("{\"p\":${line.removePrefix(FILE)}}").getString("p") }.getOrNull()
                line.startsWith("[download] Destination:") -> onEvent(Event.Part(++parts))
                postProcessors.any { line.startsWith(it) } -> onEvent(Event.Converting)
                else -> parsePercent(line)?.let { onEvent(Event.Progress(it)) }
            }
        }
        val audio = path?.let(::File)?.takeIf { it.isFile }
            ?: dir.listFiles()?.firstOrNull { it.extension.equals(format.ext, ignoreCase = true) }
            ?: throw YoutubeDLException("The download finished but no ${format.label} file was made.")
        val thumbnail = dir.listFiles()?.firstOrNull { it.extension.lowercase() in setOf("jpg", "jpeg", "png") }
        return Result(audio, thumbnail, info ?: Info(audio.nameWithoutExtension, audio.nameWithoutExtension, "", 0))
    }

    fun cancel(processId: String) {
        YoutubeDL.getInstance().destroyProcessById(processId)
    }

    fun parsePercent(line: String): Float? = percent.find(line)?.groupValues?.get(1)?.toFloatOrNull()

    /** Reads the JSON that the `SGINFO` line carries. */
    fun parseInfo(json: String): Info? = try {
        val o = JSONObject(json)
        fun str(key: String) = o.optString(key).trim().takeIf { it.isNotEmpty() && it != "NA" && it != "null" }
        val title = str("meta_title") ?: str("title") ?: str("id") ?: "Untitled"
        val artist = str("meta_artist") ?: str("artist") ?: str("creator") ?: str("uploader")?.removeSuffix(" - Topic") ?: ""
        Info(
            id = str("id") ?: title,
            title = title,
            artist = artist,
            durationSec = o.optDouble("duration", 0.0).takeIf { !it.isNaN() }?.toInt() ?: 0,
            height = o.optInt("height"),
        )
    } catch (e: Exception) {
        null
    }

    /** A short, readable reason for a failed download, from yt-dlp's error output. */
    fun explain(error: Throwable): String {
        val text = error.message.orEmpty()
        val line = text.lines().map { it.trim() }.lastOrNull { it.startsWith("ERROR:") }?.removePrefix("ERROR:")?.trim()
            ?: text.lines().map { it.trim() }.lastOrNull { it.isNotEmpty() }
            ?: error.javaClass.simpleName
        val reason = line.replace(Regex("""^\[[\w:]+]\s*[\w-]+:\s*"""), "")
        return when {
            reason.contains("Sign in to confirm", true) || reason.contains("not a bot", true) ->
                "YouTube wants a sign-in to prove you're not a bot. Try again later, or on another network."
            reason.contains("Private video", true) -> "This video is private."
            reason.contains("age", true) && reason.contains("confirm", true) -> "This video is age-restricted, so it can't be saved without signing in."
            reason.contains("not available", true) || reason.contains("unavailable", true) -> "This video isn't available: $reason"
            reason.contains("Unable to resolve host", true) || reason.contains("Failed to resolve", true) ||
                reason.contains("Network is unreachable", true) || reason.contains("timed out", true) -> "Couldn't reach the internet. Check your connection and try again."
            reason.contains("Unsupported URL", true) -> "That link isn't a video SongGrab can read."
            else -> reason.take(300)
        }
    }

    /** Whether a newer yt-dlp might fix this (YouTube changed something), rather than a network or video problem. */
    fun mightBeFixedByUpdate(error: Throwable): Boolean {
        val text = error.message.orEmpty()
        val notFixable = listOf("Private video", "Unable to resolve host", "Failed to resolve", "Network is unreachable", "Unsupported URL", "timed out")
        return notFixable.none { text.contains(it, ignoreCase = true) }
    }
}
