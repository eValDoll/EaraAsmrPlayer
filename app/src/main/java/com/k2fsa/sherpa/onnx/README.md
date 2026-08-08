# sherpa-onnx Kotlin API

这里保留了 sherpa-onnx `v1.13.2` Android Kotlin API 中本项目实际需要的 JNI 数据结构与包装类，
上游源码位于 <https://github.com/k2-fsa/sherpa-onnx/tree/v1.13.2/sherpa-onnx/kotlin-api>。

与上游的唯一行为差异是移除了各包装类中的 `System.loadLibrary("sherpa-onnx-jni")` 静态加载。
应用必须先通过 `SherpaOnnxNativeLoader` 校验并按绝对路径加载按需安装的只读原生库。
许可证见 `app/libs/sherpa-onnx-LICENSE.txt`。
