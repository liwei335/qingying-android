# 清影 Android 0.2.0

面向 Android 8.0 及以上的原生视频聚合客户端。Java、Media3 ExoPlayer、WorkManager、SQLite；应用不接入广告和统计 SDK。0.2.0 保留 0.1.0 的签名和包名，可覆盖安装并迁移原片源、收藏和进度。

## 0.2.0 新增

- **资源选择**：按片源浏览、搜索；点击影片先选择资源，播放中也可查找并切换同名资源。不同年份、不同季尽量分开匹配，分集对应不上时提示重新选集。
- **热播推荐**：首页使用豆瓣公开的热播剧集和热门电影榜，按榜单顺序展示；“最近更新”保留在片库，不当作热度。榜单更新失败保留旧缓存和原时间，过期会提示。
- **动漫独立推荐**：底栏“动漫”提供独立动漫热榜与动漫片库。
- **屏幕适配**：片库按窗口宽度调整列数，页头可以滚动；播放器按实际窗口布局保持比例，可选择完整画面或裁切填满。自动画质同时参考视口像素、带宽与设备支持，也可手动选 1080p/4K 上限。
- **更多 4K 路径**：新增“快车 · 4K 专区”。“4K 专区”包括资源站标注；“已识别 4K”按播放清单或解码器给出的尺寸筛选，横向像素至少 3840 或纵向至少 2160。资源站可能误标，播放器显示的实际尺寸更有参考价值。
- **夸克网盘**：在应用内打开夸克官方页面登录，浏览个人目录、打开分享页面，原画或平台返回的转码画质交给原生播放器。分享转存由用户在官方页面自行操作，应用不自动转存、删除或修改网盘文件。
- **长按快进**：按住视频画面临时 2 倍速，松手恢复之前的速度；移动取消、失焦、切后台和离开页面时也恢复。进度条和播放按钮保留原本操作。

## 夸克使用

点击“夸克原画”或“设置 → 夸克网盘”，在官方页面完成登录后点击“进入我的网盘”。选择文件夹和视频即可播放，播放页“原画 / 画质”列出当前账号可用的地址。系统浏览器与应用 WebView 的登录态不共享，不能在系统浏览器登录后直接当作应用已登录。

本版本通过网页登录态兼容夸克网页接口，并非夸克官方 SDK。实际可用画质、原画权限、速度由文件、账号权益、网络与设备决定。接口可能变化；尚未使用真实账号完成登录后端到端测试。

登录态保存在应用私有的 WebView Cookie 存储中，不写入订阅、日志或导出文件。播放地址在播放前重新获取，不把含访问令牌的临时 URL 写入本地片单；携带 Cookie 的请求限于夸克/UC 官方域名，跳转后重新检查目标域名。退出登录会清除本地登录态和网盘记录。私人网盘条目不参与公共片源的搜索或后台检查。

## 当前功能

- 自动更新内置的公开 MacCMS 采集接口；本地搜索、全源搜索、分页加载。
- 按片源更新时间展示内容，区分更新时间与影片发布日期。
- 播放 MP4、HLS、DASH；选集、换线路、倍速、横屏、断点续播、收藏。
- 自适应画质及 1080p / 4K 画质上限；只有分辨率数据支持时才显示 4K 标签。单码率文件不能凭空切换清晰度。
- 每日后台刷新与资源检查，默认只在非计费网络、电量不低时执行；打开应用时补充过期刷新，也可手动刷新。
- HTTPS JSON/M3U 订阅、MacCMS JSON API，以及 TVBox 配置中的 `type: 1` HTTPS 采集线路导入。

## 使用

安装 APK 后，首次联网会抓取片单。点击影片播放，长按影片收藏；“选集 / 线路”可以切换分集和备用源。“片源与设置”可添加、移除片源或直接打开 HTTPS 视频地址。

当前内置暴风、非凡和快车 4K 专区，并保留 Blender 开放影片 Sintel 的 Shaka 4K 演示源。聚合接口的内容及可达性由第三方维护，无法保证每条线路持续可用。应用没有广告 SDK；第三方视频或夸克官方网页内部的内容由对应服务提供。

饭太硬、肥猫这类 TVBox 配置常包含加密配置及远程 JAR/JS 爬虫。当前版本**不是完整 TVBox 内核**，不能保证直接兼容这些订阅；导入时只接收支持的 `type: 1` 数据接口，不下载和执行远程插件。

后台检查受 Android 省电、网络、强行停止和厂商后台策略约束，不能保证每天固定时刻运行。每次任务限时约 3 分钟、最多 120 条地址，每部最多检查前两个分集/线路，较旧条目轮换检查。HLS 检查播放清单及首个视频片段；DASH 检查清单；MP4 检查有限文件头。`链接可达` 不是整部影片、全部分集、全部画质、音轨和解码的完整测试。404/410 与暂时超时、403、限流分开记录，不因一次网络错误删除影片。

## 构建

需要 JDK 17、Android SDK Platform 35、Build Tools 35.0.0、Gradle 8.11.1；工程固定 Android Gradle Plugin 8.9.1。

```sh
export JAVA_HOME=/path/to/jdk-17
export ANDROID_HOME=/path/to/android-sdk
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Debug APK 位于 `app/build/outputs/apk/debug/app-debug.apk`。发布构建需要 `.signing/keystore.properties` 中的本地签名配置；签名密钥不应提交到仓库或与源码一起公开发布。

运行 `./tools/build.sh` 可创建并保留本地签名身份，然后执行测试、Lint 和 Release 构建，产物写入 `dist/`。覆盖安装已有测试版需要使用原来的 `.signing/`，重新生成的密钥不能覆盖原安装。

## 自定义订阅

JSON 格式见 `examples/feed.json`。`id` 在同一来源内应保持稳定；URL 更新时收藏和记录仍可保留。`published` 表示真实发布日期，未知时省略；`height` / `width` 应填写真实分辨率。每份通用订阅读取最多 120 个条目，每条最多 12 个流。

M3U 支持 `#EXTINF` 名称与 `tvg-logo`、相对链接；含 `#EXT-X-` 的 HLS 清单会被当作单个视频处理。源必须直接提供 HTTPS 视频流，网页播放地址、DRM 授权、网盘登录及网页嗅探不在首版支持范围内。

## 参考与署名

- [Media3 官方文档](https://developer.android.com/media/media3/exoplayer/hello-world)
- [WorkManager 官方文档](https://developer.android.com/develop/background-work/background-tasks/persistent)
- [TVBox 数据配置示例](https://github.com/jiusanzhou/tvbox/blob/main/tvbox.json)
- [Shaka 演示资源清单](https://github.com/shaka-project/shaka-player/blob/main/demo/common/assets.js)
- [豆瓣剧集热榜](https://m.douban.com/subject_collection/tv_hot)、[热门电影](https://m.douban.com/subject_collection/movie_hot_gaia)、[动漫热榜](https://m.douban.com/subject_collection/tv_animation)
- [夸克网盘官方页面](https://pan.quark.cn/)、[官方功能说明](https://www.quark.cn/documents/help/quark-drive)
- [公开客户端使用的夸克网页接口参考](https://github.com/OpenListTeam/OpenList/tree/main/drivers/quark_uc)：只参考接口字段与端点，应用使用独立实现。
- Sintel：© Blender Foundation，https://durian.blender.org/ ，CC BY 3.0。应用只引用外部播放地址，未打包影片文件。

验证结果和安装包信息记录在 `VALIDATION.md`。源码编译、静态检查或单元测试不能替代手机上的触摸、解码、网络、功耗和后台调度测试。
