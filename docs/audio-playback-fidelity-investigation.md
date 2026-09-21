# RJ01439823 播放听感差异排查

日期：2026-09-21。基线：远程 `origin/release/v1.2.3` 的 `6d2e9cc`，分支：`fix/audio-playback-fidelity`。

## 当前结论

实机数字输出测试未发现 App 的解码或音效处理使本次样本的双声道声场变窄。手机原有版本和本分支 Release 在同一段 14 秒音频上的 1,344,000 个 PCM 采样值完全一致；App 输出与同一 MP3 的 FFmpeg 解码结果也高度一致，见下方“实机自动化补测”。

用户确认使用同一副耳机、播放 MP3、App 音效全部关闭。随后手动试听手机浏览器，反馈“在手机的浏览器上听也和电脑上不一样”，并进一步确认关闭系统 Dolby 后听感符合预期，要求提供系统原声相关入口。结合 App 数字输出测试，本次反馈指向设备系统音效，而非 App 特有的解码问题。Dolby 对听感的影响来自用户试听确认，尚无耳机端声学回录；该反馈也没有明确确认手机网页与 App 的数字输出完全一致。

音频排查未改变生产播放逻辑、音效参数或系统音频策略。用户随后要求在播放设置访问原声相关设置；经下方系统旁路能力验证后，选择增加“系统音效”入口，进入手机系统页面手动选择音效。

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
- **电脑与手机的输出差异**：下方补测已读取手机的系统音效、单声道/声道平衡设置、蓝牙协商和输出音量；电脑端对应状态仍未取得。同一副耳机不等于同一条输出链路，目前没有证据确定是哪一项导致听感差异。

## 验证

新增 `AudioFidelityTest`，覆盖：

1. Media3 默认处理器与应用处理链在正常速度下保留 PCM 样本。
2. 44.1 / 48 / 96 kHz、16-bit / float 双声道在所有应用音效关闭时逐字节一致。
3. 频谱采集开启时，PCM 不变。
4. 音效曾开启后实时关闭，不重新配置处理链也能恢复原始 PCM；解码器复用输入缓冲不破坏输出。

测试使用左右声道单独发声、反相、极小幅度和满幅样本；可检测混单声道、串音、衰减、削波与缓冲复用。它验证的是应用处理链，不替代真实手机 MP3 解码输出或耳机端测量。

Release 单元测试通过：`AudioFidelityTest` 4 项、`DynamicAudioProcessorChainTest` 2 项、`VolumeThresholdAudioProcessorTest` 4 项，共 10 项，零失败、零错误。

执行命令：`gradlew-local.bat -g D:\toyProjects\EaraAsmrPlayer\.gradle-user-home :app:testReleaseUnitTest --tests com.asmr.player.playback.AudioFidelityTest --tests com.asmr.player.playback.DynamicAudioProcessorChainTest --tests com.asmr.player.playback.VolumeThresholdAudioProcessorTest :app:installRelease`。复用主目录依赖缓存，构建输出仍在独立工作目录。

首轮 Release 编译、R8 混淆与 `packageRelease` 均通过；当时 `installRelease` 因 `No connected devices!` 失败。用户随后连接手机，第二轮已成功完成 `gradlew-local.bat :app:installRelease`，安装到 1 台设备，并进行了以下实机录音验证。

## 实机自动化补测

设备为 Redmi `23078RKD5C`，Android 16 / API 36。测试前 App 版本为 1.2.2（10202）。先测已安装版本，再备份原 APK、核对签名一致，安装本分支 `9a9260e` 的 Release 后复测；没有清除 App 数据。

通过媒体会话确认正在播放的音源为 `RJ01439823 / 01_本篇（MP3） / 第 04 首`，为实际 `media/download` URL，48 kHz / 192 kbps / 双声道。完整 MP3 的 SHA-256 为 `bc44582bd48d4f2d7a3714f3c211c5e69fdac63d5f7d6a5bdba0f971f92d3972`。

设备实际状态：

- MP3 解码器：`c2.mtk.mp3.decoder`。
- App AudioTrack：48 kHz、16-bit PCM、双声道，播放速度 1.0。
- 音量阈值、场景效果、立体声、均衡器关闭；声道模式正常。
- 系统 master mono 关闭、左右平衡为 0。
- 耳机路由为 Bluetooth A2DP，协商 AAC / 44.1 kHz / 16-bit / stereo。
- 系统媒体音量 4/15；系统 Dolby effect 已启用，MiSound effect 未启用。测试没有改动这些设置。

使用 [scrcpy 的 playback duplication](https://github.com/Genymobile/scrcpy/blob/v2.7/doc/audio.md) 和 raw WAV 录音，保留手机耳机播放；每轮从约 08:54 开始采集 27 秒。下载同一 MP3，用 FFmpeg 解码为 48 kHz 双声道 float PCM，以归一化互相关对齐采样，再避开开始/停止淡入淡出，选 16 秒稳定片段计算指标。

| 指标 | 手机原有版本 | 本分支 Release |
| --- | ---: | ---: |
| 主声道（右）与参考波形的相关系数 | 0.9999999889 | 0.9999999887 |
| 右声道增益变化 | +0.000181 dB | +0.000174 dB |
| 左声道增益变化 | +0.000261 dB | +0.000260 dB |
| 左右电平差相对原音的变化 | −0.000694 dB | −0.000717 dB |
| 增益拟合后右声道残差 RMS | −98.04 dBFS | −98.14 dBFS |

原音在该片段右声道比左声道强约 41 dB，App 保留了这种强烈的左右差异。原有版本在录音第 6、10、14、18、22 秒得到相同的对齐延迟，没有检出片段内变速或持续采样漂移。

再将两次 App 录音精确裁到同一原音位置 **08:58–09:12**：672,000 帧 / 1,344,000 个左右声道采样，**不同采样数为 0，逐样本完全相同**。这说明本分支 Release 没有在这一实机片段引入相对手机原有版本的数字音频变化。

完整数值见 [设备测试结果 JSON](audio-playback-fidelity-device-results.json)。录音和设备日志仅保存在工作目录的 `.dev-data/audio-fidelity-device/`，未加入 Git。测试后恢复原曲目约 08:54 的暂停位置和原队列。

可用 [比对脚本](../tools/compare-audio-captures.py) 重现采样对齐、增益、相关系数、串音矩阵和两次录音的逐样本比较。脚本依赖 NumPy、SciPy，拒绝采样率不同或非双声道输入，不进行重采样、归一化音量或混声道。以下命令已对本次两份实机录音执行，复现上述结果：

```powershell
$env:PYTHONUTF8=1
python tools/compare-audio-captures.py --reference .dev-data/audio-fidelity-device/reference-segment.wav --reference-start 520 --capture .dev-data/audio-fidelity-device/app.wav --capture .dev-data/audio-fidelity-device/release.wav --capture-start 6 --duration 16 --common-source-start 538 --common-duration 14 --output .dev-data/audio-fidelity-device/reproduced-comparison.json
```

**测量边界：** 此处验证的是 Android playback capture 能取得的数字信号，不是蓝牙 AAC 编码之后或耳机的模拟/声学输出。不能据此断言系统 Dolby、手机与电脑的蓝牙编码及耳机端处理完全相同，也没有把 Dolby 的启用状态认定为已证实根因。

**浏览器对照：** 自动审批曾拒绝通过 ADB 启动手机 Chrome 打开作品页的操作，仅返回 `blocked by policy`，未给出具体原因。当前电脑使用工具仅暴露 Codex 内置浏览器，没有手机浏览器控制入口。用户随后自行试听，确认手机网页与电脑听感也不同。这是用户主观试听结果，尚未取得浏览器录音，不能将其写作 App 与手机网页的数字输出 A/B 对比已通过。

## 系统 Dolby 旁路能力与设置入口

用户确认关闭系统 Dolby 后听感符合预期，并提出在播放设置增加原声选项。已制作按音轨设置 `SPATIALIZATION_BEHAVIOR_NEVER` 的实验开关，在实机验证后撤回，未作为有效的 Dolby 旁路功能交付。

[Android 的公开接口](https://developer.android.com/media/grow/spatial-audio)允许音轨禁止平台空间化。手机确实接受该标记：标准 Spatializer 能力查询中，双声道 AUTO 可空间化，NEVER 不可空间化。但用户重新开启 Dolby 后，标准 Spatializer 的 enabled 仍为 false，而厂商独立的 Dolby DAP 已启用。

在 App UID 下创建相同格式的 AudioTrack，依次运行 AUTO → NEVER → AUTO，每轮持续播放 8 秒静音 PCM，并采集 AudioPolicy / AudioFlinger 状态：

| 模式 | AudioPolicy 属性 Flags | 输出线程 | Dolby DAP |
| --- | --- | --- | --- |
| AUTO | `0x800` | `AudioOut_15`，Bluetooth A2DP | session 0，启用、未暂停 |
| NEVER | `0x8800` | 同一线程 | session 0，仍启用、未暂停 |
| AUTO | `0x800` | 同一线程 | session 0，仍启用、未暂停 |

接口调用和 AudioTrack 属性校验通过，但独立 Dolby 没有从效果链移除或停用。这不是耳机端回录，也不能仅凭属性校验通过承诺已绕过 Dolby。因此不保留可能误导用户的“原声播放”开关，不尝试改变全局 Dolby 或伪装通话、闹钟等音频用途。设备当前输出也没有报告可配置的 bit-perfect mixer。

保留 `SystemSpatializationAndroidTest` 作为设备能力探针。传入 instrumentation 参数 `audioPolicyProbeDurationMs=8000` 可开启上述静音播放窗口，用 `dumpsys media.audio_policy`、`dumpsys media.audio_flinger` 检查路由及效果链。默认测试只验证平台接受属性，不将 Dolby 旁路作为成功条件。曾尝试通过 ActivityScenario 验证实际服务切换，但测试页面启动超时，没有进入开关切换；该实验测试已移除，不计入通过项目。

用户选择的最终实现是在“播放设置”增加“系统音效”一行，只有标题和右箭头。小米、Redmi、POCO 优先通过 `miui.intent.action.HEADSET_SETTINGS` 打开 `com.miui.misound`；若厂商入口不存在或权限受限，则依次尝试 Android 声音设置、系统设置。实机解析到 `com.miui.misound/.HeadsetSettingsActivity`。页面由系统提供，系统音效的选择会影响其他应用；App 本身不更改系统开关。

最终执行 `gradlew-local.bat -g D:\toyProjects\EaraAsmrPlayer\.gradle-user-home :app:installRelease -PreleaseAndroidTest :app:assembleReleaseAndroidTest` 成功，Release 安装到连接的手机。实机运行 `SystemAudioEffectsSettingsAndroidTest`、`SystemSpatializationAndroidTest`，2 项通过。ActivityTaskManager 确认入口请求来自 `com.asmr.player` 的 UID 10584，目标为 `com.miui.misound/.HeadsetSettingsActivity`，随后该页面为前台 resumed activity。通用声音设置、系统设置的回退分支未在其他品牌设备上实测。

## 后续定位方向

App 解码方向目前没有发现需要修复的缺陷。若继续定位设备间的听感差异，应保持同目录、同曲目、同一片段、正常速度与音调，并尽量匹配实际响度，每次只改变一个条件：

- 首先对照手机已确认开启的系统 Dolby 开关；App 音效关闭不代表系统音效关闭。当前测试没有修改该设置，也未证明 Dolby 是根因。
- 再核对电脑的系统音效、耳机连接方式与蓝牙协商参数。手机实际为 A2DP AAC / 44.1 kHz，电脑端参数未知，不能直接认定某种编码导致差异。
- 只有同一手机上网页与 App 仍有差异时，再采集两者相同片段的数字输出，并比较实际 AudioTrack 路由、系统音效和最终耳机输出。

本分支保留回归测试、复测脚本、排查证据和用户选定的系统音效入口；没有改变解码器或既有音效算法。
