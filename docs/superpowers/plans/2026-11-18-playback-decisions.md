# Playback Decisions Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把时长选择、手势 seek 门、倍速进出、软解恢复收成纯函数，界面只执行返回值，播放行为保持现状。

**Architecture:** 新增 `PlaybackDecisions.kt`，与 `SeekTarget`、`PlaybackSessionLogic` 一样不持有播放器。`PlayerScreen`、`PlayerChrome`、`PlayerSessionEffects` 把现有分支换成这些函数的调用。`PlaybackSession` 继续管打开、复用、续播、睡眠、字幕和进度保存。

**Tech Stack:** Kotlin、JUnit 4、Android Gradle `fullDebug` 单元测试。

## Global Constraints

- 播放行为保持现状。
- `PlaybackDecisions.kt` 不引用 ExoPlayer，也不引用 Compose。
- `SeekTarget` 的计算公式不动。
- `isSoftwareDecodeRetryable` 和 `isSourceReadFailure` 继续负责错误码分类。
- 进度条拖动和投屏 seek 不调用 `seekCommit`。
- TextureView / SurfaceView 继续只跟 `useTextureView` 走。
- 长按加速下限是 `MIN_SPEED_BOOST = 2.0f`。
- 新测试是 JVM 单测，不使用 Robolectric。
- 实现代码不添加注释。
- `PlayerScreen` 不在这一轮拆分。亮度、音量、全屏、画中画、选集、片头片尾、缩放、面板不动。
- `handleBack` 里的 `ui.resetSpeedBoost()` 保持原样。

## File Structure

- Create: `app/src/main/java/com/pfpt/localvideoplayer/player/PlaybackDecisions.kt` — 四个决定的纯函数和它们的返回类型。
- Create: `app/src/test/java/com/pfpt/localvideoplayer/player/PlaybackDecisionsTest.kt` — 上述函数的 JVM 测试。
- Modify: `app/src/main/java/com/pfpt/localvideoplayer/ui/PlayerScreen.kt` — 时长、默认倍速、`commitSeek`。
- Modify: `app/src/main/java/com/pfpt/localvideoplayer/ui/player/PlayerSessionEffects.kt` — 时长回填回写、离开界面时的倍速、解码失败分支。
- Modify: `app/src/main/java/com/pfpt/localvideoplayer/ui/player/PlayerChrome.kt` — 长按开始和结束。

---

### Task 1: 时长决定

**Files:**
- Create: `app/src/test/java/com/pfpt/localvideoplayer/player/PlaybackDecisionsTest.kt`
- Create: `app/src/main/java/com/pfpt/localvideoplayer/player/PlaybackDecisions.kt`
- Test: `app/src/test/java/com/pfpt/localvideoplayer/player/PlaybackDecisionsTest.kt`

**Interfaces:**
- Consumes: 无。
- Produces:
  - `fun resolvedDurationMs(liveDurationMs: Long, catalogDurationMs: Long): Long`
  - `fun displayDurationMs(isCasting: Boolean, castDurationMs: Long, localDurationMs: Long): Long`
  - `fun shouldBackfillDuration(catalogDurationMs: Long, liveDurationMs: Long): Boolean`
  - `fun shouldPublishKnownDuration(displayDurationMs: Long, catalogDurationMs: Long): Boolean`

- [ ] **Step 1: Write the failing test**

创建 `app/src/test/java/com/pfpt/localvideoplayer/player/PlaybackDecisionsTest.kt`：

```kotlin
package com.pfpt.localvideoplayer.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackDecisionsTest {
    @Test
    fun liveDurationWinsWhenPositive() {
        assertEquals(8_000L, resolvedDurationMs(liveDurationMs = 8_000L, catalogDurationMs = 1_000L))
    }

    @Test
    fun catalogDurationUsedWhenLiveIsMissing() {
        assertEquals(5_000L, resolvedDurationMs(liveDurationMs = 0L, catalogDurationMs = 5_000L))
        assertEquals(5_000L, resolvedDurationMs(liveDurationMs = -1L, catalogDurationMs = 5_000L))
    }

    @Test
    fun missingDurationsResolveToZero() {
        assertEquals(0L, resolvedDurationMs(liveDurationMs = 0L, catalogDurationMs = 0L))
        assertEquals(0L, resolvedDurationMs(liveDurationMs = -1L, catalogDurationMs = -5L))
    }

    @Test
    fun castDurationWinsWhileCasting() {
        assertEquals(
            9_000L,
            displayDurationMs(isCasting = true, castDurationMs = 9_000L, localDurationMs = 4_000L),
        )
    }

    @Test
    fun localDurationUsedWhenCastDurationIsMissing() {
        assertEquals(
            4_000L,
            displayDurationMs(isCasting = false, castDurationMs = 9_000L, localDurationMs = 4_000L),
        )
        assertEquals(
            4_000L,
            displayDurationMs(isCasting = true, castDurationMs = 0L, localDurationMs = 4_000L),
        )
    }

    @Test
    fun backfillOnlyWhenBothDurationsAreMissing() {
        assertTrue(shouldBackfillDuration(catalogDurationMs = 0L, liveDurationMs = 0L))
        assertFalse(shouldBackfillDuration(catalogDurationMs = 1L, liveDurationMs = 0L))
        assertFalse(shouldBackfillDuration(catalogDurationMs = 0L, liveDurationMs = 1L))
    }

    @Test
    fun publishOnlyWhenScreenKnowsDurationAndCatalogDoesNot() {
        assertTrue(shouldPublishKnownDuration(displayDurationMs = 3_000L, catalogDurationMs = 0L))
        assertFalse(shouldPublishKnownDuration(displayDurationMs = 0L, catalogDurationMs = 0L))
        assertFalse(shouldPublishKnownDuration(displayDurationMs = 3_000L, catalogDurationMs = 3_000L))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testFullDebugUnitTest --tests com.pfpt.localvideoplayer.player.PlaybackDecisionsTest`

Expected: FAIL，编译错误包含 `Unresolved reference 'resolvedDurationMs'`。

- [ ] **Step 3: Write minimal implementation**

创建 `app/src/main/java/com/pfpt/localvideoplayer/player/PlaybackDecisions.kt`：

```kotlin
package com.pfpt.localvideoplayer.player

fun resolvedDurationMs(liveDurationMs: Long, catalogDurationMs: Long): Long {
    if (liveDurationMs > 0L) return liveDurationMs
    return catalogDurationMs.coerceAtLeast(0L)
}

fun displayDurationMs(isCasting: Boolean, castDurationMs: Long, localDurationMs: Long): Long {
    if (isCasting && castDurationMs > 0L) return castDurationMs
    return localDurationMs
}

fun shouldBackfillDuration(catalogDurationMs: Long, liveDurationMs: Long): Boolean {
    return catalogDurationMs <= 0L && liveDurationMs <= 0L
}

fun shouldPublishKnownDuration(displayDurationMs: Long, catalogDurationMs: Long): Boolean {
    return displayDurationMs > 0L && catalogDurationMs <= 0L
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testFullDebugUnitTest --tests com.pfpt.localvideoplayer.player.PlaybackDecisionsTest`

Expected: PASS，`BUILD SUCCESSFUL`。

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/pfpt/localvideoplayer/player/PlaybackDecisions.kt app/src/test/java/com/pfpt/localvideoplayer/player/PlaybackDecisionsTest.kt
git commit -m "$(cat <<'EOF'
Move duration selection into pure playback decisions.

EOF
)"
```

---

### Task 2: 界面改用时长决定

**Files:**
- Modify: `app/src/main/java/com/pfpt/localvideoplayer/ui/PlayerScreen.kt:58-59`
- Modify: `app/src/main/java/com/pfpt/localvideoplayer/ui/PlayerScreen.kt:271-284`
- Modify: `app/src/main/java/com/pfpt/localvideoplayer/ui/player/PlayerSessionEffects.kt:317-334`
- Test: `app/src/test/java/com/pfpt/localvideoplayer/player/PlaybackDecisionsTest.kt`

**Interfaces:**
- Consumes: Task 1 的四个时长函数。
- Produces: 播放界面的本地 `resolvedDurationMs()` 和 `displayDurationMs` 改为调用纯函数。回填和回写改为调用布尔函数。

- [ ] **Step 1: 替换播放界面的时长选择**

在 `PlayerScreen.kt` 的 import 区，紧挨现有的 `seekTargetMs` import，加入：

```kotlin
import com.pfpt.localvideoplayer.player.displayDurationMs as playbackDisplayDurationMs
import com.pfpt.localvideoplayer.player.resolvedDurationMs as playbackResolvedDurationMs
```

把 `PlayerScreenContent` 里的本地函数和 `displayDurationMs` 换成：

```kotlin
fun resolvedDurationMs(): Long {
    return playbackResolvedDurationMs(
        liveDurationMs = progress.durationMs,
        catalogDurationMs = playlist.getOrNull(ui.currentIndex)?.durationMs ?: 0L,
    )
}
val displayDurationMs = playbackDisplayDurationMs(
    isCasting = isCasting,
    castDurationMs = castDurationMs,
    localDurationMs = resolvedDurationMs(),
)
```

保留紧随其后的这段，不要改：

```kotlin
LaunchedEffect(displayDurationMs, isCasting) {
    if (isCasting && displayDurationMs > 0) {
        progress.durationMs = displayDurationMs
    }
}
```

- [ ] **Step 2: 替换回填和回写**

在 `PlayerSessionEffects.kt` 加入：

```kotlin
import com.pfpt.localvideoplayer.player.shouldBackfillDuration
import com.pfpt.localvideoplayer.player.shouldPublishKnownDuration
```

把文件末尾两个 `LaunchedEffect` 换成：

```kotlin
LaunchedEffect(currentVideo?.uri) {
    val video = currentVideo ?: return@LaunchedEffect
    if (!shouldBackfillDuration(catalogDurationMs = video.durationMs, liveDurationMs = progress.durationMs)) {
        return@LaunchedEffect
    }
    val duration = withContext(Dispatchers.IO) {
        durationResolver.resolveAll(listOf(video))[video.uri.toString()] ?: 0L
    }
    if (duration > 0L && progress.durationMs <= 0L) {
        progress.durationMs = duration
        onDurationKnown(video.uri.toString(), duration)
    }
}
LaunchedEffect(displayDurationMs, currentVideo?.uri) {
    val video = currentVideo ?: return@LaunchedEffect
    if (!shouldPublishKnownDuration(displayDurationMs = displayDurationMs, catalogDurationMs = video.durationMs)) {
        return@LaunchedEffect
    }
    onDurationKnown(video.uri.toString(), displayDurationMs)
}
```

- [ ] **Step 3: 编译并重跑时长测试**

Run: `./gradlew :app:compileFullDebugKotlin :app:testFullDebugUnitTest --tests com.pfpt.localvideoplayer.player.PlaybackDecisionsTest`

Expected: PASS，`BUILD SUCCESSFUL`。

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/pfpt/localvideoplayer/ui/PlayerScreen.kt app/src/main/java/com/pfpt/localvideoplayer/ui/player/PlayerSessionEffects.kt
git commit -m "$(cat <<'EOF'
Use playback duration decisions in the player screen.

EOF
)"
```

---

### Task 3: Seek 门

**Files:**
- Modify: `app/src/test/java/com/pfpt/localvideoplayer/player/PlaybackDecisionsTest.kt`
- Modify: `app/src/main/java/com/pfpt/localvideoplayer/player/PlaybackDecisions.kt`
- Test: `app/src/test/java/com/pfpt/localvideoplayer/player/PlaybackDecisionsTest.kt`

**Interfaces:**
- Consumes: 无。相对位移仍由已有的 `seekTargetMs` 计算，本任务不修改它。
- Produces:
  - `sealed interface SeekCommit`
  - `data class Apply(val positionMs: Long) : SeekCommit`
  - `data object Reject : SeekCommit`
  - `fun seekCommit(targetMs: Long, playerDurationMs: Long, resolvedDurationMs: Long, currentPositionMs: Long, seekable: Boolean): SeekCommit`

- [ ] **Step 1: Write the failing test**

在 `PlaybackDecisionsTest` 末尾追加：

```kotlin
@Test
fun knownPlayerDurationAlwaysApplies() {
    assertEquals(
        SeekCommit.Apply(1_000L),
        seekCommit(
            targetMs = 1_000L,
            playerDurationMs = 9_000L,
            resolvedDurationMs = 0L,
            currentPositionMs = 4_000L,
            seekable = false,
        ),
    )
}

@Test
fun resolvedDurationAppliesWhenPlayerDurationIsMissing() {
    assertEquals(
        SeekCommit.Apply(1_000L),
        seekCommit(
            targetMs = 1_000L,
            playerDurationMs = 0L,
            resolvedDurationMs = 9_000L,
            currentPositionMs = 4_000L,
            seekable = false,
        ),
    )
}

@Test
fun seekableUnknownDurationAlwaysApplies() {
    assertEquals(
        SeekCommit.Apply(500L),
        seekCommit(
            targetMs = 500L,
            playerDurationMs = 0L,
            resolvedDurationMs = 0L,
            currentPositionMs = 4_000L,
            seekable = true,
        ),
    )
}

@Test
fun unseekableUnknownDurationAppliesOnlyForward() {
    assertEquals(
        SeekCommit.Apply(5_000L),
        seekCommit(
            targetMs = 5_000L,
            playerDurationMs = -1L,
            resolvedDurationMs = 0L,
            currentPositionMs = 4_000L,
            seekable = false,
        ),
    )
    assertEquals(
        SeekCommit.Reject,
        seekCommit(
            targetMs = 4_000L,
            playerDurationMs = 0L,
            resolvedDurationMs = 0L,
            currentPositionMs = 4_000L,
            seekable = false,
        ),
    )
    assertEquals(
        SeekCommit.Reject,
        seekCommit(
            targetMs = 3_000L,
            playerDurationMs = 0L,
            resolvedDurationMs = 0L,
            currentPositionMs = 4_000L,
            seekable = false,
        ),
    )
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testFullDebugUnitTest --tests com.pfpt.localvideoplayer.player.PlaybackDecisionsTest`

Expected: FAIL，编译错误包含 `Unresolved reference 'seekCommit'`。

- [ ] **Step 3: Write minimal implementation**

在 `PlaybackDecisions.kt` 末尾追加：

```kotlin
sealed interface SeekCommit {
    data class Apply(val positionMs: Long) : SeekCommit
    data object Reject : SeekCommit
}

fun seekCommit(
    targetMs: Long,
    playerDurationMs: Long,
    resolvedDurationMs: Long,
    currentPositionMs: Long,
    seekable: Boolean,
): SeekCommit {
    val duration = if (playerDurationMs > 0L) playerDurationMs else resolvedDurationMs
    if (duration <= 0L && !seekable && targetMs <= currentPositionMs) {
        return SeekCommit.Reject
    }
    return SeekCommit.Apply(targetMs)
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testFullDebugUnitTest --tests com.pfpt.localvideoplayer.player.PlaybackDecisionsTest`

Expected: PASS，`BUILD SUCCESSFUL`。

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/pfpt/localvideoplayer/player/PlaybackDecisions.kt app/src/test/java/com/pfpt/localvideoplayer/player/PlaybackDecisionsTest.kt
git commit -m "$(cat <<'EOF'
Move the seek commit gate into pure playback decisions.

EOF
)"
```

---

### Task 4: 手势和相对快进改用 Seek 门

**Files:**
- Modify: `app/src/main/java/com/pfpt/localvideoplayer/ui/PlayerScreen.kt:389-399`
- Test: `app/src/test/java/com/pfpt/localvideoplayer/player/PlaybackDecisionsTest.kt`

**Interfaces:**
- Consumes: `seekCommit` 和 `SeekCommit`。`seekBy` 继续先调用现有的 `seekTargetMs`，再调用本地 `commitSeek`。
- Produces: `commitSeek` 在 `Reject` 时返回 false，在 `Apply` 时 seek 到返回的位置。

- [ ] **Step 1: 替换 commitSeek**

在 `PlayerScreen.kt` 加入：

```kotlin
import com.pfpt.localvideoplayer.player.SeekCommit
import com.pfpt.localvideoplayer.player.seekCommit
```

把 `commitSeek` 换成：

```kotlin
fun commitSeek(targetMs: Long): Boolean {
    val decision = seekCommit(
        targetMs = targetMs,
        playerDurationMs = exoPlayer.duration,
        resolvedDurationMs = resolvedDurationMs(),
        currentPositionMs = exoPlayer.currentPosition.coerceAtLeast(0L),
        seekable = exoPlayer.isCurrentMediaItemSeekable,
    )
    return when (decision) {
        SeekCommit.Reject -> false
        is SeekCommit.Apply -> {
            exoPlayer.seekTo(decision.positionMs)
            progress.currentPositionMs = decision.positionMs
            ui.previewPositionMs = decision.positionMs
            true
        }
    }
}
```

`seekBy` 保持「先 `seekTargetMs`，失败就 return，成功再写双击提示」。不要修改 `PlayerChrome.seekToPosition`。

- [ ] **Step 2: 编译并重跑测试**

Run: `./gradlew :app:compileFullDebugKotlin :app:testFullDebugUnitTest --tests com.pfpt.localvideoplayer.player.PlaybackDecisionsTest`

Expected: PASS，`BUILD SUCCESSFUL`。

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/pfpt/localvideoplayer/ui/PlayerScreen.kt
git commit -m "$(cat <<'EOF'
Use the seek commit gate for gesture and relative seeks.

EOF
)"
```

---

### Task 5: 倍速决定

**Files:**
- Modify: `app/src/test/java/com/pfpt/localvideoplayer/player/PlaybackDecisionsTest.kt`
- Modify: `app/src/main/java/com/pfpt/localvideoplayer/player/PlaybackDecisions.kt`
- Test: `app/src/test/java/com/pfpt/localvideoplayer/player/PlaybackDecisionsTest.kt`

**Interfaces:**
- Consumes: 无。
- Produces:
  - `const val MIN_SPEED_BOOST = 2.0f`
  - `data class SpeedSnapshot(val playbackSpeed: Float, val savedPlaybackSpeed: Float, val boosted: Boolean)`
  - `sealed interface SpeedChange`
  - `data class Set(val playbackSpeed: Float, val savedPlaybackSpeed: Float, val boosted: Boolean, val playIfPaused: Boolean) : SpeedChange`
  - `data object Unchanged : SpeedChange`
  - `fun speedAfterDefault(snapshot: SpeedSnapshot, defaultSpeed: Float): SpeedSnapshot`
  - `fun speedBoostStart(snapshot: SpeedSnapshot, isPlaying: Boolean): SpeedChange`
  - `fun speedBoostEnd(snapshot: SpeedSnapshot): SpeedSnapshot`
  - `fun speedToRestoreOnLeave(snapshot: SpeedSnapshot): Float?`

- [ ] **Step 1: Write the failing test**

在 `PlaybackDecisionsTest.kt` 增加 `import org.junit.Assert.assertNull`，并在类末尾追加：

```kotlin
@Test
fun defaultSpeedReplacesBothValuesWhenNotBoosted() {
    val next = speedAfterDefault(
        snapshot = SpeedSnapshot(playbackSpeed = 1.5f, savedPlaybackSpeed = 1.5f, boosted = false),
        defaultSpeed = 1.25f,
    )
    assertEquals(1.25f, next.playbackSpeed, 0f)
    assertEquals(1.25f, next.savedPlaybackSpeed, 0f)
    assertFalse(next.boosted)
}

@Test
fun defaultSpeedLeavesBoostedSnapshotUntouched() {
    val current = SpeedSnapshot(playbackSpeed = 2f, savedPlaybackSpeed = 1.5f, boosted = true)
    assertEquals(current, speedAfterDefault(current, defaultSpeed = 1.25f))
}

@Test
fun boostFromNormalSpeedUsesFloorAndPlaysWhenPaused() {
    val change = speedBoostStart(
        snapshot = SpeedSnapshot(playbackSpeed = 1f, savedPlaybackSpeed = 1f, boosted = false),
        isPlaying = false,
    )
    assertEquals(
        SpeedChange.Set(
            playbackSpeed = 2f,
            savedPlaybackSpeed = 1f,
            boosted = true,
            playIfPaused = true,
        ),
        change,
    )
}

@Test
fun boostWhileAlreadyPlayingDoesNotRequestPlay() {
    val change = speedBoostStart(
        snapshot = SpeedSnapshot(playbackSpeed = 1f, savedPlaybackSpeed = 1f, boosted = false),
        isPlaying = true,
    )
    assertEquals(
        SpeedChange.Set(
            playbackSpeed = 2f,
            savedPlaybackSpeed = 1f,
            boosted = true,
            playIfPaused = false,
        ),
        change,
    )
}

@Test
fun boostKeepsSpeedAboveFloor() {
    val change = speedBoostStart(
        snapshot = SpeedSnapshot(playbackSpeed = 3f, savedPlaybackSpeed = 1f, boosted = false),
        isPlaying = true,
    )
    assertEquals(
        SpeedChange.Set(
            playbackSpeed = 3f,
            savedPlaybackSpeed = 3f,
            boosted = true,
            playIfPaused = false,
        ),
        change,
    )
}

@Test
fun repeatedBoostIsUnchanged() {
    assertEquals(
        SpeedChange.Unchanged,
        speedBoostStart(
            snapshot = SpeedSnapshot(playbackSpeed = 2f, savedPlaybackSpeed = 1f, boosted = true),
            isPlaying = false,
        ),
    )
}

@Test
fun endingBoostRestoresSavedSpeed() {
    assertEquals(
        SpeedSnapshot(playbackSpeed = 1.5f, savedPlaybackSpeed = 1.5f, boosted = false),
        speedBoostEnd(SpeedSnapshot(playbackSpeed = 2f, savedPlaybackSpeed = 1.5f, boosted = true)),
    )
}

@Test
fun endingBoostWithoutActiveBoostReturnsSnapshot() {
    val current = SpeedSnapshot(playbackSpeed = 1.25f, savedPlaybackSpeed = 1.25f, boosted = false)
    assertEquals(current, speedBoostEnd(current))
}

@Test
fun leaveRestoresSavedSpeedOnlyWhileBoosted() {
    assertEquals(
        1.5f,
        speedToRestoreOnLeave(SpeedSnapshot(playbackSpeed = 2f, savedPlaybackSpeed = 1.5f, boosted = true)),
        0f,
    )
    assertNull(
        speedToRestoreOnLeave(SpeedSnapshot(playbackSpeed = 1.5f, savedPlaybackSpeed = 1.5f, boosted = false)),
    )
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testFullDebugUnitTest --tests com.pfpt.localvideoplayer.player.PlaybackDecisionsTest`

Expected: FAIL，编译错误包含 `Unresolved reference 'speedAfterDefault'`。

- [ ] **Step 3: Write minimal implementation**

在 `PlaybackDecisions.kt` 顶部加入 `import kotlin.math.max`，在文件末尾追加：

```kotlin
const val MIN_SPEED_BOOST = 2.0f

data class SpeedSnapshot(
    val playbackSpeed: Float,
    val savedPlaybackSpeed: Float,
    val boosted: Boolean,
)

sealed interface SpeedChange {
    data class Set(
        val playbackSpeed: Float,
        val savedPlaybackSpeed: Float,
        val boosted: Boolean,
        val playIfPaused: Boolean,
    ) : SpeedChange
    data object Unchanged : SpeedChange
}

fun speedAfterDefault(snapshot: SpeedSnapshot, defaultSpeed: Float): SpeedSnapshot {
    if (snapshot.boosted) return snapshot
    return snapshot.copy(
        playbackSpeed = defaultSpeed,
        savedPlaybackSpeed = defaultSpeed,
    )
}

fun speedBoostStart(snapshot: SpeedSnapshot, isPlaying: Boolean): SpeedChange {
    if (snapshot.boosted) return SpeedChange.Unchanged
    val saved = snapshot.playbackSpeed
    return SpeedChange.Set(
        playbackSpeed = max(MIN_SPEED_BOOST, saved),
        savedPlaybackSpeed = saved,
        boosted = true,
        playIfPaused = !isPlaying,
    )
}

fun speedBoostEnd(snapshot: SpeedSnapshot): SpeedSnapshot {
    if (!snapshot.boosted) return snapshot
    return snapshot.copy(
        playbackSpeed = snapshot.savedPlaybackSpeed,
        boosted = false,
    )
}

fun speedToRestoreOnLeave(snapshot: SpeedSnapshot): Float? {
    if (!snapshot.boosted) return null
    return snapshot.savedPlaybackSpeed
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testFullDebugUnitTest --tests com.pfpt.localvideoplayer.player.PlaybackDecisionsTest`

Expected: PASS，`BUILD SUCCESSFUL`。

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/pfpt/localvideoplayer/player/PlaybackDecisions.kt app/src/test/java/com/pfpt/localvideoplayer/player/PlaybackDecisionsTest.kt
git commit -m "$(cat <<'EOF'
Move speed boost decisions into pure playback decisions.

EOF
)"
```

---

### Task 6: 界面改用倍速决定

**Files:**
- Modify: `app/src/main/java/com/pfpt/localvideoplayer/ui/PlayerScreen.kt:250-257`
- Modify: `app/src/main/java/com/pfpt/localvideoplayer/ui/player/PlayerChrome.kt:13-51`
- Modify: `app/src/main/java/com/pfpt/localvideoplayer/ui/player/PlayerSessionEffects.kt:262-289`
- Test: `app/src/test/java/com/pfpt/localvideoplayer/player/PlaybackDecisionsTest.kt`

**Interfaces:**
- Consumes: `SpeedSnapshot`、`SpeedChange`、`speedAfterDefault`、`speedBoostStart`、`speedBoostEnd`、`speedToRestoreOnLeave`。
- Produces: 三个调用点把数字写回 `PlayerUiState`。`playIfPaused` 为真才 `play()`。`Unchanged` 不改状态。离开界面时先恢复播放器速度，离开流程末尾再用 `speedBoostEnd` 清界面状态。

- [ ] **Step 1: 替换默认倍速**

在 `PlayerScreen.kt` 加入：

```kotlin
import com.pfpt.localvideoplayer.player.SpeedSnapshot
import com.pfpt.localvideoplayer.player.speedAfterDefault
```

把默认速度的 `LaunchedEffect` 换成：

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

不要改 `handleBack` 里的 `ui.resetSpeedBoost()`。

- [ ] **Step 2: 替换长按开始和结束**

在 `PlayerChrome.kt` 删除 `private const val MIN_SPEED_BOOST = 2.0f` 和 `import kotlin.math.max`。加入：

```kotlin
import com.pfpt.localvideoplayer.player.SpeedChange
import com.pfpt.localvideoplayer.player.SpeedSnapshot
import com.pfpt.localvideoplayer.player.speedBoostEnd
import com.pfpt.localvideoplayer.player.speedBoostStart
```

把 `onSpeedBoostStart` 和 `onSpeedBoostEnd` 换成：

```kotlin
onSpeedBoostStart = {
    val change = speedBoostStart(
        snapshot = SpeedSnapshot(
            playbackSpeed = ui.playbackSpeed,
            savedPlaybackSpeed = ui.savedPlaybackSpeed,
            boosted = ui.isSpeedBoosted,
        ),
        isPlaying = exoPlayer.isPlaying,
    )
    when (change) {
        SpeedChange.Unchanged -> Unit
        is SpeedChange.Set -> {
            ui.savedPlaybackSpeed = change.savedPlaybackSpeed
            ui.playbackSpeed = change.playbackSpeed
            ui.isSpeedBoosted = change.boosted
            if (change.playIfPaused) {
                exoPlayer.play()
            }
        }
    }
},
onSpeedBoostEnd = {
    val next = speedBoostEnd(
        SpeedSnapshot(
            playbackSpeed = ui.playbackSpeed,
            savedPlaybackSpeed = ui.savedPlaybackSpeed,
            boosted = ui.isSpeedBoosted,
        ),
    )
    ui.playbackSpeed = next.playbackSpeed
    ui.savedPlaybackSpeed = next.savedPlaybackSpeed
    ui.isSpeedBoosted = next.boosted
},
```

`seekToPosition` 保持投屏走 `CastManager`、本地直接 `seekTo`。

- [ ] **Step 3: 替换离开界面时的速度恢复**

在 `PlayerSessionEffects.kt` 加入：

```kotlin
import com.pfpt.localvideoplayer.player.SpeedSnapshot
import com.pfpt.localvideoplayer.player.speedBoostEnd
import com.pfpt.localvideoplayer.player.speedToRestoreOnLeave
```

在 `DisposableEffect(exoPlayer)` 的 `onDispose` 里，`if (stillOwned)` 之前读取：

```kotlin
val leavingSpeed = SpeedSnapshot(
    playbackSpeed = ui.playbackSpeed,
    savedPlaybackSpeed = ui.savedPlaybackSpeed,
    boosted = ui.isSpeedBoosted,
)
```

把 `stillOwned` 内的：

```kotlin
if (ui.isSpeedBoosted) {
    exoPlayer.setPlaybackSpeed(ui.savedPlaybackSpeed)
}
```

换成：

```kotlin
speedToRestoreOnLeave(leavingSpeed)?.let { exoPlayer.setPlaybackSpeed(it) }
```

把 `onDispose` 后部的 `ui.resetSpeedBoost()` 换成：

```kotlin
val ended = speedBoostEnd(leavingSpeed)
ui.playbackSpeed = ended.playbackSpeed
ui.savedPlaybackSpeed = ended.savedPlaybackSpeed
ui.isSpeedBoosted = ended.boosted
```

`LaunchedEffect(ui.playbackSpeed)` 里写入 `PlaybackParameters` 的代码不要改。

- [ ] **Step 4: 编译并重跑测试**

Run: `./gradlew :app:compileFullDebugKotlin :app:testFullDebugUnitTest --tests com.pfpt.localvideoplayer.player.PlaybackDecisionsTest`

Expected: PASS，`BUILD SUCCESSFUL`。

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/pfpt/localvideoplayer/ui/PlayerScreen.kt app/src/main/java/com/pfpt/localvideoplayer/ui/player/PlayerChrome.kt app/src/main/java/com/pfpt/localvideoplayer/ui/player/PlayerSessionEffects.kt
git commit -m "$(cat <<'EOF'
Use playback speed decisions in the player chrome.

EOF
)"
```

---

### Task 7: 软解恢复决定

**Files:**
- Modify: `app/src/test/java/com/pfpt/localvideoplayer/player/PlaybackDecisionsTest.kt`
- Modify: `app/src/main/java/com/pfpt/localvideoplayer/player/PlaybackDecisions.kt`
- Test: `app/src/test/java/com/pfpt/localvideoplayer/player/PlaybackDecisionsTest.kt`

**Interfaces:**
- Consumes: 调用方传入的五个布尔。错误码仍由 `PlaybackException.isSoftwareDecodeRetryable` 和 `PlaybackException.isSourceReadFailure` 计算。
- Produces:
  - `enum class DecoderRecovery { RetrySoft, Ignore, FailRead, FailDecode }`
  - `fun decoderRecovery(retryable: Boolean, sourceRead: Boolean, appliedSoftDecode: Boolean, alreadyRetried: Boolean, retryPending: Boolean): DecoderRecovery`

- [ ] **Step 1: Write the failing test**

在 `PlaybackDecisionsTest` 末尾追加：

```kotlin
@Test
fun firstRetryableFailureRecoversWithSoftwareDecoder() {
    assertEquals(
        DecoderRecovery.RetrySoft,
        decoderRecovery(
            retryable = true,
            sourceRead = false,
            appliedSoftDecode = false,
            alreadyRetried = false,
            retryPending = true,
        ),
    )
}

@Test
fun retryableFailureAfterSoftwareDecodeWaitsOrFails() {
    assertEquals(
        DecoderRecovery.Ignore,
        decoderRecovery(
            retryable = true,
            sourceRead = true,
            appliedSoftDecode = true,
            alreadyRetried = false,
            retryPending = true,
        ),
    )
    assertEquals(
        DecoderRecovery.FailRead,
        decoderRecovery(
            retryable = true,
            sourceRead = true,
            appliedSoftDecode = false,
            alreadyRetried = true,
            retryPending = false,
        ),
    )
    assertEquals(
        DecoderRecovery.FailDecode,
        decoderRecovery(
            retryable = true,
            sourceRead = false,
            appliedSoftDecode = true,
            alreadyRetried = true,
            retryPending = false,
        ),
    )
}

@Test
fun pendingRetryIgnoresFurtherErrors() {
    assertEquals(
        DecoderRecovery.Ignore,
        decoderRecovery(
            retryable = false,
            sourceRead = true,
            appliedSoftDecode = true,
            alreadyRetried = true,
            retryPending = true,
        ),
    )
}

@Test
fun terminalFailureFollowsSourceReadFlag() {
    assertEquals(
        DecoderRecovery.FailRead,
        decoderRecovery(
            retryable = false,
            sourceRead = true,
            appliedSoftDecode = false,
            alreadyRetried = false,
            retryPending = false,
        ),
    )
    assertEquals(
        DecoderRecovery.FailDecode,
        decoderRecovery(
            retryable = false,
            sourceRead = false,
            appliedSoftDecode = false,
            alreadyRetried = false,
            retryPending = false,
        ),
    )
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testFullDebugUnitTest --tests com.pfpt.localvideoplayer.player.PlaybackDecisionsTest`

Expected: FAIL，编译错误包含 `Unresolved reference 'decoderRecovery'`。

- [ ] **Step 3: Write minimal implementation**

在 `PlaybackDecisions.kt` 末尾追加：

```kotlin
enum class DecoderRecovery {
    RetrySoft,
    Ignore,
    FailRead,
    FailDecode,
}

fun decoderRecovery(
    retryable: Boolean,
    sourceRead: Boolean,
    appliedSoftDecode: Boolean,
    alreadyRetried: Boolean,
    retryPending: Boolean,
): DecoderRecovery {
    if (retryable && !appliedSoftDecode && !alreadyRetried) {
        return DecoderRecovery.RetrySoft
    }
    if (retryPending) return DecoderRecovery.Ignore
    return if (sourceRead) DecoderRecovery.FailRead else DecoderRecovery.FailDecode
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testFullDebugUnitTest --tests com.pfpt.localvideoplayer.player.PlaybackDecisionsTest`

Expected: PASS，`BUILD SUCCESSFUL`。

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/pfpt/localvideoplayer/player/PlaybackDecisions.kt app/src/test/java/com/pfpt/localvideoplayer/player/PlaybackDecisionsTest.kt
git commit -m "$(cat <<'EOF'
Move decoder recovery into pure playback decisions.

EOF
)"
```

---

### Task 8: 解码失败改用软解恢复

**Files:**
- Modify: `app/src/main/java/com/pfpt/localvideoplayer/ui/player/PlayerSessionEffects.kt:88-112`
- Test: `app/src/test/java/com/pfpt/localvideoplayer/player/PlaybackDecisionsTest.kt`

**Interfaces:**
- Consumes: `decoderRecovery` 和 `DecoderRecovery`。`retryable` 来自 `error.isSoftwareDecodeRetryable()`，`sourceRead` 来自 `error.isSourceReadFailure()`。
- Produces: `handlePlayerError` 按返回值执行现有副作用。`STATE_READY` 清失败和等待标记的监听保持原样。

- [ ] **Step 1: 替换 handlePlayerError**

在 `PlayerSessionEffects.kt` 加入：

```kotlin
import com.pfpt.localvideoplayer.player.DecoderRecovery
import com.pfpt.localvideoplayer.player.decoderRecovery
```

把 `handlePlayerError` 换成：

```kotlin
fun handlePlayerError(error: PlaybackException) {
    when (
        decoderRecovery(
            retryable = error.isSoftwareDecodeRetryable(),
            sourceRead = error.isSourceReadFailure(),
            appliedSoftDecode = ui.appliedSoftDecode,
            alreadyRetried = ui.softDecodeRetried,
            retryPending = ui.softDecodeRetryPending,
        )
    ) {
        DecoderRecovery.RetrySoft -> {
            ui.softDecodeRetried = true
            ui.softDecodeRetryPending = true
            ui.playbackFailure = null
            ui.replayPositionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
            playbackSession.resumedIndices.remove(ui.currentIndex)
            scope.launch {
                runCatching { saveCurrentProgress() }
                ui.appliedSoftDecode = true
                ui.softDecodeGeneration += 1
            }
        }
        DecoderRecovery.Ignore -> Unit
        DecoderRecovery.FailRead -> ui.playbackFailure = PlaybackFailure.Read
        DecoderRecovery.FailDecode -> ui.playbackFailure = PlaybackFailure.Decode
    }
}
```

不要改 `onPlaybackStateChanged` 里 `STATE_READY` 对 `playbackFailure` 和 `softDecodeRetryPending` 的清理。

- [ ] **Step 2: 编译并重跑全部新测试**

Run: `./gradlew :app:compileFullDebugKotlin :app:testFullDebugUnitTest --tests com.pfpt.localvideoplayer.player.PlaybackDecisionsTest`

Expected: PASS，`BUILD SUCCESSFUL`。

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/pfpt/localvideoplayer/ui/player/PlayerSessionEffects.kt
git commit -m "$(cat <<'EOF'
Use decoder recovery for playback failures.

EOF
)"
```
