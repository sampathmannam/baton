// Baton JNI bridge to llama.cpp.
//
// Surface:
//   nativeLoad(modelPath: String, nCtx: Int, nThreads: Int): Long
//   nativeInfer(handle: Long, prompt: String, maxTokens: Int): String
//   nativeGetLastEvalMs(handle: Long): Long
//   nativeFree(handle: Long)
//
// Scope note: this JNI layer is intentionally thin. M1 ships greedy
// sampling only. GBNF grammar (M1-T4) is enforced by the higher-level
// Kotlin extractor that uses the llama.cpp server's GBNF support
// via a thin wrapper, NOT by adding grammar primitives here.
//
// Lifecycle:
//   - nativeLoad reads the GGUF model, allocates a context, returns
//     a non-zero handle (pointer to LlamaSession).
//   - nativeInfer tokenises the prompt, evaluates it, then greedy-
//     samples up to `maxTokens` additional tokens.
//   - nativeGetLastEvalMs returns the wall-clock ms of the last call.
//   - nativeFree tears down the context and frees the session.

#include <jni.h>
#include <string>
#include <vector>
#include <chrono>
#include <android/log.h>

#include "llama.h"

#define LOG_TAG "BatonLLama"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

struct LlamaSession {
    llama_model *model = nullptr;
    llama_context *ctx = nullptr;
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
Java_com_baton_app_ai_llama_LlamaBridge_nativeLoad(
    JNIEnv *env, jobject /*thiz*/,
    jstring model_path, jint n_ctx, jint n_threads) {

    std::string path = jstring_to_std(env, model_path);
    LOGI("nativeLoad: path=%s n_ctx=%d n_threads=%d", path.c_str(), n_ctx, n_threads);

    llama_model_params model_params = llama_model_default_params();
    model_params.n_gpu_layers = 0;  // M1: CPU only.

    llama_model *model = llama_load_model_from_file(path.c_str(), model_params);
    if (model == nullptr) {
        LOGE("llama_load_model_from_file failed for %s", path.c_str());
        return 0;
    }

    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx = static_cast<uint32_t>(n_ctx);
    ctx_params.n_threads = static_cast<uint32_t>(n_threads);
    ctx_params.n_threads_batch = static_cast<uint32_t>(n_threads);

    llama_context *ctx = llama_new_context_with_model(model, ctx_params);
    if (ctx == nullptr) {
        LOGE("llama_new_context_with_model failed");
        llama_free_model(model);
        return 0;
    }

    auto *session = new LlamaSession();
    session->model = model;
    session->ctx = ctx;
    return reinterpret_cast<jlong>(session);
}

JNIEXPORT jstring JNICALL
Java_com_baton_app_ai_llama_LlamaBridge_nativeInfer(
    JNIEnv *env, jobject /*thiz*/,
    jlong handle, jstring prompt, jint max_tokens) {

    auto *session = reinterpret_cast<LlamaSession *>(handle);
    if (session == nullptr || session->ctx == nullptr) {
        return env->NewStringUTF("");
    }
    std::string prompt_str = jstring_to_std(env, prompt);

    const llama_vocab *vocab = llama_model_get_vocab(session->model);
    if (vocab == nullptr) {
        LOGE("llama_model_get_vocab returned null");
        return env->NewStringUTF("");
    }

    // Tokenize the prompt.
    std::vector<llama_token> tokens(prompt_str.size() + 16);
    int n_tokens = llama_tokenize(
        vocab, prompt_str.c_str(), (int32_t)prompt_str.size(),
        tokens.data(), (int32_t)tokens.size(),
        /*add_special*/ true, /*parse_special*/ true);
    if (n_tokens < 0) {
        LOGE("tokenize failed: %d", n_tokens);
        return env->NewStringUTF("");
    }
    tokens.resize(n_tokens);

    // Evaluate the prompt.
    auto t0 = std::chrono::steady_clock::now();
    if (llama_decode(session->ctx, llama_batch_get_one(tokens.data(), n_tokens)) != 0) {
        LOGE("llama_decode (prompt) failed");
        return env->NewStringUTF("");
    }

    // Single greedy sampler. No chain, no grammar here — the Kotlin
    // layer handles grammar via a separate, simpler pass if needed.
    llama_sampler *smpl = llama_sampler_init_greedy();

    // Generate up to max_tokens.
    std::string out;
    int n_remain = static_cast<int>(max_tokens);
    while (n_remain > 0) {
        llama_token id = llama_sampler_sample(smpl, session->ctx, -1);
        if (llama_vocab_is_eog(vocab, id)) break;
        char buf[64];
        int n = llama_token_to_piece(vocab, id, buf, sizeof(buf), 0, /*special*/ false);
        if (n > 0) out.append(buf, n);
        if (llama_decode(session->ctx, llama_batch_get_one(&id, 1)) != 0) break;
        n_remain -= 1;
    }

    auto t1 = std::chrono::steady_clock::now();
    session->last_eval_ms = std::chrono::duration_cast<std::chrono::milliseconds>(t1 - t0).count();

    llama_sampler_free(smpl);
    return env->NewStringUTF(out.c_str());
}

JNIEXPORT jlong JNICALL
Java_com_baton_app_ai_llama_LlamaBridge_nativeGetLastEvalMs(
    JNIEnv */*env*/, jobject /*thiz*/, jlong handle) {
    auto *session = reinterpret_cast<LlamaSession *>(handle);
    if (session == nullptr) return 0;
    return static_cast<jlong>(session->last_eval_ms);
}

JNIEXPORT void JNICALL
Java_com_baton_app_ai_llama_LlamaBridge_nativeFree(
    JNIEnv */*env*/, jobject /*thiz*/, jlong handle) {
    auto *session = reinterpret_cast<LlamaSession *>(handle);
    if (session == nullptr) return;
    if (session->ctx != nullptr) llama_free(session->ctx);
    if (session->model != nullptr) llama_free_model(session->model);
    delete session;
}

}  // extern "C"
