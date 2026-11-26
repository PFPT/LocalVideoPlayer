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

        fun exists(index: Int): Boolean =
            segmentFile(index).exists() || legacySegmentFile(hlsPackage, playlist.segmentFormat(), index).exists()

        val allOnDisk = (0 until total).all(::exists)
        if (allOnDisk) return HlsResumeState(completedSegments = total, shouldWipe = false)

        val metadataMatches = task.hlsPlaylistUrl == playlist.url &&
            task.hlsPlaylistSignature == playlist.signature
        if (!metadataMatches) {
            return HlsResumeState(
                completedSegments = 0,
                shouldWipe = hlsPackage.listFiles()?.isNotEmpty() == true,
            )
        }

        var contiguous = 0
        while (contiguous < total && exists(contiguous)) contiguous++
        return HlsResumeState(completedSegments = contiguous, shouldWipe = false)
    }

    fun legacySegmentFile(hlsPackage: File, format: HlsSegmentFormat, index: Int): File {
        val legacyExtension = if (format == HlsSegmentFormat.FMP4) "ts" else "m4s"
        return File(hlsPackage, "seg%05d.$legacyExtension".format(index))
    }
}
