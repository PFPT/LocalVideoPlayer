package com.pfpt.localvideoplayer.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.clickable
import androidx.compose.ui.unit.dp
import com.pfpt.localvideoplayer.ui.theme.LocalLumenTheme
import com.pfpt.localvideoplayer.ui.theme.LumenChipCorner
import com.pfpt.localvideoplayer.ui.theme.LumenSpaceLg
import com.pfpt.localvideoplayer.ui.theme.LumenSpaceMd
import com.pfpt.localvideoplayer.ui.theme.LumenSpaceSm

@Composable
fun LumenEmptyState(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val theme = LocalLumenTheme.current
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = theme.onSurfaceMuted,
                modifier = Modifier.size(40.dp),
            )
            Spacer(modifier = Modifier.height(LumenSpaceLg))
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = theme.onBackground,
        )
        if (!subtitle.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(LumenSpaceSm))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = theme.onSurfaceMuted,
            )
        }
        if (actionLabel != null && onAction != null) {
            Spacer(modifier = Modifier.height(LumenSpaceLg))
            GlassSurface(
                shape = RoundedCornerShape(LumenChipCorner),
                modifier = Modifier.clickable(onClick = onAction),
            ) {
                Text(
                    text = actionLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = theme.onBackground,
                    modifier = Modifier.padding(horizontal = LumenSpaceLg, vertical = LumenSpaceMd),
                )
            }
        }
    }
}
