package com.zhaoshuo.localvideoplayer.util

data class ParsedTitle(
    val seriesName: String,
    val season: Int?,
    val episode: Int?,
)

object TitleParser {

    private val sxePattern = Regex("""(?i)S(\d{1,2})E(\d{1,3})""")
    private val epPattern = Regex("""(?i)(?:^|[\s._-])E(?:P)?(\d{1,3})(?:[\s._\-\[]|$)""")
    private val chineseEpPattern = Regex("""第\s*(\d{1,3})\s*集""")
    private val dashEpPattern = Regex("""[\s\-–—]\s*(\d{1,3})(?:\s*[\[\(]|$)""")
    private val bracketEpPattern = Regex("""\[(\d{1,3})\]""")
    private val trailingEpPattern = Regex("""[\s._-](\d{1,3})$""")
    private val qualityPattern = Regex(
        """(?i)\b(\d{3,4}p|4k|hdr|hevc|x264|x265|aac|flac|web-?dl|bluray|bdrip|dvdrip|hdtv)\b""",
    )
    private val bracketContentPattern = Regex("""\[[^\]]*]""")
    private val parenContentPattern = Regex("""[（(][^)）]*[)）]""")
    private val yearPattern = Regex("""\b(19|20)\d{2}\b""")

    fun parse(filename: String): ParsedTitle {
        val nameWithoutExt = filename.substringBeforeLast('.').trim()
        var season: Int? = null
        var episode: Int? = null
        var working = nameWithoutExt

        sxePattern.find(working)?.let { match ->
            season = match.groupValues[1].toIntOrNull()
            episode = match.groupValues[2].toIntOrNull()
            working = working.replace(match.value, " ")
        }

        if (episode == null) {
            chineseEpPattern.find(working)?.let { match ->
                episode = match.groupValues[1].toIntOrNull()
                working = working.replace(match.value, " ")
            }
        }

        if (episode == null) {
            epPattern.find(working)?.let { match ->
                episode = match.groupValues[1].toIntOrNull()
                working = working.replace(match.value, " ")
            }
        }

        if (episode == null) {
            dashEpPattern.find(working)?.let { match ->
                episode = match.groupValues[1].toIntOrNull()
                working = working.replace(match.value, " ")
            }
        }

        if (episode == null) {
            bracketEpPattern.findAll(working).lastOrNull()?.let { match ->
                val num = match.groupValues[1].toIntOrNull()
                if (num != null && num in 1..999) {
                    episode = num
                    working = working.replace(match.value, " ")
                }
            }
        }

        if (episode == null) {
            trailingEpPattern.find(working)?.let { match ->
                episode = match.groupValues[1].toIntOrNull()
                working = working.replace(match.value, " ")
            }
        }

        val seriesName = cleanSeriesName(working)
        return ParsedTitle(
            seriesName = seriesName.ifBlank { nameWithoutExt },
            season = season,
            episode = episode,
        )
    }

    fun normalizeForGrouping(name: String): String {
        return name
            .lowercase()
            .replace(bracketContentPattern, " ")
            .replace(parenContentPattern, " ")
            .replace(qualityPattern, " ")
            .replace(yearPattern, " ")
            .replace(Regex("""[._\-–—·]+"""), " ")
            .replace(Regex("""[^\p{L}\p{N}\s]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    private fun cleanSeriesName(raw: String): String {
        return raw
            .replace(bracketContentPattern, " ")
            .replace(parenContentPattern, " ")
            .replace(qualityPattern, " ")
            .replace(yearPattern, " ")
            .replace(Regex("""[._\-–—]+"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }
}
