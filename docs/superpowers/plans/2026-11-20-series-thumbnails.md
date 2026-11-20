# Series Thumbnails Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 剧集详情停稳后，只给当时真正出现在屏幕里的集加载缩略图。

**Architecture:** 剧集详情复用已有的 `thumbnailShouldLoad`。`SeriesDetailScreen` 从自己的 `LazyListState.layoutInfo.visibleItemsInfo` 收集可见 key，把函数的否定结果传给现有的 `deferThumbnailLoad`。`MediaThumbnail` 现有的 `LaunchedEffect` 在 `deferLoad` 变真时取消进行中的加载。主列表和最近播放不改。

**Tech Stack:** Kotlin、Jetpack Compose、JUnit 4、Android Gradle `./gradlew :app:testFullDebugUnitTest`。

## Global Constraints

- 剧集详情停稳后，只给当时真正出现在屏幕里的集加载缩略图。屏幕外即使已经被列表排上，也保持占位，滑进来并停稳后再加载。
- 滚动过程中不出新图。滑出屏幕、还没完成的加载取消。
- 缓存里已有的图马上显示。这一集已经显示的图，滑出屏幕后不清掉。
- 剧集详情复用 `thumbnailShouldLoad(isScrolling, itemKey, visibleKeys)`，不新写决定函数。顺序仍是：正在滚动不加载；没在滚动且 `itemKey` 在 `visibleKeys` 里才加载；其余不加载。可见行为空时也不加载。
- `visibleKeys` 来自剧集详情列表当前已经排出来的可见项 key。露出一点的集也在这个集合里。函数本身不测量像素。列表还没完成第一次布局时，可见行是空的，先保持占位。
- 传给函数的 `itemKey` 必须是这一集在 `LazyColumn` 里使用的同一个 key，也就是 `episode.uri.toString()`。
- `SeriesDetailScreen` 把 `deferThumbnailLoad` 设为这个函数的否定。主列表和最近播放不改。
- 这一集不再该加载时，`deferThumbnailLoad` 变为真，正在进行的加载协程取消，结果不写进这一集。现有的 `MediaThumbnail` 和 `VideoThumbnailCache` 不改。
- 阻塞解码如果在取消被观察到之前已经写进缓存，缓存留着。下次这集又该显示时，已有的图马上出来。
- 平台缩略图和帧回退都拿不到图时，这一集保持现在的占位。不增加失败文案。这集一直可见时，不反复重试。滚出屏幕再滚回来之后，按新的一次加载再试。
- 帧回退仍是 400 毫秒后再试一次，而且只在这集仍然该加载时进行。平台图并发仍是最多 3 个，帧回退仍是 1 个。
- 不新增测试，也不使用 Compose 或 Robolectric。`ThumbnailLoadTest` 继续锁住 `thumbnailShouldLoad`。
- 实现代码不添加注释。
- 这一轮不动：主列表的缩略图和 200 毫秒行滑动、最近播放的缩略图、剧集详情行现有的位置动画、占位的样子、缩略图尺寸、缓存容量、分组、排序、筛选、打开库、滚动位置恢复、播放、下载。
- 每个任务只提交该任务的代码。规格和本计划留在工作区，不放进这些提交。
- Gradle 命令不加 `--offline`。

## File Structure

- Modify: `app/src/main/java/com/pfpt/localvideoplayer/ui/SeriesDetailScreen.kt` — 收集可见 key，并用已有决定函数设置每一集的推迟加载。

---

### Task 1: 把决定接到剧集详情

**Files:**
- Modify: `app/src/main/java/com/pfpt/localvideoplayer/ui/SeriesDetailScreen.kt`
- Test: `./gradlew :app:testFullDebugUnitTest --tests com.pfpt.localvideoplayer.ui.ThumbnailLoadTest`

**Interfaces:**
- Consumes: `thumbnailShouldLoad(isScrolling: Boolean, itemKey: Any, visibleKeys: Set<Any>): Boolean`，已在 `com.pfpt.localvideoplayer.ui`。`SeriesDetailScreen` 也在这个包，不需要新的 import。
- Produces: 剧集详情每一集的 `deferThumbnailLoad`。

规格规定这一轮不新增测试。不要改 `ThumbnailLoad.kt`、`ThumbnailLoadTest.kt`、`VideoListScreen.kt`、`MediaThumbnail`、`VideoThumbnailCache`、`RecentPlaybackSection`、`EpisodeListItem` 的参数表。`Box(modifier = Modifier.animateItem())` 保持原样。

- [ ] **Step 1: Collect visible keys**

在 `isListScrolling` 的 `derivedStateOf` 旁边增加：

```kotlin
        val visibleItemKeys by remember {
            derivedStateOf {
                listState.layoutInfo.visibleItemsInfo.map { it.key }.toSet()
            }
        }
```

不要按偏移或高度再过滤。`visibleItemsInfo` 里露出一点的项保留。

- [ ] **Step 2: Decide per episode**

`itemsIndexed` 的 `key = { _, episode -> episode.uri.toString() }` 保持不动。循环里已有 `val uriKey = episode.uri.toString()`。把：

```kotlin
                            deferThumbnailLoad = isListScrolling,
```

换成：

```kotlin
                            deferThumbnailLoad = !thumbnailShouldLoad(
                                isScrolling = isListScrolling,
                                itemKey = uriKey,
                                visibleKeys = visibleItemKeys,
                            ),
```

`EpisodeListItem` 的其余参数、点击、长按和侧滑删除保持不动。

- [ ] **Step 3: Verify**

Run: `./gradlew :app:testFullDebugUnitTest --tests com.pfpt.localvideoplayer.ui.ThumbnailLoadTest`

Expected: PASS。

Run: `./gradlew :app:testFullDebugUnitTest`

Expected: BUILD SUCCESSFUL。

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/pfpt/localvideoplayer/ui/SeriesDetailScreen.kt
git commit -m "$(cat <<'EOF'
Load series thumbnails only for episodes on screen.

EOF
)"
```

提交前用 `git status` 确认规格和本计划不在暂存区。
