# LumenPlay

一款基于 Jetpack Compose 与 Media3 的 Android 本地视频播放器，自动整理设备中的视频库，提供流畅的播放体验与高度可定制的界面。

## 功能特性

### 视频库

- 扫描设备本地视频，支持下拉刷新与手动选择文件导入
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
| 播放 | Media3 ExoPlayer、HLS |
| 存储 | DataStore Preferences |
| 最低 SDK | Android 9（API 28） |
| 目标 SDK | Android 15（API 35） |

## 环境要求

- Android Studio（推荐 Ladybug 或更高版本）
- JDK 17
- Android SDK 35

## 构建与安装

```bash
# 克隆仓库
git clone <your-repo-url>
cd LocalVideoPlayer

# 调试版构建
./gradlew assembleDebug

# APK 输出路径
# app/build/outputs/apk/debug/app-debug.apk
```

在 Android Studio 中打开项目后，直接 Run 即可安装到设备或模拟器。

## 权限说明

| 权限 | 用途 |
|------|------|
| `READ_MEDIA_VIDEO`（Android 13+） | 扫描设备视频 |
| `READ_EXTERNAL_STORAGE`（Android 12 及以下） | 扫描设备视频 |

首次启动会请求存储权限；也可通过右下角按钮手动选取视频文件，无需全盘扫描。

## 项目结构

```
app/src/main/java/com/zhaoshuo/localvideoplayer/
├── data/           # DataStore、视频仓库、播放记录
├── model/          # 数据模型、排序与筛选逻辑
├── player/         # 播放列表状态
├── pip/            # 画中画控制
├── ui/             # Compose 界面
│   └── components/ # 可复用 UI 组件
└── util/           # 分组、权限、删除等工具
```

## 截图

> 待补充

## 开发说明

- 应用包名：`com.zhaoshuo.localvideoplayer`
- 显示名称：**LumenPlay**
- 库缓存采用两阶段加载：先展示单集列表，后台完成智能分组以加快启动速度

## License

暂未指定开源协议。如需二次分发，请联系作者。
