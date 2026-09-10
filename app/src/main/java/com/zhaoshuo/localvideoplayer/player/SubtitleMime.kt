package com.zhaoshuo.localvideoplayer.player

import androidx.media3.common.MimeTypes

fun subtitleMimeType(uriString: String): String {
    val path = uriString.substringBefore('?').substringBefore('#').lowercase()
    return when {
        path.endsWith(".vtt") -> MimeTypes.TEXT_VTT
        path.endsWith(".ass") || path.endsWith(".ssa") -> MimeTypes.TEXT_SSA
        else -> MimeTypes.APPLICATION_SUBRIP
    }
}

fun sidecarSubtitleCandidates(fileName: String): List<String> {
    val base = fileName.substringBeforeLast('.', fileName)
    if (base.isBlank()) return emptyList()
    return listOf("$base.srt", "$base.vtt", "$base.ass", "$base.ssa")
}
