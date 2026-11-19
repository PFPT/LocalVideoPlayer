# 打开库先出上次的列表

## 目标

有上次的列表时，打开库不先出现「没有视频」，也不转圈。缓存读完就显示上次的列表，后台刷新直接替换。从来没有缓存时仍转圈。

磁盘读取完成前，列表区域留白。这一轮不要求缓存行画在第一次组合上。

## 状态

`LibraryViewModel` 一开始是缓存未读完。`loadFromCache` 在 `finally` 里标记已读完，读到条目、读到空、读取失败都一样。读取失败按没有缓存处理。

没权限时仍是现在的权限页，不读缓存。

界面用纯函数 `libraryOpening(cacheResolved, entryCount, isLibraryLoading)` 决定列表区域，按这个顺序：

- `cacheResolved` 为假：留白。不显示空状态，不显示转圈。这一条优先于条目数和 `isLibraryLoading`。
- `entryCount > 0`：列表。即使 `isLibraryLoading` 为真也显示列表。
- `isLibraryLoading` 为真：转圈。
- 其余：现在的「没有视频」。

`VideoListScreen` 用这个函数替换打开时那两段空状态和转圈条件。分类筛选后的「没有结果」保持现状。`isSortingLibrary` 不再参与打开时的转圈判断。

缓存读完且没有条目时，`loadFromCache` 在返回前把正在加载设为真。随后的 `refresh(showLoading = true)` 保持这个状态。这样读完空缓存到刷新开始之间，不会闪出「没有视频」。

## 调用

`LocalVideoPlayerApp` 仍在有权限后调用 `loadFromCache()`，然后 `refresh`。

有条目时，`refresh(showLoading = false)`。已有列表的刷新不打开转圈，结果直接替换列表。

没有条目时，`refresh(showLoading = true)`，扫描过程保持现在的转圈。

## 失败和替换

刷新抛出异常时，不把已有列表清成空。`isLibraryLoading` 仍在现有的 `finally` 里关掉。

扫描结果为空，并且这次不是下拉刷新时，保持现在的提前返回，留下上次的列表。

下拉刷新保持现在的转圈和结果处理。

替换列表时不新增滚动恢复。切换分类时滚到顶部保持现状。

## 测试

纯函数覆盖四种结果：缓存未读完、有条目、没有条目且正在加载、空库。测试不使用 Compose，也不使用 Robolectric。

## 这一轮不动

- 缩略图的加载和占位
- 分组算法、排序、筛选和增量扫描
- 下拉刷新的手势和指示
- 滚动位置恢复
- 播放和下载
