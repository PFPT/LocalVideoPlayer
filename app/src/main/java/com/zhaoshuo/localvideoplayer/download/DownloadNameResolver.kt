package com.zhaoshuo.localvideoplayer.download

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.ServerSocket
import java.net.URL
import java.net.URLDecoder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

object DownloadNameResolver {
    data class ParsedShare(
        val title: String?,
        val url: String?,
        val explicitTitle: Boolean = false,
    )

    internal data class FongMiMedia(
        val title: String,
        val artist: String,
        val url: String = "",
    )

    internal data class FongMiHistoryEntry(
        val vodName: String,
        val vodRemarks: String,
        val episodeUrl: String,
    )

    internal data class DownloadEpisodeIdentity(
        val downloadUrl: String,
        val cdnKey: String,
        val date: String,
        val folderStem: String,
    )

    private const val HISTORY_MATCH_THRESHOLD = 60
    private const val SCORE_URL_EXACT = 100
    private const val SCORE_CDN_KEY = 80
    private const val SCORE_LABEL_PRECISE = 60

    data class DownloadFieldChange(
        val text: String,
        val title: String?,
        val pasted: Boolean,
        val explicitTitle: Boolean = false,
    )

    fun applyDownloadFieldChange(current: String, incoming: String): DownloadFieldChange {
        val cleaned = stripInvisibleChars(incoming)
        val pasted = cleaned.contains('\n') ||
            cleaned.contains('\r') ||
            cleaned.length - current.length > 1
        if (!pasted) return DownloadFieldChange(text = cleaned, title = null, pasted = false)
        val parsed = parseSharedText(cleaned)
        if (!parsed.url.isNullOrBlank()) {
            return DownloadFieldChange(text = parsed.url, title = parsed.title, pasted = true, explicitTitle = parsed.explicitTitle)
        }
        val flattened = cleaned.replace("\r", "").replace("\n", "").trim()
        val reparsed = parseSharedText(flattened)
        if (!reparsed.url.isNullOrBlank()) {
            return DownloadFieldChange(text = reparsed.url, title = reparsed.title, pasted = true, explicitTitle = reparsed.explicitTitle)
        }
        return DownloadFieldChange(text = flattened, title = null, pasted = true)
    }

    fun parseSharedText(text: String): ParsedShare {
        val trimmed = stripInvisibleChars(text).trim()
        if (trimmed.isEmpty()) return ParsedShare(null, null)
        if ('|' in trimmed) {
            val parts = trimmed.split('|', limit = 2)
            if (parts.size == 2) {
                val left = parts[0].trim()
                val right = parts[1].trim()
                return when {
                    left.isDownloadUrl() -> shareWithExplicitTitle(sanitizeTitle(right), left.normalizeDownloadUrl())
                    right.isDownloadUrl() -> shareWithExplicitTitle(sanitizeTitle(left), right.normalizeDownloadUrl())
                    else -> {
                        val embedded = extractInlineDownloadUrl(trimmed)
                        ParsedShare(
                            title = sanitizeTitle(left).takeIf { embedded != null },
                            url = embedded,
                        )
                    }
                }
            }
        }
        val lines = trimmed.lines().map { it.trim() }.filter { it.isNotBlank() }
        val urlLine = lines.firstOrNull { it.isDownloadUrl() }?.normalizeDownloadUrl()
        if (lines.size >= 2 && urlLine != null) {
            val titleCandidates = lines
                .filter { it != urlLine && !it.isDownloadUrl() }
                .map(::sanitizeTitle)
                .filter { it.isNotBlank() && !isPlaybackStatusLabel(it) }
            val titleLine = titleCandidates.firstOrNull { titleMatchesDownload(it, urlLine) }
                ?: titleCandidates.firstOrNull()
            if (titleLine != null) {
                return ParsedShare(title = titleLine, url = urlLine)
            }
        }
        if (trimmed.isDownloadUrl()) return ParsedShare(null, trimmed.normalizeDownloadUrl())
        if (urlLine != null) return ParsedShare(null, urlLine)
        return ParsedShare(null, extractInlineDownloadUrl(trimmed))
    }

    private fun shareWithExplicitTitle(title: String, url: String): ParsedShare =
        ParsedShare(title = title, url = url, explicitTitle = title.isNotBlank())

    suspend fun resolve(url: String, fallback: String, context: Context? = null): String =
        resolveForEnqueue(url, "", fallback, context)

    suspend fun resolveForEnqueue(
        url: String,
        userFileName: String,
        fallback: String,
        context: Context? = null,
        trustUserFileName: Boolean = false,
    ): String {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return fallback
        if (DownloadUrlClassifier.isMagnetUrl(trimmed)) {
            val sanitizedUser = sanitizeTitle(userFileName)
            if (sanitizedUser.isNotBlank()) {
                return toOutputFileName(sanitizedUser, isHls = false, fallback)
            }
            val magnetName = DownloadUrlClassifier.magnetDisplayName(trimmed)?.let(::sanitizeTitle).orEmpty()
            return toOutputFileName(magnetName.ifBlank { fallback }, isHls = false, fallback)
        }
        val isHls = DownloadUrlClassifier.isHlsDownloadUrl(trimmed)
        val sanitizedUser = sanitizeTitle(userFileName)
        if (sanitizedUser.isNotBlank() && (trustUserFileName || titleMatchesDownload(sanitizedUser, trimmed))) {
            return toOutputFileName(sanitizedUser, isHls, fallback)
        }
        extractMatchingTitleFromUrl(trimmed)?.let { return toOutputFileName(it, isHls, fallback) }
        if (context != null) {
            downloadCdnIdentityKey(trimmed)?.let { cdnKey ->
                DownloadTitleCache.get(context, cdnKey)?.let { cached ->
                    return toOutputFileName(cached, isHls, fallback)
                }
            }
        }
        var fongMiMedia: FongMiMedia? = null
        var historyEntries: List<FongMiHistoryEntry>? = null
        if (DownloadUrlClassifier.isLocalProxyUrl(trimmed)) {
            localBaseUrl(trimmed)?.let { base ->
                historyEntries = withContext(Dispatchers.IO) {
                    fetchFongMiHistory(base)
                }
                historyEntries?.let { entries ->
                    resolveTitleFromHistory(entries, trimmed)?.let { composed ->
                        return toOutputFileName(composed, isHls, fallback)
                    }
                }
                fongMiMedia = withContext(Dispatchers.IO) {
                    fetchFongMiMedia(base)
                }
                fongMiMedia?.let { media ->
                    if (mediaMatchesDownload(media, trimmed)) {
                        val composed = composeFongMiTitle(media, trimmed)
                        if (composed.isNotBlank()) {
                            return toOutputFileName(composed, isHls, fallback)
                        }
                    }
                }
                historyEntries?.let { entries ->
                    resolveShowNameFromHistory(entries, trimmed)?.let { composed ->
                        return toOutputFileName(composed, isHls, fallback)
                    }
                }
            }
        }
        resolveTitleFromSeriesCache(context, trimmed, historyEntries, fongMiMedia)?.let { composed ->
            return toOutputFileName(composed, isHls, fallback)
        }
        fongMiMedia?.let { media ->
            composeFongMiTitleWithShowFallback(media, trimmed, historyEntries)?.let { composed ->
                return toOutputFileName(composed, isHls, fallback)
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

    internal fun seriesStemPrefix(folderStem: String): String? {
        val prefix = folderStem.substringBefore('_').trim()
        return prefix.takeIf { it.isNotBlank() }
    }

    internal fun extractShowNameFromTitle(title: String): String? {
        val polished = polishDisplayTitle(title)
        if (polished.isBlank()) return null
        EPISODE_NUMBER_PATTERN.find(polished)?.let { match ->
            val before = polished.substring(0, match.range.first).trimEnd('-').trim()
            if (before.isNotBlank() && !isEpisodeLabel(before)) return before
        }
        DATE_WITH_SUFFIX_PATTERN.find(polished)?.let { match ->
            val before = polished.substring(0, match.range.first).trimEnd('-').trim()
            if (before.isNotBlank()) return before
        }
        DATE_IN_TITLE_PATTERN.find(polished)?.let { match ->
            val before = polished.substring(0, match.range.first).trimEnd('-').trim()
            if (before.isNotBlank() && !isEpisodeLabel(before)) return before
        }
        return polished.takeIf { !isEpisodeLabel(it) }
    }

    internal fun inferEpisodeLabelFromDownload(downloadUrl: String): String? {
        val identity = downloadEpisodeIdentity(downloadUrl) ?: return null
        episodeNumberFromFolderStem(identity.folderStem)?.let { return "第${it}集" }
        return identity.date.takeIf { it.isNotBlank() }
    }

    internal fun extractEpisodeLabelFromText(text: String): String? {
        val normalized = normalizeFongMiText(text)
        if (normalized.isBlank()) return null
        EPISODE_NUMBER_PATTERN.find(normalized)?.let { match ->
            return "第${match.groupValues[1]}集"
        }
        DATE_WITH_SUFFIX_PATTERN.find(normalized)?.let { match ->
            return "${match.groupValues[1]}${match.groupValues[2]}"
        }
        if (isEpisodeLabel(normalized) && !DATE_IN_TITLE_PATTERN.matches(normalized)) {
            return normalized
        }
        return null
    }

    internal fun resolveEpisodeLabelFromHistory(
        entries: List<FongMiHistoryEntry>,
        downloadUrl: String,
    ): String? {
        val ranked = entries
            .map { entry -> entry to historyMatchScore(entry, downloadUrl) }
            .filter { (_, score) -> score >= SCORE_CDN_KEY }
            .sortedWith(
                compareByDescending<Pair<FongMiHistoryEntry, Int>> { it.second }
                    .thenByDescending { if (it.first.episodeUrl.isNotBlank()) 1 else 0 },
            )
        for ((entry, _) in ranked) {
            extractEpisodeLabelFromText(entry.vodRemarks)?.let { return it }
            extractEpisodeLabelFromText(entry.vodName)?.let { return it }
        }
        return null
    }

    internal fun resolveBestEpisodeLabel(
        downloadUrl: String,
        historyEntries: List<FongMiHistoryEntry>? = null,
        media: FongMiMedia? = null,
    ): String? {
        historyEntries?.let { resolveEpisodeLabelFromHistory(it, downloadUrl) }?.let { return it }
        if (media != null && mediaMatchesDownload(media, downloadUrl)) {
            extractEpisodeLabelFromText(media.artist)?.let { return it }
            extractEpisodeLabelFromText(media.title)?.let { return it }
        }
        return inferEpisodeLabelFromDownload(downloadUrl)
    }

    internal fun composeFongMiTitleWithShowFallback(
        media: FongMiMedia,
        downloadUrl: String,
        historyEntries: List<FongMiHistoryEntry>? = null,
    ): String? {
        val showName = extractShowNameFromMedia(media) ?: return null
        val episodeLabel = resolveBestEpisodeLabel(downloadUrl, historyEntries, media) ?: return null
        return polishDisplayTitle("$showName-$episodeLabel")
    }

    internal fun resolveShowNameFromHistory(
        entries: List<FongMiHistoryEntry>,
        downloadUrl: String,
    ): String? {
        val identity = downloadEpisodeIdentity(downloadUrl) ?: return null
        val stemPrefix = seriesStemPrefix(identity.folderStem) ?: return null
        val vodName = entries
            .filter { historyEntrySharesSeries(it, stemPrefix) }
            .map { normalizeFongMiText(it.vodName) }
            .firstOrNull { it.isNotBlank() && !isEpisodeLabel(it) }
            ?: return null
        val episodeLabel = resolveBestEpisodeLabel(downloadUrl, entries) ?: return null
        return polishDisplayTitle("$vodName-$episodeLabel")
    }

    internal fun resolveTitleFromHistory(
        entries: List<FongMiHistoryEntry>,
        downloadUrl: String,
    ): String? {
        val best = entries
            .map { entry -> entry to historyMatchScore(entry, downloadUrl) }
            .filter { (_, score) -> score >= HISTORY_MATCH_THRESHOLD }
            .maxWithOrNull(
                compareBy<Pair<FongMiHistoryEntry, Int>> { it.second }
                    .thenBy { if (it.first.episodeUrl.isNotBlank()) 1 else 0 },
            )
            ?: return null
        val composed = composeHistoryTitle(best.first.vodName, best.first.vodRemarks, downloadUrl)
        return composed.ifBlank { null }
    }

    internal fun composeHistoryTitle(vodName: String, vodRemarks: String, downloadUrl: String): String =
        composeFongMiTitle(
            FongMiMedia(title = vodName, artist = vodRemarks),
            downloadUrl,
        )

    internal fun historyEntryMatchesDownload(entry: FongMiHistoryEntry, downloadUrl: String): Boolean =
        historyMatchScore(entry, downloadUrl) >= HISTORY_MATCH_THRESHOLD

    internal fun historyMatchScore(entry: FongMiHistoryEntry, downloadUrl: String): Int {
        var best = 0
        if (entry.episodeUrl.isNotBlank() && mediaMatchesDownloadUrl(entry.episodeUrl, downloadUrl)) {
            best = maxOf(best, SCORE_URL_EXACT)
        }
        val entryKey = downloadCdnIdentityKey(entry.episodeUrl)
        val downloadKey = downloadCdnIdentityKey(downloadUrl)
        if (entryKey != null && entryKey == downloadKey) {
            best = maxOf(best, SCORE_CDN_KEY)
        }
        val identity = downloadEpisodeIdentity(downloadUrl) ?: return best
        if (urlContainsFolderStem(entry.episodeUrl, identity.folderStem)) {
            best = maxOf(best, SCORE_CDN_KEY)
        }
        val labels = listOf(entry.vodRemarks, entry.vodName)
            .map(::normalizeFongMiText)
            .filter { it.isNotBlank() }
        val combined = listOf(entry.vodName, entry.vodRemarks).filter { it.isNotBlank() }.joinToString("-")
        for (label in labels + listOf(normalizeFongMiText(combined))) {
            if (labelMatchesEpisodeIdentity(label, identity, entry.episodeUrl)) {
                best = maxOf(best, SCORE_LABEL_PRECISE)
            }
        }
        return best
    }

    internal fun matchesDownloadTarget(sourceUrl: String, sourceLabel: String, downloadUrl: String): Boolean {
        if (sourceUrl.isNotBlank() && mediaMatchesDownloadUrl(sourceUrl, downloadUrl)) {
            return true
        }
        val identity = downloadEpisodeIdentity(downloadUrl) ?: return false
        return labelMatchesEpisodeIdentity(normalizeFongMiText(sourceLabel), identity, sourceUrl)
    }

    internal fun downloadEpisodeIdentity(downloadUrl: String): DownloadEpisodeIdentity? {
        val cdnKey = downloadCdnIdentityKey(downloadUrl) ?: return null
        val date = cdnKey.substringBefore('/')
        val folderStem = cdnKey.substringAfter('/')
        if (date.isBlank() || folderStem.isBlank()) return null
        return DownloadEpisodeIdentity(
            downloadUrl = downloadUrl,
            cdnKey = cdnKey,
            date = date,
            folderStem = folderStem,
        )
    }

    internal fun labelMatchesEpisodeIdentity(
        label: String,
        identity: DownloadEpisodeIdentity,
        sourceUrl: String = "",
    ): Boolean {
        val normalized = normalizeFongMiText(label)
        if (normalized.isBlank() || !isEpisodeLabel(normalized)) return false
        if (identity.folderStem.isNotBlank()) {
            if (normalized.contains(identity.folderStem)) return true
            if (sourceUrl.isNotBlank() && sourceUrl.contains(identity.folderStem, ignoreCase = true)) return true
        }
        val episodeNumber = episodeNumberFromLabel(normalized)
        if (episodeNumber != null && folderStemMatchesEpisodeNumber(identity.folderStem, episodeNumber)) {
            return true
        }
        val dateSuffix = dateSuffixFromLabel(normalized) ?: return false
        if (dateSuffix.first != identity.date) return false
        val suffix = dateSuffix.second
        if (suffix.isEmpty()) return false
        if (sourceUrl.isNotBlank() && mediaMatchesDownloadUrl(sourceUrl, identity.downloadUrl)) {
            return true
        }
        if (identity.folderStem.contains(suffix)) return true
        if (normalized.endsWith(suffix) && episodeNumber != null &&
            folderStemMatchesEpisodeNumber(identity.folderStem, episodeNumber)
        ) {
            return true
        }
        return false
    }

    private fun dateSuffixFromLabel(label: String): Pair<String, String>? {
        val match = DATE_WITH_SUFFIX_PATTERN.find(label) ?: return null
        return match.groupValues[1] to match.groupValues[2]
    }

    private fun episodeNumberFromLabel(label: String): Int? =
        EPISODE_NUMBER_PATTERN.find(label)?.groupValues?.get(1)?.toIntOrNull()

    private fun episodeNumberFromFolderStem(folderStem: String): Int? =
        Regex("""(?:^|[_\-])ep(\d+)(?:[_\-]|$)""", RegexOption.IGNORE_CASE)
            .find(folderStem)
            ?.groupValues
            ?.get(1)
            ?.toIntOrNull()

    private fun extractShowNameFromMedia(media: FongMiMedia): String? {
        var title = normalizeFongMiText(media.title)
        var artist = normalizeFongMiText(media.artist)
        if (isPlaybackStatusLabel(title)) title = ""
        if (isPlaybackStatusLabel(artist)) artist = ""
        if (title.isNotBlank() && !isEpisodeLabel(title)) return title
        extractShowNameFromTitle(artist)?.let { return it }
        if (artist.contains('-')) {
            val prefix = artist.substringBefore('-').trim()
            if (prefix.isNotBlank() && !isEpisodeLabel(prefix) && !isPlaybackStatusLabel(prefix)) {
                return prefix
            }
        }
        return null
    }

    private fun historyEntrySharesSeries(entry: FongMiHistoryEntry, stemPrefix: String): Boolean {
        val entryKey = downloadCdnIdentityKey(entry.episodeUrl)
        if (entryKey != null && seriesStemPrefix(entryKey.substringAfter('/')) == stemPrefix) {
            return true
        }
        return urlContainsFolderStem(entry.episodeUrl, stemPrefix)
    }

    private fun urlContainsFolderStem(url: String, folderStem: String): Boolean {
        if (url.isBlank() || folderStem.isBlank()) return false
        if (url.contains(folderStem, ignoreCase = true)) return true
        return runCatching {
            DownloadUrlResolver.resolveDownloadUrl(url).contains(folderStem, ignoreCase = true)
        }.getOrDefault(false)
    }

    private suspend fun resolveTitleFromSeriesCache(
        context: Context?,
        downloadUrl: String,
        historyEntries: List<FongMiHistoryEntry>? = null,
        media: FongMiMedia? = null,
    ): String? {
        if (context == null) return null
        val identity = downloadEpisodeIdentity(downloadUrl) ?: return null
        val stemPrefix = seriesStemPrefix(identity.folderStem) ?: return null
        val showName = DownloadTitleCache.getSeries(context, stemPrefix) ?: return null
        val episodeLabel = resolveBestEpisodeLabel(downloadUrl, historyEntries, media) ?: return null
        return polishDisplayTitle("$showName-$episodeLabel")
    }

    private fun folderStemMatchesEpisodeNumber(folderStem: String, episodeNumber: Int): Boolean {
        val patterns = listOf(
            Regex("""(?:^|[_\-])ep$episodeNumber(?:[_\-]|$)""", RegexOption.IGNORE_CASE),
            Regex("""(?:^|[_\-])$episodeNumber(?:[_\-]|$)"""),
        )
        return patterns.any { it.containsMatchIn(folderStem) }
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
        }
    }

    private val GENERIC_M3U8_NAMES = setOf("index", "playlist", "master", "stream", "media", "chunklist")

    internal fun normalizeFongMiText(text: String): String =
        text.trim()
            .replace(PLAYING_STATUS_INLINE_PATTERN, "")
            .replace(Regex("""^[·\s\-]+"""), "")
            .replace(Regex("""[·\s\-]+$"""), "")
            .trim()

    internal fun polishDisplayTitle(title: String): String {
        var result = normalizeFongMiText(title)
        result = result.replace(PLAYING_STATUS_INLINE_PATTERN, "")
        result = result.replace(Regex("""\-{2,}"""), "-").trim('-')
        val trailingDate = TRAILING_DATE_PATTERN.find(result)
        if (trailingDate != null) {
            val prefix = result.removeSuffix(trailingDate.value)
            val hasEpisodeMarker = DATE_IN_TITLE_PATTERN.containsMatchIn(prefix) ||
                EPISODE_NUMBER_PATTERN.containsMatchIn(prefix) ||
                prefix.endsWith("上") || prefix.endsWith("下")
            if (hasEpisodeMarker) {
                result = prefix.trimEnd('-')
            }
        }
        return result.trim('-').trim()
    }

    internal fun isPlaybackStatusLabel(text: String): Boolean {
        val normalized = normalizeFongMiText(text)
        return normalized.isEmpty() || normalized.equals("正在播放", ignoreCase = true)
    }

    internal fun titleMatchesDownload(candidateTitle: String, downloadUrl: String): Boolean {
        val normalized = polishDisplayTitle(candidateTitle)
        if (normalized.isBlank()) return false
        val identity = downloadEpisodeIdentity(downloadUrl) ?: return true
        val episodeNumber = episodeNumberFromLabel(normalized)
        if (episodeNumber != null) {
            return folderStemMatchesEpisodeNumber(identity.folderStem, episodeNumber)
        }
        if (labelMatchesEpisodeIdentity(normalized, identity)) return true
        val showNameOnly = !isEpisodeLabel(normalized) &&
            DATE_IN_TITLE_PATTERN.findAll(normalized).none() &&
            EPISODE_NUMBER_PATTERN.find(normalized) == null
        return showNameOnly
    }

    internal fun composeFongMiTitle(media: FongMiMedia, downloadUrl: String): String {
        var title = normalizeFongMiText(media.title)
        var artist = normalizeFongMiText(media.artist)
        if (isPlaybackStatusLabel(title)) title = ""
        if (isPlaybackStatusLabel(artist)) artist = ""

        if (title.isNotBlank() && artist.isNotBlank() && !isEpisodeLabel(title) && isEpisodeLabel(artist)) {
            return polishDisplayTitle("$title-$artist")
        }
        if (title.isNotBlank() && isEpisodeLabel(title) && !isEpisodeLabel(artist)) {
            return polishDisplayTitle(title)
        }
        extractEpisodeLabelFromText(artist)?.let { ep ->
            val show = title.takeIf { it.isNotBlank() && !isEpisodeLabel(it) }
                ?: extractShowNameFromTitle(artist)
            if (!show.isNullOrBlank()) return polishDisplayTitle("$show-$ep")
        }
        extractEpisodeLabelFromText(title)?.let { ep ->
            val show = artist.takeIf { it.isNotBlank() && !isEpisodeLabel(it) }
                ?: extractShowNameFromTitle(title)
            if (!show.isNullOrBlank()) return polishDisplayTitle("$show-$ep")
        }

        val identity = downloadEpisodeIdentity(downloadUrl)
        fun labelMatchesDownload(label: String): Boolean =
            identity != null && labelMatchesEpisodeIdentity(label, identity, media.url)

        val episodeLabel = when {
            labelMatchesDownload(artist) -> artist
            labelMatchesDownload(title) -> title
            else -> null
        }
        val showName = when {
            title.isNotBlank() && title != episodeLabel && !isEpisodeLabel(title) -> title
            artist.isNotBlank() && artist != episodeLabel && !isEpisodeLabel(artist) -> artist
            else -> ""
        }

        val combined = when {
            showName.isNotBlank() && episodeLabel != null && !showName.contains(episodeLabel) -> "$showName-$episodeLabel"
            title.isEmpty() -> artist
            artist.isEmpty() -> title
            title.contains(artist) -> title
            artist.contains(title) -> artist
            episodeLabel != null -> episodeLabel
            else -> title
        }
        return polishDisplayTitle(combined)
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

    internal fun mediaMatchesDownload(media: FongMiMedia, downloadUrl: String): Boolean {
        val title = normalizeFongMiText(media.title)
        val artist = normalizeFongMiText(media.artist)
        if (matchesDownloadTarget(media.url, artist, downloadUrl)) return true
        if (matchesDownloadTarget(media.url, title, downloadUrl)) return true
        return false
    }

    internal fun mediaMatchesDownloadUrl(mediaUrl: String, downloadUrl: String): Boolean {
        if (mediaUrl.isBlank()) return false
        val mediaResolved = DownloadUrlResolver.resolveDownloadUrl(mediaUrl)
        val downloadResolved = DownloadUrlResolver.resolveDownloadUrl(downloadUrl)
        if (mediaResolved.equals(downloadResolved, ignoreCase = true)) return true
        val mediaNested = DownloadUrlClassifier.extractNestedM3u8Url(mediaUrl)
        val downloadNested = DownloadUrlClassifier.extractNestedM3u8Url(downloadUrl)
        if (mediaNested != null && downloadNested != null && mediaNested.equals(downloadNested, ignoreCase = true)) {
            return true
        }
        val mediaKey = cdnIdentityKey(mediaResolved) ?: mediaNested?.let(::cdnIdentityKey)
        val downloadKey = downloadCdnIdentityKey(downloadUrl)
        if (mediaKey != null && mediaKey == downloadKey) return true
        val folderStem = downloadKey?.substringAfter('/').orEmpty()
        return folderStem.isNotBlank() && (
            mediaUrl.contains(folderStem, ignoreCase = true) ||
                mediaResolved.contains(folderStem, ignoreCase = true) ||
                mediaNested?.contains(folderStem, ignoreCase = true) == true
            )
    }

    internal fun downloadCdnIdentityKey(downloadUrl: String): String? {
        val downloadResolved = DownloadUrlResolver.resolveDownloadUrl(downloadUrl)
        val downloadNested = DownloadUrlClassifier.extractNestedM3u8Url(downloadUrl)
        return cdnIdentityKey(downloadResolved) ?: downloadNested?.let(::cdnIdentityKey)
    }

    fun cacheableTitle(fileName: String): String {
        val dot = fileName.lastIndexOf('.')
        return if (dot > 0) fileName.substring(0, dot) else fileName
    }

    private fun cdnIdentityKey(url: String): String? {
        val path = url.substringBefore('?').substringBefore('#')
        val segments = path.split('/').filter { it.isNotBlank() }
        if (segments.size < 2) return null
        val fileName = segments.last().substringBeforeLast('.')
        val parent = segments[segments.size - 2]
        val dateSegment = segments.firstOrNull { Regex("""^\d{8}$""").matches(it) } ?: return null
        val folderStem = when {
            fileName.lowercase() in GENERIC_M3U8_NAMES -> parent
            fileName.isNotBlank() -> fileName
            else -> parent
        }
        return "$dateSegment/$folderStem"
    }

    fun sanitizeTitle(name: String): String =
        name.trim().replace(Regex("[\\\\/:*?\"<>|]"), "_").takeIf { it.isNotBlank() }.orEmpty()

    fun toOutputFileName(title: String, isHls: Boolean, fallback: String): String {
        val sanitized = sanitizeTitle(polishDisplayTitle(title)).ifBlank { return fallback }
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
            if (body.isBlank() || body == "{}") return null
            val json = JSONObject(body)
            val title = json.optString("title").trim()
            val artist = json.optString("artist").trim()
            val streamUrl = streamUrlFromJson(json)
            if (title.isEmpty() && artist.isEmpty()) return null
            FongMiMedia(title = title, artist = artist, url = streamUrl)
        } finally {
            connection.disconnect()
        }
    }.getOrNull()

    private fun fetchFongMiHistory(baseUrl: String): List<FongMiHistoryEntry>? {
        val deviceBody = httpGet("$baseUrl/device") ?: return null
        return runCatching {
            repeat(HISTORY_SYNC_MAX_ATTEMPTS) { attempt ->
                val payload = runHistorySyncAttempt(baseUrl, deviceBody)
                if (!payload.isNullOrBlank()) {
                    return@runCatching parseHistoryEntries(payload)
                }
                if (attempt < HISTORY_SYNC_MAX_ATTEMPTS - 1) {
                    Thread.sleep(HISTORY_SYNC_RETRY_DELAY_MS)
                }
            }
            emptyList()
        }.getOrNull()
    }

    private fun runHistorySyncAttempt(baseUrl: String, deviceBody: String): String? {
        val targetsPayload = AtomicReference<String?>(null)
        val readyLatch = CountDownLatch(1)
        val doneLatch = CountDownLatch(1)
        val server = ServerSocket(0)
        try {
            val listenPort = server.localPort
            val syncDevice = JSONObject(deviceBody).apply {
                put("ip", "http://127.0.0.1:$listenPort")
            }
            thread(name = "fongmi-history-sync", isDaemon = true) {
                try {
                    readyLatch.countDown()
                    server.accept().use { socket ->
                        socket.soTimeout = HISTORY_SYNC_TIMEOUT_MS
                        val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                        val requestLine = reader.readLine().orEmpty()
                        if (!requestLine.startsWith("POST", ignoreCase = true)) return@thread
                        val headers = mutableMapOf<String, String>()
                        while (true) {
                            val line = reader.readLine() ?: break
                            if (line.isEmpty()) break
                            val colon = line.indexOf(':')
                            if (colon > 0) {
                                headers[line.substring(0, colon).trim().lowercase()] =
                                    line.substring(colon + 1).trim()
                            }
                        }
                        val contentLength = headers["content-length"]?.toIntOrNull() ?: 0
                        val body = if (contentLength > 0) {
                            val buffer = CharArray(contentLength)
                            var read = 0
                            while (read < contentLength) {
                                val count = reader.read(buffer, read, contentLength - read)
                                if (count < 0) break
                                read += count
                            }
                            String(buffer, 0, read.coerceAtMost(contentLength))
                        } else {
                            reader.readText()
                        }
                        targetsPayload.set(parseTargetsPayload(body))
                        OutputStreamWriter(socket.getOutputStream()).use { writer ->
                            writer.write("HTTP/1.1 200 OK\r\nContent-Length: 2\r\n\r\nOK")
                            writer.flush()
                        }
                    }
                } catch (_: IOException) {
                } finally {
                    doneLatch.countDown()
                }
            }
            readyLatch.await(HISTORY_SYNC_READY_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            val posted = postFongMiHistorySync(baseUrl, syncDevice.toString())
            if (!posted) {
                runCatching { server.close() }
                doneLatch.await(HISTORY_SYNC_FAIL_FAST_MS, TimeUnit.MILLISECONDS)
                return targetsPayload.get()
            }
            doneLatch.await(HISTORY_SYNC_TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS)
        } finally {
            runCatching { server.close() }
        }
        return targetsPayload.get()
    }

    private fun postFongMiHistorySync(baseUrl: String, deviceJson: String): Boolean {
        val configJson = """{"type":0}"""
        val connection = (
            URL(
                "$baseUrl/action?do=sync&type=history&mode=2&force=false" +
                    "&config=${encodeQuery(configJson)}" +
                    "&device=${encodeQuery(deviceJson)}",
            ).openConnection() as HttpURLConnection
            ).apply {
            connectTimeout = HISTORY_SYNC_TIMEOUT_MS
            readTimeout = HISTORY_SYNC_TIMEOUT_MS
            requestMethod = "POST"
            doOutput = true
        }
        return try {
            connection.outputStream.use { it.write(ByteArray(0)) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            stream?.bufferedReader()?.use { it.readText() }
            code in 200..299
        } catch (_: Exception) {
            false
        } finally {
            connection.disconnect()
        }
    }

    private fun httpGet(url: String): String? {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = HISTORY_SYNC_TIMEOUT_MS
            readTimeout = HISTORY_SYNC_TIMEOUT_MS
            requestMethod = "GET"
        }
        return try {
            if (connection.responseCode !in 200..299) return null
            connection.inputStream.bufferedReader().readText()
        } finally {
            connection.disconnect()
        }
    }

    private fun parseHistoryEntries(targetsJson: String?): List<FongMiHistoryEntry> {
        if (targetsJson.isNullOrBlank()) return emptyList()
        val array = JSONArray(targetsJson)
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val vodName = item.optString("vodName").trim()
                val vodRemarks = item.optString("vodRemarks").trim()
                val episodeUrl = sequenceOf("episodeUrl", "playUrl", "url", "vodUrl")
                    .map { item.optString(it).trim() }
                    .firstOrNull { it.isNotBlank() }
                    .orEmpty()
                if (vodName.isEmpty() && vodRemarks.isEmpty() && episodeUrl.isEmpty()) continue
                add(
                    FongMiHistoryEntry(
                        vodName = vodName,
                        vodRemarks = vodRemarks,
                        episodeUrl = episodeUrl,
                    ),
                )
            }
        }
    }

    private fun parseTargetsPayload(body: String): String? {
        if (body.isBlank()) return null
        extractFormValue(body, "targets")?.let { return it }
        val trimmed = body.trim()
        if (trimmed.startsWith("[")) return trimmed
        if (trimmed.startsWith("{")) {
            runCatching {
                JSONObject(trimmed).optString("targets").trim().takeIf { it.isNotBlank() }
            }.getOrNull()?.let { return it }
        }
        return null
    }

    private fun extractFormValue(body: String, key: String): String? {
        if (body.isBlank()) return null
        return body.split('&').firstNotNullOfOrNull { part ->
            val partKey = part.substringBefore('=')
            if (!partKey.equals(key, ignoreCase = true)) return@firstNotNullOfOrNull null
            decodeOnce(part.substringAfter('='))
        }
    }

    private fun encodeQuery(value: String): String =
        java.net.URLEncoder.encode(value, Charsets.UTF_8.name())

    internal fun extractTitleFromUrl(url: String): String? {
        titleFromQuery(url)?.let { return it }
        var current = extractUrlParam(url)
        while (current != null) {
            titleFromQuery(current)?.let { return it }
            current = extractUrlParam(current)
        }
        return null
    }

    private fun extractMatchingTitleFromUrl(url: String): String? {
        val raw = extractTitleFromUrl(url) ?: return null
        return raw.takeIf { titleMatchesDownload(it, url) }
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
        startsWith("http://", ignoreCase = true) || startsWith("https://", ignoreCase = true)

    private fun String.isDownloadUrl(): Boolean =
        isHttpUrl() || DownloadUrlClassifier.isMagnetUrl(this) || contains("magnet:?", ignoreCase = true)

    private fun String.normalizeDownloadUrl(): String =
        if (DownloadUrlClassifier.isMagnetUrl(this) || contains("magnet:?", ignoreCase = true)) {
            DownloadUrlClassifier.extractFirstMagnet(this) ?: this
        } else {
            normalizeHttpScheme()
        }

    private fun String.normalizeHttpScheme(): String {
        val scheme = when {
            startsWith("https://", ignoreCase = true) -> "https://"
            startsWith("http://", ignoreCase = true) -> "http://"
            else -> return this
        }
        return scheme + substring(scheme.length)
    }

    private fun stripInvisibleChars(text: String): String = buildString(text.length) {
        text.forEach { ch ->
            if (ch != '\uFEFF' && ch != '\u200B' && ch != '\u200C' && ch != '\u200D' && ch != '\u2060') {
                append(ch)
            }
        }
    }

    private fun extractInlineDownloadUrl(text: String): String? =
        DownloadUrlClassifier.extractFirstMagnet(text)
            ?: INLINE_HTTP_URL_PATTERN.find(text)?.value
                ?.trimEnd(',', '.', ';', ')', ']', '}', '"', '\'')
                ?.normalizeHttpScheme()

    private val INLINE_HTTP_URL_PATTERN = Regex("""https?://\S+""", RegexOption.IGNORE_CASE)

    private val TITLE_QUERY_KEYS = setOf("name", "title", "filename", "n", "vod_name")

    private val DATE_IN_TITLE_PATTERN = Regex("""\d{8}""")

    private val DATE_IN_TITLE_SHORT_PATTERN = Regex("""(?<!\d)\d{6}(?!\d)""")

    private val DATE_WITH_SUFFIX_PATTERN = Regex("""(\d{8})([上下])""")

    private val EPISODE_NUMBER_PATTERN = Regex("""第\s*(\d+)\s*集""")

    private val PLAYING_STATUS_INLINE_PATTERN = Regex("""[·\s]*正在播放[：:\s·]*""")

    private val TRAILING_DATE_PATTERN = Regex("""-(\d{8})$""")

    private const val HISTORY_SYNC_TIMEOUT_MS = 10_000
    private const val HISTORY_SYNC_READY_TIMEOUT_MS = 2_000L
    private const val HISTORY_SYNC_FAIL_FAST_MS = 500L
    private const val HISTORY_SYNC_RETRY_DELAY_MS = 200L
    private const val HISTORY_SYNC_MAX_ATTEMPTS = 2

    private val STREAM_URL_JSON_KEYS = listOf("url", "playUrl", "mediaUrl", "src")

    private fun streamUrlFromJson(json: JSONObject): String {
        for (key in STREAM_URL_JSON_KEYS) {
            val value = json.optString(key).trim()
            if (value.isNotBlank()) return value
        }
        return ""
    }
}
