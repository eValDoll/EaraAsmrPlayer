# Eara（Android）

<p align="center">
  <img src="../asmr_logo.svg" width="160" alt="Eara logo" />
</p>

<p align="center">
  <strong>为 ASMR 而生的本地播放器：专辑管理、沉浸播放、耳机向音效与内容检索下载，一站式完成。</strong>
</p>

---

## 一句话介绍

Eara 是一款面向 ASMR 内容的 Android 播放器：既有顺滑的本地媒体库体验，也提供播放器级增强能力，包括同步歌词、后台下载、左右声道可视化与声道控制、深度主题定制等。

## 示例图片

以下示例图展示当前版本的主要页面与视觉风格，优先覆盖本地库、在线搜索、收藏整理与播放体验。

### 本地库

| 专辑卡片 | 专辑列表 |
|:---:|:---:|
| <img src="../example_screen/本地库_专辑卡片_示例.png" width="50%"/> | <img src="../example_screen/本地库_专辑列表_示例.png" width="50%"/> |

| 音轨列表 | 暗色主题 |
|:---:|:---:|
| <img src="../example_screen/本地库_音轨列表_示例.png" width="50%"/> | <img src="../example_screen/本地库_暗色主题_示例.png" width="50%"/> |

### 搜索与同步

| 在线搜索卡片 | 在线搜索列表 |
|:---:|:---:|
| <img src="../example_screen/在线搜索_专辑卡片_示例.png" width="50%"/> | <img src="../example_screen/在线搜索_专辑列表_示例.png" width="50%"/> |

| 搜索暗色主题 | 本地 + 云同步 |
|:---:|:---:|
| <img src="../example_screen/在线搜索_暗色主题_示例.png" width="50%"/> | <img src="../example_screen/本地+云同步_示例.png" width="50%"/> |

### 详情与收藏

| 本地专辑详情 | 在线专辑详情 |
|:---:|:---:|
| <img src="../example_screen/专辑详情_本地_示例.png" width="50%"/> | <img src="../example_screen/专辑详情_在线_示例.png" width="50%"/> |

| 我的收藏 | 下载管理 |
|:---:|:---:|
| <img src="../example_screen/我的收藏_示例.png" width="50%"/> | <img src="../example_screen/下载管理_示例.png" width="50%"/> |

### 播放体验

| 封面动态主色 | 透明背景播放页 |
|:---:|:---:|
| <img src="../example_screen/封面动态主色_示例.png" width="50%"/> | <img src="../example_screen/播放页面_背景透明-0_示例.png" width="50%"/> |

| 歌词页 | 沉浸背景播放页 |
|:---:|:---:|
| <img src="../example_screen/歌词页面_背景透明-45_示例.png" width="50%"/> | <img src="../example_screen/播放页面_背景透明-75_示例.png" width="50%"/> |

## 亮点功能

- **本地库体验**：以“专辑 / 曲目”为核心组织方式，浏览、筛选与搜索更高效
- **沉浸式播放**：播放器内置歌词页、专注式播放布局、封面背景氛围与频轨可视化
- **耳机向音效**：均衡器、混响、增益、Virtualizer、左右声道平衡与空间化
- **双声道频谱**：面向双耳内容优化的 L/R 频谱与可视化
- **片段标记与循环**：在进度条上标注切片，支持 AB 循环、拖拽精调与片段预览
- **内容检索聚合**：支持 DLsite、asmr.one 等来源，快速进入播放与整理流程
- **DLsite 已购**：登录后可在应用内播放或下载 Play 已购内容
- **后台下载与离线**：WorkManager 管理下载任务，结合本地数据库实现离线浏览与播放
- **悬浮歌词**：在系统悬浮窗中显示歌词，适合边做事边听
- **视频支持**：支持常见视频格式与 m3u8 资源的预览与播放
- **定时与通知**：睡眠定时、系统通知控制与后台播放

## 适合谁？

- 有较大本地 ASMR 音频库，希望以“专辑”为中心统一整理与播放
- 重视左右声道细节、喜欢频谱可视化、需要声道平衡或空间化效果的耳机党
- 想要歌词沉浸，甚至跨应用悬浮显示的用户
- 希望把“检索 → 下载 → 入库 → 离线播放”串成一个工作流的整理型用户

## 下载体验

- 从 **GitHub Releases** 下载（tag `v*`，最新：`v0.2.2`）
- 或在本地自行构建 Debug：

```bash
./gradlew :app:assembleDebug
```

## 开始使用

- 添加本地库：进入“本地库” → “添加文件夹”，选择你的专辑根目录
- 播放与歌词：在专辑或曲目列表播放；歌词页支持 LRC / VTT / SRT 同步显示
- 悬浮歌词：在设置中开启“悬浮歌词”，按系统提示授予悬浮窗权限
- 片段与循环：在“正在播放”页开启切片模式，标记片段并进行 AB 循环或拖拽微调
- DLsite 已购：从侧边栏或设置进入“DLsite 登录”，登录后可在详情页使用已购播放或下载
- 下载管理：在“下载”页查看任务进度与状态，支持后台进行

## 本地编译与安装（含 Profile）

### 环境要求

- Android Studio（建议使用稳定版）
- JDK 17（Android Gradle Plugin 8.x 需要）
- Android SDK：`compileSdk/targetSdk = 34`，`minSdk = 24`

### 命令行构建与安装

```bash
./gradlew :app:installDebug
./gradlew :app:assembleRelease
```

### Baseline/Startup Profile

- 已包含：
  - Baseline Profile: [app/src/main/baseline-prof.txt](../app/src/main/baseline-prof.txt)
  - Startup Profile: [app/src/main/startup-prof.txt](../app/src/main/startup-prof.txt)
- 重新采集（可选，需要连接设备 / 模拟器）：

```bash
./gradlew :app:assembleBenchmark
./gradlew :baselineprofile:connectedBenchmarkAndroidTest
./gradlew :app:assembleRelease
```

Profile 采集完成后将在后续 Release 构建中生效，用于优化启动与滚动性能。

### 构建产物位置

- 默认：`<repo>/.build_asmr_player_android/`

## 权限说明（简要）

- **媒体 / 存储访问**：用于扫描并播放你的本地音频文件
- **通知权限**：用于播放控制与播放服务的前台通知
- **悬浮窗权限（可选）**：仅在开启“悬浮歌词”功能时需要

## 免责声明

本项目为非官方作品，不隶属于任何第三方平台或品牌。请遵守所在地法律法规与第三方服务条款，自行承担使用风险。
