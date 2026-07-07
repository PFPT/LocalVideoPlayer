package com.zhaoshuo.localvideoplayer.ui.components

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LumenModalBottomSheet(
    onDismissRequest: () -> Unit,
    sheetState: SheetState,
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background,
        scrimColor = Color.Black.copy(alpha = 0.6f),
        content = content,
    )
}
