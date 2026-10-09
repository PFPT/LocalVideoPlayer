package com.zhaoshuo.localvideoplayer.data

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.DocumentsContract
import com.zhaoshuo.localvideoplayer.model.VideoItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File

class FolderVideoScanner(
    private val context: Context,
    private val durationCache: FolderDurationCache = FolderDurationCache(
        File(context.applicationContext.filesDir, "folder_duration_cache.json"),
    ),
) {
    private var cacheLoaded = false

    suspend fun loadVideos(treeUri: Uri): List<VideoItem> = coroutineScope {
        ensureCacheLoaded()
        val pending = mutableListOf<PendingVideo>()
        val treeId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull()
            ?: return@coroutineScope emptyList()
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, treeId)
        collectDocuments(treeUri, childrenUri, parentPath = "", pending)
        val semaphore = Semaphore(DURATION_PARALLELISM)
        val videos = pending.map { item ->
            async(Dispatchers.IO) {
                semaphore.withPermit {
                    item.toVideo(queryDurationMs(item.documentUri, item.modifiedMs))
                }
            }
        }.awaitAll()
        durationCache.save()
        videos
    }

    private fun ensureCacheLoaded() {
        if (cacheLoaded) return
        durationCache.load()
        cacheLoaded = true
    }

    private fun collectDocuments(
        treeUri: Uri,
        childrenUri: Uri,
        parentPath: String,
        out: MutableList<PendingVideo>,
    ) {
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        )
        context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
            val modifiedColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
            while (cursor.moveToNext()) {
                val documentId = cursor.getString(idColumn) ?: continue
                val name = cursor.getString(nameColumn) ?: continue
                val mime = cursor.getString(mimeColumn).orEmpty()
                val modified = cursor.getLong(modifiedColumn)
                val documentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)
                if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                    val nested = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, documentId)
                    val childPath = if (parentPath.isEmpty()) name else "$parentPath/$name"
                    collectDocuments(treeUri, nested, childPath, out)
                    continue
                }
                if (!isVideoFile(name, mime)) continue
                out.add(
                    PendingVideo(
                        documentUri = documentUri,
                        name = name,
                        modifiedMs = modified,
                        relativePath = folderDocumentRelativePath(parentPath, name),
                        bucketName = parentPath.substringAfterLast('/').ifBlank { parentPath.ifBlank { null } },
                    ),
                )
            }
        }
    }

    private suspend fun queryDurationMs(uri: Uri, modifiedMs: Long): Long {
        val uriKey = uri.toString()
        durationCache.get(uriKey, modifiedMs)?.let { return it }
        val duration = withContext(Dispatchers.IO) {
            try {
                MediaMetadataRetriever().use { retriever ->
                    retriever.setDataSource(context, uri)
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                0L
            }
        }
        durationCache.put(uriKey, modifiedMs, duration)
        return duration
    }

    private data class PendingVideo(
        val documentUri: Uri,
        val name: String,
        val modifiedMs: Long,
        val relativePath: String,
        val bucketName: String?,
    ) {
        fun toVideo(durationMs: Long): VideoItem {
            return VideoItem(
                id = documentUri.hashCode().toLong(),
                uri = documentUri,
                title = name,
                durationMs = durationMs,
                dateAdded = if (modifiedMs > 0) modifiedMs / 1000 else 0L,
                dateModified = if (modifiedMs > 0) modifiedMs / 1000 else 0L,
                thumbnailUri = null,
                relativePath = relativePath,
                bucketName = bucketName,
            )
        }
    }

    companion object {
        private const val DURATION_PARALLELISM = 4
        private val VIDEO_EXTENSIONS = setOf(
            "mp4", "mkv", "avi", "webm", "mov", "m4v", "ts", "flv", "wmv", "mpeg", "mpg", "m3u8",
        )

        fun folderDocumentRelativePath(parentPath: String, name: String): String {
            return if (parentPath.isEmpty()) name else "$parentPath/$name"
        }

        fun isVideoFile(name: String, mime: String): Boolean {
            if (mime.startsWith("video/")) return true
            if (mime == "application/vnd.apple.mpegurl" || mime == "application/x-mpegURL") return true
            val ext = name.substringAfterLast('.', "").lowercase()
            return ext in VIDEO_EXTENSIONS
        }
    }
}
