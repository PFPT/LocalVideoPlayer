package com.pfpt.localvideoplayer.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SeriesPlaybackLogicTest {
    @Test
    fun blankAndUndLanguagesAreNotUsable() {
        assertNull(usableTrackLanguage(null))
        assertNull(usableTrackLanguage("  "))
        assertNull(usableTrackLanguage("und"))
        assertNull(usableTrackLanguage(" UND "))
    }

    @Test
    fun usableLanguageKeepsTrimmedText() {
        assertEquals("jpn", usableTrackLanguage(" jpn "))
    }

    @Test
    fun audioAutoClearsAndMissingLanguageLeavesMemory() {
        assertEquals(TrackMemoryUpdate.Clear, audioMemoryUpdate("auto", "jpn"))
        assertEquals(TrackMemoryUpdate.SaveLanguage("eng"), audioMemoryUpdate("0:1", " eng "))
        assertEquals(TrackMemoryUpdate.Leave, audioMemoryUpdate("0:1", "und"))
        assertEquals(TrackMemoryUpdate.Leave, audioMemoryUpdate("0:1", null))
    }

    @Test
    fun textAutoClearsOffSavesAndMissingLanguageLeavesMemory() {
        assertEquals(TrackMemoryUpdate.Clear, textMemoryUpdate("auto", "jpn"))
        assertEquals(TrackMemoryUpdate.SaveOff, textMemoryUpdate("off", null))
        assertEquals(TrackMemoryUpdate.SaveLanguage("chi"), textMemoryUpdate("1:0", "chi"))
        assertEquals(TrackMemoryUpdate.Leave, textMemoryUpdate("1:0", " "))
    }

    @Test
    fun storedTextOffUndAndBlankReadAsSpecified() {
        assertNull(textMemoryFromStorage(null))
        assertEquals(SeriesTextMemory.Off, textMemoryFromStorage(SERIES_TEXT_OFF))
        assertNull(textMemoryFromStorage("und"))
        assertNull(textMemoryFromStorage("  "))
        assertEquals(SeriesTextMemory.Language("jpn"), textMemoryFromStorage(" jpn "))
    }

    @Test
    fun seriesSpeedOverridesDefault() {
        assertEquals(1.5f, effectivePlaybackSpeed(seriesSpeed = 1.5f, defaultSpeed = 1f), 0f)
        assertEquals(1.25f, effectivePlaybackSpeed(seriesSpeed = null, defaultSpeed = 1.25f), 0f)
    }

    @Test
    fun seriesSpeedIsClamped() {
        assertEquals(0.5f, normalizeSeriesSpeed(0.1f), 0f)
        assertEquals(5f, normalizeSeriesSpeed(8f), 0f)
        assertEquals(1.5f, normalizeSeriesSpeed(1.5f), 0f)
    }

    @Test
    fun boostIgnoresIncomingEffectiveSpeed() {
        val boosted = SpeedSnapshot(playbackSpeed = 2f, savedPlaybackSpeed = 1f, boosted = true)
        val effective = effectivePlaybackSpeed(seriesSpeed = 1.5f, defaultSpeed = 1f)
        assertEquals(boosted, speedAfterDefault(boosted, effective))
    }

    @Test
    fun manualSubtitleWinsAndOffSkipsSidecar() {
        assertEquals(
            "manual.srt",
            externalSubtitleTarget(manual = "manual.srt", sidecar = "side.srt", textOff = true),
        )
        assertNull(externalSubtitleTarget(manual = null, sidecar = "side.srt", textOff = true))
        assertEquals(
            "side.srt",
            externalSubtitleTarget(manual = null, sidecar = "side.srt", textOff = false),
        )
    }

    @Test
    fun externalSubtitleLeavesEmbeddedChoiceAlone() {
        val directive = seriesTrackDirective(
            audioLanguage = "jpn",
            text = SeriesTextMemory.Language("chi"),
            hasExternalSubtitle = true,
        )
        assertEquals(SeriesAudioDirective.Language("jpn"), directive.audio)
        assertEquals(SeriesTextDirective.External, directive.text)
    }

    @Test
    fun offWithoutExternalDisablesText() {
        val directive = seriesTrackDirective(
            audioLanguage = null,
            text = SeriesTextMemory.Off,
            hasExternalSubtitle = false,
        )
        assertEquals(SeriesAudioDirective.Auto, directive.audio)
        assertEquals(SeriesTextDirective.Off, directive.text)
    }

    @Test
    fun languageWithoutExternalSelectsEmbeddedText() {
        val directive = seriesTrackDirective(
            audioLanguage = null,
            text = SeriesTextMemory.Language("chi"),
            hasExternalSubtitle = false,
        )
        assertEquals(SeriesTextDirective.Language("chi"), directive.text)
    }

    @Test
    fun tracksApplyOnceAndSkipTheSelectionJustSaved() {
        val request = SeriesTrackApplyRequest(
            index = 0,
            seriesKey = "name:show",
            audioLanguage = "jpn",
            text = null,
        )
        assertFalse(
            shouldApplySeriesTracks(
                tracksReady = false,
                request = request,
                applied = null,
                skipped = null,
            ),
        )
        assertTrue(
            shouldApplySeriesTracks(
                tracksReady = true,
                request = request,
                applied = null,
                skipped = null,
            ),
        )
        assertFalse(
            shouldApplySeriesTracks(
                tracksReady = true,
                request = request,
                applied = request,
                skipped = null,
            ),
        )
        val selected = request.copy(text = SeriesTextMemory.Off)
        assertFalse(
            shouldApplySeriesTracks(
                tracksReady = true,
                request = selected,
                applied = request,
                skipped = selected,
            ),
        )
        assertTrue(
            shouldApplySeriesTracks(
                tracksReady = true,
                request = selected.copy(index = 1),
                applied = request,
                skipped = selected,
            ),
        )
    }

    @Test
    fun commitWhenTracksReadySameIndexSkippedNullAndNotApplied() {
        val request = seriesTrackRequest()
        assertTrue(
            shouldCommitSeriesTrackApply(
                request = request,
                indexNow = request.index,
                applied = null,
                skippedAtStart = null,
                skippedNow = null,
                tracksReady = true,
            ),
        )
    }

    @Test
    fun doNotCommitWhenSameRequestAlreadyApplied() {
        val request = seriesTrackRequest()
        assertFalse(
            shouldCommitSeriesTrackApply(
                request = request,
                indexNow = request.index,
                applied = request,
                skippedAtStart = null,
                skippedNow = null,
                tracksReady = true,
            ),
        )
    }

    @Test
    fun doNotCommitWhenSkippedNowDiffersFromSkippedAtStart() {
        val request = seriesTrackRequest().copy(index = 1)
        val previous = seriesTrackRequest()
        assertFalse(
            shouldCommitSeriesTrackApply(
                request = request,
                indexNow = request.index,
                applied = null,
                skippedAtStart = previous,
                skippedNow = previous.copy(text = SeriesTextMemory.Off),
                tracksReady = true,
            ),
        )
    }

    @Test
    fun doNotCommitWhenIndexNowDiffersFromRequest() {
        val request = seriesTrackRequest()
        assertFalse(
            shouldCommitSeriesTrackApply(
                request = request,
                indexNow = request.index + 1,
                applied = null,
                skippedAtStart = null,
                skippedNow = null,
                tracksReady = true,
            ),
        )
    }

    @Test
    fun doNotCommitWhenSkippedAtStartAndSkippedNowBothEqualRequest() {
        val request = seriesTrackRequest()
        assertFalse(
            shouldCommitSeriesTrackApply(
                request = request,
                indexNow = request.index,
                applied = null,
                skippedAtStart = request,
                skippedNow = request,
                tracksReady = true,
            ),
        )
    }

    @Test
    fun commitWhenNextEpisodeRequestKeepsPreviousEpisodeSkip() {
        val previous = seriesTrackRequest()
        val request = previous.copy(index = 1)
        assertTrue(
            shouldCommitSeriesTrackApply(
                request = request,
                indexNow = 1,
                applied = null,
                skippedAtStart = previous,
                skippedNow = previous,
                tracksReady = true,
            ),
        )
    }

    @Test
    fun seriesTextOffUsesPendingOffOnThisIndexAndSeries() {
        val pending = seriesTrackRequest().copy(text = SeriesTextMemory.Off)
        assertTrue(
            seriesTextOffForApply(
                stored = SeriesTextMemory.Language("jpn"),
                pending = pending,
                indexNow = pending.index,
                seriesKey = pending.seriesKey,
            ),
        )
    }

    @Test
    fun seriesTextOffFollowsStoredWhenPendingIsPreviousEpisode() {
        val pending = seriesTrackRequest().copy(text = SeriesTextMemory.Off)
        assertFalse(
            seriesTextOffForApply(
                stored = SeriesTextMemory.Language("jpn"),
                pending = pending,
                indexNow = pending.index + 1,
                seriesKey = pending.seriesKey,
            ),
        )
    }

    @Test
    fun seriesTextOffFollowsStoredWhenPendingTextEqualsStored() {
        val stored = SeriesTextMemory.Language("jpn")
        val pending = seriesTrackRequest().copy(text = stored)
        assertFalse(
            seriesTextOffForApply(
                stored = stored,
                pending = pending,
                indexNow = pending.index,
                seriesKey = pending.seriesKey,
            ),
        )
    }

    @Test
    fun seriesTextOffWhenStoredOffAndPendingNull() {
        assertTrue(
            seriesTextOffForApply(
                stored = SeriesTextMemory.Off,
                pending = null,
                indexNow = 0,
                seriesKey = "name:show",
            ),
        )
    }

    private fun seriesTrackRequest(): SeriesTrackApplyRequest {
        return SeriesTrackApplyRequest(
            index = 0,
            seriesKey = "name:show",
            audioLanguage = "jpn",
            text = null,
        )
    }
}