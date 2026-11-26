package com.pfpt.localvideoplayer.data

import android.net.Uri
import com.pfpt.localvideoplayer.model.VideoItem
import kotlinx.coroutines.CancellationException

internal data class FolderScanResult(
    val videos: List<VideoItem>,
    val failed: Boolean,
)

internal object LibraryFolderScan {
    suspend fun collect(
        sources: List<FolderSource>,
        canScanExternalDirs: Boolean,
        loadTree: suspend (Uri) -> List<VideoItem>,
        loadDirectories: suspend () -> List<VideoItem>,
    ): FolderScanResult {
        var failed = false
        val fromTrees = sources.flatMap { source ->
            val uri = runCatching { Uri.parse(source.treeUri) }.getOrNull()
            if (uri == null) {
                failed = true
                return@flatMap emptyList()
            }
            try {
                loadTree(uri)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                failed = true
                emptyList()
            }
        }
        val fromPaths = if (canScanExternalDirs) {
            try {
                loadDirectories()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                failed = true
                emptyList()
            }
        } else {
            emptyList()
        }
        val videos = if (fromPaths.isEmpty()) fromTrees else fromTrees + fromPaths
        return FolderScanResult(videos = videos, failed = failed)
    }
}
