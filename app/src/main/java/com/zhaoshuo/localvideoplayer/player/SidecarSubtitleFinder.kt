package com.zhaoshuo.localvideoplayer.player

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import com.zhaoshuo.localvideoplayer.model.VideoItem

fun findSidecarSubtitle(context: Context, video: VideoItem): Uri? {
    findTreeSidecar(context, video)?.let { return it }
    return findMediaStoreSidecar(context, video)
}

private fun folderTreeUri(video: VideoItem): Uri? {
    val stored = video.relativePath
        ?.takeIf { it.startsWith("content://") }
        ?.let { runCatching { Uri.parse(it) }.getOrNull() }
    if (stored != null && DocumentsContract.isTreeUri(stored)) {
        val segments = stored.pathSegments
        if (segments.size >= 2 && segments[0] == "tree" && !segments.contains("document")) {
            return stored
        }
    }
    if (!DocumentsContract.isTreeUri(video.uri)) return null
    val authority = video.uri.authority ?: return null
    val treeId = runCatching { DocumentsContract.getTreeDocumentId(video.uri) }.getOrNull() ?: return null
    return DocumentsContract.buildTreeDocumentUri(authority, treeId)
}

private fun findTreeSidecar(context: Context, video: VideoItem): Uri? {
    val treeUri = folderTreeUri(video) ?: return null
    if (!DocumentsContract.isTreeUri(treeUri)) return null
    val candidates = sidecarSubtitleCandidates(video.title)
    if (candidates.isEmpty()) return null
    val ranked = candidates.mapIndexed { index, name -> name.lowercase() to index }.toMap()
    val videoDocId = runCatching { DocumentsContract.getDocumentId(video.uri) }.getOrNull() ?: return null
    val parentId = if (videoDocId.contains('/')) {
        videoDocId.substringBeforeLast('/')
    } else {
        runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull() ?: return null
    }
    val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId)
    var best: Pair<Int, Uri>? = null
    runCatching {
        context.contentResolver.query(
            childrenUri,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            ),
            null,
            null,
            null,
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            while (cursor.moveToNext()) {
                val name = cursor.getString(nameColumn) ?: continue
                val rank = ranked[name.lowercase()] ?: continue
                val id = cursor.getString(idColumn) ?: continue
                val uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, id)
                val current = best
                if (current == null || rank < current.first) {
                    best = rank to uri
                }
            }
        }
    }
    return best?.second
}

private fun findMediaStoreSidecar(context: Context, video: VideoItem): Uri? {
    val candidates = sidecarSubtitleCandidates(video.title)
    if (candidates.isEmpty()) return null
    val relativePath = video.relativePath?.takeUnless { it.startsWith("content://") }
    val projection = buildList {
        add(MediaStore.Files.FileColumns._ID)
        add(MediaStore.Files.FileColumns.DISPLAY_NAME)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            add(MediaStore.Files.FileColumns.RELATIVE_PATH)
        }
    }.toTypedArray()
    val placeholders = candidates.joinToString(",") { "?" }
    val selection = buildString {
        append(MediaStore.Files.FileColumns.DISPLAY_NAME)
        append(" IN ($placeholders)")
        if (!relativePath.isNullOrBlank() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            append(" AND ")
            append(MediaStore.Files.FileColumns.RELATIVE_PATH)
            append(" = ?")
        }
    }
    val args = buildList {
        addAll(candidates)
        if (!relativePath.isNullOrBlank() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            add(relativePath)
        }
    }.toTypedArray()
    val ranked = candidates.mapIndexed { index, name -> name.lowercase() to index }.toMap()
    var best: Pair<Int, Uri>? = null
    runCatching {
        context.contentResolver.query(
            MediaStore.Files.getContentUri("external"),
            projection,
            selection,
            args,
            null,
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
            while (cursor.moveToNext()) {
                val name = cursor.getString(nameColumn) ?: continue
                val rank = ranked[name.lowercase()] ?: continue
                val id = cursor.getLong(idColumn)
                val uri = ContentUris.withAppendedId(MediaStore.Files.getContentUri("external"), id)
                val current = best
                if (current == null || rank < current.first) {
                    best = rank to uri
                }
            }
        }
    }
    return best?.second
}
