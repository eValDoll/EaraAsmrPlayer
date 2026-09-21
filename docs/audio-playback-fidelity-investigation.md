# RJ01439823 播放听感差异排查

日期：2026-09-21。基线：远程 `origin/release/v1.2.3` 的 `6d2e9cc`，分支：`fix/audio-playback-fidelity`。

## 当前结论

尚未证实 App 的解码或音效处理导致用户反馈的“贴耳感减弱”。用户确认使用同一副耳机、播放 MP3、App 音效全部关闭，但网页在电脑上播放，App 在手机上播放。这个对比同时改变了播放设备和输出链路，不能仅据此归因于 App。

本次只增加音频保真回归测试与排查记录，没有修改生产播放逻辑、音效参数、系统音频策略或界面。需要在同一部手机上做网页与 App 的对比，才能继续缩小问题范围。

## 解码和播放链路的历史变动

- `a329655`（2026-08-23）将 Media3 从 1.2.1 升到 1.8.0，提交目的为修复 FLAC 拖动失败。确实存在播放库升级，但不能由升级本身认定 MP3 出现音质退化。
- `ddf3ab7`（2026-08-01）把自定义音效合并到 `DynamicAudioProcessorChain`，关闭的处理器在运行时旁路。
- `b21e480`（2026-08-02）修复旁路 PCM 缓冲被解码器复用的问题。最新 release 已包含该修复。
- `6b14ba0`（2026-08-24）调整输出缓冲用于频谱同步，没有加入混单声道、声场扩展或降采样处理。

当前链路：音源 URL / 文件 → Media3 提取与平台解码 → 默认 PCM 格式适配 → 频谱采样与应用音效链 → Media3 默认变速/静音处理器 → AudioTrack → 设备音频系统。

检查位置：

- `app/build.gradle.kts`：Media3 1.8.0。
- `app/src/main/java/com/asmr/player/playback/AsmrRenderersFactory.kt`：使用默认解码器工厂，自定义 AudioSink 的音效处理链。
- `app/src/main/java/com/asmr/player/playback/DynamicAudioProcessorChain.kt`：无生效音效时按字节复制 PCM 到独立缓冲，没有混音或数值运算。
- `app/src/main/java/com/asmr/player/service/PlaybackService.kt`：均衡器、场景、响度和立体声各自按开关生效；立体声关闭时，声道模式归零、平衡归零、环绕处理器关闭。音频用途是 `USAGE_MEDIA / CONTENT_TYPE_MUSIC`。
- `app/src/main/java/com/asmr/player/playback/StereoSpectrumTapAudioProcessor.kt`：只读取频谱样本，输出原样复制。

代码中未发现主动切换蓝牙通话模式、启用 SCO、强制单声道或降低 MP3 码率的操作。

## 指定作品的音源核对

读取 [asmr.one 曲目接口](https://api.asmr.one/api/tracks/1439823?v=2) 和 [asmr-200 曲目接口](https://api.asmr-200.com/api/tracks/1439823?v=2)。两者均返回 20 个音频条目，目录路径和所有音频的 `mediaDownloadUrl` 一致。App 常用的 asmr-200 镜像在本次请求中没有给出另一套音频。

作品同时包含普通版 MP3、WAV，以及“无效果音”下的 MP3、WAV；比较时仍需保证目录版本与曲目相同。

App 在线曲目取 `mediaDownloadUrl`，缺失时使用 `streamUrl`。对普通版第 01、02 首 MP3，分别对 `mediaStreamUrl` 和 `mediaDownloadUrl` 请求两个字节区间：`0–65535` 和 `1048576–1114111`。每组均返回 65,536 字节且 SHA-256 相同。第二个区间覆盖音频正文，避免只比较 ID3 标签。该结果是区间抽样，不是完整文件哈希。

| 曲目 | 区间起点 | stream / download 共用的 SHA-256 |
| --- | ---: | --- |
| 普通 MP3 01 | 0 | `0410b9ecf5b61b321350af4050d5765fa17c7f1895436ada58b31684b9fea0a5` |
| 普通 MP3 01 | 1048576 | `cf572dc81f0df1675fee6adba546b5e923ed036264b76fe97a4903847b478049` |
| 普通 MP3 02 | 0 | `59bb1391036149a16f0193f2390f2aa377643dcd9c84f514a96563da946872bb` |
| 普通 MP3 02 | 1048576 | `c00589089dd453e1fd970ee4a98681ec980e05ca30e408beff954b8db6586e6c` |

抽样 MP3 帧头为 MPEG-1 Layer III、48 kHz、192 kbps、joint stereo。未发现下载地址额外转码的证据。网页页面在本环境返回 HTTP 403，因此没有核实用户浏览器实际请求的 URL、网页播放器设置或电脑端解码输出；上述结论仅针对接口提供的两类地址。

## 排除及未证实的因素

- **应用立体声音效的衰减**：启用时，居中声像和默认距离 5 会产生约 0.524 倍（−5.62 dB）增益；这是已有音效逻辑。用户确认关闭，因此不能用它解释本次反馈，也未改动该效果。
- **高位深 WAV 转换**：本作品普通 WAV 01 为 24-bit PCM，02 为 32-bit float，均为 48 kHz 双声道。[Media3 1.8.0 默认关闭浮点输出](https://github.com/androidx/media/blob/1.8.0/libraries/exoplayer/src/main/java/androidx/media3/exoplayer/audio/DefaultAudioSink.java)，自定义音效链使用 16-bit 输出路径。用户本次播放 MP3，不能把 WAV 的位深差异当作本次原因。直接打开 float output 还会绕过 Media3 的自定义音效链，不能作为无验证的修复。
- **系统空间音效**：当前使用默认空间化策略，没有主动禁止系统空间化。[Android 官方说明](https://developer.android.com/media/grow/spatial-audio)指出平台默认仅空间化多声道内容，厂商可定制行为。本作品的 MP3 是双声道，因此不能仅凭默认策略断言手机做了二次空间化。
- **电脑与手机的输出差异**：系统音效、单声道/声道平衡设置、蓝牙协商和输出音量等仍待核实。同一副耳机不等于同一条输出链路；目前没有证据确定是哪一项。

## 验证

新增 `AudioFidelityTest`，覆盖：

1. Media3 默认处理器与应用处理链在正常速度下保留 PCM 样本。
2. 44.1 / 48 / 96 kHz、16-bit / float 双声道在所有应用音效关闭时逐字节一致。
3. 频谱采集开启时，PCM 不变。
4. 音效曾开启后实时关闭，不重新配置处理链也能恢复原始 PCM；解码器复用输入缓冲不破坏输出。

测试使用左右声道单独发声、反相、极小幅度和满幅样本；可检测混单声道、串音、衰减、削波与缓冲复用。它验证的是应用处理链，不替代真实手机 MP3 解码输出或耳机端测量。

Release 单元测试通过：`AudioFidelityTest` 4 项、`DynamicAudioProcessorChainTest` 2 项、`VolumeThresholdAudioProcessorTest` 4 项，共 10 项，零失败、零错误。

执行命令：`gradlew-local.bat -g D:\toyProjects\EaraAsmrPlayer\.gradle-user-home :app:testReleaseUnitTest --tests com.asmr.player.playback.AudioFidelityTest --tests com.asmr.player.playback.DynamicAudioProcessorChainTest --tests com.asmr.player.playback.VolumeThresholdAudioProcessorTest :app:installRelease`。复用主目录依赖缓存，构建输出仍在独立工作目录。

Release 编译、R8 混淆与 `packageRelease` 均通过；`installRelease` 因 `No connected devices!` 失败。`adb devices -l` 未列出设备，未读取用户手机实际安装版本或进行设备解码、录音及听感验证。

## 下一步复现条件

在同一部手机、同一耳机及连接方式下，比较同目录、同曲目的 App 和手机网页，均设正常速度与音调，并尽量匹配实际响度：

- 手机网页与 App 一致，而电脑不同：先检查两台设备的系统音效、输出与连接配置。
- 手机网页仍明显优于 App：用同一 MP3 文件对比另一个手机播放器，并连接设备读取实际解码器、采样率、声道、AudioTrack 路由和系统音效状态；必要时再做解码 PCM 对比。

在完成上述对照前，不替换解码器、不改变既有音效算法，也不把主观听感差异报告为已经修复。
