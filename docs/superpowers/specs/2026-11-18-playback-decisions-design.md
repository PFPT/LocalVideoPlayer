# 播放决定收口

## 目标

播放行为保持现状。把时长选择、手势 seek 是否执行、倍速进出、解码失败后是否改软解，从界面挪到可单测的纯函数。界面提交快照，执行返回的动作。

`PlaybackSession` 继续负责播放器的打开、复用、续播、睡眠定时、字幕和进度保存。

## 位置

新文件 `app/src/main/java/com/pfpt/localvideoplayer/player/PlaybackDecisions.kt`。

它不引用 ExoPlayer，也不引用 Compose。现有的 `SeekTarget` 继续负责位移计算，`isSoftwareDecodeRetryable` 和 `isSourceReadFailure` 继续负责错误码分类。

## 时长

```kotlin
fun resolvedDurationMs(liveDurationMs: Long, catalogDurationMs: Long): Long

fun displayDurationMs(isCasting: Boolean, castDurationMs: Long, localDurationMs: Long): Long

fun shouldBackfillDuration(catalogDurationMs: Long, liveDurationMs: Long): Boolean

fun shouldPublishKnownDuration(displayDurationMs: Long, catalogDurationMs: Long): Boolean
```

`resolvedDurationMs`：`liveDurationMs > 0` 时返回 live，否则返回 `catalogDurationMs.coerceAtLeast(0)`。

`displayDurationMs`：正在投屏且 `castDurationMs > 0` 时返回投屏时长，否则返回 `localDurationMs`。

`shouldBackfillDuration`：库里的时长和 live 时长都 `<= 0` 时为真。界面这时才去读元数据。

`shouldPublishKnownDuration`：屏幕上的时长 `> 0` 且库里的时长 `<= 0` 时为真。界面这时才调用 `onDurationKnown`。

读元数据返回后，界面再看 `progress.durationMs`。它已经 `> 0` 时不覆盖，也不再调用 `onDurationKnown`。读到的时长 `<= 0` 时同样不写。

投屏且 `displayDurationMs > 0` 时，界面仍把该值写入 `progress.durationMs`。

## Seek

```kotlin
sealed interface SeekCommit {
    data class Apply(val positionMs: Long) : SeekCommit
    data object Reject : SeekCommit
}

fun seekCommit(
    targetMs: Long,
    playerDurationMs: Long,
    resolvedDurationMs: Long,
    currentPositionMs: Long,
    seekable: Boolean,
): SeekCommit
```

有效时长先取 `playerDurationMs`（只有 `> 0` 才算），否则用 `resolvedDurationMs`。有效时长 `<= 0`、`seekable == false`、且 `targetMs <= currentPositionMs` 时返回 `Reject`。其余返回 `Apply(targetMs)`。

这道门只用于手势松手和相对快进。相对快进先调用现有的 `seekTargetMs`，再把结果交给 `seekCommit`。`Reject` 时界面不调用 `seekTo`，不改 `progress.currentPositionMs` 和 `previewPositionMs`；相对快进还同时不显示双击提示。`Apply` 时界面 `seekTo(positionMs)`，并写下进度和预览位置。

进度条拖动继续直接 `seekTo`。投屏中的 seek 继续交给 `CastManager`。这两条路径不调用 `seekCommit`。

## 倍速

```kotlin
data class SpeedSnapshot(
    val playbackSpeed: Float,
    val savedPlaybackSpeed: Float,
    val boosted: Boolean,
)

sealed interface SpeedChange {
    data class Set(
        val playbackSpeed: Float,
        val savedPlaybackSpeed: Float,
        val boosted: Boolean,
        val playIfPaused: Boolean,
    ) : SpeedChange
    data object Unchanged : SpeedChange
}

fun speedAfterDefault(snapshot: SpeedSnapshot, defaultSpeed: Float): SpeedSnapshot

fun speedBoostStart(snapshot: SpeedSnapshot, isPlaying: Boolean): SpeedChange

fun speedBoostEnd(snapshot: SpeedSnapshot): SpeedSnapshot

fun speedToRestoreOnLeave(snapshot: SpeedSnapshot): Float?
```

长按加速的下限是常量 `MIN_SPEED_BOOST = 2.0f`，放在 `PlaybackDecisions.kt`。

`speedAfterDefault`：`boosted` 为真时原样返回。否则 `playbackSpeed` 和 `savedPlaybackSpeed` 都改为 `defaultSpeed`。

`speedBoostStart`：已经 `boosted` 时返回 `Unchanged`。否则 `savedPlaybackSpeed` 取当前 `playbackSpeed`，新的播放速度是 `max(2.0f, savedPlaybackSpeed)`，`boosted` 为真，`playIfPaused` 等于 `!isPlaying`。

`speedBoostEnd`：未加速时原样返回。否则 `playbackSpeed` 回到 `savedPlaybackSpeed`，`boosted` 为假。

`speedToRestoreOnLeave`：正在加速时返回 `savedPlaybackSpeed`，否则返回 `null`。

`Set` 时界面把返回的数字写进 `PlayerUiState`。`playIfPaused` 为真时调用 `play()`。`Unchanged` 时不改状态，也不调用 `play()`。`LaunchedEffect(playbackSpeed)` 继续把 `playbackSpeed` 写成 `PlaybackParameters`。同一个默认速度效果里，画面比例和单集循环仍直接写入界面状态。离开界面时，返回值非空才先 `setPlaybackSpeed`，然后再用 `speedBoostEnd` 清界面上的加速状态。

## 软解恢复

```kotlin
enum class DecoderRecovery {
    RetrySoft,
    Ignore,
    FailRead,
    FailDecode,
}

fun decoderRecovery(
    retryable: Boolean,
    sourceRead: Boolean,
    appliedSoftDecode: Boolean,
    alreadyRetried: Boolean,
    retryPending: Boolean,
): DecoderRecovery
```

判断顺序：

1. `retryable && !appliedSoftDecode && !alreadyRetried` 返回 `RetrySoft`。
2. 否则 `retryPending` 返回 `Ignore`。
3. 否则 `sourceRead` 返回 `FailRead`，其余返回 `FailDecode`。

`retryable` 和 `sourceRead` 由现有的错误码函数算出。

`RetrySoft`：界面记下当前位置，清掉 `playbackFailure`，标记已重试和正在等待重建，从 `resumedIndices` 移除当前下标，用 `runCatching` 保存进度，然后把 `appliedSoftDecode` 设为真并增加 `softDecodeGeneration`。保存失败也继续重建。现有的 `produceState` 继续负责创建播放器。

`Ignore`：不改失败状态，不重建。

`FailRead` / `FailDecode`：只把 `playbackFailure` 设为对应值。

播放器进入 `STATE_READY` 时，现有监听清掉 `playbackFailure` 和 `softDecodeRetryPending`。

## 调用点

| 现有位置 | 改为 |
| --- | --- |
| `PlayerScreen.resolvedDurationMs`、投屏时长选择 | `resolvedDurationMs`、`displayDurationMs` |
| `PlayerSessionEffects` 的时长回填和回写 | `shouldBackfillDuration`、`shouldPublishKnownDuration` |
| `PlayerScreen.commitSeek`、`seekBy` | `seekTargetMs` 之后调用 `seekCommit` |
| `PlayerScreen` 里默认倍速的 `LaunchedEffect` | `speedAfterDefault` |
| `PlayerChrome` 的长按开始和结束 | `speedBoostStart`、`speedBoostEnd` |
| `PlayerSessionEffects` 离开界面时的速度恢复 | `speedToRestoreOnLeave`，再 `speedBoostEnd` |
| `PlayerSessionEffects.handlePlayerError` | `decoderRecovery` |

`PlayerChrome.seekToPosition` 保持现状：投屏走 `CastManager`，本地直接 `seekTo`。

## 这一轮不动

- `PlaybackSession` 的打开、复用、续播、睡眠定时、字幕、进度保存
- 亮度、音量、全屏、画中画、选集、片头片尾、缩放、面板
- `SeekTarget` 的计算公式
- TextureView / SurfaceView，继续只跟 `useTextureView` 设置走
- 进度条拖动和投屏 seek 不进入 `seekCommit`

上表之外的界面逻辑保持原样。`PlayerScreen` 不在这一轮拆分。

## 测试

新文件 `app/src/test/java/com/pfpt/localvideoplayer/player/PlaybackDecisionsTest.kt`。JVM 单测，不使用 Robolectric。

时长：

- live 大于 0 时用 live
- live 不大于 0 时用库里的非负时长
- 两者都不大于 0 时结果为 0
- 正在投屏且投屏时长大于 0 时用投屏时长
- 未投屏，或投屏时长不大于 0 时用本地时长
- 只有库里和 live 都没有时长才回填
- 只有屏幕时长大于 0 且库里没有时长才回写

Seek：

- 播放器时长大于 0 时一律 `Apply`，包括目标小于当前位置
- 播放器时长不大于 0、resolved 大于 0 时一律 `Apply`
- 有效时长不大于 0 且可 seek 时一律 `Apply`
- 有效时长不大于 0、不可 seek、目标大于当前位置时 `Apply`
- 有效时长不大于 0、不可 seek、目标等于或小于当前位置时 `Reject`

倍速：

- 未加速时，默认速度同时写入播放速度和保存速度
- 已加速时，默认速度不改变快照
- 从 1 倍长按得到播放速度 2、保存速度 1、`boosted` 为真；未播放时 `playIfPaused` 为真，正在播放时为假
- 从 3 倍长按得到播放速度 3、保存速度 3
- 已加速时再长按返回 `Unchanged`
- 松手后播放速度回到保存速度，`boosted` 为假
- 未加速时松手返回原快照
- 离开界面时，只有正在加速才返回保存速度

软解：

- 可重试、尚未软解、尚未重试，即使 `retryPending` 为真，也返回 `RetrySoft`
- 可重试但已经软解或已经重试，且正在等待时返回 `Ignore`
- 可重试但已经软解或已经重试，且不在等待时，按 `sourceRead` 返回 `FailRead` 或 `FailDecode`
- 不可重试且正在等待时返回 `Ignore`
- 不可重试、不在等待、读源失败时返回 `FailRead`
- 不可重试、不在等待、其他错误时返回 `FailDecode`

`SeekTargetTest` 和错误码分类测试留在原文件。

## 完成标准

上述调用点不再自己写这四类分支。新单测覆盖上列分支。手势 seek、进度条、投屏 seek、长按倍速、解码失败后的软解重建，对外表现与改动前一致。
