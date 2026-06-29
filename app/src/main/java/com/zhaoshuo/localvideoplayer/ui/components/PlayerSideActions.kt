package com.zhaoshuo.localvideoplayer.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.zhaoshuo.localvideoplayer.R

@Composable
internal fun PlayerSideActions(
    controlsLocked: Boolean,
    pipSupported: Boolean,
    isFullscreen: Boolean,
    horizontal: Boolean,
    onBack: () -> Unit,
    onLockToggle: () -> Unit,
    onRotateClick: () -> Unit,
    onPipClick: () -> Unit,
    onFullscreenClick: () -> Unit,
    modifier: Modifier = Modifier,
    showBack: Boolean = true,
) {
    val buttons: @Composable () -> Unit = {
        if (showBack) {
            SideRailButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.back),
                    tint = Color.White,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        SideRailButton(onClick = onLockToggle) {
            Icon(
                imageVector = if (controlsLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                contentDescription = stringResource(
                    if (controlsLocked) R.string.unlock_controls else R.string.lock_controls,
                ),
                tint = if (controlsLocked) Color(0xFFFFD700) else Color.White,
                modifier = Modifier.size(22.dp),
            )
        }
        SideRailButton(onClick = onRotateClick) {
            Icon(
                imageVector = Icons.Default.ScreenRotation,
                contentDescription = stringResource(R.string.rotate_screen),
                tint = Color.White,
                modifier = Modifier.size(22.dp),
            )
        }
        if (pipSupported) {
            SideRailButton(onClick = onPipClick) {
                Icon(
                    imageVector = Icons.Default.PictureInPictureAlt,
                    contentDescription = stringResource(R.string.picture_in_picture),
                    tint = Color.White,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        SideRailButton(onClick = onFullscreenClick) {
            Icon(
                imageVector = if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                contentDescription = stringResource(
                    if (isFullscreen) R.string.exit_fullscreen else R.string.fullscreen,
                ),
                tint = Color.White,
                modifier = Modifier.size(22.dp),
            )
        }
    }

    if (horizontal) {
        Row(
            modifier = modifier,
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            buttons()
        }
    } else {
        Column(
            modifier = modifier,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            buttons()
        }
    }
}

@Composable
internal fun SideRailButton(
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    IconButton(onClick = onClick, modifier = Modifier.size(44.dp)) {
        content()
    }
}
