# sherpa-onnx Android 运行库

- 文件：`sherpa-onnx-1.13.2.aar`
- 上游：<https://github.com/k2-fsa/sherpa-onnx/releases/tag/v1.13.2>
- SHA-256：`aa5505c0ec4f8bdaee5f214a64ba3012be64f2aecc022e82a64f33392b8dd245`
- 许可证：Apache License 2.0，见 `sherpa-onnx-LICENSE.txt`

AAR 通过 Git LFS 管理，应用只打包 `arm64-v8a` 原生库。Silero VAD 模型位于
`app/src/main/assets/subtitle/silero_vad.onnx`，SHA-256 为
`9e2449e1087496d8d4caba907f23e0bd3f78d91fa552479bb9c23ac09cbb1fd6`。

日语字幕模型使用运行时下载的
`sherpa-onnx-nemo-parakeet-tdt_ctc-0.6b-ja-35000-int8`。原始 NVIDIA
Parakeet 模型采用 CC-BY-4.0 许可证，应用不会把该模型打包进 APK。
除 Hugging Face 上游下载地址外，GitHub 镜像发布在
<https://github.com/eValDoll/EaraAsmrPlayer/releases/tag/subtitle-model-parakeet-ja-int8>，
两个渠道下载后都会按应用内固定的文件大小和 SHA-256 摘要校验。
