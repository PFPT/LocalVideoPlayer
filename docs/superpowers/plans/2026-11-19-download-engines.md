# Download Engines Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `full` 的下载行为保持现状。`DownloadService` 不再自己实现渐进式、HLS 分片和种子下载。`lite` 能编译，并且不链接 FFmpeg 和 libtorrent。

**Architecture:** 调度、通知、进度写入和媒体库登记留在 `DownloadService`。渐进式、HLS 分片、种子编排分别进入 `ProgressiveDownloader`、`HlsDownloader`、`TorrentDownloader`。共享的 HTTP 请求进入 `DownloadRequests`。`HlsMergeEngine` 和 `TorrentEngine` 只在 `src/full` 与 `src/lite` 里出现，包名和类型名相同。`full` 原样转发已经移入 `src/full` 的 `HlsMerger` 和 `TorrentSession`。`lite` 的合并和磁力下载抛出 `AdvancedDownloadUnavailable`。

**Tech Stack:** Kotlin、JUnit 4、Android Gradle product flavors `full` / `lite`、`./gradlew :app:testFullDebugUnitTest`、`:app:testLiteDebugUnitTest`、`:app:compileLiteDebugKotlin`。

## Global Constraints

- `full` 的渐进式、HLS 和种子下载行为保持现状。
- `DownloadService` 继续负责队列、并发槽、暂停和取消、通知、进度写入、失败状态，以及把完成文件登记进媒体库。
- `download` 的分发顺序保持：已有合并结果直接入库；磁力链接交给 `TorrentDownloader`；HLS 交给 `HlsDownloader`；其余交给 `ProgressiveDownloader`。
- 三个下载器通过现有方法调用和函数参数接收进度，不新增监听接口。Service 写 `DownloadManager` 和通知。
- `HlsDownloader` 下完分片后调用 `onMerging`，再在 Service 传入的 `mergeDispatcher` 上调用 `HlsMergeEngine.merge`。它不捕获 `AdvancedDownloadUnavailable`，不包含合并策略，不做重命名，不入库。
- Service 接着替换 `.part`，清掉分片目录，然后 `publishPart`。合并失败时先原样抛出 `AdvancedDownloadUnavailable`，再按现有规则包装其他异常。
- 种子下载返回视频文件后，Service 继续补扩展名、`publishPart`，并删除种子目录。暂停且未取消时，仍抛出现有的暂停文案。
- `AdvancedDownloadUnavailable` 继承 `IOException`，不带消息。
- `failureMessage` 在现有分支之前识别 `AdvancedDownloadUnavailable`，返回 `download_requires_full`，文案是「当前安装包不包含这项下载」。
- `HlsMerger.kt` 和 `TorrentSession.kt` 原样移动。策略顺序、命令、二进制拼接后的 FFmpeg 调用、tracker、优先级和速度逻辑保持不变。
- `libs.ffmpeg.kit.min`、`libs.libtorrent4j`、`libs.libtorrent4j.android.arm`、`libs.libtorrent4j.android.arm64`、`libs.libtorrent4j.android.x64` 改为 `fullImplementation`。
- 删除两个风味的 `HAS_ADVANCED_DOWNLOAD`。`buildConfig` 设为 `false`。
- `processTask` 对暂停、取消和其他异常的处理保持现状。
- `app/src/test/java/` 不再引用 `HlsMerger`、`TorrentSession`、FFmpegKit 或 libtorrent。
- 实现代码不添加注释。
- 这一轮不动：分片算法、断点续传、多连接渐进式算法、通知文案、并发槽、媒体库登记、下载界面、播放决定。
- 每个任务只提交该任务的代码。规格和本计划留在工作区，不放进这些提交。
- Gradle 命令不加 `--offline`。

## File Structure

- Create: `app/src/main/java/com/pfpt/localvideoplayer/download/AdvancedDownloadUnavailable.kt` — 无消息异常，以及 `failureMessage` 使用的纯函数。
- Create: `app/src/test/java/com/pfpt/localvideoplayer/download/DownloadFailureMessageTest.kt` — 失败文案的分支顺序。
- Create: `app/src/main/java/com/pfpt/localvideoplayer/download/DownloadRequests.kt` — `executeRequest`、`executeHlsRequest`、`contentTotalBytes`。
- Create: `app/src/main/java/com/pfpt/localvideoplayer/download/ProgressiveDownloader.kt` — 渐进式探测、单连接、多区间和拷贝。
- Create: `app/src/main/java/com/pfpt/localvideoplayer/download/HlsDownloader.kt` — HLS 分片下载，以及之后的 `merge`。
- Create: `app/src/main/java/com/pfpt/localvideoplayer/download/TorrentDownloader.kt` — 把磁力下载转给引擎。
- Create: `app/src/full/java/com/pfpt/localvideoplayer/download/HlsMergeEngine.kt` — 把参数装进 `HlsMerger.MergeInput` 并调用 `mergeToMp4`。
- Create: `app/src/full/java/com/pfpt/localvideoplayer/download/TorrentEngine.kt` — 原样转发 `downloadMagnet` 和 `releaseForServiceDestroy`。
- Create: `app/src/lite/java/com/pfpt/localvideoplayer/download/HlsMergeEngine.kt` — 抛出 `AdvancedDownloadUnavailable`。
- Create: `app/src/lite/java/com/pfpt/localvideoplayer/download/TorrentEngine.kt` — `downloadMagnet` 抛出同一异常，`releaseForServiceDestroy` 不做事。
- Create: `app/src/testLite/java/com/pfpt/localvideoplayer/download/LiteDownloadEnginesTest.kt` — 两个 lite 桩都抛出该异常。
- Move: `app/src/main/java/com/pfpt/localvideoplayer/download/HlsMerger.kt` → `app/src/full/java/com/pfpt/localvideoplayer/download/HlsMerger.kt`
- Move: `app/src/main/java/com/pfpt/localvideoplayer/download/torrent/TorrentSession.kt` → `app/src/full/java/com/pfpt/localvideoplayer/download/torrent/TorrentSession.kt`
- Move: `HlsMergerTest.kt` 和 `HlsMergerErrorTest.kt` → `app/src/testFull/java/com/pfpt/localvideoplayer/download/`
- Modify: `DownloadService.kt` — 分发、进度方法和合并失败包装。
- Modify: `app/build.gradle.kts` — 依赖风味和 `buildConfig`。
- Modify: `app/src/main/res/values/strings.xml` — 新增 `download_requires_full`。

进度方法留在 Service 上。Kotlin 的函数类型不能用命名参数调用，而现有循环使用命名参数，所以 `reportProgress`、`reportTrackedProgress` 保持为 Service 的 `internal` 方法。下载器接收 `service: DownloadService`，把这些调用改成 `service.方法(...)`。`onMerging`、种子的 `onName` 和 `onProgress` 是函数参数。

---

### Task 1: 不可用下载的失败文案

**Files:**
- Create: `app/src/test/java/com/pfpt/localvideoplayer/download/DownloadFailureMessageTest.kt`
- Create: `app/src/main/java/com/pfpt/localvideoplayer/download/AdvancedDownloadUnavailable.kt`
- Modify: `app/src/main/java/com/pfpt/localvideoplayer/download/DownloadService.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Test: `app/src/test/java/com/pfpt/localvideoplayer/download/DownloadFailureMessageTest.kt`

**Interfaces:**
- Consumes: 现有 `failureMessage` 的超时和空白消息分支。
- Produces: `class AdvancedDownloadUnavailable : IOException`，无参构造，无消息。`fun downloadFailureMessage(error: Exception, unavailableMessage: String, timeoutMessage: String, fallbackMessage: String): String`。

- [ ] **Step 1: Write the failing test**

把下面的文件写成 `app/src/test/java/com/pfpt/localvideoplayer/download/DownloadFailureMessageTest.kt`：

```kotlin
package com.pfpt.localvideoplayer.download

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException

class DownloadFailureMessageTest {
    @Test
    fun unavailableWinsOverBlankMessage() {
        assertEquals(
            "当前安装包不包含这项下载",
            downloadFailureMessage(
                error = AdvancedDownloadUnavailable(),
                unavailableMessage = "当前安装包不包含这项下载",
                timeoutMessage = "连接超时，点击继续重试",
                fallbackMessage = "无法访问视频直链，请检查分享链接是否有效",
            ),
        )
    }

    @Test
    fun timeoutUsesTimeoutMessage() {
        assertEquals(
            "连接超时，点击继续重试",
            downloadFailureMessage(
                error = SocketTimeoutException(),
                unavailableMessage = "当前安装包不包含这项下载",
                timeoutMessage = "连接超时，点击继续重试",
                fallbackMessage = "无法访问视频直链，请检查分享链接是否有效",
            ),
        )
    }

    @Test
    fun timeoutCauseUsesTimeoutMessage() {
        assertEquals(
            "连接超时，点击继续重试",
            downloadFailureMessage(
                error = IOException("wrapped", SocketTimeoutException()),
                unavailableMessage = "当前安装包不包含这项下载",
                timeoutMessage = "连接超时，点击继续重试",
                fallbackMessage = "无法访问视频直链，请检查分享链接是否有效",
            ),
        )
    }

    @Test
    fun blankMessageUsesFallback() {
        assertEquals(
            "无法访问视频直链，请检查分享链接是否有效",
            downloadFailureMessage(
                error = IOException(" "),
                unavailableMessage = "当前安装包不包含这项下载",
                timeoutMessage = "连接超时，点击继续重试",
                fallbackMessage = "无法访问视频直链，请检查分享链接是否有效",
            ),
        )
    }

    @Test
    fun otherMessageIsPreserved() {
        assertEquals(
            "HTTP 404",
            downloadFailureMessage(
                error = IOException("HTTP 404"),
                unavailableMessage = "当前安装包不包含这项下载",
                timeoutMessage = "连接超时，点击继续重试",
                fallbackMessage = "无法访问视频直链，请检查分享链接是否有效",
            ),
        )
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testFullDebugUnitTest --tests com.pfpt.localvideoplayer.download.DownloadFailureMessageTest`

Expected: FAIL，`downloadFailureMessage` 或 `AdvancedDownloadUnavailable` 未定义。

- [ ] **Step 3: Write the minimal implementation**

把下面的文件写成 `app/src/main/java/com/pfpt/localvideoplayer/download/AdvancedDownloadUnavailable.kt`：

```kotlin
package com.pfpt.localvideoplayer.download

import java.io.IOException
import java.net.SocketTimeoutException

class AdvancedDownloadUnavailable : IOException()

fun downloadFailureMessage(
    error: Exception,
    unavailableMessage: String,
    timeoutMessage: String,
    fallbackMessage: String,
): String {
    if (error is AdvancedDownloadUnavailable) return unavailableMessage
    if (error is SocketTimeoutException || error.cause is SocketTimeoutException) return timeoutMessage
    return error.message?.takeIf { it.isNotBlank() } ?: fallbackMessage
}
```

在 `app/src/main/res/values/strings.xml` 的 `download_timeout_retry` 后面增加：

```xml
    <string name="download_requires_full">当前安装包不包含这项下载</string>
```

把 `DownloadService.failureMessage` 换成：

```kotlin
    private fun failureMessage(error: Exception, @Suppress("UNUSED_PARAMETER") taskUrl: String = ""): String {
        return downloadFailureMessage(
            error = error,
            unavailableMessage = getString(R.string.download_requires_full),
            timeoutMessage = getString(R.string.download_timeout_retry),
            fallbackMessage = getString(R.string.download_direct_link_failed),
        )
    }
```

保留 `taskUrl` 参数和现有调用点。`processTask` 不改。

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :app:testFullDebugUnitTest --tests com.pfpt.localvideoplayer.download.DownloadFailureMessageTest`

Expected: PASS。

Run: `./gradlew :app:testFullDebugUnitTest`

Expected: BUILD SUCCESSFUL。

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/pfpt/localvideoplayer/download/AdvancedDownloadUnavailable.kt app/src/test/java/com/pfpt/localvideoplayer/download/DownloadFailureMessageTest.kt app/src/main/java/com/pfpt/localvideoplayer/download/DownloadService.kt app/src/main/res/values/strings.xml
git commit -m "$(cat <<'EOF'
Map unavailable downloads to their own failure message.

EOF
)"
```

---

### Task 2: 渐进式下载离开 Service

**Files:**
- Create: `app/src/main/java/com/pfpt/localvideoplayer/download/DownloadRequests.kt`
- Create: `app/src/main/java/com/pfpt/localvideoplayer/download/ProgressiveDownloader.kt`
- Modify: `app/src/main/java/com/pfpt/localvideoplayer/download/DownloadService.kt`
- Test: `./gradlew :app:testFullDebugUnitTest`

**Interfaces:**
- Consumes: `DownloadService.TaskControl`，`reportProgress`。
- Produces: `internal object DownloadRequests`，方法 `executeRequest`、`executeHlsRequest`、`contentTotalBytes`，签名与现在的 Service 私有方法相同。`internal object ProgressiveDownloader` 的 `suspend fun download(service: DownloadService, task: DownloadTask, part: File, manager: DownloadManager, control: DownloadService.TaskControl)`。

- [ ] **Step 1: Move the shared request functions**

新建 `DownloadRequests.kt`，文件头是：

```kotlin
package com.pfpt.localvideoplayer.download

import okhttp3.Request
import okhttp3.Response

internal object DownloadRequests {
```

把 `DownloadService` 里的 `executeHlsRequest`、`executeRequest`、`contentTotalBytes` 原样放进这个 object，函数可见性改为 `internal`，函数体不改。这三个函数目前是：

```kotlin
    private fun executeHlsRequest(
        address: String,
        headers: Map<String, String>,
        playlistUrl: String?,
    ): Response

    private fun executeRequest(
        address: String,
        headers: Map<String, String>,
        method: String = "GET",
        rangeStart: Long? = null,
        rangeHeader: String? = null,
    ): Response

    private fun contentTotalBytes(response: Response, offset: Long): Long
```

三个函数放进 `DownloadRequests` 后，object 以 `}` 结束。从 `DownloadService` 删除这三个函数。Service 和后续下载器里的调用改成 `DownloadRequests.executeRequest`、`DownloadRequests.executeHlsRequest`、`DownloadRequests.contentTotalBytes`。

- [ ] **Step 2: Move progressive download without changing its decisions**

把 `DownloadService` 的嵌套 `private class TaskControl` 改成 `internal class TaskControl`。

把下面这些 companion 常量从 `private` 改成 `internal`：`IO_BUFFER_SIZE`、`SEGMENT_MAX_RETRIES`、`RETRY_BACKOFF_MS`、`PROGRESS_STEP`。

把 `reportProgress` 从 `private` 改成 `internal`。签名保持：

```kotlin
    internal suspend fun reportProgress(
        task: DownloadTask,
        manager: DownloadManager,
        downloaded: Long,
        total: Long,
        force: Boolean = false,
    )
```

新建 `ProgressiveDownloader.kt`：

```kotlin
package com.pfpt.localvideoplayer.download

import com.pfpt.localvideoplayer.R
import com.pfpt.localvideoplayer.download.DownloadService.TaskControl
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

internal object ProgressiveDownloader {
    suspend fun download(
        service: DownloadService,
        task: DownloadTask,
        part: File,
        manager: DownloadManager,
        control: TaskControl,
    ) {
        val connections = DownloadManager.get(service).connectionsPerDownload()
        val probe = probeProgressive(service, task.url, task.headers, control)
        if (probe != null &&
            probe.acceptsRanges &&
            ProgressiveRangePlanner.shouldUseMultiRange(probe.totalBytes, connections)
        ) {
            downloadProgressiveMultiRange(service, task, part, probe.totalBytes, connections, manager, control)
            return
        }
        downloadProgressiveSingle(service, task, part, manager, control)
    }
}
```

把下面这些函数从 `DownloadService` 原样搬进 `ProgressiveDownloader`，并删除 Service 里的原函数：

- `ProgressiveProbe`
- `probeProgressive`
- `downloadProgressiveSingle`
- `downloadProgressiveMultiRange`
- `downloadByteRange`
- `rangePartFile`
- `cleanupRangeParts`（实例方法，不是 companion 的同名方法）
- `copyStream`

搬迁时只做这些替换：

- 每个搬迁函数增加第一个参数 `service: DownloadService`。调用这些函数时传入同一个 `service`。
- `getString(` 改成 `service.getString(`。
- `reportProgress(` 改成 `service.reportProgress(`。
- `cacheDir` 改成 `service.cacheDir`。
- `DownloadManager.get(this)` 不留在搬迁函数里；连接数已经在 `download` 入口读取。
- `executeRequest(` 改成 `DownloadRequests.executeRequest(`。
- `contentTotalBytes(` 改成 `DownloadRequests.contentTotalBytes(`。
- 实例方法 `cleanupRangeParts(task.id)` 改成 `DownloadService.cleanupRangeParts(service, task.id)`。
- `IO_BUFFER_SIZE`、`SEGMENT_MAX_RETRIES`、`RETRY_BACKOFF_MS`、`PROGRESS_STEP` 改成 `DownloadService` 的同名常量。
- `TaskControl` 使用 `DownloadService.TaskControl`。

探测、区间、重试次数、退避时间和进度节流的数值不改。`nextDownloadAttempt` 继续调用现有的顶层函数。

`DownloadService.download` 里，完成文件短路和 `publishPart` 留在 Service。只把 `downloadProgressive(task, part, manager, control)` 换成：

```kotlin
        ProgressiveDownloader.download(
            service = this,
            task = task,
            part = part,
            manager = manager,
            control = control,
        )
```

- [ ] **Step 3: Verify the service no longer owns progressive download**

Run: `rg "fun downloadProgressive|fun executeRequest|fun contentTotalBytes" app/src/main/java/com/pfpt/localvideoplayer/download/DownloadService.kt`

Expected: 无匹配。

Run: `./gradlew :app:testFullDebugUnitTest`

Expected: BUILD SUCCESSFUL。

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/pfpt/localvideoplayer/download/DownloadRequests.kt app/src/main/java/com/pfpt/localvideoplayer/download/ProgressiveDownloader.kt app/src/main/java/com/pfpt/localvideoplayer/download/DownloadService.kt
git commit -m "$(cat <<'EOF'
Move progressive downloads out of DownloadService.

EOF
)"
```

---

### Task 3: HLS 分片下载离开 Service

**Files:**
- Create: `app/src/main/java/com/pfpt/localvideoplayer/download/HlsDownloader.kt`
- Modify: `app/src/main/java/com/pfpt/localvideoplayer/download/DownloadService.kt`
- Test: `./gradlew :app:testFullDebugUnitTest`

**Interfaces:**
- Consumes: `DownloadRequests.executeHlsRequest`，`DownloadService.TaskControl`，`reportTrackedProgress`。
- Produces: `internal object HlsDownloader` 的 `suspend fun download(service: DownloadService, task: DownloadTask, hlsPackage: File, manager: DownloadManager, control: DownloadService.TaskControl)`。这一步仍由 Service 调用 `HlsMerger`。

- [ ] **Step 1: Move segment download**

把下面这些 companion 常量从 `private` 改成 `internal`：`LIVE_PROGRESS_INTERVAL_MS`、`HLS_SOURCE_PLAYLIST_NAME`、`AES_128_KEY_BYTES`。

把下面这些成员从 `private` 改成 `internal`：`speedTrackers`、`hlsByteCounters`、`hlsInflightBytes`、`hlsSizeHints`、`reportTrackedProgress`、`hlsFailureMessage`、`hlsSegmentFile`、`resolveSegmentFile`、`hlsInitFile`、`directorySize`。

`hlsFailureMessage` 留在 Service。`resolveMergePlaylist` 仍调用它。

`reportTrackedProgress` 的参数保持不变。

新建 `HlsDownloader.kt`，公开入口是：

```kotlin
package com.pfpt.localvideoplayer.download

import com.pfpt.localvideoplayer.R
import com.pfpt.localvideoplayer.download.DownloadService.TaskControl
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

internal object HlsDownloader {
    suspend fun download(
        service: DownloadService,
        task: DownloadTask,
        hlsPackage: File,
        manager: DownloadManager,
        control: TaskControl,
    ) {
        val resolved = resolveHlsSource(service, task, hlsPackage, control)
        val playlist = resolved.playlist
        val resumeState = HlsResume.resolve(task, hlsPackage, playlist) { index ->
            service.hlsSegmentFile(hlsPackage, playlist.segmentFormat(), index)
        }
        val segmentFormat = playlist.segmentFormat()
        val completedSegments = resumeState.completedSegments
        if (resumeState.shouldWipe) hlsPackage.listFiles()?.forEach { it.delete() }
        File(hlsPackage, DownloadService.HLS_SOURCE_PLAYLIST_NAME).writeText(resolved.mediaText)
        val initialBytes = if (resumeState.shouldWipe) 0L else service.directorySize(hlsPackage)
        service.hlsByteCounters[task.id] = java.util.concurrent.atomic.AtomicLong(initialBytes)
        manager.update(task.id) {
            it.copy(
                hlsCompletedSegments = completedSegments,
                hlsTotalSegments = playlist.segments.size,
                hlsPlaylistUrl = playlist.url,
                hlsPlaylistSignature = playlist.signature,
                downloadedBytes = initialBytes,
                totalBytes = estimateHlsTotalBytes(service, initialBytes, completedSegments, playlist.segments.size, task.id),
                isMerging = false,
            )
        }
        val completedCounter = java.util.concurrent.atomic.AtomicInteger(completedSegments)
        suspend fun publishSnapshot(forceDb: Boolean) {
            val done = completedCounter.get().coerceAtMost(playlist.segments.size)
            val downloaded = currentHlsDownloaded(service, task.id)
            val totalBytes = estimateHlsTotalBytes(
                service,
                currentHlsBytes(service, task.id),
                done,
                playlist.segments.size,
                task.id,
            )
            val speed = service.speedTrackers.getOrPut(task.id) { DownloadSpeedTracker() }.markProgress(downloaded)
            service.reportTrackedProgress(
                task = task,
                manager = manager,
                downloaded = downloaded,
                total = totalBytes,
                completedSegments = done,
                totalSegments = playlist.segments.size,
                speed = speed,
                forceDb = forceDb,
            )
        }
        kotlinx.coroutines.coroutineScope {
            val ticker = launch {
                while (isActive) {
                    kotlinx.coroutines.delay(DownloadService.LIVE_PROGRESS_INTERVAL_MS)
                    publishSnapshot(forceDb = false)
                }
            }
            try {
                playlist.initSegmentUrl?.let { initUrl ->
                    val initFile = service.hlsInitFile(hlsPackage)
                    if (!initFile.exists()) {
                        if (control.stopped) throw java.io.IOException(service.getString(com.pfpt.localvideoplayer.R.string.download_paused))
                        downloadHlsSegmentAtomic(
                            service = service,
                            taskId = task.id,
                            address = initUrl,
                            finalFile = initFile,
                            headers = task.headers,
                            control = control,
                            encryption = null,
                            playlistUrl = playlist.url,
                        )
                    }
                }
                val keys = java.util.concurrent.ConcurrentHashMap<String, ByteArray>()
                prefetchHlsKeys(service, playlist, task.headers, control, keys, playlist.url)
                val pendingSegments = playlist.segments.drop(completedSegments)
                val concurrency = DownloadManager.get(service).connectionsPerDownload()
                val nextWork = java.util.concurrent.atomic.AtomicInteger(0)
                val workers = List(concurrency.coerceAtLeast(1)) {
                    async {
                        while (true) {
                            if (control.stopped) throw java.io.IOException(service.getString(com.pfpt.localvideoplayer.R.string.download_paused))
                            val offset = nextWork.getAndIncrement()
                            if (offset >= pendingSegments.size) break
                            val segment = pendingSegments[offset]
                            val segmentIndex = completedSegments + offset
                            val segmentFile = service.hlsSegmentFile(hlsPackage, segmentFormat, segmentIndex)
                            if (service.resolveSegmentFile(hlsPackage, segmentFormat, segmentIndex).exists()) {
                                completedCounter.incrementAndGet()
                                continue
                            }
                            val encryption = segment.encryption
                            val keyIv = encryption?.let { enc ->
                                val key = keys[enc.keyUrl]
                                    ?: requestHlsBytes(service, enc.keyUrl, task.headers, control, playlist.url).also {
                                        if (it.size != DownloadService.AES_128_KEY_BYTES) {
                                            throw java.io.IOException(service.getString(com.pfpt.localvideoplayer.R.string.download_hls_key_invalid))
                                        }
                                        keys[enc.keyUrl] = it
                                    }
                                key to enc.iv
                            }
                            downloadHlsSegmentAtomic(
                                service = service,
                                taskId = task.id,
                                address = segment.url,
                                finalFile = segmentFile,
                                headers = task.headers,
                                control = control,
                                encryption = keyIv,
                                playlistUrl = playlist.url,
                            )
                            completedCounter.incrementAndGet()
                        }
                    }
                }
                workers.awaitAll()
            } finally {
                ticker.cancel()
                publishSnapshot(forceDb = true)
            }
        }
    }
}
```

`publishSnapshot(forceDb = true)` 留在 `finally` 里，和现在的 `downloadHls` 一样。

把下面这些函数原样搬进 `HlsDownloader`，从 Service 删除，并给每个函数增加第一个参数 `service: DownloadService`：

- `unwrapPlaylist`
- `unwrapResolved`
- `prefetchHlsKeys`
- `estimateHlsTotalBytes`
- `requestHlsText`
- `requestHlsBytes`
- `downloadHlsSegmentAtomic`
- `copyCounted`
- `resolveHlsSource`
- `currentHlsBytes`
- `currentHlsDownloaded`
- `addHlsBytes`
- `addHlsInflight`

替换规则：

- `getString(` 改成 `service.getString(`。
- `hlsByteCounters`、`hlsInflightBytes`、`hlsSizeHints`、`speedTrackers` 改成 `service` 上的同名字段。
- `hlsSegmentFile(`、`resolveSegmentFile(`、`hlsInitFile(`、`directorySize(` 改成 `service` 上的同名方法。
- `hlsFailureMessage(` 改成 `service.hlsFailureMessage(`。
- `executeHlsRequest(` 改成 `DownloadRequests.executeHlsRequest(`。
- `executeRequest(` 改成 `DownloadRequests.executeRequest(`。
- `HLS_SOURCE_PLAYLIST_NAME`、`AES_128_KEY_BYTES`、`SEGMENT_MAX_RETRIES`、`RETRY_BACKOFF_MS`、`IO_BUFFER_SIZE` 改成 `DownloadService` 的同名常量。
- 函数之间的调用传入同一个 `service`。
- 分片重试、AES key 长度、进度字节计数和 playlist 文件名不改。

`mergeAndPublishHls`、`resolveMergePlaylist`、`listHlsSegmentFiles`、`publishPart` 留在 Service。`HlsMerger.mergeToMp4` 这一步仍由 Service 调用。

`DownloadService.download` 里的 HLS 分支改成：

```kotlin
        if (isHls) {
            val hlsPackage = hlsDir(task.id)
            hlsPackage.mkdirs()
            HlsDownloader.download(this, task, hlsPackage, manager, control)
            if (control.stopped) throw IOException(getString(R.string.download_paused))
            mergeAndPublishHls(task.id, hlsPackage, manager, control)
            return
        }
```

- [ ] **Step 2: Verify segment download left the service**

Run: `rg "fun downloadHls|fun downloadHlsSegmentAtomic|fun resolveHlsSource" app/src/main/java/com/pfpt/localvideoplayer/download/DownloadService.kt`

Expected: 无匹配。

Run: `rg "HlsMerger.mergeToMp4" app/src/main/java/com/pfpt/localvideoplayer/download/DownloadService.kt`

Expected: 仍有一处匹配。合并还在 Service。

Run: `./gradlew :app:testFullDebugUnitTest`

Expected: BUILD SUCCESSFUL。

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/pfpt/localvideoplayer/download/HlsDownloader.kt app/src/main/java/com/pfpt/localvideoplayer/download/DownloadService.kt
git commit -m "$(cat <<'EOF'
Move HLS segment downloads out of DownloadService.

EOF
)"
```

---

### Task 4: 种子编排离开 Service

**Files:**
- Create: `app/src/main/java/com/pfpt/localvideoplayer/download/TorrentDownloader.kt`
- Modify: `app/src/main/java/com/pfpt/localvideoplayer/download/DownloadService.kt`
- Test: `./gradlew :app:testFullDebugUnitTest`

**Interfaces:**
- Consumes: `TorrentSession.downloadMagnet(taskId: String, magnetUri: String, saveDir: File, shouldStop: () -> Boolean, isCanceled: () -> Boolean, onName: suspend (String) -> Unit, onProgress: suspend (downloaded: Long, total: Long, speedBps: Long) -> Unit): File`。
- Produces: `internal object TorrentDownloader` 的同签名 `download`，返回 `File`。

- [ ] **Step 1: Forward the magnet call**

新建 `app/src/main/java/com/pfpt/localvideoplayer/download/TorrentDownloader.kt`：

```kotlin
package com.pfpt.localvideoplayer.download

import com.pfpt.localvideoplayer.download.torrent.TorrentSession
import java.io.File

internal object TorrentDownloader {
    suspend fun download(
        taskId: String,
        magnetUri: String,
        saveDir: File,
        shouldStop: () -> Boolean,
        isCanceled: () -> Boolean,
        onName: suspend (String) -> Unit,
        onProgress: suspend (downloaded: Long, total: Long, speedBps: Long) -> Unit,
    ): File = TorrentSession.downloadMagnet(
        taskId = taskId,
        magnetUri = magnetUri,
        saveDir = saveDir,
        shouldStop = shouldStop,
        isCanceled = isCanceled,
        onName = onName,
        onProgress = onProgress,
    )
}
```

`downloadTorrent` 里只替换 `TorrentSession.downloadMagnet` 这一次调用。`try/catch`、暂停文案、`ensureExtension`、`publishPart` 和删除种子目录保持原样：

```kotlin
        val videoFile = try {
            TorrentDownloader.download(
                taskId = task.id,
                magnetUri = task.url,
                saveDir = saveDir,
                shouldStop = { control.stopped },
                isCanceled = { control.canceled },
                onName = { name ->
                    val sanitized = DownloadNameResolver.sanitizeTitle(name)
                    if (sanitized.isBlank()) return@download
                    val currentName = manager.get(task.id)?.fileName.orEmpty()
                    val looksLikeHash = currentName.length in 32..64 && currentName.all { it.isLetterOrDigit() }
                    if (looksLikeHash || currentName.isBlank() || currentName == getString(R.string.download_default_file_name)) {
                        val withExt = if ('.' in sanitized.substringAfterLast('/')) sanitized else "$sanitized.mp4"
                        manager.update(task.id) { it.copy(fileName = withExt) }
                    }
                },
                onProgress = { downloaded, total, speedBps ->
                    reportTorrentProgress(task.id, manager, downloaded, total, speedBps)
                },
            )
        } catch (error: IOException) {
            if (control.stopped && !control.canceled) {
                throw IOException(getString(R.string.download_paused))
            }
            throw error
        }
```

lambda 标签从 `return@downloadMagnet` 改成 `return@download`。`onName` 和 `onProgress` 的函数体不改。删除 Service 里不再使用的 `TorrentSession` import。`onDestroy` 这一步仍调用 `TorrentSession.releaseForServiceDestroy()`。

- [ ] **Step 2: Verify torrent orchestration left the download call**

Run: `rg "TorrentSession.downloadMagnet" app/src/main/java/com/pfpt/localvideoplayer/download/DownloadService.kt`

Expected: 无匹配。

Run: `./gradlew :app:testFullDebugUnitTest`

Expected: BUILD SUCCESSFUL。

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/pfpt/localvideoplayer/download/TorrentDownloader.kt app/src/main/java/com/pfpt/localvideoplayer/download/DownloadService.kt
git commit -m "$(cat <<'EOF'
Move torrent orchestration out of DownloadService.

EOF
)"
```

---

### Task 5: full 保留引擎，lite 断开两个库

**Files:**
- Create: `app/src/full/java/com/pfpt/localvideoplayer/download/HlsMergeEngine.kt`
- Create: `app/src/full/java/com/pfpt/localvideoplayer/download/TorrentEngine.kt`
- Create: `app/src/lite/java/com/pfpt/localvideoplayer/download/HlsMergeEngine.kt`
- Create: `app/src/lite/java/com/pfpt/localvideoplayer/download/TorrentEngine.kt`
- Create: `app/src/testLite/java/com/pfpt/localvideoplayer/download/LiteDownloadEnginesTest.kt`
- Move: `HlsMerger.kt`、`TorrentSession.kt`、`HlsMergerTest.kt`、`HlsMergerErrorTest.kt`
- Modify: `HlsDownloader.kt`、`TorrentDownloader.kt`、`DownloadService.kt`、`app/build.gradle.kts`
- Test: `./gradlew :app:testFullDebugUnitTest`、`:app:testLiteDebugUnitTest`、`:app:compileLiteDebugKotlin`

**Interfaces:**
- Consumes: `HlsMerger.mergeToMp4(input: HlsMerger.MergeInput, outputFile: File)`，`TorrentSession.downloadMagnet`，`TorrentSession.releaseForServiceDestroy()`。
- Produces: 两个风味里同签名的 `internal object HlsMergeEngine` 和 `internal object TorrentEngine`。`HlsDownloader.merge(segmentFiles: List<File>, durationsSec: List<Double>, format: HlsSegmentFormat, initFile: File?, outputFile: File, mergeDispatcher: CoroutineDispatcher, onMerging: suspend () -> Unit)`。

- [ ] **Step 1: Write the lite failure test**

新建 `app/src/testLite/java/com/pfpt/localvideoplayer/download/LiteDownloadEnginesTest.kt`：

```kotlin
package com.pfpt.localvideoplayer.download

import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

class LiteDownloadEnginesTest {
    @Test
    fun mergeThrowsUnavailable() {
        val error = runCatching {
            HlsMergeEngine.merge(
                segmentFiles = emptyList(),
                durationsSec = emptyList(),
                format = HlsSegmentFormat.TS,
                initFile = null,
                outputFile = File("unused.mp4"),
            )
        }.exceptionOrNull()
        assertTrue(error is AdvancedDownloadUnavailable)
    }

    @Test
    fun torrentDownloadThrowsUnavailable() {
        val error = runBlocking {
            runCatching {
                TorrentEngine.downloadMagnet(
                    taskId = "task",
                    magnetUri = "magnet:?xt=urn:btih:0123456789abcdef0123456789abcdef01234567",
                    saveDir = File("unused"),
                    shouldStop = { false },
                    isCanceled = { false },
                    onName = {},
                    onProgress = { _, _, _ -> },
                )
            }.exceptionOrNull()
        }
        assertTrue(error is AdvancedDownloadUnavailable)
    }
}
```

- [ ] **Step 2: Run the lite test to verify it fails**

Run: `./gradlew :app:testLiteDebugUnitTest --tests com.pfpt.localvideoplayer.download.LiteDownloadEnginesTest`

Expected: FAIL，`HlsMergeEngine` 未定义。

- [ ] **Step 3: Move the libraries into the full source set**

```bash
mkdir -p app/src/full/java/com/pfpt/localvideoplayer/download/torrent
mkdir -p app/src/testFull/java/com/pfpt/localvideoplayer/download
git mv app/src/main/java/com/pfpt/localvideoplayer/download/HlsMerger.kt app/src/full/java/com/pfpt/localvideoplayer/download/HlsMerger.kt
git mv app/src/main/java/com/pfpt/localvideoplayer/download/torrent/TorrentSession.kt app/src/full/java/com/pfpt/localvideoplayer/download/torrent/TorrentSession.kt
git mv app/src/test/java/com/pfpt/localvideoplayer/download/HlsMergerTest.kt app/src/testFull/java/com/pfpt/localvideoplayer/download/HlsMergerTest.kt
git mv app/src/test/java/com/pfpt/localvideoplayer/download/HlsMergerErrorTest.kt app/src/testFull/java/com/pfpt/localvideoplayer/download/HlsMergerErrorTest.kt
```

移动后的包名保持 `com.pfpt.localvideoplayer.download` 和 `com.pfpt.localvideoplayer.download.torrent`。不改 `HlsMerger` 和 `TorrentSession` 的函数体。不改两个测试的断言。`VideoDurationResolverTest` 留在 `src/test`；它检查的是文件字节标记 `FFmpeg.Service01`，不引用 FFmpegKit。

`app/src/full/java/com/pfpt/localvideoplayer/download/HlsMergeEngine.kt`：

```kotlin
package com.pfpt.localvideoplayer.download

import java.io.File

internal object HlsMergeEngine {
    fun merge(
        segmentFiles: List<File>,
        durationsSec: List<Double>,
        format: HlsSegmentFormat,
        initFile: File?,
        outputFile: File,
    ) {
        HlsMerger.mergeToMp4(
            HlsMerger.MergeInput(
                segmentFiles = segmentFiles,
                durationsSec = durationsSec,
                format = format,
                initFile = initFile,
            ),
            outputFile,
        )
    }
}
```

`app/src/full/java/com/pfpt/localvideoplayer/download/TorrentEngine.kt`：

```kotlin
package com.pfpt.localvideoplayer.download

import com.pfpt.localvideoplayer.download.torrent.TorrentSession
import java.io.File

internal object TorrentEngine {
    suspend fun downloadMagnet(
        taskId: String,
        magnetUri: String,
        saveDir: File,
        shouldStop: () -> Boolean,
        isCanceled: () -> Boolean,
        onName: suspend (String) -> Unit,
        onProgress: suspend (downloaded: Long, total: Long, speedBps: Long) -> Unit,
    ): File = TorrentSession.downloadMagnet(
        taskId = taskId,
        magnetUri = magnetUri,
        saveDir = saveDir,
        shouldStop = shouldStop,
        isCanceled = isCanceled,
        onName = onName,
        onProgress = onProgress,
    )

    fun releaseForServiceDestroy() {
        TorrentSession.releaseForServiceDestroy()
    }
}
```

`app/src/lite/java/com/pfpt/localvideoplayer/download/HlsMergeEngine.kt`：

```kotlin
package com.pfpt.localvideoplayer.download

import java.io.File

internal object HlsMergeEngine {
    fun merge(
        segmentFiles: List<File>,
        durationsSec: List<Double>,
        format: HlsSegmentFormat,
        initFile: File?,
        outputFile: File,
    ): Unit = throw AdvancedDownloadUnavailable()
}
```

`app/src/lite/java/com/pfpt/localvideoplayer/download/TorrentEngine.kt`：

```kotlin
package com.pfpt.localvideoplayer.download

import java.io.File

internal object TorrentEngine {
    suspend fun downloadMagnet(
        taskId: String,
        magnetUri: String,
        saveDir: File,
        shouldStop: () -> Boolean,
        isCanceled: () -> Boolean,
        onName: suspend (String) -> Unit,
        onProgress: suspend (downloaded: Long, total: Long, speedBps: Long) -> Unit,
    ): File = throw AdvancedDownloadUnavailable()

    fun releaseForServiceDestroy() = Unit
}
```

lite 的 `merge` 和 `downloadMagnet` 不读取参数。参数保留是为了和 full 的签名一致。

- [ ] **Step 4: Point the downloaders at the engines**

在 `HlsDownloader` 增加：

```kotlin
    suspend fun merge(
        segmentFiles: List<File>,
        durationsSec: List<Double>,
        format: HlsSegmentFormat,
        initFile: File?,
        outputFile: File,
        mergeDispatcher: kotlinx.coroutines.CoroutineDispatcher,
        onMerging: suspend () -> Unit,
    ) {
        onMerging()
        kotlinx.coroutines.withContext(mergeDispatcher) {
            HlsMergeEngine.merge(
                segmentFiles = segmentFiles,
                durationsSec = durationsSec,
                format = format,
                initFile = initFile,
                outputFile = outputFile,
            )
        }
    }
```

`merge` 不写 `catch`。

`mergeAndPublishHls` 里，分片为空和数量不一致的两个 `throw` 留在 `try` 外面。删除 `try` 之前那一次 `manager.update(... isMerging = true ...)` 和 `updateNotification(... merging = true ...)`。`try` 的合并调用换成：

```kotlin
        try {
            HlsDownloader.merge(
                segmentFiles = segmentFiles,
                durationsSec = playlist.segments.map { it.durationSec },
                format = segmentFormat,
                initFile = hlsInitFile(hlsPackage).takeIf { it.exists() },
                outputFile = mergedTemp,
                mergeDispatcher = mergeDispatcher,
                onMerging = {
                    manager.update(taskId) { it.copy(isMerging = true, downloadSpeedBps = 0) }
                    updateNotification(
                        task = task.copy(isMerging = true, downloadSpeedBps = 0),
                        taskId = task.id,
                        merging = true,
                        force = true,
                    )
                },
            )
            mergedPart.delete()
            if (!mergedTemp.renameTo(mergedPart)) {
                mergedTemp.copyTo(mergedPart, overwrite = true)
                mergedTemp.delete()
            }
        } catch (error: AdvancedDownloadUnavailable) {
            mergedTemp.delete()
            mergedPart.delete()
            throw error
        } catch (error: Exception) {
            mergedTemp.delete()
            mergedPart.delete()
            val message = when {
                error.message == "SEGMENTS_CORRUPT" || error.message?.startsWith("SEGMENTS_CORRUPT:") == true ->
                    getString(R.string.download_hls_segments_corrupt)
                else -> {
                    val detail = error.message?.takeIf { it.isNotBlank() }
                    if (detail != null) {
                        "${getString(R.string.download_hls_merge_failed)}: $detail"
                    } else {
                        getString(R.string.download_hls_merge_failed)
                    }
                }
            }
            throw IOException(message, error)
        }
```

`try` 之后的暂停检查、删除分片目录、更新任务和 `publishPart` 不改。

`TorrentDownloader.download` 的返回改成调用 `TorrentEngine.downloadMagnet`，参数原样传递。删除 `TorrentSession` import。

`DownloadService.onDestroy` 改成：

```kotlin
    override fun onDestroy() {
        TorrentEngine.releaseForServiceDestroy()
        scope.cancel()
        super.onDestroy()
    }
```

删除 Service 里的 `TorrentSession` import。`processTask` 不改。

- [ ] **Step 5: Move the dependencies to full**

`app/build.gradle.kts` 的 `productFlavors` 改成：

```kotlin
    productFlavors {
        create("full") {
            dimension = "features"
            isDefault = true
        }
        create("lite") {
            dimension = "features"
        }
    }
```

`buildFeatures` 改成：

```kotlin
    buildFeatures {
        compose = true
        buildConfig = false
    }
```

依赖里删除这五行 `implementation`：

```kotlin
    implementation(libs.ffmpeg.kit.min)
    implementation(libs.libtorrent4j)
    implementation(libs.libtorrent4j.android.arm)
    implementation(libs.libtorrent4j.android.arm64)
    implementation(libs.libtorrent4j.android.x64)
```

在 `dependencies` 末尾增加：

```kotlin
    fullImplementation(libs.ffmpeg.kit.min)
    fullImplementation(libs.libtorrent4j)
    fullImplementation(libs.libtorrent4j.android.arm)
    fullImplementation(libs.libtorrent4j.android.arm64)
    fullImplementation(libs.libtorrent4j.android.x64)
```

`libs.smart.exception.java` 保持 `implementation`。`proguard-rules.pro` 不改。

- [ ] **Step 6: Verify both flavors**

Run: `rg "HlsMerger|TorrentSession|ffmpegkit|libtorrent4j" app/src/main`

Expected: 无匹配。

Run: `rg "HlsMerger|TorrentSession|ffmpegkit|libtorrent" app/src/test/java`

Expected: 无匹配。

Run: `./gradlew :app:dependencies --configuration liteDebugCompileClasspath`

Expected: 输出里没有 `ffmpeg`，也没有 `libtorrent`。

Run: `./gradlew :app:dependencies --configuration fullDebugCompileClasspath`

Expected: 输出里同时有 `ffmpeg` 和 `libtorrent4j`。

Run: `./gradlew :app:testLiteDebugUnitTest --tests com.pfpt.localvideoplayer.download.LiteDownloadEnginesTest`

Expected: PASS。

Run: `./gradlew :app:testFullDebugUnitTest`

Expected: BUILD SUCCESSFUL。`HlsMergerTest` 和 `HlsMergerErrorTest` 在 full 里通过。

Run: `./gradlew :app:testLiteDebugUnitTest`

Expected: BUILD SUCCESSFUL。

Run: `./gradlew :app:compileLiteDebugKotlin`

Expected: BUILD SUCCESSFUL。

- [ ] **Step 7: Commit**

```bash
git add app/build.gradle.kts app/src/main/java/com/pfpt/localvideoplayer/download app/src/full app/src/lite app/src/test/java/com/pfpt/localvideoplayer/download app/src/testFull app/src/testLite
git commit -m "$(cat <<'EOF'
Keep FFmpeg and libtorrent in the full download flavor.

EOF
)"
```

提交前用 `git status` 确认规格、本计划和 `.superpowers/` 不在暂存区。
