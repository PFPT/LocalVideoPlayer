package com.pfpt.localvideoplayer.player

import com.pfpt.localvideoplayer.model.VideoItem

data class PlaylistState(
    val items: List<VideoItem>,
    val currentIndex: Int,
)
