package com.pfpt.localvideoplayer.ui

import android.net.Uri
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.pfpt.localvideoplayer.ui.theme.LocalLumenTheme
import com.pfpt.localvideoplayer.ui.theme.LumenThumbCorner
import com.pfpt.localvideoplayer.util.VideoThumbnailCache
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.yield

fun sharedOpenCorner(
    restingCorner: Dp,
    atRest: Boolean,
): Dp {
    if (atRest) return restingCorner
    return if (restingCorner == 0.dp) LumenThumbCorner else 0.dp
}

enum class FlightPhase {
    None,
    ToPlayer,
    ToList,
}

fun flightPageHidden(
    phase: FlightPhase,
    sourceKnown: Boolean,
    targetKnown: Boolean,
): Boolean {
    return when (phase) {
        FlightPhase.None -> false
        FlightPhase.ToPlayer -> sourceKnown
        FlightPhase.ToList -> sourceKnown && targetKnown
    }
}

fun flightBounds(start: Rect, end: Rect, fraction: Float): Rect {
    return lerp(start, end, fraction)
}

fun flightBoundsSpring(): SpringSpec<Rect> {
    return spring(
        stiffness = Spring.StiffnessMediumLow,
        visibilityThreshold = Rect.VisibilityThreshold,
    )
}

class PlayerFlightState {
    var phase by mutableStateOf(FlightPhase.None)
        private set
    var activeUri by mutableStateOf<String?>(null)
        private set
    var source by mutableStateOf<Rect?>(null)
        private set
    var target by mutableStateOf<Rect?>(null)
        private set
    var thumbnailDateModified by mutableStateOf(0L)
        private set

    private val sources = mutableMapOf<String, Rect>()
    private val dates = mutableMapOf<String, Long>()
    private val holders = mutableMapOf<String, Int>()
    private var returnWatchUri: String? = null

    val pageHidden: Boolean
        get() = flightPageHidden(
            phase = phase,
            sourceKnown = source != null,
            targetKnown = target != null,
        )

    fun acquireSource(uri: String) {
        holders[uri] = (holders[uri] ?: 0) + 1
    }

    fun releaseSource(uri: String) {
        val next = (holders[uri] ?: 1) - 1
        if (next <= 0) {
            holders.remove(uri)
            sources.remove(uri)
            dates.remove(uri)
        } else {
            holders[uri] = next
        }
    }

    fun reportSource(uri: String, rect: Rect, dateModifiedSeconds: Long) {
        val stable = rect.stable()
        sources[uri] = stable
        dates[uri] = dateModifiedSeconds
        if (returnWatchUri == uri && target != null) {
            source = stable
            activeUri = uri
            thumbnailDateModified = dateModifiedSeconds
            phase = FlightPhase.ToList
            returnWatchUri = null
        } else if (phase == FlightPhase.ToList && activeUri == uri && source != stable) {
            source = stable
            thumbnailDateModified = dateModifiedSeconds
        }
    }

    fun reportTarget(rect: Rect) {
        val stable = rect.stable()
        if (target == stable) return
        target = stable
    }

    fun clearTarget() {
        if (phase != FlightPhase.None || returnWatchUri != null) return
        target = null
    }

    fun beginOpen(uri: String, rect: Rect, dateModifiedSeconds: Long) {
        activeUri = uri
        source = rect.stable()
        thumbnailDateModified = dateModifiedSeconds
        target = null
        returnWatchUri = null
        phase = FlightPhase.ToPlayer
    }

    fun beginReturn(uri: String) {
        if (phase != FlightPhase.None) {
            finish()
            return
        }
        if (target == null) return
        returnWatchUri = uri
    }

    fun settleReturnLayout() {
        val uri = returnWatchUri ?: return
        val rect = sources[uri]
        val frame = target
        returnWatchUri = null
        if (rect != null && frame != null) {
            source = rect
            activeUri = uri
            thumbnailDateModified = dates[uri] ?: 0L
            phase = FlightPhase.ToList
        } else {
            target = null
        }
    }

    fun finish() {
        val returning = phase == FlightPhase.ToList
        phase = FlightPhase.None
        activeUri = null
        source = null
        returnWatchUri = null
        if (returning) {
            target = null
        }
    }

    fun hidesThumbnail(uri: String): Boolean {
        return phase != FlightPhase.None && activeUri == uri
    }
}

private val LocalPlayerFlight = staticCompositionLocalOf<PlayerFlightState?> { null }

@Composable
fun currentPlayerFlight(): PlayerFlightState? = LocalPlayerFlight.current

@Composable
fun ProvidePlayerFlight(
    flight: PlayerFlightState,
    content: @Composable () -> Unit,
) {
    androidx.compose.runtime.CompositionLocalProvider(
        LocalPlayerFlight provides flight,
        content = content,
    )
}

class PlayerSharedOpen internal constructor(
    val contentKey: String?,
    val onClick: () -> Unit,
    val onMeasured: (Rect) -> Unit,
)

@Composable
fun rememberPlayerSharedOpen(
    candidateKey: String?,
    opensPlayer: Boolean,
    dateModifiedSeconds: Long = 0L,
    onClick: () -> Unit,
): PlayerSharedOpen {
    val flight = LocalPlayerFlight.current
    val contentKey = if (opensPlayer) candidateKey else null
    var rect by remember { mutableStateOf<Rect?>(null) }
    val click = {
        val bounds = rect
        if (contentKey != null && bounds != null) {
            flight?.beginOpen(contentKey, bounds, dateModifiedSeconds)
        }
        onClick()
    }
    return PlayerSharedOpen(
        contentKey = contentKey,
        onClick = click,
        onMeasured = { rect = it },
    )
}

@Composable
fun Modifier.trackFlightSource(
    uri: String?,
    dateModifiedSeconds: Long,
    onMeasured: ((Rect) -> Unit)?,
): Modifier {
    val flight = LocalPlayerFlight.current
    if (uri == null || flight == null) return this
    DisposableEffect(uri) {
        flight.acquireSource(uri)
        onDispose { flight.releaseSource(uri) }
    }
    return onGloballyPositioned { coords ->
        val rect = coords.boundsInRoot()
        flight.reportSource(uri, rect, dateModifiedSeconds)
        onMeasured?.invoke(rect)
    }
}

@Composable
fun Modifier.reportFlightTarget(enabled: Boolean): Modifier {
    val flight = LocalPlayerFlight.current ?: return this
    if (!enabled) return this
    DisposableEffect(Unit) {
        onDispose { flight.clearTarget() }
    }
    return onGloballyPositioned { coords ->
        flight.reportTarget(coords.boundsInRoot())
    }
}

@Composable
fun flightHidesThumbnail(uri: String?): Boolean {
    if (uri == null) return false
    val flight = LocalPlayerFlight.current ?: return false
    return flight.hidesThumbnail(uri)
}

@Composable
fun playerTransitionActive(): Boolean {
    val flight = LocalPlayerFlight.current ?: return false
    return flight.pageHidden
}

fun hidePlayerSurfaceForTransition(
    useTextureView: Boolean,
    isCasting: Boolean,
    transitionActive: Boolean,
): Boolean {
    if (useTextureView) return false
    if (isCasting) return false
    return transitionActive
}

fun hidePlayerPageForTransition(transitionActive: Boolean): Boolean {
    return transitionActive
}

@Composable
fun PlayerFlightOverlay(
    flight: PlayerFlightState,
    modifier: Modifier = Modifier,
) {
    val phase = flight.phase
    val activeUri = flight.activeUri
    val from = when (phase) {
        FlightPhase.ToPlayer -> flight.source
        FlightPhase.ToList -> flight.target
        FlightPhase.None -> null
    }
    val bounds = remember(phase, activeUri) {
        Animatable(from ?: Rect.Zero, Rect.VectorConverter)
    }
    val corner = remember(phase, activeUri) {
        Animatable(
            if (phase == FlightPhase.None) 0.dp else flightCorner(phase, atStart = true),
            Dp.VectorConverter,
        )
    }
    LaunchedEffect(phase, activeUri) {
        val phase = flight.phase
        if (phase == FlightPhase.None) return@LaunchedEffect
        val from = when (phase) {
            FlightPhase.ToPlayer -> flight.source
            FlightPhase.ToList -> flight.target
            FlightPhase.None -> null
        } ?: return@LaunchedEffect
        val fromCorner = flightCorner(phase, atStart = true)
        val toCorner = flightCorner(phase, atStart = false)
        bounds.snapTo(from)
        corner.snapTo(fromCorner)
        supervisorScope {
            val boundsJob = launch {
                snapshotFlow { flightDestination(flight) }
                    .filterNotNull()
                    .collectLatest { destination ->
                        bounds.animateTo(destination, flightBoundsSpring())
                    }
            }
            val cornerJob = launch {
                snapshotFlow { flightDestination(flight) }.filterNotNull().first()
                corner.animateTo(toCorner, spring())
            }
            yield()
            snapshotFlow {
                val destination = flightDestination(flight)
                destination != null &&
                    !bounds.isRunning &&
                    !corner.isRunning &&
                    bounds.value.reached(destination)
            }.first { it }
            cornerJob.cancel()
            boundsJob.cancel()
        }
        if (flight.phase == phase && flight.activeUri != null) {
            flight.finish()
        }
    }
    if (flight.phase == FlightPhase.None) return
    val rect = bounds.value
    if (rect.width <= 0f || rect.height <= 0f) return
    val theme = LocalLumenTheme.current
    val density = LocalDensity.current
    val bitmap = flight.activeUri?.let { uri ->
        VideoThumbnailCache.peek(Uri.parse(uri), flight.thumbnailDateModified)
    }
    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .offset {
                    IntOffset(rect.left.roundToInt(), rect.top.roundToInt())
                }
                .size(
                    with(density) { rect.width.toDp() },
                    with(density) { rect.height.toDp() },
                )
                .clip(RoundedCornerShape(corner.value))
                .background(theme.surface),
            contentAlignment = Alignment.Center,
        ) {
            FlightThumbnail(bitmap = bitmap)
        }
    }
}

@Composable
private fun FlightThumbnail(bitmap: ImageBitmap?) {
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        return
    }
    val theme = LocalLumenTheme.current
    Icon(
        imageVector = Icons.Filled.Movie,
        contentDescription = null,
        tint = theme.onSurfaceMuted.copy(alpha = 0.35f),
        modifier = Modifier.size(28.dp),
    )
}

private fun flightDestination(flight: PlayerFlightState): Rect? {
    return when (flight.phase) {
        FlightPhase.ToPlayer -> flight.target
        FlightPhase.ToList -> flight.source
        FlightPhase.None -> null
    }
}

private fun flightCorner(phase: FlightPhase, atStart: Boolean): Dp {
    return when (phase) {
        FlightPhase.ToPlayer -> if (atStart) {
            sharedOpenCorner(LumenThumbCorner, atRest = true)
        } else {
            sharedOpenCorner(0.dp, atRest = true)
        }
        FlightPhase.ToList -> if (atStart) {
            sharedOpenCorner(0.dp, atRest = true)
        } else {
            sharedOpenCorner(LumenThumbCorner, atRest = true)
        }
        FlightPhase.None -> 0.dp
    }
}

private fun Rect.reached(target: Rect): Boolean {
    return kotlin.math.abs(left - target.left) <= 1f &&
        kotlin.math.abs(top - target.top) <= 1f &&
        kotlin.math.abs(right - target.right) <= 1f &&
        kotlin.math.abs(bottom - target.bottom) <= 1f
}

private fun Rect.stable(): Rect {
    return Rect(
        left.roundToInt().toFloat(),
        top.roundToInt().toFloat(),
        right.roundToInt().toFloat(),
        bottom.roundToInt().toFloat(),
    )
}
