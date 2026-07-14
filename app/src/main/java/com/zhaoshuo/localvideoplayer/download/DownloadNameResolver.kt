package com.zhaoshuo.localvideoplayer.download

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder

object DownloadNameResolver {
    data class ParsedShare(
        val title: String?,
        val url: String?,
    )

    fun parseSharedText(text: String): ParsedShare {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return ParsedShare(null, null)
        if ('|' in trimmed) {
            val parts = trimmed.split('|', limit = 2)
            if (parts.size == 2) {
                val left = parts[0].trim()
                val right = parts[1].trim()
                return when {
                    left.isHttpUrl() -> ParsedShare(title = sanitizeTitle(right), url = left)
                    right.isHttpUrl() -> ParsedShare(title = sanitizeTitle(left), url = right)
                    else -> ParsedShare(title = sanitizeTitle(left), url = null)
                }
            }
        }
        val lines = trimmed.lines().map { it.trim() }.filter { it.isNotBlank() }
        val urlLine = lines.firstOrNull { it.isHttpUrl() }
        if (lines.size >= 2 && urlLine != null) {
            val titleLine = lines.firstOrNull { it != urlLine && !it.isHttpUrl() }
            if (titleLine != null) {
                return ParsedShare(title = sanitizeTitle(titleLine), url = urlLine)
            }
        }
        if (trimmed.isHttpUrl()) return ParsedShare(null, trimmed)
        return ParsedShare(null, urlLine)
    }

    suspend fun resolve(url: String, fallback: String): String = resolveForEnqueue(url, "", fallback)

    suspend fun resolveForEnqueue(url: String, userFileName: String, fallback: String): String {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return fallback
        val isHls = DownloadUrlClassifier.isHlsDownloadUrl(trimmed)
        val sanitizedUser = sanitizeTitle(userFileName)
        if (sanitizedUser.isNotBlank()) {
            return toOutputFileName(sanitizedUser, isHls, fallback)
        }
        extractTitleFromUrl(trimmed)?.let { return toOutputFileName(it, isHls, fallback) }
        if (DownloadUrlClassifier.isLocalProxyUrl(trimmed)) {
            localBaseUrl(trimmed)?.let { base ->
                withContext(Dispatchers.IO) {
                    fetchFongMiMedia(base)
                }?.let { media ->
                    val composed = composeFongMiTitle(media, trimmed)
                    if (composed.isNotBlank()) {
                        return toOutputFileName(composed, isHls, fallback)
                    }
                }
            }
        }
        val directUrl = DownloadUrlResolver.resolveDownloadUrl(trimmed)
        inferTitleFromCdnPath(directUrl)?.let {
            return toOutputFileName(it, isHls, fallback)
        }
        return if (isHls) {
            DownloadUrlClassifier.defaultFileName(trimmed, fallback)
        } else {
            directUrl.substringBefore('?').substringAfterLast('/').ifBlank { fallback }
        }
    }

    internal fun inferTitleFromCdnPath(url: String): String? {
        val path = url.substringBefore('?').substringBefore('#')
        val segments = path.split('/').filter { it.isNotBlank() }
        if (segments.size < 2) return null
        val fileName = segments.last()
        val parent = segments[segments.size - 2]
        val dateSegment = segments.firstOrNull { Regex("""^\d{8}$""").matches(it) }
        val folderStem = when {
            fileName.substringBeforeLast('.').lowercase() in GENERIC_M3U8_NAMES -> parent
            else -> fileName.substringBeforeLast('.').takeIf { it.isNotBlank() } ?: parent
        }
        return when {
            dateSegment != null && folderStem.isNotBlank() && folderStem != dateSegment -> "$dateSegment-$folderStem"
            dateSegment != null -> dateSegment
            folderStem.isNotBlank() -> folderStem
            else -> null
        }?.let { enrichTitleFromStreamUrl(it, url) }
    }

    private val GENERIC_M3U8_NAMES = setOf("index", "playlist", "master", "stream", "media", "chunklist")

    internal fun composeFongMiTitle(media: FongMiMedia, downloadUrl: String): String {
        val title = media.title.trim()
        val artist = media.artist.trim()
        val combined = when {
            title.isEmpty() -> artist
            artist.isEmpty() -> title
            title.contains(artist) -> title
            artist.contains(title) -> artist
            isEpisodeLabel(artist) -> "$title-$artist"
            isEpisodeLabel(title) -> title
            else -> title
        }
        return enrichTitleFromStreamUrl(combined, downloadUrl)
    }

    internal fun isEpisodeLabel(value: String): Boolean {
        if (Regex("""\d{6,8}""").containsMatchIn(value)) return true
        if (value.endsWith("上") || value.endsWith("下")) return true
        if (Regex("""第\s*\d+\s*集""").containsMatchIn(value)) return true
        if (Regex("""S\d+E\d+""", RegexOption.IGNORE_CASE).containsMatchIn(value)) return true
        return false
    }

    internal fun enrichTitleFromStreamUrl(title: String, downloadUrl: String): String {
        val nested = DownloadUrlClassifier.extractNestedM3u8Url(downloadUrl) ?: downloadUrl
        val dateSegment = Regex("""/(\d{8})/""").find(nested)?.groupValues?.get(1) ?: return title
        if (title.contains(dateSegment)) return title
        return "$title-$dateSegment"
    }

    internal data class FongMiMedia(
        val title: String,
        val artist: String,
    )

    fun sanitizeTitle(name: String): String =
        name.trim().replace(Regex("[\\\\/:*?\"<>|]"), "_").takeIf { it.isNotBlank() }.orEmpty()

    fun toOutputFileName(title: String, isHls: Boolean, fallback: String): String {
        val sanitized = sanitizeTitle(title).ifBlank { return fallback }
        val baseName = sanitized.substringAfterLast('/')
        if (!isHls) return sanitized
        return if ('.' in baseName) sanitized else "$sanitized.mp4"
    }

    fun composeRenameFileName(currentFileName: String, newName: String): String? {
        val sanitized = sanitizeTitle(newName).ifBlank { return null }
        val dotIndex = currentFileName.lastIndexOf('.')
        val extension = if (dotIndex > 0 && dotIndex < currentFileName.lastIndex) {
            currentFileName.substring(dotIndex + 1).takeIf { it.isNotBlank() }
        } else {
            null
        }
        return if (extension != null) "$sanitized.$extension" else sanitized
    }

    private fun fetchFongMiMedia(baseUrl: String): FongMiMedia? = runCatching {
        val connection = (URL("$baseUrl/media").openConnection() as HttpURLConnection).apply {
            connectTimeout = 3_000
            readTimeout = 3_000
            requestMethod = "GET"
        }
        try {
            if (connection.responseCode !in 200..299) return null
            val body = connection.inputStream.bufferedReader().readText()
            if (body.isBlank()) return null
            val json = JSONObject(body)
            val title = json.optString("title").trim()
            val artist = json.optString("artist").trim()
            if (title.isEmpty() && artist.isEmpty()) return null
            FongMiMedia(title = title, artist = artist)
        } finally {
            connection.disconnect()
        }
    }.getOrNull()

    internal fun extractTitleFromUrl(url: String): String? {
        titleFromQuery(url)?.let { return it }
        var current = extractUrlParam(url)
        while (current != null) {
            titleFromQuery(current)?.let { return it }
            current = extractUrlParam(current)
        }
        return null
    }

    private fun titleFromQuery(url: String): String? {
        val query = url.substringAfter('?', "").substringBefore('#')
        if (query.isEmpty()) return null
        return query.split('&').firstNotNullOfOrNull { param ->
            val key = param.substringBefore('=').lowercase()
            if (key !in TITLE_QUERY_KEYS) return@firstNotNullOfOrNull null
            decodeOnce(param.substringAfter('=')).trim().takeIf { it.isNotEmpty() }
        }
    }

    private fun localBaseUrl(url: String): String? {
        if (!DownloadUrlClassifier.isLocalProxyUrl(url)) return null
        val authority = url.trim().substringAfter("://").substringBefore('/')
        if (authority.isBlank()) return null
        return "http://$authority"
    }

    private fun extractUrlParam(url: String): String? {
        val query = url.substringAfter('?', "").substringBefore('#')
        if (query.isEmpty()) return null
        return query.split('&')
            .firstOrNull { it.startsWith("url=", ignoreCase = true) }
            ?.substringAfter('=')
            ?.takeIf { it.isNotBlank() }
            ?.let(::decodeOnce)
    }

    private fun decodeOnce(value: String): String = runCatching {
        URLDecoder.decode(value, Charsets.UTF_8.name())
    }.getOrDefault(value)

    private fun String.isHttpUrl(): Boolean =
        startsWith("http://") || startsWith("https://")

    private fun String.takeHttpUrl(): String? = takeIf { it.isHttpUrl() }

    private val TITLE_QUERY_KEYS = setOf("name", "title", "filename", "n", "vod_name")
}
