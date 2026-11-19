package com.pfpt.localvideoplayer.download

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
import com.pfpt.localvideoplayer.MainActivity
import com.pfpt.localvideoplayer.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

class DownloadService : Service() {
    private val downloadDispatcher = Dispatchers.IO.limitedParallelism(64)
    private val scope = CoroutineScope(SupervisorJob() + downloadDispatcher)

    internal fun launchOnScope(block: suspend () -> Unit) {
        scope.launch { block() }
    }
    private val mergeDispatcher = Dispatchers.IO.limitedParallelism(1)
    private val activeControls = ConcurrentHashMap<String, TaskControl>()
    private val activeJobs = ConcurrentHashMap<String, Job>()
    private val outputLocks = ConcurrentHashMap<String, Mutex>()
    internal val speedTrackers = ConcurrentHashMap<String, DownloadSpeedTracker>()
    private val lastProgressReportAt = ConcurrentHashMap<String, Long>()
    internal val hlsByteCounters = ConcurrentHashMap<String, AtomicLong>()
    internal val hlsInflightBytes = ConcurrentHashMap<String, AtomicLong>()
    internal val hlsSizeHints = ConcurrentHashMap<String, Long>()
    private val lastProgressDbAt = ConcurrentHashMap<String, Long>()
    private val taskProgressSnapshots = ConcurrentHashMap<String, DownloadTask>()
    private val lastNotificationAt = ConcurrentHashMap<String, Long>()
    private val queueLock = Any()
    private val fillLock = Mutex()
    private var schedulerJob: Job? = null
    @Volatile private var serviceStartId = 0

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createChannel()
        synchronized(queueLock) {
            serviceStartId = startId
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
        }
        return START_STICKY
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
                manager.requeueOrphanedRunning(activeJobs.keys.toSet())
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
        if (hasDownloadWork(manager)) return
        val startId = serviceStartId
        val stoppingJob = synchronized(queueLock) { schedulerJob }
        if (hasDownloadWork(manager) || serviceStartId != startId) {
            ensureSchedulerRunning()
            return
        }
        val shouldStop = synchronized(queueLock) {
            if (schedulerJob !== stoppingJob || serviceStartId != startId) {
                false
            } else {
                schedulerJob?.cancel()
                schedulerJob = null
                true
            }
        }
        if (!shouldStop || hasDownloadWork(manager)) {
            ensureSchedulerRunning()
            return
        }
        synchronized(queueLock) {
            if (serviceStartId != startId) {
                ensureSchedulerRunning()
                return
            }
            stopForeground(STOP_FOREGROUND_REMOVE)
            cancelAllTaskNotifications()
            stopSelf(startId)
        }
    }

    private suspend fun hasDownloadWork(manager: DownloadManager): Boolean =
        activeJobs.isNotEmpty() || manager.hasQueued() || manager.hasPendingEnqueue()

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
        } catch (error: CancellationException) {
            throw error
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
            hlsInflightBytes.remove(taskId)
            hlsSizeHints.remove(taskId)
            lastProgressDbAt.remove(taskId)
            manager.clearLiveProgress(taskId)
            taskProgressSnapshots.remove(taskId)
            lastNotificationAt.remove(taskId)
            cancelTaskNotification(taskId)
            if (manager.get(taskId)?.status == DownloadStatus.QUEUED) {
                fillDownloadSlots()
            }
        }
    }

    private fun failureMessage(error: Exception, @Suppress("UNUSED_PARAMETER") taskUrl: String = ""): String {
        return downloadFailureMessage(
            error = error,
            unavailableMessage = getString(R.string.download_requires_full),
            timeoutMessage = getString(R.string.download_timeout_retry),
            fallbackMessage = getString(R.string.download_direct_link_failed),
        )
    }

    private fun effectiveTask(task: DownloadTask): DownloadTask {
        val downloadUrl = DownloadUrlResolver.taskDownloadUrl(task.url)
        val headers = DownloadUrlResolver.inferHeaders(task.url, task.headers)
        return if (downloadUrl == task.url && headers == task.headers) task else task.copy(url = downloadUrl, headers = headers)
    }

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
            HlsDownloader.download(this, task, hlsPackage, manager, control)
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
        ProgressiveDownloader.download(
            service = this,
            task = task,
            part = part,
            manager = manager,
            control = control,
        )
        if (control.stopped) throw IOException(getString(R.string.download_paused))
        publishPart(task.id, part, manager, control)
    }

    private suspend fun downloadTorrent(task: DownloadTask, manager: DownloadManager, control: TaskControl) {
        val saveDir = torrentDir(task.id)
        saveDir.mkdirs()
        manager.update(task.id) { it.copy(partialPath = saveDir.absolutePath) }
        val videoFile = try {
            TorrentDownloader.download(
                taskId = task.id,
                magnetUri = task.url,
                saveDir = saveDir,
                shouldStop = { control.stopped },
                isCanceled = { control.canceled },
                onName = { name ->
                    val sanitized = DownloadNameResolver.sanitizeTitle(name)
                    if (sanitized.isBlank()) return@download
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
        val nextDownloaded = maxOf(downloaded.coerceAtLeast(0L), current.downloadedBytes)
        val speed = speedBps.coerceAtLeast(0L)
        val now = System.currentTimeMillis()
        val lastUi = lastProgressReportAt[taskId] ?: 0L
        if (!force && now - lastUi < LIVE_PROGRESS_INTERVAL_MS) return
        lastProgressReportAt[taskId] = now
        manager.publishLiveProgress(
            taskId,
            LiveDownloadProgress(
                downloadedBytes = nextDownloaded,
                totalBytes = nextTotal,
                downloadSpeedBps = speed,
            ),
        )
        val lastDb = lastProgressDbAt[taskId] ?: 0L
        if (force || now - lastDb >= PROGRESS_DB_MS) {
            lastProgressDbAt[taskId] = now
            manager.update(taskId) {
                it.copy(
                    downloadedBytes = nextDownloaded,
                    totalBytes = nextTotal,
                    downloadSpeedBps = speed,
                )
            }
        }
        updateNotification(
            task = current.copy(
                downloadedBytes = nextDownloaded,
                totalBytes = nextTotal,
                downloadSpeedBps = speed,
            ),
            taskId = taskId,
            force = force,
        )
    }

    private fun ensureExtension(preferred: String, fallbackFileName: String): String {
        val preferredBase = preferred.substringAfterLast('/')
        if ('.' in preferredBase) return preferred
        val ext = fallbackFileName.substringAfterLast('.', "")
        return if (ext.isNotBlank()) "$preferred.$ext" else preferred
    }

    internal suspend fun reportTrackedProgress(
        task: DownloadTask,
        manager: DownloadManager,
        downloaded: Long,
        total: Long,
        completedSegments: Int,
        totalSegments: Int,
        speed: Long,
        forceDb: Boolean,
    ) {
        val segmentsDone = completedSegments.coerceAtMost(totalSegments)
        manager.publishLiveProgress(
            task.id,
            LiveDownloadProgress(
                downloadedBytes = downloaded,
                totalBytes = total,
                hlsCompletedSegments = segmentsDone,
                hlsTotalSegments = totalSegments,
                downloadSpeedBps = speed,
            ),
        )
        val now = System.currentTimeMillis()
        val lastDb = lastProgressDbAt[task.id] ?: 0L
        if (forceDb || now - lastDb >= PROGRESS_DB_MS) {
            lastProgressDbAt[task.id] = now
            manager.update(task.id) {
                it.copy(
                    downloadedBytes = downloaded,
                    totalBytes = total,
                    hlsCompletedSegments = segmentsDone,
                    hlsTotalSegments = totalSegments,
                    downloadSpeedBps = speed,
                )
            }
        }
        updateNotification(
            task = task.copy(
                downloadedBytes = downloaded,
                totalBytes = total,
                hlsCompletedSegments = segmentsDone,
                hlsTotalSegments = totalSegments,
                downloadSpeedBps = speed,
            ),
            taskId = task.id,
            force = forceDb,
        )
    }

    internal fun hlsFailureMessage(failure: HlsVodFailure): String = when (failure) {
        HlsVodFailure.INVALID -> getString(R.string.download_hls_invalid)
        HlsVodFailure.EXPIRED -> getString(R.string.download_hls_expired)
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
        val playlist = resolveMergePlaylist(effective, hlsPackage)
        val segmentFormat = playlist.segmentFormat()
        val segmentFiles = listHlsSegmentFiles(hlsPackage, segmentFormat)
        segmentFiles.forEach(HlsSegmentSniffer::stripDisguise)
        hlsInitFile(hlsPackage).takeIf { it.exists() }?.let(HlsSegmentSniffer::stripDisguise)
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
        val mergedPart = partFile(task.id)
        val mergedTemp = File(mergedPart.parentFile, "${task.id}.merge.mp4")
        mergedPart.parentFile?.mkdirs()
        try {
            HlsDownloader.merge(
                segmentFiles = segmentFiles,
                durationsSec = playlist.segments.map { it.durationSec },
                format = segmentFormat,
                initFile = hlsInitFile(hlsPackage).takeIf { it.exists() },
                outputFile = mergedTemp,
                mergeDispatcher = mergeDispatcher,
                onMerging = {
                    manager.update(taskId) { it.copy(isMerging = true, downloadSpeedBps = 0) }
                    updateNotification(
                        task = task.copy(isMerging = true, downloadSpeedBps = 0),
                        taskId = task.id,
                        merging = true,
                        force = true,
                    )
                },
            )
            mergedPart.delete()
            if (!mergedTemp.renameTo(mergedPart)) {
                mergedTemp.copyTo(mergedPart, overwrite = true)
                mergedTemp.delete()
            }
        } catch (error: AdvancedDownloadUnavailable) {
            mergedTemp.delete()
            mergedPart.delete()
            throw error
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

    internal suspend fun reportProgress(
        task: DownloadTask,
        manager: DownloadManager,
        downloaded: Long,
        total: Long,
        force: Boolean = false,
    ) {
        val tracker = speedTrackers.getOrPut(task.id) { DownloadSpeedTracker() }
        val speed = tracker.markProgress(downloaded)
        val now = System.currentTimeMillis()
        val lastUi = lastProgressReportAt[task.id] ?: 0L
        if (!force && now - lastUi < LIVE_PROGRESS_INTERVAL_MS) return
        lastProgressReportAt[task.id] = now
        manager.publishLiveProgress(
            task.id,
            LiveDownloadProgress(
                downloadedBytes = downloaded,
                totalBytes = total,
                downloadSpeedBps = speed,
            ),
        )
        val lastDb = lastProgressDbAt[task.id] ?: 0L
        if (force || now - lastDb >= PROGRESS_DB_MS) {
            lastProgressDbAt[task.id] = now
            manager.update(task.id) {
                it.copy(downloadedBytes = downloaded, totalBytes = total, downloadSpeedBps = speed)
            }
        }
        updateNotification(
            task = task.copy(downloadedBytes = downloaded, totalBytes = total, downloadSpeedBps = speed),
            taskId = task.id,
            force = force,
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

        val tasks = notificationTasksInStableOrder(
            activeTaskIds.mapNotNull { taskId ->
                taskProgressSnapshots[taskId] ?: manager.get(taskId)
            },
        )
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
    ): HlsVodPlaylist {
        val localPlaylist = File(hlsPackage, HLS_SOURCE_PLAYLIST_NAME)
        if (!localPlaylist.exists()) throw IOException(getString(R.string.download_hls_invalid))
        return try {
            HlsVodPlaylistParser.parseMediaPlaylist(task.hlsPlaylistUrl ?: task.url, localPlaylist.readText())
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

    private fun buildTaskNotification(task: DownloadTask, merging: Boolean): NotificationCompat.Builder {
        val title = when {
            merging -> getString(R.string.download_notification_merging, task.fileName)
            else -> getString(R.string.download_notification_progress, task.fileName)
        }
        val builder = notification(title)
            .setGroup(NOTIFICATION_GROUP)
            .setWhen(task.createdAt)
            .setSortKey(notificationSortKey(task.createdAt, task.id))
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
        TorrentEngine.releaseForServiceDestroy()
        scope.cancel()
        super.onDestroy()
    }

    private fun partFile(id: String) = File(filesDir, "download_parts/$id.part")

    private fun hlsDir(id: String) = File(filesDir, "hls_downloads/$id")

    private fun torrentDir(id: String) = File(filesDir, "torrent_downloads/$id")

    internal fun hlsSegmentFile(hlsPackage: File, format: HlsSegmentFormat, index: Int) =
        File(hlsPackage, "seg%05d.${format.segmentExtension()}".format(index))

    internal fun resolveSegmentFile(hlsPackage: File, format: HlsSegmentFormat, index: Int): File {
        val primary = hlsSegmentFile(hlsPackage, format, index)
        if (primary.exists()) return primary
        val legacy = HlsResume.legacySegmentFile(hlsPackage, format, index)
        if (legacy.exists()) return legacy
        return primary
    }

    internal fun hlsInitFile(hlsPackage: File) = File(hlsPackage, "init.mp4")

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

    internal fun directorySize(directory: File): Long =
        directory.listFiles()?.sumOf { if (it.isFile) it.length() else 0L } ?: 0L

    private fun outputKey(task: DownloadTask) = "${task.saveToMovies}:${task.fileName.lowercase()}"

    internal class TaskControl {
        @Volatile var stopped = false
        @Volatile var canceled = false
    }

    companion object {
        const val ACTION_RUN_QUEUE = "com.pfpt.localvideoplayer.download.RUN_QUEUE"
        const val ACTION_STOP_CURRENT = "com.pfpt.localvideoplayer.download.STOP_CURRENT"
        const val ACTION_CANCEL_CURRENT = "com.pfpt.localvideoplayer.download.CANCEL_CURRENT"
        const val ACTION_OPEN_DOWNLOADS = "com.pfpt.localvideoplayer.download.OPEN_DOWNLOADS"
        const val EXTRA_TASK_ID = "task_id"
        const val EXTRA_OPEN_DOWNLOADS = "open_downloads"
        private const val CHANNEL_ID = "downloads"
        private const val NOTIFICATION_GROUP = "downloads"
        private const val NOTIFICATION_ID = 3001
        private const val NOTIFICATION_THROTTLE_MS = 1_000L
        internal const val PROGRESS_STEP = 512 * 1024L
        internal const val IO_BUFFER_SIZE = 256 * 1024
        internal const val SEGMENT_MAX_RETRIES = 3
        internal const val LIVE_PROGRESS_INTERVAL_MS = 500L
        private const val PROGRESS_DB_MS = 5_000L
        internal const val HLS_SOURCE_PLAYLIST_NAME = "source.m3u8"
        internal const val AES_128_KEY_BYTES = 16
        internal val RETRY_BACKOFF_MS = longArrayOf(1_000, 2_000, 4_000)

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
