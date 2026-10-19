package io.github.akrishna87.lumen

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import java.io.File
import java.text.Collator
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

/** A photo or video on the phone, as Android's media library knows it. */
data class MediaItem(
    val id: Long,
    val isVideo: Boolean,
    val fileName: String,
    val mimeType: String,
    /** When it was taken (milliseconds), falling back to when the file was last changed. */
    val takenMs: Long,
    val width: Int,
    val height: Int,
    val sizeBytes: Long,
    val durationMs: Long,
    /** Folder as shown in the app, e.g. "Camera" or "Pictures/Screenshots". */
    val folder: String,
    /** Seconds since 1970 when it was moved to the trash (Android 11+), or 0. */
    val trashedUntil: Long = 0,
) {
    val uri: Uri
        get() = ContentUris.withAppendedId(
            if (isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            id,
        )

    /** The album it lives in: the last part of its folder. */
    val albumName: String get() = folder.substringAfterLast('/').ifEmpty { "Internal storage" }

    val date: LocalDate get() = Instant.ofEpochMilli(takenMs).atZone(ZoneId.systemDefault()).toLocalDate()
    val dateTime: LocalDateTime get() = Instant.ofEpochMilli(takenMs).atZone(ZoneId.systemDefault()).toLocalDateTime()

    val isScreenshot: Boolean
        get() = folder.contains("Screenshot", ignoreCase = true) || fileName.startsWith("Screenshot", ignoreCase = true)

    /** Much wider (or taller) than a camera photo. */
    val isPanorama: Boolean
        get() = !isVideo && width > 0 && height > 0 && maxOf(width, height).toFloat() / minOf(width, height) >= 2.4f

    val isAnimated: Boolean get() = mimeType == "image/gif"

    /** "JPEG", "HEIF", "PNG", "MP4"… */
    val format: String
        get() = when (val sub = mimeType.substringAfter('/').uppercase(Locale.US)) {
            "" -> fileName.substringAfterLast('.', "").uppercase(Locale.US)
            "QUICKTIME" -> "MOV"
            "X-MATROSKA" -> "MKV"
            "HEIC" -> "HEIF"
            else -> sub
        }

    /** What a screen reader says for the thumbnail, and what the smoke test looks for. */
    val description: String
        get() = (if (isVideo) "Video" else "Photo") + ", " + dateTime.format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT))

    fun matches(words: List<String>): Boolean {
        val haystack = listOf(fileName, folder, Format.month(date), date.year.toString(), if (isVideo) "video" else "photo",
            if (isScreenshot) "screenshot" else "", if (isPanorama) "panorama" else "").joinToString(" ")
        return words.all { haystack.contains(it, ignoreCase = true) }
    }
}

/** Photos and videos that share a folder. */
data class Album(val folder: String, val items: List<MediaItem>) {
    val name: String get() = items.first().albumName
    val cover: MediaItem get() = items.last()
    val photoCount: Int get() = items.count { !it.isVideo }
    val videoCount: Int get() = items.count { it.isVideo }
}

/** A month in the library, for the Months view. */
data class MonthGroup(val year: Int, val month: Int, val items: List<MediaItem>) {
    val title: String get() = Format.month(items.first().date)
}

/** Reads every photo and video Android's media scanner knows about, straight from storage. */
object MediaRepository {

    private val filesUri: Uri = MediaStore.Files.getContentUri("external")

    private val selection =
        "${MediaStore.Files.FileColumns.MEDIA_TYPE} IN (${MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE}, ${MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO})"

    /** Everything on the phone, oldest first (the Library reads top to bottom, newest at the end). */
    fun load(context: Context): List<MediaItem> = query(context, trashed = false)

    /** Items in the trash, newest first. Only Android 11+ has a trash. */
    fun loadTrash(context: Context): List<MediaItem> =
        if (Build.VERSION.SDK_INT >= 30) query(context, trashed = true).sortedByDescending { it.trashedUntil } else emptyList()

    @Suppress("DEPRECATION") // DATA is the only folder info before Android 10
    private fun query(context: Context, trashed: Boolean): List<MediaItem> {
        val modern = Build.VERSION.SDK_INT >= 29
        val columns = mutableListOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.MEDIA_TYPE,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.DATE_ADDED,
            MediaStore.MediaColumns.DATE_MODIFIED,
            MediaStore.MediaColumns.WIDTH,
            MediaStore.MediaColumns.HEIGHT,
            MediaStore.MediaColumns.SIZE,
            MediaStore.Images.ImageColumns.DATE_TAKEN,
            MediaStore.Video.VideoColumns.DURATION,
            MediaStore.Images.ImageColumns.ORIENTATION,
        )
        if (modern) columns += MediaStore.MediaColumns.RELATIVE_PATH else columns += MediaStore.MediaColumns.DATA
        if (Build.VERSION.SDK_INT >= 30) columns += MediaStore.MediaColumns.DATE_EXPIRES

        val cursor: Cursor? = if (Build.VERSION.SDK_INT >= 30) {
            val args = Bundle().apply {
                putString(android.content.ContentResolver.QUERY_ARG_SQL_SELECTION, selection)
                putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, if (trashed) MediaStore.MATCH_ONLY else MediaStore.MATCH_EXCLUDE)
            }
            context.contentResolver.query(filesUri, columns.toTypedArray(), args, null)
        } else {
            context.contentResolver.query(filesUri, columns.toTypedArray(), selection, null, null)
        }

        val items = ArrayList<MediaItem>()
        cursor?.use { c ->
            fun col(name: String) = c.getColumnIndex(name)
            val iId = col(MediaStore.Files.FileColumns._ID)
            val iType = col(MediaStore.Files.FileColumns.MEDIA_TYPE)
            val iName = col(MediaStore.MediaColumns.DISPLAY_NAME)
            val iMime = col(MediaStore.MediaColumns.MIME_TYPE)
            val iAdded = col(MediaStore.MediaColumns.DATE_ADDED)
            val iModified = col(MediaStore.MediaColumns.DATE_MODIFIED)
            val iWidth = col(MediaStore.MediaColumns.WIDTH)
            val iHeight = col(MediaStore.MediaColumns.HEIGHT)
            val iSize = col(MediaStore.MediaColumns.SIZE)
            val iTaken = col(MediaStore.Images.ImageColumns.DATE_TAKEN)
            val iDuration = col(MediaStore.Video.VideoColumns.DURATION)
            val iOrientation = col(MediaStore.Images.ImageColumns.ORIENTATION)
            val iRelative = if (modern) col(MediaStore.MediaColumns.RELATIVE_PATH) else -1
            val iData = if (modern) -1 else col(MediaStore.MediaColumns.DATA)
            val iExpires = if (Build.VERSION.SDK_INT >= 30) col(MediaStore.MediaColumns.DATE_EXPIRES) else -1

            fun long(i: Int) = if (i < 0 || c.isNull(i)) 0L else c.getLong(i)
            fun int(i: Int) = if (i < 0 || c.isNull(i)) 0 else c.getInt(i)
            fun string(i: Int) = if (i < 0 || c.isNull(i)) "" else c.getString(i)

            while (c.moveToNext()) {
                val isVideo = int(iType) == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
                val taken = long(iTaken).takeIf { it > 0 }
                    ?: long(iModified).takeIf { it > 0 }?.times(1000)
                    ?: (long(iAdded) * 1000)
                val rotated = int(iOrientation) % 180 != 0
                val folder = if (modern) string(iRelative).trim('/') else legacyFolder(string(iData))
                items += MediaItem(
                    id = long(iId),
                    isVideo = isVideo,
                    fileName = string(iName),
                    mimeType = string(iMime),
                    takenMs = taken,
                    width = if (rotated) int(iHeight) else int(iWidth),
                    height = if (rotated) int(iWidth) else int(iHeight),
                    sizeBytes = long(iSize),
                    durationMs = if (isVideo) long(iDuration) else 0,
                    folder = folder,
                    trashedUntil = if (trashed) long(iExpires) else 0,
                )
            }
        }
        items.sortWith(compareBy<MediaItem> { it.takenMs }.thenBy { it.id })
        return items
    }

    /** "/storage/emulated/0/DCIM/Camera/a.jpg" → "DCIM/Camera". */
    private fun legacyFolder(path: String): String {
        val parent = File(path).parent.orEmpty()
        return when {
            parent.startsWith("/storage/emulated/") -> parent.removePrefix("/storage/emulated/").substringAfter('/', "")
            parent.startsWith("/storage/") -> parent.removePrefix("/storage/").substringAfter('/', "")
            else -> parent.trim('/')
        }
    }
}

object Grouping {
    private val collator: Collator = Collator.getInstance().apply { strength = Collator.PRIMARY }

    /** Albums by folder: Camera first, then the rest by their newest photo. */
    fun albums(items: List<MediaItem>): List<Album> =
        items.groupBy { it.folder }
            .map { (folder, list) -> Album(folder, list) }
            .sortedWith(
                compareByDescending<Album> { it.folder.endsWith("Camera", ignoreCase = true) }
                    .thenByDescending { it.cover.takenMs }
                    .thenComparator { a, b -> collator.compare(a.name, b.name) },
            )

    fun months(items: List<MediaItem>): List<MonthGroup> =
        items.groupBy { it.date.year to it.date.monthValue }
            .map { (key, list) -> MonthGroup(key.first, key.second, list) }
            .sortedWith(compareBy({ it.year }, { it.month }))

    fun years(items: List<MediaItem>): List<Pair<Int, List<MediaItem>>> =
        items.groupBy { it.date.year }.toList().sortedBy { it.first }
}

object Format {
    private val monthYear = DateTimeFormatter.ofPattern("LLLL yyyy")
    private val dayMonth = DateTimeFormatter.ofPattern("MMMM d")
    private val dayMonthYear = DateTimeFormatter.ofPattern("MMMM d, yyyy")
    private val weekdayFull = DateTimeFormatter.ofPattern("EEEE")
    private val time = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
    private val infoDate = DateTimeFormatter.ofPattern("EEEE • MMM d, yyyy")

    fun month(date: LocalDate): String = date.format(monthYear)

    /** The viewer's title, like Photos: "Today", "Yesterday", "Monday", "March 3" or "March 3, 2021". */
    fun dayTitle(date: LocalDate, today: LocalDate = LocalDate.now()): String {
        val days = ChronoUnit.DAYS.between(date, today)
        return when {
            days == 0L -> "Today"
            days == 1L -> "Yesterday"
            days in 2..6 -> date.format(weekdayFull)
            date.year == today.year -> date.format(dayMonth)
            else -> date.format(dayMonthYear)
        }
    }

    /** The Library header for a run of days: "Oct 12, 2026" or "Oct 3 – 12, 2026". */
    fun range(first: LocalDate, last: LocalDate): String {
        val short = DateTimeFormatter.ofPattern("MMM d, yyyy")
        if (first == last) return last.format(short)
        return when {
            first.year != last.year -> first.format(short) + " – " + last.format(short)
            first.month != last.month -> first.format(DateTimeFormatter.ofPattern("MMM d")) + " – " + last.format(short)
            else -> first.format(DateTimeFormatter.ofPattern("MMM d")) + " – " + last.dayOfMonth + ", " + last.year
        }
    }

    fun time(dateTime: LocalDateTime): String = dateTime.format(time)

    fun infoDate(dateTime: LocalDateTime): String = dateTime.format(infoDate) + " • " + time(dateTime)

    /** 1:02:03 or 0:05. */
    fun duration(ms: Long): String {
        val total = ((ms + 500) / 1000).coerceAtLeast(0)
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s) else String.format(Locale.US, "%d:%02d", m, s)
    }

    fun size(bytes: Long): String = when {
        bytes >= 1_000_000_000 -> String.format(Locale.US, "%.1f GB", bytes / 1e9)
        bytes >= 1_000_000 -> String.format(Locale.US, "%.1f MB", bytes / 1e6)
        bytes >= 1_000 -> String.format(Locale.US, "%d KB", bytes / 1_000)
        else -> "$bytes bytes"
    }

    /** "12 MP" for photos, or null when it's too small to say. */
    fun megapixels(width: Int, height: Int): String? {
        val mp = width.toLong() * height / 1_000_000.0
        return if (mp >= 1) String.format(Locale.US, if (mp >= 10) "%.0f MP" else "%.1f MP", mp).replace(".0 MP", " MP") else null
    }

    /** "1,234 Photos, 56 Videos", the way Photos counts an album. */
    fun counts(photos: Int, videos: Int): String = listOfNotNull(
        if (photos > 0) plural(photos, "Photo") else null,
        if (videos > 0) plural(videos, "Video") else null,
    ).joinToString(", ").ifEmpty { "No Items" }

    fun plural(n: Int, word: String): String = String.format(Locale.getDefault(), "%,d %s", n, if (n == 1) word else word + "s")

    /** "Selected" bar title: "3 Photos Selected", "1 Video Selected", "4 Items Selected". */
    fun selected(items: List<MediaItem>): String {
        if (items.isEmpty()) return "Select Items"
        val word = when {
            items.all { it.isVideo } -> "Video"
            items.none { it.isVideo } -> "Photo"
            else -> "Item"
        }
        return plural(items.size, word) + " Selected"
    }
}
