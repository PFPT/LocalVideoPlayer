package com.pfpt.localvideoplayer.download

import java.io.File

internal object TorrentDownloader {
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