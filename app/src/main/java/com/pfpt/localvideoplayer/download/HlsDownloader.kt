package com.pfpt.localvideoplayer.download

import com.pfpt.localvideoplayer.R
import com.pfpt.localvideoplayer.download.DownloadService.TaskControl
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

internal object HlsDownloader {
    suspend fun download(
        service: DownloadService,
        task: DownloadTask,
        hlsPackage: File,
        manager: DownloadManager,
        control: TaskControl,
    ) {
        val resolved = resolveHlsSource(service, task, hlsPackage, control)
        val playlist = resolved.playlist
        val resumeState = HlsResume.resolve(task, hlsPackage, playlist) { index ->
            service.hlsSegmentFile(hlsPackage, playlist.segmentFormat(), index)
        }
        val segmentFormat = playlist.segmentFormat()
        val completedSegments = resumeState.completedSegments
        if (resumeState.shouldWipe) hlsPackage.listFiles()?.forEach { it.delete() }
        File(hlsPackage, DownloadService.HLS_SOURCE_PLAYLIST_NAME).writeText(resolved.mediaText)
        val initialBytes = if (resumeState.shouldWipe) 0L else service.directorySize(hlsPackage)
        service.hlsByteCounters[task.id] = java.util.concurrent.atomic.AtomicLong(initialBytes)
        manager.update(task.id) {
            it.copy(
                hlsCompletedSegments = completedSegments,
                hlsTotalSegments = playlist.segments.size,
                hlsPlaylistUrl = playlist.url,
                hlsPlaylistSignature = playlist.signature,
                downloadedBytes = initialBytes,
                totalBytes = estimateHlsTotalBytes(service, initialBytes, completedSegments, playlist.segments.size, task.id),
                isMerging = false,
            )
        }
        val completedCounter = java.util.concurrent.atomic.AtomicInteger(completedSegments)
        suspend fun publishSnapshot(forceDb: Boolean) {
            val done = completedCounter.get().coerceAtMost(playlist.segments.size)
            val downloaded = currentHlsDownloaded(service, task.id)
            val totalBytes = estimateHlsTotalBytes(
                service,
                currentHlsBytes(service, task.id),
                done,
                playlist.segments.size,
                task.id,
            )
            val speed = service.speedTrackers.getOrPut(task.id) { DownloadSpeedTracker() }.markProgress(downloaded)
            service.reportTrackedProgress(
                task = task,
                manager = manager,
                downloaded = downloaded,
                total = totalBytes,
                completedSegments = done,
                totalSegments = playlist.segments.size,
                speed = speed,
                forceDb = forceDb,
            )
        }
        kotlinx.coroutines.coroutineScope {
            val ticker = launch {
                while (isActive) {
                    kotlinx.coroutines.delay(DownloadService.LIVE_PROGRESS_INTERVAL_MS)
                    publishSnapshot(forceDb = false)
                }
            }
            try {
                playlist.initSegmentUrl?.let { initUrl ->
                    val initFile = service.hlsInitFile(hlsPackage)
                    if (!initFile.exists()) {
                        if (control.stopped) throw java.io.IOException(service.getString(com.pfpt.localvideoplayer.R.string.download_paused))
                        downloadHlsSegmentAtomic(
                            service = service,
                            taskId = task.id,
                            address = initUrl,
                            finalFile = initFile,
                            headers = task.headers,
                            control = control,
                            encryption = null,
                            playlistUrl = playlist.url,
                        )
                    }
                }
                val keys = java.util.concurrent.ConcurrentHashMap<String, ByteArray>()
                prefetchHlsKeys(service, playlist, task.headers, control, keys, playlist.url)
                val pendingSegments = playlist.segments.drop(completedSegments)
                val concurrency = DownloadManager.get(service).connectionsPerDownload()
                val nextWork = java.util.concurrent.atomic.AtomicInteger(0)
                val workers = List(concurrency.coerceAtLeast(1)) {
                    async {
                        while (true) {
                            if (control.stopped) throw java.io.IOException(service.getString(com.pfpt.localvideoplayer.R.string.download_paused))
                            val offset = nextWork.getAndIncrement()
                            if (offset >= pendingSegments.size) break
                            val segment = pendingSegments[offset]
                            val segmentIndex = completedSegments + offset
                            val segmentFile = service.hlsSegmentFile(hlsPackage, segmentFormat, segmentIndex)
                            if (service.resolveSegmentFile(hlsPackage, segmentFormat, segmentIndex).exists()) {
                                completedCounter.incrementAndGet()
                                continue
                            }
                            val encryption = segment.encryption
                            val keyIv = encryption?.let { enc ->
                                val key = keys[enc.keyUrl]
                                    ?: requestHlsBytes(service, enc.keyUrl, task.headers, control, playlist.url).also {
                                        if (it.size != DownloadService.AES_128_KEY_BYTES) {
                                            throw java.io.IOException(service.getString(com.pfpt.localvideoplayer.R.string.download_hls_key_invalid))
                                        }
                                        keys[enc.keyUrl] = it
                                    }
                                key to enc.iv
                            }
                            downloadHlsSegmentAtomic(
                                service = service,
                                taskId = task.id,
                                address = segment.url,
                                finalFile = segmentFile,
                                headers = task.headers,
                                control = control,
                                encryption = keyIv,
                                playlistUrl = playlist.url,
                            )
                            completedCounter.incrementAndGet()
                        }
                    }
                }
                workers.awaitAll()
            } finally {
                ticker.cancel()
                publishSnapshot(forceDb = true)
            }
        }
    }

    private fun unwrapPlaylist(service: DownloadService, playlist: HlsVodPlaylist): HlsVodPlaylist = playlist.copy(
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

    private fun unwrapResolved(service: DownloadService, result: HlsVodResolveResult): HlsVodResolveResult =
        result.copy(playlist = unwrapPlaylist(service, result.playlist))

    private fun prefetchHlsKeys(
        service: DownloadService,
        playlist: HlsVodPlaylist,
        headers: Map<String, String>,
        control: TaskControl,
        keys: ConcurrentHashMap<String, ByteArray>,
        playlistUrl: String,
    ) {
        playlist.segments.mapNotNull { it.encryption?.keyUrl }.toSet().forEach { keyUrl ->
            if (control.stopped) throw IOException(service.getString(R.string.download_paused))
            keys.getOrPut(keyUrl) {
                requestHlsBytes(service, keyUrl, headers, control, playlistUrl).also {
                    if (it.size != DownloadService.AES_128_KEY_BYTES) throw IOException(service.getString(R.string.download_hls_key_invalid))
                }
            }
        }
    }

    private fun estimateHlsTotalBytes(
        service: DownloadService,
        completedBytes: Long,
        completedSegments: Int,
        totalSegments: Int,
        taskId: String,
    ): Long {
        if (totalSegments <= 0) return -1
        val average = when {
            completedSegments > 0 && completedBytes > 0 -> completedBytes / completedSegments
            else -> service.hlsSizeHints[taskId] ?: return -1
        }
        if (average <= 0) return -1
        return average * totalSegments
    }

    private fun requestHlsText(
        service: DownloadService,
        address: String,
        headers: Map<String, String>,
        control: TaskControl,
        playlistUrl: String? = null,
    ): String = String(requestHlsBytes(service, address, headers, control, playlistUrl), Charsets.UTF_8)

    private fun requestHlsBytes(
        service: DownloadService,
        address: String,
        headers: Map<String, String>,
        control: TaskControl,
        playlistUrl: String? = null,
    ): ByteArray {
        var attempt = 0
        var lastError: IOException? = null
        while (attempt < DownloadService.SEGMENT_MAX_RETRIES) {
            if (control.stopped) throw IOException(service.getString(R.string.download_paused))
            try {
                DownloadRequests.executeHlsRequest(address, headers, playlistUrl).use { response ->
                    if (response.code !in 200..299) {
                        throw IOException(service.getString(R.string.download_hls_request_failed, response.code))
                    }
                    val body = response.body ?: throw IOException(service.getString(R.string.download_failed))
                    return body.bytes()
                }
            } catch (error: IOException) {
                if (control.stopped || control.canceled) throw error
                lastError = error
                val next = nextDownloadAttempt(
                    stopped = false,
                    canceled = false,
                    attempt = attempt,
                    maxAttempts = DownloadService.SEGMENT_MAX_RETRIES,
                )
                if (next == null) break
                attempt = next
                Thread.sleep(DownloadService.RETRY_BACKOFF_MS[attempt - 1])
            }
        }
        throw lastError ?: IOException(service.getString(R.string.download_failed))
    }

    private fun downloadHlsSegmentAtomic(
        service: DownloadService,
        taskId: String,
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
        while (attempt < DownloadService.SEGMENT_MAX_RETRIES) {
            if (control.stopped) throw IOException(service.getString(R.string.download_paused))
            finalFile.parentFile?.mkdirs()
            if (partFile.exists()) partFile.delete()
            var written = 0L
            try {
                DownloadRequests.executeHlsRequest(address, headers, playlistUrl).use { response ->
                    if (response.code !in 200..299) {
                        throw IOException(service.getString(R.string.download_hls_request_failed, response.code))
                    }
                    val body = response.body ?: throw IOException(service.getString(R.string.download_failed))
                    val length = body.contentLength()
                    if (length > 0) service.hlsSizeHints.putIfAbsent(taskId, length)
                    FileOutputStream(partFile).use { fileOut ->
                        BufferedInputStream(body.byteStream(), DownloadService.IO_BUFFER_SIZE).use { source ->
                            try {
                                if (encryption != null) {
                                    val (key, iv) = encryption
                                    HlsAesDecryptor.decryptingOutputStream(fileOut, key, iv).use { decrypted ->
                                        written += copyCounted(service, source, decrypted, control, taskId)
                                        decrypted.flush()
                                    }
                                } else {
                                    written += copyCounted(service, source, fileOut, control, taskId)
                                }
                            } catch (error: IOException) {
                                throw error
                            } catch (error: Exception) {
                                throw IOException(service.getString(R.string.download_hls_decryption_failed), error)
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
                    throw IOException(service.getString(R.string.download_failed))
                }
                HlsSegmentSniffer.stripDisguise(finalFile)
                addHlsBytes(service, taskId, finalFile.length())
                addHlsInflight(service, taskId, -written)
                return
            } catch (error: IOException) {
                addHlsInflight(service, taskId, -written)
                if (control.stopped || control.canceled) throw error
                lastError = error
                partFile.delete()
                val next = nextDownloadAttempt(
                    stopped = false,
                    canceled = false,
                    attempt = attempt,
                    maxAttempts = DownloadService.SEGMENT_MAX_RETRIES,
                )
                if (next == null) break
                attempt = next
                Thread.sleep(DownloadService.RETRY_BACKOFF_MS[attempt - 1])
            }
        }
        throw lastError ?: IOException(service.getString(R.string.download_failed))
    }

    private fun copyCounted(
        service: DownloadService,
        source: BufferedInputStream,
        sink: java.io.OutputStream,
        control: TaskControl,
        taskId: String,
    ): Long {
        val buffer = ByteArray(DownloadService.IO_BUFFER_SIZE)
        var written = 0L
        try {
            while (true) {
                if (control.stopped) throw IOException(service.getString(R.string.download_paused))
                val count = source.read(buffer)
                if (count < 0) return written
                sink.write(buffer, 0, count)
                written += count
                addHlsInflight(service, taskId, count.toLong())
            }
        } catch (error: Exception) {
            addHlsInflight(service, taskId, -written)
            throw error
        }
    }

    private fun resolveHlsSource(
        service: DownloadService,
        task: DownloadTask,
        hlsPackage: File,
        control: TaskControl,
    ): HlsVodResolveResult {
        val localPlaylist = File(hlsPackage, DownloadService.HLS_SOURCE_PLAYLIST_NAME)
        if (localPlaylist.exists()) {
            val text = localPlaylist.readText()
            val parsed = try {
                HlsVodPlaylistParser.parseMediaPlaylist(task.hlsPlaylistUrl ?: task.url, text)
            } catch (error: HlsVodException) {
                throw IOException(service.hlsFailureMessage(error.failure), error)
            }
            return HlsVodResolveResult(playlist = unwrapPlaylist(service, parsed), mediaText = text)
        }
        return try {
            unwrapResolved(
                service,
                HlsVodResolver { address -> requestHlsText(service, address, task.headers, control) }
                    .resolveWithMediaText(task.url),
            )
        } catch (error: HlsVodException) {
            throw IOException(service.hlsFailureMessage(error.failure), error)
        }
    }

    private fun currentHlsBytes(service: DownloadService, taskId: String): Long = service.hlsByteCounters[taskId]?.get() ?: 0L

    private fun currentHlsDownloaded(service: DownloadService, taskId: String): Long {
        val inflight = service.hlsInflightBytes[taskId]?.get() ?: 0L
        return currentHlsBytes(service, taskId) + inflight.coerceAtLeast(0L)
    }

    private fun addHlsBytes(service: DownloadService, taskId: String, bytes: Long) {
        if (bytes == 0L) return
        service.hlsByteCounters.getOrPut(taskId) { AtomicLong(0L) }.addAndGet(bytes)
    }

    private fun addHlsInflight(service: DownloadService, taskId: String, bytes: Long) {
        if (bytes == 0L) return
        service.hlsInflightBytes.getOrPut(taskId) { AtomicLong(0L) }.addAndGet(bytes)
    }
}
