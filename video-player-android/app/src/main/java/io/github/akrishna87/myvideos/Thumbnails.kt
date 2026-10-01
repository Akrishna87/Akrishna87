package io.github.akrishna87.myvideos

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.provider.MediaStore
import android.util.LruCache
import android.util.Size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.util.Collections

/** Video thumbnails from Android's media library, with a small in-memory cache. */
object Thumbnails {
    private const val WIDTH = 384
    private const val HEIGHT = 216

    private val cache = object : LruCache<Long, Bitmap>((Runtime.getRuntime().maxMemory() / 8).toInt()) {
        override fun sizeOf(key: Long, value: Bitmap) = value.byteCount
    }
    private val missing: MutableSet<Long> = Collections.synchronizedSet(HashSet())
    private val limiter = Semaphore(3)

    fun cached(video: Video): Bitmap? = cache.get(video.id)

    suspend fun load(context: Context, video: Video): Bitmap? {
        cache.get(video.id)?.let { return it }
        if (video.id in missing) return null
        return limiter.withPermit {
            withContext(Dispatchers.IO) {
                val bmp = decode(context.applicationContext, video)
                if (bmp != null) cache.put(video.id, bmp) else missing += video.id
                bmp
            }
        }
    }

    @Suppress("DEPRECATION") // the only thumbnail API before Android 10
    private fun decode(context: Context, video: Video): Bitmap? = try {
        if (Build.VERSION.SDK_INT >= 29) {
            context.contentResolver.loadThumbnail(video.uri, Size(WIDTH, HEIGHT), null)
        } else {
            MediaStore.Video.Thumbnails.getThumbnail(context.contentResolver, video.id, MediaStore.Video.Thumbnails.MINI_KIND, null)
        }
    } catch (e: Exception) {
        null // a file Android can't read a frame from, or one that has gone
    }
}
