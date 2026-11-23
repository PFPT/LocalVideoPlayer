package com.pfpt.localvideoplayer.player

import android.content.Context
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import com.pfpt.localvideoplayer.R
import com.pfpt.localvideoplayer.data.needsConstantBitrateSeeking
import com.pfpt.localvideoplayer.model.VideoItem
import com.pfpt.localvideoplayer.ui.components.TrackOption
import com.pfpt.localvideoplayer.util.toMediaItem

fun buildExoPlayer(
    context: Context,
    playlist: List<VideoItem>,
    startIndex: Int,
    softDecode: Boolean,
    subtitleUri: Uri? = null,
    startPositionMs: Long = 0L,
): ExoPlayer {
    val renderersFactory = DefaultRenderersFactory(context).apply {
        setEnableDecoderFallback(true)
        if (softDecode) {
            setMediaCodecSelector(MediaCodecSelector.PREFER_SOFTWARE)
        }
    }
    val safeIndex = startIndex.coerceIn(0, (playlist.size - 1).coerceAtLeast(0))
    val useCbr = playlist.any { needsConstantBitrateSeeking(context, it) }
    val mediaSourceFactory = if (useCbr) {
        DefaultMediaSourceFactory(
            context,
            DefaultExtractorsFactory().setConstantBitrateSeekingEnabled(true),
        )
    } else {
        DefaultMediaSourceFactory(context)
    }
    return ExoPlayer.Builder(context, renderersFactory)
        .setMediaSourceFactory(mediaSourceFactory)
        .build()
        .apply {
        setWakeMode(C.WAKE_MODE_LOCAL)
        setAudioAttributes(
            androidx.media3.common.AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .build(),
            true,
        )
        val items = playlist.map { video ->
            if (subtitleUri != null && playlist.getOrNull(safeIndex)?.id == video.id) {
                video.toMediaItemWithSubtitle(context, subtitleUri)
            } else {
                video.toMediaItem(context)
            }
        }
        setMediaItems(items, safeIndex, startPositionMs.coerceAtLeast(0L))
        prepare()
        playWhenReady = true
    }
}

fun PlaybackException.isSoftwareDecodeRetryable(): Boolean {
    return when (errorCode) {
        PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
        PlaybackException.ERROR_CODE_DECODING_FAILED,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
        -> true
        else -> false
    }
}

fun PlaybackException.isSourceReadFailure(): Boolean {
    return when (errorCode) {
        PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
        PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE,
        PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
        PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
        PlaybackException.ERROR_CODE_IO_NO_PERMISSION,
        PlaybackException.ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED,
        PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE,
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
        PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
        PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED,
        -> true
        else -> false
    }
}

fun VideoItem.toMediaItemWithSubtitle(context: Context, subtitleUri: Uri): MediaItem {
    val mime = subtitleMimeType(subtitleUri.toString())
    val subtitle = MediaItem.SubtitleConfiguration.Builder(subtitleUri)
        .setMimeType(mime)
        .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
        .build()
    return toMediaItem(context).buildUpon()
        .setSubtitleConfigurations(listOf(subtitle))
        .build()
}

fun playingTrackFallback(
    preferredLanguage: String?,
    hasOverride: Boolean,
    trackDisabled: Boolean,
    selectedLanguages: List<String?>,
): Boolean {
    if (preferredLanguage == null || hasOverride || trackDisabled) return false
    if (selectedLanguages.isEmpty()) return false
    return selectedLanguages.none { sameTrackLanguage(preferredLanguage, it) }
}

fun trackChoiceSelected(
    choice: String,
    trackSelected: Boolean,
    hasOverride: Boolean,
    trackDisabled: Boolean,
    preferredLanguage: String?,
    trackLanguage: String?,
    playingFallback: Boolean = false,
): Boolean {
    return when (choice) {
        "auto" -> preferredLanguage == null && !trackDisabled && !hasOverride
        "off" -> trackDisabled
        else -> trackSelected && (
            hasOverride ||
                sameTrackLanguage(preferredLanguage, trackLanguage) ||
                playingFallback
        )
    }
}

fun sameTrackLanguage(preferredLanguage: String?, trackLanguage: String?): Boolean {
    val preferred = preferredLanguage?.trim()?.takeIf { it.isNotEmpty() } ?: return false
    val track = usableTrackLanguage(trackLanguage) ?: return false
    return track.equals(preferred, ignoreCase = true)
}

fun ExoPlayer.trackOptions(
    context: Context,
    type: @C.TrackType Int,
): List<TrackOption> {
    val tracks = currentTracks
    val disabled = trackSelectionParameters.disabledTrackTypes.contains(type)
    val override = hasExplicitOverride(type)
    val preferred = preferredTrackLanguage(type)
    val selectedLanguages = mutableListOf<String?>()
    tracks.groups.forEach { group ->
        if (group.type != type) return@forEach
        for (i in 0 until group.length) {
            if (!group.isTrackSupported(i) || !group.isTrackSelected(i)) continue
            selectedLanguages.add(group.getTrackFormat(i).language)
        }
    }
    val playingFallback = playingTrackFallback(
        preferredLanguage = preferred,
        hasOverride = override,
        trackDisabled = disabled,
        selectedLanguages = selectedLanguages,
    )
    val options = mutableListOf(
        TrackOption(
            id = "auto",
            label = context.getString(R.string.track_auto),
            selected = trackChoiceSelected(
                choice = "auto",
                trackSelected = false,
                hasOverride = override,
                trackDisabled = disabled,
                preferredLanguage = preferred,
                trackLanguage = null,
                playingFallback = playingFallback,
            ),
        ),
    )
    if (type == C.TRACK_TYPE_TEXT) {
        options.add(
            TrackOption(
                id = "off",
                label = context.getString(R.string.track_off),
                selected = trackChoiceSelected(
                    choice = "off",
                    trackSelected = false,
                    hasOverride = override,
                    trackDisabled = disabled,
                    preferredLanguage = preferred,
                    trackLanguage = null,
                    playingFallback = playingFallback,
                ),
            ),
        )
    }
    tracks.groups.forEachIndexed { groupIndex, group ->
        if (group.type != type) return@forEachIndexed
        for (i in 0 until group.length) {
            if (!group.isTrackSupported(i)) continue
            val format = group.getTrackFormat(i)
            val label = format.label
                ?: format.language
                ?: "${groupIndex + 1}-${i + 1}"
            options.add(
                TrackOption(
                    id = "$groupIndex:$i",
                    label = label,
                    selected = trackChoiceSelected(
                        choice = "$groupIndex:$i",
                        trackSelected = group.isTrackSelected(i),
                        hasOverride = override,
                        trackDisabled = disabled,
                        preferredLanguage = preferred,
                        trackLanguage = format.language,
                        playingFallback = playingFallback,
                    ),
                    language = format.language,
                ),
            )
        }
    }
    return options
}

private fun ExoPlayer.preferredTrackLanguage(type: @C.TrackType Int): String? {
    val languages = when (type) {
        C.TRACK_TYPE_AUDIO -> trackSelectionParameters.preferredAudioLanguages
        C.TRACK_TYPE_TEXT -> trackSelectionParameters.preferredTextLanguages
        else -> return null
    }
    return languages.firstOrNull()
}

fun ExoPlayer.heldOptionApplied(type: @C.TrackType Int, optionId: String): Boolean {
    if (optionId == "auto") {
        return preferredTrackLanguage(type) == null &&
            !trackSelectionParameters.disabledTrackTypes.contains(type) &&
            !hasExplicitOverride(type)
    }
    if (optionId == "off") {
        return trackSelectionParameters.disabledTrackTypes.contains(type)
    }
    val parts = optionId.split(":")
    if (parts.size != 2) return false
    val groupIndex = parts[0].toIntOrNull() ?: return false
    val trackIndex = parts[1].toIntOrNull() ?: return false
    val group = currentTracks.groups.getOrNull(groupIndex) ?: return false
    if (group.type != type) return false
    val override = trackSelectionParameters.overrides[group.mediaTrackGroup] ?: return false
    return trackIndex in override.trackIndices
}

private fun ExoPlayer.hasExplicitOverride(type: @C.TrackType Int): Boolean {
    return trackSelectionParameters.overrides.values.any { it.type == type }
}

fun ExoPlayer.applyTrackSelection(type: @C.TrackType Int, optionId: String) {
    var params = trackSelectionParameters.buildUpon()
        .clearOverridesOfType(type)
        .setTrackTypeDisabled(type, false)
        .build()
    when (optionId) {
        "auto" -> {
            trackSelectionParameters = when (type) {
                C.TRACK_TYPE_AUDIO -> params.buildUpon()
                    .setPreferredAudioLanguage(null)
                    .build()
                C.TRACK_TYPE_TEXT -> params.buildUpon()
                    .setPreferredTextLanguage(null)
                    .build()
                else -> params
            }
        }
        "off" -> {
            trackSelectionParameters = params.buildUpon()
                .setTrackTypeDisabled(type, true)
                .build()
        }
        else -> {
            val parts = optionId.split(":")
            if (parts.size != 2) return
            val groupIndex = parts[0].toIntOrNull() ?: return
            val trackIndex = parts[1].toIntOrNull() ?: return
            val group = currentTracks.groups.getOrNull(groupIndex) ?: return
            if (group.type != type) return
            val override = TrackSelectionOverride(group.mediaTrackGroup, listOf(trackIndex))
            trackSelectionParameters = params.buildUpon()
                .addOverride(override)
                .build()
        }
    }
}

fun ExoPlayer.applySeriesTrackDirective(directive: SeriesTrackDirective) {
    val builder = trackSelectionParameters
        .buildUpon()
        .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
        .clearOverridesOfType(C.TRACK_TYPE_TEXT)
    when (val audio = directive.audio) {
        SeriesAudioDirective.Auto -> builder.setPreferredAudioLanguage(null)
        is SeriesAudioDirective.Language -> builder.setPreferredAudioLanguage(audio.code)
    }
    when (val text = directive.text) {
        SeriesTextDirective.Auto,
        SeriesTextDirective.External,
        -> builder
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            .setPreferredTextLanguage(null)
        SeriesTextDirective.Off -> builder
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            .setPreferredTextLanguage(null)
        is SeriesTextDirective.Language -> builder
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            .setPreferredTextLanguage(text.code)
    }
    trackSelectionParameters = builder.build()
}

fun Tracks.hasType(type: @C.TrackType Int): Boolean {
    return groups.any { it.type == type && it.length > 0 }
}
