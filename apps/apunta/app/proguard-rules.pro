# JNI: native code calls these by name and signature.
-keep class app.apunta.whisper.WhisperNative { *; }
-keep interface app.apunta.whisper.WhisperListener { *; }
-keep class * implements app.apunta.whisper.WhisperListener { *; }
-keepclasseswithmembernames class * { native <methods>; }
