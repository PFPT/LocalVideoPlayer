package com.pfpt.localvideoplayer.download

import java.io.File

internal object HlsMergeEngine {
    fun merge(
        segmentFiles: List<File>,
        durationsSec: List<Double>,
        format: HlsSegmentFormat,
        initFile: File?,
        outputFile: File,
    ) {
        HlsMerger.mergeToMp4(
            HlsMerger.MergeInput(
                segmentFiles = segmentFiles,
                durationsSec = durationsSec,
                format = format,
                initFile = initFile,
            ),
            outputFile,
        )
    }
}
