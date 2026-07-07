package com.zhaoshuo.localvideoplayer.ui

import android.app.Activity
import android.content.IntentSender
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.zhaoshuo.localvideoplayer.R
import com.zhaoshuo.localvideoplayer.data.HiddenVideosStore
import com.zhaoshuo.localvideoplayer.data.PlayCountStore
import com.zhaoshuo.localvideoplayer.data.PlaybackStore
import com.zhaoshuo.localvideoplayer.util.MediaDeleteHelper
import com.zhaoshuo.localvideoplayer.util.UriConfirmRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun rememberVideoDeleter(
    playbackStore: PlaybackStore,
    hiddenVideosStore: HiddenVideosStore,
    playCountStore: PlayCountStore,
    onVideosRemoved: (List<Uri>) -> Unit,
    onDeleteStarted: () -> Unit = {},
    onDeleteFinished: () -> Unit = {},
): (List<Uri>) -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val deleteHelper = remember { MediaDeleteHelper(context) }
    var pendingConfirmUris by remember { mutableStateOf<List<Uri>?>(null) }
    var confirmQueue by remember { mutableStateOf<List<UriConfirmRequest>>(emptyList()) }
    var nextConfirmSender by remember { mutableStateOf<IntentSender?>(null) }

    suspend fun applyHidden(uris: List<Uri>) {
        if (uris.isEmpty()) return
        withContext(Dispatchers.IO) {
            hiddenVideosStore.hideAll(uris)
            playbackStore.removeRecords(uris)
            playCountStore.removeForUris(uris)
        }
        onVideosRemoved(uris)
    }

    fun finishDelete() {
        pendingConfirmUris = null
        confirmQueue = emptyList()
        nextConfirmSender = null
        onDeleteFinished()
    }

    val confirmDeleteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        val uris = pendingConfirmUris ?: return@rememberLauncherForActivityResult
        pendingConfirmUris = null
        scope.launch {
            if (result.resultCode == Activity.RESULT_OK) {
                applyHidden(uris)
                if (confirmQueue.isNotEmpty()) {
                    confirmQueue = confirmQueue.drop(1)
                    val next = confirmQueue.firstOrNull()
                    if (next != null) {
                        pendingConfirmUris = listOf(next.uri)
                        nextConfirmSender = next.intentSender
                        return@launch
                    }
                }
                finishDelete()
            } else {
                confirmQueue = emptyList()
                Toast.makeText(
                    context,
                    context.getString(R.string.delete_cancelled),
                    Toast.LENGTH_SHORT,
                ).show()
                finishDelete()
            }
        }
    }

    LaunchedEffect(nextConfirmSender) {
        val sender = nextConfirmSender ?: return@LaunchedEffect
        nextConfirmSender = null
        confirmDeleteLauncher.launch(IntentSenderRequest.Builder(sender).build())
    }

    return { uris ->
        onDeleteStarted()
        scope.launch {
            val batchResult = withContext(Dispatchers.IO) {
                deleteHelper.deleteUris(uris)
            }

            if (batchResult.deleted.isNotEmpty()) {
                applyHidden(batchResult.deleted)
            }

            val batchIntent = batchResult.confirmIntentSender
            if (batchResult.needsConfirmation.isNotEmpty() && batchIntent != null) {
                pendingConfirmUris = batchResult.needsConfirmation
                if (batchResult.failed.isNotEmpty()) {
                    handleDeleteFailures(context, batchResult.failed)
                }
                withContext(Dispatchers.Main.immediate) {
                    confirmDeleteLauncher.launch(
                        IntentSenderRequest.Builder(batchIntent).build(),
                    )
                }
                return@launch
            }

            if (batchResult.perUriConfirmations.isNotEmpty()) {
                confirmQueue = batchResult.perUriConfirmations
                if (batchResult.failed.isNotEmpty()) {
                    handleDeleteFailures(context, batchResult.failed)
                }
                val first = confirmQueue.first()
                pendingConfirmUris = listOf(first.uri)
                nextConfirmSender = first.intentSender
                return@launch
            }

            val unhandledConfirmation = batchResult.needsConfirmation.filter { uri ->
                batchResult.perUriConfirmations.none { it.uri == uri }
            }
            if (unhandledConfirmation.isNotEmpty()) {
                handleDeleteFailures(context, unhandledConfirmation)
            }

            if (batchResult.failed.isNotEmpty()) {
                handleDeleteFailures(context, batchResult.failed)
            }

            finishDelete()
        }
    }
}

private fun handleDeleteFailures(
    context: android.content.Context,
    failed: List<Uri>,
) {
    if (failed.isEmpty()) return
    Toast.makeText(
        context,
        context.getString(R.string.delete_partial_failed, failed.size),
        Toast.LENGTH_SHORT,
    ).show()
}
