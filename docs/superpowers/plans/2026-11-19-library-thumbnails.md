# Library Thumbnails Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 视频库主列表停稳后，只给当时真正出现在屏幕里的行加载缩略图。

**Architecture:** `thumbnailShouldLoad` 是纯函数。正在滚动，或者这一行的列表 key 不在当前可见项里，就不加载。`VideoListScreen` 用 `LazyListState.layoutInfo.visibleItemsInfo` 的 key 调用它，并把否定结果传给现有的 `deferThumbnailLoad`。`MediaThumbnail` 现有的 `LaunchedEffect` 在 `deferLoad` 变真时取消进行中的加载。剧集详情和最近播放不走这个函数。

**Tech Stack:** Kotlin、JUnit 4、Android Gradle `./gradlew :app:testFullDebugUnitTest`。

## Global Constraints

- 视频库主列表停稳后，只给当时真正出现在屏幕里的行加载缩略图。屏幕外即使已经被列表排上，也保持占位，滑进来并停稳后再加载。
- 滚动过程中不出新图。滑出屏幕、还没完成的加载取消。
- 缓存里已有的图马上显示。这一行已经显示的图，滑出屏幕后不清掉。
- `thumbnailShouldLoad(isScrolling, itemKey, visibleKeys)` 的顺序是：`isScrolling` 为真则不加载，并且优先于可见行；`itemKey` 在 `visibleKeys` 里则加载；其余不加载。可见行为空时也不加载。
- `visibleKeys` 是主列表当前已经排出来的可见项 key。露出一点的行也在这个集合里。函数本身不测量像素。
- 列表还没完成第一次布局时，可见行是空的，先保持占位。布局出来之后，没在滚动就给当时可见的行加载。
- 传给函数的 `itemKey` 必须是这一行在 `LazyColumn` 里使用的同一个 key。最近播放、分类栏、空状态和分组标题不走这个函数。
- `VideoListScreen` 把 `deferThumbnailLoad` 设为这个函数的否定。剧集详情仍只按是否正在滚动推迟加载。最近播放仍马上加载。
- 这一行不再该加载时，`deferThumbnailLoad` 变为真，正在进行的加载协程取消，结果不写进这一行。
- 阻塞解码如果在取消被观察到之前已经写进缓存，缓存留着。下次这行又该显示时，已有的图马上出来。
- 平台缩略图和帧回退都拿不到图时，这一行保持现在的占位。不增加失败文案。这行一直可见时，不反复重试。滚出屏幕再滚回来之后，按新的一次加载再试。
- 帧回退仍是 400 毫秒后再试一次，而且只在这行仍然该加载时进行。平台图并发仍是最多 3 个，帧回退仍是 1 个。
- 纯函数测试不使用 Compose，也不使用 Robolectric。取消和界面组合不进这个测试。
- 实现代码不添加注释。
- 这一轮不动：剧集详情和最近播放的缩略图、占位的样子、缩略图尺寸、缓存容量、分组算法、排序、筛选、增量扫描、打开库时的留白、列表、转圈和空库、下拉刷新的手势和指示、滚动位置恢复、播放、下载。
- 每个任务只提交该任务的代码。规格和本计划留在工作区，不放进这些提交。
- Gradle 命令不加 `--offline`。

## File Structure

- Create: `app/src/main/java/com/pfpt/localvideoplayer/ui/ThumbnailLoad.kt` — 主列表一行是否加载缩略图。
- Create: `app/src/test/java/com/pfpt/localvideoplayer/ui/ThumbnailLoadTest.kt` — 滚动优先、可见 key、空可见行。
- Modify: `app/src/main/java/com/pfpt/localvideoplayer/ui/library/LibraryRows.kt` — 三行函数接收已经算好的 `deferThumbnailLoad`。
- Modify: `app/src/main/java/com/pfpt/localvideoplayer/ui/VideoListScreen.kt` — 收集可见 key，并用决定函数设置主列表每一行的推迟加载。

---

### Task 1: 缩略图是否加载的决定

**Files:**
- Create: `app/src/test/java/com/pfpt/localvideoplayer/ui/ThumbnailLoadTest.kt`
- Create: `app/src/main/java/com/pfpt/localvideoplayer/ui/ThumbnailLoad.kt`
- Test: `app/src/test/java/com/pfpt/localvideoplayer/ui/ThumbnailLoadTest.kt`

**Interfaces:**
- Consumes: 无。
- Produces: `fun thumbnailShouldLoad(isScrolling: Boolean, itemKey: Any, visibleKeys: Set<Any>): Boolean`。

- [ ] **Step 1: Write the failing test**

把下面的文件写成 `app/src/test/java/com/pfpt/localvideoplayer/ui/ThumbnailLoadTest.kt`：

```kotlin
package com.pfpt.localvideoplayer.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThumbnailLoadTest {
    @Test
    fun scrollingSkipsVisibleKey() {
        assertFalse(
            thumbnailShouldLoad(
                isScrolling = true,
                itemKey = "row-a",
                visibleKeys = setOf("row-a"),
            ),
        )
    }

    @Test
    fun restingVisibleKeyLoads() {
        assertTrue(
            thumbnailShouldLoad(
                isScrolling = false,
                itemKey = "row-a",
                visibleKeys = setOf("row-a", "row-b"),
            ),
        )
    }

    @Test
    fun restingHiddenKeyStaysDeferred() {
        assertFalse(
            thumbnailShouldLoad(
                isScrolling = false,
                itemKey = "row-a",
                visibleKeys = setOf("row-b"),
            ),
        )
    }

    @Test
    fun scrollingHiddenKeyStaysDeferred() {
        assertFalse(
            thumbnailShouldLoad(
                isScrolling = true,
                itemKey = "row-a",
                visibleKeys = setOf("row-b"),
            ),
        )
    }

    @Test
    fun emptyVisibleKeysStayDeferred() {
        assertFalse(
            thumbnailShouldLoad(
                isScrolling = false,
                itemKey = "row-a",
                visibleKeys = emptySet(),
            ),
        )
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testFullDebugUnitTest --tests com.pfpt.localvideoplayer.ui.ThumbnailLoadTest`

Expected: FAIL。编译错误是 `thumbnailShouldLoad` 未解析。

- [ ] **Step 3: Write minimal implementation**

把下面的文件写成 `app/src/main/java/com/pfpt/localvideoplayer/ui/ThumbnailLoad.kt`：

```kotlin
package com.pfpt.localvideoplayer.ui

fun thumbnailShouldLoad(
    isScrolling: Boolean,
    itemKey: Any,
    visibleKeys: Set<Any>,
): Boolean {
    if (isScrolling) return false
    return itemKey in visibleKeys
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testFullDebugUnitTest --tests com.pfpt.localvideoplayer.ui.ThumbnailLoadTest`

Expected: PASS。

Run: `./gradlew :app:testFullDebugUnitTest`

Expected: BUILD SUCCESSFUL。

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/pfpt/localvideoplayer/ui/ThumbnailLoad.kt app/src/test/java/com/pfpt/localvideoplayer/ui/ThumbnailLoadTest.kt
git commit -m "$(cat <<'EOF'
Decide which library rows may load a thumbnail.

EOF
)"
```

提交前用 `git status` 确认规格和本计划不在暂存区。

---

### Task 2: 把决定接到主列表

**Files:**
- Modify: `app/src/main/java/com/pfpt/localvideoplayer/ui/library/LibraryRows.kt`
- Modify: `app/src/main/java/com/pfpt/localvideoplayer/ui/VideoListScreen.kt`
- Test: `./gradlew :app:testFullDebugUnitTest`

**Interfaces:**
- Consumes: `thumbnailShouldLoad(isScrolling: Boolean, itemKey: Any, visibleKeys: Set<Any>): Boolean`。
- Produces: 主列表每一行的 `deferThumbnailLoad`。剧集详情和最近播放的调用保持原样。

不要改 `MediaThumbnail`、`VideoThumbnailCache`、`SeriesDetailScreen`、`RecentPlaybackSection`。取消、失败后不反复重试、滚出再滚回来再试一次，都由现有的 `LaunchedEffect(thumbnailUri, dateModifiedSeconds, deferLoad)` 完成。`deferLoad` 变真时协程取消，已经显示的图不清掉，缓存写入也不撤销。

- [ ] **Step 1: Pass the already decided flag through the rows**

`SeriesLibraryRow`、`SingleLibraryRow`、`AnyLibraryRow` 的参数 `isListScrolling: Boolean` 改成 `deferThumbnailLoad: Boolean`。三个函数之间继续按这个参数名传递。`SeriesFolderItem` 和 `VideoListItem` 仍写 `deferThumbnailLoad = deferThumbnailLoad`。`entryKey(entry)` 仍只用于选中和标记，不拿它当列表 key。

`AnyLibraryRow` 改成：

```kotlin
@Composable
internal fun AnyLibraryRow(
    entry: VideoLibraryEntry,
    selectedKeys: Set<String>,
    selectionMode: Boolean,
    deferThumbnailLoad: Boolean,
    pinnedKeys: Set<String>,
    playbackStore: PlaybackStore,
    onToggleMark: (String) -> Unit,
    onToggleSelection: (String, Boolean) -> Unit,
    onOpenSeries: (String) -> Unit,
    onIncrementPlayCount: (String) -> Unit,
    onPlayVideo: (VideoItem) -> Unit,
    onEnterSelection: (String) -> Unit,
    onDeleteUris: (List<Uri>) -> Unit,
) {
    when (entry) {
        is VideoLibraryEntry.Series -> SeriesLibraryRow(
            entry = entry,
            selectedKeys = selectedKeys,
            selectionMode = selectionMode,
            deferThumbnailLoad = deferThumbnailLoad,
            pinnedKeys = pinnedKeys,
            playbackStore = playbackStore,
            onToggleMark = onToggleMark,
            onToggleSelection = onToggleSelection,
            onOpenSeries = onOpenSeries,
            onIncrementPlayCount = onIncrementPlayCount,
            onEnterSelection = onEnterSelection,
            onDeleteUris = onDeleteUris,
        )
        is VideoLibraryEntry.Single -> SingleLibraryRow(
            entry = entry,
            selectedKeys = selectedKeys,
            selectionMode = selectionMode,
            deferThumbnailLoad = deferThumbnailLoad,
            pinnedKeys = pinnedKeys,
            playbackStore = playbackStore,
            onToggleMark = onToggleMark,
            onToggleSelection = onToggleSelection,
            onPlayVideo = onPlayVideo,
            onEnterSelection = onEnterSelection,
            onDeleteUris = onDeleteUris,
        )
    }
}
```

`SeriesLibraryRow` 的参数和传给 `SeriesFolderItem` 的那一行改成：

```kotlin
internal fun SeriesLibraryRow(
    entry: VideoLibraryEntry.Series,
    selectedKeys: Set<String>,
    selectionMode: Boolean,
    deferThumbnailLoad: Boolean,
    pinnedKeys: Set<String>,
    playbackStore: PlaybackStore,
    onToggleMark: (String) -> Unit,
    onToggleSelection: (String, Boolean) -> Unit,
    onOpenSeries: (String) -> Unit,
    onIncrementPlayCount: (String) -> Unit,
    onEnterSelection: (String) -> Unit,
    onDeleteUris: (List<Uri>) -> Unit,
)
```

```kotlin
        deferThumbnailLoad = deferThumbnailLoad,
```

`SingleLibraryRow` 的参数和传给 `VideoListItem` 的那一行改成：

```kotlin
internal fun SingleLibraryRow(
    entry: VideoLibraryEntry.Single,
    selectedKeys: Set<String>,
    selectionMode: Boolean,
    deferThumbnailLoad: Boolean,
    pinnedKeys: Set<String>,
    playbackStore: PlaybackStore,
    onToggleMark: (String) -> Unit,
    onToggleSelection: (String, Boolean) -> Unit,
    onPlayVideo: (VideoItem) -> Unit,
    onEnterSelection: (String) -> Unit,
    onDeleteUris: (List<Uri>) -> Unit,
)
```

```kotlin
        deferThumbnailLoad = deferThumbnailLoad,
```

这两个函数的其余参数、进度读取和点击行为保持不动。

- [ ] **Step 2: Collect visible keys and decide per row**

在 `VideoListScreen` 里，`isListScrolling` 的 `derivedStateOf` 旁边增加：

```kotlin
    val visibleItemKeys by remember {
        derivedStateOf {
            listState.layoutInfo.visibleItemsInfo.map { it.key }.toSet()
        }
    }
```

不要按偏移或高度再过滤。`visibleItemsInfo` 里露出一点的项保留。

把本地的 `LibraryListRow` 换成：

```kotlin
    @Composable
    fun LibraryListRow(entry: VideoLibraryEntry, listKey: Any) {
        AnyLibraryRow(
            entry = entry,
            selectedKeys = selectedKeys,
            selectionMode = selectionMode,
            deferThumbnailLoad = !thumbnailShouldLoad(
                isScrolling = isListScrolling,
                itemKey = listKey,
                visibleKeys = visibleItemKeys,
            ),
            pinnedKeys = pinnedKeys,
            playbackStore = playbackStore,
            onToggleMark = ::toggleMark,
            onToggleSelection = toggleSelectionHandler,
            onOpenSeries = openSeriesHandler,
            onIncrementPlayCount = ::incrementPlayCount,
            onPlayVideo = playVideoHandler,
            onEnterSelection = enterSelectionHandler,
            onDeleteUris = openDeleteHandler,
        )
    }
```

- [ ] **Step 3: Pass each row the same key the list uses**

六处 `LibraryListRow(entry)` 都改成传入和 `items` 的 `key` 相同的值。

平铺列表，`key = { entry -> "flat-${viewLayout.name}-${entryKey(entry)}" }`，调用改成：

```kotlin
                                    Box(modifier = Modifier.animateItem(placementSpec = null)) {
                                        LibraryListRow(
                                            entry = entry,
                                            listKey = "flat-${viewLayout.name}-${entryKey(entry)}",
                                        )
                                    }
```

置顶分组，`key = { entry -> "sectioned-${viewLayout.name}-${entryKey(entry)}" }`，调用改成：

```kotlin
                                            Box(modifier = Modifier.animateItem(placementSpec = null)) {
                                                LibraryListRow(
                                                    entry = entry,
                                                    listKey = "sectioned-${viewLayout.name}-${entryKey(entry)}",
                                                )
                                            }
```

剧集、文件夹、短视频、未折叠的单集这四处，`key = { entry -> entryKey(entry) }`，每一处调用都改成：

```kotlin
                                            Box(modifier = Modifier.animateItem(placementSpec = null)) {
                                                LibraryListRow(
                                                    entry = entry,
                                                    listKey = entryKey(entry),
                                                )
                                            }
```

未折叠单集那一处保持它现有的更深缩进，调用改成：

```kotlin
                                                    Box(modifier = Modifier.animateItem(placementSpec = null)) {
                                                        LibraryListRow(
                                                            entry = entry,
                                                            listKey = entryKey(entry),
                                                        )
                                                    }
```

不要改这六处的 `key`、`contentType` 或 `Box`。最近播放、分类栏、加载、空库、没有结果和分组标题的 `item` 不调用 `LibraryListRow`。

- [ ] **Step 4: Verify**

Run: `./gradlew :app:testFullDebugUnitTest --tests com.pfpt.localvideoplayer.ui.ThumbnailLoadTest`

Expected: PASS。

Run: `./gradlew :app:testFullDebugUnitTest`

Expected: BUILD SUCCESSFUL。

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/pfpt/localvideoplayer/ui/library/LibraryRows.kt app/src/main/java/com/pfpt/localvideoplayer/ui/VideoListScreen.kt
git commit -m "$(cat <<'EOF'
Load library thumbnails only for rows on screen.

EOF
)"
```

提交前用 `git status` 确认规格和本计划不在暂存区。
