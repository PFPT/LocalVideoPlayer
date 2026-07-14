package com.zhaoshuo.localvideoplayer.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.zhaoshuo.localvideoplayer.R
import com.zhaoshuo.localvideoplayer.download.DownloadNameResolver
import com.zhaoshuo.localvideoplayer.download.DownloadTask

@Composable
fun RenameDownloadDialog(
    task: DownloadTask,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val extension = remember(task.fileName) {
        val dotIndex = task.fileName.lastIndexOf('.')
        if (dotIndex > 0) task.fileName.substring(dotIndex) else ""
    }
    val initialName = remember(task.fileName) {
        val dotIndex = task.fileName.lastIndexOf('.')
        if (dotIndex > 0) task.fileName.substring(0, dotIndex) else task.fileName
    }
    var name by remember(task.id) { mutableStateOf(initialName) }
    val isValid = remember(name) {
        DownloadNameResolver.composeRenameFileName(task.fileName, name) != null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.download_rename)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.download_rename_hint)) },
                    singleLine = true,
                    supportingText = if (extension.isNotBlank()) {
                        { Text("$name$extension", style = MaterialTheme.typography.bodySmall) }
                    } else {
                        null
                    },
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name) },
                enabled = isValid && name.isNotBlank(),
            ) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}
