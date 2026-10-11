package app.apunta.whisper

import android.content.Context

/** Callbacks invoked from the native thread running whisper_full (the caller's thread). */
interface WhisperListener {
    fun onSegment(startMs: Long, endMs: Long, utf8: ByteArray, noSpeechProb: Float)
    fun onProgress(percent: Int)
    fun shouldAbort(): Boolean
    fun onLanguage(code: String)
}

/** Thin binding over libapunta.so (see src/main/cpp/apunta_jni.cpp). */
object WhisperNative {
    private var loaded = false

    @Synchronized
    fun load(context: Context) {
        if (loaded) return
        System.loadLibrary("c++_shared")
        System.loadLibrary("ggml-base")
        System.loadLibrary("ggml")
        System.loadLibrary("whisper")
        System.loadLibrary("apunta")
        loadBackends(context.applicationInfo.nativeLibraryDir)
        loaded = true
    }

    private external fun loadBackends(nativeLibDir: String)
    external fun systemInfo(): String
    external fun initContext(modelPath: String): Long
    external fun freeContext(ptr: Long)
    external fun transcribe(
        ptr: Long, samples: FloatArray, nSamples: Int, language: String, translate: Boolean,
        threads: Int, initialPrompt: String?, timeOffsetMs: Long, listener: WhisperListener,
    ): Int
}
