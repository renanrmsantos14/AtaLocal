#include <jni.h>
#include <llama.h>
#include <string>
#include <vector>

extern "C" JNIEXPORT jstring JNICALL
Java_br_com_betinhos_atalocal_summarization_LlamaNative_generate(
    JNIEnv *env, jclass, jstring model_path, jstring prompt, jint max_tokens) {
    const char *model = env->GetStringUTFChars(model_path, nullptr);
    const char *prompt_utf = env->GetStringUTFChars(prompt, nullptr);
    llama_model_params model_params = llama_model_default_params();
    llama_model *loaded = llama_model_load_from_file(model, model_params);
    if (!loaded) {
        env->ReleaseStringUTFChars(model_path, model);
        env->ReleaseStringUTFChars(prompt, prompt_utf);
        return env->NewStringUTF("{}");
    }
    llama_context_params context_params = llama_context_default_params();
    context_params.n_ctx = 4096;
    llama_context *context = llama_init_from_model(loaded, context_params);
    if (!context) {
        llama_model_free(loaded);
        env->ReleaseStringUTFChars(model_path, model);
        env->ReleaseStringUTFChars(prompt, prompt_utf);
        return env->NewStringUTF("{}");
    }
    llama_sampler *sampler = llama_sampler_init_greedy();
    const llama_vocab *vocab = llama_model_get_vocab(loaded);
    std::string output;
    std::vector<llama_token> tokens(static_cast<size_t>(llama_tokenize(vocab, prompt_utf, -1, nullptr, 0, true, true)));
    llama_tokenize(vocab, prompt_utf, -1, tokens.data(), static_cast<int32_t>(tokens.size()), true, true);
    llama_batch batch = llama_batch_get_one(tokens.data(), static_cast<int32_t>(tokens.size()));
    if (llama_decode(context, batch) == 0) {
        for (int i = 0; i < max_tokens; ++i) {
            llama_token token = llama_sampler_sample(sampler, context, -1);
            if (llama_vocab_is_eog(vocab, token)) break;
            char piece[256];
            const int length = llama_token_to_piece(vocab, token, piece, sizeof(piece), 0, true);
            if (length > 0) output.append(piece, length);
            llama_sampler_accept(sampler, token);
            batch = llama_batch_get_one(&token, 1);
            if (llama_decode(context, batch) != 0) break;
        }
    }
    llama_sampler_free(sampler);
    llama_free(context);
    llama_model_free(loaded);
    env->ReleaseStringUTFChars(model_path, model);
    env->ReleaseStringUTFChars(prompt, prompt_utf);
    return env->NewStringUTF(output.c_str());
}
