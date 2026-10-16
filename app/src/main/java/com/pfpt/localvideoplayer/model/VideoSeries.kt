package com.zhaoshuo.localvideoplayer.model

enum class SeriesGroupKind {
    EPISODE,
    DIRECTORY,
    BUCKET,
    SHORT_CLIPS,
}

data class VideoSeries(
    val id: String,
    val title: String,
    val episodes: List<VideoItem>,
    val groupKind: SeriesGroupKind = SeriesGroupKind.EPISODE,
)
