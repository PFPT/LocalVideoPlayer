package com.pfpt.localvideoplayer.util

import com.pfpt.localvideoplayer.model.SeriesGroupKind
import com.pfpt.localvideoplayer.model.VideoItem
import com.pfpt.localvideoplayer.model.VideoLibraryEntry
import com.pfpt.localvideoplayer.model.VideoSeries
import java.util.Calendar
import java.util.Locale

internal fun groupByFolder(
    parsed: List<Pair<VideoItem, ParsedTitle>>,
    assigned: BooleanArray,
    entries: MutableList<VideoLibraryEntry>,
) {
    val folderGroups = mutableMapOf<String, MutableList<Int>>()

    parsed.forEachIndexed { index, (video, _) ->
        if (assigned[index]) return@forEachIndexed
        val folderKey = SeriesGrouper.parentFolderKey(video) ?: return@forEachIndexed
        folderGroups.getOrPut(folderKey) { mutableListOf() }.add(index)
    }

    folderGroups.values
        .filter { it.size >= 2 }
        .forEach { indices ->
            if (indices.size > SeriesGrouper.MAX_SIMILAR_NAME_CLUSTER_SIZE) {
                return@forEach
            }
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
                        folderKey = SeriesGrouper.parentFolderKey(groupVideos.first()),
                    )
                    val seriesKey = "folder:${SeriesGrouper.parentFolderKey(groupVideos.first())}:$title"
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

internal fun groupBySeriesName(
    parsed: List<Pair<VideoItem, ParsedTitle>>,
    assigned: BooleanArray,
    entries: MutableList<VideoLibraryEntry>,
) {
    val nameGroups = mutableMapOf<String, MutableList<Int>>()

    parsed.forEachIndexed { index, (_, parsedTitle) ->
        if (assigned[index]) return@forEachIndexed
        val nameKey = TitleParser.normalizeForGrouping(parsedTitle.seriesName)
        if (nameKey.length < SeriesGrouper.MIN_GROUP_KEY_LENGTH) return@forEachIndexed
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

internal fun groupByDirectoryLoose(
    parsed: List<Pair<VideoItem, ParsedTitle>>,
    assigned: BooleanArray,
    entries: MutableList<VideoLibraryEntry>,
) {
    val folderGroups = mutableMapOf<String, MutableList<Int>>()

    parsed.forEachIndexed { index, (video, _) ->
        if (assigned[index]) return@forEachIndexed
        val folderKey = SeriesGrouper.parentFolderKey(video) ?: return@forEachIndexed
        if (folderKey.startsWith("bucket:")) return@forEachIndexed
        if (SeriesGrouper.isGenericFolderName(SeriesGrouper.folderLeafName(folderKey))) return@forEachIndexed
        folderGroups.getOrPut(folderKey) { mutableListOf() }.add(index)
    }

    folderGroups.values
        .filter { it.size >= SeriesGrouper.MIN_GROUP_SIZE }
        .forEach { indices ->
            val groupVideos = indices.map { parsed[it].first }
            val folderKey = SeriesGrouper.parentFolderKey(groupVideos.first()) ?: return@forEach
            val title = SeriesGrouper.folderLeafName(folderKey).ifBlank {
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

internal fun groupByBucket(
    parsed: List<Pair<VideoItem, ParsedTitle>>,
    assigned: BooleanArray,
    entries: MutableList<VideoLibraryEntry>,
) {
    val bucketGroups = mutableMapOf<String, MutableList<Int>>()

    parsed.forEachIndexed { index, (video, _) ->
        if (assigned[index]) return@forEachIndexed
        val bucket = video.bucketName?.trim()?.takeIf { it.isNotBlank() } ?: return@forEachIndexed
        if (SeriesGrouper.isGenericFolderName(bucket)) return@forEachIndexed
        bucketGroups.getOrPut(bucket) { mutableListOf() }.add(index)
    }

    bucketGroups.values
        .filter { it.size >= SeriesGrouper.MIN_GROUP_SIZE }
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

internal fun groupShortClipsByMonth(
    parsed: List<Pair<VideoItem, ParsedTitle>>,
    assigned: BooleanArray,
    entries: MutableList<VideoLibraryEntry>,
) {
    val monthGroups = mutableMapOf<String, MutableList<Int>>()

    parsed.forEachIndexed { index, (video, _) ->
        if (assigned[index]) return@forEachIndexed
        if (video.durationMs > SeriesGrouper.SHORT_CLIP_MAX_MS) return@forEachIndexed
        monthGroups.getOrPut(monthKey(video.dateAdded)) { mutableListOf() }.add(index)
    }

    monthGroups.entries
        .filter { it.value.size >= SeriesGrouper.MIN_SHORT_MONTH_GROUP }
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

internal fun groupBySimilarName(
    parsed: List<Pair<VideoItem, ParsedTitle>>,
    assigned: BooleanArray,
    entries: MutableList<VideoLibraryEntry>,
) {
    val indices = parsed.indices.filter { !assigned[it] }
    if (indices.size < 2) return
    if (indices.size > SeriesGrouper.MAX_SIMILAR_NAME_CLUSTER_SIZE) return

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

internal fun addSeries(
    entries: MutableList<VideoLibraryEntry>,
    indices: List<Int>,
    assigned: BooleanArray,
    videos: List<VideoItem>,
    title: String,
    seriesKey: String,
    groupKind: SeriesGroupKind,
) {
    indices.forEach { assigned[it] = true }
    val sortedEpisodes = SeriesGrouper.sortEpisodes(
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

internal fun monthKey(dateAdded: Long): String {
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

internal fun formatShortClipsMonthTitle(monthKey: String): String {
    val parts = monthKey.split("-")
    if (parts.size != 2) return "短视频"
    val year = parts[0].toIntOrNull() ?: return "短视频"
    val month = parts[1].toIntOrNull() ?: return "短视频"
    return "短视频 · ${year}年${month}月"
}

internal fun resolveSeriesTitle(
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

internal fun canGroupAsSeries(parsedTitles: List<ParsedTitle>): Boolean {
    if (parsedTitles.size < 2) return false
    if (parsedTitles.count { it.episode != null } >= 2) return true

    val names = parsedTitles
        .map { it.seriesName.trim() }
        .filter { it.isNotBlank() }
    if (names.size < 2) return false

    for (i in names.indices) {
        for (j in i + 1 until names.size) {
            if (nameSimilarity(names[i], names[j]) >= SeriesGrouper.MEDIUM_SIMILARITY) {
                return true
            }
        }
    }
    return false
}

internal fun shouldGroup(left: ParsedTitle, right: ParsedTitle): Boolean {
    val normA = TitleParser.normalizeForGrouping(left.seriesName)
    val normB = TitleParser.normalizeForGrouping(right.seriesName)
    if (hasDistinguishingSuffix(normA, normB)) return false

    val score = nameSimilarity(left.seriesName, right.seriesName)
    if (score >= SeriesGrouper.HIGH_SIMILARITY) return true
    if (score >= SeriesGrouper.MEDIUM_SIMILARITY) {
        return left.episode != null || right.episode != null || score >= 0.8
    }
    if (score >= 0.62) {
        return left.episode != null && right.episode != null
    }
    return false
}

internal fun hasDistinguishingSuffix(a: String, b: String): Boolean {
    val tokensA = a.split(Regex("\\s+")).filter { it.isNotBlank() }
    val tokensB = b.split(Regex("\\s+")).filter { it.isNotBlank() }
    if (tokensA.size != tokensB.size || tokensA.size < 2) return false
    if (tokensA.dropLast(1) != tokensB.dropLast(1)) return false
    val lastA = tokensA.last()
    val lastB = tokensB.last()
    return lastA != lastB && lastA.length <= 4 && lastB.length <= 4
}

