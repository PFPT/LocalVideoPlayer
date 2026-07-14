package com.zhaoshuo.localvideoplayer.ui.components

import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.zhaoshuo.localvideoplayer.R
import com.zhaoshuo.localvideoplayer.download.DownloadManager
import com.zhaoshuo.localvideoplayer.download.DownloadNameResolver
import com.zhaoshuo.localvideoplayer.download.DownloadUrlClassifier
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddDownloadBottomSheet(
    manager: DownloadManager,
    initialShareText: String?,
    onDismiss: () -> Unit,
    onSubmitted: () -> Unit,
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var url by remember { mutableStateOf("") }
    var fileName by remember { mutableStateOf("") }
    var headersText by remember { mutableStateOf("") }
    var saveToMovies by remember { mutableStateOf(false) }
    var inputError by remember { mutableStateOf<String?>(null) }
    var fileNameEdited by remember { mutableStateOf(false) }
    val invalidUrl = stringResource(R.string.download_invalid_url)
    val invalidHeaders = stringResource(R.string.download_invalid_headers)
    val defaultFileName = stringResource(R.string.download_default_file_name)

    fun applySharedText(text: String, showClipboardToast: Boolean) {
        val parsed = DownloadNameResolver.parseSharedText(text)
        parsed.url?.let { sharedUrl ->
            url = sharedUrl
            if (!fileNameEdited) {
                scope.launch {
                    val resolved = DownloadNameResolver.resolveForEnqueue(
                        sharedUrl,
                        parsed.title.orEmpty(),
                        defaultFileName,
                    )
                    fileName = resolved
                    if (parsed.title != null) fileNameEdited = true
                }
            }
            if (showClipboardToast) {
                Toast.makeText(context, context.getString(R.string.download_clipboard_filled), Toast.LENGTH_SHORT).show()
            }
        }
    }

    LaunchedEffect(initialShareText) {
        initialShareText?.let { applySharedText(it, showClipboardToast = false) }
    }

    LaunchedEffect(Unit) {
        if (initialShareText != null) return@LaunchedEffect
        val clip = context.getSystemService(ClipboardManager::class.java)
            ?.primaryClip
            ?.getItemAt(0)
            ?.coerceToText(context)
            ?.toString()
            .orEmpty()
        if (clip.contains("http://") || clip.contains("https://")) {
            applySharedText(clip, showClipboardToast = true)
        }
    }

    LaunchedEffect(url) {
        val trimmed = url.trim()
        if (trimmed.isEmpty() || fileNameEdited) return@LaunchedEffect
        val resolved = DownloadNameResolver.resolveForEnqueue(trimmed, "", defaultFileName)
        if (resolved != defaultFileName || DownloadUrlClassifier.isHlsDownloadUrl(trimmed)) {
            fileName = resolved
        }
    }

    LumenModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.download_add),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            OutlinedTextField(
                value = url,
                onValueChange = {
                    url = it
                    inputError = null
                    if (it.isBlank()) fileNameEdited = false
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.download_url_label)) },
                singleLine = true,
                isError = inputError != null,
                supportingText = inputError?.let { { Text(it) } },
            )
            OutlinedTextField(
                value = fileName,
                onValueChange = {
                    fileName = it
                    fileNameEdited = it.isNotBlank()
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.download_file_name_label)) },
                singleLine = true,
            )
            OutlinedTextField(
                value = headersText,
                onValueChange = { headersText = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.download_headers_label)) },
                minLines = 2,
            )
            Row {
                Checkbox(checked = saveToMovies, onCheckedChange = { saveToMovies = it })
                Text(stringResource(R.string.download_save_movies), modifier = Modifier.padding(top = 12.dp))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(
                    onClick = {
                        scope.launch {
                            sheetState.hide()
                            onDismiss()
                        }
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.theme_cancel))
                }
                Button(
                    onClick = {
                        scope.launch {
                            val parsedUrl = url.trim()
                            if (!parsedUrl.startsWith("http://") && !parsedUrl.startsWith("https://")) {
                                inputError = invalidUrl
                                return@launch
                            }
                            val headers = parseHeaders(headersText)
                            if (headers == null) {
                                inputError = invalidHeaders
                                return@launch
                            }
                            val usedProxy = manager.enqueue(parsedUrl, fileName.trim(), headers, saveToMovies)
                            if (usedProxy) {
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.download_proxy_unwrapped),
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                            sheetState.hide()
                            onSubmitted()
                            onDismiss()
                        }
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.download_add_queue))
                }
            }
        }
    }
}

private fun parseHeaders(text: String): Map<String, String>? = buildMap {
    text.lineSequence().filter { it.isNotBlank() }.forEach { line ->
        val split = line.indexOf(':')
        if (split <= 0 || split == line.lastIndex) return null
        put(line.substring(0, split).trim(), line.substring(split + 1).trim())
    }
}
