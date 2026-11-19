package com.pfpt.localvideoplayer.download

import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

class LiteDownloadEnginesTest {
    @Test
    fun mergeThrowsUnavailable() {
        val error = runCatching {
            HlsMergeEngine.merge(
                segmentFiles = emptyList(),
                durationsSec = emptyList(),
                format = HlsSegmentFormat.TS,
                initFile = null,
                outputFile = File("unused.mp4"),
            )
        }.exceptionOrNull()
        assertTrue(error is AdvancedDownloadUnavailable)
    }

    @Test
    fun torrentDownloadThrowsUnavailable() {
        val error = runBlocking {
            runCatching {
                TorrentEngine.downloadMagnet(
                    taskId = "task",
                    magnetUri = "magnet:?xt=urn:btih:0123456789abcdef0123456789abcdef01234567",
                    saveDir = File("unused"),
                    shouldStop = { false },
                    isCanceled = { false },
                    onName = {},
                    onProgress = { _, _, _ -> },
                )
            }.exceptionOrNull()
        }
        assertTrue(error is AdvancedDownloadUnavailable)
    }
}
