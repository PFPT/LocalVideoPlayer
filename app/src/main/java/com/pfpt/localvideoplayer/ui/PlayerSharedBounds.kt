package com.pfpt.localvideoplayer.ui

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier

@OptIn(ExperimentalSharedTransitionApi::class)
private val LocalPlayerSharedTransition = staticCompositionLocalOf<SharedTransitionScope?> { null }

private val LocalPlayerAnimatedVisibility = staticCompositionLocalOf<AnimatedVisibilityScope?> { null }

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun ProvidePlayerSharedTransition(
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalPlayerSharedTransition provides sharedTransitionScope,
        LocalPlayerAnimatedVisibility provides animatedVisibilityScope,
        content = content,
    )
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.playerSharedBounds(contentKey: String?): Modifier {
    if (contentKey == null) return this
    val sharedTransitionScope = LocalPlayerSharedTransition.current ?: return this
    val animatedVisibilityScope = LocalPlayerAnimatedVisibility.current ?: return this
    return with(sharedTransitionScope) {
        sharedBounds(
            sharedContentState = rememberSharedContentState(key = contentKey),
            animatedVisibilityScope = animatedVisibilityScope,
        )
    }
}

class PlayerSharedSource {
    var activeId by mutableStateOf<Any?>(null)
}

private val LocalPlayerSharedSource = staticCompositionLocalOf<PlayerSharedSource?> { null }

@Composable
fun ProvidePlayerSharedSource(content: @Composable () -> Unit) {
    val source = remember { PlayerSharedSource() }
    CompositionLocalProvider(LocalPlayerSharedSource provides source, content = content)
}

class PlayerSharedOpen internal constructor(
    val contentKey: String?,
    val onClick: () -> Unit,
)

@Composable
fun rememberPlayerSharedOpen(
    candidateKey: String?,
    opensPlayer: Boolean,
    onClick: () -> Unit,
): PlayerSharedOpen {
    val source = LocalPlayerSharedSource.current
    val id = remember { Any() }
    var pending by remember { mutableStateOf(false) }
    LaunchedEffect(pending) {
        if (!pending) return@LaunchedEffect
        pending = false
        onClick()
    }
    val contentKey = if (opensPlayer && candidateKey != null && source?.activeId === id) {
        candidateKey
    } else {
        null
    }
    val click = {
        if (!opensPlayer || candidateKey == null || source == null) {
            onClick()
        } else {
            source.activeId = id
            pending = true
        }
    }
    return PlayerSharedOpen(contentKey, click)
}
