package com.zhaoshuo.localvideoplayer.util

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object SeekPreviewFrames {
    private const val BUCKET_MS = 2_000L
    private val cache = object : LruCache<String, Bitmap>(8) {
        override fun entryRemoved(evicted: Boolean, key: String, oldValue: Bitmap, newValue: Bitmap?) {
            if (key == pinnedKey || oldValue === newValue || oldValue.isRecycled) return
            oldValue.recycle()
        }
    }
    private val retrieverLock = Any()
    private var openUri: String? = null
    private var retriever: MediaMetadataRetriever? = null
    private var pinnedKey: String? = null

    fun cacheKey(uri: String, positionMs: Long): String {
        val bucket = positionMs.coerceAtLeast(0L) / BUCKET_MS
        return "$uri@$bucket"
    }

    fun release() {
        synchronized(retrieverLock) {
            closeRetrieverLocked()
            val keepKey = pinnedKey
            val keepBitmap = keepKey?.let { cache.get(it) }
            cache.evictAll()
            if (keepKey != null && keepBitmap != null && !keepBitmap.isRecycled) {
                cache.put(keepKey, keepBitmap)
            } else {
                pinnedKey = null
            }
        }
    }

    suspend fun load(context: Context, uri: Uri, positionMs: Long): ImageBitmap? {
        val key = cacheKey(uri.toString(), positionMs)
        cachedBitmap(key)?.let { return it.asImageBitmap() }
        return withContext(Dispatchers.IO) {
            cachedBitmap(key)?.let { return@withContext it.asImageBitmap() }
            val bitmap = capture(context.applicationContext, uri, positionMs) ?: return@withContext null
            synchronized(retrieverLock) {
                pinnedKey = key
                cache.put(key, bitmap)
            }
            bitmap.asImageBitmap()
        }
    }

    private fun cachedBitmap(key: String): Bitmap? {
        return synchronized(retrieverLock) {
            val bitmap = cache.get(key) ?: return@synchronized null
            if (bitmap.isRecycled) return@synchronized null
            pinnedKey = key
            bitmap
        }
    }

    private fun capture(context: Context, uri: Uri, positionMs: Long): Bitmap? {
        val bucketStart = (positionMs.coerceAtLeast(0L) / BUCKET_MS) * BUCKET_MS
        val timeUs = bucketStart * 1_000L
        return synchronized(retrieverLock) {
            val active = openRetrieverLocked(context, uri) ?: return@synchronized null
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                    active.getScaledFrameAtTime(
                        timeUs,
                        MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                        320,
                        180,
                    )
                } else {
                    active.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                }
            }.getOrNull()
        }
    }

    private fun openRetrieverLocked(context: Context, uri: Uri): MediaMetadataRetriever? {
        val key = uri.toString()
        if (openUri == key) return retriever
        closeRetrieverLocked()
        val created = MediaMetadataRetriever()
        return try {
            created.setDataSource(context, uri)
            retriever = created
            openUri = key
            created
        } catch (_: Exception) {
            runCatching { created.release() }
            null
        }
    }

    private fun closeRetrieverLocked() {
        retriever?.let { runCatching { it.release() } }
        retriever = null
        openUri = null
    }
}
