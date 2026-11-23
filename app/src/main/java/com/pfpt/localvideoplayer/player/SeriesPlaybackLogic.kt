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