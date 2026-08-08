# Keep application model and integration names stable for Gson, Room, Retrofit,
# Compose state restoration, and any persisted class-name based data. R8 may
# still optimize method bodies and rewrite Baseline/Startup Profile rules.
-keep,allowoptimization class com.asmr.player.** { *; }

-keepattributes Signature,*Annotation*,InnerClasses,EnclosingMethod

# Gson reads the generic superclass of anonymous TypeToken implementations at
# runtime. Keeping the base and its subclasses prevents R8 full-mode class
# merging from erasing that relationship while leaving the rest of the app
# eligible for shrinking and method optimization.
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken { *; }

# sherpa-onnx 通过 JNI 解析 Kotlin 配置和推理结果。
-keep class com.k2fsa.sherpa.onnx.** { *; }
