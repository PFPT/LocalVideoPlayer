package com.zhaoshuo.localvideoplayer.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.zhaoshuo.localvideoplayer.model.AspectRatioMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.playerPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "player_preferences",
)

enum class SubtitleTextSize {
    SMALL,
    STANDARD,
    LARGE,
    ;

    val scale: Float
        get() = when (this) {
            SMALL -> 0.75f
            STANDARD -> 1f
            LARGE -> 1.45f
        }

    companion object {
        fun fromStorage(value: String?): SubtitleTextSize {
            return entries.firstOrNull { it.name == value } ?: STANDARD
        }
    }
}

data class PlayerPreferences(
    val softDecode: Boolean = false,
    val skipIntroSeconds: Int = 0,
    val skipOutroSeconds: Int = 0,
    val shuffleEnabled: Boolean = false,
    val defaultSpeed: Float = 1.0f,
    val aspectRatioMode: AspectRatioMode = AspectRatioMode.FIT,
    val repeatOne: Boolean = false,
    val gestureBrightness: Boolean = true,
    val gestureVolume: Boolean = true,
    val gestureSeek: Boolean = true,
    val gestureDoubleTapSeek: Boolean = true,
    val gesturePinchZoom: Boolean = true,
    val useTextureView: Boolean = false,
    val subtitleTextSize: SubtitleTextSize = SubtitleTextSize.STANDARD,
    val subtitleYellow: Boolean = false,
    val subtitleDelayMs: Int = 0,
)

class PlayerPreferencesStore(private val context: Context) {

    val preferences: Flow<PlayerPreferences> = context.playerPreferencesDataStore.data.map { prefs ->
        PlayerPreferences(
            softDecode = prefs[SOFT_DECODE] ?: false,
            skipIntroSeconds = (prefs[SKIP_INTRO_SECONDS] ?: 0).coerceIn(0, 600),
            skipOutroSeconds = (prefs[SKIP_OUTRO_SECONDS] ?: 0).coerceIn(0, 600),
            shuffleEnabled = prefs[SHUFFLE_ENABLED] ?: false,
            defaultSpeed = (prefs[DEFAULT_SPEED] ?: 1.0f).coerceIn(0.5f, 5.0f),
            aspectRatioMode = AspectRatioMode.fromStorage(prefs[ASPECT_RATIO_MODE]),
            repeatOne = prefs[REPEAT_ONE] ?: false,
            gestureBrightness = prefs[GESTURE_BRIGHTNESS] ?: true,
            gestureVolume = prefs[GESTURE_VOLUME] ?: true,
            gestureSeek = prefs[GESTURE_SEEK] ?: true,
            gestureDoubleTapSeek = prefs[GESTURE_DOUBLE_TAP_SEEK] ?: true,
            gesturePinchZoom = prefs[GESTURE_PINCH_ZOOM] ?: true,
            useTextureView = prefs[USE_TEXTURE_VIEW] ?: false,
            subtitleTextSize = SubtitleTextSize.fromStorage(prefs[SUBTITLE_TEXT_SIZE]),
            subtitleYellow = prefs[SUBTITLE_YELLOW] ?: false,
            subtitleDelayMs = normalizeSubtitleDelay(prefs[SUBTITLE_DELAY_MS] ?: 0),
        )
    }

    suspend fun getPreferences(): PlayerPreferences = preferences.first()

    suspend fun setSoftDecode(enabled: Boolean) {
        context.playerPreferencesDataStore.edit { it[SOFT_DECODE] = enabled }
    }

    suspend fun setSkipIntroSeconds(seconds: Int) {
        context.playerPreferencesDataStore.edit {
            it[SKIP_INTRO_SECONDS] = seconds.coerceIn(0, 600)
        }
    }

    suspend fun setSkipOutroSeconds(seconds: Int) {
        context.playerPreferencesDataStore.edit {
            it[SKIP_OUTRO_SECONDS] = seconds.coerceIn(0, 600)
        }
    }

    suspend fun setShuffleEnabled(enabled: Boolean) {
        context.playerPreferencesDataStore.edit { it[SHUFFLE_ENABLED] = enabled }
    }

    suspend fun setDefaultSpeed(speed: Float) {
        context.playerPreferencesDataStore.edit { it[DEFAULT_SPEED] = speed.coerceIn(0.5f, 5.0f) }
    }

    suspend fun setAspectRatioMode(mode: AspectRatioMode) {
        context.playerPreferencesDataStore.edit { it[ASPECT_RATIO_MODE] = mode.name }
    }

    suspend fun setRepeatOne(enabled: Boolean) {
        context.playerPreferencesDataStore.edit { it[REPEAT_ONE] = enabled }
    }

    suspend fun setGestureBrightness(enabled: Boolean) {
        context.playerPreferencesDataStore.edit { it[GESTURE_BRIGHTNESS] = enabled }
    }

    suspend fun setGestureVolume(enabled: Boolean) {
        context.playerPreferencesDataStore.edit { it[GESTURE_VOLUME] = enabled }
    }

    suspend fun setGestureSeek(enabled: Boolean) {
        context.playerPreferencesDataStore.edit { it[GESTURE_SEEK] = enabled }
    }

    suspend fun setGestureDoubleTapSeek(enabled: Boolean) {
        context.playerPreferencesDataStore.edit { it[GESTURE_DOUBLE_TAP_SEEK] = enabled }
    }

    suspend fun setGesturePinchZoom(enabled: Boolean) {
        context.playerPreferencesDataStore.edit { it[GESTURE_PINCH_ZOOM] = enabled }
    }

    suspend fun setUseTextureView(enabled: Boolean) {
        context.playerPreferencesDataStore.edit { it[USE_TEXTURE_VIEW] = enabled }
    }

    suspend fun setSubtitleTextSize(size: SubtitleTextSize) {
        context.playerPreferencesDataStore.edit { it[SUBTITLE_TEXT_SIZE] = size.name }
    }

    suspend fun setSubtitleYellow(enabled: Boolean) {
        context.playerPreferencesDataStore.edit { it[SUBTITLE_YELLOW] = enabled }
    }

    suspend fun setSubtitleDelayMs(delayMs: Int) {
        context.playerPreferencesDataStore.edit {
            it[SUBTITLE_DELAY_MS] = normalizeSubtitleDelay(delayMs)
        }
    }

    companion object {
        private val SOFT_DECODE = booleanPreferencesKey("soft_decode")
        private val SKIP_INTRO_SECONDS = intPreferencesKey("skip_intro_seconds")
        private val SKIP_OUTRO_SECONDS = intPreferencesKey("skip_outro_seconds")
        private val SHUFFLE_ENABLED = booleanPreferencesKey("shuffle_enabled")
        private val DEFAULT_SPEED = floatPreferencesKey("default_speed")
        private val ASPECT_RATIO_MODE = stringPreferencesKey("aspect_ratio_mode")
        private val REPEAT_ONE = booleanPreferencesKey("repeat_one")
        private val GESTURE_BRIGHTNESS = booleanPreferencesKey("gesture_brightness")
        private val GESTURE_VOLUME = booleanPreferencesKey("gesture_volume")
        private val GESTURE_SEEK = booleanPreferencesKey("gesture_seek")
        private val GESTURE_DOUBLE_TAP_SEEK = booleanPreferencesKey("gesture_double_tap_seek")
        private val GESTURE_PINCH_ZOOM = booleanPreferencesKey("gesture_pinch_zoom")
        private val USE_TEXTURE_VIEW = booleanPreferencesKey("use_texture_view")
        private val SUBTITLE_TEXT_SIZE = stringPreferencesKey("subtitle_text_size")
        private val SUBTITLE_YELLOW = booleanPreferencesKey("subtitle_yellow")
        private val SUBTITLE_DELAY_MS = intPreferencesKey("subtitle_delay_ms")
    }
}

fun normalizeSubtitleDelay(delayMs: Int): Int {
    val snapped = (delayMs / 500) * 500
    return snapped.coerceIn(-5_000, 5_000)
}
