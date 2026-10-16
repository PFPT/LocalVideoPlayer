package com.pfpt.localvideoplayer.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.max
import kotlin.math.min

data class HsvColor(
    val hue: Float,
    val saturation: Float,
    val value: Float,
)

fun Color.toHsv(): HsvColor {
    val r = red
    val g = green
    val b = blue
    val maxC = max(r, max(g, b))
    val minC = min(r, min(g, b))
    val delta = maxC - minC

    val hue = when {
        delta == 0f -> 0f
        maxC == r -> ((g - b) / delta + if (g < b) 6f else 0f) * 60f
        maxC == g -> ((b - r) / delta + 2f) * 60f
        else -> ((r - g) / delta + 4f) * 60f
    }
    val saturation = if (maxC == 0f) 0f else delta / maxC
    return HsvColor(
        hue = hue.coerceIn(0f, 360f),
        saturation = saturation.coerceIn(0f, 1f),
        value = maxC.coerceIn(0f, 1f),
    )
}

fun HsvColor.toColor(alpha: Float = 1f): Color {
    val h = hue % 360f
    val s = saturation.coerceIn(0f, 1f)
    val v = value.coerceIn(0f, 1f)
    if (s == 0f) {
        return Color(v, v, v, alpha)
    }
    val sector = h / 60f
    val i = sector.toInt()
    val f = sector - i
    val p = v * (1f - s)
    val q = v * (1f - s * f)
    val t = v * (1f - s * (1f - f))
    val (r, g, b) = when (i % 6) {
        0 -> Triple(v, t, p)
        1 -> Triple(q, v, p)
        2 -> Triple(p, v, t)
        3 -> Triple(p, q, v)
        4 -> Triple(t, p, v)
        else -> Triple(v, p, q)
    }
    return Color(r, g, b, alpha.coerceIn(0f, 1f))
}

fun formatAlphaPercent(alpha: Float): String {
    return "${(alpha * 100).toInt()}%"
}

fun Color.toHexArgb(): String {
    val a = (alpha * 255f).toInt().coerceIn(0, 255)
    val r = (red * 255f).toInt().coerceIn(0, 255)
    val g = (green * 255f).toInt().coerceIn(0, 255)
    val b = (blue * 255f).toInt().coerceIn(0, 255)
    return String.format("#%02X%02X%02X%02X", a, r, g, b)
}

fun Color.toArgbInt(): Int {
    val a = (alpha * 255f).toInt().coerceIn(0, 255)
    val r = (red * 255f).toInt().coerceIn(0, 255)
    val g = (green * 255f).toInt().coerceIn(0, 255)
    val b = (blue * 255f).toInt().coerceIn(0, 255)
    return (a shl 24) or (r shl 16) or (g shl 8) or b
}

fun Int.toComposeColor(): Color = Color(this)
