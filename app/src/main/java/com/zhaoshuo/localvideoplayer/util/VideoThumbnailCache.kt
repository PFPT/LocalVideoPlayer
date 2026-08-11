package com.zhaoshuo.localvideoplayer.util

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.LruCache
import android.util.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

object VideoThumbnailCache {
    private const val MAX_BYTES = 24 * 1024 * 1024
    private const val MAX_CONCURRENT_LOADS = 3
    private val THUMB_SIZE = Size(320, 180)

    private data class Entry(val bitmap: ImageBitmap, val byteCount: Int)

    private val cache = object : LruCache<Long, Entry>(MAX_BYTES) {
        override fun sizeOf(key: Long, value: Entry) = value.byteCount.coerceAtLeast(1)
    }

    private val locks = ConcurrentHashMap<Long, Mutex>()
    private val loadSemaphore = Semaphore(MAX_CONCURRENT_LOADS)

    fun peek(videoId: Long): ImageBitmap? = cache.get(videoId)?.bitmap

    suspend fun load(context: Context, videoId: Long): ImageBitmap? {
        cache.get(videoId)?.let { return it.bitmap }
        val lock = locks.getOrPut(videoId) { Mutex() }
        return lock.withLock {
            cache.get(videoId)?.bitmap ?: loadSemaphore.withPermit {
                withContext(Dispatchers.IO) {
                    loadThumbnail(context.applicationContext, videoId)?.also { (image, bytes) ->
                        cache.put(videoId, Entry(image, bytes))
                    }?.first
                }
            }
        }.also {
            locks.remove(videoId)
        }
    }

    private fun loadThumbnail(context: Context, videoId: Long): Pair<ImageBitmap, Int>? {
        val uri = Uri.withAppendedPath(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            videoId.toString(),
        )
        val bitmap: Bitmap = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                context.contentResolver.loadThumbnail(uri, THUMB_SIZE, null)
            } else {
                @Suppress("DEPRECATION")
                MediaStore.Video.Thumbnails.getThumbnail(
                    context.contentResolver,
                    videoId,
                    MediaStore.Video.Thumbnails.MINI_KIND,
                    null,
                )
            }
        } catch (_: Exception) {
            null
        } ?: return null
        return bitmap.asImageBitmap() to bitmap.byteCount.coerceAtLeast(1)
    }
}
