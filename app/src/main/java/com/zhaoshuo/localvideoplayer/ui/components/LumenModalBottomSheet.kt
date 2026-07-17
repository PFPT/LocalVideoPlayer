package com.zhaoshuo.localvideoplayer.ui.components

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.zhaoshuo.localvideoplayer.ui.theme.LocalLumenTheme
import com.zhaoshuo.localvideoplayer.ui.theme.LocalThemeMode
import com.zhaoshuo.localvideoplayer.ui.theme.ThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LumenModalBottomSheet(
    onDismissRequest: () -> Unit,
    sheetState: SheetState,
    content: @Composable ColumnScope.() -> Unit,
) {
    val theme = LocalLumenTheme.current
    val mode = LocalThemeMode.current
    val containerColor = when (mode) {
        ThemeMode.Light -> theme.sheetBg
        ThemeMode.Dark -> theme.background
    }
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = containerColor,
        scrimColor = Color.Black.copy(alpha = if (mode == ThemeMode.Light) 0.4f else 0.6f),
        content = content,
    )
}
