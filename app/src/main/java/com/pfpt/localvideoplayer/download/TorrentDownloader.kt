package com.pfpt.localvideoplayer.download

import java.io.File
import java.io.IOException

internal object TorrentDownloader {
    suspend fun collect(
        task: DownloadTask,
        saveDir: File,
        manager: DownloadManager,
        control: DownloadService.TaskControl,
        pausedMessage: String,
        defaultFileName: String,
        onProgress: suspend (downloaded: Long, total: Long, speedBps: Long) -> Unit,
    ): File {
        saveDir.mkdirs()
        manager.update(task.id) { it.copy(partialPath = saveDir.absolutePath) }
        val videoFile = try {
            download(
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
                    if (looksLikeHash || currentName.isBlank() || currentName == defaultFileName) {
                        val withExt = if ('.' in sanitized.substringAfterLast('/')) sanitized else "$sanitized.mp4"
                        manager.update(task.id) { it.copy(fileName = withExt) }
                    }
                },
                onProgress = onProgress,
            )
        } catch (error: IOException) {
            if (control.stopped && !control.canceled) {
                throw IOException(pausedMessage)
            }
            throw error
        }
        if (control.stopped) throw IOException(pausedMessage)
        val publishedName = ensureExtension(manager.get(task.id)?.fileName ?: videoFile.name, videoFile.name)
        manager.update(task.id) { it.copy(fileName = publishedName) }
        return videoFile
    }

    private fun ensureExtension(preferred: String, fallbackFileName: String): String {
        val preferredBase = preferred.substringAfterLast('/')
        if ('.' in preferredBase) return preferred
        val ext = fallbackFileName.substringAfterLast('.', "")
        return if (ext.isNotBlank()) "$preferred.$ext" else preferred
    }

    suspend fun download(
        taskId: String,
        magnetUri: String,
        saveDir: File,
        shouldStop: () -> Boolean,
        isCanceled: () -> Boolean,
        onName: suspend (String) -> Unit,
        onProgress: suspend (downloaded: Long, total: Long, speedBps: Long) -> Unit,
    ): File = TorrentEngine.downloadMagnet(
        taskId = taskId,
        magnetUri = magnetUri,
        saveDir = saveDir,
        shouldStop = shouldStop,
        isCanceled = isCanceled,
        onName = onName,
        onProgress = onProgress,
    )
}