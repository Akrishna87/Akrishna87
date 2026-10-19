package io.github.akrishna87.lumen

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Build
import android.provider.MediaStore
import android.util.LruCache
import android.util.Size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.util.Collections

/** Grid thumbnails from Android's media library, with an in-memory cache so scrolling back is instant. */
object Thumbnails {
    private const val SIZE = 320

    private val cache = object : LruCache<Long, Bitmap>((Runtime.getRuntime().maxMemory() / 6).toInt()) {
        override fun sizeOf(key: Long, value: Bitmap) = value.byteCount
    }
    private val missing: MutableSet<Long> = Collections.synchronizedSet(HashSet())
    private val limiter = Semaphore(4)

    fun cached(item: MediaItem): Bitmap? = cache.get(item.id)

    suspend fun load(context: Context, item: MediaItem): Bitmap? {
        cache.get(item.id)?.let { return it }
        if (item.id in missing) return null
        return limiter.withPermit {
            cache.get(item.id) ?: withContext(Dispatchers.IO) {
                val bmp = decode(context.applicationContext, item)
                if (bmp != null) cache.put(item.id, bmp) else missing += item.id
                bmp
            }
        }
    }

    @Suppress("DEPRECATION") // the only thumbnail APIs before Android 10
    private fun decode(context: Context, item: MediaItem): Bitmap? = try {
        if (Build.VERSION.SDK_INT >= 29) {
            context.contentResolver.loadThumbnail(item.uri, Size(SIZE, SIZE), null)
        } else if (item.isVideo) {
            MediaStore.Video.Thumbnails.getThumbnail(context.contentResolver, item.id, MediaStore.Video.Thumbnails.MINI_KIND, null)
        } else {
            MediaStore.Images.Thumbnails.getThumbnail(context.contentResolver, item.id, MediaStore.Images.Thumbnails.MINI_KIND, null)
                ?.let { rotate(it, orientation(context, item)) }
        }
    } catch (e: Exception) {
        null // a file Android can't read, or one that has gone
    }

    @Suppress("DEPRECATION")
    private fun orientation(context: Context, item: MediaItem): Int =
        context.contentResolver.query(item.uri, arrayOf(MediaStore.Images.ImageColumns.ORIENTATION), null, null, null)
            ?.use { if (it.moveToFirst()) it.getInt(0) else 0 } ?: 0

    private fun rotate(bitmap: Bitmap, degrees: Int): Bitmap =
        if (degrees % 360 == 0) bitmap
        else Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply { postRotate(degrees.toFloat()) }, true)
}
