package com.zhaoshuo.localvideoplayer.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.zhaoshuo.localvideoplayer.model.DateFilter
import com.zhaoshuo.localvideoplayer.model.DurationFilter
import com.zhaoshuo.localvideoplayer.model.LibraryFilter
import com.zhaoshuo.localvideoplayer.model.LibraryTypeTab
import com.zhaoshuo.localvideoplayer.model.LibraryViewLayout
import com.zhaoshuo.localvideoplayer.model.VideoSortOrder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.libraryPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "library_preferences",
)

data class LibraryPreferences(
    val sortOrder: VideoSortOrder = VideoSortOrder.DATE_DESC,
    val durationFilter: DurationFilter = DurationFilter.ALL,
    val dateFilter: DateFilter = DateFilter.ALL,
    val typeTab: LibraryTypeTab = LibraryTypeTab.ALL,
    val viewLayout: LibraryViewLayout = LibraryViewLayout.SECTIONED,
    val collapseSingles: Boolean = false,
) {
    val libraryFilter: LibraryFilter
        get() = LibraryFilter(duration = durationFilter, date = dateFilter)
}

class LibraryPreferencesStore(private val context: Context) {

    val preferences: Flow<LibraryPreferences> = context.libraryPreferencesDataStore.data.map { prefs ->
        LibraryPreferences(
            sortOrder = enumValueOr(prefs[SORT_ORDER], VideoSortOrder.DATE_DESC),
            durationFilter = enumValueOr(prefs[DURATION_FILTER], DurationFilter.ALL),
            dateFilter = enumValueOr(prefs[DATE_FILTER], DateFilter.ALL),
            typeTab = enumValueOr(prefs[TYPE_TAB], LibraryTypeTab.ALL),
            viewLayout = enumValueOr(prefs[VIEW_LAYOUT], LibraryViewLayout.SECTIONED),
            collapseSingles = prefs[COLLAPSE_SINGLES] ?: false,
        )
    }

    suspend fun setSortOrder(order: VideoSortOrder) {
        context.libraryPreferencesDataStore.edit { it[SORT_ORDER] = order.name }
    }

    suspend fun setLibraryFilter(filter: LibraryFilter) {
        context.libraryPreferencesDataStore.edit {
            it[DURATION_FILTER] = filter.duration.name
            it[DATE_FILTER] = filter.date.name
        }
    }

    suspend fun setTypeTab(tab: LibraryTypeTab) {
        context.libraryPreferencesDataStore.edit { it[TYPE_TAB] = tab.name }
    }

    suspend fun setViewLayout(layout: LibraryViewLayout) {
        context.libraryPreferencesDataStore.edit { it[VIEW_LAYOUT] = layout.name }
    }

    suspend fun setCollapseSingles(enabled: Boolean) {
        context.libraryPreferencesDataStore.edit { it[COLLAPSE_SINGLES] = enabled }
    }

    companion object {
        private val SORT_ORDER = stringPreferencesKey("sort_order")
        private val DURATION_FILTER = stringPreferencesKey("duration_filter")
        private val DATE_FILTER = stringPreferencesKey("date_filter")
        private val TYPE_TAB = stringPreferencesKey("type_tab")
        private val VIEW_LAYOUT = stringPreferencesKey("view_layout")
        private val COLLAPSE_SINGLES = booleanPreferencesKey("collapse_singles")

        private inline fun <reified T : Enum<T>> enumValueOr(value: String?, fallback: T): T {
            if (value.isNullOrBlank()) return fallback
            return runCatching { enumValueOf<T>(value) }.getOrDefault(fallback)
        }
    }
}
