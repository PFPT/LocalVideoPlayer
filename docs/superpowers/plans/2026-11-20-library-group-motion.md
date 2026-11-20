# Library Group Motion Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 主列表里同一个 key 的视频行换位置时，用 200 毫秒滑过去。

**Architecture:** 刷新仍按现在的顺序发布。没有列表时先放单集，分组算完再换；已有列表时不拆回单集。`VideoListScreen` 增加共用的 `LibraryRowPlacement`，只接到六处包住 `LibraryListRow` 的 `Box` 上。其余 `animateItem(placementSpec = null)` 保持 `null`。

**Tech Stack:** Kotlin、Jetpack Compose、JUnit 4、Android Gradle `./gradlew :app:testFullDebugUnitTest`。

## Global Constraints

- 已经有列表时，分组在后台算完再替换，中间不拆回单集。还没有列表时，仍先放出单集，分组算完再换成组。
- 替换、排序或筛选让某一行换位置时，同一个 key 的视频行用 200 毫秒滑过去。单集收进剧集时，旧行离开，剧集行出现。不把某一集变形成剧集卡片。
- 刷新时的发布顺序保持现在这样，不新增 ViewModel 状态。
- 还没有列表时，先把扫描结果放成单集，再调用 `applyVideos`。`applyVideos` 仍在分组算完之后才写回 `_libraryEntries`。
- 已经有列表时，不先改成单集。需要整表重算时，仍等 `applyVideos` 算完再替换。不需要整表重算时，仍走现在的 `patchLibraryEntries`。
- `shouldFullyRegroup` 的条件不改。`loadFromCache` 有分组缓存时仍直接显示缓存；只有视频缓存、没有分组缓存时，仍先放单集再分组。
- 主列表六处视频行共用 `LibraryRowPlacement`，值是 `tween(durationMillis = 200)`。这六处是平铺、置顶、剧集、文件夹、短视频，以及未折叠的单集。
- 最近播放、分类栏、加载、空状态、没有结果和分组标题的 `placementSpec` 仍是 `null`。
- 切换分类时滚到顶部保持现状。不新做滚动恢复。
- 分组在写回列表之前抛出时，屏幕上已有的列表留下。不把已放出的单集清掉，也不改回转圈。
- `performRefresh` 的异常处理仍不清空列表，现有的刷新失败提示保持现状。`loadFromCache` 里分组抛出时，外层的 `runCatching` 仍接住，然后继续 `refresh`。`isLibraryLoading` 仍在现有的 `finally` 里关掉。
- 不增加新的失败文案，也不为分组失败加原地重试。
- 这一轮不新增测试，也不使用 Compose 或 Robolectric。`LibraryRefreshTest` 继续锁住整表重算的条件。200 毫秒的滑动不单测。
- 实现代码不添加注释。
- 这一轮不动：分组算法、排序和筛选的规则、`shouldFullyRegroup` 的阈值、缩略图、打开库、剧集详情和最近播放的动画、分组标题、分类栏、空状态的位置动画、下拉刷新、滚动位置恢复、播放、下载。
- 每个任务只提交该任务的代码。规格和本计划留在工作区，不放进这些提交。
- Gradle 命令不加 `--offline`。

## File Structure

- Modify: `app/src/main/java/com/pfpt/localvideoplayer/ui/VideoListScreen.kt` — 共用的 200 毫秒位置动画，以及六处视频行。

---

### Task 1: 主列表视频行的位置滑动

**Files:**
- Modify: `app/src/main/java/com/pfpt/localvideoplayer/ui/VideoListScreen.kt`
- Test: `./gradlew :app:testFullDebugUnitTest --tests com.pfpt.localvideoplayer.data.LibraryRefreshTest`

**Interfaces:**
- Consumes: 现有的 `Modifier.animateItem(placementSpec = ...)`。`androidx.compose.animation.core.tween` 已经导入。
- Produces: `private val LibraryRowPlacement = tween<IntOffset>(durationMillis = 200)`。

规格规定这一轮不新增测试。不要写新的测试文件，也不要为 200 毫秒加纯函数。不要改 `LibraryViewModel.kt`、`LibraryRefresh.kt`、`LibraryRows.kt`、`LocalVideoPlayerApp.kt`。

- [ ] **Step 1: Add the shared placement**

在 `import androidx.compose.ui.unit.dp` 上面增加：

```kotlin
import androidx.compose.ui.unit.IntOffset
```

在 `VIDEO_PICKER_MIME_TYPES` 后面增加：

```kotlin
private val LibraryRowPlacement = tween<IntOffset>(durationMillis = 200)
```

- [ ] **Step 2: Use it on the six video rows**

只改包住 `LibraryListRow` 的六个 `Box`。不要用全文替换把所有 `placementSpec = null` 都换掉。

平铺列表：

```kotlin
                                    Box(modifier = Modifier.animateItem(placementSpec = LibraryRowPlacement)) {
                                        LibraryListRow(
                                            entry = entry,
                                            listKey = "flat-${viewLayout.name}-${entryKey(entry)}",
                                        )
                                    }
```

置顶分组：

```kotlin
                                            Box(modifier = Modifier.animateItem(placementSpec = LibraryRowPlacement)) {
                                                LibraryListRow(
                                                    entry = entry,
                                                    listKey = "sectioned-${viewLayout.name}-${entryKey(entry)}",
                                                )
                                            }
```

剧集、文件夹、短视频这三处的 `Box` 缩进相同，每一处改成：

```kotlin
                                            Box(modifier = Modifier.animateItem(placementSpec = LibraryRowPlacement)) {
```

这三处的 `LibraryListRow`、`listKey = entryKey(entry)` 和 `Box` 的其余内容保持不动。

未折叠单集保持更深缩进：

```kotlin
                                                    Box(modifier = Modifier.animateItem(placementSpec = LibraryRowPlacement)) {
                                                        LibraryListRow(
                                                            entry = entry,
                                                            listKey = entryKey(entry),
                                                        )
                                                    }
```

改完后，`placementSpec = null` 必须仍留在最近播放、分类栏、加载、空库、没有结果和分组标题上。切换分类的 `listState.animateScrollToItem(0)` 保持不动。

- [ ] **Step 3: Verify**

Run: `./gradlew :app:testFullDebugUnitTest --tests com.pfpt.localvideoplayer.data.LibraryRefreshTest`

Expected: PASS。

Run: `./gradlew :app:testFullDebugUnitTest`

Expected: BUILD SUCCESSFUL。

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/pfpt/localvideoplayer/ui/VideoListScreen.kt
git commit -m "$(cat <<'EOF'
Slide library rows that keep the same key.

EOF
)"
```

提交前用 `git status` 确认规格和本计划不在暂存区。
