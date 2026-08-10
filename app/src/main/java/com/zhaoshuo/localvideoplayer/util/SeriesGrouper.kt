package com.zhaoshuo.localvideoplayer.util

import com.zhaoshuo.localvideoplayer.model.SeriesGroupKind
import com.zhaoshuo.localvideoplayer.model.VideoItem
import com.zhaoshuo.localvideoplayer.model.VideoLibraryEntry
import com.zhaoshuo.localvideoplayer.model.VideoSeries
import com.zhaoshuo.localvideoplayer.model.APP_DOWNLOAD_BUCKET
import com.zhaoshuo.localvideoplayer.model.APP_DOWNLOAD_FOLDER_KEY
import com.zhaoshuo.localvideoplayer.model.isAppDownloadLocation
import com.zhaoshuo.localvideoplayer.player.PlaylistState
import java.util.Calendar
import java.util.Locale

object SeriesGrouper {

    private const val MIN_GROUP_KEY_LENGTH = 3
    private const val HIGH_SIMILARITY = 0.88
    private const val MEDIUM_SIMILARITY = 0.72
    private const val SHORT_CLIP_MAX_MS = 60_000L
    private const val MIN_GROUP_SIZE = 2
    private const val MIN_SHORT_MONTH_GROUP = 3
    private const val MAX_SIMILAR_NAME_CLUSTER_SIZE = 150

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
        val parsed = videos.map { video ->
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

    private fun groupByFolder(
        parsed: List<Pair<VideoItem, ParsedTitle>>,
        assigned: BooleanArray,
        entries: MutableList<VideoLibraryEntry>,
    ) {
        val folderGroups = mutableMapOf<String, MutableList<Int>>()

        parsed.forEachIndexed { index, (video, _) ->
            if (assigned[index]) return@forEachIndexed
            val folderKey = parentFolderKey(video) ?: return@forEachIndexed
            folderGroups.getOrPut(folderKey) { mutableListOf() }.add(index)
        }

        folderGroups.values
            .filter { it.size >= 2 }
            .forEach { indices ->
                val clusters = clusterIndices(indices) { left, right ->
                    shouldGroup(parsed[left].second, parsed[right].second)
                }
                clusters
                    .filter { it.size >= 2 }
                    .forEach { cluster ->
                        val groupVideos = cluster.map { parsed[it].first }
                        val parsedTitles = cluster.map { parsed[it].second }
                        if (!canGroupAsSeries(parsedTitles)) return@forEach
                        val title = resolveSeriesTitle(
                            groupVideos,
                            parsedTitles,
                            folderKey = parentFolderKey(groupVideos.first()),
                        )
                        val seriesKey = "folder:${parentFolderKey(groupVideos.first())}:$title"
                        addSeries(
                            entries,
                            cluster,
                            assigned,
                            groupVideos,
                            title,
                            seriesKey,
                            SeriesGroupKind.EPISODE,
                        )
                    }
            }
    }

    private fun groupBySeriesName(
        parsed: List<Pair<VideoItem, ParsedTitle>>,
        assigned: BooleanArray,
        entries: MutableList<VideoLibraryEntry>,
    ) {
        val nameGroups = mutableMapOf<String, MutableList<Int>>()

        parsed.forEachIndexed { index, (_, parsedTitle) ->
            if (assigned[index]) return@forEachIndexed
            val nameKey = TitleParser.normalizeForGrouping(parsedTitle.seriesName)
            if (nameKey.length < MIN_GROUP_KEY_LENGTH) return@forEachIndexed
            nameGroups.getOrPut(nameKey) { mutableListOf() }.add(index)
        }

        nameGroups.values
            .filter { it.size >= 2 }
            .forEach { indices ->
                val groupVideos = indices.map { parsed[it].first }
                val parsedTitles = indices.map { parsed[it].second }
                val title = resolveSeriesTitle(groupVideos, parsedTitles, folderKey = null)
                val seriesKey = "name:${TitleParser.normalizeForGrouping(title)}"
                addSeries(
                    entries,
                    indices,
                    assigned,
                    groupVideos,
                    title,
                    seriesKey,
                    SeriesGroupKind.EPISODE,
                )
            }
    }

    private fun groupByDirectoryLoose(
        parsed: List<Pair<VideoItem, ParsedTitle>>,
        assigned: BooleanArray,
        entries: MutableList<VideoLibraryEntry>,
    ) {
        val folderGroups = mutableMapOf<String, MutableList<Int>>()

        parsed.forEachIndexed { index, (video, _) ->
            if (assigned[index]) return@forEachIndexed
            val folderKey = parentFolderKey(video) ?: return@forEachIndexed
            if (folderKey.startsWith("bucket:")) return@forEachIndexed
            if (isGenericFolderName(folderLeafName(folderKey))) return@forEachIndexed
            folderGroups.getOrPut(folderKey) { mutableListOf() }.add(index)
        }

        folderGroups.values
            .filter { it.size >= MIN_GROUP_SIZE }
            .forEach { indices ->
                val groupVideos = indices.map { parsed[it].first }
                val folderKey = parentFolderKey(groupVideos.first()) ?: return@forEach
                val title = folderLeafName(folderKey).ifBlank {
                    groupVideos.first().bucketName ?: folderKey
                }
                val seriesKey = "dir:$folderKey"
                addSeries(
                    entries,
                    indices,
                    assigned,
                    groupVideos,
                    title,
                    seriesKey,
                    SeriesGroupKind.DIRECTORY,
                )
            }
    }

    private fun groupByBucket(
        parsed: List<Pair<VideoItem, ParsedTitle>>,
        assigned: BooleanArray,
        entries: MutableList<VideoLibraryEntry>,
    ) {
        val bucketGroups = mutableMapOf<String, MutableList<Int>>()

        parsed.forEachIndexed { index, (video, _) ->
            if (assigned[index]) return@forEachIndexed
            val bucket = video.bucketName?.trim()?.takeIf { it.isNotBlank() } ?: return@forEachIndexed
            if (isGenericFolderName(bucket)) return@forEachIndexed
            bucketGroups.getOrPut(bucket) { mutableListOf() }.add(index)
        }

        bucketGroups.values
            .filter { it.size >= MIN_GROUP_SIZE }
            .forEach { indices ->
                val groupVideos = indices.map { parsed[it].first }
                val bucket = groupVideos.first().bucketName?.trim().orEmpty()
                val seriesKey = "bucket:$bucket"
                addSeries(
                    entries,
                    indices,
                    assigned,
                    groupVideos,
                    bucket,
                    seriesKey,
                    SeriesGroupKind.BUCKET,
                )
            }
    }

    private fun groupShortClipsByMonth(
        parsed: List<Pair<VideoItem, ParsedTitle>>,
        assigned: BooleanArray,
        entries: MutableList<VideoLibraryEntry>,
    ) {
        val monthGroups = mutableMapOf<String, MutableList<Int>>()

        parsed.forEachIndexed { index, (video, _) ->
            if (assigned[index]) return@forEachIndexed
            if (video.durationMs > SHORT_CLIP_MAX_MS) return@forEachIndexed
            monthGroups.getOrPut(monthKey(video.dateAdded)) { mutableListOf() }.add(index)
        }

        monthGroups.entries
            .filter { it.value.size >= MIN_SHORT_MONTH_GROUP }
            .forEach { (month, indices) ->
                val groupVideos = indices.map { parsed[it].first }
                val title = formatShortClipsMonthTitle(month)
                val seriesKey = "shorts:$month"
                addSeries(
                    entries,
                    indices,
                    assigned,
                    groupVideos,
                    title,
                    seriesKey,
                    SeriesGroupKind.SHORT_CLIPS,
                )
            }
    }

    private fun groupBySimilarName(
        parsed: List<Pair<VideoItem, ParsedTitle>>,
        assigned: BooleanArray,
        entries: MutableList<VideoLibraryEntry>,
    ) {
        val indices = parsed.indices.filter { !assigned[it] }
        if (indices.size < 2) return
        if (indices.size > MAX_SIMILAR_NAME_CLUSTER_SIZE) return

        val clusters = clusterIndices(indices) { left, right ->
            shouldGroup(parsed[left].second, parsed[right].second)
        }

        clusters
            .filter { it.size >= 2 }
            .forEach { cluster ->
                val groupVideos = cluster.map { parsed[it].first }
                val parsedTitles = cluster.map { parsed[it].second }
                val title = resolveSeriesTitle(groupVideos, parsedTitles, folderKey = null)
                val seriesKey = "fuzzy:${TitleParser.normalizeForGrouping(title)}"
                addSeries(
                    entries,
                    cluster,
                    assigned,
                    groupVideos,
                    title,
                    seriesKey,
                    SeriesGroupKind.EPISODE,
                )
            }
    }

    private fun addSeries(
        entries: MutableList<VideoLibraryEntry>,
        indices: List<Int>,
        assigned: BooleanArray,
        videos: List<VideoItem>,
        title: String,
        seriesKey: String,
        groupKind: SeriesGroupKind,
    ) {
        indices.forEach { assigned[it] = true }
        val sortedEpisodes = sortEpisodes(
            videos.map { it.copy(seriesKey = seriesKey) },
        )
        entries.add(
            VideoLibraryEntry.Series(
                VideoSeries(
                    id = seriesKey.hashCode().toUInt().toString(16),
                    title = title,
                    episodes = sortedEpisodes,
                    groupKind = groupKind,
                ),
            ),
        )
    }

    private fun isGenericFolderName(name: String): Boolean {
        return name.trim().lowercase(Locale.ROOT) in GENERIC_FOLDER_NAMES
    }

    private fun folderLeafName(folderKey: String): String {
        return if (folderKey.startsWith("bucket:")) {
            folderKey.removePrefix("bucket:")
        } else {
            folderKey.substringAfterLast('/').trim('/')
        }
    }

    private fun monthKey(dateAdded: Long): String {
        val millis = if (dateAdded < 1_000_000_000_000L) dateAdded * 1000 else dateAdded
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = millis
        return String.format(
            Locale.US,
            "%04d-%02d",
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH) + 1,
        )
    }

    private fun formatShortClipsMonthTitle(monthKey: String): String {
        val parts = monthKey.split("-")
        if (parts.size != 2) return "短视频"
        val year = parts[0].toIntOrNull() ?: return "短视频"
        val month = parts[1].toIntOrNull() ?: return "短视频"
        return "短视频 · ${year}年${month}月"
    }

    private fun resolveSeriesTitle(
        videos: List<VideoItem>,
        parsedTitles: List<ParsedTitle>,
        folderKey: String?,
    ): String {
        val commonName = parsedTitles
            .map { it.seriesName }
            .filter { it.isNotBlank() }
            .groupingBy { it }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key

        if (!commonName.isNullOrBlank()) return commonName

        val normalizedCounts = parsedTitles
            .map { TitleParser.normalizeForGrouping(it.seriesName) }
            .filter { it.isNotBlank() }
            .groupingBy { it }
            .eachCount()
        val topNormalized = normalizedCounts.maxByOrNull { it.value }?.key
        if (topNormalized != null) {
            parsedTitles
                .map { it.seriesName }
                .firstOrNull { TitleParser.normalizeForGrouping(it) == topNormalized }
                ?.let { return it }
        }

        folderKey?.substringAfterLast('/')?.trim('/')?.let { folderName ->
            if (folderName.isNotBlank() && folderName != ".") return folderName
        }

        videos.first().bucketName?.let { bucket ->
            if (bucket.isNotBlank()) return bucket
        }

        return videos.first().title.substringBeforeLast('.')
    }

    private fun canGroupAsSeries(parsedTitles: List<ParsedTitle>): Boolean {
        if (parsedTitles.size < 2) return false
        if (parsedTitles.count { it.episode != null } >= 2) return true

        val names = parsedTitles
            .map { it.seriesName.trim() }
            .filter { it.isNotBlank() }
        if (names.size < 2) return false

        for (i in names.indices) {
            for (j in i + 1 until names.size) {
                if (nameSimilarity(names[i], names[j]) >= MEDIUM_SIMILARITY) {
                    return true
                }
            }
        }
        return false
    }

    private fun shouldGroup(left: ParsedTitle, right: ParsedTitle): Boolean {
        val normA = TitleParser.normalizeForGrouping(left.seriesName)
        val normB = TitleParser.normalizeForGrouping(right.seriesName)
        if (hasDistinguishingSuffix(normA, normB)) return false

        val score = nameSimilarity(left.seriesName, right.seriesName)
        if (score >= HIGH_SIMILARITY) return true
        if (score >= MEDIUM_SIMILARITY) {
            return left.episode != null || right.episode != null || score >= 0.8
        }
        if (score >= 0.62) {
            return left.episode != null && right.episode != null
        }
        return false
    }

    private fun hasDistinguishingSuffix(a: String, b: String): Boolean {
        val tokensA = a.split(Regex("\\s+")).filter { it.isNotBlank() }
        val tokensB = b.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (tokensA.size != tokensB.size || tokensA.size < 2) return false
        if (tokensA.dropLast(1) != tokensB.dropLast(1)) return false
        val lastA = tokensA.last()
        val lastB = tokensB.last()
        return lastA != lastB && lastA.length <= 4 && lastB.length <= 4
    }

    private fun nameSimilarity(rawA: String, rawB: String): Double {
        val a = TitleParser.normalizeForGrouping(rawA)
        val b = TitleParser.normalizeForGrouping(rawB)
        if (a == b) return 1.0
        if (a.isBlank() || b.isBlank()) return 0.0
        if (a.length < MIN_GROUP_KEY_LENGTH || b.length < MIN_GROUP_KEY_LENGTH) return 0.0

        val shorter = if (a.length <= b.length) a else b
        val longer = if (a.length <= b.length) b else a

        if (longer.startsWith(shorter)) {
            return shorter.length.toDouble() / longer.length
        }

        val prefixLen = a.commonPrefixWith(b).length
        val prefixRatio = prefixLen.toDouble() / shorter.length
        val tokenScore = tokenJaccard(a, b)
        val editScore = levenshteinRatio(a, b)
        val charScore = if (hasCjk(a) || hasCjk(b)) charBigramJaccard(a, b) else 0.0

        return maxOf(editScore, tokenScore, prefixRatio, charScore)
    }

    private fun hasCjk(text: String): Boolean =
        text.any { it.code in 0x2E80..0x9FFF }

    private fun tokenJaccard(a: String, b: String): Double {
        val tokensA = a.split(Regex("\\s+")).filter { it.length >= 2 }.toSet()
        val tokensB = b.split(Regex("\\s+")).filter { it.length >= 2 }.toSet()
        if (tokensA.isEmpty() || tokensB.isEmpty()) return 0.0
        val intersection = tokensA.intersect(tokensB).size
        val union = tokensA.union(tokensB).size
        return intersection.toDouble() / union
    }

    private fun charBigramJaccard(a: String, b: String): Double {
        val compactA = a.replace(" ", "")
        val compactB = b.replace(" ", "")
        if (compactA == compactB) return 1.0
        if (compactA.length < 2 || compactB.length < 2) {
            return levenshteinRatio(compactA, compactB)
        }
        val bigramsA = (0 until compactA.length - 1).map { compactA.substring(it, it + 2) }.toSet()
        val bigramsB = (0 until compactB.length - 1).map { compactB.substring(it, it + 2) }.toSet()
        val intersection = bigramsA.intersect(bigramsB).size
        val union = bigramsA.union(bigramsB).size
        return intersection.toDouble() / union
    }

    private fun levenshteinRatio(a: String, b: String): Double {
        val maxLen = maxOf(a.length, b.length)
        if (maxLen == 0) return 1.0
        return 1.0 - levenshteinDistance(a, b).toDouble() / maxLen
    }

    private fun levenshteinDistance(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length

        val prev = IntArray(b.length + 1) { it }
        val curr = IntArray(b.length + 1)

        for (i in a.indices) {
            curr[0] = i + 1
            for (j in b.indices) {
                val cost = if (a[i] == b[j]) 0 else 1
                curr[j + 1] = minOf(
                    curr[j] + 1,
                    prev[j + 1] + 1,
                    prev[j] + cost,
                )
            }
            prev.indices.forEach { idx -> prev[idx] = curr[idx] }
        }
        return prev[b.length]
    }

    private fun clusterIndices(
        indices: List<Int>,
        shouldMerge: (Int, Int) -> Boolean,
    ): List<List<Int>> {
        if (indices.size < 2) return listOf(indices)

        val parent = IntArray(indices.size) { it }

        fun find(i: Int): Int {
            var root = i
            while (parent[root] != root) {
                parent[root] = parent[parent[root]]
                root = parent[root]
            }
            return root
        }

        fun union(i: Int, j: Int) {
            val rootI = find(i)
            val rootJ = find(j)
            if (rootI != rootJ) parent[rootJ] = rootI
        }

        for (i in indices.indices) {
            for (j in i + 1 until indices.size) {
                if (shouldMerge(indices[i], indices[j])) {
                    union(i, j)
                }
            }
        }

        return indices.indices
            .groupBy { find(it) }
            .values
            .map { positions -> positions.map { indices[it] } }
    }

    private fun parentFolderKey(video: VideoItem): String? {
        val relativePath = video.relativePath ?: return video.bucketName?.let { "bucket:$it" }
        val trimmed = relativePath.trimEnd('/')
        val lastSlash = trimmed.lastIndexOf('/')
        if (lastSlash <= 0) return video.bucketName?.let { "bucket:$it" }
        return trimmed.substring(0, lastSlash)
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
