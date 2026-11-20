# Library Tab Scroll Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 切回某个库分类时，列表第一帧就停在离开时的位置。

**Architecture:** `VideoListScreen` 为六个 `LibraryTypeTab` 各保留一块 `rememberSaveable(saver = LazyListState.Saver)`。当前列表使用当前分类的那一块。删掉切换分类时滚到顶部的那段。

**Tech Stack:** Kotlin、Jetpack Compose、JUnit 4、Android Gradle `./gradlew :app:testFullDebugUnitTest`。

## Global Constraints

- 从「全部」切到别的分类再切回来，列表第一帧就停在离开时的位置。不从顶部滑过去。
- `VideoListScreen` 为 `LibraryTypeTab` 的六项各保留一块 `rememberSaveable(saver = LazyListState.Saver)`：`ALL`、`EPISODES`、`FOLDERS`、`SHORT_CLIPS`、`SINGLES`、`MARKED`。保存键是 `library-scroll-` 加上分类名。这六块在库页面每次组合时都创建，不放进当前分类的条件分支里。
- 当前列表、可见项、缩略图和回到顶部按钮都使用当前分类的那一块。切换分类时删掉滚到顶部的那段。切过去的第一帧就是那一块里的第一条可见项和偏移。没有保存过的分类停在顶部，不做滑动。
- 搜索继续使用当前分类的这一块。进入搜索、输入、清空搜索都不主动滚动。排序和筛选也不主动滚动。
- 列表变短、保存的索引超出末尾时，列表进到最后一项。不另写修正。
- 离开库再从播放页返回时，这六块位置跟着现在这一次库页面的保存走。不另建一份按分类存放的仓库。
- 不新增测试，也不使用 Compose 或 Robolectric。
- 实现代码不添加注释。
- 这一轮不动：剧集详情的滚动位置、回到顶部按钮对当前分类的行为、共享元素、圆角和哪一张缩略图注册、缩略图的加载和占位规则、主列表 200 毫秒的行滑动、播放决定和下载。
- 每个任务只提交该任务的代码。规格和本计划留在工作区，不放进这些提交。
- Gradle 命令不加 `--offline`。

## File Structure

- Modify: `app/src/main/java/com/pfpt/localvideoplayer/ui/VideoListScreen.kt` — 六个分类的列表状态，并删掉切换分类时滚到顶部。
- 不改 `SeriesDetailScreen.kt`。

---

### Task 1: 每个分类记住滚动位置

**Files:**
- Modify: `app/src/main/java/com/pfpt/localvideoplayer/ui/VideoListScreen.kt:354-364`
- Test: `./gradlew :app:testFullDebugUnitTest`

**Interfaces:**
- Consumes: `com.pfpt.localvideoplayer.model.LibraryTypeTab`，六项是 `ALL`、`EPISODES`、`FOLDERS`、`SHORT_CLIPS`、`SINGLES`、`MARKED`。`rememberSaveable` 和 `LazyListState` 已在这个文件导入。
- Produces: `val listState: LazyListState`，它是 `listStates.getValue(selectedTypeTab)`。后面的 `isListScrolling`、`visibleItemKeys`、`isListScrolled`、`LaunchedEffect(listState)` 和 `LazyColumn(state = listState)` 继续读这个 `listState`。

规格规定这一轮不新增测试。不要在搜索、排序或筛选变化时滚动。不要改回到顶部按钮里对 `listState` 的 `animateScrollBy`。

- [ ] **Step 1: Give each tab its own list state**

把 `VideoListScreen.kt` 里的：

```kotlin
    val listState = rememberSaveable(saver = LazyListState.Saver) {
        LazyListState()
    }
    var previousTypeTab by rememberSaveable { mutableStateOf(selectedTypeTab) }

    LaunchedEffect(selectedTypeTab) {
        if (previousTypeTab != selectedTypeTab) {
            listState.animateScrollToItem(0)
            previousTypeTab = selectedTypeTab
        }
    }
```

换成：

```kotlin
    val listStates = LibraryTypeTab.entries.associateWith { tab ->
        rememberSaveable(
            saver = LazyListState.Saver,
            key = "library-scroll-${tab.name}",
        ) {
            LazyListState()
        }
    }
    val listState = listStates.getValue(selectedTypeTab)
```

`LibraryTypeTab.entries` 的顺序保持枚举声明顺序。六次 `rememberSaveable` 都在库页面的这次组合里执行，不放进 `if (selectedTypeTab == ...)` 里。

- [ ] **Step 2: Verify**

Run: `./gradlew :app:testFullDebugUnitTest`

Expected: BUILD SUCCESSFUL。

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/pfpt/localvideoplayer/ui/VideoListScreen.kt
git commit -m "$(cat <<'EOF'
Remember the library scroll position for each category.

EOF
)"
```

提交前用 `git status` 确认规格和本计划不在暂存区。
