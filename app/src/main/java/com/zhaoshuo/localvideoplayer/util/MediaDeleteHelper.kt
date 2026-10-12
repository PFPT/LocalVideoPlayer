package com.zhaoshuo.localvideoplayer.util

import android.app.RecoverableSecurityException
import android.content.ContentUris
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import java.io.File

private val CREATE_DELETE_REQUEST_COLLECTIONS = setOf(
    "video",
    "images",
    "audio",
    "downloads",
)

fun acceptsCreateDeleteRequest(uri: Uri): Boolean {
    if (!uri.scheme.equals("content", ignoreCase = true)) return false
    val authority = uri.authority?.substringAfter('@') ?: return false
    if (!authority.equals("media", ignoreCase = true)) return false
    return uri.pathSegments.any { segment ->
        segment.lowercase() in CREATE_DELETE_REQUEST_COLLECTIONS
    }
}

data class UriConfirmRequest(
    val uri: Uri,
    val intentSender: IntentSender,
)

data class MediaDeleteBatchResult(
    val deleted: List<Uri>,
    val needsConfirmation: List<Uri>,
    val confirmIntentSender: IntentSender?,
    val perUriConfirmations: List<UriConfirmRequest>,
    val failed: List<Uri>,
)

class MediaDeleteHelper(private val context: Context) {

    fun deleteUris(uris: List<Uri>): MediaDeleteBatchResult {
        val deleted = mutableListOf<Uri>()
        val needsConfirmation = mutableListOf<Uri>()
        val confirmTargets = mutableListOf<Uri>()
        val perUriConfirmations = mutableListOf<UriConfirmRequest>()
        val failed = mutableListOf<Uri>()
        var recoverableIntent: IntentSender? = null

        uris.forEach { uri ->
            when (val attempt = tryDelete(uri)) {
                is DeleteAttempt.Success -> deleted.add(uri)
                is DeleteAttempt.NeedsConfirmation -> {
                    val requestUri = attempt.requestUri ?: uri
                    val sender = attempt.intentSender ?: recoverDeleteIntentSender(requestUri)
                    if (sender != null) {
                        perUriConfirmations.add(UriConfirmRequest(uri, sender))
                        if (recoverableIntent == null) {
                            recoverableIntent = sender
                        }
                    }
                    if (acceptsCreateDeleteRequest(requestUri)) {
                        needsConfirmation.add(uri)
                        confirmTargets.add(requestUri)
                    } else if (sender != null) {
                        needsConfirmation.add(uri)
                    } else {
                        failed.add(uri)
                    }
                }
                is DeleteAttempt.Failed -> failed.add(uri)
            }
        }

        val confirmIntent = buildDeleteRequest(confirmTargets) ?: recoverableIntent

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            val withoutSender = needsConfirmation.filter { uri ->
                perUriConfirmations.none { it.uri == uri }
            }
            failed.addAll(withoutSender)
        }

        return MediaDeleteBatchResult(
            deleted = deleted,
            needsConfirmation = needsConfirmation,
            confirmIntentSender = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) confirmIntent else null,
            perUriConfirmations = perUriConfirmations,
            failed = failed,
        )
    }

    private sealed interface DeleteAttempt {
        data object Success : DeleteAttempt
        data class NeedsConfirmation(
            val intentSender: IntentSender? = null,
            val requestUri: Uri? = null,
        ) : DeleteAttempt
        data object Failed : DeleteAttempt
    }

    private fun tryDelete(uri: Uri): DeleteAttempt {
        if (uri.scheme.equals("file", ignoreCase = true) || isFilesCollectionUri(uri)) {
            return tryFileOrFilesDelete(uri)
        }
        if (DocumentsContract.isDocumentUri(context, uri)) {
            return tryDocumentDelete(uri)
        }
        if (acceptsCreateDeleteRequest(uri)) {
            return tryOwnedMediaDelete(uri)
        }
        return DeleteAttempt.Failed
    }

    private fun tryOwnedMediaDelete(uri: Uri): DeleteAttempt {
        when (val attempt = tryMediaStoreDelete(uri)) {
            is DeleteAttempt.Success -> return DeleteAttempt.Success
            is DeleteAttempt.NeedsConfirmation -> {
                if (tryFilesystemDelete(uri)) return DeleteAttempt.Success
                return attempt
            }
            is DeleteAttempt.Failed -> Unit
        }
        if (tryFilesystemDelete(uri)) return DeleteAttempt.Success
        return if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            acceptsCreateDeleteRequest(uri)
        ) {
            DeleteAttempt.NeedsConfirmation()
        } else {
            DeleteAttempt.Failed
        }
    }

    private fun tryFileOrFilesDelete(uri: Uri): DeleteAttempt {
        if (tryFilesystemDelete(uri)) return DeleteAttempt.Success
        val mediaUri = findMediaStoreUri(uri) ?: return DeleteAttempt.Failed
        return when (val attempt = tryOwnedMediaDelete(mediaUri)) {
            is DeleteAttempt.Success -> DeleteAttempt.Success
            is DeleteAttempt.Failed -> DeleteAttempt.Failed
            is DeleteAttempt.NeedsConfirmation -> attempt.copy(
                requestUri = attempt.requestUri ?: mediaUri,
            )
        }
    }

    private fun tryDocumentDelete(uri: Uri): DeleteAttempt {
        try {
            if (DocumentsContract.deleteDocument(context.contentResolver, uri)) {
                return DeleteAttempt.Success
            }
        } catch (_: Exception) {
        }
        return if (tryPrimaryDocumentFileDelete(uri)) {
            DeleteAttempt.Success
        } else {
            DeleteAttempt.Failed
        }
    }

    private fun tryMediaStoreDelete(uri: Uri): DeleteAttempt {
        return try {
            val rows = context.contentResolver.delete(uri, null, null)
            if (rows > 0) {
                DeleteAttempt.Success
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                DeleteAttempt.NeedsConfirmation()
            } else {
                DeleteAttempt.Failed
            }
        } catch (e: RecoverableSecurityException) {
            DeleteAttempt.NeedsConfirmation(e.userAction.actionIntent.intentSender)
        } catch (_: SecurityException) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                DeleteAttempt.NeedsConfirmation()
            } else {
                DeleteAttempt.Failed
            }
        } catch (_: Exception) {
            DeleteAttempt.Failed
        }
    }

    private fun tryFilesystemDelete(uri: Uri): Boolean {
        if (!canDeleteSharedStorage()) return false
        val path = resolvePath(uri) ?: return false
        return deleteFileAtPath(path, uri)
    }

    private fun tryPrimaryDocumentFileDelete(uri: Uri): Boolean {
        if (!canDeleteSharedStorage()) return false
        val documentId = runCatching { DocumentsContract.getDocumentId(uri) }.getOrNull() ?: return false
        val path = primaryDocumentPath(documentId) ?: return false
        return deleteFileAtPath(path, uri)
    }

    private fun deleteFileAtPath(path: String, sourceUri: Uri): Boolean {
        val file = File(path)
        if (!file.isFile || !file.delete()) return false
        if (sourceUri.scheme.equals("content", ignoreCase = true)) {
            try {
                context.contentResolver.delete(sourceUri, null, null)
            } catch (_: Exception) {
            }
        }
        findMediaStoreUri(path)?.let { mediaUri ->
            if (mediaUri != sourceUri) {
                try {
                    context.contentResolver.delete(mediaUri, null, null)
                } catch (_: Exception) {
                }
            }
        }
        return true
    }

    private fun canDeleteSharedStorage(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager()
    }

    private fun resolvePath(uri: Uri): String? {
        if (uri.scheme.equals("file", ignoreCase = true)) return uri.path
        if (DocumentsContract.isDocumentUri(context, uri)) {
            val documentId = runCatching { DocumentsContract.getDocumentId(uri) }.getOrNull()
            return documentId?.let(::primaryDocumentPath)
        }
        val projection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            arrayOf(
                MediaStore.MediaColumns.RELATIVE_PATH,
                MediaStore.MediaColumns.DISPLAY_NAME,
            )
        } else {
            @Suppress("DEPRECATION")
            arrayOf(MediaStore.MediaColumns.DATA)
        }
        context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return null
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val relativePathIndex = cursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
                val displayNameIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                if (relativePathIndex < 0 || displayNameIndex < 0) return null
                val relativePath = cursor.getString(relativePathIndex) ?: return null
                val displayName = cursor.getString(displayNameIndex) ?: return null
                File(Environment.getExternalStorageDirectory(), relativePath + displayName).absolutePath
            } else {
                @Suppress("DEPRECATION")
                val dataIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                if (dataIndex < 0) null else cursor.getString(dataIndex)
            }
        }
        return null
    }

    private fun findMediaStoreUri(uri: Uri): Uri? {
        val path = resolvePath(uri) ?: return null
        return findMediaStoreUri(path)
    }

    private fun findMediaStoreUri(absolutePath: String): Uri? {
        val file = File(absolutePath)
        val collections = buildList {
            add(MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(MediaStore.Downloads.EXTERNAL_CONTENT_URI)
            }
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return collections.firstNotNullOfOrNull { collection ->
                queryByData(collection, file.absolutePath)
            }
        }
        val relative = relativeToPrimary(file) ?: return null
        val directory = relative.substringBeforeLast('/', "")
        val relativePath = if (directory.isEmpty()) "" else "$directory/"
        return collections.firstNotNullOfOrNull { collection ->
            queryByDisplayName(collection, file.name, relativePath)
        }
    }

    private fun queryByDisplayName(collection: Uri, displayName: String, relativePath: String): Uri? {
        val withSlash = if (relativePath.endsWith("/")) relativePath else "$relativePath/"
        val withoutSlash = withSlash.trimEnd('/')
        return queryMediaId(
            collection,
            "${MediaStore.MediaColumns.DISPLAY_NAME} = ? AND " +
                "(${MediaStore.MediaColumns.RELATIVE_PATH} = ? OR ${MediaStore.MediaColumns.RELATIVE_PATH} = ?)",
            arrayOf(displayName, withSlash, withoutSlash),
        )
    }

    private fun queryByData(collection: Uri, absolutePath: String): Uri? {
        @Suppress("DEPRECATION")
        return queryMediaId(
            collection,
            "${MediaStore.MediaColumns.DATA} = ?",
            arrayOf(absolutePath),
        )
    }

    private fun queryMediaId(collection: Uri, selection: String, args: Array<String>): Uri? {
        return try {
            context.contentResolver.query(
                collection,
                arrayOf(MediaStore.MediaColumns._ID),
                selection,
                args,
                null,
            )?.use { cursor ->
                if (!cursor.moveToFirst()) return null
                val idIndex = cursor.getColumnIndex(MediaStore.MediaColumns._ID)
                if (idIndex < 0) return null
                val id = cursor.getLong(idIndex)
                ContentUris.withAppendedId(collection, id)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun relativeToPrimary(file: File): String? {
        val primary = Environment.getExternalStorageDirectory().absolutePath.trimEnd('/')
        val absolute = file.absolutePath
        if (!absolute.startsWith(primary)) return null
        return absolute.removePrefix(primary).trimStart('/').ifEmpty { null }
    }

    private fun primaryDocumentPath(documentId: String): String? {
        val separator = documentId.indexOf(':')
        if (separator <= 0) return null
        val volume = documentId.substring(0, separator)
        if (!volume.equals("primary", ignoreCase = true)) return null
        val relative = documentId.substring(separator + 1)
        if (relative.isEmpty()) return null
        return File(Environment.getExternalStorageDirectory(), relative).absolutePath
    }

    private fun isFilesCollectionUri(uri: Uri): Boolean {
        if (!uri.scheme.equals("content", ignoreCase = true)) return false
        val authority = uri.authority?.substringAfter('@') ?: return false
        if (!authority.equals("media", ignoreCase = true)) return false
        return uri.pathSegments.any { it.equals("file", ignoreCase = true) }
    }

    private fun buildDeleteRequest(uris: List<Uri>): IntentSender? {
        val acceptable = uris.filter(::acceptsCreateDeleteRequest).distinct()
        if (acceptable.isEmpty()) return null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return try {
                MediaStore.createDeleteRequest(context.contentResolver, acceptable).intentSender
            } catch (_: Exception) {
                null
            }
        }
        return null
    }

    private fun recoverDeleteIntentSender(uri: Uri): IntentSender? {
        if (!acceptsCreateDeleteRequest(uri)) return null
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        try {
            context.contentResolver.delete(uri, null, null)
        } catch (e: RecoverableSecurityException) {
            return e.userAction.actionIntent.intentSender
        } catch (_: Exception) {
        }
        return null
    }
}
