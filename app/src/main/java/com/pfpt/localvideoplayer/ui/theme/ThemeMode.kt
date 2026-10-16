package com.pfpt.localvideoplayer.ui.theme

enum class ThemeMode {
    System,
    Dark,
    Light,
    ;

    fun resolveDark(isSystemDark: Boolean): Boolean = when (this) {
        System -> isSystemDark
        Dark -> true
        Light -> false
    }

    companion object {
        fun fromStorage(value: String?): ThemeMode {
            return entries.firstOrNull { it.name == value } ?: System
        }
    }
}
