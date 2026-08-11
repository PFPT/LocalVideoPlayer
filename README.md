# LumenPlay

一款基于 Jetpack Compose 与 Media3 的 Android 本地视频播放器，自动整理设备中的视频库，提供流畅的播放体验与高度可定制的界面。

## 功能特性

### 视频库

- 扫描设备本地视频，支持下拉刷新与手动选择文件导入
- MediaStore 变更监听与分组结果缓存
- 智能分组：按剧集名、文件夹、相似标题、存储桶、短视频月份等自动归类
- 类型 Tab：全部 / 剧集 / 文件夹 / 短视频 / 单集 / 标记
- 分区视图与平铺视图切换
- 排序：时间、名称、时长、热度（按点击次数）
- 筛选：时长（含 20 / 25 分钟以上）、添加日期
- 标记收藏，标记内容单独分区展示
- 最近播放记录与续播进度
- 多选删除：从列表隐藏或从设备永久删除

### 播放器

- 基于 ExoPlayer（Media3），支持 HLS（`.m3u8`）
- 横屏 / 竖屏、全屏、画面比例调节
- 手势调节亮度与音量
- 倍速播放、长按 2x 加速
- 上一集 / 下一集、播放列表
- 画中画（PiP）悬浮窗播放
- 断点续播，自动保存播放进度
- Chromecast 投屏（本地文件经手机 HTTP 侧载）

### 下载

- 链接分享下载（HLS / 渐进式 / 种子）
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
| 播放 | Media3 ExoPlayer、HLS、Cast |
| 存储 | DataStore Preferences、Room |
| 后台 | WorkManager、Foreground Service |
| 最低 SDK | Android 9（API 28） |
| 目标 SDK | Android 15（API 35） |

## 环境要求

- Android Studio（推荐 Ladybug 或更高版本）
- JDK 17
- Android SDK 35

## 构建与安装

```bash
git clone <your-repo-url>
cd LocalVideoPlayer

./gradlew assembleFullDebug

# Release（开启 R8）
./gradlew assembleFullRelease

# APK 输出路径示例
# app/build/outputs/apk/full/debug/app-full-debug.apk
```

产品风味：

- `full`（默认）：完整下载能力（FFmpeg / libtorrent）
- `lite`：同源码构建，`BuildConfig.HAS_ADVANCED_DOWNLOAD=false`，便于后续裁剪重依赖

## 权限说明

| 权限 | 用途 |
|------|------|
| `READ_MEDIA_VIDEO`（Android 13+） | 扫描设备视频 |
| `READ_EXTERNAL_STORAGE`（Android 12 及以下） | 扫描设备视频 |
| `NEARBY_WIFI_DEVICES`（Android 13+） | Cast 设备发现 |
| `ACCESS_FINE_LOCATION`（Android 12 及以下） | Cast 设备发现 |
| `FOREGROUND_SERVICE*` / `WAKE_LOCK` | 下载与投屏保活 |

首次启动会请求存储权限；也可通过右下角按钮手动选取视频文件，无需全盘扫描。

## 项目结构

```
app/src/main/java/com/zhaoshuo/localvideoplayer/
├── cast/           # Chromecast、本地 HTTP 侧载、保活服务
├── data/           # Repository、DataStore、LibraryViewModel、Room 相关仓库
├── download/       # 下载管理、HLS/种子、前台服务
├── model/          # 数据模型、排序与筛选逻辑
├── player/         # 播放列表、ExoPlayer 构建、PlayerViewModel
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
