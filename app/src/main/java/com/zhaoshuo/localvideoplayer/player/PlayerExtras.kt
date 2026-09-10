package com.zhaoshuo.localvideoplayer.player

import android.content.Context
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import com.zhaoshuo.localvideoplayer.R
import com.zhaoshuo.localvideoplayer.model.VideoItem
import com.zhaoshuo.localvideoplayer.ui.components.TrackOption
import com.zhaoshuo.localvideoplayer.util.toMediaItem

fun buildExoPlayer(
    context: Context,
    playlist: List<VideoItem>,
    startIndex: Int,
    softDecode: Boolean,
    subtitleUri: Uri? = null,
): ExoPlayer {
    val renderersFactory = DefaultRenderersFactory(context).apply {
        if (softDecode) {
            setMediaCodecSelector(MediaCodecSelector.PREFER_SOFTWARE)
            setEnableDecoderFallback(true)
        }
    }
    return ExoPlayer.Builder(context, renderersFactory).build().apply {
        val items = playlist.map { video ->
            if (subtitleUri != null && playlist.getOrNull(startIndex)?.id == video.id) {
                video.toMediaItemWithSubtitle(subtitleUri)
            } else {
                video.toMediaItem()
            }
        }
        setMediaItems(items, startIndex.coerceIn(0, (playlist.size - 1).coerceAtLeast(0)), 0L)
        prepare()
        playWhenReady = true
    }
}

fun VideoItem.toMediaItemWithSubtitle(subtitleUri: Uri): MediaItem {
    val mime = subtitleMimeType(subtitleUri.toString())
    val subtitle = MediaItem.SubtitleConfiguration.Builder(subtitleUri)
        .setMimeType(mime)
        .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
        .build()
    return toMediaItem().buildUpon()
        .setSubtitleConfigurations(listOf(subtitle))
        .build()
}

fun ExoPlayer.trackOptions(
    context: Context,
    type: @C.TrackType Int,
): List<TrackOption> {
    val tracks = currentTracks
    val options = mutableListOf(
        TrackOption(
            id = "auto",
            label = context.getString(R.string.track_auto),
            selected = !hasExplicitOverride(type),
        ),
    )
    if (type == C.TRACK_TYPE_TEXT) {
        options.add(
            TrackOption(
                id = "off",
                label = context.getString(R.string.track_off),
                selected = trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT),
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
            val selected = group.isTrackSelected(i)
            options.add(
                TrackOption(
                    id = "$groupIndex:$i",
                    label = label,
                    selected = selected && hasExplicitOverride(type),
                ),
            )
        }
    }
    return options
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
            trackSelectionParameters = params
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

fun Tracks.hasType(type: @C.TrackType Int): Boolean {
    return groups.any { it.type == type && it.length > 0 }
}
