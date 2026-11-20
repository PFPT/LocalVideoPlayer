# Player Shared Corner Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 飞进播放页的缩略图框从 12dp 圆角收到直角，返回时再张开到 12dp。

**Architecture:** 纯函数 `sharedOpenCorner` 决定静止和飞行中的圆角。`playerSharedBounds` 用当前页面进入/离开状态调用它，再把结果交给 `clipInOverlayDuringTransition`。缩略图传入 `LumenThumbCorner`，播放画面继续用默认的 `0.dp`。

**Tech Stack:** Kotlin、Jetpack Compose、Navigation 2.8、JUnit 4、Android Gradle `./gradlew :app:testFullDebugUnitTest`。

## Global Constraints

- 从缩略图进入播放页时，飞着的框从 12dp 圆角收到直角。返回时，框从直角张开到 12dp。铺满之后播放画面仍是直角。
- 圆角目标跟着当前页面的进入和离开状态变，不另开一段点击计时。框仍走共享边界现有的弹簧，导航的淡入淡出时长不改。
- `playerSharedBounds` 增加静止圆角，默认 `0.dp`。缩略图传入 `LumenThumbCorner`。播放画面不传，用默认的 `0.dp`。
- 页面可见时用静止圆角。进入或离开时：静止值是 `0.dp` 就用 `LumenThumbCorner`，否则用 `0.dp`。可见、进入、离开来自 `AnimatedVisibilityScope.transition`，圆角用 `animateDp` 的默认规格。这个值交给 `clipInOverlayDuringTransition` 的 `OverlayClip(RoundedCornerShape(...))`。
- `sharedContentKey` 为空，或共享范围还没提供时，修饰符原样返回，不播圆角。
- 列表上静止时的 12dp 裁剪留在原来的父级，不挪进共享边界。缩略图还没解码时，飞的仍是现在的占位框，圆角规则相同。
- 主列表的单集、剧集详情里的一集、最近播放那一排都带上缩略图的静止圆角。剧集封面和文件夹封面不注册共享边界，圆角不变。
- 播放页的竖屏和横屏本地画面外框使用默认静止圆角 `0.dp`。视频表面不注册。画中画和投屏不注册。
- 返回时，来源那一行还在组合里，圆角从 0 张开到 12dp。那一行不在组合里，共享元素不出现，播放页按现在的方式关掉。不把列表滚回去找出那一行。
- 播放页里切上一集或下一集只换画面，不飞。播放页这时仍是可见状态，圆角停在 0。
- 圆角对照收成纯函数 `sharedOpenCorner(restingCorner, atRest)`。可见对应 `atRest = true`，进入和离开对应 `atRest = false`。
- 单元测试覆盖四种：静止圆角是 `LumenThumbCorner` 且静止时结果是 `LumenThumbCorner`；静止圆角是 `LumenThumbCorner` 且在飞时结果是 `0.dp`；静止圆角是 `0.dp` 且静止时结果是 `0.dp`；静止圆角是 `0.dp` 且在飞时结果是 `LumenThumbCorner`。
- 不使用 Compose 界面测试或 Robolectric。
- 实现代码不添加注释。
- 这一轮不动：剧集封面和文件夹封面、视频表面、画中画和投屏、导航的淡入淡出时长、共享边界的框弹簧、缩略图的加载和占位规则、列表上原来的父级圆角裁剪、主列表 200 毫秒的行滑动、滚动位置恢复、哪一张缩略图注册共享边界、播放决定和下载。
- 每个任务只提交该任务的代码。规格和本计划留在工作区，不放进这些提交。
- Gradle 命令不加 `--offline`。

## File Structure

- Modify: `app/src/main/java/com/pfpt/localvideoplayer/ui/PlayerSharedBounds.kt` — `sharedOpenCorner`，以及 `playerSharedBounds` 的遮罩圆角。
- Modify: `app/src/main/java/com/pfpt/localvideoplayer/ui/components/LumenMediaCard.kt` — `MediaThumbnail` 传入 `LumenThumbCorner`。
- Create: `app/src/test/java/com/pfpt/localvideoplayer/ui/SharedOpenCornerTest.kt` — 四种圆角对照。
- 不改 `PortraitPlayerLayout.kt` 和 `LandscapePlayerContent.kt`。它们已经调用 `playerSharedBounds(videoUri.toString())`，新参数的默认值就是播放画面要的 `0.dp`。

---

### Task 1: 圆角对照

**Files:**
- Create: `app/src/test/java/com/pfpt/localvideoplayer/ui/SharedOpenCornerTest.kt`
- Modify: `app/src/main/java/com/pfpt/localvideoplayer/ui/PlayerSharedBounds.kt`
- Test: `./gradlew :app:testFullDebugUnitTest --tests com.pfpt.localvideoplayer.ui.SharedOpenCornerTest`

**Interfaces:**
- Consumes: `com.pfpt.localvideoplayer.ui.theme.LumenThumbCorner`，值是 `12.dp`。
- Produces: `fun sharedOpenCorner(restingCorner: Dp, atRest: Boolean): Dp`。静止时返回 `restingCorner`。飞行中，`restingCorner` 等于 `0.dp` 时返回 `LumenThumbCorner`，否则返回 `0.dp`。

- [ ] **Step 1: Write the failing test**

把下面的文件写成 `app/src/test/java/com/pfpt/localvideoplayer/ui/SharedOpenCornerTest.kt`：

```kotlin
package com.pfpt.localvideoplayer.ui

import androidx.compose.ui.unit.dp
import com.pfpt.localvideoplayer.ui.theme.LumenThumbCorner
import org.junit.Assert.assertEquals
import org.junit.Test

class SharedOpenCornerTest {
    @Test
    fun thumbnailAtRestKeepsThumbCorner() {
        assertEquals(
            LumenThumbCorner,
            sharedOpenCorner(restingCorner = LumenThumbCorner, atRest = true),
        )
    }

    @Test
    fun thumbnailInFlightBecomesSquare() {
        assertEquals(
            0.dp,
            sharedOpenCorner(restingCorner = LumenThumbCorner, atRest = false),
        )
    }

    @Test
    fun playerAtRestStaysSquare() {
        assertEquals(
            0.dp,
            sharedOpenCorner(restingCorner = 0.dp, atRest = true),
        )
    }

    @Test
    fun playerInFlightUsesThumbCorner() {
        assertEquals(
            LumenThumbCorner,
            sharedOpenCorner(restingCorner = 0.dp, atRest = false),
        )
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :app:testFullDebugUnitTest --tests com.pfpt.localvideoplayer.ui.SharedOpenCornerTest`

Expected: FAIL。编译错误是 `sharedOpenCorner` 未定义。

- [ ] **Step 3: Add the corner function**

在 `PlayerSharedBounds.kt` 里、`playerSharedBounds` 之前加上这个函数。保留文件里已有的共享范围和点击登记。

```kotlin
fun sharedOpenCorner(
    restingCorner: Dp,
    atRest: Boolean,
): Dp {
    if (atRest) return restingCorner
    return if (restingCorner == 0.dp) LumenThumbCorner else 0.dp
}
```

补上 `androidx.compose.ui.unit.Dp`、`androidx.compose.ui.unit.dp` 和 `com.pfpt.localvideoplayer.ui.theme.LumenThumbCorner` 的导入。

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :app:testFullDebugUnitTest --tests com.pfpt.localvideoplayer.ui.SharedOpenCornerTest`

Expected: BUILD SUCCESSFUL。

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/pfpt/localvideoplayer/ui/PlayerSharedBounds.kt app/src/test/java/com/pfpt/localvideoplayer/ui/SharedOpenCornerTest.kt
git commit -m "$(cat <<'EOF'
Map the shared-open corner from the resting shape.

EOF
)"
```

提交前用 `git status` 确认规格和本计划不在暂存区。

---

### Task 2: 飞入时使用这段圆角

**Files:**
- Modify: `app/src/main/java/com/pfpt/localvideoplayer/ui/PlayerSharedBounds.kt`
- Modify: `app/src/main/java/com/pfpt/localvideoplayer/ui/components/LumenMediaCard.kt`
- Test: `./gradlew :app:testFullDebugUnitTest`

**Interfaces:**
- Consumes: `fun sharedOpenCorner(restingCorner: Dp, atRest: Boolean): Dp`。可见对应 `atRest = true`，进入和离开对应 `atRest = false`。
- Produces: `@Composable fun Modifier.playerSharedBounds(contentKey: String?, restingCorner: Dp = 0.dp): Modifier`。`contentKey` 为 `null`，或两处局部范围还没提供时，修饰符原样返回。

不改 `PortraitPlayerLayout.kt` 和 `LandscapePlayerContent.kt`。不改 `sharedBounds` 的 `boundsTransform`、`enter`、`exit`。不把父级的 `clip` 挪进共享边界。不新增界面测试。

- [ ] **Step 1: Clip the shared bounds with the corner**

把 `PlayerSharedBounds.kt` 里的 `playerSharedBounds` 换成：

```kotlin
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.playerSharedBounds(
    contentKey: String?,
    restingCorner: Dp = 0.dp,
): Modifier {
    if (contentKey == null) return this
    val sharedTransitionScope = LocalPlayerSharedTransition.current ?: return this
    val animatedVisibilityScope = LocalPlayerAnimatedVisibility.current ?: return this
    val corner by animatedVisibilityScope.transition.animateDp { state ->
        sharedOpenCorner(
            restingCorner = restingCorner,
            atRest = state == EnterExitState.Visible,
        )
    }
    return with(sharedTransitionScope) {
        sharedBounds(
            sharedContentState = rememberSharedContentState(key = contentKey),
            animatedVisibilityScope = animatedVisibilityScope,
            clipInOverlayDuringTransition = OverlayClip(RoundedCornerShape(corner)),
        )
    }
}
```

补上这些导入，已有的不要重复：`androidx.compose.animation.EnterExitState`、`androidx.compose.animation.core.animateDp`、`androidx.compose.foundation.shape.RoundedCornerShape`。`OverlayClip` 在 `SharedTransitionScope` 的接收者上调用，所以上面的 `with(sharedTransitionScope)` 必须留着。

- [ ] **Step 2: Pass the thumbnail corner**

`LumenMediaCard.kt` 的 `MediaThumbnail` 里，把：

```kotlin
.playerSharedBounds(sharedContentKey)
```

换成：

```kotlin
.playerSharedBounds(
    contentKey = sharedContentKey,
    restingCorner = LumenThumbCorner,
)
```

`LumenThumbCorner` 在这个文件里已经导入。剧集封面那次 `MediaThumbnail` 不传 `sharedContentKey`，空 key 会在圆角动画之前返回。

- [ ] **Step 3: Verify**

Run: `./gradlew :app:testFullDebugUnitTest`

Expected: BUILD SUCCESSFUL。

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/pfpt/localvideoplayer/ui/PlayerSharedBounds.kt app/src/main/java/com/pfpt/localvideoplayer/ui/components/LumenMediaCard.kt
git commit -m "$(cat <<'EOF'
Clip the flying thumbnail from a rounded corner to square.

EOF
)"
```

提交前用 `git status` 确认规格和本计划不在暂存区。
