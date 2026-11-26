package com.pfpt.localvideoplayer.data

import com.pfpt.localvideoplayer.model.VideoItem
import com.pfpt.localvideoplayer.model.VideoLibraryEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class LibraryCacheWriter(
    private val store: LibraryCacheStore,
) {
    suspend fun save(videos: List<VideoItem>, entries: List<VideoLibraryEntry>) {
        if (videos.isEmpty()) return
        withContext(Dispatchers.IO) {
            runCatching {
                store.saveVideos(videos)
                store.saveLibraryEntries(entries)
            }
        }
    }
}
