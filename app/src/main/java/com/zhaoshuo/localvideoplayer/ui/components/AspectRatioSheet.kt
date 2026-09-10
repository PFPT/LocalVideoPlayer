package com.zhaoshuo.localvideoplayer.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.zhaoshuo.localvideoplayer.R
import com.zhaoshuo.localvideoplayer.model.AspectRatioMode
import com.zhaoshuo.localvideoplayer.ui.theme.LocalLumenTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AspectRatioSheet(
    currentMode: AspectRatioMode,
    onModeSelected: (AspectRatioMode) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val theme = LocalLumenTheme.current
    val options = listOf(
        AspectRatioMode.FIT to R.string.aspect_ratio_fit,
        AspectRatioMode.FILL to R.string.aspect_ratio_fill,
        AspectRatioMode.STRETCH to R.string.aspect_ratio_stretch,
        AspectRatioMode.RATIO_16_9 to R.string.aspect_ratio_16_9,
        AspectRatioMode.RATIO_4_3 to R.string.aspect_ratio_4_3,
    )
    LumenModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(modifier = Modifier.padding(bottom = 32.dp)) {
            Text(
                text = stringResource(R.string.aspect_ratio),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            options.forEach { (mode, labelRes) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onModeSelected(mode)
                            onDismiss()
                        }
                        .padding(horizontal = 24.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(labelRes),
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (mode == currentMode) theme.accent else theme.onBackground,
                        modifier = Modifier.weight(1f),
                    )
                    if (mode == currentMode) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = theme.accent,
                        )
                    }
                }
            }
        }
    }
}

fun AspectRatioMode.labelRes(): Int {
    return when (this) {
        AspectRatioMode.FIT -> R.string.aspect_ratio_fit
        AspectRatioMode.FILL -> R.string.aspect_ratio_fill
        AspectRatioMode.STRETCH -> R.string.aspect_ratio_stretch
        AspectRatioMode.RATIO_16_9 -> R.string.aspect_ratio_16_9
        AspectRatioMode.RATIO_4_3 -> R.string.aspect_ratio_4_3
    }
}
