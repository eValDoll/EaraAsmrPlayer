# Keep application model and integration names stable for Gson, Room, Retrofit,
# Compose state restoration, and any persisted class-name based data. R8 may
# still optimize method bodies and rewrite Baseline/Startup Profile rules.
-keep,allowoptimization class com.asmr.player.** { *; }

-keepattributes Signature,*Annotation*,InnerClasses,EnclosingMethod

# Retrofit 2.9's bundled rules predate the R8 full-mode handling required by
# Kotlin suspend services. Retrofit reflects on Continuation's type argument;
# if R8 strips it, every suspend API call fails before OkHttp sees a request.
-keep,allowoptimization,allowshrinking,allowobfuscation class kotlin.coroutines.Continuation
-if interface * { @retrofit2.http.* public *** *(...); }
-keep,allowoptimization,allowshrinking,allowobfuscation class <3>
-keep,allowoptimization,allowshrinking,allowobfuscation class retrofit2.Response

# Gson reads the generic superclass of anonymous TypeToken implementations at
# runtime. Keeping the base and its subclasses prevents R8 full-mode class
# merging from erasing that relationship while leaving the rest of the app
# eligible for shrinking and method optimization.
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken { *; }

# sherpa-onnx 通过 JNI 解析 Kotlin 配置和推理结果。
-keep class com.k2fsa.sherpa.onnx.** { *; }
