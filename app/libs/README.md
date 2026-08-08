# sherpa-onnx Android 运行库

应用 APK 不再打包 sherpa-onnx AAR 或原生库。用户第一次在设置页手动下载日语字幕模型时，
后台任务会先安装固定版本的 arm64-v8a 运行时，再继续下载模型。删除模型时保留运行时，
避免以后重复下载约 11 MiB 的运行时压缩包。

- 上游：<https://github.com/k2-fsa/sherpa-onnx/releases/tag/v1.13.2>
- 上游 AAR SHA-256：`aa5505c0ec4f8bdaee5f214a64ba3012be64f2aecc022e82a64f33392b8dd245`
- 许可证：Apache License 2.0，见 `sherpa-onnx-LICENSE.txt`
- 运行时打包脚本：`tools/package_sherpa_runtime.py`
- 运行时 ZIP：`sherpa-onnx-runtime-1.13.2-android-arm64-v8a.zip`
- 运行时 ZIP 大小：`11,311,151` 字节
- 运行时 ZIP SHA-256：`bfa564c5da27a7ab734d4c788cafd7c95c1e4934e02056be24358532d3d33c2e`

运行时 ZIP 只包含 `libonnxruntime.so` 与 `libsherpa-onnx-jni.so`。应用会校验压缩包及内部
每个文件的固定大小和 SHA-256，并只解压到应用内部目录；原生库在加载前会被设置为只读。
Silero VAD 模型仍位于 `app/src/main/assets/subtitle/silero_vad.onnx`，SHA-256 为
`9e2449e1087496d8d4caba907f23e0bd3f78d91fa552479bb9c23ac09cbb1fd6`。

日语字幕模型使用运行时下载的
`sherpa-onnx-nemo-parakeet-tdt_ctc-0.6b-ja-35000-int8`。原始 NVIDIA
Parakeet 模型采用 CC-BY-4.0 许可证，应用不会把该模型打包进 APK。
除 Hugging Face 上游下载地址外，GitHub 镜像发布在
<https://github.com/eValDoll/EaraAsmrPlayer/releases/tag/subtitle-model-parakeet-ja-int8>，
两个渠道下载后都会按应用内固定的文件大小和 SHA-256 摘要校验。
