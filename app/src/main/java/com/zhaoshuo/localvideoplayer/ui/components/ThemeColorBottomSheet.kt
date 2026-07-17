package com.zhaoshuo.localvideoplayer.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.zhaoshuo.localvideoplayer.R
import com.zhaoshuo.localvideoplayer.model.LibraryTypeTab
import com.zhaoshuo.localvideoplayer.ui.theme.HsvColor
import com.zhaoshuo.localvideoplayer.ui.theme.LocalLumenTheme
import com.zhaoshuo.localvideoplayer.ui.theme.LumenThemeColors
import com.zhaoshuo.localvideoplayer.ui.theme.LumenThumbCorner
import com.zhaoshuo.localvideoplayer.ui.theme.ThemeMode
import com.zhaoshuo.localvideoplayer.ui.theme.formatAlphaPercent
import com.zhaoshuo.localvideoplayer.ui.theme.toColor
import com.zhaoshuo.localvideoplayer.ui.theme.toHexArgb
import com.zhaoshuo.localvideoplayer.ui.theme.toHsv
import kotlinx.coroutines.launch

private enum class ThemeColorTarget {
    Background,
    Text,
    ListOption,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeColorBottomSheet(
    themeMode: ThemeMode,
    initialColors: LumenThemeColors,
    onSave: (LumenThemeColors) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val theme = LocalLumenTheme.current
    val modeDefaults = remember(themeMode) { LumenThemeColors.defaults(themeMode) }

    var draftColors by remember(initialColors) { mutableStateOf(initialColors) }
    var selectedTarget by remember { mutableStateOf(ThemeColorTarget.Background) }

    val editingColor = when (selectedTarget) {
        ThemeColorTarget.Background -> draftColors.background
        ThemeColorTarget.Text -> draftColors.onBackground
        ThemeColorTarget.ListOption -> draftColors.surface
    }

    var hue by remember(editingColor, selectedTarget) { mutableFloatStateOf(editingColor.toHsv().hue) }
    var saturation by remember(editingColor, selectedTarget) {
        mutableFloatStateOf(editingColor.toHsv().saturation)
    }
    var value by remember(editingColor, selectedTarget) { mutableFloatStateOf(editingColor.toHsv().value) }
    var alpha by remember(editingColor, selectedTarget) { mutableFloatStateOf(editingColor.alpha) }

    fun currentPickerColor(): Color = HsvColor(hue, saturation, value).toColor(alpha)

    fun applyPickerColorToDraft() {
        val color = currentPickerColor()
        draftColors = when (selectedTarget) {
            ThemeColorTarget.Background -> draftColors.withBackground(color)
            ThemeColorTarget.Text -> draftColors.withOnBackground(color)
            ThemeColorTarget.ListOption -> draftColors.withSurface(color)
        }
    }

    fun syncPickerFromDraft(target: ThemeColorTarget) {
        val color = when (target) {
            ThemeColorTarget.Background -> draftColors.background
            ThemeColorTarget.Text -> draftColors.onBackground
            ThemeColorTarget.ListOption -> draftColors.surface
        }
        val hsv = color.toHsv()
        hue = hsv.hue
        saturation = hsv.saturation
        value = hsv.value
        alpha = color.alpha
    }

    fun dismissSheet(save: Boolean) {
        scope.launch {
            sheetState.hide()
            if (save) {
                onSave(draftColors)
            }
            onDismiss()
        }
    }

    fun resetAllAndSave() {
        scope.launch {
            draftColors = modeDefaults
            sheetState.hide()
            onSave(modeDefaults)
            onDismiss()
        }
    }

    val color = currentPickerColor()
    val opaqueColor = HsvColor(hue, saturation, value).toColor(1f)

    LumenModalBottomSheet(
        onDismissRequest = { dismissSheet(save = false) },
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ThemePreviewBar(colors = draftColors)

            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.theme_color),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    TextButton(
                        onClick = { resetAllAndSave() },
                    ) {
                        Text(
                            text = stringResource(R.string.theme_reset_all),
                            color = theme.accent,
                        )
                    }
                }

                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = selectedTarget == ThemeColorTarget.Background,
                        onClick = {
                            selectedTarget = ThemeColorTarget.Background
                            syncPickerFromDraft(ThemeColorTarget.Background)
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3),
                    ) {
                        Text(stringResource(R.string.theme_background))
                    }
                    SegmentedButton(
                        selected = selectedTarget == ThemeColorTarget.Text,
                        onClick = {
                            selectedTarget = ThemeColorTarget.Text
                            syncPickerFromDraft(ThemeColorTarget.Text)
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
                    ) {
                        Text(stringResource(R.string.theme_text))
                    }
                    SegmentedButton(
                        selected = selectedTarget == ThemeColorTarget.ListOption,
                        onClick = {
                            selectedTarget = ThemeColorTarget.ListOption
                            syncPickerFromDraft(ThemeColorTarget.ListOption)
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
                    ) {
                        Text(stringResource(R.string.theme_list_option))
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(
                        onClick = {
                            val defaultColor = when (selectedTarget) {
                                ThemeColorTarget.Background -> modeDefaults.background
                                ThemeColorTarget.Text -> modeDefaults.onBackground
                                ThemeColorTarget.ListOption -> modeDefaults.surface
                            }
                            val hsv = defaultColor.toHsv()
                            hue = hsv.hue
                            saturation = hsv.saturation
                            value = hsv.value
                            alpha = defaultColor.alpha
                            applyPickerColorToDraft()
                        },
                    ) {
                        Text(
                            text = stringResource(R.string.theme_reset_default),
                            color = theme.accent,
                        )
                    }
                }

                CurrentColorRow(color = color)

                SaturationValuePicker(
                    hue = hue,
                    saturation = saturation,
                    value = value,
                    onChange = { s, v ->
                        saturation = s
                        value = v
                        applyPickerColorToDraft()
                    },
                )

                HueSlider(
                    hue = hue,
                    onHueChange = {
                        hue = it
                        applyPickerColorToDraft()
                    },
                )

                AlphaSliderSection(
                    color = opaqueColor,
                    alpha = alpha,
                    onAlphaChange = {
                        alpha = it
                        applyPickerColorToDraft()
                    },
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    TextButton(
                        onClick = { dismissSheet(save = false) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.cancel))
                    }
                    Button(
                        onClick = { dismissSheet(save = true) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.theme_save))
                    }
                }
            }
        }
    }
}

@Composable
private fun CurrentColorRow(color: Color) {
    val theme = LocalLumenTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, theme.sheetBorder, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .border(1.dp, theme.sheetBorder, CircleShape),
        ) {
            CheckerboardBackground(modifier = Modifier.fillMaxSize())
            Box(modifier = Modifier.fillMaxSize().background(color))
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = stringResource(R.string.theme_current_color),
                style = MaterialTheme.typography.bodySmall,
                color = theme.onSurfaceMuted,
            )
            Text(
                text = color.toHexArgb(),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
    }
}

@Composable
private fun ThemePreviewBar(colors: LumenThemeColors) {
    val theme = LocalLumenTheme.current
    val previewTabs = remember {
        listOf(LibraryTypeTab.ALL, LibraryTypeTab.EPISODES, LibraryTypeTab.MARKED)
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, theme.sheetBorder, RoundedCornerShape(12.dp)),
    ) {
        CheckerboardBackground(modifier = Modifier.fillMaxSize())
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.background)
                .padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CompositionLocalProvider(LocalLumenTheme provides colors) {
                LumenLibraryTabBar(
                    tabs = previewTabs,
                    selectedTab = LibraryTypeTab.EPISODES,
                    onTabSelected = {},
                    contentPadding = PaddingValues(horizontal = 12.dp),
                )
            }
            Row(
                modifier = Modifier.padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 72.dp, height = 48.dp)
                        .clip(RoundedCornerShape(LumenThumbCorner))
                        .background(colors.surface.copy(alpha = 0.85f)),
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(R.string.video_list_title),
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.onBackground,
                        maxLines = 1,
                    )
                    Text(
                        text = "00:00 · 2026/01/01",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceMuted,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun SaturationValuePicker(
    hue: Float,
    saturation: Float,
    value: Float,
    onChange: (Float, Float) -> Unit,
) {
    val theme = LocalLumenTheme.current
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, theme.sheetBorder, RoundedCornerShape(12.dp))
            .background(
                Brush.horizontalGradient(
                    colors = listOf(Color.White, HsvColor(hue, 1f, 1f).toColor()),
                ),
            )
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color.Transparent, Color.Black),
                ),
            )
            .pointerInput(hue) {
                detectDragGestures(
                    onDragStart = { offset ->
                        val s = (offset.x / size.width).coerceIn(0f, 1f)
                        val v = (1f - offset.y / size.height).coerceIn(0f, 1f)
                        onChange(s, v)
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        val s = (change.position.x / size.width).coerceIn(0f, 1f)
                        val v = (1f - change.position.y / size.height).coerceIn(0f, 1f)
                        onChange(s, v)
                    },
                )
            },
    ) {
        val thumbSize = 22.dp
        val thumbOffsetX = (maxWidth - thumbSize) * saturation
        val thumbOffsetY = (maxHeight - thumbSize) * (1f - value)
        Box(
            modifier = Modifier
                .offset(x = thumbOffsetX, y = thumbOffsetY)
                .size(thumbSize)
                .shadow(4.dp, CircleShape)
                .border(2.dp, Color.White, CircleShape)
                .background(HsvColor(hue, saturation, value).toColor(), CircleShape),
        )
    }
}

@Composable
private fun HueSlider(
    hue: Float,
    onHueChange: (Float) -> Unit,
) {
    val theme = LocalLumenTheme.current
    val hueColors = remember {
        List(7) { index ->
            HsvColor(index * 60f, 1f, 1f).toColor()
        }
    }
    val currentHueColor = HsvColor(hue, 1f, 1f).toColor()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, theme.sheetBorder, RoundedCornerShape(12.dp)),
    ) {
        Slider(
            value = hue,
            onValueChange = onHueChange,
            valueRange = 0f..360f,
            colors = SliderDefaults.colors(
                thumbColor = currentHueColor,
                activeTrackColor = Color.Transparent,
                inactiveTrackColor = Color.Transparent,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp)
                .background(Brush.horizontalGradient(hueColors)),
        )
    }
}

@Composable
private fun AlphaSliderSection(
    color: Color,
    alpha: Float,
    onAlphaChange: (Float) -> Unit,
) {
    val theme = LocalLumenTheme.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, theme.sheetBorder, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(R.string.theme_alpha),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = formatAlphaPercent(alpha),
                style = MaterialTheme.typography.bodySmall,
                color = theme.onSurfaceMuted,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp)
                .clip(RoundedCornerShape(8.dp)),
        ) {
            CheckerboardBackground(modifier = Modifier.fillMaxSize())
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(color.copy(alpha = 0f), color),
                        ),
                    ),
            )
            Slider(
                value = alpha,
                onValueChange = onAlphaChange,
                valueRange = 0f..1f,
                colors = SliderDefaults.colors(
                    thumbColor = theme.accent,
                    activeTrackColor = Color.Transparent,
                    inactiveTrackColor = Color.Transparent,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun CheckerboardBackground(modifier: Modifier = Modifier) {
    val light = Color(0xFF3A3A48)
    val dark = Color(0xFF2A2A36)
    Canvas(modifier = modifier) {
        val cell = 8.dp.toPx()
        val columns = (size.width / cell).toInt() + 1
        val rows = (size.height / cell).toInt() + 1
        for (row in 0 until rows) {
            for (column in 0 until columns) {
                drawRect(
                    color = if ((row + column) % 2 == 0) light else dark,
                    topLeft = Offset(column * cell, row * cell),
                    size = Size(cell, cell),
                )
            }
        }
    }
}
