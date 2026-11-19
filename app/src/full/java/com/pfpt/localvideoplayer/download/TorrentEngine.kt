package com.pfpt.localvideoplayer.download

import com.pfpt.localvideoplayer.download.torrent.TorrentSession
import java.io.File

internal object TorrentEngine {
    suspend fun downloadMagnet(
        taskId: String,
        magnetUri: String,
        saveDir: File,
        shouldStop: () -> Boolean,
        isCanceled: () -> Boolean,
        onName: suspend (String) -> Unit,
        onProgress: suspend (downloaded: Long, total: Long, speedBps: Long) -> Unit,
    ): File = TorrentSession.downloadMagnet(
        taskId = taskId,
        magnetUri = magnetUri,
        saveDir = saveDir,
        shouldStop = shouldStop,
        isCanceled = isCanceled,
        onName = onName,
        onProgress = onProgress,
    )

    fun releaseForServiceDestroy() {
        TorrentSession.releaseForServiceDestroy()
    }
}
