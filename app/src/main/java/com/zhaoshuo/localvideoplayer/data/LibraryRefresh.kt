package com.zhaoshuo.localvideoplayer.data

import com.zhaoshuo.localvideoplayer.model.SeriesGroupKind
import com.zhaoshuo.localvideoplayer.model.VideoItem
import com.zhaoshuo.localvideoplayer.model.VideoLibraryEntry
import com.zhaoshuo.localvideoplayer.model.VideoSeries
import com.zhaoshuo.localvideoplayer.util.SeriesGrouper
import com.zhaoshuo.localvideoplayer.util.TitleParser

fun withResolvedDurations(videos: List<VideoItem>, resolved: Map<String, Long>): List<VideoItem> {
    if (resolved.isEmpty()) return videos
    var changed = false
    val next = videos.map { video ->
        if (video.durationMs > 0L) return@map video
        val duration = resolved[video.uri.toString()] ?: return@map video
        if (duration <= 0L) return@map video
        changed = true
        video.copy(durationMs = duration)
    }
    return if (changed) next else videos
}

fun patchLibraryEntries(
    entries: List<VideoLibraryEntry>,
    nextVideos: List<VideoItem>,
): List<VideoLibraryEntry> {
    val nextByUri = nextVideos.associateBy { it.uri.toString() }
    val seen = mutableSetOf<String>()
    val patched = mutableListOf<VideoLibraryEntry>()
    entries.forEach { entry ->
        when (entry) {
            is VideoLibraryEntry.Single -> {
                val updated = nextByUri[entry.video.uri.toString()] ?: return@forEach
                seen += updated.uri.toString()
                patched += VideoLibraryEntry.Single(updated)
            }
            is VideoLibraryEntry.Series -> {
                val episodes = entry.series.episodes.mapNotNull { episode ->
                    nextByUri[episode.uri.toString()]?.also { seen += it.uri.toString() }
                }
                if (episodes.isEmpty()) return@forEach
                patched += VideoLibraryEntry.Series(
                    entry.series.copy(episodes = SeriesGrouper.sortEpisodes(episodes)),
                )
            }
        }
    }
    nextVideos.filter { it.uri.toString() !in seen }.forEach { video ->
        val index = seriesIndexFor(patched, video)
        if (index == null) {
            patched += VideoLibraryEntry.Single(video)
        } else {
            val series = (patched[index] as VideoLibraryEntry.Series).series
            val seriesKey = series.episodes.firstOrNull()?.seriesKey
            val parsed = TitleParser.parse(video.title)
            patched[index] = VideoLibraryEntry.Series(
                series.copy(
                    episodes = SeriesGrouper.sortEpisodes(
                        series.episodes + video.copy(
                            seriesKey = seriesKey ?: video.seriesKey,
                            season = parsed.season ?: video.season,
                            episode = parsed.episode ?: video.episode,
                        ),
                    ),
                ),
            )
        }
    }
    return patched
}

private fun seriesIndexFor(entries: List<VideoLibraryEntry>, video: VideoItem): Int? {
    val nameKey = seriesNameKey(video.title)
    if (nameKey != null) {
        val byName = entries.indexOfFirst { entry ->
            entry is VideoLibraryEntry.Series &&
                entry.series.groupKind == SeriesGroupKind.EPISODE &&
                entrySeriesNameKey(entry.series) == nameKey
        }
        if (byName >= 0) return byName
    }
    val folder = SeriesGrouper.libraryFolderKey(video) ?: return null
    val byFolder = entries.indexOfFirst { entry ->
        entry is VideoLibraryEntry.Series &&
            entry.series.groupKind != SeriesGroupKind.SHORT_CLIPS &&
            entry.series.episodes.any { SeriesGrouper.libraryFolderKey(it) == folder }
    }
    return byFolder.takeIf { it >= 0 }
}

private fun seriesNameKey(title: String): String? {
    val name = TitleParser.normalizeForGrouping(TitleParser.parse(title).seriesName)
    return name.takeIf { it.length >= 3 }
}

private fun entrySeriesNameKey(series: VideoSeries): String? {
    val fromEpisodes = series.episodes
        .mapNotNull { seriesNameKey(it.title) }
        .groupingBy { it }
        .eachCount()
        .maxByOrNull { it.value }
        ?.key
    if (fromEpisodes != null) return fromEpisodes
    return TitleParser.normalizeForGrouping(series.title).takeIf { it.length >= 3 }
}
