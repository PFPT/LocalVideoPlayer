# Series Scroll Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 从剧集详情进入播放页再返回时，详情列表停在离开时的位置。

**Architecture:** `SeriesDetailScreen` 的 `listState` 改用 `rememberSaveable(saver = LazyListState.Saver)`，和主列表一样。位置存在这一次详情的返回栈里，返回的第一帧就是离开时的第一条可见项和偏移。

**Tech Stack:** Kotlin、Jetpack Compose、JUnit 4、Android Gradle `./gradlew :app:testFullDebugUnitTest`。

## Global Constraints

- 从剧集详情进入播放页，再返回时，详情列表停在离开时的位置。退出详情、再从库里打开这部剧，从顶部开始。
- `SeriesDetailScreen` 的 `listState` 改成和主列表一样：`rememberSaveable(saver = LazyListState.Saver)`。保存的是第一条可见项和偏移，存在这一次详情的返回栈里。
- 播放页盖在上面时，这次保存还在。返回的第一帧就是离开时的位置，不调用 `scrollToItem`，也不调用 `animateScrollToItem`。那一集还在组合里，返回的飞回可以接上。不把列表滚回去找出那一行。
- 退出详情后，这次返回栈丢掉。再从库里打开，从顶部开始。不按剧集 id 另存一份位置。
- 列表变短、保存的索引超出末尾时，列表进到最后一项。不另写修正。
- 不新增测试，也不使用 Compose 或 Robolectric。
- 实现代码不添加注释。
- 这一轮不动：主列表的滚动位置、切换库分类时滚回顶部、共享元素、圆角和哪一张缩略图注册、主列表 200 毫秒的行滑动、缩略图的加载和占位规则、播放决定和下载。
- 每个任务只提交该任务的代码。规格和本计划留在工作区，不放进这些提交。
- Gradle 命令不加 `--offline`。

## File Structure

- Modify: `app/src/main/java/com/pfpt/localvideoplayer/ui/SeriesDetailScreen.kt` — 把详情列表的 `listState` 改成可保存的 `LazyListState`。
- 不改 `VideoListScreen.kt`。主列表已经是 `rememberSaveable(saver = LazyListState.Saver)`。

---

### Task 1: 记住剧集详情的滚动位置

**Files:**
- Modify: `app/src/main/java/com/pfpt/localvideoplayer/ui/SeriesDetailScreen.kt:207`
- Test: `./gradlew :app:testFullDebugUnitTest`

**Interfaces:**
- Consumes: `androidx.compose.foundation.lazy.LazyListState` 和 `androidx.compose.runtime.saveable.rememberSaveable`。`rememberSaveable` 在这个文件里已经导入。
- Produces: `SeriesDetailScreen` 里的 `listState` 使用 `LazyListState.Saver`。现有的 `visibleItemKeys`、`deferThumbnailLoad` 和 `itemsIndexed` 继续读这个 `listState`。

规格规定这一轮不新增测试。不要调用 `scrollToItem` 或 `animateScrollToItem`。不要把位置按 `series.id` 存到详情外面。

- [ ] **Step 1: Save the list state with the back stack**

`SeriesDetailScreen.kt` 增加导入：

```kotlin
import androidx.compose.foundation.lazy.LazyListState
```

如果 `rememberLazyListState` 不再使用，删掉这一行导入：

```kotlin
import androidx.compose.foundation.lazy.rememberLazyListState
```

把：

```kotlin
        val listState = rememberLazyListState()
```

换成：

```kotlin
        val listState = rememberSaveable(saver = LazyListState.Saver) {
            LazyListState()
        }
```

`listState` 仍放在 `AnimatedContent` 外面。下面的 `LazyColumn`、`visibleItemKeys` 和 `itemsIndexed` 不改。

- [ ] **Step 2: Verify**

Run: `./gradlew :app:testFullDebugUnitTest`

Expected: BUILD SUCCESSFUL。

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/pfpt/localvideoplayer/ui/SeriesDetailScreen.kt
git commit -m "$(cat <<'EOF'
Keep the series list where it was after playback.

EOF
)"
```

提交前用 `git status` 确认规格和本计划不在暂存区。
