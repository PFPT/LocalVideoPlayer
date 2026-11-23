package com.pfpt.localvideoplayer.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
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
    fun commitWhenNoHoldNotAppliedTracksReadySameIndex() {
        val request = seriesTrackRequest()
        assertTrue(
            shouldCommitSeriesTrackApply(
                request = request,
                indexNow = request.index,
                holdAtStart = null,
                holdNow = null,
                applied = null,
                tracksReady = true,
            ),
        )
    }

    @Test
    fun doNotCommitWhenTracksNotReady() {
        val request = seriesTrackRequest()
        assertFalse(
            shouldCommitSeriesTrackApply(
                request = request,
                indexNow = request.index,
                holdAtStart = null,
                holdNow = null,
                applied = null,
                tracksReady = false,
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
                holdAtStart = null,
                holdNow = null,
                applied = request,
                tracksReady = true,
            ),
        )
    }

    @Test
    fun doNotCommitWhenHoldNowDiffersFromHoldAtStart() {
        val request = seriesTrackRequest()
        val holdAtStart = SeriesTrackHold(index = request.index, seriesKey = request.seriesKey)
        assertFalse(
            shouldCommitSeriesTrackApply(
                request = request,
                indexNow = request.index,
                holdAtStart = holdAtStart,
                holdNow = SeriesTrackHold(index = request.index + 1, seriesKey = request.seriesKey),
                applied = null,
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
                holdAtStart = null,
                holdNow = null,
                applied = null,
                tracksReady = true,
            ),
        )
    }

    @Test
    fun doNotCommitWhenHoldMatchesRequestIndexAndSeries() {
        val request = seriesTrackRequest()
        val hold = SeriesTrackHold(index = request.index, seriesKey = request.seriesKey)
        assertFalse(
            shouldCommitSeriesTrackApply(
                request = request,
                indexNow = request.index,
                holdAtStart = hold,
                holdNow = hold,
                applied = null,
                tracksReady = true,
            ),
        )
    }

    @Test
    fun commitWhenHoldIsPreviousEpisodeAndRequestIsNext() {
        val request = seriesTrackRequest().copy(index = 1)
        val hold = SeriesTrackHold(index = 0, seriesKey = request.seriesKey)
        assertTrue(
            shouldCommitSeriesTrackApply(
                request = request,
                indexNow = 1,
                holdAtStart = hold,
                holdNow = hold,
                applied = null,
                tracksReady = true,
            ),
        )
    }

    @Test
    fun seriesTextOffWhenPendingOffAndStoredLanguage() {
        assertTrue(
            seriesTextOffForApply(
                stored = SeriesTextMemory.Language("jpn"),
                pending = PendingSeriesText(
                    index = 2,
                    seriesKey = "name:show",
                    value = SeriesTextMemory.Off,
                ),
                indexNow = 2,
                seriesKey = "name:show",
            ),
        )
    }

    @Test
    fun seriesTextOffFalseWhenPendingOffIsPreviousIndex() {
        assertFalse(
            seriesTextOffForApply(
                stored = null,
                pending = PendingSeriesText(
                    index = 1,
                    seriesKey = "name:show",
                    value = SeriesTextMemory.Off,
                ),
                indexNow = 2,
                seriesKey = "name:show",
            ),
        )
    }

    @Test
    fun seriesTextOffFalseWhenPendingAutoAndStoredOff() {
        assertFalse(
            seriesTextOffForApply(
                stored = SeriesTextMemory.Off,
                pending = PendingSeriesText(
                    index = 2,
                    seriesKey = "name:show",
                    value = null,
                ),
                indexNow = 2,
                seriesKey = "name:show",
            ),
        )
    }

    @Test
    fun seriesTextOffWhenPendingNullAndStoredOff() {
        assertTrue(
            seriesTextOffForApply(
                stored = SeriesTextMemory.Off,
                pending = null,
                indexNow = 2,
                seriesKey = "name:show",
            ),
        )
    }

    @Test
    fun seriesTextOffFalseWhenPendingOffKeylessOnDifferentEpisode() {
        assertFalse(
            seriesTextOffForApply(
                stored = null,
                pending = PendingSeriesText(
                    index = 1,
                    seriesKey = null,
                    value = SeriesTextMemory.Off,
                ),
                indexNow = 2,
                seriesKey = null,
            ),
        )
    }

    @Test
    fun seriesTextOffFalseWhenPendingEqualsStoredLanguage() {
        val stored = SeriesTextMemory.Language("jpn")
        assertFalse(
            seriesTextOffForApply(
                stored = stored,
                pending = PendingSeriesText(
                    index = 2,
                    seriesKey = "name:show",
                    value = stored,
                ),
                indexNow = 2,
                seriesKey = "name:show",
            ),
        )
    }

    @Test
    fun clearedSeriesTrackHoldStaysNull() {
        assertNull(clearedSeriesTrackHold(hold = null, indexNow = 0, seriesKey = "name:show"))
    }

    @Test
    fun clearedSeriesTrackHoldKeepsMatchingHold() {
        val hold = SeriesTrackHold(index = 2, seriesKey = "name:show")
        assertSame(
            hold,
            clearedSeriesTrackHold(hold = hold, indexNow = 2, seriesKey = "name:show"),
        )
    }

    @Test
    fun clearedSeriesTrackHoldClearsWhenIndexDiffers() {
        val hold = SeriesTrackHold(index = 2, seriesKey = "name:show")
        assertNull(clearedSeriesTrackHold(hold = hold, indexNow = 3, seriesKey = "name:show"))
    }

    @Test
    fun clearedSeriesTrackHoldClearsWhenSeriesKeyDiffers() {
        val hold = SeriesTrackHold(index = 2, seriesKey = "name:show")
        assertNull(clearedSeriesTrackHold(hold = hold, indexNow = 2, seriesKey = "name:other"))
    }

    @Test
    fun clearedAppliedSeriesTracksStaysNull() {
        assertNull(
            clearedAppliedSeriesTracks(applied = null, indexNow = 0, seriesKey = "name:show"),
        )
    }

    @Test
    fun clearedAppliedSeriesTracksKeepsMatchingRequest() {
        val applied = SeriesTrackApplyRequest(
            index = 2,
            seriesKey = "name:show",
            audioLanguage = "jpn",
            text = SeriesTextMemory.Language("jpn"),
        )
        assertSame(
            applied,
            clearedAppliedSeriesTracks(applied = applied, indexNow = 2, seriesKey = "name:show"),
        )
    }

    @Test
    fun clearedAppliedSeriesTracksClearsWhenIndexDiffers() {
        val applied = SeriesTrackApplyRequest(
            index = 2,
            seriesKey = "name:show",
            audioLanguage = "jpn",
            text = null,
        )
        assertNull(
            clearedAppliedSeriesTracks(applied = applied, indexNow = 3, seriesKey = "name:show"),
        )
    }

    @Test
    fun clearedAppliedSeriesTracksClearsWhenSeriesKeyDiffers() {
        val applied = SeriesTrackApplyRequest(
            index = 2,
            seriesKey = "name:show",
            audioLanguage = "jpn",
            text = null,
        )
        assertNull(
            clearedAppliedSeriesTracks(applied = applied, indexNow = 2, seriesKey = "name:other"),
        )
    }

    @Test
    fun clearedPendingSeriesTextStaysNull() {
        assertNull(
            clearedPendingSeriesText(pending = null, indexNow = 0, seriesKey = "name:show"),
        )
    }

    @Test
    fun clearedPendingSeriesTextKeepsMatchingPending() {
        val pending = PendingSeriesText(
            index = 2,
            seriesKey = "name:show",
            value = SeriesTextMemory.Off,
        )
        assertSame(
            pending,
            clearedPendingSeriesText(pending = pending, indexNow = 2, seriesKey = "name:show"),
        )
    }

    @Test
    fun clearedPendingSeriesTextClearsWhenIndexDiffers() {
        val pending = PendingSeriesText(
            index = 2,
            seriesKey = "name:show",
            value = SeriesTextMemory.Off,
        )
        assertNull(
            clearedPendingSeriesText(pending = pending, indexNow = 3, seriesKey = "name:show"),
        )
    }

    @Test
    fun clearedPendingSeriesTextClearsWhenSeriesKeyDiffers() {
        val pending = PendingSeriesText(
            index = 2,
            seriesKey = "name:show",
            value = SeriesTextMemory.Off,
        )
        assertNull(
            clearedPendingSeriesText(pending = pending, indexNow = 2, seriesKey = "name:other"),
        )
    }

    @Test
    fun clearedPendingSeriesTextKeepsKeylessPendingOnSameIndex() {
        val pending = PendingSeriesText(
            index = 2,
            seriesKey = null,
            value = SeriesTextMemory.Off,
        )
        assertSame(
            pending,
            clearedPendingSeriesText(pending = pending, indexNow = 2, seriesKey = null),
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