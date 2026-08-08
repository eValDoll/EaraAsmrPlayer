package com.k2fsa.sherpa.onnx

data class QnnConfig(
    var backendLib: String = "",
    var contextBinary: String = "",
    var systemLib: String = ""
)

data class FeatureConfig(
    var sampleRate: Int = 16000,
    var featureDim: Int = 80,
    var dither: Float = 0.0f
)

data class HomophoneReplacerConfig(
    var dictDir: String = "",
    var lexicon: String = "",
    var ruleFsts: String = ""
)
