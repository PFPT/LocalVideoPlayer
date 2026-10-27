package com.pfpt.localvideoplayer.util

import com.pfpt.localvideoplayer.data.FolderVideoScanner
import com.pfpt.localvideoplayer.model.SeriesGroupKind
import com.pfpt.localvideoplayer.model.VideoItem
import com.pfpt.localvideoplayer.model.VideoLibraryEntry
import com.pfpt.localvideoplayer.model.VideoSeries
import com.pfpt.localvideoplayer.model.APP_DOWNLOAD_BUCKET
import com.pfpt.localvideoplayer.model.APP_DOWNLOAD_FOLDER_KEY
import com.pfpt.localvideoplayer.model.isAppDownloadLocation
import com.pfpt.localvideoplayer.player.PlaylistState
import java.util.Locale

object SeriesGrouper {

    internal const val MIN_GROUP_KEY_LENGTH = 3
    internal const val HIGH_SIMILARITY = 0.88
    internal const val MEDIUM_SIMILARITY = 0.72
    internal const val SHORT_CLIP_MAX_MS = 60_000L
    internal const val MIN_GROUP_SIZE = 2
    internal const val MIN_SHORT_MONTH_GROUP = 3
    internal const val MAX_SIMILAR_NAME_CLUSTER_SIZE = 150

    private val GENERIC_FOLDER_NAMES = setOf(
        "camera",
        "dcim",
        "download",
        "downloads",
        "movies",
        "video",
        "videos",
        "相册",
    )

    fun groupVideos(videos: List<VideoItem>): List<VideoLibraryEntry> {
        val parsed = dedupeLibraryVideos(videos).map { video ->
            val parsedTitle = TitleParser.parse(video.title)
            video.copy(
                season = parsedTitle.season,
                episode = parsedTitle.episode,
            ) to parsedTitle
        }

        val assigned = BooleanArray(parsed.size)
        val entries = mutableListOf<VideoLibraryEntry>()

        groupByFolder(parsed, assigned, entries)
        groupBySeriesName(parsed, assigned, entries)
        groupBySimilarName(parsed, assigned, entries)
        groupByDirectoryLoose(parsed, assigned, entries)
        groupByBucket(parsed, assigned, entries)
        groupShortClipsByMonth(parsed, assigned, entries)

        parsed.forEachIndexed { index, (video, _) ->
            if (!assigned[index]) {
                entries.add(VideoLibraryEntry.Single(video))
            }
        }

        ensureCompleteDownloadFolder(parsed.map { it.first }, entries)
        mergeEpisodeSeriesByName(entries)
        return entries
    }

    fun playlistBySeriesName(target: VideoItem, candidates: List<VideoItem>): PlaylistState? {
        val targetParsed = TitleParser.parse(target.title)
        val targetKey = TitleParser.normalizeForGrouping(targetParsed.seriesName)
            .takeIf { it.length >= MIN_GROUP_KEY_LENGTH }
            ?: return null

        val siblings = candidates.mapNotNull { video ->
            val parsed = TitleParser.parse(video.title)
            val key = TitleParser.normalizeForGrouping(parsed.seriesName)
            if (key != targetKey) return@mapNotNull null
            video.copy(
                season = parsed.season ?: video.season,
                episode = parsed.episode ?: video.episode,
            )
        }.distinctBy { it.uri.toString() }

        if (siblings.size < MIN_GROUP_SIZE) return null

        val sorted = sortEpisodes(siblings)
        val index = sorted.indexOfFirst { it.uri.toString() == target.uri.toString() }
            .coerceAtLeast(0)
        return PlaylistState(items = sorted, currentIndex = index)
    }

    private fun mergeEpisodeSeriesByName(entries: MutableList<VideoLibraryEntry>) {
        val episodeEntries = entries.mapIndexedNotNull { index, entry ->
            if (entry is VideoLibraryEntry.Series &&
                entry.series.groupKind == SeriesGroupKind.EPISODE
            ) {
                index to entry
            } else {
                null
            }
        }
        if (episodeEntries.size < 2) return

        val groups = linkedMapOf<String, MutableList<IndexedValue<VideoLibraryEntry.Series>>>()
        episodeEntries.forEach { (index, entry) ->
            val key = episodeMergeKey(entry) ?: return@forEach
            groups.getOrPut(key) { mutableListOf() }.add(IndexedValue(index, entry))
        }

        val indicesToRemove = mutableSetOf<Int>()
        val mergedEntries = mutableListOf<VideoLibraryEntry.Series>()

        groups.values.forEach { group ->
            if (group.size < 2) return@forEach
            val allEpisodes = group
                .flatMap { it.value.series.episodes }
                .distinctBy { it.uri.toString() }
            if (allEpisodes.size <= group.maxOf { it.value.series.episodes.size }) {
                return@forEach
            }
            val title = group
                .map { it.value.series.title }
                .groupingBy { it }
                .eachCount()
                .maxByOrNull { it.value }
                ?.key
                ?: group.first().value.series.title
            val seriesKey = "name:${TitleParser.normalizeForGrouping(title)}"
            val sortedEpisodes = sortEpisodes(
                allEpisodes.map { it.copy(seriesKey = seriesKey) },
            )
            group.forEach { indicesToRemove.add(it.index) }
            mergedEntries.add(
                VideoLibraryEntry.Series(
                    VideoSeries(
                        id = seriesKey.hashCode().toUInt().toString(16),
                        title = title,
                        episodes = sortedEpisodes,
                        groupKind = SeriesGroupKind.EPISODE,
                    ),
                ),
            )
        }

        if (indicesToRemove.isEmpty()) return
        val remaining = entries.filterIndexed { index, _ -> index !in indicesToRemove }.toMutableList()
        remaining.addAll(0, mergedEntries)
        entries.clear()
        entries.addAll(remaining)
    }

    private fun episodeMergeKey(entry: VideoLibraryEntry.Series): String? {
        val fromEpisodes = entry.series.episodes
            .map { TitleParser.normalizeForGrouping(TitleParser.parse(it.title).seriesName) }
            .filter { it.length >= MIN_GROUP_KEY_LENGTH }
            .groupingBy { it }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key
        if (fromEpisodes != null) return fromEpisodes
        val fromTitle = TitleParser.normalizeForGrouping(entry.series.title)
        return fromTitle.takeIf { it.length >= MIN_GROUP_KEY_LENGTH }
    }

    private fun ensureCompleteDownloadFolder(
        videos: List<VideoItem>,
        entries: MutableList<VideoLibraryEntry>,
    ) {
        val downloadVideos = videos.filter(::isAppDownloadLocation)
        if (downloadVideos.isEmpty()) return

        entries.removeAll { entry ->
            entry is VideoLibraryEntry.Series &&
                entry.series.groupKind == SeriesGroupKind.BUCKET &&
                entry.series.title.equals(APP_DOWNLOAD_BUCKET, ignoreCase = true)
        }

        val sortedEpisodes = sortEpisodes(
            downloadVideos.map { it.copy(seriesKey = APP_DOWNLOAD_FOLDER_KEY) },
        )
        entries.add(
            VideoLibraryEntry.Series(
                VideoSeries(
                    id = APP_DOWNLOAD_FOLDER_KEY.hashCode().toUInt().toString(16),
                    title = APP_DOWNLOAD_BUCKET,
                    episodes = sortedEpisodes,
                    groupKind = SeriesGroupKind.BUCKET,
                ),
            ),
        )
    }


    internal fun isGenericFolderName(name: String): Boolean {
        return name.trim().lowercase(Locale.ROOT) in GENERIC_FOLDER_NAMES
    }

    internal fun folderLeafName(folderKey: String): String {
        return if (folderKey.startsWith("bucket:")) {
            folderKey.removePrefix("bucket:")
        } else {
            folderKey.substringAfterLast('/').trim('/')
        }
    }



    internal fun parentFolderKey(video: VideoItem): String? {
        val relativePath = video.relativePath ?: return video.bucketName?.let { "bucket:$it" }
        val trimmed = relativePath.trim().trimEnd('/')
        if (trimmed.isEmpty()) return video.bucketName?.let { "bucket:$it" }
        val lastSlash = trimmed.lastIndexOf('/')
        val lastSegment = if (lastSlash >= 0) trimmed.substring(lastSlash + 1) else trimmed
        return if (looksLikeVideoFileName(lastSegment)) {
            if (lastSlash <= 0) {
                video.bucketName?.let { "bucket:$it" }
            } else {
                trimmed.substring(0, lastSlash)
            }
        } else {
            trimmed
        }
    }

    private fun looksLikeVideoFileName(name: String): Boolean {
        val ext = name.substringAfterLast('.', missingDelimiterValue = "")
        if (ext.isEmpty() || ext.length == name.length) return false
        return FolderVideoScanner.isVideoFile(name, mime = "")
    }

    fun libraryFolderKey(video: VideoItem): String? {
        val key = parentFolderKey(video) ?: return null
        if (isGenericFolderName(folderLeafName(key))) return null
        return key
    }

    fun sortEpisodes(videos: List<VideoItem>): List<VideoItem> {
        return videos.sortedWith(
            compareBy<VideoItem> { it.season ?: Int.MAX_VALUE }
                .thenBy { it.episode ?: Int.MAX_VALUE }
                .thenBy { it.title.lowercase() },
        )
    }

    fun quickPlaylistFor(target: VideoItem, candidates: List<VideoItem>): PlaylistState {
        val targetParsed = TitleParser.parse(target.title)
        val targetWithMeta = target.copy(
            season = targetParsed.season,
            episode = targetParsed.episode,
        )
        val targetFolder = parentFolderKey(targetWithMeta)
        val targetSeriesKey = TitleParser.normalizeForGrouping(targetParsed.seriesName)
            .takeIf { it.length >= MIN_GROUP_KEY_LENGTH }

        val folderSiblings = if (targetFolder != null && !isGenericFolderName(folderLeafName(targetFolder))) {
            candidates.mapNotNull { video ->
                val parsed = TitleParser.parse(video.title)
                val withMeta = video.copy(season = parsed.season, episode = parsed.episode)
                if (parentFolderKey(withMeta) == targetFolder) withMeta else null
            }
        } else {
            emptyList()
        }

        val nameSiblings = if (targetSeriesKey != null) {
            candidates.mapNotNull { video ->
                val parsed = TitleParser.parse(video.title)
                val withMeta = video.copy(season = parsed.season, episode = parsed.episode)
                val seriesKey = TitleParser.normalizeForGrouping(parsed.seriesName)
                if (seriesKey == targetSeriesKey) withMeta else null
            }
        } else {
            emptyList()
        }

        val siblings = when {
            folderSiblings.size >= MIN_GROUP_SIZE -> folderSiblings
            nameSiblings.size >= MIN_GROUP_SIZE &&
                nameSiblings.any { it.episode != null || it.season != null } -> nameSiblings
            else -> listOf(targetWithMeta)
        }

        val sorted = if (siblings.size >= MIN_GROUP_SIZE) {
            sortEpisodes(siblings.distinctBy { it.uri.toString() })
        } else {
            listOf(targetWithMeta)
        }
        val index = sorted.indexOfFirst { it.uri.toString() == target.uri.toString() }
            .coerceAtLeast(0)
        return PlaylistState(
            items = sorted,
            currentIndex = index,
        )
    }
}
