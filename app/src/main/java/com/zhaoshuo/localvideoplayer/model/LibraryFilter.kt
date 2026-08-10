package com.zhaoshuo.localvideoplayer.model

import java.util.Calendar
import java.util.concurrent.TimeUnit

enum class DurationFilter {
    ALL,
    SHORT,
    MEDIUM,
    LONG,
    MIN_20,
    MIN_25,
}

enum class DateFilter {
    ALL,
    TODAY,
    WEEK,
    MONTH,
    YEAR,
}

enum class LibraryTypeTab {
    ALL,
    EPISODES,
    FOLDERS,
    SHORT_CLIPS,
    SINGLES,
    MARKED,
}

enum class LibraryViewLayout {
    SECTIONED,
    FLAT,
}

data class LibraryFilter(
    val duration: DurationFilter = DurationFilter.ALL,
    val date: DateFilter = DateFilter.ALL,
)

private const val SHORT_MAX_MS = 60_000L
private const val MEDIUM_MAX_MS = 600_000L
private const val MIN_20_MS = 1_200_000L
private const val MIN_25_MS = 1_500_000L

fun filterLibrary(
    entries: List<VideoLibraryEntry>,
    filter: LibraryFilter,
): List<VideoLibraryEntry> {
    return entries.filter { entry ->
        matchesDuration(entry, filter.duration) && matchesDate(entry, filter.date)
    }
}

fun applyTypeTab(
    entries: List<VideoLibraryEntry>,
    tab: LibraryTypeTab,
    pinnedKeys: Set<String>,
): List<VideoLibraryEntry> {
    if (tab == LibraryTypeTab.ALL) {
        return entries.filterNot(::isAppDownloadFolder)
    }
    return entries.filter { entry -> matchesTypeTab(entry, tab, pinnedKeys) }
}

fun pinMarkedFirst(
    entries: List<VideoLibraryEntry>,
    pinnedKeys: Set<String>,
): List<VideoLibraryEntry> {
    if (pinnedKeys.isEmpty()) return entries
    val pinned = mutableListOf<VideoLibraryEntry>()
    val rest = mutableListOf<VideoLibraryEntry>()
    for (entry in entries) {
        if (entryKey(entry) in pinnedKeys) {
            pinned.add(entry)
        } else {
            rest.add(entry)
        }
    }
    return pinned + rest
}

data class PinnedLibrarySections(
    val pinned: List<VideoLibraryEntry>,
    val episodes: List<VideoLibraryEntry.Series>,
    val folders: List<VideoLibraryEntry.Series>,
    val shortClips: List<VideoLibraryEntry.Series>,
    val singles: List<VideoLibraryEntry.Single>,
)

fun partitionWithPinned(
    entries: List<VideoLibraryEntry>,
    pinnedKeys: Set<String>,
): PinnedLibrarySections {
    val pinned = mutableListOf<VideoLibraryEntry>()
    val unpinned = mutableListOf<VideoLibraryEntry>()
    for (entry in entries) {
        if (entryKey(entry) in pinnedKeys) {
            pinned.add(entry)
        } else {
            unpinned.add(entry)
        }
    }
    val sections = partitionLibrary(unpinned)
    return PinnedLibrarySections(
        pinned = pinned,
        episodes = sections.episodes,
        folders = sections.folders,
        shortClips = sections.shortClips,
        singles = sections.singles,
    )
}

private fun matchesTypeTab(
    entry: VideoLibraryEntry,
    tab: LibraryTypeTab,
    pinnedKeys: Set<String>,
): Boolean {
    return when (tab) {
        LibraryTypeTab.ALL -> true
        LibraryTypeTab.MARKED -> entryKey(entry) in pinnedKeys
        LibraryTypeTab.SINGLES -> entry is VideoLibraryEntry.Single
        LibraryTypeTab.EPISODES -> entry is VideoLibraryEntry.Series &&
            entry.series.groupKind == SeriesGroupKind.EPISODE
        LibraryTypeTab.FOLDERS -> entry is VideoLibraryEntry.Series &&
            entry.series.groupKind in setOf(SeriesGroupKind.DIRECTORY, SeriesGroupKind.BUCKET)
        LibraryTypeTab.SHORT_CLIPS -> entry is VideoLibraryEntry.Series &&
            entry.series.groupKind == SeriesGroupKind.SHORT_CLIPS
    }
}

private fun matchesDuration(entry: VideoLibraryEntry, filter: DurationFilter): Boolean {
    if (filter == DurationFilter.ALL) return true
    return when (entry) {
        is VideoLibraryEntry.Single -> durationMatches(entry.video.durationMs, filter)
        is VideoLibraryEntry.Series -> entry.series.episodes.any {
            durationMatches(it.durationMs, filter)
        }
    }
}

private fun durationMatches(durationMs: Long, filter: DurationFilter): Boolean {
    return when (filter) {
        DurationFilter.ALL -> true
        DurationFilter.SHORT -> durationMs in 1..SHORT_MAX_MS
        DurationFilter.MEDIUM -> durationMs in (SHORT_MAX_MS + 1)..MEDIUM_MAX_MS
        DurationFilter.LONG -> durationMs > MEDIUM_MAX_MS
        DurationFilter.MIN_20 -> durationMs > MIN_20_MS
        DurationFilter.MIN_25 -> durationMs > MIN_25_MS
    }
}

private fun matchesDate(entry: VideoLibraryEntry, filter: DateFilter): Boolean {
    if (filter == DateFilter.ALL) return true
    val dateAdded = when (entry) {
        is VideoLibraryEntry.Single -> entry.video.dateAdded
        is VideoLibraryEntry.Series -> entry.series.episodes.maxOfOrNull { it.dateAdded } ?: 0L
    }
    return dateMatches(dateAdded, filter)
}

private fun dateMatches(dateAdded: Long, filter: DateFilter): Boolean {
    val millis = if (dateAdded < 1_000_000_000_000L) dateAdded * 1000 else dateAdded
    val now = Calendar.getInstance()
    val target = Calendar.getInstance().apply { timeInMillis = millis }
    return when (filter) {
        DateFilter.ALL -> true
        DateFilter.TODAY -> isSameDay(now, target)
        DateFilter.WEEK -> millis >= now.timeInMillis - TimeUnit.DAYS.toMillis(7)
        DateFilter.MONTH -> millis >= now.timeInMillis - TimeUnit.DAYS.toMillis(30)
        DateFilter.YEAR -> now.get(Calendar.YEAR) == target.get(Calendar.YEAR)
    }
}

private fun isSameDay(a: Calendar, b: Calendar): Boolean {
    return a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
        a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
}
