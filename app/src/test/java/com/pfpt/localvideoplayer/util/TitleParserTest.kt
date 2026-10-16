package com.pfpt.localvideoplayer.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TitleParserTest {
    @Test
    fun parsesSxePattern() {
        val parsed = TitleParser.parse("Show.Name.S02E05.1080p.mkv")
        assertEquals("Show Name", parsed.seriesName)
        assertEquals(2, parsed.season)
        assertEquals(5, parsed.episode)
    }

    @Test
    fun parsesChineseEpisode() {
        val parsed = TitleParser.parse("某剧 第12集.mp4")
        assertEquals(12, parsed.episode)
        assertNull(parsed.season)
    }

    @Test
    fun normalizeForGroupingStripsQuality() {
        val normalized = TitleParser.normalizeForGrouping("Cool Show 1080p WEB-DL")
        assertEquals("cool show", normalized)
    }
}
