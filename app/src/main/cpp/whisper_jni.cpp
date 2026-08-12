// Baton JNI bridge to whisper.cpp.
//
// Surface (M2-T3 + M2-T4):
//   nativeLoad(modelPath: String, nThreads: Int): Long
//   nativeTranscribe(handle: Long, pcmBytes: ByteArray, sampleRate: Int): String
//   nativeGetLastEvalMs(handle: Long): Long
//   nativeFree(handle: Long)
//
// Scope: thin JNI layer. Whisper.cpp's full API (params, contexts,
// language, translate) is exposed at the C++ level but the M2
// surface is intentionally minimal — load, transcribe PCM, free.
// M3+ can add more knobs (language hint, beam size, vad) without
// touching the Kotlin facade.
//
// M2-T3 vendor strategy: the whisper.cpp source tree is fetched
// by the Gradle `vendorWhisperCpp` task into
// app/src/main/cpp/whisper-cpp/, mirroring how M1's `vendorLlamaCpp`
// task handles llama.cpp. The CMake add_subdirectory() pulls in
// the `whisper` static lib; the JNI .cpp links against it.
//
// Lifecycle:
//   - nativeLoad reads the ggml-tiny.en.bin model, allocates a
//     whisper_context, returns a non-zero handle.
//   - nativeTranscribe runs whisper_full on the supplied PCM
//     (16-bit little-endian, mono, 16 kHz typical). Returns the
//     concatenated segment text. Empty string on error.
//   - nativeGetLastEvalMs returns the wall-clock ms of the last
//     transcribe call (used by the UI to show "Transcribed in
//     1.4s").
//   - nativeFree tears down the context and frees the session.

#include <jni.h>
#include <string>
#include <vector>
#include <chrono>
#include <android/log.h>

#include "whisper.h"

#define WHISPER_LOG_TAG "BatonWhisper"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, WHISPER_LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, WHISPER_LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, WHISPER_LOG_TAG, __VA_ARGS__)

struct WhisperSession {
    whisper_context *ctx = nullptr;
    int n_threads = 4;
    long last_eval_ms = 0;
};

static std::string jstring_to_std(JNIEnv *env, jstring s) {
    if (s == nullptr) return std::string();
    const char *c = env->GetStringUTFChars(s, nullptr);
    std::string out(c == nullptr ? "" : c);
    if (c != nullptr) env->ReleaseStringUTFChars(s, c);
    return out;
}

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_baton_app_ai_whisper_WhisperBridge_nativeLoad(
    JNIEnv *env, jobject /*thiz*/,
    jstring model_path, jint n_threads) {

    std::string path = jstring_to_std(env, model_path);
    LOGI("nativeLoad: path=%s n_threads=%d", path.c_str(), n_threads);

    // whisper.cpp v1.8.x removed n_threads from whisper_context_params;
    // n_threads is now a whisper_full_params field set at decode time.
    // We keep the JNI signature for forward compat (the n_threads value
    // is captured here and used in nativeTranscribe).
    whisper_context_params cparams = whisper_context_default_params();
    cparams.use_gpu = false;  // M2: CPU only (GPU is M3+).

    whisper_context *ctx = whisper_init_from_file_with_params(path.c_str(), cparams);
    if (ctx == nullptr) {
        LOGE("whisper_init_from_file_with_params failed for %s", path.c_str());
        return 0;
    }

    auto *session = new WhisperSession();
    session->ctx = ctx;
    session->n_threads = static_cast<int>(n_threads);
    return reinterpret_cast<jlong>(session);
}

JNIEXPORT jstring JNICALL
Java_com_baton_app_ai_whisper_WhisperBridge_nativeTranscribe(
    JNIEnv *env, jobject /*thiz*/,
    jlong handle, jbyteArray pcm, jint sample_rate) {

    auto *session = reinterpret_cast<WhisperSession *>(handle);
    if (session == nullptr || session->ctx == nullptr) {
        return env->NewStringUTF("");
    }
    if (pcm == nullptr) {
        return env->NewStringUTF("");
    }
    jsize n_bytes = env->GetArrayLength(pcm);
    jbyte *bytes = env->GetByteArrayElements(pcm, nullptr);
    if (bytes == nullptr) {
        return env->NewStringUTF("");
    }
    // whisper expects float32 in [-1, 1]. Convert int16 little-endian.
    int n_samples = n_bytes / 2;
    std::vector<float> pcm_f32(n_samples);
    const int16_t *samples_i16 = reinterpret_cast<const int16_t *>(bytes);
    for (int i = 0; i < n_samples; ++i) {
        pcm_f32[i] = static_cast<float>(samples_i16[i]) / 32768.0f;
    }
    env->ReleaseByteArrayElements(pcm, bytes, JNI_ABORT);

    whisper_full_params wparams = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    wparams.print_progress = false;
    wparams.print_realtime = false;
    wparams.print_timestamps = false;
    wparams.translate = false;
    wparams.language = "en";
    wparams.n_threads = session->n_threads > 0 ? session->n_threads : 4;
    wparams.n_max_text_ctx = 0;  // no prompt prefix
    wparams.offset_ms = 0;

    auto t0 = std::chrono::steady_clock::now();
    int rc = whisper_full(session->ctx, wparams, pcm_f32.data(), n_samples);
    auto t1 = std::chrono::steady_clock::now();
    session->last_eval_ms =
        std::chrono::duration_cast<std::chrono::milliseconds>(t1 - t0).count();

    if (rc != 0) {
        LOGE("whisper_full failed: %d", rc);
        return env->NewStringUTF("");
    }

    // Concatenate all segment texts.
    int n_segments = whisper_full_n_segments(session->ctx);
    std::string out;
    for (int i = 0; i < n_segments; ++i) {
        const char *text = whisper_full_get_segment_text(session->ctx, i);
        if (text != nullptr) {
            out.append(text);
        }
    }
    return env->NewStringUTF(out.c_str());
}

JNIEXPORT jlong JNICALL
Java_com_baton_app_ai_whisper_WhisperBridge_nativeGetLastEvalMs(
    JNIEnv */*env*/, jobject /*thiz*/, jlong handle) {
    auto *session = reinterpret_cast<WhisperSession *>(handle);
    if (session == nullptr) return 0;
    return static_cast<jlong>(session->last_eval_ms);
}

JNIEXPORT void JNICALL
Java_com_baton_app_ai_whisper_WhisperBridge_nativeFree(
    JNIEnv */*env*/, jobject /*thiz*/, jlong handle) {
    auto *session = reinterpret_cast<WhisperSession *>(handle);
    if (session == nullptr) return;
    if (session->ctx != nullptr) whisper_free(session->ctx);
    delete session;
}

}  // extern "C"
