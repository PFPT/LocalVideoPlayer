package com.zhaoshuo.localvideoplayer.util

import android.app.RecoverableSecurityException
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File

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
        val perUriConfirmations = mutableListOf<UriConfirmRequest>()
        val failed = mutableListOf<Uri>()
        var recoverableIntent: IntentSender? = null

        uris.forEach { uri ->
            when (val attempt = tryDelete(uri)) {
                is DeleteAttempt.Success -> deleted.add(uri)
                is DeleteAttempt.NeedsConfirmation -> {
                    needsConfirmation.add(uri)
                    val sender = attempt.intentSender ?: recoverDeleteIntentSender(uri)
                    if (sender != null) {
                        perUriConfirmations.add(UriConfirmRequest(uri, sender))
                        if (recoverableIntent == null) {
                            recoverableIntent = sender
                        }
                    }
                }
                is DeleteAttempt.Failed -> failed.add(uri)
            }
        }

        val confirmIntent = buildDeleteRequest(needsConfirmation) ?: recoverableIntent

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
        data class NeedsConfirmation(val intentSender: IntentSender? = null) : DeleteAttempt
        data object Failed : DeleteAttempt
    }

    private fun tryDelete(uri: Uri): DeleteAttempt {
        when (val mediaStoreAttempt = tryMediaStoreDelete(uri)) {
            is DeleteAttempt.Success -> return DeleteAttempt.Success
            is DeleteAttempt.NeedsConfirmation -> return mediaStoreAttempt
            is DeleteAttempt.Failed -> Unit
        }
        return if (tryFilesystemDelete(uri)) {
            DeleteAttempt.Success
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            DeleteAttempt.NeedsConfirmation()
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
        } catch (_: UnsupportedOperationException) {
            DeleteAttempt.Failed
        }
    }

    private fun tryFilesystemDelete(uri: Uri): Boolean {
        val path = resolvePath(uri) ?: return false
        val file = File(path)
        if (!file.isFile || !file.delete()) return false
        try {
            context.contentResolver.delete(uri, null, null)
        } catch (_: Exception) {
        }
        return true
    }

    private fun resolvePath(uri: Uri): String? {
        if (uri.scheme == "file") return uri.path
        val projection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            arrayOf(
                MediaStore.Video.Media.RELATIVE_PATH,
                MediaStore.Video.Media.DISPLAY_NAME,
            )
        } else {
            @Suppress("DEPRECATION")
            arrayOf(MediaStore.Video.Media.DATA)
        }
        context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return null
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val relativePath = cursor.getString(0) ?: return null
                val displayName = cursor.getString(1) ?: return null
                File(Environment.getExternalStorageDirectory(), relativePath + displayName).absolutePath
            } else {
                @Suppress("DEPRECATION")
                cursor.getString(0)
            }
        }
        return null
    }

    private fun buildDeleteRequest(uris: List<Uri>): IntentSender? {
        if (uris.isEmpty()) return null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return try {
                MediaStore.createDeleteRequest(context.contentResolver, uris).intentSender
            } catch (_: Exception) {
                null
            }
        }
        return null
    }

    private fun recoverDeleteIntentSender(uri: Uri): IntentSender? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        try {
            context.contentResolver.delete(uri, null, null)
        } catch (e: RecoverableSecurityException) {
            return e.userAction.actionIntent.intentSender
        }
        return null
    }
}
