package com.pfpt.localvideoplayer.download

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Method

class HlsMergerErrorTest {
    @Test
    fun summarizeErrorSkipsFfmpegBanner() {
        val method = HlsMerger::class.java.getDeclaredMethod(
            "summarizeError",
            String::class.java,
        ).apply { isAccessible = true }
        val summary = method.invoke(
            HlsMerger,
            """
                ffmpeg version n8.1.2 Copyright (c) 2000-2026 the FFmpeg developers
                built with Android clang
                /path/seg00000.ts: No such file or directory
            """.trimIndent(),
        ) as String
        assertFalse(summary.contains("ffmpeg version"))
        assertTrue(summary.contains("No such file", ignoreCase = true))
    }
}
