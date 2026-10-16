package com.pfpt.localvideoplayer.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.pfpt.localvideoplayer.model.VideoItem
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.coroutineScope
import java.io.File
import kotlin.coroutines.coroutineContext

class FolderVideoScanner(
    private val context: Context,
    private val durationResolver: VideoDurationResolver = VideoDurationResolver(context),
) {
    suspend fun loadVideos(treeUri: Uri): List<VideoItem> = coroutineScope {
        val pending = mutableListOf<PendingVideo>()
        val treeId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull()
            ?: return@coroutineScope emptyList()
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, treeId)
        collectDocuments(treeUri, childrenUri, parentPath = "", pending)
        resolvePending(pending)
    }

    suspend fun loadVideosFromDirectories(roots: List<File>): List<VideoItem> = coroutineScope {
        if (roots.isEmpty()) return@coroutineScope emptyList()
        val pending = mutableListOf<PendingVideo>()
        roots.forEach { root ->
            if (!root.isDirectory || !root.canRead()) return@forEach
            val relativeRoot = relativeToPrimaryStorage(root) ?: root.name
            collectFiles(root, parentPath = relativeRoot, out = pending)
        }
        resolvePending(pending)
    }

    private suspend fun resolvePending(pending: List<PendingVideo>): List<VideoItem> {
        if (pending.isEmpty()) return emptyList()
        val videos = pending.map { it.toVideo(0L) }
        val resolved = durationResolver.resolveFast(videos)
        if (resolved.isEmpty()) return videos
        return videos.map { video ->
            val duration = resolved[video.uri.toString()] ?: 0L
            if (duration > 0L) video.copy(durationMs = duration) else video
        }
    }

    private suspend fun collectFiles(directory: File, parentPath: String, out: MutableList<PendingVideo>) {
        coroutineContext.ensureActive()
        val children = directory.listFiles() ?: return
        for (child in children) {
            coroutineContext.ensureActive()
            if (child.isDirectory) {
                val childPath = if (parentPath.isEmpty()) child.name else "$parentPath/${child.name}"
                collectFiles(child, childPath, out)
                continue
            }
            if (!child.isFile || !isVideoFile(child.name, mime = "")) continue
            val uri = Uri.fromFile(child)
            out.add(
                PendingVideo(
                    documentUri = uri,
                    name = child.name,
                    modifiedMs = child.lastModified(),
                    relativePath = folderDocumentRelativePath(parentPath),
                    bucketName = parentPath.substringAfterLast('/').ifBlank { parentPath.ifBlank { null } },
                ),
            )
        }
    }

    private suspend fun collectDocuments(
        treeUri: Uri,
        childrenUri: Uri,
        parentPath: String,
        out: MutableList<PendingVideo>,
    ) {
        coroutineContext.ensureActive()
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
                coroutineContext.ensureActive()
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
                        relativePath = folderDocumentRelativePath(parentPath),
                        bucketName = parentPath.substringAfterLast('/').ifBlank { parentPath.ifBlank { null } },
                    ),
                )
            }
        }
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
        private val PRIMARY_STORAGE = File("/storage/emulated/0")
        private val VIDEO_EXTENSIONS = setOf(
            "mp4", "mkv", "avi", "webm", "mov", "m4v", "ts", "flv", "wmv", "mpeg", "mpg", "m3u8",
        )

        fun folderDocumentRelativePath(parentPath: String): String {
            val trimmed = parentPath.trim().trim('/')
            return if (trimmed.isEmpty()) "" else "$trimmed/"
        }

        fun relativeToPrimaryStorage(file: File, primary: File = PRIMARY_STORAGE): String? {
            val primaryAbs = primary.absolutePath.trimEnd('/')
            val fileAbs = file.absolutePath.trimEnd('/')
            if (!fileAbs.startsWith(primaryAbs)) return null
            val relative = fileAbs.removePrefix(primaryAbs).trimStart('/')
            return relative.ifEmpty { null }
        }

        fun isVideoFile(name: String, mime: String): Boolean {
            if (mime.startsWith("video/")) return true
            if (mime == "application/vnd.apple.mpegurl" || mime == "application/x-mpegURL") return true
            val ext = name.substringAfterLast('.', "").lowercase()
            return ext in VIDEO_EXTENSIONS
        }
    }
}
