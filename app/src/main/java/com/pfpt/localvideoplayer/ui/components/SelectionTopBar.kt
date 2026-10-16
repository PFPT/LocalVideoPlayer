package com.pfpt.localvideoplayer.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pfpt.localvideoplayer.R
import com.pfpt.localvideoplayer.ui.theme.LocalLumenTheme
import com.pfpt.localvideoplayer.ui.theme.LumenDelete

@Composable
fun SelectionTopBar(
    selectedCount: Int,
    totalCount: Int,
    onSelectAll: () -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = LocalLumenTheme.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onCancel) {
            Text(
                text = stringResource(R.string.cancel_selection),
                color = theme.onBackground,
            )
        }
        Text(
            text = stringResource(R.string.selected_count, selectedCount),
            style = MaterialTheme.typography.titleMedium,
            color = theme.onBackground,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(
                onClick = onSelectAll,
                enabled = selectedCount < totalCount,
            ) {
                Text(
                    text = stringResource(R.string.select_all),
                    color = if (selectedCount < totalCount) theme.accent else theme.onSurfaceMuted,
                )
            }
            TextButton(
                onClick = onDelete,
                enabled = selectedCount > 0,
            ) {
                Text(
                    text = stringResource(R.string.delete),
                    color = if (selectedCount > 0) LumenDelete else theme.onSurfaceMuted,
                )
            }
        }
    }
}
