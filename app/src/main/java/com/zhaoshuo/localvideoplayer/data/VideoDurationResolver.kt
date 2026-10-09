package com.zhaoshuo.localvideoplayer.data

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.zhaoshuo.localvideoplayer.model.VideoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.File

class VideoDurationResolver(
    context: Context,
    private val cache: FolderDurationCache = FolderDurationCache(
        File(context.applicationContext.filesDir, "video_duration_cache.json"),
    ),
) {
    private val appContext = context.applicationContext
    private var cacheLoaded = false

    suspend fun resolveAll(videos: List<VideoItem>): Map<String, Long> = coroutineScope {
        if (videos.isEmpty()) return@coroutineScope emptyMap()
        ensureCacheLoaded()
        val semaphore = Semaphore(PARALLELISM)
        val pairs = videos.map { video ->
            async(Dispatchers.IO) {
                semaphore.withPermit {
                    video.uri.toString() to readDurationMs(video.uri, video.dateModified)
                }
            }
        }.awaitAll()
        cache.save()
        pairs.filter { it.second > 0L }.toMap()
    }

    private fun readDurationMs(uri: Uri, modifiedMs: Long): Long {
        cache.get(uri.toString(), modifiedMs)?.let { return it }
        val duration = readFromExtractor(uri).takeIf { it > 0L } ?: readFromRetriever(uri)
        if (duration > 0L) {
            cache.put(uri.toString(), modifiedMs, duration)
        }
        return duration
    }

    private fun readFromRetriever(uri: Uri): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            if (!bindRetriever(retriever, uri)) return 0L
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
                ?: 0L
        } catch (_: Exception) {
            0L
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun bindRetriever(retriever: MediaMetadataRetriever, uri: Uri): Boolean {
        val descriptor = openDescriptor(uri)
        if (descriptor != null) {
            try {
                descriptor.use { file ->
                    val length = file.statSize
                    if (length > 0L) {
                        retriever.setDataSource(file.fileDescriptor, 0, length)
                    } else {
                        retriever.setDataSource(file.fileDescriptor)
                    }
                }
                return true
            } catch (_: Exception) {
            }
        }
        return try {
            retriever.setDataSource(appContext, uri)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun readFromExtractor(uri: Uri): Long {
        val extractor = MediaExtractor()
        return try {
            if (!bindExtractor(extractor, uri)) return 0L
            var maxUs = 0L
            for (index in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(index)
                if (format.containsKey(MediaFormat.KEY_DURATION)) {
                    maxUs = maxOf(maxUs, format.getLong(MediaFormat.KEY_DURATION))
                }
            }
            if (maxUs > 0L) maxUs / 1_000L else 0L
        } catch (_: Exception) {
            0L
        } finally {
            runCatching { extractor.release() }
        }
    }

    private fun bindExtractor(extractor: MediaExtractor, uri: Uri): Boolean {
        val descriptor = openDescriptor(uri)
        if (descriptor != null) {
            try {
                descriptor.use { file ->
                    val length = file.statSize
                    if (length > 0L) {
                        extractor.setDataSource(file.fileDescriptor, 0, length)
                    } else {
                        extractor.setDataSource(file.fileDescriptor)
                    }
                }
                return true
            } catch (_: Exception) {
            }
        }
        return try {
            extractor.setDataSource(appContext, uri, null)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun openDescriptor(uri: Uri): ParcelFileDescriptor? {
        return try {
            appContext.contentResolver.openFileDescriptor(uri, "r")
        } catch (_: Exception) {
            null
        }
    }

    private fun ensureCacheLoaded() {
        if (cacheLoaded) return
        cache.load()
        cacheLoaded = true
    }

    companion object {
        private const val PARALLELISM = 4
    }
}
