// JNI bridge between Kotlin (app.apunta.whisper.WhisperNative) and whisper.cpp.
//
// Design notes:
//  * Text crosses the boundary as UTF-8 byte arrays, never through NewStringUTF:
//    whisper can emit byte sequences that are not valid "modified UTF-8" (emoji,
//    or a multi-byte character split across tokens) and NewStringUTF aborts the
//    process on those under CheckJNI. Kotlin decodes the bytes leniently.
//  * whisper_full() runs on the calling (Kotlin worker) thread and every callback
//    fires on that same thread, so the JNIEnv captured at entry stays valid.
//  * Every pointer handed to Kotlin is an opaque jlong; the Kotlin side guarantees
//    a context is freed exactly once and never used concurrently.

#include <jni.h>
#include <android/log.h>

#include <atomic>
#include <cstring>
#include <string>
#include <vector>

#include "ggml-backend.h"
#include "whisper.h"

#define TAG "ApuntaJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, TAG, __VA_ARGS__)

namespace {

std::atomic<bool> g_backends_loaded{false};

struct CallbackCtx {
    JNIEnv *env;
    jobject listener;
    jmethodID on_segment;
    jmethodID on_progress;
    jmethodID should_abort;
    whisper_context *ctx;
    int64_t time_offset_cs;  // added to every segment timestamp (centiseconds)
    bool failed;             // set when a Java callback threw
};

std::string jstring_to_std(JNIEnv *env, jstring s) {
    if (s == nullptr) return {};
    const char *chars = env->GetStringUTFChars(s, nullptr);
    if (chars == nullptr) return {};
    std::string out(chars);
    env->ReleaseStringUTFChars(s, chars);
    return out;
}

jbyteArray to_bytes(JNIEnv *env, const char *text) {
    const jsize len = text ? static_cast<jsize>(strlen(text)) : 0;
    jbyteArray arr = env->NewByteArray(len);
    if (arr != nullptr && len > 0) {
        env->SetByteArrayRegion(arr, 0, len, reinterpret_cast<const jbyte *>(text));
    }
    return arr;
}

void log_cb(ggml_log_level level, const char *text, void * /*user*/) {
    if (level >= GGML_LOG_LEVEL_WARN) {
        __android_log_print(ANDROID_LOG_WARN, TAG, "%s", text);
    }
}

void on_new_segment(whisper_context *ctx, whisper_state * /*state*/, int n_new, void *user) {
    auto *cb = static_cast<CallbackCtx *>(user);
    if (cb->failed) return;
    JNIEnv *env = cb->env;
    const int n = whisper_full_n_segments(ctx);
    for (int i = n - n_new; i < n; ++i) {
        const int64_t t0 = whisper_full_get_segment_t0(ctx, i) + cb->time_offset_cs;
        const int64_t t1 = whisper_full_get_segment_t1(ctx, i) + cb->time_offset_cs;
        const float no_speech = whisper_full_get_segment_no_speech_prob(ctx, i);
        jbyteArray text = to_bytes(env, whisper_full_get_segment_text(ctx, i));
        if (text == nullptr) { cb->failed = true; return; }
        // centiseconds -> milliseconds
        env->CallVoidMethod(cb->listener, cb->on_segment,
                            static_cast<jlong>(t0 * 10), static_cast<jlong>(t1 * 10),
                            text, static_cast<jfloat>(no_speech));
        env->DeleteLocalRef(text);
        if (env->ExceptionCheck()) { cb->failed = true; return; }
    }
}

void on_progress(whisper_context * /*ctx*/, whisper_state * /*state*/, int progress, void *user) {
    auto *cb = static_cast<CallbackCtx *>(user);
    if (cb->failed) return;
    cb->env->CallVoidMethod(cb->listener, cb->on_progress, static_cast<jint>(progress));
    if (cb->env->ExceptionCheck()) cb->failed = true;
}

bool should_abort(void *user) {
    auto *cb = static_cast<CallbackCtx *>(user);
    if (cb->failed) return true;
    const jboolean abort = cb->env->CallBooleanMethod(cb->listener, cb->should_abort);
    if (cb->env->ExceptionCheck()) { cb->failed = true; return true; }
    return abort == JNI_TRUE;
}

bool encoder_begin(whisper_context * /*ctx*/, whisper_state * /*state*/, void *user) {
    return !should_abort(user);
}

}  // namespace

extern "C" {

JNIEXPORT void JNICALL
Java_app_apunta_whisper_WhisperNative_loadBackends(JNIEnv *env, jobject /*thiz*/, jstring native_lib_dir) {
    if (g_backends_loaded.exchange(true)) return;
    ggml_log_set(log_cb, nullptr);
    whisper_log_set(log_cb, nullptr);
    const std::string dir = jstring_to_std(env, native_lib_dir);
    // CPU backend variants (armv8.0, armv8.2+dotprod, ... ) are separate .so files;
    // ggml scores each one against the running CPU and keeps the best match.
    ggml_backend_load_all_from_path(dir.empty() ? nullptr : dir.c_str());
    LOGI("ggml backends loaded: %zu", ggml_backend_reg_count());
}

JNIEXPORT jstring JNICALL
Java_app_apunta_whisper_WhisperNative_systemInfo(JNIEnv *env, jobject /*thiz*/) {
    // ASCII only, safe for NewStringUTF.
    return env->NewStringUTF(whisper_print_system_info());
}

JNIEXPORT jlong JNICALL
Java_app_apunta_whisper_WhisperNative_initContext(JNIEnv *env, jobject /*thiz*/, jstring model_path) {
    const std::string path = jstring_to_std(env, model_path);
    if (path.empty()) return 0;
    whisper_context_params cparams = whisper_context_default_params();
    cparams.use_gpu = false;
    cparams.flash_attn = true;
    whisper_context *ctx = whisper_init_from_file_with_params(path.c_str(), cparams);
    if (ctx == nullptr) LOGW("whisper_init_from_file failed");
    return reinterpret_cast<jlong>(ctx);
}

JNIEXPORT void JNICALL
Java_app_apunta_whisper_WhisperNative_freeContext(JNIEnv * /*env*/, jobject /*thiz*/, jlong ptr) {
    auto *ctx = reinterpret_cast<whisper_context *>(ptr);
    if (ctx != nullptr) whisper_free(ctx);
}

/**
 * Transcribes one chunk of 16 kHz mono float PCM.
 * Returns 0 on success, 1 if aborted, a negative value on error.
 * Segments are delivered through listener.onSegment(t0Ms, t1Ms, utf8, noSpeechProb).
 * Detected language (when [language] is "auto") is returned through listener.onLanguage.
 */
JNIEXPORT jint JNICALL
Java_app_apunta_whisper_WhisperNative_transcribe(
        JNIEnv *env, jobject /*thiz*/, jlong ptr, jfloatArray samples, jint n_samples,
        jstring language, jboolean translate, jint n_threads, jstring initial_prompt,
        jlong time_offset_ms, jobject listener) {
    auto *ctx = reinterpret_cast<whisper_context *>(ptr);
    if (ctx == nullptr || samples == nullptr || listener == nullptr) return -1;

    const jsize array_len = env->GetArrayLength(samples);
    if (n_samples < 0 || n_samples > array_len) return -2;

    jclass cls = env->GetObjectClass(listener);
    CallbackCtx cb{};
    cb.env = env;
    cb.listener = listener;
    cb.on_segment = env->GetMethodID(cls, "onSegment", "(JJ[BF)V");
    cb.on_progress = env->GetMethodID(cls, "onProgress", "(I)V");
    cb.should_abort = env->GetMethodID(cls, "shouldAbort", "()Z");
    jmethodID on_language = env->GetMethodID(cls, "onLanguage", "(Ljava/lang/String;)V");
    env->DeleteLocalRef(cls);
    if (!cb.on_segment || !cb.on_progress || !cb.should_abort || !on_language) {
        env->ExceptionClear();
        return -3;
    }
    cb.ctx = ctx;
    cb.time_offset_cs = static_cast<int64_t>(time_offset_ms) / 10;

    const std::string lang = jstring_to_std(env, language);
    const std::string prompt = jstring_to_std(env, initial_prompt);

    whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.n_threads = n_threads > 0 ? n_threads : 4;
    params.translate = translate == JNI_TRUE;
    params.language = lang.empty() ? "auto" : lang.c_str();
    params.detect_language = false;
    params.print_progress = false;
    params.print_realtime = false;
    params.print_special = false;
    params.print_timestamps = false;
    params.no_context = false;
    params.suppress_blank = true;
    params.suppress_nst = true;
    params.initial_prompt = prompt.empty() ? nullptr : prompt.c_str();
    params.greedy.best_of = 2;
    params.new_segment_callback = on_new_segment;
    params.new_segment_callback_user_data = &cb;
    params.progress_callback = on_progress;
    params.progress_callback_user_data = &cb;
    params.abort_callback = should_abort;
    params.abort_callback_user_data = &cb;
    params.encoder_begin_callback = encoder_begin;
    params.encoder_begin_callback_user_data = &cb;

    jfloat *data = env->GetFloatArrayElements(samples, nullptr);
    if (data == nullptr) return -4;
    const int rc = whisper_full(ctx, params, data, n_samples);
    env->ReleaseFloatArrayElements(samples, data, JNI_ABORT);

    if (cb.failed) {
        // Let a pending Java exception propagate if there is one.
        return env->ExceptionCheck() ? -5 : 1;
    }
    if (rc != 0) {
        // whisper returns non-zero both on abort and on real failures.
        const jboolean aborted = env->CallBooleanMethod(listener, cb.should_abort);
        if (env->ExceptionCheck()) return -5;
        return aborted == JNI_TRUE ? 1 : -6;
    }

    const int lang_id = whisper_full_lang_id(ctx);
    const char *lang_str = lang_id >= 0 ? whisper_lang_str(lang_id) : nullptr;
    if (lang_str != nullptr) {
        jstring jl = env->NewStringUTF(lang_str);  // ISO codes are ASCII
        env->CallVoidMethod(listener, on_language, jl);
        env->DeleteLocalRef(jl);
        if (env->ExceptionCheck()) return -5;
    }
    return 0;
}

}  // extern "C"
