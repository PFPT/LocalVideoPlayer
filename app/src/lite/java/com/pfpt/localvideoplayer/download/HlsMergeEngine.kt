package com.pfpt.localvideoplayer.download

import java.io.File

internal object HlsMergeEngine {
    fun merge(
        segmentFiles: List<File>,
        durationsSec: List<Double>,
        format: HlsSegmentFormat,
        initFile: File?,
        outputFile: File,
    ): Unit = throw AdvancedDownloadUnavailable()
}
