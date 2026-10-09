package com.zhaoshuo.localvideoplayer.data

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import com.zhaoshuo.localvideoplayer.model.VideoItem
import com.zhaoshuo.localvideoplayer.model.VideoLibraryEntry
import com.zhaoshuo.localvideoplayer.model.withoutUris
import com.zhaoshuo.localvideoplayer.util.SeriesGrouper
import com.zhaoshuo.localvideoplayer.util.isM3u8Title
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LibraryViewModel(
    context: Context,
    private val repository: VideoRepository = VideoRepository(context),
    private val libraryCacheStore: LibraryCacheStore = LibraryCacheStore(context),
    private val hiddenVideosStore: HiddenVideosStore = HiddenVideosStore(context),
    private val pinnedItemsStore: PinnedItemsStore = PinnedItemsStore(context),
    private val playCountStore: PlayCountStore = PlayCountStore(context),
    private val folderSourceStore: FolderSourceStore = FolderSourceStore(context),
    private val folderVideoScanner: FolderVideoScanner = FolderVideoScanner(context),
    private val durationResolver: VideoDurationResolver = VideoDurationResolver(context),
    private val playbackStore: PlaybackStore = PlaybackStore(context),
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _libraryEntries = MutableStateFlow<List<VideoLibraryEntry>>(emptyList())
    val libraryEntries: StateFlow<List<VideoLibraryEntry>> = _libraryEntries.asStateFlow()

    private val _isLibraryLoading = MutableStateFlow(false)
    val isLibraryLoading: StateFlow<Boolean> = _isLibraryLoading.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _refreshError = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val refreshError: SharedFlow<String> = _refreshError.asSharedFlow()

    private var observerRegistered = false
    private var folderWatchStarted = false
    private var debounceJob: Job? = null
    private var lastKnownMaxId = 0L
    private var lastKnownMaxDateAdded = 0L
    private var lastInputSignature: String? = null
    private var lastFolderSignature: String? = null
    private var cachedFolderSourceKey: String? = null
    private var cachedFolderVideos: List<VideoItem> = emptyList()

    private val contentObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            onChange(selfChange, null)
        }

        override fun onChange(selfChange: Boolean, uri: Uri?) {
            debounceJob?.cancel()
            debounceJob = scope.launch {
                delay(MEDIA_DEBOUNCE_MS)
                refresh(showLoading = false, fromPull = false, incrementalPreferred = true)
            }
        }
    }

    fun startObserving() {
        if (observerRegistered) return
        try {
            appContext.contentResolver.registerContentObserver(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                true,
                contentObserver,
            )
            observerRegistered = true
        } catch (e: Exception) {
            Log.w(TAG, "Failed to register MediaStore observer", e)
        }
        if (!folderWatchStarted) {
            folderWatchStarted = true
            scope.launch {
                folderSourceStore.sources.collect {
                    refresh(showLoading = false, fromPull = false, incrementalPreferred = true)
                }
            }
        }
    }

    fun stopObserving() {
        if (!observerRegistered) return
        try {
            appContext.contentResolver.unregisterContentObserver(contentObserver)
        } catch (_: Exception) {
        }
        observerRegistered = false
        debounceJob?.cancel()
    }

    fun clear() {
        stopObserving()
        scope.coroutineContext[Job]?.cancel()
    }

    suspend fun loadFromCache() {
        val hiddenUris = withContext(Dispatchers.IO) { hiddenVideosStore.getHiddenUris() }
        val cachedEntries = withContext(Dispatchers.IO) { libraryCacheStore.loadLibraryEntries() }
        if (cachedEntries.isNotEmpty()) {
            _libraryEntries.value = cachedEntries.filterNotHidden(hiddenUris)
            rememberSignatures(flattenVideos(_libraryEntries.value))
            return
        }
        val videos = withContext(Dispatchers.IO) { libraryCacheStore.loadVideos() }
            .filter { it.uri.toString() !in hiddenUris }
        if (videos.isEmpty()) return
        _libraryEntries.value = videos.map { VideoLibraryEntry.Single(it) }
        applyVideos(videos, emptySet())
        rememberSignatures(flattenVideos(_libraryEntries.value))
    }

    fun refresh(showLoading: Boolean = false, fromPull: Boolean = false, incrementalPreferred: Boolean = false) {
        scope.launch {
            performRefresh(showLoading, fromPull, incrementalPreferred)
            backfillMissingDurations()
        }
    }

    fun removeVideos(uris: Collection<Uri>) {
        if (uris.isEmpty()) return
        val uriSet = uris.map { it.toString() }.toSet()
        val removedSeriesIds = _libraryEntries.value.mapNotNull { entry ->
            when (entry) {
                is VideoLibraryEntry.Series -> {
                    val remaining = entry.series.episodes.filter { it.uri.toString() !in uriSet }
                    if (remaining.isEmpty()) entry.series.id else null
                }
                is VideoLibraryEntry.Single -> null
            }
        }
        _libraryEntries.value = _libraryEntries.value.withoutUris(uris)
        cachedFolderVideos = cachedFolderVideos.filter { it.uri.toString() !in uriSet }
        scope.launch(Dispatchers.IO) {
            libraryCacheStore.removeVideos(uris)
            libraryCacheStore.saveLibraryEntries(_libraryEntries.value)
            pinnedItemsStore.removeKeysForUris(uris)
            pinnedItemsStore.removeSeriesKeys(removedSeriesIds)
            playCountStore.removeForUris(uris)
            playCountStore.removeKeys(removedSeriesIds.map { "series:$it" })
        }
    }

    fun playlistStateFor(video: VideoItem) =
        repository.playlistStateFor(video, _libraryEntries.value)

    private suspend fun performRefresh(
        showLoading: Boolean,
        fromPull: Boolean,
        incrementalPreferred: Boolean,
    ) {
        val previousSignature = if (fromPull) librarySignature(_libraryEntries.value) else emptyList()
        if (fromPull) _isRefreshing.value = true
        else if (showLoading) _isLibraryLoading.value = true
        try {
            val hiddenUris = withContext(Dispatchers.IO) { hiddenVideosStore.getHiddenUris() }
            val folderVideos = loadFolderSourceVideos(force = fromPull)
            val folderSignature = withContext(Dispatchers.Default) { videoSetSignature(folderVideos) }
            val useIncremental = incrementalPreferred && !fromPull && lastKnownMaxId > 0
            val scanned = withContext(Dispatchers.IO) {
                if (useIncremental) {
                    val delta = repository.loadVideosAddedAfter(lastKnownMaxDateAdded, lastKnownMaxId)
                    val mediaExisting = flattenVideos(_libraryEntries.value).filterNot(::isFolderSourceVideo)
                    val liveIds = repository.loadVideoIds()
                    resolveIncrementalMedia(mediaExisting, delta, liveIds)
                } else {
                    var list = repository.loadVideos()
                    if (list.isEmpty()) {
                        delay(400)
                        list = repository.loadVideos()
                    }
                    list
                }
            }
            if (scanned == null && folderSignature == lastFolderSignature) {
                if (fromPull && librarySignature(_libraryEntries.value) == previousSignature) {
                    _refreshError.emit(REFRESH_UP_TO_DATE)
                }
                return
            }
            val mediaVideos = scanned ?: flattenVideos(_libraryEntries.value).filterNot(::isFolderSourceVideo)
            val scannedWithFolders = mergeVideos(mediaVideos, folderVideos)
            if (scannedWithFolders.isEmpty() && _libraryEntries.value.isNotEmpty() && !fromPull) {
                return
            }
            val pendingSignature = withContext(Dispatchers.Default) { videoSetSignature(scannedWithFolders) }
            if (!fromPull && pendingSignature == lastInputSignature && _libraryEntries.value.isNotEmpty()) {
                rememberSignatures(flattenVideos(_libraryEntries.value))
                return
            }
            val existingEntries = _libraryEntries.value
            val hadEntries = existingEntries.isNotEmpty()
            if (!hadEntries) {
                _libraryEntries.value = scannedWithFolders
                    .filter { it.uri.toString() !in hiddenUris }
                    .map { VideoLibraryEntry.Single(it) }
                _isLibraryLoading.value = false
            }
            val m3u8Items = withContext(Dispatchers.IO) {
                repository.loadM3u8Playlists(scannedWithFolders)
            }
            val merged = if (m3u8Items.isEmpty()) scannedWithFolders else scannedWithFolders + m3u8Items
            val visible = merged.filter { it.uri.toString() !in hiddenUris }
            val changeCount = libraryChangeCount(flattenVideos(existingEntries), visible)
            if (shouldFullyRegroup(fromPull, !hadEntries, changeCount)) {
                applyVideos(merged, hiddenUris)
            } else {
                _libraryEntries.value = withContext(Dispatchers.Default) {
                    patchLibraryEntries(existingEntries, visible)
                }
            }
            saveCaches(merged)
            rememberSignatures(flattenVideos(_libraryEntries.value))

            if (fromPull && librarySignature(_libraryEntries.value) == previousSignature) {
                _refreshError.emit(REFRESH_UP_TO_DATE)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Library refresh failed", e)
            _refreshError.emit(e.message ?: "refresh_failed")
        } finally {
            _isRefreshing.value = false
            _isLibraryLoading.value = false
        }
    }

    private suspend fun applyVideos(videos: List<VideoItem>, hiddenUris: Set<String>): List<VideoLibraryEntry> {
        val entries = withContext(Dispatchers.Default) {
            SeriesGrouper.groupVideos(videos.filter { it.uri.toString() !in hiddenUris })
        }
        _libraryEntries.value = entries
        return entries
    }

    private suspend fun saveCaches(videos: List<VideoItem>) {
        if (videos.isEmpty()) return
        withContext(Dispatchers.IO) {
            runCatching {
                libraryCacheStore.saveVideos(videos)
                libraryCacheStore.saveLibraryEntries(_libraryEntries.value)
            }
        }
    }

    private suspend fun rememberSignatures(videos: List<VideoItem>) {
        val signatures = withContext(Dispatchers.Default) {
            val input = videos.filterNot { isM3u8Title(it.title) }
            Triple(
                input.filterNot(::isFolderSourceVideo),
                videoSetSignature(input.filter(::isFolderSourceVideo)),
                videoSetSignature(input),
            )
        }
        updateWatermarks(signatures.first)
        lastFolderSignature = signatures.second
        lastInputSignature = signatures.third
    }

    private fun updateWatermarks(videos: List<VideoItem>) {
        lastKnownMaxId = videos.maxOfOrNull { it.id } ?: 0L
        lastKnownMaxDateAdded = videos.maxOfOrNull { it.dateAdded } ?: 0L
    }

    private fun librarySignature(entries: List<VideoLibraryEntry>): List<String> {
        return entries.map { entry ->
            when (entry) {
                is VideoLibraryEntry.Series -> "series:${entry.series.id}:${entry.series.episodes.size}"
                is VideoLibraryEntry.Single -> "single:${entry.video.uri}"
            }
        }
    }

    private fun flattenVideos(entries: List<VideoLibraryEntry>): List<VideoItem> {
        return entries.flatMap { entry ->
            when (entry) {
                is VideoLibraryEntry.Single -> listOf(entry.video)
                is VideoLibraryEntry.Series -> entry.series.episodes
            }
        }
    }

    private fun List<VideoLibraryEntry>.filterNotHidden(hiddenUris: Set<String>): List<VideoLibraryEntry> {
        if (hiddenUris.isEmpty()) return this
        return withoutUris(hiddenUris.map(Uri::parse))
    }

    private suspend fun loadFolderSourceVideos(force: Boolean): List<VideoItem> {
        val sources = folderSourceStore.getSources()
        if (sources.isEmpty()) {
            cachedFolderSourceKey = ""
            cachedFolderVideos = emptyList()
            return emptyList()
        }
        val key = sources.joinToString("\n") { it.treeUri }
        if (!force && key == cachedFolderSourceKey) {
            return cachedFolderVideos
        }
        val videos = withContext(Dispatchers.IO) {
            sources.flatMap { source ->
                val uri = runCatching { Uri.parse(source.treeUri) }.getOrNull() ?: return@flatMap emptyList()
                try {
                    folderVideoScanner.loadVideos(uri)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    emptyList()
                }
            }
        }
        cachedFolderSourceKey = key
        cachedFolderVideos = videos
        return videos
    }

    private suspend fun backfillMissingDurations() {
        val missing = flattenVideos(_libraryEntries.value)
            .filter { it.durationMs <= 0L && !isM3u8Title(it.title) }
        if (missing.isEmpty()) return
        val fromPlayback = playbackStore.durationMsByUri()
        applyResolvedDurations(fromPlayback)
        val stillMissing = flattenVideos(_libraryEntries.value)
            .filter { it.durationMs <= 0L && !isM3u8Title(it.title) }
        if (stillMissing.isEmpty()) return
        val resolved = durationResolver.resolveAll(stillMissing)
        applyResolvedDurations(resolved)
    }

    private suspend fun applyResolvedDurations(resolved: Map<String, Long>) {
        if (resolved.isEmpty()) return
        val latest = _libraryEntries.value
        val latestVideos = flattenVideos(latest)
        val updated = withResolvedDurations(latestVideos, resolved)
        if (updated === latestVideos) return
        _libraryEntries.value = withContext(Dispatchers.Default) {
            patchLibraryEntries(latest, updated)
        }
        saveCaches(updated)
        rememberSignatures(flattenVideos(_libraryEntries.value))
    }

    private fun mergeVideos(primary: List<VideoItem>, extra: List<VideoItem>): List<VideoItem> {
        if (extra.isEmpty()) return primary
        val seen = primary.mapTo(mutableSetOf()) { it.uri.toString() }
        return primary + extra.filter { seen.add(it.uri.toString()) }
    }

    companion object {
        private const val TAG = "LibraryViewModel"
        private const val MEDIA_DEBOUNCE_MS = 1_200L
        const val REFRESH_UP_TO_DATE = "up_to_date"
    }
}
