package com.zhaoshuo.localvideoplayer.player

import com.zhaoshuo.localvideoplayer.model.VideoItem

data class PlaylistState(
    val items: List<VideoItem>,
    val currentIndex: Int,
)
