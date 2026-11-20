package com.pfpt.localvideoplayer.ui

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
