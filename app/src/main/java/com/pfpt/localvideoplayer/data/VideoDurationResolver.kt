package com.pfpt.localvideoplayer.data

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Handler
import android.os.HandlerThread
import android.os.ParcelFileDescriptor
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import com.pfpt.localvideoplayer.model.VideoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.coroutineContext

class VideoDurationResolver(
    context: Context,
    private val cache: FolderDurationCache = FolderDurationCache(
        File(context.applicationContext.filesDir, "video_duration_cache.json"),
    ),
) {
    private val appContext = context.applicationContext
    private var cacheLoaded = false
    private val playerProbeMutex = Mutex()
    private val probeFailures = ConcurrentHashMap<String, Long>()
    private val disguisedFlags = ConcurrentHashMap<String, Boolean>()
    private val probeHandler: Handler by lazy {
        val thread = HandlerThread("duration-probe")
        thread.start()
        Handler(thread.looper)
    }
    private var sharedProbePlayer: ExoPlayer? = null
    private val activeProbeDuration = AtomicLong(0L)
    private val activeProbeFinished = AtomicReference<CountDownLatch?>(null)
    private val sharedProbeListener = object : Player.Listener {
        override fun onTimelineChanged(timeline: Timeline, reason: Int) {
            val value = windowDurationMs(timeline)
            if (value > 0L) {
                activeProbeDuration.set(value)
                activeProbeFinished.get()?.countDown()
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState != Player.STATE_READY && playbackState != Player.STATE_ENDED) {
                return
            }
            val player = sharedProbePlayer ?: return
            val value = windowDurationMs(player.currentTimeline)
            if (value > 0L) {
                activeProbeDuration.set(value)
            }
            activeProbeFinished.get()?.countDown()
        }

        override fun onPlayerError(error: PlaybackException) {
            activeProbeFinished.get()?.countDown()
        }
    }

    suspend fun resolveFast(videos: List<VideoItem>): Map<String, Long> {
        if (videos.isEmpty()) return emptyMap()
        return withContext(Dispatchers.IO) {
            ensureCacheLoaded()
            disguisedFlags.clear()
            try {
                coroutineScope {
                    val semaphore = Semaphore(PARALLELISM)
                    val fast = videos.map { video ->
                        async {
                            semaphore.withPermit {
                                video.uri.toString() to readFastDurationMs(video)
                            }
                        }
                    }.awaitAll()
                    val resolved = LinkedHashMap<String, Long>()
                    fast.forEach { (uri, duration) ->
                        if (duration > 0L) resolved[uri] = duration
                    }
                    cache.save()
                    resolved
                }
            } finally {
                disguisedFlags.clear()
            }
        }
    }

    suspend fun resolveAll(
        videos: List<VideoItem>,
        onPartial: (suspend (Map<String, Long>) -> Unit)? = null,
    ): Map<String, Long> {
        if (videos.isEmpty()) return emptyMap()
        return withContext(Dispatchers.IO) {
            ensureCacheLoaded()
            disguisedFlags.clear()
            try {
                coroutineScope {
                    val ordered = videos.sortedByDescending { it.dateAdded }
                    val semaphore = Semaphore(PARALLELISM)
                    val fast = ordered.map { video ->
                        async {
                            semaphore.withPermit {
                                video.uri.toString() to readFastDurationMs(video)
                            }
                        }
                    }.awaitAll()
                    val resolved = LinkedHashMap<String, Long>()
                    val needPlayer = mutableListOf<VideoItem>()
                    ordered.forEachIndexed { index, video ->
                        val duration = fast[index].second
                        if (duration > 0L) {
                            resolved[video.uri.toString()] = duration
                        } else {
                            needPlayer += video
                        }
                    }
                    if (resolved.isNotEmpty()) {
                        onPartial?.invoke(resolved.toMap())
                    }
                    var pendingBatch = LinkedHashMap<String, Long>()
                    for (video in needPlayer) {
                        coroutineContext.ensureActive()
                        if (isProbeFailedRecently(video.uri.toString(), video.dateModified)) continue
                        val duration = playerProbeMutex.withLock {
                            coroutineContext.ensureActive()
                            readFromPlayer(video)
                        }
                        if (duration == PROBE_INTERRUPTED) {
                            coroutineContext.ensureActive()
                            break
                        }
                        if (duration > 0L) {
                            clearProbeFailure(video.uri.toString(), video.dateModified)
                            cache.put(video.uri.toString(), video.dateModified, duration)
                            resolved[video.uri.toString()] = duration
                            pendingBatch[video.uri.toString()] = duration
                            if (pendingBatch.size >= PLAYER_PARTIAL_BATCH) {
                                onPartial?.invoke(pendingBatch.toMap())
                                pendingBatch = LinkedHashMap()
                            }
                        } else {
                            markProbeFailed(video.uri.toString(), video.dateModified)
                        }
                    }
                    if (pendingBatch.isNotEmpty()) {
                        onPartial?.invoke(pendingBatch.toMap())
                    }
                    cache.save()
                    resolved
                }
            } finally {
                disguisedFlags.clear()
            }
        }
    }

    fun clearProbeFailures() {
        probeFailures.clear()
    }

    fun release() {
        val handler = runCatching { probeHandler }.getOrNull() ?: return
        val released = CountDownLatch(1)
        handler.post {
            runCatching { sharedProbePlayer?.release() }
            sharedProbePlayer = null
            released.countDown()
            handler.looper.quitSafely()
        }
        runCatching { released.await(PROBE_RELEASE_TIMEOUT_MS, TimeUnit.MILLISECONDS) }
    }

    private fun isProbeFailedRecently(uri: String, modifiedMs: Long): Boolean {
        val failedAt = probeFailures[FolderDurationCache.cacheKey(uri, modifiedMs)] ?: return false
        return System.currentTimeMillis() - failedAt < PROBE_FAILURE_TTL_MS
    }

    private fun markProbeFailed(uri: String, modifiedMs: Long) {
        probeFailures[FolderDurationCache.cacheKey(uri, modifiedMs)] = System.currentTimeMillis()
    }

    private fun clearProbeFailure(uri: String, modifiedMs: Long) {
        probeFailures.remove(FolderDurationCache.cacheKey(uri, modifiedMs))
    }

    private fun readFastDurationMs(video: VideoItem): Long {
        val uri = video.uri
        cache.get(uri.toString(), video.dateModified)?.let { return it }
        val file = primaryStorageFile(video.relativePath, video.title)?.takeIf { it.canRead() }
        val header = readMediaHeaderBytes(appContext, uri, video.relativePath, video.title)
        if (header != null && shouldSkipFastProbe(header)) {
            disguisedFlags[uri.toString()] = true
            return 0L
        }
        disguisedFlags[uri.toString()] = false
        val duration = readFromRetriever(uri, file).takeIf { it > 0L }
            ?: readFromExtractor(uri, file)
        if (duration > 0L) {
            cache.put(uri.toString(), video.dateModified, duration)
        }
        return duration
    }

    private fun readFromRetriever(uri: Uri, file: File?): Long {
        readRetriever { it.setDataSource(appContext, uri) }.takeIf { it > 0L }?.let { return it }
        if (file != null) {
            readRetriever { it.setDataSource(file.absolutePath) }.takeIf { it > 0L }?.let { return it }
        }
        return readRetrieverDescriptor(uri)
    }

    private fun readRetriever(bind: (MediaMetadataRetriever) -> Unit): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            bind(retriever)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
                ?: 0L
        } catch (_: Exception) {
            0L
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun readRetrieverDescriptor(uri: Uri): Long {
        val descriptor = openDescriptor(uri) ?: return 0L
        val retriever = MediaMetadataRetriever()
        return try {
            descriptor.use { file ->
                val length = file.statSize
                if (length > 0L) {
                    retriever.setDataSource(file.fileDescriptor, 0, length)
                } else {
                    retriever.setDataSource(file.fileDescriptor)
                }
            }
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
                ?: 0L
        } catch (_: Exception) {
            0L
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun readFromExtractor(uri: Uri, file: File?): Long {
        readExtractor { it.setDataSource(appContext, uri, null) }.takeIf { it > 0L }?.let { return it }
        if (file != null) {
            readExtractor { it.setDataSource(file.absolutePath) }.takeIf { it > 0L }?.let { return it }
        }
        return readExtractorDescriptor(uri)
    }

    private fun readExtractor(bind: (MediaExtractor) -> Unit): Long {
        val extractor = MediaExtractor()
        return try {
            bind(extractor)
            trackDurationMs(extractor)
        } catch (_: Exception) {
            0L
        } finally {
            runCatching { extractor.release() }
        }
    }

    private fun readExtractorDescriptor(uri: Uri): Long {
        val descriptor = openDescriptor(uri) ?: return 0L
        val extractor = MediaExtractor()
        return try {
            descriptor.use { file ->
                val length = file.statSize
                if (length > 0L) {
                    extractor.setDataSource(file.fileDescriptor, 0, length)
                } else {
                    extractor.setDataSource(file.fileDescriptor)
                }
            }
            trackDurationMs(extractor)
        } catch (_: Exception) {
            0L
        } finally {
            runCatching { extractor.release() }
        }
    }

    private fun trackDurationMs(extractor: MediaExtractor): Long {
        var maxUs = 0L
        for (index in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(index)
            if (format.containsKey(MediaFormat.KEY_DURATION)) {
                maxUs = maxOf(maxUs, format.getLong(MediaFormat.KEY_DURATION))
            }
        }
        return if (maxUs > 0L) maxUs / 1_000L else 0L
    }

    private fun readFromPlayer(video: VideoItem): Long {
        val file = primaryStorageFile(video.relativePath, video.title)?.takeIf { it.canRead() }
        val probeUri = file?.let(Uri::fromFile) ?: video.uri
        val mediaItem = probeMediaItem(probeUri, file, video.uri)
        val handler = probeHandler
        val started = CountDownLatch(1)
        val finished = CountDownLatch(1)
        var interrupted = false
        try {
            handler.post {
                val player = sharedProbePlayer ?: ExoPlayer.Builder(appContext)
                    .setMediaSourceFactory(
                        DefaultMediaSourceFactory(
                            appContext,
                            DefaultExtractorsFactory().setConstantBitrateSeekingEnabled(true),
                        ),
                    )
                    .setLooper(handler.looper)
                    .build()
                    .also {
                        it.addListener(sharedProbeListener)
                        sharedProbePlayer = it
                    }
                runCatching { player.stop() }
                runCatching { player.clearMediaItems() }
                activeProbeDuration.set(0L)
                activeProbeFinished.set(finished)
                player.setMediaItem(mediaItem)
                player.prepare()
                started.countDown()
            }
            if (!started.await(PROBE_QUEUE_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                return if (Thread.currentThread().isInterrupted) PROBE_INTERRUPTED else 0L
            }
            finished.await(PROBE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            interrupted = true
        } finally {
            val cleaned = CountDownLatch(1)
            handler.post {
                sharedProbePlayer?.let { player ->
                    runCatching { player.stop() }
                    runCatching { player.clearMediaItems() }
                }
                cleaned.countDown()
            }
            runCatching { cleaned.await(PROBE_RELEASE_TIMEOUT_MS, TimeUnit.MILLISECONDS) }
            activeProbeFinished.compareAndSet(finished, null)
        }
        if (interrupted || Thread.currentThread().isInterrupted) {
            return PROBE_INTERRUPTED
        }
        return activeProbeDuration.get()
    }

    private fun probeMediaItem(probeUri: Uri, file: File?, contentUri: Uri): MediaItem {
        val builder = MediaItem.Builder().setUri(probeUri)
        val cached = disguisedFlags[contentUri.toString()]
        val disguised = cached ?: isDisguisedMpegTs(file, contentUri)
        if (disguised) {
            builder.setMimeType(MimeTypes.VIDEO_MP2T)
        }
        return builder.build()
    }

    private fun isDisguisedMpegTs(file: File?, contentUri: Uri): Boolean {
        if (file != null) {
            readFileHeaderBytes(file)?.let { return isDisguisedMpegTsHeader(it) }
        }
        return isDisguisedMpegTsMedia(appContext, contentUri, relativePath = null, title = "")
    }

    private fun windowDurationMs(timeline: Timeline): Long {
        if (timeline.isEmpty) return 0L
        val duration = timeline.getWindow(0, Timeline.Window()).durationMs
        return duration.takeIf { it > 0L } ?: 0L
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
        private const val PLAYER_PARTIAL_BATCH = 3
        private const val PROBE_TIMEOUT_MS = 12_000L
        private const val PROBE_FAILURE_TTL_MS = 30 * 60_000L
        private const val PROBE_QUEUE_TIMEOUT_MS = 5_000L
        private const val PROBE_RELEASE_TIMEOUT_MS = 5_000L
        const val HEADER_SNIFF_BYTES = 64 * 1024
        private const val PROBE_INTERRUPTED = -1L
    }
}

fun readMediaHeaderBytes(
    context: Context,
    uri: Uri,
    relativePath: String?,
    title: String,
): ByteArray? {
    val file = primaryStorageFile(relativePath, title)?.takeIf { it.canRead() }
    if (file != null) {
        readFileHeaderBytes(file)?.let { return it }
    }
    if (uri.scheme.equals("file", ignoreCase = true)) {
        uri.path?.let { path ->
            readFileHeaderBytes(File(path))?.let { return it }
        }
    }
    return runCatching {
        context.applicationContext.contentResolver.openInputStream(uri)?.use { input ->
            val buffer = ByteArray(VideoDurationResolver.HEADER_SNIFF_BYTES)
            val read = input.read(buffer)
            if (read > 0) buffer.copyOf(read) else null
        }
    }.getOrNull()
}

fun readFileHeaderBytes(file: File): ByteArray? {
    return runCatching {
        file.inputStream().use { input ->
            val buffer = ByteArray(VideoDurationResolver.HEADER_SNIFF_BYTES)
            val read = input.read(buffer)
            if (read > 0) buffer.copyOf(read) else null
        }
    }.getOrNull()
}

fun isDisguisedMpegTsMedia(
    context: Context,
    uri: Uri,
    relativePath: String?,
    title: String,
): Boolean {
    val header = readMediaHeaderBytes(context, uri, relativePath, title) ?: return false
    return isDisguisedMpegTsHeader(header)
}

fun needsConstantBitrateSeeking(context: Context, video: VideoItem): Boolean {
    if (looksLikeTsName(video.title)) return true
    val segment = video.uri.lastPathSegment.orEmpty()
    if (looksLikeTsName(segment)) return true
    return isDisguisedMpegTsMedia(context, video.uri, video.relativePath, video.title)
}

fun looksLikeTsName(name: String): Boolean {
    val base = name.substringBefore('?').substringBefore('#')
    return base.endsWith(".ts", ignoreCase = true)
}

fun primaryStorageFile(relativePath: String?, title: String): File? {
    val relative = relativePath?.trim()?.trim('/') ?: return null
    if (relative.isEmpty() || relative.startsWith("content:")) return null
    val path = if (relative == title || relative.endsWith("/$title")) {
        relative
    } else {
        "$relative/$title"
    }
    return File("/storage/emulated/0/$path")
}

fun shouldSkipFastProbe(header: ByteArray): Boolean = isDisguisedMpegTsHeader(header)

fun isDisguisedMpegTsHeader(header: ByteArray): Boolean {
    if (header.size < 2) return false
    val png = isPngHeader(header)
    val bmp = header[0] == 'B'.code.toByte() && header[1] == 'M'.code.toByte()
    if (!png && !bmp) return false
    val text = String(header, Charsets.ISO_8859_1)
    if (text.contains("FFmpeg") || text.contains("Service01")) return true
    val iend = indexOfBytes(header, IEND_MARKER)
    if (iend < 0) return false
    for (index in (iend + IEND_MARKER.size) until header.size) {
        if (header[index] == TS_SYNC_BYTE) return true
    }
    return false
}

internal fun isPngHeader(header: ByteArray): Boolean {
    if (header.size >= PNG_SIGNATURE.size) {
        var matches = true
        for (index in PNG_SIGNATURE.indices) {
            if (header[index] != PNG_SIGNATURE[index]) {
                matches = false
                break
            }
        }
        if (matches) return true
    }
    return header.size >= 16 &&
        header[12] == 'I'.code.toByte() &&
        header[13] == 'H'.code.toByte() &&
        header[14] == 'D'.code.toByte() &&
        header[15] == 'R'.code.toByte()
}

internal fun indexOfBytes(haystack: ByteArray, needle: ByteArray): Int {
    if (needle.isEmpty() || haystack.size < needle.size) return -1
    outer@ for (start in 0..(haystack.size - needle.size)) {
        for (offset in needle.indices) {
            if (haystack[start + offset] != needle[offset]) continue@outer
        }
        return start
    }
    return -1
}

private val PNG_SIGNATURE = byteArrayOf(
    0x89.toByte(),
    'P'.code.toByte(),
    'N'.code.toByte(),
    'G'.code.toByte(),
    0x0D,
    0x0A,
    0x1A,
    0x0A,
)

private const val TS_SYNC_BYTE: Byte = 0x47

private val IEND_MARKER = byteArrayOf(
    'I'.code.toByte(),
    'E'.code.toByte(),
    'N'.code.toByte(),
    'D'.code.toByte(),
)
