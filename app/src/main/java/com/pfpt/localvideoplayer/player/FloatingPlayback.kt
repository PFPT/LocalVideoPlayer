package com.pfpt.localvideoplayer.player

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object FloatingPlayback {
    private val showing = MutableStateFlow(false)
    val visible: StateFlow<Boolean> = showing.asStateFlow()

    fun isShowing(): Boolean = showing.value

    fun setShowing(value: Boolean) {
        showing.value = value
    }
}

fun shouldSuppressVideo(
    onPlayerScreen: Boolean,
    hostStarted: Boolean,
    floating: Boolean,
): Boolean {
    if (floating) return false
    return !onPlayerScreen || !hostStarted
}

fun floatingWindowSize(
    screenWidth: Int,
    screenHeight: Int,
    videoWidth: Int,
    videoHeight: Int,
): Pair<Int, Int> {
    val shortSide = minOf(screenWidth, screenHeight).coerceAtLeast(1)
    var width = (shortSide * 0.45f).toInt().coerceAtLeast(1)
    val ratio = if (videoWidth > 0 && videoHeight > 0) {
        (videoWidth.toFloat() / videoHeight).coerceIn(100f / 239f, 239f / 100f)
    } else {
        16f / 9f
    }
    var height = (width / ratio).toInt().coerceAtLeast(1)
    val maxWidth = (screenWidth * 0.8f).toInt().coerceAtLeast(1)
    val maxHeight = (screenHeight * 0.8f).toInt().coerceAtLeast(1)
    if (width > maxWidth || height > maxHeight) {
        val scale = minOf(maxWidth.toFloat() / width, maxHeight.toFloat() / height)
        width = (width * scale).toInt().coerceAtLeast(1)
        height = (height * scale).toInt().coerceAtLeast(1)
    }
    return width to height
}

fun clampWindowOffset(value: Int, min: Int, max: Int): Int {
    if (max < min) return min
    return value.coerceIn(min, max)
}

enum class FloatingCorner {
    TopLeft,
    TopRight,
    BottomLeft,
    BottomRight,
}

data class FloatingFrame(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
)

fun resizeFloatingFrame(
    origin: FloatingFrame,
    corner: FloatingCorner,
    fingerX: Int,
    fingerY: Int,
    ratio: Float,
    minWidth: Int,
    screenWidth: Int,
    screenHeight: Int,
): FloatingFrame {
    val safeRatio = if (ratio.isFinite() && ratio > 0f) ratio else 16f / 9f
    val fixedX = when (corner) {
        FloatingCorner.TopLeft, FloatingCorner.BottomLeft -> origin.x + origin.width
        FloatingCorner.TopRight, FloatingCorner.BottomRight -> origin.x
    }
    val fixedY = when (corner) {
        FloatingCorner.TopLeft, FloatingCorner.TopRight -> origin.y + origin.height
        FloatingCorner.BottomLeft, FloatingCorner.BottomRight -> origin.y
    }
    val rawWidth = kotlin.math.abs(fingerX - fixedX).coerceAtLeast(1)
    val rawHeight = kotlin.math.abs(fingerY - fixedY).coerceAtLeast(1)
    var width: Int
    var height: Int
    if (rawWidth / rawHeight.toFloat() > safeRatio) {
        height = rawHeight
        width = (height * safeRatio).toInt().coerceAtLeast(1)
    } else {
        width = rawWidth
        height = (width / safeRatio).toInt().coerceAtLeast(1)
    }
    val minW = minWidth.coerceAtMost(screenWidth.coerceAtLeast(1)).coerceAtLeast(1)
    if (width < minW) {
        width = minW
        height = (width / safeRatio).toInt().coerceAtLeast(1)
    }
    val maxWidth = when (corner) {
        FloatingCorner.TopLeft, FloatingCorner.BottomLeft -> fixedX
        FloatingCorner.TopRight, FloatingCorner.BottomRight -> screenWidth - fixedX
    }.coerceAtLeast(1)
    val maxHeight = when (corner) {
        FloatingCorner.TopLeft, FloatingCorner.TopRight -> fixedY
        FloatingCorner.BottomLeft, FloatingCorner.BottomRight -> screenHeight - fixedY
    }.coerceAtLeast(1)
    if (width > maxWidth || height > maxHeight) {
        val scale = minOf(maxWidth.toFloat() / width, maxHeight.toFloat() / height)
        width = (width * scale).toInt().coerceAtLeast(1)
        height = (height * scale).toInt().coerceAtLeast(1)
    }
    val x = when (corner) {
        FloatingCorner.TopLeft, FloatingCorner.BottomLeft -> fixedX - width
        FloatingCorner.TopRight, FloatingCorner.BottomRight -> fixedX
    }
    val y = when (corner) {
        FloatingCorner.TopLeft, FloatingCorner.TopRight -> fixedY - height
        FloatingCorner.BottomLeft, FloatingCorner.BottomRight -> fixedY
    }
    return FloatingFrame(x, y, width, height)
}

enum class FloatingPointerAction {
    ToggleControls,
    OpenPlayer,
    Move,
}

fun floatingPointerAction(
    movedPastSlop: Boolean,
    secondTapWithinTimeout: Boolean,
): FloatingPointerAction {
    if (movedPastSlop) return FloatingPointerAction.Move
    if (secondTapWithinTimeout) return FloatingPointerAction.OpenPlayer
    return FloatingPointerAction.ToggleControls
}

fun floatingMinWidthPx(screenShortSidePx: Int, density: Float): Int {
    val fromDp = (120f * density).toInt()
    val fromScreen = (screenShortSidePx.coerceAtLeast(1) * 0.28f).toInt()
    return minOf(fromDp, fromScreen).coerceAtLeast(1)
}

fun floatingCornerHandle(
    parentWidth: Int,
    parentHeight: Int,
    handleSize: Int,
    corner: FloatingCorner,
): FloatingFrame {
    val size = handleSize.coerceAtLeast(1)
    val left = if (corner == FloatingCorner.TopLeft || corner == FloatingCorner.BottomLeft) {
        0
    } else {
        (parentWidth - size).coerceAtLeast(0)
    }
    val top = if (corner == FloatingCorner.TopLeft || corner == FloatingCorner.TopRight) {
        0
    } else {
        (parentHeight - size).coerceAtLeast(0)
    }
    return FloatingFrame(
        x = left,
        y = top,
        width = size.coerceAtMost(parentWidth.coerceAtLeast(1)),
        height = size.coerceAtMost(parentHeight.coerceAtLeast(1)),
    )
}

fun fitFloatingFrame(
    frame: FloatingFrame,
    screenWidth: Int,
    screenHeight: Int,
): FloatingFrame {
    var width = frame.width.coerceAtLeast(1)
    var height = frame.height.coerceAtLeast(1)
    if (width > screenWidth || height > screenHeight) {
        val scale = minOf(
            screenWidth.coerceAtLeast(1).toFloat() / width,
            screenHeight.coerceAtLeast(1).toFloat() / height,
        )
        width = (width * scale).toInt().coerceAtLeast(1)
        height = (height * scale).toInt().coerceAtLeast(1)
    }
    return FloatingFrame(
        x = clampWindowOffset(frame.x, 0, screenWidth - width),
        y = clampWindowOffset(frame.y, 0, screenHeight - height),
        width = width,
        height = height,
    )
}
