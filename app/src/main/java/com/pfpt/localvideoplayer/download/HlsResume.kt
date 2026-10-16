package com.pfpt.localvideoplayer.download

import java.io.File

internal data class HlsResumeState(
    val completedSegments: Int,
    val shouldWipe: Boolean,
)

internal object HlsResume {
    fun resolve(
        task: DownloadTask,
        hlsPackage: File,
        playlist: HlsVodPlaylist,
        segmentFile: (Int) -> File,
    ): HlsResumeState {
        val total = playlist.segments.size
        if (total == 0) return HlsResumeState(completedSegments = 0, shouldWipe = hlsPackage.exists())

        val allOnDisk = (0 until total).all { index ->
            segmentFile(index).exists() || legacySegmentFile(hlsPackage, playlist.segmentFormat(), index).exists()
        }
        if (allOnDisk) return HlsResumeState(completedSegments = total, shouldWipe = false)

        val metadataMatches = task.hlsPlaylistUrl == playlist.url &&
            task.hlsPlaylistSignature == playlist.signature &&
            task.hlsCompletedSegments in 0..total
        if (metadataMatches) {
            val completed = task.hlsCompletedSegments
            val contiguous = completed == 0 || (0 until completed).all { index ->
                segmentFile(index).exists() || legacySegmentFile(hlsPackage, playlist.segmentFormat(), index).exists()
            }
            if (contiguous) return HlsResumeState(completedSegments = completed, shouldWipe = false)
        }

        return HlsResumeState(
            completedSegments = 0,
            shouldWipe = hlsPackage.listFiles()?.isNotEmpty() == true,
        )
    }

    fun legacySegmentFile(hlsPackage: File, format: HlsSegmentFormat, index: Int): File {
        val legacyExtension = if (format == HlsSegmentFormat.FMP4) "ts" else "m4s"
        return File(hlsPackage, "seg%05d.$legacyExtension".format(index))
    }
}
