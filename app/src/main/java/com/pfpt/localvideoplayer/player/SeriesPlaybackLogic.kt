package com.pfpt.localvideoplayer.player

const val SERIES_TEXT_OFF = "__off__"

data class SeriesPlaybackSettings(
    val audioLanguage: String? = null,
    val text: SeriesTextMemory? = null,
    val speed: Float? = null,
)

sealed interface SeriesTextMemory {
    data class Language(val code: String) : SeriesTextMemory
    data object Off : SeriesTextMemory
}

sealed interface TrackMemoryUpdate {
    data object Clear : TrackMemoryUpdate
    data class SaveLanguage(val code: String) : TrackMemoryUpdate
    data object SaveOff : TrackMemoryUpdate
    data object Leave : TrackMemoryUpdate
}

fun usableTrackLanguage(language: String?): String? {
    val trimmed = language?.trim().orEmpty()
    if (trimmed.isEmpty() || trimmed.equals("und", ignoreCase = true)) return null
    return trimmed
}

fun audioMemoryUpdate(optionId: String, language: String?): TrackMemoryUpdate {
    if (optionId == "auto") return TrackMemoryUpdate.Clear
    val code = usableTrackLanguage(language) ?: return TrackMemoryUpdate.Leave
    return TrackMemoryUpdate.SaveLanguage(code)
}

fun textMemoryUpdate(optionId: String, language: String?): TrackMemoryUpdate {
    if (optionId == "auto") return TrackMemoryUpdate.Clear
    if (optionId == "off") return TrackMemoryUpdate.SaveOff
    val code = usableTrackLanguage(language) ?: return TrackMemoryUpdate.Leave
    return TrackMemoryUpdate.SaveLanguage(code)
}

fun seriesMemoryWriteOnTap(
    alreadySelected: Boolean,
    storedLanguage: String?,
    trackLanguage: String?,
): Boolean {
    if (!alreadySelected) return true
    return sameTrackLanguage(storedLanguage, trackLanguage)
}

fun textMemoryFromStorage(value: String?): SeriesTextMemory? {
    if (value == null || value == SERIES_TEXT_OFF) {
        return if (value == SERIES_TEXT_OFF) SeriesTextMemory.Off else null
    }
    val code = usableTrackLanguage(value) ?: return null
    return SeriesTextMemory.Language(code)
}

fun normalizeSeriesSpeed(speed: Float): Float = speed.coerceIn(0.5f, 5.0f)

fun effectivePlaybackSpeed(seriesSpeed: Float?, defaultSpeed: Float): Float {
    return seriesSpeed ?: defaultSpeed
}

fun playbackSpeedAfterSeriesLoad(loaded: SeriesPlaybackSettings?, defaultSpeed: Float): Float? {
    loaded ?: return null
    return effectivePlaybackSpeed(loaded.speed, defaultSpeed)
}

fun externalSubtitleTarget(manual: String?, sidecar: String?, textOff: Boolean): String? {
    if (manual != null) return manual
    if (textOff) return null
    return sidecar
}

sealed interface SeriesAudioDirective {
    data object Auto : SeriesAudioDirective
    data class Language(val code: String) : SeriesAudioDirective
}

sealed interface SeriesTextDirective {
    data object Auto : SeriesTextDirective
    data object Off : SeriesTextDirective
    data class Language(val code: String) : SeriesTextDirective
    data object External : SeriesTextDirective
}

data class SeriesTrackDirective(
    val audio: SeriesAudioDirective,
    val text: SeriesTextDirective,
)

fun showExternalSubtitle(resolved: String?, textOff: Boolean): Boolean {
    return resolved != null && !textOff
}

fun seriesTrackDirective(
    audioLanguage: String?,
    text: SeriesTextMemory?,
    hasExternalSubtitle: Boolean,
): SeriesTrackDirective {
    val audio = if (audioLanguage == null) {
        SeriesAudioDirective.Auto
    } else {
        SeriesAudioDirective.Language(audioLanguage)
    }
    val textDirective = when {
        hasExternalSubtitle -> SeriesTextDirective.External
        text is SeriesTextMemory.Off -> SeriesTextDirective.Off
        text is SeriesTextMemory.Language -> SeriesTextDirective.Language(text.code)
        else -> SeriesTextDirective.Auto
    }
    return SeriesTrackDirective(audio = audio, text = textDirective)
}

data class SeriesTrackApplyRequest(
    val index: Int,
    val seriesKey: String?,
    val audioLanguage: String?,
    val text: SeriesTextMemory?,
)

fun shouldApplySeriesTracks(
    tracksReady: Boolean,
    request: SeriesTrackApplyRequest,
    applied: SeriesTrackApplyRequest?,
    skipped: SeriesTrackApplyRequest?,
): Boolean {
    if (!tracksReady) return false
    if (request == applied || request == skipped) return false
    return true
}

data class SeriesTrackHold(
    val index: Int,
    val seriesKey: String?,
    val audioOptionId: String? = null,
    val textOptionId: String? = null,
)

data class HeldTrackSelection(
    val audioOptionId: String?,
    val textOptionId: String?,
)

fun heldTrackSelection(
    hold: SeriesTrackHold?,
    indexNow: Int,
    seriesKey: String?,
): HeldTrackSelection? {
    if (hold == null) return null
    if (hold.index != indexNow || hold.seriesKey != seriesKey) return null
    return HeldTrackSelection(hold.audioOptionId, hold.textOptionId)
}

fun clearedSeriesTrackHold(
    hold: SeriesTrackHold?,
    indexNow: Int,
    seriesKey: String?,
): SeriesTrackHold? {
    if (hold == null) return null
    if (hold.index == indexNow && hold.seriesKey == seriesKey) return hold
    return null
}

fun clearedAppliedSeriesTracks(
    applied: SeriesTrackApplyRequest?,
    indexNow: Int,
    seriesKey: String?,
): SeriesTrackApplyRequest? {
    if (applied == null) return null
    if (applied.index == indexNow && applied.seriesKey == seriesKey) return applied
    return null
}

fun clearedPendingSeriesText(
    pending: PendingSeriesText?,
    indexNow: Int,
    seriesKey: String?,
): PendingSeriesText? {
    if (pending == null) return null
    if (pending.index == indexNow && pending.seriesKey == seriesKey) return pending
    return null
}

data class PendingSeriesText(
    val index: Int,
    val seriesKey: String?,
    val value: SeriesTextMemory?,
)

fun seriesTracksReady(
    groupsNonEmpty: Boolean,
    tracksIndex: Int,
    requestIndex: Int,
): Boolean {
    return groupsNonEmpty && tracksIndex == requestIndex
}

fun shouldCommitSeriesTrackApply(
    request: SeriesTrackApplyRequest,
    indexNow: Int,
    holdAtStart: SeriesTrackHold?,
    holdNow: SeriesTrackHold?,
    applied: SeriesTrackApplyRequest?,
    tracksReady: Boolean,
    allowHeldApply: Boolean = false,
): Boolean {
    if (!tracksReady) return false
    if (indexNow != request.index) return false
    if (holdEpisodeChanged(holdAtStart, holdNow)) return false
    if (
        !allowHeldApply &&
        applied != null &&
        holdNow != null &&
        holdNow.index == request.index &&
        holdNow.seriesKey == request.seriesKey
    ) {
        return false
    }
    if (request == applied) return false
    return true
}

fun holdEpisodeChanged(holdAtStart: SeriesTrackHold?, holdNow: SeriesTrackHold?): Boolean {
    if (holdAtStart == null || holdNow == null) return false
    return holdAtStart.index != holdNow.index || holdAtStart.seriesKey != holdNow.seriesKey
}

fun textOffOverrideKey(seriesKey: String?, videoUri: String): String {
    return seriesKey?.takeIf { it.isNotBlank() } ?: videoUri
}

fun seriesTextOffForVideo(
    videoSeriesKey: String?,
    videoUri: String,
    overrideKey: String?,
    overrideOff: Boolean,
    storedOff: Boolean,
): Boolean {
    val key = textOffOverrideKey(videoSeriesKey, videoUri)
    if (overrideKey != null && overrideKey == key) return overrideOff
    return storedOff
}

data class AttachedSubtitle(
    val target: String?,
    val textOff: Boolean,
)

fun attachedSubtitle(
    manual: String?,
    sidecar: String?,
    storedOff: Boolean,
    videoSeriesKey: String?,
    videoUri: String,
    overrideKey: String?,
    overrideOff: Boolean,
): AttachedSubtitle {
    val textOff = seriesTextOffForVideo(
        videoSeriesKey = videoSeriesKey,
        videoUri = videoUri,
        overrideKey = overrideKey,
        overrideOff = overrideOff,
        storedOff = storedOff,
    )
    return AttachedSubtitle(
        target = externalSubtitleTarget(manual, sidecar, textOff),
        textOff = textOff,
    )
}

fun seriesTextOffForApply(
    stored: SeriesTextMemory?,
    pending: PendingSeriesText?,
    indexNow: Int,
    seriesKey: String?,
): Boolean {
    val text = if (
        pending != null &&
        pending.index == indexNow &&
        pending.seriesKey == seriesKey
    ) {
        pending.value
    } else {
        stored
    }
    return text is SeriesTextMemory.Off
}
