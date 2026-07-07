package com.zhaoshuo.localvideoplayer.util

import android.content.Context
import android.provider.MediaStore
import android.util.LruCache
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
    private const val MAX_ENTRIES = 128
    private const val MAX_CONCURRENT_LOADS = 3

    private val cache = object : LruCache<Long, ImageBitmap>(MAX_ENTRIES) {
        override fun sizeOf(key: Long, value: ImageBitmap) = 1
    }

    private val locks = ConcurrentHashMap<Long, Mutex>()
    private val loadSemaphore = Semaphore(MAX_CONCURRENT_LOADS)

    fun peek(videoId: Long): ImageBitmap? = cache.get(videoId)

    suspend fun load(context: Context, videoId: Long): ImageBitmap? {
        cache.get(videoId)?.let { return it }
        val lock = locks.getOrPut(videoId) { Mutex() }
        return lock.withLock {
            cache.get(videoId) ?: loadSemaphore.withPermit {
                withContext(Dispatchers.IO) {
                    loadThumbnail(context.applicationContext, videoId)?.also { cache.put(videoId, it) }
                }
            }
        }.also {
            locks.remove(videoId)
        }
    }

    private fun loadThumbnail(context: Context, videoId: Long): ImageBitmap? {
        @Suppress("DEPRECATION")
        val bitmap = MediaStore.Video.Thumbnails.getThumbnail(
            context.contentResolver,
            videoId,
            MediaStore.Video.Thumbnails.MINI_KIND,
            null,
        ) ?: return null
        return bitmap.asImageBitmap()
    }
}
