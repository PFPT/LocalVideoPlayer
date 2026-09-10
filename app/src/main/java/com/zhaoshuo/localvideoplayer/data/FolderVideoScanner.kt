package com.zhaoshuo.localvideoplayer.data

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.DocumentsContract
import com.zhaoshuo.localvideoplayer.model.VideoItem

class FolderVideoScanner(private val context: Context) {

    fun loadVideos(treeUri: Uri): List<VideoItem> {
        val videos = mutableListOf<VideoItem>()
        val treeId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull() ?: return emptyList()
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, treeId)
        collectDocuments(treeUri, childrenUri, videos)
        return videos
    }

    private fun collectDocuments(treeUri: Uri, childrenUri: Uri, out: MutableList<VideoItem>) {
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
                    collectDocuments(treeUri, nested, out)
                    continue
                }
                if (!isVideoFile(name, mime)) continue
                val durationMs = queryDurationMs(documentUri)
                out.add(
                    VideoItem(
                        id = documentUri.hashCode().toLong(),
                        uri = documentUri,
                        title = name,
                        durationMs = durationMs,
                        dateAdded = if (modified > 0) modified / 1000 else 0L,
                        thumbnailUri = null,
                        relativePath = treeUri.toString(),
                        bucketName = treeUri.lastPathSegment,
                    ),
                )
            }
        }
    }

    private fun queryDurationMs(uri: Uri): Long {
        return runCatching {
            MediaMetadataRetriever().use { retriever ->
                retriever.setDataSource(context, uri)
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            }
        }.getOrDefault(0L)
    }

    companion object {
        private val VIDEO_EXTENSIONS = setOf(
            "mp4", "mkv", "avi", "webm", "mov", "m4v", "ts", "flv", "wmv", "mpeg", "mpg", "m3u8",
        )

        fun isVideoFile(name: String, mime: String): Boolean {
            if (mime.startsWith("video/")) return true
            if (mime == "application/vnd.apple.mpegurl" || mime == "application/x-mpegURL") return true
            val ext = name.substringAfterLast('.', "").lowercase()
            return ext in VIDEO_EXTENSIONS
        }
    }
}
