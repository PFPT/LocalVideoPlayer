# Player Flight Surface Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 飞入和返回时，默认的 SurfaceView 先不显示，共享过渡结束后再显示。

**Architecture:** 纯函数 `hidePlayerSurfaceForTransition` 决定要不要藏。竖屏和横屏在返回 `true` 时不组合 `FittedPlayerSurface`，圆角目标框留着。TextureView、画中画和投屏不走这条隐藏。

**Tech Stack:** Kotlin、Jetpack Compose、JUnit 4、Android Gradle `./gradlew :app:testFullDebugUnitTest`。

## Global Constraints

- 飞入播放页和返回时，圆角框还在动，默认的 SurfaceView 先不显示。过渡结束后再显示视频表面。设置里的表面类型不变。
- 竖屏和横屏的本地画面里，同时满足这三条就不组合 `FittedPlayerSurface`：当前不是 TextureView、当前没有投屏、共享过渡还在进行。
- 圆角目标框留着。过渡结束后立刻组合表面，不为表面另加淡入。
- 已经选用 TextureView 的，表面一直显示。画中画那个分支不改。投屏时表面保持现在的样子。
- 要不要藏，收成 `hidePlayerSurfaceForTransition(useTextureView, isCasting, transitionActive)`。返回 `true` 时不组合表面。`transitionActive` 来自 `SharedTransitionScope.isTransitionActive`。没有共享范围时当作没在过渡。
- 换集不走导航过渡，表面不藏。没有匹配的缩略图时，共享过渡不进行，表面按现在的方式出现。
- 单元测试覆盖四种：SurfaceView、未投屏、过渡中为藏；SurfaceView、未投屏、未过渡为显示；TextureView、过渡中为显示；投屏、SurfaceView、过渡中为显示。
- 不使用 Compose 界面测试或 Robolectric。
- 实现代码不添加注释。
- 这一轮不动：设置里的 SurfaceView 和 TextureView 选项、圆角、共享 key 和哪一张缩略图注册、导航的淡入淡出时长、共享边界的框弹簧、缩略图的加载和占位规则、画中画和投屏的现有画面、播放决定和下载。
- 每个任务只提交该任务的代码。规格和本计划留在工作区，不放进这些提交。
- Gradle 命令不加 `--offline`。

## File Structure

- Modify: `app/src/main/java/com/pfpt/localvideoplayer/ui/PlayerSharedBounds.kt` — 隐藏判断，以及读取共享过渡是否在进行。
- Create: `app/src/test/java/com/pfpt/localvideoplayer/ui/PlayerFlightSurfaceTest.kt` — 四种对照。
- Modify: `app/src/main/java/com/pfpt/localvideoplayer/ui/components/PortraitPlayerLayout.kt` — 过渡中不组合 SurfaceView。
- Modify: `app/src/main/java/com/pfpt/localvideoplayer/ui/components/LandscapePlayerContent.kt` — 过渡中不组合 SurfaceView。
- 不改 `PlayerScreen.kt` 里画中画那个 `FittedPlayerSurface`。

---

### Task 1: 隐藏判断

**Files:**
- Create: `app/src/test/java/com/pfpt/localvideoplayer/ui/PlayerFlightSurfaceTest.kt`
- Modify: `app/src/main/java/com/pfpt/localvideoplayer/ui/PlayerSharedBounds.kt`
- Test: `./gradlew :app:testFullDebugUnitTest --tests com.pfpt.localvideoplayer.ui.PlayerFlightSurfaceTest`

**Interfaces:**
- Consumes: 无。
- Produces: `fun hidePlayerSurfaceForTransition(useTextureView: Boolean, isCasting: Boolean, transitionActive: Boolean): Boolean`。TextureView 或投屏返回 `false`。其余返回 `transitionActive`。

- [ ] **Step 1: Write the failing test**

把下面的文件写成 `app/src/test/java/com/pfpt/localvideoplayer/ui/PlayerFlightSurfaceTest.kt`：

```kotlin
package com.pfpt.localvideoplayer.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerFlightSurfaceTest {
    @Test
    fun surfaceViewHidesWhileTransitionRuns() {
        assertTrue(
            hidePlayerSurfaceForTransition(
                useTextureView = false,
                isCasting = false,
                transitionActive = true,
            ),
        )
    }

    @Test
    fun surfaceViewShowsWhenTransitionIsIdle() {
        assertFalse(
            hidePlayerSurfaceForTransition(
                useTextureView = false,
                isCasting = false,
                transitionActive = false,
            ),
        )
    }

    @Test
    fun textureViewStaysVisibleDuringTransition() {
        assertFalse(
            hidePlayerSurfaceForTransition(
                useTextureView = true,
                isCasting = false,
                transitionActive = true,
            ),
        )
    }

    @Test
    fun castSurfaceStaysVisibleDuringTransition() {
        assertFalse(
            hidePlayerSurfaceForTransition(
                useTextureView = false,
                isCasting = true,
                transitionActive = true,
            ),
        )
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :app:testFullDebugUnitTest --tests com.pfpt.localvideoplayer.ui.PlayerFlightSurfaceTest`

Expected: FAIL。编译错误是 `hidePlayerSurfaceForTransition` 未定义。

- [ ] **Step 3: Add the function**

在 `PlayerSharedBounds.kt` 末尾加上：

```kotlin
fun hidePlayerSurfaceForTransition(
    useTextureView: Boolean,
    isCasting: Boolean,
    transitionActive: Boolean,
): Boolean {
    if (useTextureView) return false
    if (isCasting) return false
    return transitionActive
}
```

不要改 `playerSharedBounds` 和 `sharedOpenCorner`。

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :app:testFullDebugUnitTest --tests com.pfpt.localvideoplayer.ui.PlayerFlightSurfaceTest`

Expected: BUILD SUCCESSFUL。

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/pfpt/localvideoplayer/ui/PlayerSharedBounds.kt app/src/test/java/com/pfpt/localvideoplayer/ui/PlayerFlightSurfaceTest.kt
git commit -m "$(cat <<'EOF'
Decide when the player surface hides during the flight.

EOF
)"
```

提交前用 `git status` 确认规格和本计划不在暂存区。

---

### Task 2: 过渡中不放 SurfaceView

**Files:**
- Modify: `app/src/main/java/com/pfpt/localvideoplayer/ui/PlayerSharedBounds.kt`
- Modify: `app/src/main/java/com/pfpt/localvideoplayer/ui/components/PortraitPlayerLayout.kt`
- Modify: `app/src/main/java/com/pfpt/localvideoplayer/ui/components/LandscapePlayerContent.kt`
- Test: `./gradlew :app:testFullDebugUnitTest`

**Interfaces:**
- Consumes: `fun hidePlayerSurfaceForTransition(useTextureView: Boolean, isCasting: Boolean, transitionActive: Boolean): Boolean`。返回 `true` 时不组合表面。
- Produces: `@Composable fun playerTransitionActive(): Boolean`。没有共享范围时返回 `false`，否则返回 `SharedTransitionScope.isTransitionActive`。

不改画中画分支。不给表面加淡入。不改圆角目标框。

- [ ] **Step 1: Read whether the shared transition is active**

在 `PlayerSharedBounds.kt` 末尾加上：

```kotlin
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun playerTransitionActive(): Boolean {
    val sharedTransitionScope = LocalPlayerSharedTransition.current ?: return false
    return sharedTransitionScope.isTransitionActive
}
```

- [ ] **Step 2: Skip the portrait surface during the flight**

`PortraitPlayerLayout.kt` 里，圆角目标框后面的 `FittedPlayerSurface` 改成只在不需要隐藏时组合：

```kotlin
                        if (!isCasting && videoUri != null) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .playerSharedBounds(videoUri.toString()),
                            )
                        }
                        if (!hidePlayerSurfaceForTransition(
                                useTextureView = useTextureView,
                                isCasting = isCasting,
                                transitionActive = playerTransitionActive(),
                            )
                        ) {
                            FittedPlayerSurface(
                                player = exoPlayer,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = contentScale,
                                frameAspectRatio = frameAspectRatio,
                                useTextureView = useTextureView,
                            )
                        }
```

补上 `hidePlayerSurfaceForTransition` 和 `playerTransitionActive` 的导入。`FittedPlayerSurface` 的参数保持原样。

- [ ] **Step 3: Skip the landscape surface during the flight**

`LandscapePlayerContent.kt` 里，圆角目标框后面的 `FittedPlayerSurface` 换成：

```kotlin
            if (!isCasting && videoUri != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .playerSharedBounds(videoUri.toString()),
                )
            }
            if (!hidePlayerSurfaceForTransition(
                    useTextureView = useTextureView,
                    isCasting = isCasting,
                    transitionActive = playerTransitionActive(),
                )
            ) {
                FittedPlayerSurface(
                    player = exoPlayer,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = contentScale,
                    frameAspectRatio = frameAspectRatio,
                    useTextureView = useTextureView,
                )
            }
```

补上 `hidePlayerSurfaceForTransition` 和 `playerTransitionActive` 的导入。圆角目标框的条件不改。

- [ ] **Step 4: Verify**

Run: `./gradlew :app:testFullDebugUnitTest`

Expected: BUILD SUCCESSFUL。

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/pfpt/localvideoplayer/ui/PlayerSharedBounds.kt app/src/main/java/com/pfpt/localvideoplayer/ui/components/PortraitPlayerLayout.kt app/src/main/java/com/pfpt/localvideoplayer/ui/components/LandscapePlayerContent.kt
git commit -m "$(cat <<'EOF'
Hide the SurfaceView while the thumbnail frame is moving.

EOF
)"
```

提交前用 `git status` 确认规格和本计划不在暂存区。
