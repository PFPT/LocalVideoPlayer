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
import com.zhaoshuo.localvideoplayer.ui.theme.LumenGlassBorder
import com.zhaoshuo.localvideoplayer.ui.theme.LumenGlassFill
import com.zhaoshuo.localvideoplayer.ui.theme.LumenSheetBg
import com.zhaoshuo.localvideoplayer.ui.theme.LumenSheetBorder

@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(20.dp),
    fillAlpha: Float = 0.10f,
    borderWidth: Dp = 1.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(LumenGlassFill.copy(alpha = fillAlpha))
            .border(BorderStroke(borderWidth, LumenGlassBorder), shape),
        content = content,
    )
}

@Composable
fun LumenSheetSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(LumenSheetBg)
            .border(BorderStroke(1.dp, LumenSheetBorder), shape),
        content = content,
    )
}
