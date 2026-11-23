# Series Playback Memory Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 同一部剧下次打开或换集时，沿用上次选的音轨语言、字幕选择和倍速。

**Architecture:** 纯函数放在 `SeriesPlaybackLogic.kt`，按 `seriesKey` 存进新的 DataStore `SeriesPlaybackStore`。界面仍用现有的序号选择作用在当前文件上；打开、换集、以及不是这次点选刚写入的记忆变化，才把记忆套回播放器。外挂字幕仍由 `PlaybackSession.resolveSubtitle` 决定，关闭状态由界面传入。

**Tech Stack:** Kotlin、Jetpack DataStore、Media3 ExoPlayer、JUnit 4、Android Gradle `fullDebug` 单元测试。

## Global Constraints

- 没有 `seriesKey` 的单集不记。单集倍速仍写入设置里的默认倍速。
- 语言代码去掉首尾空白后为空，或忽略大小写等于 `und`，不能跨集记住。能记住的代码按去掉首尾空白后的原文保存。
- 音轨或字幕选自动时清掉这部剧对应的记忆。字幕关闭记下 `__off__`。没有可用语言的选择只作用在当前文件，已有记忆保持不变。
- 视频轨只改当前播放，不写入。
- 同一集里有多条相同语言时，当前这次选择用轨道序号。换集或下次打开只按语言选。
- 用户这次点选写入记忆后，当前这一集保持刚才的序号选择。轨道列表随后刷新不再套用。套用发生在打开并且轨道已出来、换集并且轨道已出来、记忆变成另一套值并且不是这次点选刚写入的那一套。同一集、同一套记忆套用过一次后不再套用。
- 套用时清掉音轨和字幕的序号覆盖。视频轨覆盖，以及离开播放页时关掉画面的视频轨开关，保持原样。
- 这一集没有记下的语言时回到自动。
- 手动字幕优先于同名外挂。记成关闭时不挂同名外挂；手动文件仍挂上，并且不改剧的记忆。记成某种语言时，有手动文件或同名外挂就用那个文件。有外挂要显示时，字幕轨保持开启并清掉字幕偏好语言。已经挂上的同名外挂不从媒体项上拆掉；关闭指令关掉字幕轨。
- 这部剧有自己的倍速就用它，否则用设置里的默认倍速。倍速限制在 `0.5f..5.0f`。
- 播放器倍速弹层在有 `seriesKey` 时只写这部剧。长按加速的那一档不写入。加速过程中，新的有效倍速不改当前这一档。
- 设置页只改默认倍速。没有“跟随默认倍速”的开关。
- 打开播放页时可以先用默认倍速，剧集倍速一到就换上。
- 投屏不读这份记忆。片头片尾存储、字幕字号颜色延迟、飞入动画、片库、下载不动。
- 只给纯函数写 JVM 单测，不写 Compose 或 Robolectric。
- 实现代码不添加注释。

## File Structure

- Create: `app/src/main/java/com/pfpt/localvideoplayer/player/SeriesPlaybackLogic.kt` — 记忆更新、倍速、外挂字幕、套用条件和轨道指令。
- Create: `app/src/test/java/com/pfpt/localvideoplayer/player/SeriesPlaybackLogicTest.kt` — 上述纯函数的 JVM 测试。
- Create: `app/src/main/java/com/pfpt/localvideoplayer/data/SeriesPlaybackStore.kt` — DataStore `series_playback`，键前缀 `spa:`、`spt:`、`sps:`。
- Modify: `app/src/main/java/com/pfpt/localvideoplayer/player/PlaybackSession.kt` — 关闭状态进入 `resolveSubtitle`。
- Modify: `app/src/main/java/com/pfpt/localvideoplayer/player/PlayerExtras.kt` — 把轨道指令写进 ExoPlayer，轨道选项带上语言。
- Modify: `app/src/main/java/com/pfpt/localvideoplayer/ui/components/TrackSelectorSheet.kt` — `TrackOption` 增加 `language`。
- Modify: `app/src/main/java/com/pfpt/localvideoplayer/ui/PlayerUiState.kt` — 轨道刷新计数，以及已套用、刚跳过的请求。
- Modify: `app/src/main/java/com/pfpt/localvideoplayer/LocalVideoPlayerApp.kt` — 创建存储并传入播放页。
- Modify: `app/src/main/java/com/pfpt/localvideoplayer/ui/PlayerScreen.kt` — 有效倍速，并把存储传到弹层和会话效果。
- Modify: `app/src/main/java/com/pfpt/localvideoplayer/ui/player/PlayerOverlaySheets.kt` — 倍速和音轨、字幕写入存储。
- Modify: `app/src/main/java/com/pfpt/localvideoplayer/ui/player/PlayerSessionEffects.kt` — 轨道出来后套用记忆。

---

### Task 1: 播放记忆纯函数

**Files:**
- Create: `app/src/test/java/com/pfpt/localvideoplayer/player/SeriesPlaybackLogicTest.kt`
- Create: `app/src/main/java/com/pfpt/localvideoplayer/player/SeriesPlaybackLogic.kt`
- Test: `app/src/test/java/com/pfpt/localvideoplayer/player/SeriesPlaybackLogicTest.kt`

**Interfaces:**
- Consumes: `SpeedSnapshot`、`speedAfterDefault`，来自 `PlaybackDecisions.kt`。
- Produces:
  - `const val SERIES_TEXT_OFF: String`
  - `data class SeriesPlaybackSettings(val audioLanguage: String? = null, val text: SeriesTextMemory? = null, val speed: Float? = null)`
  - `sealed interface SeriesTextMemory`，`data class Language(val code: String)` 与 `data object Off`
  - `sealed interface TrackMemoryUpdate`，`Clear`、`SaveLanguage(code)`、`SaveOff`、`Leave`
  - `fun usableTrackLanguage(language: String?): String?`
  - `fun audioMemoryUpdate(optionId: String, language: String?): TrackMemoryUpdate`
  - `fun textMemoryUpdate(optionId: String, language: String?): TrackMemoryUpdate`
  - `fun textMemoryFromStorage(value: String?): SeriesTextMemory?`
  - `fun normalizeSeriesSpeed(speed: Float): Float`
  - `fun effectivePlaybackSpeed(seriesSpeed: Float?, defaultSpeed: Float): Float`
  - `fun externalSubtitleTarget(manual: String?, sidecar: String?, textOff: Boolean): String?`
  - `sealed interface SeriesAudioDirective`，`Auto` 与 `Language(code)`
  - `sealed interface SeriesTextDirective`，`Auto`、`Off`、`Language(code)`、`External`
  - `data class SeriesTrackDirective(val audio: SeriesAudioDirective, val text: SeriesTextDirective)`
  - `fun seriesTrackDirective(audioLanguage: String?, text: SeriesTextMemory?, hasExternalSubtitle: Boolean): SeriesTrackDirective`
  - `data class SeriesTrackApplyRequest(val index: Int, val seriesKey: String?, val audioLanguage: String?, val text: SeriesTextMemory?)`
  - `fun shouldApplySeriesTracks(tracksReady: Boolean, request: SeriesTrackApplyRequest, applied: SeriesTrackApplyRequest?, skipped: SeriesTrackApplyRequest?): Boolean`

- [ ] **Step 1: Write the failing test**

创建 `app/src/test/java/com/pfpt/localvideoplayer/player/SeriesPlaybackLogicTest.kt`：

```kotlin
package com.pfpt.localvideoplayer.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SeriesPlaybackLogicTest {
    @Test
    fun blankAndUndLanguagesAreNotUsable() {
        assertNull(usableTrackLanguage(null))
        assertNull(usableTrackLanguage("  "))
        assertNull(usableTrackLanguage("und"))
        assertNull(usableTrackLanguage(" UND "))
    }

    @Test
    fun usableLanguageKeepsTrimmedText() {
        assertEquals("jpn", usableTrackLanguage(" jpn "))
    }

    @Test
    fun audioAutoClearsAndMissingLanguageLeavesMemory() {
        assertEquals(TrackMemoryUpdate.Clear, audioMemoryUpdate("auto", "jpn"))
        assertEquals(TrackMemoryUpdate.SaveLanguage("eng"), audioMemoryUpdate("0:1", " eng "))
        assertEquals(TrackMemoryUpdate.Leave, audioMemoryUpdate("0:1", "und"))
        assertEquals(TrackMemoryUpdate.Leave, audioMemoryUpdate("0:1", null))
    }

    @Test
    fun textAutoClearsOffSavesAndMissingLanguageLeavesMemory() {
        assertEquals(TrackMemoryUpdate.Clear, textMemoryUpdate("auto", "jpn"))
        assertEquals(TrackMemoryUpdate.SaveOff, textMemoryUpdate("off", null))
        assertEquals(TrackMemoryUpdate.SaveLanguage("chi"), textMemoryUpdate("1:0", "chi"))
        assertEquals(TrackMemoryUpdate.Leave, textMemoryUpdate("1:0", " "))
    }

    @Test
    fun storedTextOffUndAndBlankReadAsSpecified() {
        assertNull(textMemoryFromStorage(null))
        assertEquals(SeriesTextMemory.Off, textMemoryFromStorage(SERIES_TEXT_OFF))
        assertNull(textMemoryFromStorage("und"))
        assertNull(textMemoryFromStorage("  "))
        assertEquals(SeriesTextMemory.Language("jpn"), textMemoryFromStorage(" jpn "))
    }

    @Test
    fun seriesSpeedOverridesDefault() {
        assertEquals(1.5f, effectivePlaybackSpeed(seriesSpeed = 1.5f, defaultSpeed = 1f), 0f)
        assertEquals(1.25f, effectivePlaybackSpeed(seriesSpeed = null, defaultSpeed = 1.25f), 0f)
    }

    @Test
    fun seriesSpeedIsClamped() {
        assertEquals(0.5f, normalizeSeriesSpeed(0.1f), 0f)
        assertEquals(5f, normalizeSeriesSpeed(8f), 0f)
        assertEquals(1.5f, normalizeSeriesSpeed(1.5f), 0f)
    }

    @Test
    fun boostIgnoresIncomingEffectiveSpeed() {
        val boosted = SpeedSnapshot(playbackSpeed = 2f, savedPlaybackSpeed = 1f, boosted = true)
        val effective = effectivePlaybackSpeed(seriesSpeed = 1.5f, defaultSpeed = 1f)
        assertEquals(boosted, speedAfterDefault(boosted, effective))
    }

    @Test
    fun manualSubtitleWinsAndOffSkipsSidecar() {
        assertEquals(
            "manual.srt",
            externalSubtitleTarget(manual = "manual.srt", sidecar = "side.srt", textOff = true),
        )
        assertNull(externalSubtitleTarget(manual = null, sidecar = "side.srt", textOff = true))
        assertEquals(
            "side.srt",
            externalSubtitleTarget(manual = null, sidecar = "side.srt", textOff = false),
        )
    }

    @Test
    fun externalSubtitleLeavesEmbeddedChoiceAlone() {
        val directive = seriesTrackDirective(
            audioLanguage = "jpn",
            text = SeriesTextMemory.Language("chi"),
            hasExternalSubtitle = true,
        )
        assertEquals(SeriesAudioDirective.Language("jpn"), directive.audio)
        assertEquals(SeriesTextDirective.External, directive.text)
    }

    @Test
    fun offWithoutExternalDisablesText() {
        val directive = seriesTrackDirective(
            audioLanguage = null,
            text = SeriesTextMemory.Off,
            hasExternalSubtitle = false,
        )
        assertEquals(SeriesAudioDirective.Auto, directive.audio)
        assertEquals(SeriesTextDirective.Off, directive.text)
    }

    @Test
    fun languageWithoutExternalSelectsEmbeddedText() {
        val directive = seriesTrackDirective(
            audioLanguage = null,
            text = SeriesTextMemory.Language("chi"),
            hasExternalSubtitle = false,
        )
        assertEquals(SeriesTextDirective.Language("chi"), directive.text)
    }

    @Test
    fun tracksApplyOnceAndSkipTheSelectionJustSaved() {
        val request = SeriesTrackApplyRequest(
            index = 0,
            seriesKey = "name:show",
            audioLanguage = "jpn",
            text = null,
        )
        assertFalse(
            shouldApplySeriesTracks(
                tracksReady = false,
                request = request,
                applied = null,
                skipped = null,
            ),
        )
        assertTrue(
            shouldApplySeriesTracks(
                tracksReady = true,
                request = request,
                applied = null,
                skipped = null,
            ),
        )
        assertFalse(
            shouldApplySeriesTracks(
                tracksReady = true,
                request = request,
                applied = request,
                skipped = null,
            ),
        )
        val selected = request.copy(text = SeriesTextMemory.Off)
        assertFalse(
            shouldApplySeriesTracks(
                tracksReady = true,
                request = selected,
                applied = request,
                skipped = selected,
            ),
        )
        assertTrue(
            shouldApplySeriesTracks(
                tracksReady = true,
                request = selected.copy(index = 1),
                applied = request,
                skipped = selected,
            ),
        )
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testFullDebugUnitTest --tests com.pfpt.localvideoplayer.player.SeriesPlaybackLogicTest`

Expected: FAIL，`SeriesPlaybackLogicKt` 或其中的函数未定义。

- [ ] **Step 3: Write minimal implementation**

创建 `app/src/main/java/com/pfpt/localvideoplayer/player/SeriesPlaybackLogic.kt`：

```kotlin
package com.pfpt.localvideoplayer.player

const val SERIES_TEXT_OFF = "__off__"

data class SeriesPlaybackSettings(
    val audioLanguage: String? = null,
    val text: SeriesTextMemory? = null,
    val speed: Float? = null,
)

sealed interface SeriesTextMemory {
    data class Language(val code: String) : SeriesTextMemory
    data object Off : SeriesTextMemory
}

sealed interface TrackMemoryUpdate {
    data object Clear : TrackMemoryUpdate
    data class SaveLanguage(val code: String) : TrackMemoryUpdate
    data object SaveOff : TrackMemoryUpdate
    data object Leave : TrackMemoryUpdate
}

fun usableTrackLanguage(language: String?): String? {
    val trimmed = language?.trim().orEmpty()
    if (trimmed.isEmpty() || trimmed.equals("und", ignoreCase = true)) return null
    return trimmed
}

fun audioMemoryUpdate(optionId: String, language: String?): TrackMemoryUpdate {
    if (optionId == "auto") return TrackMemoryUpdate.Clear
    val code = usableTrackLanguage(language) ?: return TrackMemoryUpdate.Leave
    return TrackMemoryUpdate.SaveLanguage(code)
}

fun textMemoryUpdate(optionId: String, language: String?): TrackMemoryUpdate {
    if (optionId == "auto") return TrackMemoryUpdate.Clear
    if (optionId == "off") return TrackMemoryUpdate.SaveOff
    val code = usableTrackLanguage(language) ?: return TrackMemoryUpdate.Leave
    return TrackMemoryUpdate.SaveLanguage(code)
}

fun textMemoryFromStorage(value: String?): SeriesTextMemory? {
    if (value == null || value == SERIES_TEXT_OFF) {
        return if (value == SERIES_TEXT_OFF) SeriesTextMemory.Off else null
    }
    val code = usableTrackLanguage(value) ?: return null
    return SeriesTextMemory.Language(code)
}

fun normalizeSeriesSpeed(speed: Float): Float = speed.coerceIn(0.5f, 5.0f)

fun effectivePlaybackSpeed(seriesSpeed: Float?, defaultSpeed: Float): Float {
    return seriesSpeed ?: defaultSpeed
}

fun externalSubtitleTarget(manual: String?, sidecar: String?, textOff: Boolean): String? {
    if (manual != null) return manual
    if (textOff) return null
    return sidecar
}

sealed interface SeriesAudioDirective {
    data object Auto : SeriesAudioDirective
    data class Language(val code: String) : SeriesAudioDirective
}

sealed interface SeriesTextDirective {
    data object Auto : SeriesTextDirective
    data object Off : SeriesTextDirective
    data class Language(val code: String) : SeriesTextDirective
    data object External : SeriesTextDirective
}

data class SeriesTrackDirective(
    val audio: SeriesAudioDirective,
    val text: SeriesTextDirective,
)

fun seriesTrackDirective(
    audioLanguage: String?,
    text: SeriesTextMemory?,
    hasExternalSubtitle: Boolean,
): SeriesTrackDirective {
    val audio = if (audioLanguage == null) {
        SeriesAudioDirective.Auto
    } else {
        SeriesAudioDirective.Language(audioLanguage)
    }
    val textDirective = when {
        hasExternalSubtitle -> SeriesTextDirective.External
        text is SeriesTextMemory.Off -> SeriesTextDirective.Off
        text is SeriesTextMemory.Language -> SeriesTextDirective.Language(text.code)
        else -> SeriesTextDirective.Auto
    }
    return SeriesTrackDirective(audio = audio, text = textDirective)
}

data class SeriesTrackApplyRequest(
    val index: Int,
    val seriesKey: String?,
    val audioLanguage: String?,
    val text: SeriesTextMemory?,
)

fun shouldApplySeriesTracks(
    tracksReady: Boolean,
    request: SeriesTrackApplyRequest,
    applied: SeriesTrackApplyRequest?,
    skipped: SeriesTrackApplyRequest?,
): Boolean {
    if (!tracksReady) return false
    if (request == applied || request == skipped) return false
    return true
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testFullDebugUnitTest --tests com.pfpt.localvideoplayer.player.SeriesPlaybackLogicTest`

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/pfpt/localvideoplayer/player/SeriesPlaybackLogic.kt app/src/test/java/com/pfpt/localvideoplayer/player/SeriesPlaybackLogicTest.kt
git commit -m "$(cat <<'EOF'
feat: decide per-series audio, subtitle, and speed in pure functions

EOF
)"
```

---

### Task 2: 剧集播放存储

**Files:**
- Create: `app/src/main/java/com/pfpt/localvideoplayer/data/SeriesPlaybackStore.kt`
- Test: `app/src/test/java/com/pfpt/localvideoplayer/player/SeriesPlaybackLogicTest.kt`

**Interfaces:**
- Consumes: `SERIES_TEXT_OFF`、`SeriesPlaybackSettings`、`normalizeSeriesSpeed`、`textMemoryFromStorage`、`usableTrackLanguage`。
- Produces:
  - `class SeriesPlaybackStore(context: Context)`
  - `fun observe(seriesKey: String?): Flow<SeriesPlaybackSettings>`
  - `suspend fun setAudioLanguage(seriesKey: String, language: String)`
  - `suspend fun clearAudioLanguage(seriesKey: String)`
  - `suspend fun setTextLanguage(seriesKey: String, language: String)`
  - `suspend fun setTextOff(seriesKey: String)`
  - `suspend fun clearText(seriesKey: String)`
  - `suspend fun setSpeed(seriesKey: String, speed: Float)`

`seriesKey` 为空白时，`observe` 发出空的 `SeriesPlaybackSettings()`，每个写入函数直接返回。读出的音轨语言再过 `usableTrackLanguage`。读出的倍速再过 `normalizeSeriesSpeed`。

- [ ] **Step 1: Write the store**

创建 `app/src/main/java/com/pfpt/localvideoplayer/data/SeriesPlaybackStore.kt`：

```kotlin
package com.pfpt.localvideoplayer.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.pfpt.localvideoplayer.player.SERIES_TEXT_OFF
import com.pfpt.localvideoplayer.player.SeriesPlaybackSettings
import com.pfpt.localvideoplayer.player.normalizeSeriesSpeed
import com.pfpt.localvideoplayer.player.textMemoryFromStorage
import com.pfpt.localvideoplayer.player.usableTrackLanguage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.seriesPlaybackDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "series_playback",
)

class SeriesPlaybackStore(private val context: Context) {

    fun observe(seriesKey: String?): Flow<SeriesPlaybackSettings> {
        return context.seriesPlaybackDataStore.data.map { prefs ->
            if (seriesKey.isNullOrBlank()) {
                SeriesPlaybackSettings()
            } else {
                prefs.toSettings(seriesKey)
            }
        }
    }

    suspend fun setAudioLanguage(seriesKey: String, language: String) {
        val code = usableTrackLanguage(language) ?: return
        if (seriesKey.isBlank()) return
        context.seriesPlaybackDataStore.edit { prefs ->
            prefs[audioKey(seriesKey)] = code
        }
    }

    suspend fun clearAudioLanguage(seriesKey: String) {
        if (seriesKey.isBlank()) return
        context.seriesPlaybackDataStore.edit { prefs ->
            prefs.remove(audioKey(seriesKey))
        }
    }

    suspend fun setTextLanguage(seriesKey: String, language: String) {
        val code = usableTrackLanguage(language) ?: return
        if (seriesKey.isBlank()) return
        context.seriesPlaybackDataStore.edit { prefs ->
            prefs[textKey(seriesKey)] = code
        }
    }

    suspend fun setTextOff(seriesKey: String) {
        if (seriesKey.isBlank()) return
        context.seriesPlaybackDataStore.edit { prefs ->
            prefs[textKey(seriesKey)] = SERIES_TEXT_OFF
        }
    }

    suspend fun clearText(seriesKey: String) {
        if (seriesKey.isBlank()) return
        context.seriesPlaybackDataStore.edit { prefs ->
            prefs.remove(textKey(seriesKey))
        }
    }

    suspend fun setSpeed(seriesKey: String, speed: Float) {
        if (seriesKey.isBlank()) return
        context.seriesPlaybackDataStore.edit { prefs ->
            prefs[speedKey(seriesKey)] = normalizeSeriesSpeed(speed)
        }
    }

    private fun Preferences.toSettings(seriesKey: String): SeriesPlaybackSettings {
        return SeriesPlaybackSettings(
            audioLanguage = usableTrackLanguage(this[audioKey(seriesKey)]),
            text = textMemoryFromStorage(this[textKey(seriesKey)]),
            speed = this[speedKey(seriesKey)]?.let(::normalizeSeriesSpeed),
        )
    }

    companion object {
        private const val AUDIO_PREFIX = "spa:"
        private const val TEXT_PREFIX = "spt:"
        private const val SPEED_PREFIX = "sps:"

        private fun audioKey(seriesKey: String) = stringPreferencesKey(AUDIO_PREFIX + seriesKey)
        private fun textKey(seriesKey: String) = stringPreferencesKey(TEXT_PREFIX + seriesKey)
        private fun speedKey(seriesKey: String) = floatPreferencesKey(SPEED_PREFIX + seriesKey)
    }
}
```

- [ ] **Step 2: Compile and re-run the pure tests**

Run: `./gradlew :app:compileFullDebugKotlin :app:testFullDebugUnitTest --tests com.pfpt.localvideoplayer.player.SeriesPlaybackLogicTest`

Expected: `compileFullDebugKotlin` 成功，测试 PASS。存储依赖 Android DataStore，这一轮不加 Robolectric。

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/pfpt/localvideoplayer/data/SeriesPlaybackStore.kt
git commit -m "$(cat <<'EOF'
feat: store per-series audio, subtitle, and speed

EOF
)"
```

---

### Task 3: 外挂字幕看关闭状态

**Files:**
- Modify: `app/src/main/java/com/pfpt/localvideoplayer/player/PlaybackSession.kt`
- Test: `app/src/test/java/com/pfpt/localvideoplayer/player/SeriesPlaybackLogicTest.kt`

**Interfaces:**
- Consumes: `externalSubtitleTarget`。
- Produces:
  - `fun setSeriesTextOff(off: Boolean)`
  - `fun peekExternalSubtitle(video: VideoItem): String?`

- [ ] **Step 1: Pass the off flag into subtitle resolution**

在 `PlaybackSession` 的字段区，`private var preloadJob: Job? = null` 下方增加：

```kotlin
    private var seriesTextOff: Boolean = false
```

在 `fun player(): ExoPlayer? = player` 上方增加：

```kotlin
    fun setSeriesTextOff(off: Boolean) {
        seriesTextOff = off
    }

    fun peekExternalSubtitle(video: VideoItem): String? = resolveSubtitle(video)
```

把 `resolveSubtitle` 换成：

```kotlin
    private fun resolveSubtitle(video: VideoItem): String? {
        val sidecar = findSidecarSubtitle(appContext, video)?.toString()
        val manual = manualSubtitles[video.uri.toString()]
        return externalSubtitleTarget(manual, sidecar, seriesTextOff)
    }
```

`subtitleForVideo` 留在 `PlaybackSessionLogic.kt`，这一轮的解析不再调用它。已有的 `PlaybackSessionLogicTest` 继续覆盖那个函数。

- [ ] **Step 2: Compile and re-run pure tests**

Run: `./gradlew :app:compileFullDebugKotlin :app:testFullDebugUnitTest --tests com.pfpt.localvideoplayer.player.SeriesPlaybackLogicTest`

Expected: 编译成功，测试 PASS。

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/pfpt/localvideoplayer/player/PlaybackSession.kt
git commit -m "$(cat <<'EOF'
feat: skip sidecar subtitles when a series turns text off

EOF
)"
```

---

### Task 4: 把轨道指令写进播放器

**Files:**
- Modify: `app/src/main/java/com/pfpt/localvideoplayer/ui/components/TrackSelectorSheet.kt`
- Modify: `app/src/main/java/com/pfpt/localvideoplayer/player/PlayerExtras.kt`
- Test: `app/src/test/java/com/pfpt/localvideoplayer/player/SeriesPlaybackLogicTest.kt`

**Interfaces:**
- Consumes: `SeriesTrackDirective`、`SeriesAudioDirective`、`SeriesTextDirective`。
- Produces:
  - `TrackOption.language: String?`
  - `fun ExoPlayer.applySeriesTrackDirective(directive: SeriesTrackDirective)`

- [ ] **Step 1: Carry the track language on the option**

把 `TrackSelectorSheet.kt` 里的 `TrackOption` 换成：

```kotlin
data class TrackOption(
    val id: String,
    val label: String,
    val selected: Boolean,
    val language: String? = null,
)
```

在 `PlayerExtras.kt` 的 `trackOptions` 里，真正的轨道项改为带上 `format.language`。`auto` 和 `off` 保持 `language` 缺省为 `null`：

```kotlin
            options.add(
                TrackOption(
                    id = "$groupIndex:$i",
                    label = label,
                    selected = selected && hasExplicitOverride(type),
                    language = format.language,
                ),
            )
```

- [ ] **Step 2: Apply a directive without touching the video track**

在 `PlayerExtras.kt` 的 `applyTrackSelection` 后面增加：

```kotlin
fun ExoPlayer.applySeriesTrackDirective(directive: SeriesTrackDirective) {
    val builder = trackSelectionParameters
        .buildUpon()
        .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
        .clearOverridesOfType(C.TRACK_TYPE_TEXT)
    when (val audio = directive.audio) {
        SeriesAudioDirective.Auto -> builder.setPreferredAudioLanguage(null)
        is SeriesAudioDirective.Language -> builder.setPreferredAudioLanguage(audio.code)
    }
    when (val text = directive.text) {
        SeriesTextDirective.Auto,
        SeriesTextDirective.External,
        -> builder
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            .setPreferredTextLanguage(null)
        SeriesTextDirective.Off -> builder
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            .setPreferredTextLanguage(null)
        is SeriesTextDirective.Language -> builder
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            .setPreferredTextLanguage(text.code)
    }
    trackSelectionParameters = builder.build()
}
```

这个函数只清音轨和字幕的序号覆盖，不调用 `clearOverridesOfType(C.TRACK_TYPE_VIDEO)`，也不改视频轨的 disabled 标志。

- [ ] **Step 3: Compile and re-run pure tests**

Run: `./gradlew :app:compileFullDebugKotlin :app:testFullDebugUnitTest --tests com.pfpt.localvideoplayer.player.SeriesPlaybackLogicTest`

Expected: 编译成功，测试 PASS。

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/pfpt/localvideoplayer/ui/components/TrackSelectorSheet.kt app/src/main/java/com/pfpt/localvideoplayer/player/PlayerExtras.kt
git commit -m "$(cat <<'EOF'
feat: apply remembered audio and subtitle languages on the player

EOF
)"
```

---

### Task 5: 倍速走剧集记忆

**Files:**
- Modify: `app/src/main/java/com/pfpt/localvideoplayer/LocalVideoPlayerApp.kt`
- Modify: `app/src/main/java/com/pfpt/localvideoplayer/ui/PlayerScreen.kt`
- Modify: `app/src/main/java/com/pfpt/localvideoplayer/ui/player/PlayerOverlaySheets.kt`
- Test: `app/src/test/java/com/pfpt/localvideoplayer/player/SeriesPlaybackLogicTest.kt`

**Interfaces:**
- Consumes: `SeriesPlaybackStore`、`effectivePlaybackSpeed`、`speedAfterDefault`。
- Produces: `PlayerScreen` 和 `PlayerOverlaySheets` 接收 `seriesPlaybackStore`。播放器里的有效倍速是 `effectivePlaybackSpeed(seriesPlayback.speed, preferences.defaultSpeed)`。

- [ ] **Step 1: Create the store beside the skip store**

`LocalVideoPlayerApp.kt` 在 `val seriesSkipStore = remember { SeriesSkipStore(context) }` 下一行增加：

```kotlin
    val seriesPlaybackStore = remember { SeriesPlaybackStore(context) }
```

补上 `import com.pfpt.localvideoplayer.data.SeriesPlaybackStore`。

`PlayerScreen(...)` 调用在 `seriesSkipStore = seriesSkipStore,` 下一行增加：

```kotlin
                                            seriesPlaybackStore = seriesPlaybackStore,
```

- [ ] **Step 2: Thread the store into the player screen**

`PlayerScreen` 和 `PlayerScreenContent` 都在 `seriesSkipStore: SeriesSkipStore,` 后面增加参数：

```kotlin
    seriesPlaybackStore: SeriesPlaybackStore,
```

`PlayerScreen` 调用 `PlayerScreenContent` 时传入 `seriesPlaybackStore = seriesPlaybackStore,`。

补上 `SeriesPlaybackStore`、`SeriesPlaybackSettings`、`effectivePlaybackSpeed` 的 import。

把现有的这段：

```kotlin
    LaunchedEffect(preferences.defaultSpeed, preferences.aspectRatioMode, preferences.repeatOne) {
        val next = speedAfterDefault(
            snapshot = SpeedSnapshot(
                playbackSpeed = ui.playbackSpeed,
                savedPlaybackSpeed = ui.savedPlaybackSpeed,
                boosted = ui.isSpeedBoosted,
            ),
            defaultSpeed = preferences.defaultSpeed,
        )
        ui.playbackSpeed = next.playbackSpeed
        ui.savedPlaybackSpeed = next.savedPlaybackSpeed
        ui.isSpeedBoosted = next.boosted
        ui.aspectRatioMode = preferences.aspectRatioMode
        ui.repeatOne = preferences.repeatOne
    }
```

换成只留下画面比例和单集循环：

```kotlin
    LaunchedEffect(preferences.aspectRatioMode, preferences.repeatOne) {
        ui.aspectRatioMode = preferences.aspectRatioMode
        ui.repeatOne = preferences.repeatOne
    }
```

在 `PlayerSessionEffects(...)` 调用之前增加：

```kotlin
    val seriesKey = playlist.getOrNull(ui.currentIndex)?.seriesKey
    val seriesPlayback by seriesPlaybackStore.observe(seriesKey).collectAsStateWithLifecycle(
        initialValue = SeriesPlaybackSettings(),
    )
    val effectiveSpeed = effectivePlaybackSpeed(seriesPlayback.speed, preferences.defaultSpeed)
    LaunchedEffect(effectiveSpeed) {
        val next = speedAfterDefault(
            snapshot = SpeedSnapshot(
                playbackSpeed = ui.playbackSpeed,
                savedPlaybackSpeed = ui.savedPlaybackSpeed,
                boosted = ui.isSpeedBoosted,
            ),
            defaultSpeed = effectiveSpeed,
        )
        ui.playbackSpeed = next.playbackSpeed
        ui.savedPlaybackSpeed = next.savedPlaybackSpeed
        ui.isSpeedBoosted = next.boosted
    }
```

删掉后面原来的 `val seriesKey = currentVideo?.seriesKey`。`seriesSkipStore.observe(seriesKey)` 继续用提前算好的 `seriesKey`。

`PlayerSessionEffects(...)` 增加参数，先接上，效果在 Task 6 里使用：

```kotlin
        seriesKey = seriesKey,
        seriesPlayback = seriesPlayback,
```

`PlayerOverlaySheets(...)` 增加：

```kotlin
            seriesPlaybackStore = seriesPlaybackStore,
            seriesPlayback = seriesPlayback,
```

- [ ] **Step 3: Write series speed from the player sheet**

`PlayerOverlaySheets` 增加参数：

```kotlin
    seriesPlaybackStore: SeriesPlaybackStore,
    seriesPlayback: SeriesPlaybackSettings,
```

补上对应 import。`seriesPlayback` 先收下，Task 6 的点选跳过会用到。

倍速弹层的 `onSpeedSelected` 换成：

```kotlin
            onSpeedSelected = { speed ->
                ui.playbackSpeed = speed
                ui.savedPlaybackSpeed = speed
                ui.resetControlsTimer()
                scope.launch {
                    val key = seriesKey
                    if (key.isNullOrBlank()) {
                        playerPreferencesStore.setDefaultSpeed(speed)
                    } else {
                        seriesPlaybackStore.setSpeed(key, speed)
                    }
                }
            },
```

设置页的 `setDefaultSpeed` 调用保持原样。

- [ ] **Step 4: Compile and re-run pure tests**

`PlayerSessionEffects` 在这一步还没有新参数，会编译失败。先把函数签名加上 `seriesKey: String?` 和 `seriesPlayback: SeriesPlaybackSettings`，函数体内先不读它们。

Run: `./gradlew :app:compileFullDebugKotlin :app:testFullDebugUnitTest --tests com.pfpt.localvideoplayer.player.SeriesPlaybackLogicTest`

Expected: 编译成功，测试 PASS。

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/pfpt/localvideoplayer/LocalVideoPlayerApp.kt app/src/main/java/com/pfpt/localvideoplayer/ui/PlayerScreen.kt app/src/main/java/com/pfpt/localvideoplayer/ui/player/PlayerOverlaySheets.kt app/src/main/java/com/pfpt/localvideoplayer/ui/player/PlayerSessionEffects.kt
git commit -m "$(cat <<'EOF'
feat: keep series playback speed separate from the global default

EOF
)"
```

---

### Task 6: 换集时套用音轨和字幕

**Files:**
- Modify: `app/src/main/java/com/pfpt/localvideoplayer/ui/PlayerUiState.kt`
- Modify: `app/src/main/java/com/pfpt/localvideoplayer/ui/player/PlayerSessionEffects.kt`
- Modify: `app/src/main/java/com/pfpt/localvideoplayer/ui/player/PlayerOverlaySheets.kt`
- Test: `app/src/test/java/com/pfpt/localvideoplayer/player/SeriesPlaybackLogicTest.kt`

**Interfaces:**
- Consumes: `audioMemoryUpdate`、`textMemoryUpdate`、`TrackMemoryUpdate`、`SeriesTrackApplyRequest`、`shouldApplySeriesTracks`、`seriesTrackDirective`、`applySeriesTrackDirective`、`peekExternalSubtitle`、`setSeriesTextOff`。
- Produces: `PlayerUiState.tracksRevision`、`appliedSeriesTracks`、`skippedSeriesTracks`。

- [ ] **Step 1: Remember which request was applied or skipped**

`PlayerUiState` 在 `var abPointB` 后面增加：

```kotlin
    var tracksRevision by mutableIntStateOf(0)
    var appliedSeriesTracks by mutableStateOf<SeriesTrackApplyRequest?>(null)
    var skippedSeriesTracks by mutableStateOf<SeriesTrackApplyRequest?>(null)
```

补上 `import com.pfpt.localvideoplayer.player.SeriesTrackApplyRequest`。

- [ ] **Step 2: Save the selection and skip one reapply**

`PlayerOverlaySheets` 里轨道弹层改成先拿到选项，再按类型写入。视频轨仍然只调用 `applyTrackSelection`：

```kotlin
    ui.showTrackSheet?.let { type ->
        val options = exoPlayer.trackOptions(context, type)
        TrackSelectorSheet(
            title = when (type) {
                C.TRACK_TYPE_AUDIO -> context.getString(R.string.audio_track)
                C.TRACK_TYPE_VIDEO -> context.getString(R.string.video_track)
                else -> context.getString(R.string.subtitles)
            },
            options = options,
            onOptionSelected = { optionId ->
                exoPlayer.applyTrackSelection(type, optionId)
                val key = seriesKey
                if (key.isNullOrBlank()) return@TrackSelectorSheet
                if (type != C.TRACK_TYPE_AUDIO && type != C.TRACK_TYPE_TEXT) return@TrackSelectorSheet
                val language = options.firstOrNull { it.id == optionId }?.language
                val update = if (type == C.TRACK_TYPE_AUDIO) {
                    audioMemoryUpdate(optionId, language)
                } else {
                    textMemoryUpdate(optionId, language)
                }
                val nextAudio = when {
                    type != C.TRACK_TYPE_AUDIO -> seriesPlayback.audioLanguage
                    update is TrackMemoryUpdate.Clear -> null
                    update is TrackMemoryUpdate.SaveLanguage -> update.code
                    else -> seriesPlayback.audioLanguage
                }
                val nextText = when {
                    type != C.TRACK_TYPE_TEXT -> seriesPlayback.text
                    update is TrackMemoryUpdate.Clear -> null
                    update is TrackMemoryUpdate.SaveOff -> SeriesTextMemory.Off
                    update is TrackMemoryUpdate.SaveLanguage -> SeriesTextMemory.Language(update.code)
                    else -> seriesPlayback.text
                }
                ui.skippedSeriesTracks = SeriesTrackApplyRequest(
                    index = ui.currentIndex,
                    seriesKey = key,
                    audioLanguage = nextAudio,
                    text = nextText,
                )
                scope.launch {
                    when (update) {
                        TrackMemoryUpdate.Clear -> if (type == C.TRACK_TYPE_AUDIO) {
                            seriesPlaybackStore.clearAudioLanguage(key)
                        } else {
                            seriesPlaybackStore.clearText(key)
                        }
                        is TrackMemoryUpdate.SaveLanguage -> if (type == C.TRACK_TYPE_AUDIO) {
                            seriesPlaybackStore.setAudioLanguage(key, update.code)
                        } else {
                            seriesPlaybackStore.setTextLanguage(key, update.code)
                        }
                        TrackMemoryUpdate.SaveOff -> seriesPlaybackStore.setTextOff(key)
                        TrackMemoryUpdate.Leave -> Unit
                    }
                }
            },
            onDismiss = { ui.showTrackSheet = null },
        )
    }
```

补上 `audioMemoryUpdate`、`textMemoryUpdate`、`TrackMemoryUpdate`、`SeriesTextMemory`、`SeriesTrackApplyRequest` 的 import。

- [ ] **Step 3: Apply when tracks for this episode are ready**

`PlayerSessionEffects` 的现有 `Player.Listener` 增加：

```kotlin
            override fun onTracksChanged(tracks: Tracks) {
                ui.tracksRevision += 1
            }
```

补上 `import androidx.media3.common.Tracks`。

在 `LaunchedEffect(ui.playbackSpeed)` 之前增加：

```kotlin
    LaunchedEffect(
        ui.currentIndex,
        seriesKey,
        seriesPlayback.audioLanguage,
        seriesPlayback.text,
        ui.tracksRevision,
    ) {
        playbackSession.setSeriesTextOff(seriesPlayback.text is SeriesTextMemory.Off)
        val request = SeriesTrackApplyRequest(
            index = ui.currentIndex,
            seriesKey = seriesKey,
            audioLanguage = seriesPlayback.audioLanguage,
            text = seriesPlayback.text,
        )
        val tracksReady = exoPlayer.currentTracks.groups.isNotEmpty()
        if (!shouldApplySeriesTracks(
                tracksReady = tracksReady,
                request = request,
                applied = ui.appliedSeriesTracks,
                skipped = ui.skippedSeriesTracks,
            )
        ) {
            return@LaunchedEffect
        }
        val video = playlist.getOrNull(ui.currentIndex)
        val external = if (video == null) {
            null
        } else {
            withContext(Dispatchers.IO) { playbackSession.peekExternalSubtitle(video) }
        }
        if (ui.currentIndex != request.index) return@LaunchedEffect
        exoPlayer.applySeriesTrackDirective(
            seriesTrackDirective(
                audioLanguage = request.audioLanguage,
                text = request.text,
                hasExternalSubtitle = external != null,
            ),
        )
        ui.appliedSeriesTracks = request
    }
```

补上 `SeriesTextMemory`、`SeriesTrackApplyRequest`、`shouldApplySeriesTracks`、`seriesTrackDirective`、`applySeriesTrackDirective` 的 import。`Dispatchers` 和 `withContext` 这个文件里已经有。

- [ ] **Step 4: Compile and re-run pure tests**

Run: `./gradlew :app:compileFullDebugKotlin :app:testFullDebugUnitTest --tests com.pfpt.localvideoplayer.player.SeriesPlaybackLogicTest`

Expected: 编译成功，测试 PASS。

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/pfpt/localvideoplayer/ui/PlayerUiState.kt app/src/main/java/com/pfpt/localvideoplayer/ui/player/PlayerSessionEffects.kt app/src/main/java/com/pfpt/localvideoplayer/ui/player/PlayerOverlaySheets.kt
git commit -m "$(cat <<'EOF'
feat: restore a series audio and subtitle choice on the next episode

EOF
)"
```
