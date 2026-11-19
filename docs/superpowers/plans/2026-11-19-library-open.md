# Library Open Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 有上次的列表时，打开库不先出现「没有视频」，也不转圈。缓存读完就显示上次的列表，后台刷新直接替换。从来没有缓存时仍转圈。

**Architecture:** `libraryOpening` 是纯函数，按缓存是否读完、条目数、`isLibraryLoading` 返回留白、列表、转圈或空库。`LibraryViewModel.loadFromCache` 在 `finally` 里标记已读完；没有条目时先把加载设为真。`VideoListScreen` 用这个结果替换打开时的转圈和空状态。权限页、下拉刷新、扫描提前返回和分类滚到顶部保持现状。

**Tech Stack:** Kotlin、JUnit 4、Android Gradle `./gradlew :app:testFullDebugUnitTest`。

## Global Constraints

- 有上次的列表时，打开库不先出现「没有视频」，也不转圈。
- 磁盘读取完成前，列表区域留白。缓存行不要求画在第一次组合上。
- `libraryOpening(cacheResolved, entryCount, isLibraryLoading)` 的顺序是：未读完为留白，并且优先于条目数和 `isLibraryLoading`；`entryCount > 0` 为列表，即使正在加载；正在加载为转圈；其余为「没有视频」。
- `loadFromCache` 在 `finally` 里标记已读完。读到条目、读到空、读取失败都一样。读取失败按没有缓存处理。
- 缓存读完且没有条目时，返回前把 `isLibraryLoading` 设为真。随后的 `refresh(showLoading = true)` 保持这个状态。
- 有条目时 `refresh(showLoading = false)`。已有列表的刷新不打开转圈，结果直接替换列表。
- 没权限时仍是现在的权限页，不读缓存。
- 刷新抛出异常时，不把已有列表清成空。`isLibraryLoading` 仍在现有的 `finally` 里关掉。
- 扫描结果为空，并且这次不是下拉刷新时，保持现在的提前返回，留下上次的列表。
- 下拉刷新保持现在的转圈和结果处理。
- 替换列表时不新增滚动恢复。切换分类时滚到顶部保持现状。
- 分类筛选后的「没有结果」保持现状。`isSortingLibrary` 不再参与打开时的转圈判断。
- 纯函数测试不使用 Compose，也不使用 Robolectric。
- 实现代码不添加注释。
- 这一轮不动：缩略图、分组算法、排序、筛选、增量扫描、下拉刷新的手势和指示、滚动位置恢复、播放、下载。
- 每个任务只提交该任务的代码。规格和本计划留在工作区，不放进这些提交。
- Gradle 命令不加 `--offline`。

## File Structure

- Create: `app/src/main/java/com/pfpt/localvideoplayer/ui/LibraryOpening.kt` — 打开列表区域的四种结果和决定函数。
- Create: `app/src/test/java/com/pfpt/localvideoplayer/ui/LibraryOpeningTest.kt` — 四种结果和优先级。
- Modify: `app/src/main/java/com/pfpt/localvideoplayer/data/LibraryViewModel.kt` — 缓存已读完标志，以及 `loadFromCache` 的 `finally`。
- Modify: `app/src/main/java/com/pfpt/localvideoplayer/LocalVideoPlayerApp.kt` — 把 `cacheResolved` 传给列表。
- Modify: `app/src/main/java/com/pfpt/localvideoplayer/ui/VideoListScreen.kt` — 用决定函数替换打开时的转圈和空状态。

---

### Task 1: 打开列表的决定

**Files:**
- Create: `app/src/test/java/com/pfpt/localvideoplayer/ui/LibraryOpeningTest.kt`
- Create: `app/src/main/java/com/pfpt/localvideoplayer/ui/LibraryOpening.kt`
- Test: `app/src/test/java/com/pfpt/localvideoplayer/ui/LibraryOpeningTest.kt`

**Interfaces:**
- Consumes: 无。
- Produces: `enum class LibraryOpening { Hold, List, Loading, Empty }`。`fun libraryOpening(cacheResolved: Boolean, entryCount: Int, isLibraryLoading: Boolean): LibraryOpening`。

- [ ] **Step 1: Write the failing test**

把下面的文件写成 `app/src/test/java/com/pfpt/localvideoplayer/ui/LibraryOpeningTest.kt`：

```kotlin
package com.pfpt.localvideoplayer.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryOpeningTest {
    @Test
    fun unresolvedStaysBlankEvenWithEntriesAndLoading() {
        assertEquals(
            LibraryOpening.Hold,
            libraryOpening(cacheResolved = false, entryCount = 3, isLibraryLoading = true),
        )
    }

    @Test
    fun unresolvedEmptyStaysBlank() {
        assertEquals(
            LibraryOpening.Hold,
            libraryOpening(cacheResolved = false, entryCount = 0, isLibraryLoading = false),
        )
    }

    @Test
    fun entriesShowListWhileLoading() {
        assertEquals(
            LibraryOpening.List,
            libraryOpening(cacheResolved = true, entryCount = 1, isLibraryLoading = true),
        )
    }

    @Test
    fun emptyLoadingShowsSpinner() {
        assertEquals(
            LibraryOpening.Loading,
            libraryOpening(cacheResolved = true, entryCount = 0, isLibraryLoading = true),
        )
    }

    @Test
    fun resolvedEmptyShowsEmptyLibrary() {
        assertEquals(
            LibraryOpening.Empty,
            libraryOpening(cacheResolved = true, entryCount = 0, isLibraryLoading = false),
        )
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testFullDebugUnitTest --tests com.pfpt.localvideoplayer.ui.LibraryOpeningTest`

Expected: FAIL，`libraryOpening` 或 `LibraryOpening` 未定义。

- [ ] **Step 3: Write the minimal implementation**

把下面的文件写成 `app/src/main/java/com/pfpt/localvideoplayer/ui/LibraryOpening.kt`：

```kotlin
package com.pfpt.localvideoplayer.ui

enum class LibraryOpening {
    Hold,
    List,
    Loading,
    Empty,
}

fun libraryOpening(
    cacheResolved: Boolean,
    entryCount: Int,
    isLibraryLoading: Boolean,
): LibraryOpening {
    if (!cacheResolved) return LibraryOpening.Hold
    if (entryCount > 0) return LibraryOpening.List
    if (isLibraryLoading) return LibraryOpening.Loading
    return LibraryOpening.Empty
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :app:testFullDebugUnitTest --tests com.pfpt.localvideoplayer.ui.LibraryOpeningTest`

Expected: PASS。

Run: `./gradlew :app:testFullDebugUnitTest`

Expected: BUILD SUCCESSFUL。

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/pfpt/localvideoplayer/ui/LibraryOpening.kt app/src/test/java/com/pfpt/localvideoplayer/ui/LibraryOpeningTest.kt
git commit -m "$(cat <<'EOF'
Decide whether the library opens blank, listed, loading, or empty.

EOF
)"
```

---

### Task 2: 把决定接到打开库

**Files:**
- Modify: `app/src/main/java/com/pfpt/localvideoplayer/data/LibraryViewModel.kt`
- Modify: `app/src/main/java/com/pfpt/localvideoplayer/LocalVideoPlayerApp.kt`
- Modify: `app/src/main/java/com/pfpt/localvideoplayer/ui/VideoListScreen.kt`
- Test: `./gradlew :app:testFullDebugUnitTest`

**Interfaces:**
- Consumes: `libraryOpening(cacheResolved: Boolean, entryCount: Int, isLibraryLoading: Boolean): LibraryOpening`。
- Produces: `LibraryViewModel.cacheResolved: StateFlow<Boolean>`，初始值 `false`。

- [ ] **Step 1: Mark the cache resolved when the read finishes**

在 `_isLibraryLoading` 旁边增加：

```kotlin
    private val _cacheResolved = MutableStateFlow(false)
    val cacheResolved: StateFlow<Boolean> = _cacheResolved.asStateFlow()
```

把 `loadFromCache` 换成：

```kotlin
    suspend fun loadFromCache() {
        try {
            val hiddenUris = withContext(Dispatchers.IO) { hiddenVideosStore.getHiddenUris() }
            val cachedEntries = withContext(Dispatchers.IO) { libraryCacheStore.loadLibraryEntries() }
            if (cachedEntries.isNotEmpty()) {
                _libraryEntries.value = cachedEntries.filterNotHidden(hiddenUris)
                rememberSignatures(flattenVideos(_libraryEntries.value))
                return
            }
            val videos = withContext(Dispatchers.IO) { libraryCacheStore.loadVideos() }
                .filter { it.uri.toString() !in hiddenUris }
            if (videos.isEmpty()) return
            _libraryEntries.value = videos.map { VideoLibraryEntry.Single(it) }
            applyVideos(videos, emptySet())
            rememberSignatures(flattenVideos(_libraryEntries.value))
        } finally {
            if (_libraryEntries.value.isEmpty()) {
                _isLibraryLoading.value = true
            }
            _cacheResolved.value = true
        }
    }
```

`finally` 先打开加载，再标记已读完。读取抛出异常时，已有条目保留；没有条目时加载为真，已读完仍为真。

不要改 `performRefresh` 里这两段：

```kotlin
            if (scannedWithFolders.isEmpty() && _libraryEntries.value.isNotEmpty() && !fromPull) {
                return true
            }
```

```kotlin
        } finally {
            _isLibraryLoading.value = false
        }
```

不要改下拉刷新的 `_isRefreshing`。

- [ ] **Step 2: Pass the flag into the list**

在 `LocalVideoPlayerApp` 里，`isLibraryLoading` 的收集旁边增加：

```kotlin
    val cacheResolved by libraryViewModel.cacheResolved.collectAsState()
```

`VideoListScreen` 增加参数 `cacheResolved: Boolean`，放在 `isLibraryLoading` 后面。调用处传入 `cacheResolved = cacheResolved`。

`refresh(showLoading = libraryViewModel.libraryEntries.value.isEmpty())` 这一行保持不变。

- [ ] **Step 3: Replace the opening spinner and empty state**

`VideoListScreen` 里，打开时的转圈条件和空库条件换成：

```kotlin
                            when (
                                libraryOpening(
                                    cacheResolved = cacheResolved,
                                    entryCount = libraryEntries.size,
                                    isLibraryLoading = isLibraryLoading,
                                )
                            ) {
                                LibraryOpening.Hold, LibraryOpening.List -> Unit
                                LibraryOpening.Loading -> {
                                    item(key = "library-loading") {
                                        Box(
                                            modifier = Modifier
                                                .fillParentMaxWidth()
                                                .padding(vertical = 48.dp)
                                                .animateItem(placementSpec = null),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            CircularProgressIndicator(color = theme.accent)
                                        }
                                    }
                                }
                                LibraryOpening.Empty -> {
                                    if (!hasRecentPlayback) {
                                        item(key = "empty") {
                                            LumenEmptyState(
                                                title = stringResource(R.string.empty_video_list),
                                                subtitle = stringResource(R.string.empty_video_hint),
                                                icon = Icons.Default.VideoLibrary,
                                                modifier = Modifier
                                                    .fillParentMaxSize()
                                                    .padding(24.dp)
                                                    .animateItem(placementSpec = null),
                                            )
                                        }
                                    }
                                }
                            }
```

转圈项和空库项的文案、图标、间距保持现在的写法。`LibraryOpening.Empty` 仍要求 `!hasRecentPlayback`。

`key = "no-filter-results"` 那一段保持现状，包括 `isSortingLibrary`。切换分类时 `listState.animateScrollToItem(0)` 保持现状。权限页的 `!hasPermission` 分支保持现状。

- [ ] **Step 4: Verify**

Run: `./gradlew :app:testFullDebugUnitTest --tests com.pfpt.localvideoplayer.ui.LibraryOpeningTest`

Expected: PASS。

Run: `./gradlew :app:testFullDebugUnitTest`

Expected: BUILD SUCCESSFUL。

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/pfpt/localvideoplayer/data/LibraryViewModel.kt app/src/main/java/com/pfpt/localvideoplayer/LocalVideoPlayerApp.kt app/src/main/java/com/pfpt/localvideoplayer/ui/VideoListScreen.kt
git commit -m "$(cat <<'EOF'
Show the cached library before an empty or loading state.

EOF
)"
```

提交前用 `git status` 确认规格和本计划不在暂存区。
