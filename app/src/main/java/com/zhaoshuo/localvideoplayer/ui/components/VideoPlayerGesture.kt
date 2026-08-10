package com.zhaoshuo.localvideoplayer.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalViewConfiguration
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import kotlin.math.hypot

private const val DOUBLE_TAP_TIMEOUT_MS = 300L
private const val MIN_SCALE = 1f
private const val MAX_SCALE = 3f
private const val PREVIEW_FRAME_THROTTLE_MS = 100L
private const val SEEK_FULL_SWIPE_MS = 10 * 60_000L
private const val VERTICAL_SENSITIVITY = 1f

private enum class DragMode {
    Seek,
    Brightness,
    Volume,
}

@Composable
fun VideoPlayerGesture(
    scale: Float,
    offsetX: Float,
    offsetY: Float,
    onScaleChange: (Float) -> Unit,
    onOffsetChange: (Float, Float) -> Unit,
    getCurrentPositionMs: () -> Long,
    durationMs: Long,
    onSeekPreview: (Long) -> Unit,
    onSeekPreviewFrame: (Long) -> Unit,
    onSeekCommitted: (Long) -> Unit,
    onTap: (Offset, Float, Float) -> Unit,
    onDoubleTap: () -> Unit,
    onSeekingChange: (Boolean) -> Unit,
    onBrightnessGestureStart: (() -> Unit)? = null,
    onBrightnessChange: ((Float) -> Unit)? = null,
    onBrightnessGestureEnd: (() -> Unit)? = null,
    onVolumeGestureStart: (() -> Unit)? = null,
    onVolumeChange: ((Float) -> Unit)? = null,
    onVolumeGestureEnd: (() -> Unit)? = null,
    getCurrentBrightness: () -> Float = { 0.5f },
    getCurrentVolume: () -> Float = { 0.5f },
    onSpeedBoostStart: (() -> Unit)? = null,
    onSpeedBoostEnd: (() -> Unit)? = null,
    enabled: Boolean,
    speedBoostOnly: Boolean = false,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val viewConfiguration = LocalViewConfiguration.current
    val touchSlop = viewConfiguration.touchSlop
    val longPressTimeout = viewConfiguration.longPressTimeoutMillis

    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offsetX
                translationY = offsetY
            }
            .then(
                when {
                    enabled && speedBoostOnly -> {
                        Modifier.pointerInput(
                            longPressTimeout,
                            touchSlop,
                            onSpeedBoostStart,
                            onSpeedBoostEnd,
                        ) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                val pointerId = down.id
                                var fingerUpBeforeTimeout = false
                                var movedBeyondSlop = false

                                val timedOutWithoutMove = withTimeoutOrNull(longPressTimeout) {
                                    while (true) {
                                        val event = awaitPointerEvent(PointerEventPass.Main)
                                        val change = event.changes.firstOrNull { it.id == pointerId }
                                            ?: return@withTimeoutOrNull false
                                        if (!change.pressed) {
                                            fingerUpBeforeTimeout = true
                                            return@withTimeoutOrNull false
                                        }
                                        val delta = change.position - down.position
                                        if (abs(delta.x) > touchSlop || abs(delta.y) > touchSlop) {
                                            movedBeyondSlop = true
                                            return@withTimeoutOrNull false
                                        }
                                    }
                                    @Suppress("UNREACHABLE_CODE")
                                    false
                                } != false

                                when {
                                    fingerUpBeforeTimeout -> {
                                        onTap(
                                            down.position,
                                            size.width.toFloat(),
                                            size.height.toFloat(),
                                        )
                                    }
                                    timedOutWithoutMove &&
                                        !movedBeyondSlop &&
                                        onSpeedBoostStart != null &&
                                        onSpeedBoostEnd != null -> {
                                        try {
                                            onSpeedBoostStart.invoke()
                                            while (true) {
                                                val event = awaitPointerEvent(PointerEventPass.Main)
                                                val change = event.changes.firstOrNull { it.id == pointerId }
                                                    ?: break
                                                if (!change.pressed) break
                                                change.consume()
                                            }
                                        } finally {
                                            onSpeedBoostEnd.invoke()
                                        }
                                    }
                                }
                            }
                        }
                    }
                    enabled -> {
                        Modifier
                            .pointerInput(scale) {
                                detectTransformGestures { _, pan, zoom, _ ->
                                    if (zoom != 1f) {
                                        val newScale = (scale * zoom).coerceIn(MIN_SCALE, MAX_SCALE)
                                        onScaleChange(newScale)
                                        if (newScale <= MIN_SCALE) {
                                            onOffsetChange(0f, 0f)
                                        }
                                    } else if (scale > MIN_SCALE) {
                                        val maxOffsetX = (size.width * (scale - 1f)) / 2f
                                        val maxOffsetY = (size.height * (scale - 1f)) / 2f
                                        onOffsetChange(
                                            (offsetX + pan.x).coerceIn(-maxOffsetX, maxOffsetX),
                                            (offsetY + pan.y).coerceIn(-maxOffsetY, maxOffsetY),
                                        )
                                    }
                                }
                            }
                            .pointerInput(scale) {
                                coroutineScope {
                                if (scale > MIN_SCALE) {
                                    detectTapGestures(
                                        onTap = { offset ->
                                            onTap(offset, size.width.toFloat(), size.height.toFloat())
                                        },
                                        onDoubleTap = { onDoubleTap() },
                                    )
                                    return@coroutineScope
                                }

                                var lastTapUptimeMs = 0L
                                var lastTapPosition = Offset.Zero
                                var pendingSingleTapJob: Job? = null

                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    val pointerId = down.id
                                    var dragMode: DragMode? = null
                                    var fingerUpBeforeSlop = false
                                    val startPositionMs = getCurrentPositionMs()
                                    var startBrightness = 0f
                                    var startVolume = 0f
                                    var previewPositionMs = startPositionMs
                                    var lastPreviewFrameAt = 0L

                                    val timedOutWithoutDrag = withTimeoutOrNull(longPressTimeout) {
                                        while (dragMode == null) {
                                            val event = awaitPointerEvent(PointerEventPass.Main)
                                            val change = event.changes.firstOrNull { it.id == pointerId }
                                                ?: return@withTimeoutOrNull false
                                            if (!change.pressed) {
                                                fingerUpBeforeSlop = true
                                                return@withTimeoutOrNull false
                                            }

                                            val deltaFromStart = change.position - down.position
                                            if (abs(deltaFromStart.x) > touchSlop || abs(deltaFromStart.y) > touchSlop) {
                                                dragMode = resolveDragMode(
                                                    deltaFromStart = deltaFromStart,
                                                    downX = down.position.x,
                                                    width = size.width.toFloat(),
                                                    durationMs = durationMs,
                                                    onBrightnessAvailable = onBrightnessGestureStart != null &&
                                                        onBrightnessChange != null,
                                                    onVolumeAvailable = onVolumeGestureStart != null &&
                                                        onVolumeChange != null,
                                                )
                                                if (dragMode == null) {
                                                    return@withTimeoutOrNull false
                                                }
                                                when (val mode = dragMode!!) {
                                                    DragMode.Seek -> onSeekingChange(true)
                                                    DragMode.Brightness -> {
                                                        startBrightness = getCurrentBrightness()
                                                        onBrightnessGestureStart?.invoke()
                                                    }
                                                    DragMode.Volume -> {
                                                        startVolume = getCurrentVolume()
                                                        onVolumeGestureStart?.invoke()
                                                    }
                                                }
                                            }
                                        }
                                        false
                                    } ?: true

                                    when {
                                        fingerUpBeforeSlop -> {
                                            val now = System.currentTimeMillis()
                                            val position = down.position
                                            val isDoubleTap = lastTapUptimeMs > 0L &&
                                                now - lastTapUptimeMs < DOUBLE_TAP_TIMEOUT_MS &&
                                                hypot(
                                                    position.x - lastTapPosition.x,
                                                    position.y - lastTapPosition.y,
                                                ) < touchSlop
                                            if (isDoubleTap) {
                                                pendingSingleTapJob?.cancel()
                                                pendingSingleTapJob = null
                                                lastTapUptimeMs = 0L
                                                onDoubleTap()
                                            } else {
                                                lastTapUptimeMs = now
                                                lastTapPosition = position
                                                pendingSingleTapJob?.cancel()
                                                pendingSingleTapJob = this@coroutineScope.launch {
                                                    delay(DOUBLE_TAP_TIMEOUT_MS)
                                                    onTap(
                                                        position,
                                                        size.width.toFloat(),
                                                        size.height.toFloat(),
                                                    )
                                                    lastTapUptimeMs = 0L
                                                }
                                            }
                                        }
                                        dragMode != null -> {
                                            while (true) {
                                                val event = awaitPointerEvent(PointerEventPass.Main)
                                                val change = event.changes.firstOrNull { it.id == pointerId }
                                                    ?: break
                                                if (!change.pressed) break

                                                val delta = change.position - down.position
                                                val result = applyDragDelta(
                                                    dragMode = dragMode!!,
                                                    delta = delta,
                                                    width = size.width.toFloat(),
                                                    height = size.height.toFloat(),
                                                    startPositionMs = startPositionMs,
                                                    startBrightness = startBrightness,
                                                    startVolume = startVolume,
                                                    durationMs = durationMs,
                                                    onSeekPreview = onSeekPreview,
                                                    onSeekPreviewFrame = onSeekPreviewFrame,
                                                    onBrightnessChange = onBrightnessChange,
                                                    onVolumeChange = onVolumeChange,
                                                    lastPreviewFrameAt = lastPreviewFrameAt,
                                                )
                                                previewPositionMs = result.previewPositionMs
                                                lastPreviewFrameAt = result.lastPreviewFrameAt
                                                change.consume()
                                            }
                                            finishDragMode(
                                                dragMode = dragMode!!,
                                                previewPositionMs = previewPositionMs,
                                                onSeekCommitted = onSeekCommitted,
                                                onSeekingChange = onSeekingChange,
                                                onBrightnessGestureEnd = onBrightnessGestureEnd,
                                                onVolumeGestureEnd = onVolumeGestureEnd,
                                            )
                                        }
                                        timedOutWithoutDrag &&
                                            onSpeedBoostStart != null &&
                                            onSpeedBoostEnd != null -> {
                                            try {
                                                onSpeedBoostStart()
                                                while (true) {
                                                    val event = awaitPointerEvent(PointerEventPass.Main)
                                                    val change = event.changes.firstOrNull { it.id == pointerId }
                                                        ?: break
                                                    if (!change.pressed) break
                                                    change.consume()
                                                }
                                            } finally {
                                                onSpeedBoostEnd()
                                            }
                                        }
                                    }
                                }
                                }
                            }
                    }
                    else -> {
                        Modifier.pointerInput(Unit) {
                            detectTapGestures(
                                onTap = { offset ->
                                    onTap(offset, size.width.toFloat(), size.height.toFloat())
                                },
                            )
                        }
                    }
                },
            ),
    ) {
        content()
    }
}

private fun resolveDragMode(
    deltaFromStart: Offset,
    downX: Float,
    width: Float,
    durationMs: Long,
    onBrightnessAvailable: Boolean,
    onVolumeAvailable: Boolean,
): DragMode? {
    return when {
        abs(deltaFromStart.x) >= abs(deltaFromStart.y) && durationMs > 0 -> DragMode.Seek
        downX <= width / 2f && onBrightnessAvailable -> DragMode.Brightness
        downX > width / 2f && onVolumeAvailable -> DragMode.Volume
        else -> null
    }
}

private data class DragApplyResult(
    val previewPositionMs: Long,
    val lastPreviewFrameAt: Long,
)

private fun applyDragDelta(
    dragMode: DragMode,
    delta: Offset,
    width: Float,
    height: Float,
    startPositionMs: Long,
    startBrightness: Float,
    startVolume: Float,
    durationMs: Long,
    onSeekPreview: (Long) -> Unit,
    onSeekPreviewFrame: (Long) -> Unit,
    onBrightnessChange: ((Float) -> Unit)?,
    onVolumeChange: ((Float) -> Unit)?,
    lastPreviewFrameAt: Long,
): DragApplyResult {
    return when (dragMode) {
        DragMode.Seek -> {
            val seekRangeMs = minOf(durationMs, SEEK_FULL_SWIPE_MS)
            val deltaMs = (delta.x / width) * seekRangeMs
            val previewMs = (startPositionMs + deltaMs.toLong()).coerceIn(0L, durationMs)
            onSeekPreview(previewMs)
            var frameAt = lastPreviewFrameAt
            val now = System.currentTimeMillis()
            if (now - frameAt >= PREVIEW_FRAME_THROTTLE_MS) {
                onSeekPreviewFrame(previewMs)
                frameAt = now
            }
            DragApplyResult(previewMs, frameAt)
        }
        DragMode.Brightness -> {
            val fraction = (startBrightness - (delta.y / height) * VERTICAL_SENSITIVITY)
                .coerceIn(0f, 1f)
            onBrightnessChange?.invoke(fraction)
            DragApplyResult(startPositionMs, lastPreviewFrameAt)
        }
        DragMode.Volume -> {
            val fraction = (startVolume - (delta.y / height) * VERTICAL_SENSITIVITY)
                .coerceIn(0f, 1f)
            onVolumeChange?.invoke(fraction)
            DragApplyResult(startPositionMs, lastPreviewFrameAt)
        }
    }
}

private fun finishDragMode(
    dragMode: DragMode,
    previewPositionMs: Long,
    onSeekCommitted: (Long) -> Unit,
    onSeekingChange: (Boolean) -> Unit,
    onBrightnessGestureEnd: (() -> Unit)?,
    onVolumeGestureEnd: (() -> Unit)?,
) {
    when (dragMode) {
        DragMode.Seek -> {
            onSeekCommitted(previewPositionMs)
            onSeekingChange(false)
        }
        DragMode.Brightness -> onBrightnessGestureEnd?.invoke()
        DragMode.Volume -> onVolumeGestureEnd?.invoke()
    }
}
