package com.zhaoshuo.localvideoplayer.download

object DownloadFormat {
    fun formatBytes(bytes: Long): String {
        if (bytes < 0) return ""
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return "%.1f KB".format(kb)
        val mb = kb / 1024.0
        if (mb < 1024) return "%.1f MB".format(mb)
        return "%.2f GB".format(mb / 1024.0)
    }

    fun formatSpeed(bps: Long): String {
        if (bps <= 0) return "0 B/s"
        return "${formatBytes(bps)}/s"
    }

    fun formatEta(seconds: Long): String {
        if (seconds <= 0) return "0:00"
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60
        return if (hours > 0) {
            "%d:%02d:%02d".format(hours, minutes, secs)
        } else {
            "%d:%02d".format(minutes, secs)
        }
    }

    fun formatProgressPercent(downloaded: Long, total: Long, completedSegments: Int, totalSegments: Int): Int {
        if (total > 0 && downloaded >= 0) {
            return ((downloaded * 100) / total).toInt().coerceIn(0, 100)
        }
        if (totalSegments > 0 && completedSegments >= 0) {
            return ((completedSegments * 100) / totalSegments).toInt().coerceIn(0, 100)
        }
        return 0
    }

    fun formatProgressFraction(downloaded: Long, total: Long): Float {
        if (total <= 0 || downloaded < 0) return 0f
        return (downloaded.toFloat() / total).coerceIn(0f, 1f)
    }

    fun formatSegmentFraction(completedSegments: Int, totalSegments: Int): Float {
        if (totalSegments <= 0 || completedSegments < 0) return 0f
        return (completedSegments.toFloat() / totalSegments).coerceIn(0f, 1f)
    }
}
