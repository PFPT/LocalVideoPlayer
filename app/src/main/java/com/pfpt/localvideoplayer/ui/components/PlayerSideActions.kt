package com.pfpt.localvideoplayer.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import com.pfpt.localvideoplayer.ui.theme.LocalAppIcons
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pfpt.localvideoplayer.R
import com.pfpt.localvideoplayer.ui.theme.LocalLumenTheme
import com.pfpt.localvideoplayer.ui.theme.LumenOnVideo

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
    val theme = LocalLumenTheme.current
    val buttons: @Composable () -> Unit = {
        if (showBack) {
            SideRailButton(onClick = onBack) {
                Icon(
                    imageVector = LocalAppIcons.current.arrowBack,
                    contentDescription = stringResource(R.string.back),
                    tint = LumenOnVideo,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        SideRailButton(onClick = onLockToggle) {
            Icon(
                imageVector = if (controlsLocked) LocalAppIcons.current.lock else LocalAppIcons.current.lockOpen,
                contentDescription = stringResource(
                    if (controlsLocked) R.string.unlock_controls else R.string.lock_controls,
                ),
                tint = if (controlsLocked) theme.accent else LumenOnVideo,
                modifier = Modifier.size(22.dp),
            )
        }
        SideRailButton(onClick = onRotateClick) {
            Icon(
                imageVector = LocalAppIcons.current.screenRotation,
                contentDescription = stringResource(R.string.rotate_screen),
                tint = LumenOnVideo,
                modifier = Modifier.size(22.dp),
            )
        }
        if (pipSupported) {
            SideRailButton(onClick = onPipClick) {
                Icon(
                    imageVector = LocalAppIcons.current.pictureInPicture,
                    contentDescription = stringResource(R.string.picture_in_picture),
                    tint = LumenOnVideo,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        SideRailButton(onClick = onFullscreenClick) {
            Icon(
                imageVector = if (isFullscreen) LocalAppIcons.current.fullscreenExit else LocalAppIcons.current.fullscreen,
                contentDescription = stringResource(
                    if (isFullscreen) R.string.exit_fullscreen else R.string.fullscreen,
                ),
                tint = LumenOnVideo,
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
