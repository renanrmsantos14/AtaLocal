#include <jni.h>
#include <android/log.h>
#include <llama.h>
#include <algorithm>
#include <mutex>
#include <string>
#include <vector>

namespace {
constexpr const char *TAG = "AtaLocalLlama";
constexpr uint32_t CONTEXT_TOKENS = 4096;
constexpr uint32_t BATCH_TOKENS = 512;
constexpr uint32_t UBATCH_TOKENS = 512;
std::mutex generation_mutex;

void throw_illegal_state(JNIEnv *env, const char *message) {
    env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), message);
}

void ensure_backend_initialized() {
    static std::once_flag initialized;
    std::call_once(initialized, [] { llama_backend_init(); });
}

bool decode_prompt_in_batches(llama_context *context, std::vector<llama_token> &tokens) {
    for (size_t offset = 0; offset < tokens.size(); offset += BATCH_TOKENS) {
        const auto count = static_cast<int32_t>(std::min<size_t>(BATCH_TOKENS, tokens.size() - offset));
        llama_batch batch = llama_batch_get_one(tokens.data() + offset, count);
        if (llama_decode(context, batch) != 0) {
            __android_log_print(ANDROID_LOG_ERROR, TAG,
                "Falha ao decodificar prompt no bloco %zu (%d tokens)", offset / BATCH_TOKENS + 1, count);
            return false;
        }
    }
    return true;
}

std::string format_chat_prompt(const llama_model *model, const char *prompt) {
    const char *template_name = llama_model_chat_template(model, nullptr);
    if (!template_name) return prompt;
    const llama_chat_message messages[] = {
        {"system", "Você é um assistente factual. Não invente informações."},
        {"user", prompt}
    };
    const int32_t required = llama_chat_apply_template(template_name, messages, 2, true, nullptr, 0);
    if (required <= 0) return prompt;
    std::vector<char> buffer(static_cast<size_t>(required) + 1, '\0');
    const int32_t written = llama_chat_apply_template(template_name, messages, 2, true, buffer.data(), static_cast<int32_t>(buffer.size()));
    if (written <= 0) return prompt;
    return std::string(buffer.data(), static_cast<size_t>(written));
}
}

extern "C" JNIEXPORT jstring JNICALL
Java_br_com_betinhos_atalocal_summarization_LlamaNative_generate(
    JNIEnv *env, jclass, jstring model_path, jstring prompt, jint max_tokens) {
    std::lock_guard<std::mutex> generation_lock(generation_mutex);
    ensure_backend_initialized();
    if (model_path == nullptr || prompt == nullptr) {
        throw_illegal_state(env, "Caminho do modelo ou prompt da ata ausente.");
        return nullptr;
    }
    const char *model = env->GetStringUTFChars(model_path, nullptr);
    const char *prompt_utf = env->GetStringUTFChars(prompt, nullptr);
    if (model == nullptr || prompt_utf == nullptr) {
        if (model != nullptr) env->ReleaseStringUTFChars(model_path, model);
        if (prompt_utf != nullptr) env->ReleaseStringUTFChars(prompt, prompt_utf);
        throw_illegal_state(env, "Não foi possível preparar o modelo da ata na memória.");
        return nullptr;
    }
    llama_model_params model_params = llama_model_default_params();
    __android_log_print(ANDROID_LOG_INFO, TAG, "Carregando modelo para geração da ata");
    llama_model *loaded = llama_model_load_from_file(model, model_params);
    if (!loaded) {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "Falha ao carregar modelo Llama: %s", model);
        throw_illegal_state(env, "Não foi possível carregar o modelo de ata. Verifique o arquivo instalado.");
        env->ReleaseStringUTFChars(model_path, model);
        env->ReleaseStringUTFChars(prompt, prompt_utf);
        return nullptr;
    }
    llama_context_params context_params = llama_context_default_params();
    context_params.n_ctx = CONTEXT_TOKENS;
    context_params.n_batch = BATCH_TOKENS;
    context_params.n_ubatch = UBATCH_TOKENS;
    __android_log_print(ANDROID_LOG_INFO, TAG,
        "Criando contexto: n_ctx=%u n_batch=%u n_ubatch=%u", CONTEXT_TOKENS, BATCH_TOKENS, UBATCH_TOKENS);
    llama_context *context = llama_init_from_model(loaded, context_params);
    if (!context) {
        throw_illegal_state(env, "Não foi possível iniciar o contexto do modelo de ata.");
        llama_model_free(loaded);
        env->ReleaseStringUTFChars(model_path, model);
        env->ReleaseStringUTFChars(prompt, prompt_utf);
        return nullptr;
    }
    llama_sampler *sampler = llama_sampler_init_greedy();
    if (!sampler) {
        llama_free(context);
        llama_model_free(loaded);
        env->ReleaseStringUTFChars(model_path, model);
        env->ReleaseStringUTFChars(prompt, prompt_utf);
        throw_illegal_state(env, "Não foi possível iniciar o gerador da ata.");
        return nullptr;
    }
    const llama_vocab *vocab = llama_model_get_vocab(loaded);
    if (!vocab) {
        llama_sampler_free(sampler);
        llama_free(context);
        llama_model_free(loaded);
        env->ReleaseStringUTFChars(model_path, model);
        env->ReleaseStringUTFChars(prompt, prompt_utf);
        throw_illegal_state(env, "O modelo da ata não possui vocabulário válido.");
        return nullptr;
    }
    const std::string formatted_prompt = format_chat_prompt(loaded, prompt_utf);
    std::string output;
    const int32_t required_tokens = llama_tokenize(vocab, formatted_prompt.c_str(), -1, nullptr, 0, true, true);
    if (required_tokens >= 0) {
        llama_sampler_free(sampler);
        llama_free(context);
        llama_model_free(loaded);
        env->ReleaseStringUTFChars(model_path, model);
        env->ReleaseStringUTFChars(prompt, prompt_utf);
        throw_illegal_state(env, "Não foi possível tokenizar o prompt da ata.");
        return nullptr;
    }
    std::vector<llama_token> tokens(static_cast<size_t>(-required_tokens));
    const int32_t tokenized = llama_tokenize(vocab, formatted_prompt.c_str(), -1, tokens.data(), static_cast<int32_t>(tokens.size()), true, true);
    if (tokenized < 0) {
        llama_sampler_free(sampler);
        llama_free(context);
        llama_model_free(loaded);
        env->ReleaseStringUTFChars(model_path, model);
        env->ReleaseStringUTFChars(prompt, prompt_utf);
        throw_illegal_state(env, "Não foi possível tokenizar o prompt da ata.");
        return nullptr;
    }
    tokens.resize(static_cast<size_t>(tokenized));
    if (tokens.size() >= CONTEXT_TOKENS) {
        llama_sampler_free(sampler);
        llama_free(context);
        llama_model_free(loaded);
        env->ReleaseStringUTFChars(model_path, model);
        env->ReleaseStringUTFChars(prompt, prompt_utf);
        throw_illegal_state(env, "A transcrição é longa demais para gerar a ata neste modelo.");
        return nullptr;
    }
    __android_log_print(ANDROID_LOG_INFO, TAG, "Decodificando prompt com %zu tokens em lotes de %u", tokens.size(), BATCH_TOKENS);
    if (decode_prompt_in_batches(context, tokens)) {
        for (int i = 0; i < max_tokens; ++i) {
            llama_token token = llama_sampler_sample(sampler, context, -1);
            if (llama_vocab_is_eog(vocab, token)) break;
            char piece[256];
            const int length = llama_token_to_piece(vocab, token, piece, sizeof(piece), 0, true);
            if (length > 0) output.append(piece, length);
            llama_sampler_accept(sampler, token);
            llama_batch batch = llama_batch_get_one(&token, 1);
            if (llama_decode(context, batch) != 0) break;
        }
    } else {
        throw_illegal_state(env, "Não foi possível processar a transcrição para gerar a ata.");
    }
    llama_sampler_free(sampler);
    llama_free(context);
    llama_model_free(loaded);
    env->ReleaseStringUTFChars(model_path, model);
    env->ReleaseStringUTFChars(prompt, prompt_utf);
    if (env->ExceptionCheck()) return nullptr;
    return env->NewStringUTF(output.c_str());
}
