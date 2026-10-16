package com.pfpt.localvideoplayer.download

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class DownloadRecoveryWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val manager = DownloadManager.get(applicationContext)
        manager.awaitReady()
        if (!manager.hasPendingWork()) return Result.success()
        return runCatching {
            ContextCompat.startForegroundService(
                applicationContext,
                Intent(applicationContext, DownloadService::class.java)
                    .setAction(DownloadService.ACTION_RUN_QUEUE),
            )
            Result.success()
        }.getOrElse { Result.retry() }
    }
}
