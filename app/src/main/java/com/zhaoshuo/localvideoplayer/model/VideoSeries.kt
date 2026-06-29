package com.zhaoshuo.localvideoplayer.model

data class VideoSeries(
    val id: String,
    val title: String,
    val episodes: List<VideoItem>,
)
