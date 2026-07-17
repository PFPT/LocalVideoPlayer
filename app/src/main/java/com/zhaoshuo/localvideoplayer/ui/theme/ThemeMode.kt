package com.zhaoshuo.localvideoplayer.ui.theme

enum class ThemeMode {
    Dark,
    Light,
    ;

    companion object {
        fun fromStorage(value: String?): ThemeMode {
            return entries.firstOrNull { it.name == value } ?: Dark
        }
    }
}
