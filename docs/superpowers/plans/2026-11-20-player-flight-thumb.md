# Player Flight Thumbnail Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 飞入和返回时，圆角框里的缩略图不淡出，过渡结束后覆盖层拿掉，播放画面按现在的时机出现。

**Architecture:** `playerSharedBounds` 里的 `sharedBounds` 不再使用默认的内容淡入淡出。进入和退出都是 `None`。圆角裁剪、框弹簧、共享 key 和播放页的空目标框保持原样。

**Tech Stack:** Kotlin、Jetpack Compose、JUnit 4、Android Gradle `./gradlew :app:testFullDebugUnitTest`。

## Global Constraints

- 飞入播放页和返回时，圆角框还在动，框里一直是来源那张缩略图。不淡出，也不淡入。过渡结束后覆盖层拿掉，播放画面按现在的时机出现。
- `playerSharedBounds` 里的 `sharedBounds` 把进入和退出都设成 `EnterTransition.None` 和 `ExitTransition.None`。不再用默认的淡入淡出。
- 圆角裁剪留着，仍从缩略图的 12dp 收到播放画面的直角，返回再张开。框弹簧留着。共享 key 和哪一张缩略图注册留着。播放页目标框仍是空的，覆盖层里看见的是缩略图。
- 缩略图还没解码时，飞的仍是现在的占位。
- 过渡结束的那一帧覆盖层消失。视频表面仍只走 `hidePlayerSurfaceForTransition`。已经选用 TextureView 的，表面在飞行全程仍然显示。画中画不改。投屏的视频表面保持现在的样子。
- 换集不走导航过渡，不飞。没有匹配的缩略图时，共享过渡不进行。返回时来源那一行不在组合里，共享框不出现，播放页按现在的方式关掉。
- 不新增测试，也不使用 Compose 界面测试或 Robolectric。
- 实现代码不添加注释。
- 这一轮不动：表面隐藏和播放页隐藏、圆角的计算、共享 key 和哪一张缩略图注册、导航的淡入淡出时长、共享边界的框弹簧、缩略图的加载和占位规则、画中画和投屏的现有画面、播放决定和下载。
- 每个任务只提交该任务的代码。规格和本计划留在工作区，不放进这些提交。
- Gradle 命令不加 `--offline`。

## File Structure

- Modify: `app/src/main/java/com/pfpt/localvideoplayer/ui/PlayerSharedBounds.kt` — 只改 `playerSharedBounds` 里 `sharedBounds` 的进入和退出。
- 不改缩略图、播放页目标框、表面隐藏和页面隐藏的调用处。

---

### Task 1: 关掉共享框内容的淡入淡出

**Files:**
- Modify: `app/src/main/java/com/pfpt/localvideoplayer/ui/PlayerSharedBounds.kt`
- Test: `./gradlew :app:testFullDebugUnitTest`

**Interfaces:**
- Consumes: 现有的 `Modifier.playerSharedBounds(contentKey, restingCorner)`。
- Produces: 同一个函数。`sharedBounds` 的 `enter` 是 `EnterTransition.None`，`exit` 是 `ExitTransition.None`。

规格规定这一轮不新增测试。不要改 `sharedOpenCorner`、`hidePlayerSurfaceForTransition`、`hidePlayerPageForTransition`、`playerTransitionActive`。不要改 `clipInOverlayDuringTransition`。

- [ ] **Step 1: Turn off the shared-content fade**

在 `PlayerSharedBounds.kt` 补上：

```kotlin
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
```

把 `playerSharedBounds` 里的 `sharedBounds` 调用改成：

```kotlin
        sharedBounds(
            sharedContentState = rememberSharedContentState(key = contentKey),
            animatedVisibilityScope = animatedVisibilityScope,
            enter = EnterTransition.None,
            exit = ExitTransition.None,
            clipInOverlayDuringTransition = OverlayClip(RoundedCornerShape(corner)),
        )
```

其余参数和函数体不改。

- [ ] **Step 2: Verify**

Run: `./gradlew :app:testFullDebugUnitTest`

Expected: BUILD SUCCESSFUL。

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/pfpt/localvideoplayer/ui/PlayerSharedBounds.kt
git commit -m "$(cat <<'EOF'
Keep the thumbnail opaque while the shared frame is moving.

EOF
)"
```

提交前用 `git status` 确认规格和本计划不在暂存区。
