package com.zhaoshuo.localvideoplayer.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.zhaoshuo.localvideoplayer.ui.theme.LocalLumenTheme

@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(20.dp),
    fillAlpha: Float? = null,
    borderWidth: Dp = 1.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    val theme = LocalLumenTheme.current
    val resolvedFill = fillAlpha?.let { theme.glassFill.copy(alpha = it) } ?: theme.glassFill
    Box(
        modifier = modifier
            .clip(shape)
            .background(resolvedFill)
            .border(BorderStroke(borderWidth, theme.glassBorder), shape),
        content = content,
    )
}

@Composable
fun LumenSheetSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
    content: @Composable BoxScope.() -> Unit,
) {
    val theme = LocalLumenTheme.current
    Box(
        modifier = modifier
            .clip(shape)
            .background(theme.sheetBg)
            .border(BorderStroke(1.dp, theme.sheetBorder), shape),
        content = content,
    )
}
