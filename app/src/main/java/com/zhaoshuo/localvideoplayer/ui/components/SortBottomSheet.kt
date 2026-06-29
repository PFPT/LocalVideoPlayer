package com.zhaoshuo.localvideoplayer.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.zhaoshuo.localvideoplayer.R
import com.zhaoshuo.localvideoplayer.model.VideoSortOrder
import com.zhaoshuo.localvideoplayer.ui.theme.LumenAccentText
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SortBottomSheet(
    currentOrder: VideoSortOrder,
    onOrderSelected: (VideoSortOrder) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val options = listOf(
        VideoSortOrder.DATE_DESC to R.string.sort_date_desc,
        VideoSortOrder.DATE_ASC to R.string.sort_date_asc,
        VideoSortOrder.NAME_ASC to R.string.sort_name_asc,
        VideoSortOrder.NAME_DESC to R.string.sort_name_desc,
        VideoSortOrder.DURATION_DESC to R.string.sort_duration_desc,
        VideoSortOrder.DURATION_ASC to R.string.sort_duration_asc,
    )

    fun dismissSheet(action: () -> Unit = {}) {
        scope.launch {
            sheetState.hide()
            action()
            onDismiss()
        }
    }

    LumenModalBottomSheet(
        onDismissRequest = { dismissSheet() },
        sheetState = sheetState,
    ) {
        Column(modifier = Modifier.padding(bottom = 32.dp)) {
            Text(
                text = stringResource(R.string.sort),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            options.forEach { (order, labelRes) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            dismissSheet { onOrderSelected(order) }
                        }
                        .padding(horizontal = 24.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(labelRes),
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (order == currentOrder) LumenAccentText else Color.White,
                        modifier = Modifier.weight(1f),
                    )
                    if (order == currentOrder) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = LumenAccentText,
                        )
                    }
                }
            }
        }
    }
}
