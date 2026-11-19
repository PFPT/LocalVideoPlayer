package com.pfpt.localvideoplayer.download

import com.pfpt.localvideoplayer.R
import com.pfpt.localvideoplayer.download.DownloadService.TaskControl
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

internal object ProgressiveDownloader {
    suspend fun download(
        service: DownloadService,
        task: DownloadTask,
        part: File,
        manager: DownloadManager,
        control: TaskControl,
    ) {
        val connections = DownloadManager.get(service).connectionsPerDownload()
        val probe = probeProgressive(service, task.url, task.headers, control)
        if (probe != null &&
            probe.acceptsRanges &&
            ProgressiveRangePlanner.shouldUseMultiRange(probe.totalBytes, connections)
        ) {
            downloadProgressiveMultiRange(service, task, part, probe.totalBytes, connections, manager, control)
            return
        }
        downloadProgressiveSingle(service, task, part, manager, control)
    }

    private data class ProgressiveProbe(val totalBytes: Long, val acceptsRanges: Boolean)

    private fun probeProgressive(
        service: DownloadService,
        url: String,
        headers: Map<String, String>,
        control: TaskControl,
    ): ProgressiveProbe? {
        if (control.stopped) throw IOException(service.getString(R.string.download_paused))
        runCatching {
            DownloadRequests.executeRequest(url, headers, rangeHeader = "bytes=0-0").use { response ->
                when (response.code) {
                    HttpURLConnection.HTTP_PARTIAL -> {
                        val total = DownloadRequests.contentTotalBytes(response, 0).takeIf { it > 0 }
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
            DownloadRequests.executeRequest(url, headers, method = "HEAD").use { response ->
                if (response.code !in 200..299) return@runCatching null
                val total = response.header("Content-Length")?.toLongOrNull()?.takeIf { it > 0 }
                    ?: return@runCatching null
                val accepts = response.header("Accept-Ranges")?.contains("bytes", ignoreCase = true) == true
                if (accepts) return@runCatching ProgressiveProbe(total, true)
                DownloadRequests.executeRequest(url, headers, rangeHeader = "bytes=0-0").use { ranged ->
                    if (ranged.code == HttpURLConnection.HTTP_PARTIAL) {
                        val rangedTotal = DownloadRequests.contentTotalBytes(ranged, 0).takeIf { it > 0 } ?: total
                        return@runCatching ProgressiveProbe(rangedTotal, true)
                    }
                }
                ProgressiveProbe(total, false)
            }
        }.getOrNull()
    }

    private suspend fun downloadProgressiveSingle(
        service: DownloadService,
        task: DownloadTask,
        part: File,
        manager: DownloadManager,
        control: TaskControl,
    ) {
        DownloadService.cleanupRangeParts(service, task.id)
        var offset = part.takeIf(File::exists)?.length() ?: 0L
        var response = DownloadRequests.executeRequest(task.url, task.headers, rangeStart = offset.takeIf { it > 0 })
        if (offset > 0 && response.code != HttpURLConnection.HTTP_PARTIAL) {
            response.close()
            part.delete()
            offset = 0
            manager.update(task.id) { it.copy(downloadedBytes = 0, totalBytes = -1) }
            response = DownloadRequests.executeRequest(task.url, task.headers)
        }
        try {
            if (response.code !in 200..299) throw IOException("HTTP ${response.code}")
            val total = DownloadRequests.contentTotalBytes(response, offset)
            val body = response.body ?: throw IOException(service.getString(R.string.download_failed))
            FileOutputStream(part, offset > 0).use { destination ->
                BufferedInputStream(body.byteStream(), DownloadService.IO_BUFFER_SIZE).use { source ->
                    copyStream(service, source, destination, offset, total, task, manager, control)
                }
            }
        } finally {
            response.close()
        }
    }

    private suspend fun downloadProgressiveMultiRange(
        service: DownloadService,
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
            val piece = rangePartFile(service, task.id, index)
            if (piece.exists() && piece.length() == range.length) {
                completedBytes.addAndGet(range.length)
            } else if (piece.exists()) {
                piece.delete()
            }
        }
        fun currentDownloaded(): Long = completedBytes.get() + inFlight.values.sum()
        manager.update(task.id) { it.copy(downloadedBytes = currentDownloaded(), totalBytes = totalBytes) }
        service.reportProgress(task, manager, currentDownloaded(), totalBytes, force = true)
        coroutineScope {
            ranges.mapIndexed { index, range ->
                async {
                    val piece = rangePartFile(service, task.id, index)
                    if (piece.exists() && piece.length() == range.length) return@async
                    downloadByteRange(
                        service,
                        url = task.url,
                        headers = task.headers,
                        range = range,
                        dest = piece,
                        control = control,
                    ) { written ->
                        inFlight[index] = written
                        service.launchOnScope {
                            service.reportProgress(task, manager, currentDownloaded(), totalBytes)
                        }
                    }
                    inFlight.remove(index)
                    completedBytes.addAndGet(range.length)
                    service.reportProgress(task, manager, currentDownloaded(), totalBytes, force = true)
                }
            }.awaitAll()
        }
        if (control.stopped) throw IOException(service.getString(R.string.download_paused))
        ranges.forEachIndexed { index, range ->
            val piece = rangePartFile(service, task.id, index)
            if (!piece.exists() || piece.length() != range.length) {
                throw IOException(service.getString(R.string.download_failed))
            }
        }
        FileOutputStream(part).use { destination ->
            ranges.indices.forEach { index ->
                if (control.stopped) throw IOException(service.getString(R.string.download_paused))
                val piece = rangePartFile(service, task.id, index)
                FileInputStream(piece).use { source ->
                    source.copyTo(destination, DownloadService.IO_BUFFER_SIZE)
                }
            }
        }
        if (part.length() != totalBytes) throw IOException(service.getString(R.string.download_failed))
        DownloadService.cleanupRangeParts(service, task.id)
        service.reportProgress(task, manager, totalBytes, totalBytes, force = true)
    }

    private fun downloadByteRange(
        service: DownloadService,
        url: String,
        headers: Map<String, String>,
        range: ByteRange,
        dest: File,
        control: TaskControl,
        onProgress: (Long) -> Unit,
    ) {
        var attempt = 0
        var lastError: IOException? = null
        while (attempt < DownloadService.SEGMENT_MAX_RETRIES) {
            if (control.stopped) throw IOException(service.getString(R.string.download_paused))
            dest.parentFile?.mkdirs()
            if (dest.exists()) dest.delete()
            try {
                DownloadRequests.executeRequest(
                    url,
                    headers,
                    rangeHeader = "bytes=${range.start}-${range.endInclusive}",
                ).use { response ->
                    if (response.code != HttpURLConnection.HTTP_PARTIAL && response.code !in 200..299) {
                        throw IOException("HTTP ${response.code}")
                    }
                    val body = response.body ?: throw IOException(service.getString(R.string.download_failed))
                    var written = 0L
                    FileOutputStream(dest).use { destination ->
                        BufferedInputStream(body.byteStream(), DownloadService.IO_BUFFER_SIZE).use { source ->
                            val buffer = ByteArray(DownloadService.IO_BUFFER_SIZE)
                            while (true) {
                                if (control.stopped) throw IOException(service.getString(R.string.download_paused))
                                val count = source.read(buffer)
                                if (count < 0) break
                                destination.write(buffer, 0, count)
                                written += count
                                onProgress(written)
                            }
                        }
                    }
                    if (written != range.length) {
                        throw IOException(service.getString(R.string.download_failed))
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
                    maxAttempts = DownloadService.SEGMENT_MAX_RETRIES,
                )
                if (next == null) break
                attempt = next
                Thread.sleep(DownloadService.RETRY_BACKOFF_MS[attempt - 1])
            }
        }
        throw lastError ?: IOException(service.getString(R.string.download_failed))
    }

    private fun rangePartFile(service: DownloadService, taskId: String, index: Int): File =
        File(service.cacheDir, "range_$taskId.$index")

    private fun cleanupRangeParts(service: DownloadService, taskId: String) {
        DownloadService.cleanupRangeParts(service, taskId)
    }

    private suspend fun copyStream(
        service: DownloadService,
        source: BufferedInputStream,
        destination: FileOutputStream,
        initialBytes: Long,
        total: Long,
        task: DownloadTask,
        manager: DownloadManager,
        control: TaskControl,
    ) {
        val buffer = ByteArray(DownloadService.IO_BUFFER_SIZE)
        var downloaded = initialBytes
        while (true) {
            if (control.stopped) throw IOException(service.getString(R.string.download_paused))
            val count = source.read(buffer)
            if (count < 0) break
            destination.write(buffer, 0, count)
            downloaded += count
            if (downloaded % DownloadService.PROGRESS_STEP < count) {
                service.reportProgress(task, manager, downloaded, total)
            }
        }
        service.reportProgress(task, manager, downloaded, total, force = true)
    }
}
