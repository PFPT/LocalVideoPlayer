# Player Flight Page Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 飞入和返回时，圆角框还在动，播放页底色、黑底、控制条和竖屏剧集信息先不显示，过渡结束后同一帧出现。

**Architecture:** `hidePlayerPageForTransition` 在共享过渡进行中返回 true。竖屏、横屏和播放页根上在返回 true 时不组合这些层，圆角目标框和它的权重留着。过渡结束后这些层按原来的条件第一次组合，控制条不再播进入动画。

**Tech Stack:** Kotlin、Jetpack Compose、JUnit 4、Android Gradle `./gradlew :app:testFullDebugUnitTest`。

## Global Constraints

- 飞入播放页和返回时，圆角框还在动，画面上只留这个框。框落地后，播放页底色、画面黑底、控制条和竖屏下半部分的剧集信息同一帧出现。
- `hidePlayerPageForTransition(transitionActive)` 在过渡进行中返回 `true`。`transitionActive` 来自 `playerTransitionActive()`。没有共享范围时当作没在过渡。
- 返回 `true` 时：播放页根上不铺主题底色；竖屏画面区的两层黑底不铺；竖屏控制条、上下遮罩、片头片尾、拖动和双击的进度预览、亮度、音量和倍速提示不组合；竖屏下半部分留权重 `0.62` 的空位，剧集信息不组合，画面区保持权重 `0.38`；横屏两层黑底不铺，横屏控制条和同样的提示不组合，画面框仍是整页；投屏按钮和投屏小条不组合；倍速、更多和投屏设备弹层不组合，打开状态留着。
- 圆角目标框继续组合。视频表面仍只走 `hidePlayerSurfaceForTransition`。TextureView 的表面在飞行全程仍然显示。画中画那个分支不经过这个判断。投屏的视频表面保持现在的样子。
- 过渡结束、判断变成 `false` 的那一帧，这些层按原来的条件组合。控制条的淡入淡出留着。这一次是带着已经可见第一次组合，所以不播进入动画。返回时判断变成 `true`，这些层当帧离开组合。`controlsVisible` 不改。
- 换集不走导航过渡，这些层保持。没有匹配的缩略图时，共享过渡不进行，播放页按现在的方式出现。
- 单元测试覆盖两种：过渡中隐藏，未过渡显示。不使用 Compose 界面测试或 Robolectric。
- 实现代码不添加注释。
- 这一轮不动：`hidePlayerSurfaceForTransition` 和设置里的表面类型、圆角、共享 key 和哪一张缩略图注册、导航的淡入淡出时长、共享边界的框弹簧、缩略图的加载和占位规则、画中画和投屏的现有画面、控制条自己的显示计时、竖屏 `0.38` / `0.62` 的比例、播放决定和下载。
- 每个任务只提交该任务的代码。规格和本计划留在工作区，不放进这些提交。
- Gradle 命令不加 `--offline`。

## File Structure

- Modify: `app/src/main/java/com/pfpt/localvideoplayer/ui/PlayerSharedBounds.kt` — 页面隐藏判断。`playerTransitionActive()` 和 `hidePlayerSurfaceForTransition` 的函数体不改。
- Create: `app/src/test/java/com/pfpt/localvideoplayer/ui/PlayerFlightPageTest.kt` — 两种对照。
- Modify: `app/src/main/java/com/pfpt/localvideoplayer/ui/components/PortraitPlayerLayout.kt` — 过渡中不铺黑底、不放控制层，下半部分留空位。
- Modify: `app/src/main/java/com/pfpt/localvideoplayer/ui/components/LandscapePlayerContent.kt` — 过渡中不铺黑底、不放控制层。
- Modify: `app/src/main/java/com/pfpt/localvideoplayer/ui/PlayerScreen.kt` — 过渡中不铺主题底色，不放弹层、双击预览、投屏按钮和投屏小条。
- 不改画中画分支里的 `FittedPlayerSurface`。不改播放失败文字。

---

### Task 1: 页面隐藏判断

**Files:**
- Create: `app/src/test/java/com/pfpt/localvideoplayer/ui/PlayerFlightPageTest.kt`
- Modify: `app/src/main/java/com/pfpt/localvideoplayer/ui/PlayerSharedBounds.kt`
- Test: `./gradlew :app:testFullDebugUnitTest --tests com.pfpt.localvideoplayer.ui.PlayerFlightPageTest`

**Interfaces:**
- Consumes: 无。
- Produces: `fun hidePlayerPageForTransition(transitionActive: Boolean): Boolean`。返回 `transitionActive`。

- [ ] **Step 1: Write the failing test**

把下面的文件写成 `app/src/test/java/com/pfpt/localvideoplayer/ui/PlayerFlightPageTest.kt`：

```kotlin
package com.pfpt.localvideoplayer.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerFlightPageTest {
    @Test
    fun pageHidesWhileTransitionRuns() {
        assertTrue(hidePlayerPageForTransition(transitionActive = true))
    }

    @Test
    fun pageShowsWhenTransitionIsIdle() {
        assertFalse(hidePlayerPageForTransition(transitionActive = false))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testFullDebugUnitTest --tests com.pfpt.localvideoplayer.ui.PlayerFlightPageTest`

Expected: FAIL，`Unresolved reference 'hidePlayerPageForTransition'`。

- [ ] **Step 3: Write minimal implementation**

在 `PlayerSharedBounds.kt` 的 `playerTransitionActive` 后面加上：

```kotlin
fun hidePlayerPageForTransition(transitionActive: Boolean): Boolean {
    return transitionActive
}
```

不改 `hidePlayerSurfaceForTransition` 和 `playerTransitionActive`。

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testFullDebugUnitTest --tests com.pfpt.localvideoplayer.ui.PlayerFlightPageTest`

Expected: BUILD SUCCESSFUL。

- [ ] **Step 5: Commit**

```bash
git add app/src/test/java/com/pfpt/localvideoplayer/ui/PlayerFlightPageTest.kt app/src/main/java/com/pfpt/localvideoplayer/ui/PlayerSharedBounds.kt
git commit -m "$(cat <<'EOF'
Decide when the player page hides during the flight.

EOF
)"
```

提交前用 `git status` 确认规格和本计划不在暂存区。

---

### Task 2: 过渡中不放播放页

**Files:**
- Modify: `app/src/main/java/com/pfpt/localvideoplayer/ui/components/PortraitPlayerLayout.kt`
- Modify: `app/src/main/java/com/pfpt/localvideoplayer/ui/components/LandscapePlayerContent.kt`
- Modify: `app/src/main/java/com/pfpt/localvideoplayer/ui/PlayerScreen.kt`
- Test: `./gradlew :app:testFullDebugUnitTest`

**Interfaces:**
- Consumes: `fun hidePlayerPageForTransition(transitionActive: Boolean): Boolean`。`@Composable fun playerTransitionActive(): Boolean`。
- Produces: 竖屏、横屏和播放页根上在判断为 true 时不组合页面层。圆角目标框继续组合。

不改 `controlsVisible`。不改 `hidePlayerSurfaceForTransition` 的调用。不改圆角目标框的条件。不给这些层加新的淡入。画中画分支里的 `FittedPlayerSurface` 保持原样。播放失败文字保持原样。

- [ ] **Step 1: Hide the portrait page during the flight**

`PortraitPlayerLayout.kt` 补上导入：

```kotlin
import com.pfpt.localvideoplayer.ui.hidePlayerPageForTransition
```

在 `val showChrome = controlsVisible || isSeeking` 后面加上：

```kotlin
val hidePage = hidePlayerPageForTransition(playerTransitionActive())
```

画面区外层 `Box` 的修饰符改成：

```kotlin
modifier = Modifier
    .fillMaxWidth()
    .weight(0.38f)
    .then(if (hidePage) Modifier else Modifier.background(Color.Black)),
```

`VideoPlayerGesture` 的修饰符改成：

```kotlin
modifier = Modifier
    .fillMaxSize()
    .then(if (hidePage) Modifier else Modifier.background(Color.Black)),
```

圆角目标框和 `FittedPlayerSurface` 的现有代码不动。

手势内容后面的两段 `AnimatedVisibility`、`BrightnessFeedbackOverlay`、`VolumeFeedbackOverlay`、`SpeedBoostOverlay`、`LiveSkipIntroOutro`、`LiveSeekPreview` 整体放进：

```kotlin
if (!hidePage) {
```

现有参数、`fadeIn()`、`fadeOut()` 都保持原样。只多这一层条件。

`PlayerMetadataPanel` 换成：

```kotlin
if (hidePage) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .weight(0.62f),
    )
} else {
    PlayerMetadataPanel(
        seriesTitle = seriesTitle,
        currentIndex = currentIndex,
        totalEpisodes = episodes.size,
        currentEpisode = episodes[currentIndex],
        episodes = episodes,
        playbackSpeed = playbackSpeed,
        repeatOne = repeatOne,
        resolution = resolution,
        onEpisodeSelected = onEpisodeSelected,
        onSpeedClick = onSpeedClick,
        onLoopClick = onLoopClick,
        onMoreClick = onMoreClick,
        modifier = Modifier
            .fillMaxWidth()
            .weight(0.62f)
            .navigationBarsPadding(),
    )
}
```

- [ ] **Step 2: Hide the landscape page during the flight**

`LandscapePlayerContent.kt` 补上导入：

```kotlin
import com.pfpt.localvideoplayer.ui.hidePlayerPageForTransition
```

在函数体开头加上：

```kotlin
val hidePage = hidePlayerPageForTransition(playerTransitionActive())
```

外层 `Box` 的修饰符改成：

```kotlin
modifier = Modifier
    .fillMaxSize()
    .then(if (hidePage) Modifier else Modifier.background(Color.Black)),
```

画面 `Box` 的修饰符改成：

```kotlin
modifier = Modifier
    .fillMaxSize()
    .then(if (hidePage) Modifier else Modifier.background(Color.Black)),
```

圆角目标框和 `FittedPlayerSurface` 的现有代码不动。

亮度、音量、倍速这三个提示在带状态栏内边距的 `Box` 里面，紧跟在 `VideoPlayerGesture` 后面。横屏控制条、片头片尾和进度预览在那个 `Box` 外面。两处各写一个 `if (!hidePage)`，不要把外层 `Box` 的右括号包进条件。这些调用的参数保持原样。`LandscapePlayerLayout` 仍然包着全屏控制条，所以全屏控制条一并留在条件里。

- [ ] **Step 3: Hide the player root, sheets, and cast chrome**

`PlayerScreen.kt` 与这些函数同包，不需要新导入。在 `PlayerOverlaySheets(...)` 前面加上：

```kotlin
val hidePage = hidePlayerPageForTransition(playerTransitionActive())
```

把现有的 `PlayerOverlaySheets(...)` 整个调用放进 `if (!hidePage)`。参数保持原样。不要把弹层的打开状态写成 false。

根 `Box` 的修饰符改成：

```kotlin
modifier = modifier
    .fillMaxSize()
    .then(
        if (hidePage) {
            Modifier
        } else {
            Modifier.background(MaterialTheme.colorScheme.background)
        },
    ),
```

`forDoubleTap = true` 的那个 `LiveSeekPreview(...)` 整个调用放进 `if (!hidePage)`。参数保持原样。

投屏按钮的条件改成：

```kotlin
if (castManager != null && ui.controlsVisible && !ui.isInPipMode && !hidePage) {
```

投屏小条的条件改成：

```kotlin
if (connected != null && !ui.isInPipMode && !hidePage) {
```

画中画分支里的 `FittedPlayerSurface` 不包 `hidePage`。播放失败文字不包 `hidePage`。

- [ ] **Step 4: Verify**

Run: `./gradlew :app:testFullDebugUnitTest`

Expected: BUILD SUCCESSFUL。

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/pfpt/localvideoplayer/ui/components/PortraitPlayerLayout.kt app/src/main/java/com/pfpt/localvideoplayer/ui/components/LandscapePlayerContent.kt app/src/main/java/com/pfpt/localvideoplayer/ui/PlayerScreen.kt
git commit -m "$(cat <<'EOF'
Hide the player page while the thumbnail frame is moving.

EOF
)"
```

提交前用 `git status` 确认规格和本计划不在暂存区。
