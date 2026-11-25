package com.pfpt.localvideoplayer.ui.library

import com.pfpt.localvideoplayer.model.PlaybackRecord
import com.pfpt.localvideoplayer.model.SeriesGroupKind
import com.pfpt.localvideoplayer.model.VideoLibraryEntry

data class RecentHomeRow(
    val key: String,
    val title: String,
    val record: PlaybackRecord,
)

fun recentHomeRows(
    records: List<PlaybackRecord>,
    entries: List<VideoLibraryEntry>,
): List<RecentHomeRow> {
    val seriesByUri = buildMap {
        for (entry in entries) {
            if (entry !is VideoLibraryEntry.Series) continue
            if (entry.series.groupKind != SeriesGroupKind.EPISODE) continue
            for (episode in entry.series.episodes) {
                put(episode.uri.toString(), entry.series)
            }
        }
    }
    val seenSeries = mutableSetOf<String>()
    return records.mapNotNull { record ->
        val series = seriesByUri[record.uri.toString()]
        if (series == null) {
            RecentHomeRow(
                key = record.uri.toString(),
                title = record.title,
                record = record,
            )
        } else if (seenSeries.add(series.id)) {
            RecentHomeRow(
                key = "series:${series.id}",
                title = series.title,
                record = record,
            )
        } else {
            null
        }
    }
}
