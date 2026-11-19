# 下载引擎拆分

## 目标

`full` 的下载行为保持现状。`DownloadService` 不再自己实现渐进式、HLS 分片和种子下载。`lite` 能编译，并且不链接 FFmpeg 和 libtorrent。

## 调度

`DownloadService` 继续负责队列、并发槽、暂停和取消、通知、进度写入、失败状态，以及把完成文件登记进媒体库。

`download` 仍按现在的顺序分发：

1. 已有合并结果时直接入库。
2. 磁力链接交给 `TorrentDownloader`。
3. HLS 交给 `HlsDownloader`。
4. 其余交给 `ProgressiveDownloader`。

三个下载器通过函数参数接收进度回调，不新增监听接口。Service 在回调里写 `DownloadManager` 和通知。下载器不持有通知。

`HlsDownloader` 下完分片后，先调用 `onMerging`，再在 Service 传入的 `mergeDispatcher` 上调用 `HlsMergeEngine.merge`。它不捕获 `AdvancedDownloadUnavailable`。Service 接着按现在的顺序处理合并结果：替换 `.part`，清掉分片目录，然后 `publishPart`。合并失败时，Service 先原样抛出 `AdvancedDownloadUnavailable`，再按现有规则包装其他异常。

种子下载返回视频文件后，Service 继续补扩展名、`publishPart`，并删除种子目录。暂停且未取消时，仍抛出现有的暂停文案。

## 公共代码

新文件放在 `app/src/main/java/com/pfpt/localvideoplayer/download/`：

- `ProgressiveDownloader`
- `HlsDownloader`
- `TorrentDownloader`
- `AdvancedDownloadUnavailable`

`AdvancedDownloadUnavailable` 继承 `IOException`，不带消息。

`HlsDownloader` 负责分片下载，并在分片完成后调用 `HlsMergeEngine.merge`。它不包含合并策略，也不做重命名和入库。

`TorrentDownloader` 调用 `TorrentEngine.downloadMagnet`。Service 销毁时调用 `TorrentEngine.releaseForServiceDestroy`。

## 风味实现

`HlsMergeEngine` 和 `TorrentEngine` 只出现在风味源集里，两边的包名和类型名相同。公共代码直接调用它们。

`full`：

- `app/src/full/java/com/pfpt/localvideoplayer/download/HlsMergeEngine.kt`
- `app/src/full/java/com/pfpt/localvideoplayer/download/HlsMerger.kt`
- `app/src/full/java/com/pfpt/localvideoplayer/download/TorrentEngine.kt`
- `app/src/full/java/com/pfpt/localvideoplayer/download/torrent/TorrentSession.kt`

`HlsMerger.kt` 从 `src/main` 原样移到 `src/full`。策略顺序、命令和二进制拼接后的 FFmpeg 调用保持不变。

`HlsMergeEngine.merge` 的参数是分片文件、时长、`HlsSegmentFormat`、可选的 init 文件和输出文件。它把这些参数装进现有的 `HlsMerger.MergeInput`，再调用 `HlsMerger.mergeToMp4`。

`TorrentSession.kt` 从 `src/main` 原样移到 `src/full`。`TorrentEngine` 把 `downloadMagnet` 和 `releaseForServiceDestroy` 原样转给它。

`lite`：

- `app/src/lite/java/com/pfpt/localvideoplayer/download/HlsMergeEngine.kt`
- `app/src/lite/java/com/pfpt/localvideoplayer/download/TorrentEngine.kt`

`HlsMergeEngine.merge` 和 `TorrentEngine.downloadMagnet` 抛出 `AdvancedDownloadUnavailable`。`TorrentEngine.releaseForServiceDestroy` 不做事。

## 依赖

`app/build.gradle.kts` 里，下面这些依赖从公共 `implementation` 改成 `fullImplementation`：

- `libs.ffmpeg.kit.min`
- `libs.libtorrent4j`
- `libs.libtorrent4j.android.arm`
- `libs.libtorrent4j.android.arm64`
- `libs.libtorrent4j.android.x64`

删除两个风味上的 `HAS_ADVANCED_DOWNLOAD`。没有任何代码读取 `BuildConfig`，同时把 `buildConfig` 设为 `false`。

## 失败

`processTask` 对暂停、取消和其他异常的处理保持现状。

`failureMessage` 在现有分支之前识别 `AdvancedDownloadUnavailable`，返回新文案 `download_requires_full`：「当前安装包不包含这项下载」。

HLS 合并周围的 `catch` 先原样抛出 `AdvancedDownloadUnavailable`。其他异常仍用现在的文案包装，包括 `SEGMENTS_CORRUPT` 和「HLS 分片合并失败」前缀。

种子下载周围的 `catch` 保持现状：已暂停且未取消时抛出暂停文案，否则原样抛出。因此未暂停时，`AdvancedDownloadUnavailable` 能到达 `failureMessage`。

## 测试

`HlsMergerTest` 和 `HlsMergerErrorTest` 移到 `app/src/testFull/java/`。它们继续覆盖播放列表、concat 列表和错误摘要。

`app/src/testLite/java/` 新增一个测试：`HlsMergeEngine.merge` 和 `TorrentEngine.downloadMagnet` 都抛出 `AdvancedDownloadUnavailable`。

`app/src/test/java/` 里的测试不再引用 `HlsMerger`、`TorrentSession`、FFmpeg 或 libtorrent。

验证命令：

- `./gradlew :app:testFullDebugUnitTest`
- `./gradlew :app:testLiteDebugUnitTest`
- `./gradlew :app:compileLiteDebugKotlin`

## 这一轮不动

- 分片下载算法、断点续传和多连接渐进式下载
- 种子会话里的 tracker、优先级和速度逻辑
- 通知文案、并发槽和媒体库登记
- 下载管理界面
- 播放决定
