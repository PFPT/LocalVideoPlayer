package com.zhaoshuo.localvideoplayer.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zhaoshuo.localvideoplayer.R
import com.zhaoshuo.localvideoplayer.ui.theme.LocalLumenTheme
import com.zhaoshuo.localvideoplayer.ui.theme.LumenDelete
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeleteVideoDialog(
    count: Int,
    subtitle: String? = null,
    isDeleting: Boolean = false,
    onDismiss: () -> Unit,
    onHideFromList: () -> Unit,
    onDeleteFromDevice: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val theme = LocalLumenTheme.current

    fun dismissSheet(action: () -> Unit = {}) {
        scope.launch {
            sheetState.hide()
            action()
            onDismiss()
        }
    }

    BackHandler(enabled = !isDeleting) {
        dismissSheet()
    }

    LumenModalBottomSheet(
        onDismissRequest = { if (!isDeleting) dismissSheet() },
        sheetState = sheetState,
    ) {
        Box {
            Column(modifier = Modifier.padding(bottom = 24.dp)) {
                Text(
                    text = if (count > 1) {
                        stringResource(R.string.delete_videos_title, count)
                    } else {
                        stringResource(R.string.delete_video_title)
                    },
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = theme.onSurfaceMuted,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 24.dp),
                    )
                }
                Text(
                    text = stringResource(R.string.delete_video_message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = theme.onSurfaceMuted,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                )
                DeleteActionRow(
                    icon = Icons.Default.RemoveCircleOutline,
                    label = stringResource(R.string.hide_from_list),
                    labelColor = theme.onBackground,
                    iconTint = theme.onBackground,
                    enabled = !isDeleting,
                    onClick = { dismissSheet(onHideFromList) },
                )
                DeleteActionRow(
                    icon = Icons.Default.DeleteForever,
                    label = stringResource(R.string.delete_from_device),
                    labelColor = LumenDelete,
                    iconTint = LumenDelete,
                    enabled = !isDeleting,
                    onClick = { onDeleteFromDevice() },
                )
                TextButton(
                    onClick = { dismissSheet() },
                    enabled = !isDeleting,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                ) {
                    Text(text = stringResource(R.string.cancel_selection))
                }
            }
            if (isDeleting) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(36.dp),
                    color = LumenDelete,
                )
            }
        }
    }
}

@Composable
private fun DeleteActionRow(
    icon: ImageVector,
    label: String,
    labelColor: Color,
    onClick: () -> Unit,
    iconTint: Color,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(22.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = labelColor,
            modifier = Modifier.padding(start = 16.dp),
        )
    }
}
