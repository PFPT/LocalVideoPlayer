package com.pfpt.localvideoplayer.ui.theme

enum class IconStyle {
    Filled,
    Outlined,
    Rounded,
    ;

    companion object {
        fun fromStorage(value: String?): IconStyle {
            return entries.firstOrNull { it.name == value } ?: Filled
        }
    }
}
