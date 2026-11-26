package com.pfpt.localvideoplayer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pfpt.localvideoplayer.R
import com.pfpt.localvideoplayer.model.LibraryTypeTab
import com.pfpt.localvideoplayer.ui.theme.LocalAppIcons
import com.pfpt.localvideoplayer.ui.theme.LocalLumenTheme
import com.pfpt.localvideoplayer.ui.theme.LumenChipCorner
import com.pfpt.localvideoplayer.ui.theme.LumenListHorizontalInset
import com.pfpt.localvideoplayer.ui.theme.LumenSpaceSm
import com.pfpt.localvideoplayer.ui.theme.LumenSpaceXs

@Composable
fun LumenLibraryTabBar(
    tabs: List<LibraryTypeTab>,
    selectedTab: LibraryTypeTab,
    onTabSelected: (LibraryTypeTab) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = LumenListHorizontalInset),
) {
    val theme = LocalLumenTheme.current
    val chipShape = RoundedCornerShape(LumenChipCorner)
    LazyRow(
        modifier = modifier,
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(LumenSpaceSm),
    ) {
        items(tabs, key = { it.name }) { tab ->
            val selected = tab == selectedTab
            val label = tabLabel(tab)
            val textColor = if (selected) theme.onBackground else theme.onSurfaceMuted
            val textStyle = if (selected) {
                MaterialTheme.typography.bodyLarge
            } else {
                MaterialTheme.typography.bodyMedium
            }
            val chipModifier = Modifier
                .clip(chipShape)
                .then(
                    if (selected) {
                        Modifier
                            .background(theme.glassFill)
                            .border(1.dp, theme.glassBorder, chipShape)
                    } else {
                        Modifier
                    },
                )
                .clickable { onTabSelected(tab) }
                .padding(horizontal = 14.dp, vertical = LumenSpaceSm)

            Row(
                modifier = chipModifier,
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(LumenSpaceXs),
            ) {
                if (tab == LibraryTypeTab.MARKED) {
                    Icon(
                        imageVector = LocalAppIcons.current.star(selected),
                        contentDescription = null,
                        tint = textColor,
                        modifier = Modifier.size(14.dp),
                    )
                }
                Text(
                    text = label,
                    style = textStyle,
                    color = textColor,
                )
            }
        }
    }
}

@Composable
fun tabLabel(tab: LibraryTypeTab): String {
    return stringResource(
        when (tab) {
            LibraryTypeTab.ALL -> R.string.tab_all
            LibraryTypeTab.EPISODES -> R.string.tab_episodes
            LibraryTypeTab.FOLDERS -> R.string.tab_folders
            LibraryTypeTab.SHORT_CLIPS -> R.string.tab_short_clips
            LibraryTypeTab.SINGLES -> R.string.tab_singles
            LibraryTypeTab.MARKED -> R.string.tab_marked
        },
    )
}
