package io.github.akrishna87.myvideos

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import java.io.File
import java.text.Collator
import java.util.Locale

/** A video file on the phone, as Android's media library knows it. */
data class Video(
    val id: Long,
    /** File name without its extension: what people recognise movies and clips by. */
    val title: String,
    val fileName: String,
    val durationMs: Long,
    val sizeBytes: Long,
    val width: Int,
    val height: Int,
    /** Seconds since 1970. */
    val dateAdded: Long,
    /** Folder as shown in the app, e.g. "Movies" or "SD card/Download". */
    val folder: String,
) {
    val uri: Uri get() = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)

    /** Key for remembering how far this video was watched. */
    val key: String get() = id.toString()

    /** "1080p", "4K"… or null when the size isn't known. */
    val quality: String?
        get() {
            val short = minOf(width, height)
            return when {
                short <= 0 -> null
                short >= 2160 -> "4K"
                short >= 1440 -> "1440p"
                short >= 1080 -> "1080p"
                short >= 720 -> "720p"
                short >= 480 -> "480p"
                else -> "${short}p"
            }
        }

    fun matches(query: String): Boolean =
        query.isBlank() || listOf(title, folder).any { it.contains(query.trim(), ignoreCase = true) }

    companion object {
        /**
         * The progress key for a URI another app handed us. Media library URIs map to the same key as
         * the video in our own list, so "Open with" and the library share where you left off.
         */
        fun keyFor(uri: Uri): String {
            val id = uri.lastPathSegment?.toLongOrNull()
            return if (uri.authority == MediaStore.AUTHORITY && uri.path.orEmpty().contains("/video/") && id != null) {
                id.toString()
            } else {
                uri.toString()
            }
        }
    }
}

/** All the videos directly in one folder. */
data class VideoFolder(val path: String, val videos: List<Video>) {
    val name: String get() = path.substringAfterLast('/').ifEmpty { "Internal storage" }
    val parent: String get() = if ('/' in path) path.substringBeforeLast('/') else ""
    val totalBytes: Long get() = videos.sumOf { it.sizeBytes }
}

enum class VideoSort(val label: String) { NEWEST("Newest"), NAME("Name"), LONGEST("Longest"), LARGEST("Largest") }

object Storage {
    const val SD_CARD = "SD card"

    private fun isPrimary(volume: String) =
        volume.isEmpty() || volume.equals("primary", true) || volume.equals("external_primary", true) || volume.equals("external", true)

    /** Folder path as shown in the app: "Movies/Hindi" on the phone, "SD card/Movies" on a memory card. */
    fun displayFolder(volume: String, relativeDir: String): String =
        listOf(if (isPrimary(volume)) "" else SD_CARD, relativeDir.trim('/')).filter { it.isNotEmpty() }.joinToString("/")

    /** Splits "/storage/emulated/0/Movies/a.mp4" or "/storage/1234-ABCD/Movies/a.mp4" into volume + folder. */
    fun splitLegacyPath(path: String): Pair<String, String> {
        val parent = File(path).parent.orEmpty()
        return when {
            parent.startsWith("/storage/emulated/") ->
                "primary" to parent.removePrefix("/storage/emulated/").substringAfter('/', "")
            parent.startsWith("/storage/") -> {
                val rest = parent.removePrefix("/storage/")
                rest.substringBefore('/') to rest.substringAfter('/', "")
            }
            else -> "primary" to parent.trim('/')
        }
    }
}

/** Reads every video Android's media scanner knows about, straight from the phone's storage. */
object VideoRepository {

    @Suppress("DEPRECATION") // MediaStore DATA is the only folder info before Android 10
    fun loadVideos(context: Context): List<Video> {
        val modern = Build.VERSION.SDK_INT >= 29
        val columns = mutableListOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT,
            MediaStore.Video.Media.DATE_ADDED,
        )
        if (modern) {
            columns += MediaStore.Video.Media.RELATIVE_PATH
            columns += MediaStore.Video.Media.VOLUME_NAME
        } else {
            columns += MediaStore.Video.Media.DATA
        }

        val videos = ArrayList<Video>()
        context.contentResolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            columns.toTypedArray(),
            null,
            null,
            null,
        )?.use { c ->
            fun col(name: String) = c.getColumnIndexOrThrow(name)
            val iId = col(MediaStore.Video.Media._ID)
            val iName = col(MediaStore.Video.Media.DISPLAY_NAME)
            val iDuration = col(MediaStore.Video.Media.DURATION)
            val iSize = col(MediaStore.Video.Media.SIZE)
            val iWidth = col(MediaStore.Video.Media.WIDTH)
            val iHeight = col(MediaStore.Video.Media.HEIGHT)
            val iAdded = col(MediaStore.Video.Media.DATE_ADDED)
            val iRelative = if (modern) col(MediaStore.Video.Media.RELATIVE_PATH) else -1
            val iVolume = if (modern) col(MediaStore.Video.Media.VOLUME_NAME) else -1
            val iData = if (modern) -1 else col(MediaStore.Video.Media.DATA)

            fun long(i: Int) = if (c.isNull(i)) 0L else c.getLong(i)
            fun int(i: Int) = if (c.isNull(i)) 0 else c.getInt(i)

            while (c.moveToNext()) {
                val fileName = c.getString(iName).orEmpty()
                val (volume, relativeDir) =
                    if (modern) c.getString(iVolume).orEmpty() to c.getString(iRelative).orEmpty()
                    else Storage.splitLegacyPath(c.getString(iData).orEmpty())
                videos += Video(
                    id = c.getLong(iId),
                    title = fileName.substringBeforeLast('.').ifBlank { fileName.ifBlank { "Video" } },
                    fileName = fileName,
                    durationMs = long(iDuration),
                    sizeBytes = long(iSize),
                    width = int(iWidth),
                    height = int(iHeight),
                    dateAdded = long(iAdded),
                    folder = Storage.displayFolder(volume, relativeDir),
                )
            }
        }
        return videos
    }
}

object VideoGrouping {
    val collator: Collator = Collator.getInstance().apply { strength = Collator.PRIMARY }

    fun sort(videos: List<Video>, sort: VideoSort): List<Video> = when (sort) {
        VideoSort.NEWEST -> videos.sortedByDescending { it.dateAdded }
        VideoSort.NAME -> videos.sortedWith(compareBy(collator) { it.title })
        VideoSort.LONGEST -> videos.sortedByDescending { it.durationMs }
        VideoSort.LARGEST -> videos.sortedByDescending { it.sizeBytes }
    }

    /** Folders holding videos, the one with the newest video first (where new downloads and recordings land). */
    fun folders(videos: List<Video>): List<VideoFolder> =
        videos.groupBy { it.folder }
            .map { (path, list) -> VideoFolder(path, list.sortedByDescending { it.dateAdded }) }
            .sortedByDescending { f -> f.videos.maxOf { it.dateAdded } }
}

object Format {
    /** 1:02:03 or 4:05. */
    fun duration(ms: Long): String {
        val total = (ms / 1000).coerceAtLeast(0)
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s) else String.format(Locale.US, "%d:%02d", m, s)
    }

    /** "1 h 12 min" or "8 min", for "… left". */
    fun minutes(ms: Long): String {
        val mins = ((ms + 59_999) / 60_000).coerceAtLeast(1)
        return if (mins >= 60) "${mins / 60} h ${mins % 60} min" else "$mins min"
    }

    fun size(bytes: Long): String = when {
        bytes >= 1L shl 30 -> String.format(Locale.US, "%.1f GB", bytes / (1L shl 30).toDouble())
        bytes >= 1L shl 20 -> String.format(Locale.US, "%.0f MB", bytes / (1L shl 20).toDouble())
        else -> String.format(Locale.US, "%.0f KB", bytes / 1024.0)
    }
}
