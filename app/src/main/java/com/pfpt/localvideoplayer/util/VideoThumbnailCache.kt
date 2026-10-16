package com.zhaoshuo.localvideoplayer.util

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
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
    const val FRAME_FALLBACK_DELAY_MS = 400L

    private const val MAX_BYTES = 24 * 1024 * 1024
    private const val MAX_CONCURRENT_LOADS = 3
    private val THUMB_SIZE = Size(320, 180)

    private data class Entry(val bitmap: ImageBitmap, val byteCount: Int)

    private val cache = object : LruCache<String, Entry>(MAX_BYTES) {
        override fun sizeOf(key: String, value: Entry) = value.byteCount.coerceAtLeast(1)
    }

    private val locks = ConcurrentHashMap<String, Mutex>()
    private val loadSemaphore = Semaphore(MAX_CONCURRENT_LOADS)
    private val frameSemaphore = Semaphore(1)

    fun peek(uri: Uri, dateModifiedSeconds: Long = 0L): ImageBitmap? {
        return cache.get(thumbnailCacheKey(uri, dateModifiedSeconds))?.bitmap
    }

    suspend fun load(
        context: Context,
        uri: Uri,
        dateModifiedSeconds: Long = 0L,
        allowFrameFallback: Boolean = true,
    ): ImageBitmap? {
        val key = thumbnailCacheKey(uri, dateModifiedSeconds)
        cache.get(key)?.let { return it.bitmap }
        val lock = locks.getOrPut(key) { Mutex() }
        return try {
            lock.withLock {
                cache.get(key)?.bitmap ?: fetchThumbnail(
                    context.applicationContext,
                    uri,
                    allowFrameFallback,
                )?.also { (image, bytes) ->
                    cache.put(key, Entry(image, bytes))
                }?.first
            }
        } finally {
            locks.remove(key)
        }
    }

    private suspend fun fetchThumbnail(
        context: Context,
        uri: Uri,
        allowFrameFallback: Boolean,
    ): Pair<ImageBitmap, Int>? {
        val platform = loadSemaphore.withPermit {
            withContext(Dispatchers.IO) { platformThumbnail(context, uri) }
        }
        val bitmap = platform ?: if (allowFrameFallback) {
            frameSemaphore.withPermit {
                withContext(Dispatchers.IO) { frameThumbnail(context, uri) }
            }
        } else {
            null
        }
        bitmap ?: return null
        return bitmap.asImageBitmap() to bitmap.byteCount.coerceAtLeast(1)
    }

    private fun platformThumbnail(context: Context, uri: Uri): Bitmap? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val loaded = runCatching {
                context.contentResolver.loadThumbnail(uri, THUMB_SIZE, null)
            }.getOrNull()
            if (loaded != null) return loaded
        } else {
            val mediaId = runCatching { ContentUris.parseId(uri) }.getOrNull()
            if (mediaId != null) {
                val legacy = runCatching {
                    @Suppress("DEPRECATION")
                    MediaStore.Video.Thumbnails.getThumbnail(
                        context.contentResolver,
                        mediaId,
                        MediaStore.Video.Thumbnails.MINI_KIND,
                        null,
                    )
                }.getOrNull()
                if (legacy != null) return legacy
            }
        }
        return null
    }

    private fun frameThumbnail(context: Context, uri: Uri): Bitmap? {
        return runCatching {
            MediaMetadataRetriever().use { retriever ->
                retriever.setDataSource(context, uri)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                    retriever.getScaledFrameAtTime(
                        1_000_000L,
                        MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                        THUMB_SIZE.width,
                        THUMB_SIZE.height,
                    )
                } else {
                    retriever.getFrameAtTime(1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                }
            }
        }.getOrNull()
    }
}

fun thumbnailCacheKey(uri: Uri, dateModifiedSeconds: Long = 0L): String {
    val base = uri.toString()
    return if (dateModifiedSeconds > 0L) "$base|$dateModifiedSeconds" else base
}
