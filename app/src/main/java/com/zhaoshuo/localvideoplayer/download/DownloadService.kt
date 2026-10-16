package com.zhaoshuo.localvideoplayer.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.IBinder
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import com.zhaoshuo.localvideoplayer.MainActivity
import com.zhaoshuo.localvideoplayer.R
import com.zhaoshuo.localvideoplayer.download.torrent.TorrentSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Request
import okhttp3.Response
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

class DownloadService : Service() {
    private val downloadDispatcher = Dispatchers.IO.limitedParallelism(64)
    private val scope = CoroutineScope(SupervisorJob() + downloadDispatcher)
    private val mergeDispatcher = Dispatchers.IO.limitedParallelism(1)
    private val activeControls = ConcurrentHashMap<String, TaskControl>()
    private val activeJobs = ConcurrentHashMap<String, Job>()
    private val outputLocks = ConcurrentHashMap<String, Mutex>()
    private val speedTrackers = ConcurrentHashMap<String, DownloadSpeedTracker>()
    private val lastProgressReportAt = ConcurrentHashMap<String, Long>()
    private val hlsByteCounters = ConcurrentHashMap<String, AtomicLong>()
    private val taskProgressSnapshots = ConcurrentHashMap<String, DownloadTask>()
    private val lastNotificationAt = ConcurrentHashMap<String, Long>()
    private val queueLock = Any()
    private val fillLock = Mutex()
    private var schedulerJob: Job? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createChannel()
        startForegroundSafely(
            NOTIFICATION_ID,
            notification(getString(R.string.download_notification_preparing))
                .setGroup(NOTIFICATION_GROUP)
                .setGroupSummary(true)
                .setContentIntent(downloadsContentIntent())
                .build(),
        )
        when (intent?.action) {
            ACTION_STOP_CURRENT, ACTION_CANCEL_CURRENT -> {
                intent.getStringExtra(EXTRA_TASK_ID)?.let { taskId -> activeControls[taskId] }?.apply {
                    stopped = true
                    canceled = intent.action == ACTION_CANCEL_CURRENT
                }
            }
            ACTION_RUN_QUEUE, null -> ensureSchedulerRunning()
        }
        return START_NOT_STICKY
    }

    private fun ensureSchedulerRunning() {
        synchronized(queueLock) {
            if (schedulerJob?.isActive == true) {
                fillDownloadSlots()
                return
            }
            schedulerJob = scope.launch {
                val manager = DownloadManager.get(this@DownloadService)
                manager.awaitReady()
                fillDownloadSlots()
            }
        }
    }

    private fun fillDownloadSlots() {
        scope.launch {
            fillLock.withLock {
                val manager = DownloadManager.get(this@DownloadService)
                manager.awaitReady()
                while (true) {
                    val limit = manager.effectiveConcurrentLimit(activeJobs.size + manager.countQueued())
                    if (activeJobs.size >= limit) break
                    val task = manager.claimNextQueued() ?: break
                    val existing = activeJobs[task.id]
                    if (existing != null && existing.isActive) {
                        manager.update(task.id) { it.copy(status = DownloadStatus.QUEUED) }
                        break
                    }
                    if (existing != null) {
                        activeJobs.remove(task.id, existing)
                        activeControls.remove(task.id)
                    }
                    launchTask(task, manager)
                }
                refreshAllNotifications(manager)
                maybeStopService(manager)
            }
        }
    }

    private fun launchTask(task: DownloadTask, manager: DownloadManager) {
        val existing = activeJobs[task.id]
        if (existing != null && existing.isActive) return
        val control = TaskControl()
        val job = scope.launch(start = CoroutineStart.LAZY) {
            processTask(task.id, manager, control)
        }
        activeControls[task.id] = control
        activeJobs[task.id] = job
        job.invokeOnCompletion { fillDownloadSlots() }
        job.start()
    }

    private suspend fun maybeStopService(manager: DownloadManager) {
        if (activeJobs.isNotEmpty() || manager.hasQueued()) return
        synchronized(queueLock) {
            schedulerJob?.cancel()
            schedulerJob = null
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        cancelAllTaskNotifications()
        stopSelf()
    }

    private suspend fun processTask(taskId: String, manager: DownloadManager, control: TaskControl) {
        try {
            if (control.stopped) throw IOException(getString(R.string.download_paused))
            val current = manager.get(taskId) ?: return
            val isHls = DownloadUrlClassifier.isHlsDownloadUrl(current.url)
            val isMagnet = DownloadUrlClassifier.isMagnetUrl(current.url)
            val mergedPart = partFile(taskId)
            val partial = when {
                mergedPart.exists() && mergedPart.length() > 0L -> mergedPart.absolutePath
                isHls -> hlsDir(taskId).also { it.mkdirs() }.absolutePath
                isMagnet -> torrentDir(taskId).also { it.mkdirs() }.absolutePath
                else -> current.partialPath ?: mergedPart.absolutePath
            }
            manager.update(taskId) { task ->
                task.copy(
                    partialPath = partial,
                    downloadedBytes = when {
                        mergedPart.exists() && mergedPart.length() > 0L -> mergedPart.length()
                        isMagnet -> task.downloadedBytes
                        isHls -> directorySize(File(partial))
                        else -> File(partial).takeIf(File::exists)?.length() ?: 0
                    },
                    isMerging = false,
                    downloadSpeedBps = 0,
                )
            }
            download(taskId, manager, control)
        } catch (error: Exception) {
            val current = manager.get(taskId) ?: return
            if (control.canceled || current.status == DownloadStatus.CANCELED) {
                manager.cleanupTaskFiles(current)
                manager.update(taskId) {
                    it.copy(
                        partialPath = null,
                        hlsCompletedSegments = 0,
                        hlsTotalSegments = 0,
                        downloadSpeedBps = 0,
                        isMerging = false,
                        hlsPlaylistUrl = null,
                        hlsPlaylistSignature = null,
                        outputUri = null,
                        downloadedBytes = 0,
                        totalBytes = -1,
                    )
                }
            } else if (!control.stopped && current.status != DownloadStatus.PAUSED) {
                manager.update(taskId) {
                    it.copy(
                        status = DownloadStatus.FAILED,
                        retryCount = it.retryCount + 1,
                        isMerging = false,
                        error = failureMessage(error, current.url),
                    )
                }
            }
        } finally {
            activeControls.remove(taskId)
            activeJobs.remove(taskId)
            speedTrackers.remove(taskId)
            lastProgressReportAt.remove(taskId)
            hlsByteCounters.remove(taskId)
            taskProgressSnapshots.remove(taskId)
            lastNotificationAt.remove(taskId)
            cancelTaskNotification(taskId)
            if (manager.get(taskId)?.status == DownloadStatus.QUEUED) {
                fillDownloadSlots()
            }
        }
    }

    private fun failureMessage(error: Exception, @Suppress("UNUSED_PARAMETER") taskUrl: String = ""): String {
        if (error is SocketTimeoutException || error.cause is SocketTimeoutException) {
            return getString(R.string.download_timeout_retry)
        }
        return error.message?.takeIf { it.isNotBlank() } ?: getString(R.string.download_direct_link_failed)
    }

    private fun effectiveTask(task: DownloadTask): DownloadTask {
        val downloadUrl = DownloadUrlResolver.taskDownloadUrl(task.url)
        val headers = DownloadUrlResolver.inferHeaders(task.url, task.headers)
        return if (downloadUrl == task.url && headers == task.headers) task else task.copy(url = downloadUrl, headers = headers)
    }

    private fun unwrapPlaylist(playlist: HlsVodPlaylist): HlsVodPlaylist = playlist.copy(
        url = DownloadUrlResolver.mediaFetchUrl(playlist.url),
        initSegmentUrl = playlist.initSegmentUrl?.let(DownloadUrlResolver::mediaFetchUrl),
        segments = playlist.segments.map { segment ->
            segment.copy(
                url = DownloadUrlResolver.mediaFetchUrl(segment.url),
                encryption = segment.encryption?.let { encryption ->
                    encryption.copy(keyUrl = DownloadUrlResolver.mediaFetchUrl(encryption.keyUrl))
                },
            )
        },
    )

    private suspend fun download(taskId: String, manager: DownloadManager, control: TaskControl) {
        val task = effectiveTask(manager.get(taskId) ?: return)
        if (!task.outputUri.isNullOrBlank()) {
            manager.update(taskId) { it.copy(status = DownloadStatus.COMPLETED, error = null) }
            return
        }
        val mergedPart = partFile(task.id)
        if (mergedPart.exists() && mergedPart.length() > 0L) {
            if (control.stopped) throw IOException(getString(R.string.download_paused))
            publishPart(taskId, mergedPart, manager, control)
            return
        }
        if (DownloadUrlClassifier.isMagnetUrl(task.url)) {
            downloadTorrent(task, manager, control)
            return
        }
        val isHls = DownloadUrlClassifier.isHlsDownloadUrl(task.url)
        if (isHls) {
            val hlsPackage = hlsDir(task.id)
            hlsPackage.mkdirs()
            downloadHls(task, hlsPackage, manager, control)
            if (control.stopped) throw IOException(getString(R.string.download_paused))
            mergeAndPublishHls(task.id, hlsPackage, manager, control)
            return
        }
        val part = File(task.partialPath ?: mergedPart.absolutePath)
        part.parentFile?.mkdirs()
        if (task.totalBytes > 0 && part.exists() && part.length() >= task.totalBytes) {
            if (control.stopped) throw IOException(getString(R.string.download_paused))
            publishPart(task.id, part, manager, control)
            return
        }
        downloadProgressive(task, part, manager, control)
        if (control.stopped) throw IOException(getString(R.string.download_paused))
        publishPart(task.id, part, manager, control)
    }

    private suspend fun downloadTorrent(task: DownloadTask, manager: DownloadManager, control: TaskControl) {
        val saveDir = torrentDir(task.id)
        saveDir.mkdirs()
        manager.update(task.id) { it.copy(partialPath = saveDir.absolutePath) }
        val videoFile = try {
            TorrentSession.downloadMagnet(
                taskId = task.id,
                magnetUri = task.url,
                saveDir = saveDir,
                shouldStop = { control.stopped },
                isCanceled = { control.canceled },
                onName = { name ->
                    val sanitized = DownloadNameResolver.sanitizeTitle(name)
                    if (sanitized.isBlank()) return@downloadMagnet
                    val currentName = manager.get(task.id)?.fileName.orEmpty()
                    val looksLikeHash = currentName.length in 32..64 && currentName.all { it.isLetterOrDigit() }
                    if (looksLikeHash || currentName.isBlank() || currentName == getString(R.string.download_default_file_name)) {
                        val withExt = if ('.' in sanitized.substringAfterLast('/')) sanitized else "$sanitized.mp4"
                        manager.update(task.id) { it.copy(fileName = withExt) }
                    }
                },
                onProgress = { downloaded, total, speedBps ->
                    reportTorrentProgress(task.id, manager, downloaded, total, speedBps)
                },
            )
        } catch (error: IOException) {
            if (control.stopped && !control.canceled) {
                throw IOException(getString(R.string.download_paused))
            }
            throw error
        }
        if (control.stopped) throw IOException(getString(R.string.download_paused))
        val publishedName = ensureExtension(manager.get(task.id)?.fileName ?: videoFile.name, videoFile.name)
        manager.update(task.id) { it.copy(fileName = publishedName) }
        publishPart(task.id, videoFile, manager, control)
        if (saveDir.exists()) {
            saveDir.deleteRecursively()
        }
    }

    private suspend fun reportTorrentProgress(
        taskId: String,
        manager: DownloadManager,
        downloaded: Long,
        total: Long,
        speedBps: Long,
        force: Boolean = false,
    ) {
        val current = manager.get(taskId) ?: return
        val nextTotal = if (total > 0) total else current.totalBytes
        val nextDownloaded = maxOf(current.downloadedBytes, downloaded.coerceAtLeast(0L))
        if (!force &&
            nextDownloaded - current.downloadedBytes < PROGRESS_STEP &&
            nextTotal == current.totalBytes &&
            speedBps == current.downloadSpeedBps
        ) {
            return
        }
        manager.update(taskId) {
            it.copy(
                downloadedBytes = nextDownloaded,
                totalBytes = nextTotal,
                downloadSpeedBps = speedBps.coerceAtLeast(0L),
            )
        }
        val latest = manager.get(taskId) ?: current.copy(
            downloadedBytes = nextDownloaded,
            totalBytes = nextTotal,
            downloadSpeedBps = speedBps.coerceAtLeast(0L),
        )
        updateNotification(task = latest, taskId = taskId)
    }

    private fun ensureExtension(preferred: String, fallbackFileName: String): String {
        val preferredBase = preferred.substringAfterLast('/')
        if ('.' in preferredBase) return preferred
        val ext = fallbackFileName.substringAfterLast('.', "")
        return if (ext.isNotBlank()) "$preferred.$ext" else preferred
    }

    private suspend fun downloadProgressive(task: DownloadTask, part: File, manager: DownloadManager, control: TaskControl) {
        val connections = DownloadManager.get(this).connectionsPerDownload()
        val probe = probeProgressive(task.url, task.headers, control)
        if (probe != null &&
            probe.acceptsRanges &&
            ProgressiveRangePlanner.shouldUseMultiRange(probe.totalBytes, connections)
        ) {
            downloadProgressiveMultiRange(task, part, probe.totalBytes, connections, manager, control)
            return
        }
        downloadProgressiveSingle(task, part, manager, control)
    }

    private data class ProgressiveProbe(val totalBytes: Long, val acceptsRanges: Boolean)

    private fun probeProgressive(url: String, headers: Map<String, String>, control: TaskControl): ProgressiveProbe? {
        if (control.stopped) throw IOException(getString(R.string.download_paused))
        runCatching {
            executeRequest(url, headers, rangeHeader = "bytes=0-0").use { response ->
                when (response.code) {
                    HttpURLConnection.HTTP_PARTIAL -> {
                        val total = contentTotalBytes(response, 0).takeIf { it > 0 }
                        if (total != null) return ProgressiveProbe(total, true)
                    }
                    in 200..299 -> {
                        val total = response.body?.contentLength()?.takeIf { it > 0 }
                        if (total != null) return ProgressiveProbe(total, false)
                    }
                }
            }
        }
        return runCatching {
            executeRequest(url, headers, method = "HEAD").use { response ->
                if (response.code !in 200..299) return@runCatching null
                val total = response.header("Content-Length")?.toLongOrNull()?.takeIf { it > 0 }
                    ?: return@runCatching null
                val accepts = response.header("Accept-Ranges")?.contains("bytes", ignoreCase = true) == true
                if (accepts) return@runCatching ProgressiveProbe(total, true)
                executeRequest(url, headers, rangeHeader = "bytes=0-0").use { ranged ->
                    if (ranged.code == HttpURLConnection.HTTP_PARTIAL) {
                        val rangedTotal = contentTotalBytes(ranged, 0).takeIf { it > 0 } ?: total
                        return@runCatching ProgressiveProbe(rangedTotal, true)
                    }
                }
                ProgressiveProbe(total, false)
            }
        }.getOrNull()
    }

    private suspend fun downloadProgressiveSingle(task: DownloadTask, part: File, manager: DownloadManager, control: TaskControl) {
        cleanupRangeParts(task.id)
        var offset = part.takeIf(File::exists)?.length() ?: 0L
        var response = executeRequest(task.url, task.headers, rangeStart = offset.takeIf { it > 0 })
        if (offset > 0 && response.code != HttpURLConnection.HTTP_PARTIAL) {
            response.close()
            part.delete()
            offset = 0
            manager.update(task.id) { it.copy(downloadedBytes = 0, totalBytes = -1) }
            response = executeRequest(task.url, task.headers)
        }
        try {
            if (response.code !in 200..299) throw IOException("HTTP ${response.code}")
            val total = contentTotalBytes(response, offset)
            val body = response.body ?: throw IOException(getString(R.string.download_failed))
            FileOutputStream(part, offset > 0).use { destination ->
                BufferedInputStream(body.byteStream(), IO_BUFFER_SIZE).use { source ->
                    copyStream(source, destination, offset, total, task, manager, control)
                }
            }
        } finally {
            response.close()
        }
    }

    private suspend fun downloadProgressiveMultiRange(
        task: DownloadTask,
        part: File,
        totalBytes: Long,
        connections: Int,
        manager: DownloadManager,
        control: TaskControl,
    ) {
        if (part.exists()) part.delete()
        val ranges = ProgressiveRangePlanner.plan(totalBytes, connections)
        val completedBytes = AtomicLong(0)
        val inFlight = ConcurrentHashMap<Int, Long>()
        ranges.forEachIndexed { index, range ->
            val piece = rangePartFile(task.id, index)
            if (piece.exists() && piece.length() == range.length) {
                completedBytes.addAndGet(range.length)
            } else if (piece.exists()) {
                piece.delete()
            }
        }
        fun currentDownloaded(): Long = completedBytes.get() + inFlight.values.sum()
        manager.update(task.id) { it.copy(downloadedBytes = currentDownloaded(), totalBytes = totalBytes) }
        reportProgress(task, manager, currentDownloaded(), totalBytes, force = true)
        coroutineScope {
            ranges.mapIndexed { index, range ->
                async {
                    val piece = rangePartFile(task.id, index)
                    if (piece.exists() && piece.length() == range.length) return@async
                    downloadByteRange(
                        url = task.url,
                        headers = task.headers,
                        range = range,
                        dest = piece,
                        control = control,
                    ) { written ->
                        inFlight[index] = written
                        scope.launch {
                            reportProgress(task, manager, currentDownloaded(), totalBytes)
                        }
                    }
                    inFlight.remove(index)
                    completedBytes.addAndGet(range.length)
                    reportProgress(task, manager, currentDownloaded(), totalBytes, force = true)
                }
            }.awaitAll()
        }
        if (control.stopped) throw IOException(getString(R.string.download_paused))
        ranges.forEachIndexed { index, range ->
            val piece = rangePartFile(task.id, index)
            if (!piece.exists() || piece.length() != range.length) {
                throw IOException(getString(R.string.download_failed))
            }
        }
        FileOutputStream(part).use { destination ->
            ranges.indices.forEach { index ->
                if (control.stopped) throw IOException(getString(R.string.download_paused))
                val piece = rangePartFile(task.id, index)
                FileInputStream(piece).use { source ->
                    source.copyTo(destination, IO_BUFFER_SIZE)
                }
            }
        }
        if (part.length() != totalBytes) throw IOException(getString(R.string.download_failed))
        cleanupRangeParts(task.id)
        reportProgress(task, manager, totalBytes, totalBytes, force = true)
    }

    private fun downloadByteRange(
        url: String,
        headers: Map<String, String>,
        range: ByteRange,
        dest: File,
        control: TaskControl,
        onProgress: (Long) -> Unit,
    ) {
        var attempt = 0
        var lastError: IOException? = null
        while (attempt < SEGMENT_MAX_RETRIES) {
            if (control.stopped) throw IOException(getString(R.string.download_paused))
            dest.parentFile?.mkdirs()
            if (dest.exists()) dest.delete()
            try {
                executeRequest(
                    url,
                    headers,
                    rangeHeader = "bytes=${range.start}-${range.endInclusive}",
                ).use { response ->
                    if (response.code != HttpURLConnection.HTTP_PARTIAL && response.code !in 200..299) {
                        throw IOException("HTTP ${response.code}")
                    }
                    val body = response.body ?: throw IOException(getString(R.string.download_failed))
                    var written = 0L
                    FileOutputStream(dest).use { destination ->
                        BufferedInputStream(body.byteStream(), IO_BUFFER_SIZE).use { source ->
                            val buffer = ByteArray(IO_BUFFER_SIZE)
                            while (true) {
                                if (control.stopped) throw IOException(getString(R.string.download_paused))
                                val count = source.read(buffer)
                                if (count < 0) break
                                destination.write(buffer, 0, count)
                                written += count
                                onProgress(written)
                            }
                        }
                    }
                    if (written != range.length) {
                        throw IOException(getString(R.string.download_failed))
                    }
                }
                return
            } catch (error: IOException) {
                if (control.stopped || control.canceled) throw error
                lastError = error
                dest.delete()
                onProgress(0)
                val next = nextDownloadAttempt(
                    stopped = false,
                    canceled = false,
                    attempt = attempt,
                    maxAttempts = SEGMENT_MAX_RETRIES,
                )
                if (next == null) break
                attempt = next
                Thread.sleep(RETRY_BACKOFF_MS[attempt - 1])
            }
        }
        throw lastError ?: IOException(getString(R.string.download_failed))
    }

    private fun rangePartFile(taskId: String, index: Int): File =
        File(cacheDir, "range_$taskId.$index")

    private fun cleanupRangeParts(taskId: String) {
        cleanupRangeParts(this, taskId)
    }

    private fun unwrapResolved(result: HlsVodResolveResult): HlsVodResolveResult =
        result.copy(playlist = unwrapPlaylist(result.playlist))

    private suspend fun downloadHls(task: DownloadTask, hlsPackage: File, manager: DownloadManager, control: TaskControl) {
        val resolved = try {
            unwrapResolved(
                HlsVodResolver { address -> requestHlsText(address, task.headers, control) }
                    .resolveWithMediaText(task.url),
            )
        } catch (error: HlsVodException) {
            throw IOException(hlsFailureMessage(error.failure), error)
        }
        val playlist = resolved.playlist
        val resumeState = HlsResume.resolve(task, hlsPackage, playlist) { index ->
            hlsSegmentFile(hlsPackage, playlist.segmentFormat(), index)
        }
        val segmentFormat = playlist.segmentFormat()
        val completedSegments = resumeState.completedSegments
        if (resumeState.shouldWipe) hlsPackage.listFiles()?.forEach { it.delete() }
        File(hlsPackage, HLS_SOURCE_PLAYLIST_NAME).writeText(resolved.mediaText)
        val initialBytes = if (resumeState.shouldWipe) 0L else directorySize(hlsPackage)
        hlsByteCounters[task.id] = AtomicLong(initialBytes)
        manager.update(task.id) {
            it.copy(
                hlsCompletedSegments = completedSegments,
                hlsTotalSegments = playlist.segments.size,
                hlsPlaylistUrl = playlist.url,
                hlsPlaylistSignature = playlist.signature,
                downloadedBytes = initialBytes,
                totalBytes = estimateHlsTotalBytes(initialBytes, completedSegments, playlist.segments.size),
                isMerging = false,
            )
        }
        playlist.initSegmentUrl?.let { initUrl ->
            val initFile = hlsInitFile(hlsPackage)
            if (!initFile.exists()) {
                if (control.stopped) throw IOException(getString(R.string.download_paused))
                downloadHlsSegmentAtomic(
                    initUrl,
                    initFile,
                    task.headers,
                    control,
                    encryption = null,
                    playlistUrl = playlist.url,
                )
                addHlsBytes(task.id, initFile.length())
            }
        }
        val keys = ConcurrentHashMap<String, ByteArray>()
        prefetchHlsKeys(playlist, task.headers, control, keys, playlist.url)
        val pendingSegments = playlist.segments.drop(completedSegments)
        val completedCounter = AtomicInteger(completedSegments)
        val concurrency = DownloadManager.get(this).connectionsPerDownload()
        val nextWork = AtomicInteger(0)
        val sinceFlush = AtomicInteger(0)
        val lastFlushAt = AtomicLong(0L)
        val flushLock = Mutex()

        suspend fun flushProgress(force: Boolean) {
            val now = System.currentTimeMillis()
            val count = sinceFlush.get()
            if (!force && now - lastFlushAt.get() < HLS_PROGRESS_FLUSH_MS && count < concurrency) return
            if (!flushLock.tryLock()) return
            try {
                if (!force && now - lastFlushAt.get() < HLS_PROGRESS_FLUSH_MS && sinceFlush.get() < concurrency) return
                sinceFlush.set(0)
                lastFlushAt.set(System.currentTimeMillis())
                val done = completedCounter.get().coerceAtMost(playlist.segments.size)
                val downloaded = currentHlsBytes(task.id)
                manager.update(task.id) {
                    it.copy(
                        hlsCompletedSegments = done,
                        hlsTotalSegments = playlist.segments.size,
                        hlsPlaylistUrl = playlist.url,
                        hlsPlaylistSignature = playlist.signature,
                        downloadedBytes = downloaded,
                        totalBytes = estimateHlsTotalBytes(downloaded, done, playlist.segments.size),
                    )
                }
                val updatedTask = manager.get(task.id) ?: return
                reportHlsProgress(updatedTask, manager, downloaded, done, playlist.segments.size, force = force)
            } finally {
                flushLock.unlock()
            }
        }

        coroutineScope {
            val workers = List(concurrency.coerceAtLeast(1)) {
                async {
                    while (true) {
                        if (control.stopped) throw IOException(getString(R.string.download_paused))
                        val offset = nextWork.getAndIncrement()
                        if (offset >= pendingSegments.size) break
                        val segment = pendingSegments[offset]
                        val segmentIndex = completedSegments + offset
                        val segmentFile = hlsSegmentFile(hlsPackage, segmentFormat, segmentIndex)
                        if (resolveSegmentFile(hlsPackage, segmentFormat, segmentIndex).exists()) {
                            completedCounter.incrementAndGet()
                            sinceFlush.incrementAndGet()
                            flushProgress(force = false)
                            continue
                        }
                        val encryption = segment.encryption
                        val keyIv = encryption?.let { enc ->
                            val key = keys[enc.keyUrl]
                                ?: requestHlsBytes(enc.keyUrl, task.headers, control, playlist.url).also {
                                    if (it.size != AES_128_KEY_BYTES) throw IOException(getString(R.string.download_hls_key_invalid))
                                    keys[enc.keyUrl] = it
                                }
                            key to enc.iv
                        }
                        downloadHlsSegmentAtomic(
                            address = segment.url,
                            finalFile = segmentFile,
                            headers = task.headers,
                            control = control,
                            encryption = keyIv,
                            playlistUrl = playlist.url,
                        )
                        addHlsBytes(task.id, segmentFile.length())
                        completedCounter.incrementAndGet()
                        sinceFlush.incrementAndGet()
                        flushProgress(force = false)
                    }
                }
            }
            workers.awaitAll()
        }
        if (control.stopped) throw IOException(getString(R.string.download_paused))
        flushProgress(force = true)
        val finalDownloaded = currentHlsBytes(task.id)
        manager.update(task.id) {
            it.copy(
                hlsCompletedSegments = playlist.segments.size,
                hlsPlaylistUrl = playlist.url,
                hlsPlaylistSignature = playlist.signature,
                downloadedBytes = finalDownloaded,
                totalBytes = estimateHlsTotalBytes(finalDownloaded, playlist.segments.size, playlist.segments.size),
            )
        }
    }

    private fun prefetchHlsKeys(
        playlist: HlsVodPlaylist,
        headers: Map<String, String>,
        control: TaskControl,
        keys: ConcurrentHashMap<String, ByteArray>,
        playlistUrl: String,
    ) {
        playlist.segments.mapNotNull { it.encryption?.keyUrl }.toSet().forEach { keyUrl ->
            if (control.stopped) throw IOException(getString(R.string.download_paused))
            keys.getOrPut(keyUrl) {
                requestHlsBytes(keyUrl, headers, control, playlistUrl).also {
                    if (it.size != AES_128_KEY_BYTES) throw IOException(getString(R.string.download_hls_key_invalid))
                }
            }
        }
    }

    private suspend fun reportHlsProgress(
        task: DownloadTask,
        manager: DownloadManager,
        downloaded: Long,
        completedSegments: Int,
        totalSegments: Int,
        force: Boolean = false,
    ) {
        val tracker = speedTrackers.getOrPut(task.id) { DownloadSpeedTracker() }
        val speed = tracker.markProgress(downloaded)
        val totalBytes = estimateHlsTotalBytes(downloaded, completedSegments, totalSegments)
        val now = System.currentTimeMillis()
        val lastReport = lastProgressReportAt[task.id] ?: 0L
        val shouldUpdate = force ||
            downloaded - task.downloadedBytes >= PROGRESS_STEP ||
            now - lastReport >= 1000
        if (!shouldUpdate && speed == task.downloadSpeedBps) return
        lastProgressReportAt[task.id] = now
        manager.update(task.id) {
            it.copy(
                downloadedBytes = downloaded,
                totalBytes = totalBytes,
                hlsCompletedSegments = completedSegments.coerceAtMost(totalSegments),
                hlsTotalSegments = totalSegments,
                downloadSpeedBps = speed,
            )
        }
        updateNotification(
            task = task.copy(
                downloadedBytes = downloaded,
                totalBytes = totalBytes,
                hlsCompletedSegments = completedSegments.coerceAtMost(totalSegments),
                hlsTotalSegments = totalSegments,
                downloadSpeedBps = speed,
            ),
            taskId = task.id,
        )
    }

    private fun estimateHlsTotalBytes(downloaded: Long, completedSegments: Int, totalSegments: Int): Long {
        if (totalSegments <= 0 || completedSegments <= 0 || downloaded <= 0) return -1
        val average = downloaded / completedSegments
        return average * totalSegments
    }

    private fun requestHlsText(
        address: String,
        headers: Map<String, String>,
        control: TaskControl,
        playlistUrl: String? = null,
    ): String = String(requestHlsBytes(address, headers, control, playlistUrl), Charsets.UTF_8)

    private fun requestHlsBytes(
        address: String,
        headers: Map<String, String>,
        control: TaskControl,
        playlistUrl: String? = null,
    ): ByteArray {
        var attempt = 0
        var lastError: IOException? = null
        while (attempt < SEGMENT_MAX_RETRIES) {
            if (control.stopped) throw IOException(getString(R.string.download_paused))
            try {
                executeHlsRequest(address, headers, playlistUrl).use { response ->
                    if (response.code !in 200..299) {
                        throw IOException(getString(R.string.download_hls_request_failed, response.code))
                    }
                    val body = response.body ?: throw IOException(getString(R.string.download_failed))
                    return body.bytes()
                }
            } catch (error: IOException) {
                if (control.stopped || control.canceled) throw error
                lastError = error
                val next = nextDownloadAttempt(
                    stopped = false,
                    canceled = false,
                    attempt = attempt,
                    maxAttempts = SEGMENT_MAX_RETRIES,
                )
                if (next == null) break
                attempt = next
                Thread.sleep(RETRY_BACKOFF_MS[attempt - 1])
            }
        }
        throw lastError ?: IOException(getString(R.string.download_failed))
    }

    private fun downloadHlsSegmentAtomic(
        address: String,
        finalFile: File,
        headers: Map<String, String>,
        control: TaskControl,
        encryption: Pair<ByteArray, ByteArray>?,
        playlistUrl: String? = null,
    ) {
        var attempt = 0
        var lastError: IOException? = null
        val partFile = File(finalFile.parentFile, "${finalFile.name}.part")
        while (attempt < SEGMENT_MAX_RETRIES) {
            if (control.stopped) throw IOException(getString(R.string.download_paused))
            finalFile.parentFile?.mkdirs()
            if (partFile.exists()) partFile.delete()
            try {
                executeHlsRequest(address, headers, playlistUrl).use { response ->
                    if (response.code !in 200..299) {
                        throw IOException(getString(R.string.download_hls_request_failed, response.code))
                    }
                    val body = response.body ?: throw IOException(getString(R.string.download_failed))
                    FileOutputStream(partFile).use { fileOut ->
                        BufferedInputStream(body.byteStream(), IO_BUFFER_SIZE).use { source ->
                            try {
                                if (encryption != null) {
                                    val (key, iv) = encryption
                                    HlsAesDecryptor.decryptingOutputStream(fileOut, key, iv).use { decrypted ->
                                        val buffer = ByteArray(IO_BUFFER_SIZE)
                                        while (true) {
                                            if (control.stopped) throw IOException(getString(R.string.download_paused))
                                            val count = source.read(buffer)
                                            if (count < 0) break
                                            decrypted.write(buffer, 0, count)
                                        }
                                        decrypted.flush()
                                    }
                                } else {
                                    val buffer = ByteArray(IO_BUFFER_SIZE)
                                    while (true) {
                                        if (control.stopped) throw IOException(getString(R.string.download_paused))
                                        val count = source.read(buffer)
                                        if (count < 0) break
                                        fileOut.write(buffer, 0, count)
                                    }
                                }
                            } catch (error: IOException) {
                                throw error
                            } catch (error: Exception) {
                                throw IOException(getString(R.string.download_hls_decryption_failed), error)
                            }
                        }
                    }
                }
                if (finalFile.exists()) finalFile.delete()
                if (!partFile.renameTo(finalFile)) {
                    partFile.copyTo(finalFile, overwrite = true)
                    partFile.delete()
                }
                if (!finalFile.exists() || finalFile.length() <= 0L) {
                    throw IOException(getString(R.string.download_failed))
                }
                return
            } catch (error: IOException) {
                if (control.stopped || control.canceled) throw error
                lastError = error
                partFile.delete()
                val next = nextDownloadAttempt(
                    stopped = false,
                    canceled = false,
                    attempt = attempt,
                    maxAttempts = SEGMENT_MAX_RETRIES,
                )
                if (next == null) break
                attempt = next
                Thread.sleep(RETRY_BACKOFF_MS[attempt - 1])
            }
        }
        throw lastError ?: IOException(getString(R.string.download_failed))
    }

    private fun hlsFailureMessage(failure: HlsVodFailure): String = when (failure) {
        HlsVodFailure.INVALID -> getString(R.string.download_hls_invalid)
        HlsVodFailure.LIVE -> getString(R.string.download_hls_live_unsupported)
        HlsVodFailure.UNSUPPORTED_MASTER -> getString(R.string.download_hls_master_unsupported)
        HlsVodFailure.UNSUPPORTED_ENCRYPTION -> getString(R.string.download_hls_encrypted_unsupported)
        HlsVodFailure.UNSUPPORTED_FRAGMENTED -> getString(R.string.download_hls_fragmented_unsupported)
        HlsVodFailure.UNSUPPORTED_TS -> getString(R.string.download_hls_ts_unsupported)
        HlsVodFailure.UNSUPPORTED_SEGMENT -> getString(R.string.download_hls_segment_unsupported)
    }

    private suspend fun mergeAndPublishHls(
        taskId: String,
        hlsPackage: File,
        manager: DownloadManager,
        control: TaskControl,
    ) {
        val task = manager.get(taskId) ?: return
        val effective = effectiveTask(task)
        val playlist = resolveMergePlaylist(effective, hlsPackage, control)
        val segmentFormat = playlist.segmentFormat()
        val segmentFiles = listHlsSegmentFiles(hlsPackage, segmentFormat)
        if (segmentFiles.isEmpty()) {
            throw IOException(getString(R.string.download_hls_merge_failed))
        }
        if (segmentFiles.size != playlist.segments.size) {
            throw IOException(
                getString(
                    R.string.download_hls_merge_segment_mismatch,
                    segmentFiles.size,
                    playlist.segments.size,
                ),
            )
        }
        manager.update(taskId) { it.copy(isMerging = true, downloadSpeedBps = 0) }
        updateNotification(
            task = task.copy(isMerging = true, downloadSpeedBps = 0),
            taskId = task.id,
            merging = true,
            force = true,
        )
        val mergedPart = partFile(task.id)
        val mergedTemp = File(mergedPart.parentFile, "${task.id}.merge.mp4")
        mergedPart.parentFile?.mkdirs()
        try {
            withContext(mergeDispatcher) {
                HlsMerger.mergeToMp4(
                    HlsMerger.MergeInput(
                        segmentFiles = segmentFiles,
                        durationsSec = playlist.segments.map { it.durationSec },
                        format = segmentFormat,
                        initFile = hlsInitFile(hlsPackage).takeIf { it.exists() },
                    ),
                    mergedTemp,
                )
            }
            mergedPart.delete()
            if (!mergedTemp.renameTo(mergedPart)) {
                mergedTemp.copyTo(mergedPart, overwrite = true)
                mergedTemp.delete()
            }
        } catch (error: Exception) {
            mergedTemp.delete()
            mergedPart.delete()
            val message = when {
                error.message == "SEGMENTS_CORRUPT" || error.message?.startsWith("SEGMENTS_CORRUPT:") == true ->
                    getString(R.string.download_hls_segments_corrupt)
                else -> {
                    val detail = error.message?.takeIf { it.isNotBlank() }
                    if (detail != null) {
                        "${getString(R.string.download_hls_merge_failed)}: $detail"
                    } else {
                        getString(R.string.download_hls_merge_failed)
                    }
                }
            }
            throw IOException(message, error)
        }
        if (control.stopped) throw IOException(getString(R.string.download_paused))
        hlsPackage.listFiles()?.forEach { it.delete() }
        hlsPackage.delete()
        manager.update(taskId) {
            it.copy(
                partialPath = mergedPart.absolutePath,
                hlsCompletedSegments = 0,
                hlsTotalSegments = 0,
                isMerging = false,
                hlsPlaylistUrl = null,
                hlsPlaylistSignature = null,
                downloadedBytes = mergedPart.length(),
                totalBytes = mergedPart.length(),
            )
        }
        publishPart(taskId, mergedPart, manager, control)
    }

    private suspend fun copyStream(
        source: BufferedInputStream,
        destination: FileOutputStream,
        initialBytes: Long,
        total: Long,
        task: DownloadTask,
        manager: DownloadManager,
        control: TaskControl,
    ) {
        val buffer = ByteArray(IO_BUFFER_SIZE)
        var downloaded = initialBytes
        while (true) {
            if (control.stopped) throw IOException(getString(R.string.download_paused))
            val count = source.read(buffer)
            if (count < 0) break
            destination.write(buffer, 0, count)
            downloaded += count
            if (downloaded % PROGRESS_STEP < count) {
                reportProgress(task, manager, downloaded, total)
            }
        }
        reportProgress(task, manager, downloaded, total, force = true)
    }

    private suspend fun reportProgress(
        task: DownloadTask,
        manager: DownloadManager,
        downloaded: Long,
        total: Long,
        force: Boolean = false,
    ) {
        val tracker = speedTrackers.getOrPut(task.id) { DownloadSpeedTracker() }
        val speed = tracker.markProgress(downloaded)
        if (!force && downloaded - task.downloadedBytes < PROGRESS_STEP && speed == task.downloadSpeedBps) return
        manager.update(task.id) { it.copy(downloadedBytes = downloaded, totalBytes = total, downloadSpeedBps = speed) }
        updateNotification(
            task = task.copy(downloadedBytes = downloaded, totalBytes = total, downloadSpeedBps = speed),
            taskId = task.id,
        )
    }

    private suspend fun publishPart(taskId: String, part: File, manager: DownloadManager, control: TaskControl) {
        val task = manager.get(taskId) ?: return
        outputLocks.getOrPut(outputKey(task)) { Mutex() }.withLock {
            publishPartLocked(task, part, manager, control)
        }
    }

    private suspend fun publishPartLocked(task: DownloadTask, part: File, manager: DownloadManager, control: TaskControl) {
        val displayName = task.fileName
        val output = createOutput(task, displayName)
        manager.update(task.id) { it.copy(outputUri = output.toString(), fileName = displayName) }
        try {
            contentResolver.openOutputStream(output)?.use { destination ->
                FileInputStream(part).use { source ->
                    val buffer = ByteArray(IO_BUFFER_SIZE)
                    while (true) {
                        if (control.stopped) throw IOException(getString(R.string.download_paused))
                        val count = source.read(buffer)
                        if (count < 0) break
                        destination.write(buffer, 0, count)
                    }
                }
            } ?: throw IOException(getString(R.string.download_output_failed))
            contentResolver.update(
                output,
                ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
                null,
                null,
            )
            val finalSize = part.length()
            part.delete()
            manager.update(task.id) {
                it.copy(
                    status = DownloadStatus.COMPLETED,
                    fileName = displayName,
                    partialPath = null,
                    hlsCompletedSegments = 0,
                    hlsTotalSegments = 0,
                    isMerging = false,
                    hlsPlaylistUrl = null,
                    hlsPlaylistSignature = null,
                    outputUri = output.toString(),
                    downloadedBytes = finalSize,
                    totalBytes = finalSize,
                    downloadSpeedBps = 0,
                    error = null,
                )
            }
        } catch (error: Exception) {
            contentResolver.delete(output, null, null)
            manager.update(task.id) { it.copy(outputUri = null) }
            throw error
        }
    }

    private fun executeHlsRequest(
        address: String,
        headers: Map<String, String>,
        playlistUrl: String?,
    ): Response {
        val prepared = if (playlistUrl.isNullOrBlank()) {
            DownloadUrlResolver.ensureDownloadHeaders(headers)
        } else {
            DownloadUrlResolver.hlsChildHeaders(playlistUrl, headers)
        }
        val response = executeRequest(address, prepared)
        if (response.code != 403) return response
        val refererKey = prepared.keys.firstOrNull { it.equals("Referer", ignoreCase = true) } ?: return response
        response.close()
        return executeRequest(address, prepared.filterKeys { !it.equals(refererKey, ignoreCase = true) })
    }

    private fun executeRequest(
        address: String,
        headers: Map<String, String>,
        method: String = "GET",
        rangeStart: Long? = null,
        rangeHeader: String? = null,
    ): Response {
        val builder = Request.Builder().url(address).method(method, null)
        headers.forEach { (name, value) -> builder.header(name, value) }
        when {
            rangeHeader != null -> builder.header("Range", rangeHeader)
            rangeStart != null -> builder.header("Range", "bytes=$rangeStart-")
        }
        if (method == "HEAD") {
            builder.header("Accept-Encoding", "identity")
        }
        return DownloadHttpClient.client.newCall(builder.build()).execute()
    }

    private fun contentTotalBytes(response: Response, offset: Long): Long {
        val range = response.header("Content-Range")
        val rangeTotal = range?.substringAfterLast('/')?.toLongOrNull()
        val length = response.body?.contentLength()?.takeIf { it >= 0 }
        return rangeTotal ?: length?.plus(offset) ?: -1
    }

    private fun createOutput(task: DownloadTask, displayName: String): Uri {
        val collection = if (task.saveToMovies) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val mimeType = when {
            displayName.endsWith(".ts", true) || displayName.endsWith(".m2ts", true) -> "video/mp2t"
            displayName.endsWith(".mkv", true) -> "video/x-matroska"
            displayName.endsWith(".webm", true) -> "video/webm"
            displayName.endsWith(".avi", true) -> "video/x-msvideo"
            displayName.endsWith(".mov", true) -> "video/quicktime"
            else -> "video/mp4"
        }
        return contentResolver.insert(
            collection,
            ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, if (task.saveToMovies) "Movies/LocalVideoPlayer" else "Download/LocalVideoPlayer")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            },
        ) ?: throw IOException(getString(R.string.download_output_failed))
    }

    private fun updateNotification(task: DownloadTask, taskId: String, merging: Boolean = false, force: Boolean = false) {
        val snapshot = when {
            merging -> task.copy(isMerging = true)
            else -> task
        }
        taskProgressSnapshots[taskId] = snapshot
        val now = System.currentTimeMillis()
        val lastAt = lastNotificationAt[taskId] ?: 0L
        if (!force && now - lastAt < NOTIFICATION_THROTTLE_MS) return
        lastNotificationAt[taskId] = now
        scope.launch {
            refreshAllNotifications(DownloadManager.get(this@DownloadService))
        }
    }

    private suspend fun refreshAllNotifications(manager: DownloadManager) {
        val activeTaskIds = activeJobs.keys.toSet()
        val staleIds = taskProgressSnapshots.keys.filter { it !in activeTaskIds }
        staleIds.forEach { cancelTaskNotification(it) }

        val tasks = activeTaskIds.mapNotNull { taskId ->
            taskProgressSnapshots[taskId] ?: manager.get(taskId)
        }
        if (tasks.isEmpty()) {
            val preparing = notification(getString(R.string.download_notification_preparing))
                .setGroup(NOTIFICATION_GROUP)
                .setGroupSummary(true)
                .setContentIntent(downloadsContentIntent())
                .build()
            startForegroundSafely(NOTIFICATION_ID, preparing)
            return
        }

        tasks.forEach { task ->
            val merging = task.isMerging
            notifySafely(
                taskNotificationId(task.id),
                buildTaskNotification(task, merging).build(),
            )
        }

        val summaryText = if (tasks.size == 1) {
            val task = tasks.first()
            when {
                task.isMerging -> getString(R.string.download_notification_merging, task.fileName)
                else -> getString(R.string.download_notification_progress, task.fileName)
            }
        } else {
            getString(R.string.download_notification_multiple, tasks.size)
        }
        val summary = notification(summaryText)
            .setGroup(NOTIFICATION_GROUP)
            .setGroupSummary(true)
            .setContentIntent(downloadsContentIntent())
            .build()
        startForegroundSafely(NOTIFICATION_ID, summary)
    }

    private fun cancelTaskNotification(taskId: String) {
        runCatching {
            getSystemService(NotificationManager::class.java).cancel(taskNotificationId(taskId))
        }
        taskProgressSnapshots.remove(taskId)
        lastNotificationAt.remove(taskId)
    }

    private fun cancelAllTaskNotifications() {
        taskProgressSnapshots.keys.toList().forEach { cancelTaskNotification(it) }
    }

    private fun resolveMergePlaylist(
        task: DownloadTask,
        hlsPackage: File,
        control: TaskControl,
    ): HlsVodPlaylist {
        val localPlaylist = File(hlsPackage, HLS_SOURCE_PLAYLIST_NAME)
        val playlistUrl = task.hlsPlaylistUrl ?: task.url
        if (localPlaylist.exists()) {
            runCatching {
                return HlsVodPlaylistParser.parseMediaPlaylist(playlistUrl, localPlaylist.readText())
            }
        }
        return try {
            unwrapPlaylist(
                HlsVodResolver { address -> requestHlsText(address, task.headers, control) }.resolve(task.url),
            )
        } catch (error: HlsVodException) {
            throw IOException(hlsFailureMessage(error.failure), error)
        }
    }

    private fun startForegroundSafely(id: Int, notification: android.app.Notification) {
        try {
            startForeground(id, notification)
        } catch (_: SecurityException) {
        }
    }

    private fun notifySafely(id: Int, notification: android.app.Notification) {
        try {
            getSystemService(NotificationManager::class.java).notify(id, notification)
        } catch (_: SecurityException) {
        }
    }

    private fun currentHlsBytes(taskId: String): Long = hlsByteCounters[taskId]?.get() ?: 0L

    private fun addHlsBytes(taskId: String, bytes: Long) {
        hlsByteCounters.getOrPut(taskId) { AtomicLong(0L) }.addAndGet(bytes)
    }

    private fun buildTaskNotification(task: DownloadTask, merging: Boolean): NotificationCompat.Builder {
        val title = when {
            merging -> getString(R.string.download_notification_merging, task.fileName)
            else -> getString(R.string.download_notification_progress, task.fileName)
        }
        val builder = notification(title)
            .setGroup(NOTIFICATION_GROUP)
            .setContentIntent(downloadsContentIntent())
            .addAction(android.R.drawable.ic_media_pause, getString(R.string.pause), actionIntent(ACTION_STOP_CURRENT, task.id))
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, getString(R.string.download_cancel), actionIntent(ACTION_CANCEL_CURRENT, task.id))
        val percent = DownloadFormat.formatProgressPercent(
            task.downloadedBytes,
            task.totalBytes,
            task.hlsCompletedSegments,
            task.hlsTotalSegments,
        )
        when {
            merging -> builder.setProgress(100, percent.coerceAtLeast(0), false)
            task.totalBytes > 0 -> builder.setProgress(100, percent, false)
            task.hlsTotalSegments > 0 -> builder.setProgress(100, percent, false)
            else -> builder.setProgress(0, 0, true)
        }
        val speedText = if (task.downloadSpeedBps > 0) DownloadFormat.formatSpeed(task.downloadSpeedBps) else null
        val detail = buildNotificationDetail(task, percent, speedText)
        if (detail.isNotBlank()) builder.setSubText(detail)
        return builder
    }

    private fun buildNotificationDetail(task: DownloadTask, percent: Int, speedText: String?): String {
        val sizeText = if (task.totalBytes > 0) {
            "${DownloadFormat.formatBytes(task.downloadedBytes)} / ${DownloadFormat.formatBytes(task.totalBytes)}"
        } else {
            DownloadFormat.formatBytes(task.downloadedBytes)
        }
        val parts = buildList {
            add("$percent%")
            if (task.hlsTotalSegments > 0) {
                add(getString(R.string.download_notification_segments, task.hlsCompletedSegments, task.hlsTotalSegments))
            }
            if (sizeText.isNotBlank()) add(sizeText)
            if (!speedText.isNullOrBlank()) add(speedText)
            val remaining = speedTrackers[task.id]?.estimateRemainingSeconds(task.downloadedBytes, task.totalBytes)
            if (remaining != null) add(getString(R.string.download_eta, DownloadFormat.formatEta(remaining)))
        }
        return parts.joinToString(" · ")
    }

    private fun downloadsContentIntent(): PendingIntent {
        return PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java)
                .setAction(ACTION_OPEN_DOWNLOADS)
                .putExtra(EXTRA_OPEN_DOWNLOADS, true)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun actionIntent(action: String, taskId: String): PendingIntent {
        return PendingIntent.getService(
            this,
            (action + taskId).hashCode(),
            Intent(this, DownloadService::class.java).setAction(action).putExtra(EXTRA_TASK_ID, taskId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun notification(text: String) = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.stat_sys_download)
        .setContentTitle(getString(R.string.download_notification_title))
        .setContentText(text)
        .setOnlyAlertOnce(true)
        .setOngoing(true)

    private fun createChannel() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.download_notification_channel), NotificationManager.IMPORTANCE_LOW),
        )
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        TorrentSession.releaseForServiceDestroy()
        scope.cancel()
        super.onDestroy()
    }

    private fun partFile(id: String) = File(filesDir, "download_parts/$id.part")

    private fun hlsDir(id: String) = File(filesDir, "hls_downloads/$id")

    private fun torrentDir(id: String) = File(filesDir, "torrent_downloads/$id")

    private fun hlsSegmentFile(hlsPackage: File, format: HlsSegmentFormat, index: Int) =
        File(hlsPackage, "seg%05d.${format.segmentExtension()}".format(index))

    private fun resolveSegmentFile(hlsPackage: File, format: HlsSegmentFormat, index: Int): File {
        val primary = hlsSegmentFile(hlsPackage, format, index)
        if (primary.exists()) return primary
        val legacy = HlsResume.legacySegmentFile(hlsPackage, format, index)
        if (legacy.exists()) return legacy
        return primary
    }

    private fun hlsInitFile(hlsPackage: File) = File(hlsPackage, "init.mp4")

    private fun listHlsSegmentFiles(hlsPackage: File, format: HlsSegmentFormat): List<File> {
        val files = mutableListOf<File>()
        var index = 0
        while (true) {
            val segment = resolveSegmentFile(hlsPackage, format, index)
            if (!segment.exists()) break
            files += segment
            index++
        }
        return files
    }

    private fun directorySize(directory: File): Long =
        directory.listFiles()?.sumOf { if (it.isFile) it.length() else 0L } ?: 0L

    private fun outputKey(task: DownloadTask) = "${task.saveToMovies}:${task.fileName.lowercase()}"

    private class TaskControl {
        @Volatile var stopped = false
        @Volatile var canceled = false
    }

    companion object {
        const val ACTION_RUN_QUEUE = "com.zhaoshuo.localvideoplayer.download.RUN_QUEUE"
        const val ACTION_STOP_CURRENT = "com.zhaoshuo.localvideoplayer.download.STOP_CURRENT"
        const val ACTION_CANCEL_CURRENT = "com.zhaoshuo.localvideoplayer.download.CANCEL_CURRENT"
        const val ACTION_OPEN_DOWNLOADS = "com.zhaoshuo.localvideoplayer.download.OPEN_DOWNLOADS"
        const val EXTRA_TASK_ID = "task_id"
        const val EXTRA_OPEN_DOWNLOADS = "open_downloads"
        private const val CHANNEL_ID = "downloads"
        private const val NOTIFICATION_GROUP = "downloads"
        private const val NOTIFICATION_ID = 3001
        private const val NOTIFICATION_THROTTLE_MS = 1_000L
        private const val PROGRESS_STEP = 512 * 1024L
        private const val IO_BUFFER_SIZE = 256 * 1024
        private const val SEGMENT_MAX_RETRIES = 3
        private const val HLS_PROGRESS_FLUSH_MS = 400L
        private const val HLS_SOURCE_PLAYLIST_NAME = "source.m3u8"
        private const val AES_128_KEY_BYTES = 16
        private val RETRY_BACKOFF_MS = longArrayOf(1_000, 2_000, 4_000)

        fun taskNotificationId(taskId: String): Int = NOTIFICATION_ID + (taskId.hashCode() and 0xFFFF)

        fun hlsPackageDir(context: android.content.Context, taskId: String) =
            File(context.filesDir, "hls_downloads/$taskId")

        fun torrentPackageDir(context: android.content.Context, taskId: String) =
            File(context.filesDir, "torrent_downloads/$taskId")

        fun cleanupRangeParts(context: android.content.Context, taskId: String) {
            context.cacheDir.listFiles()
                ?.filter { it.name.startsWith("range_$taskId.") }
                ?.forEach { it.delete() }
        }
    }
}
