package com.pfpt.localvideoplayer.download

class DownloadSpeedTracker(
    private val windowMs: Long = 3000,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    private var lastBytes = -1L
    private var lastTime = -1L
    private var smoothedBps = 0L

    fun markProgress(downloadedBytes: Long): Long {
        val now = clock()
        if (lastTime < 0L) {
            lastBytes = downloadedBytes
            lastTime = now
            return smoothedBps
        }
        val elapsed = now - lastTime
        if (elapsed <= 0) return smoothedBps
        val deltaBytes = downloadedBytes - lastBytes
        if (deltaBytes < 0) {
            lastBytes = downloadedBytes
            lastTime = now
            return smoothedBps
        }
        val instantBps = (deltaBytes * 1000) / elapsed
        smoothedBps = if (smoothedBps == 0L) {
            instantBps
        } else if (elapsed >= windowMs) {
            instantBps
        } else {
            (smoothedBps * 3 + instantBps) / 4
        }
        lastBytes = downloadedBytes
        lastTime = now
        return smoothedBps
    }

    fun estimateRemainingSeconds(downloaded: Long, total: Long): Long? {
        if (total <= 0 || downloaded < 0 || downloaded >= total || smoothedBps <= 0) return null
        return ((total - downloaded) / smoothedBps).coerceAtLeast(0)
    }
}
