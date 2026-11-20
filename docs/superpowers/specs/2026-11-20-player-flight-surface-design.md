# 飞入时先不显示 SurfaceView

## 目标

飞入播放页和返回时，圆角框还在动，默认的 SurfaceView 先不显示。过渡结束后再显示视频表面。设置里的表面类型不变。

## 表面

竖屏和横屏的本地画面里，同时满足这三条就不组合 `FittedPlayerSurface`：

- 当前不是 TextureView
- 当前没有投屏
- 共享过渡还在进行

圆角目标框留着。过渡结束后立刻组合表面，不为表面另加淡入。

已经选用 TextureView 的，表面一直显示。画中画那个分支不改。投屏时表面保持现在的样子。

要不要藏，收成 `hidePlayerSurfaceForTransition(useTextureView, isCasting, transitionActive)`。返回 `true` 时不组合表面。`transitionActive` 来自 `SharedTransitionScope.isTransitionActive`。没有共享范围时当作没在过渡。

换集不走导航过渡，表面不藏。没有匹配的缩略图时，共享过渡不进行，表面按现在的方式出现。

## 测试

单元测试覆盖四种：

- SurfaceView、未投屏、过渡中：藏
- SurfaceView、未投屏、未过渡：显示
- TextureView、过渡中：显示
- 投屏、SurfaceView、过渡中：显示

不使用 Compose 界面测试或 Robolectric。

## 这一轮不动

- 设置里的 SurfaceView 和 TextureView 选项
- 圆角、共享 key 和哪一张缩略图注册
- 导航的淡入淡出时长
- 共享边界的框弹簧
- 缩略图的加载和占位规则
- 画中画和投屏的现有画面
- 播放决定和下载
