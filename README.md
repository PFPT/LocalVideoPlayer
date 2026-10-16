# LumenPlay

<p>
  <img src="docs/icon.png" width="96" alt="LumenPlay">
</p>

一款基于 Jetpack Compose 与 Media3 的 Android 本地视频播放器。自动整理设备中的视频，并支持下载、投屏和可定制界面。

## 安装

需要 Android 9（API 28）及以上。

从 [最新 Release](https://github.com/PFPT/LocalVideoPlayer/releases/latest) 下载 `LumenPlay-1.0.apk` 后安装。对外发布的是带完整下载能力的 `full` 包。

## 功能特性

### 视频库

- 扫描本地视频，支持下拉刷新与手动选择文件导入
- 搜索标题或剧名
- MediaStore 变更监听与分组结果缓存
- 智能分组：按剧集名、文件夹、相似标题、存储桶、短视频月份自动归类
- 类型 Tab：全部 / 剧集 / 文件夹 / 短视频 / 单集 / 标记
- 分区视图与平铺视图切换
- 排序：时间、名称、时长、热度（按点击次数）
- 筛选：时长（含 20 / 25 分钟以上）、添加日期
- 标记收藏，标记内容单独分区展示
- 最近播放记录与续播进度
- 多选删除：从列表隐藏或从设备永久删除；已隐藏的视频可在设置中恢复
- 文件夹源，以及访问所有文件以便扫描迅雷等下载目录

### 播放器

- 基于 ExoPlayer（Media3），支持 HLS（`.m3u8`）
- 横屏 / 竖屏、全屏、画面比例调节
- 手势调节亮度、音量、进度，双击快进快退，捏合缩放（均可单独关闭）
- 倍速播放、长按 2x 加速；可设置默认倍速、画面比例和单集循环
- 上一集 / 下一集、播放列表
- 书签、A-B 循环
- 片头片尾跳过，支持全局时长和按剧单独保存
- 画中画（PiP）悬浮窗播放
- 离开界面后通过媒体会话继续后台播放
- 断点续播，自动保存播放进度
- Chromecast 投屏（本地文件经手机 HTTP 侧载）

### 下载

- 链接分享下载（HLS / 渐进式 / 种子）
- 从剪贴板填入链接；分享文本为 `标题|链接` 时，标题直接作为文件名
- 本机代理链接保持走代理下载，下载期间需让来源应用留在后台
- 队列控制：暂停 / 继续、清除已完成或失败、重命名、复制链接
- 可设置同时下载数和单任务连接数
- 前台下载服务、Room 任务持久化、WorkManager 恢复
- FFmpeg 合并 HLS；libtorrent 处理种子（`full` 风味）

### 界面与主题

- Material 3 + 自定义 Lumen 主题
- 可自定义背景色、文字色及透明度
- 列表、播放器统一视觉风格

## 技术栈

| 类别 | 技术 |
|------|------|
| 语言 | Kotlin |
| UI | Jetpack Compose、Material 3 |
| 导航 | Navigation Compose |
| 播放 | Media3 ExoPlayer、HLS、Cast、MediaSession |
| 存储 | DataStore Preferences、Room |
| 后台 | WorkManager、Foreground Service |
| 最低 SDK | Android 9（API 28） |
| 目标 SDK | Android 15（API 35） |

## 环境要求

- Android Studio（推荐 Ladybug 或更高版本）
- JDK 17
- Android SDK 35

## 构建

```bash
git clone https://github.com/PFPT/LocalVideoPlayer.git
cd LocalVideoPlayer

./gradlew assembleFullDebug

# Release（开启 R8）。存在 keystore.properties 时会签名
./gradlew assembleFullRelease
```

产品风味：

- `full`（默认，GitHub Release 使用这一版）：完整下载能力（FFmpeg / libtorrent）
- `lite`：同源码构建，`BuildConfig.HAS_ADVANCED_DOWNLOAD=false`，便于后续裁剪重依赖

正式包签名读取仓库根目录的 `keystore.properties`（已忽略，不提交）。没有该文件时仍可编译，但 Release 包未签名。

## 权限说明

| 权限 | 用途 |
|------|------|
| `READ_MEDIA_VIDEO`（Android 13+） | 扫描设备视频 |
| `READ_EXTERNAL_STORAGE`（Android 12 及以下） | 扫描设备视频 |
| `NEARBY_WIFI_DEVICES`（Android 13+） | Cast 设备发现 |
| `ACCESS_FINE_LOCATION`（Android 12 及以下） | Cast 设备发现 |
| `FOREGROUND_SERVICE*` / `WAKE_LOCK` | 下载、后台播放与投屏保活 |

首次启动会请求存储权限；也可通过右下角按钮手动选取视频文件，无需全盘扫描。

## 项目结构

```
app/src/main/java/com/zhaoshuo/localvideoplayer/
├── cast/           # Chromecast、本地 HTTP 侧载、保活服务
├── data/           # Repository、DataStore、LibraryViewModel、Room 相关仓库
├── download/       # 下载管理、HLS/种子、前台服务
├── model/          # 数据模型、排序与筛选逻辑
├── player/         # 播放列表、ExoPlayer 构建、PlayerViewModel、后台播放
├── pip/            # 画中画控制
├── ui/             # Compose 界面
│   └── components/ # 可复用 UI 组件（含 Cast）
└── util/           # 分组、缩略图、权限、删除等工具
```

## 开发说明

- 应用包名：`com.zhaoshuo.localvideoplayer`
- 显示名称：**LumenPlay**
- 库缓存：视频列表 + 已分组结果；启动先读缓存，再后台刷新
- Release 默认开启 R8 / shrinkResources

## License

暂未指定开源协议。如需二次分发，请联系作者。
