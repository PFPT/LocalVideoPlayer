package com.zhaoshuo.localvideoplayer.model

import androidx.compose.ui.layout.ContentScale

enum class AspectRatioMode {
    FIT,
    FILL,
    STRETCH,
    RATIO_16_9,
    RATIO_4_3,
    ;

    val contentScale: ContentScale
        get() = when (this) {
            FIT, RATIO_16_9, RATIO_4_3 -> ContentScale.Fit
            FILL -> ContentScale.Crop
            STRETCH -> ContentScale.FillBounds
        }

    val frameAspectRatio: Float?
        get() = when (this) {
            RATIO_16_9 -> 16f / 9f
            RATIO_4_3 -> 4f / 3f
            else -> null
        }

    companion object {
        fun fromStorage(value: String?): AspectRatioMode {
            return entries.firstOrNull { it.name == value } ?: FIT
        }
    }
}
