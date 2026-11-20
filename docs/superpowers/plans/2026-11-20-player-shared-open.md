# Player Shared Open Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 从缩略图进入播放页时，这张图的框扩成播放画面的框；返回时缩回原来的缩略图。

**Architecture:** `NavHost` 外包 `SharedTransitionLayout`，不改现有淡入淡出。能进入播放页的缩略图和播放画面的目标框使用同一个 `uri.toString()`，通过 `Modifier.sharedBounds` 连接。视频表面是目标框上面的一层，不注册共享元素。画中画、投屏、剧集封面和文件夹封面不注册。

**Tech Stack:** Kotlin、Jetpack Compose、Navigation 2.8、JUnit 4、Android Gradle `./gradlew :app:testFullDebugUnitTest`。

## Global Constraints

- 从缩略图进入播放页时，这张图的框扩成播放画面的框。返回时，框缩回原来的缩略图。飞的是图的框，视频表面本身不跟着变形。
- 主列表的单集、剧集详情里的一集、最近播放那一排都这样。剧集行和文件夹行仍先进详情，不飞到播放页。
- `NavHost` 外包一层 `SharedTransitionLayout`。同一部视频的缩略图框和播放画面外框使用同一个 key，就是 `uri.toString()`。
- 只有会进入播放页的缩略图注册这个 key。剧集封面和文件夹封面不注册。缩略图还没解码时，飞的是现在的占位框。
- 播放页只给当前这一部、正在显示的本地画面外框注册同一个 key。视频表面不注册。画中画和投屏不注册。
- 导航现有的淡入淡出不改。共享边界跟着播放页现有的过渡走。
- 返回时，来源那一行还在组合里，框缩回那张缩略图。那一行不在组合里，共享元素不出现，播放页按现在的方式关掉。不把列表滚回去找出那一行。
- 播放页里切上一集或下一集只换画面，不飞。
- 不新增测试，也不使用 Compose 或 Robolectric。
- 实现代码不添加注释。
- 这一轮不动：剧集封面和文件夹封面、视频表面、画中画和投屏、导航的淡入淡出时长、缩略图的加载和占位规则、主列表 200 毫秒的行滑动、滚动位置恢复、播放决定和下载。
- 每个任务只提交该任务的代码。规格和本计划留在工作区，不放进这些提交。
- Gradle 命令不加 `--offline`。

## File Structure

- Create: `app/src/main/java/com/pfpt/localvideoplayer/ui/PlayerSharedBounds.kt` — 共享过渡的局部提供者和 `playerSharedBounds`。
- Modify: `app/src/main/java/com/pfpt/localvideoplayer/LocalVideoPlayerApp.kt` — `SharedTransitionLayout`，以及列表、剧集详情、播放页三处提供范围。
- Modify: `app/src/main/java/com/pfpt/localvideoplayer/ui/components/LumenMediaCard.kt` — 可选的共享 key，只传给默认缩略图。
- Modify: `app/src/main/java/com/pfpt/localvideoplayer/ui/components/VideoListItem.kt` — 单集和剧集详情的一集可以带上 key。
- Modify: `app/src/main/java/com/pfpt/localvideoplayer/ui/SeriesDetailScreen.kt` — 给每一集传 `episode.uri.toString()`。
- Modify: `app/src/main/java/com/pfpt/localvideoplayer/ui/components/RecentPlaybackSection.kt` — 最近播放的缩略图带上 `record.uri.toString()`。
- Modify: `app/src/main/java/com/pfpt/localvideoplayer/ui/components/PortraitPlayerLayout.kt` — 本地画面后面放目标框。
- Modify: `app/src/main/java/com/pfpt/localvideoplayer/ui/components/LandscapePlayerContent.kt` — 本地画面后面放目标框。

---

### Task 1: 缩略图框注册共享边界

**Files:**
- Create: `app/src/main/java/com/pfpt/localvideoplayer/ui/PlayerSharedBounds.kt`
- Modify: `app/src/main/java/com/pfpt/localvideoplayer/LocalVideoPlayerApp.kt`
- Modify: `app/src/main/java/com/pfpt/localvideoplayer/ui/components/LumenMediaCard.kt`
- Modify: `app/src/main/java/com/pfpt/localvideoplayer/ui/components/VideoListItem.kt`
- Modify: `app/src/main/java/com/pfpt/localvideoplayer/ui/SeriesDetailScreen.kt`
- Modify: `app/src/main/java/com/pfpt/localvideoplayer/ui/components/RecentPlaybackSection.kt`
- Test: `./gradlew :app:testFullDebugUnitTest`

**Interfaces:**
- Consumes: Navigation 2.8 的 `composable` 内容接收者是 `AnimatedContentScope`。`SharedTransitionLayout` 的接收者是 `SharedTransitionScope`。
- Produces: `@Composable fun Modifier.playerSharedBounds(contentKey: String?): Modifier`。`contentKey` 为 `null`，或两处局部范围还没提供时，修饰符原样返回。`@Composable fun ProvidePlayerSharedTransition(sharedTransitionScope: SharedTransitionScope, animatedVisibilityScope: AnimatedVisibilityScope, content: @Composable () -> Unit)`。

规格规定这一轮不新增测试。不要改 `NavHost` 的 `enterTransition`、`exitTransition`、`popEnterTransition`、`popExitTransition`。不要给剧集封面或文件夹封面传 key。

- [ ] **Step 1: Add the shared-bounds helper**

把下面的文件写成 `app/src/main/java/com/pfpt/localvideoplayer/ui/PlayerSharedBounds.kt`：

```kotlin
package com.pfpt.localvideoplayer.ui

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier

@OptIn(ExperimentalSharedTransitionApi::class)
private val LocalPlayerSharedTransition = staticCompositionLocalOf<SharedTransitionScope?> { null }

private val LocalPlayerAnimatedVisibility = staticCompositionLocalOf<AnimatedVisibilityScope?> { null }

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun ProvidePlayerSharedTransition(
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalPlayerSharedTransition provides sharedTransitionScope,
        LocalPlayerAnimatedVisibility provides animatedVisibilityScope,
        content = content,
    )
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.playerSharedBounds(contentKey: String?): Modifier {
    if (contentKey == null) return this
    val sharedTransitionScope = LocalPlayerSharedTransition.current ?: return this
    val animatedVisibilityScope = LocalPlayerAnimatedVisibility.current ?: return this
    return with(sharedTransitionScope) {
        sharedBounds(
            sharedContentState = rememberSharedContentState(key = contentKey),
            animatedVisibilityScope = animatedVisibilityScope,
        )
    }
}
```

- [ ] **Step 2: Provide the scopes on the three routes**

`LocalVideoPlayerApp` 里包住 `NavHost` 的那一层改成 `SharedTransitionLayout`。不要给 `NavHost` 增加过渡参数。

`ROUTE_VIDEO_LIST`、`ROUTE_SERIES`、`ROUTE_PLAYER` 三处的目的地内容用 `ProvidePlayerSharedTransition` 包起来：

```kotlin
ProvidePlayerSharedTransition(
    sharedTransitionScope = this@SharedTransitionLayout,
    animatedVisibilityScope = this@composable,
) {
    // 原来的目的地内容
}
```

包含这段调用的函数加上 `@OptIn(ExperimentalSharedTransitionApi::class)`。设置、下载和其他路由不包。

- [ ] **Step 3: Pass the key only from thumbnails that open the player**

`MediaThumbnail` 增加参数 `sharedContentKey: String? = null`。它最外层的 `Box` 使用 `modifier.playerSharedBounds(sharedContentKey)`。加载、占位和 `deferLoad` 不改。

`LumenMediaCard` 增加参数 `sharedContentKey: String? = null`，只传给默认的 `MediaThumbnail`。`thumbnailContent` 那条分支不传。

`VideoListItem` 给单集的 `LumenMediaCard` 传入 `sharedContentKey = video.uri.toString()`。`SeriesFolderItem` 不传。

`VideoListItem.kt` 里的 `EpisodeListItem` 增加参数 `sharedContentKey: String? = null`，并传给它的 `LumenMediaCard`。`SeriesDetailScreen` 的调用传入 `sharedContentKey = episode.uri.toString()`。播放页里的选集列表不传这个参数。

`RecentPlaybackSection` 的 `MediaThumbnail` 传入 `sharedContentKey = record.uri.toString()`。

- [ ] **Step 4: Verify**

Run: `./gradlew :app:testFullDebugUnitTest`

Expected: BUILD SUCCESSFUL。

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/pfpt/localvideoplayer/ui/PlayerSharedBounds.kt app/src/main/java/com/pfpt/localvideoplayer/LocalVideoPlayerApp.kt app/src/main/java/com/pfpt/localvideoplayer/ui/components/LumenMediaCard.kt app/src/main/java/com/pfpt/localvideoplayer/ui/components/VideoListItem.kt app/src/main/java/com/pfpt/localvideoplayer/ui/SeriesDetailScreen.kt app/src/main/java/com/pfpt/localvideoplayer/ui/components/RecentPlaybackSection.kt
git commit -m "$(cat <<'EOF'
Register thumbnail bounds for the player transition.

EOF
)"
```

提交前用 `git status` 确认规格和本计划不在暂存区。

---

### Task 2: 播放画面提供目标框

**Files:**
- Modify: `app/src/main/java/com/pfpt/localvideoplayer/ui/components/PortraitPlayerLayout.kt`
- Modify: `app/src/main/java/com/pfpt/localvideoplayer/ui/components/LandscapePlayerContent.kt`
- Test: `./gradlew :app:testFullDebugUnitTest`

**Interfaces:**
- Consumes: `Modifier.playerSharedBounds(contentKey: String?): Modifier`。`contentKey` 为 `null` 时修饰符原样返回。
- Produces: 竖屏和横屏本地画面后面各一个目标框。画中画分支不增加。

不要把 `playerSharedBounds` 加到 `FittedPlayerSurface` 或其直接父级上。不要为上一集、下一集添加过渡。不要改投屏时的画面。

- [ ] **Step 1: Add the portrait target behind the surface**

`PortraitPlayerLayout` 里，包住 `FittedPlayerSurface` 的 `Box` 改成先放目标框，再放表面：

```kotlin
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (!isCasting && videoUri != null) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .playerSharedBounds(videoUri.toString()),
                            )
                        }
                        FittedPlayerSurface(
                            player = exoPlayer,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = contentScale,
                            frameAspectRatio = frameAspectRatio,
                            useTextureView = useTextureView,
                        )
                    }
```

`FittedPlayerSurface` 的参数保持原样。

- [ ] **Step 2: Add the landscape target behind the surface**

`LandscapePlayerContent` 里，包住 `FittedPlayerSurface` 的 `Box` 同样先放目标框：

```kotlin
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            if (!isCasting && videoUri != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .playerSharedBounds(videoUri.toString()),
                )
            }
            FittedPlayerSurface(
                player = exoPlayer,
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale,
                frameAspectRatio = frameAspectRatio,
                useTextureView = useTextureView,
            )
        }
```

`PlayerScreen` 里 `ui.isInPipMode` 那个 `FittedPlayerSurface` 不包目标框。

- [ ] **Step 3: Verify**

Run: `./gradlew :app:testFullDebugUnitTest`

Expected: BUILD SUCCESSFUL。

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/pfpt/localvideoplayer/ui/components/PortraitPlayerLayout.kt app/src/main/java/com/pfpt/localvideoplayer/ui/components/LandscapePlayerContent.kt
git commit -m "$(cat <<'EOF'
Match the player frame to the opening thumbnail.

EOF
)"
```

提交前用 `git status` 确认规格和本计划不在暂存区。
