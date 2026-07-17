package com.zhaoshuo.localvideoplayer.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.zhaoshuo.localvideoplayer.R
import com.zhaoshuo.localvideoplayer.model.DateFilter
import com.zhaoshuo.localvideoplayer.model.DurationFilter
import com.zhaoshuo.localvideoplayer.model.LibraryFilter
import com.zhaoshuo.localvideoplayer.model.LibraryViewLayout
import com.zhaoshuo.localvideoplayer.model.VideoSortOrder
import com.zhaoshuo.localvideoplayer.ui.theme.LocalLumenTheme
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryOptionsBottomSheet(
    currentOrder: VideoSortOrder,
    viewLayout: LibraryViewLayout,
    libraryFilter: LibraryFilter,
    collapseSingles: Boolean,
    onOrderSelected: (VideoSortOrder) -> Unit,
    onViewLayoutSelected: (LibraryViewLayout) -> Unit,
    onFilterChanged: (LibraryFilter) -> Unit,
    onCollapseSinglesChanged: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val theme = LocalLumenTheme.current
    val sortOptions = listOf(
        VideoSortOrder.DATE_DESC to R.string.sort_date_desc,
        VideoSortOrder.DATE_ASC to R.string.sort_date_asc,
        VideoSortOrder.NAME_ASC to R.string.sort_name_asc,
        VideoSortOrder.NAME_DESC to R.string.sort_name_desc,
        VideoSortOrder.DURATION_DESC to R.string.sort_duration_desc,
        VideoSortOrder.DURATION_ASC to R.string.sort_duration_asc,
        VideoSortOrder.POPULARITY_DESC to R.string.sort_popularity_desc,
    )
    val layoutOptions = listOf(
        LibraryViewLayout.SECTIONED to R.string.view_sectioned,
        LibraryViewLayout.FLAT to R.string.view_flat,
    )
    val durationOptions = listOf(
        DurationFilter.ALL to R.string.filter_duration_all,
        DurationFilter.SHORT to R.string.filter_duration_short,
        DurationFilter.MEDIUM to R.string.filter_duration_medium,
        DurationFilter.LONG to R.string.filter_duration_long,
        DurationFilter.MIN_20 to R.string.filter_duration_min_20,
        DurationFilter.MIN_25 to R.string.filter_duration_min_25,
    )
    val dateOptions = listOf(
        DateFilter.ALL to R.string.filter_date_all,
        DateFilter.TODAY to R.string.filter_date_today,
        DateFilter.WEEK to R.string.filter_date_week,
        DateFilter.MONTH to R.string.filter_date_month,
        DateFilter.YEAR to R.string.filter_date_year,
    )

    fun dismissSheet(action: () -> Unit = {}) {
        scope.launch {
            sheetState.hide()
            action()
            onDismiss()
        }
    }

    LumenModalBottomSheet(
        onDismissRequest = { dismissSheet() },
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                text = stringResource(R.string.library_options),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )

            OptionsSectionTitle(stringResource(R.string.view_layout))
            layoutOptions.forEach { (layout, labelRes) ->
                SelectableOptionRow(
                    label = stringResource(labelRes),
                    selected = viewLayout == layout,
                    onClick = { onViewLayoutSelected(layout) },
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            OptionsSectionTitle(stringResource(R.string.sort))
            sortOptions.forEach { (order, labelRes) ->
                SelectableOptionRow(
                    label = stringResource(labelRes),
                    selected = currentOrder == order,
                    onClick = { dismissSheet { onOrderSelected(order) } },
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            OptionsSectionTitle(stringResource(R.string.filter))
            durationOptions.forEach { (duration, labelRes) ->
                SelectableOptionRow(
                    label = stringResource(labelRes),
                    selected = libraryFilter.duration == duration,
                    onClick = {
                        onFilterChanged(libraryFilter.copy(duration = duration))
                    },
                )
            }
            dateOptions.forEach { (date, labelRes) ->
                SelectableOptionRow(
                    label = stringResource(labelRes),
                    selected = libraryFilter.date == date,
                    onClick = {
                        onFilterChanged(libraryFilter.copy(date = date))
                    },
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            OptionsSectionTitle(stringResource(R.string.display))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.collapse_singles),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = collapseSingles,
                    onCheckedChange = onCollapseSinglesChanged,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = theme.accent,
                        checkedTrackColor = theme.accent.copy(alpha = 0.35f),
                    ),
                )
            }
        }
    }
}

@Composable
private fun OptionsSectionTitle(title: String) {
    val theme = LocalLumenTheme.current
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = theme.onSurfaceMuted,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
    )
}

@Composable
private fun SelectableOptionRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val theme = LocalLumenTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (selected) theme.accent else theme.onBackground,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = theme.accent,
            )
        }
    }
}
