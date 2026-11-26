package com.pfpt.localvideoplayer.download

import android.content.ContentResolver
import android.content.ContentValues
import android.net.Uri
import android.provider.MediaStore
import java.io.File
import java.io.FileInputStream
import java.io.IOException

internal class DownloadMediaPublisher(
    private val resolver: ContentResolver,
    private val pausedMessage: String,
    private val outputFailedMessage: String,
) {
    suspend fun publish(
        task: DownloadTask,
        part: File,
        manager: DownloadManager,
        control: DownloadService.TaskControl,
    ) {
        val displayName = task.fileName
        val output = createOutput(task, displayName)
        manager.update(task.id) { it.copy(outputUri = output.toString(), fileName = displayName) }
        try {
            resolver.openOutputStream(output)?.use { destination ->
                FileInputStream(part).use { source ->
                    val buffer = ByteArray(DownloadService.IO_BUFFER_SIZE)
                    while (true) {
                        if (control.stopped) throw IOException(pausedMessage)
                        val count = source.read(buffer)
                        if (count < 0) break
                        destination.write(buffer, 0, count)
                    }
                }
            } ?: throw IOException(outputFailedMessage)
            resolver.update(
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
            if (error is kotlinx.coroutines.CancellationException) throw error
            resolver.delete(output, null, null)
            manager.update(task.id) { it.copy(outputUri = null) }
            throw error
        }
    }

    private fun createOutput(task: DownloadTask, displayName: String): Uri {
        val collection = if (task.saveToMovies) {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        } else {
            MediaStore.Downloads.EXTERNAL_CONTENT_URI
        }
        val mimeType = when {
            displayName.endsWith(".ts", true) || displayName.endsWith(".m2ts", true) -> "video/mp2t"
            displayName.endsWith(".mkv", true) -> "video/x-matroska"
            displayName.endsWith(".webm", true) -> "video/webm"
            displayName.endsWith(".avi", true) -> "video/x-msvideo"
            displayName.endsWith(".mov", true) -> "video/quicktime"
            else -> "video/mp4"
        }
        return resolver.insert(
            collection,
            ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(
                    MediaStore.MediaColumns.RELATIVE_PATH,
                    if (task.saveToMovies) "Movies/LocalVideoPlayer" else "Download/LocalVideoPlayer",
                )
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            },
        ) ?: throw IOException(outputFailedMessage)
    }
}
