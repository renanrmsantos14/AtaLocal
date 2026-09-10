#include <jni.h>
#include <android/log.h>
#include <whisper.h>
#include <fstream>
#include <iterator>
#include <cstdint>
#include <sstream>
#include <vector>

namespace {
constexpr const char *TAG = "AtaLocalWhisper";

std::vector<float> read_pcm16_wav(const char *path) {
    std::ifstream file(path, std::ios::binary);
    if (!file) return {};
    file.seekg(44, std::ios::beg);
    std::vector<uint8_t> bytes((std::istreambuf_iterator<char>(file)), {});
    std::vector<float> audio;
    audio.reserve(bytes.size() / 2);
    for (size_t i = 0; i + 1 < bytes.size(); i += 2) {
        const auto value = static_cast<int16_t>(bytes[i] | (static_cast<uint16_t>(bytes[i + 1]) << 8));
        audio.push_back(static_cast<float>(value) / 32768.0f);
    }
    return audio;
}

std::string json_escape(const std::string &value) {
    std::string escaped;
    for (const char character : value) {
        if (character == '\\' || character == '"') escaped += '\\';
        escaped += character;
    }
    return escaped;
}

void throw_illegal_state(JNIEnv *env, const char *message) {
    env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), message);
}
}

extern "C" JNIEXPORT jstring JNICALL
Java_br_com_betinhos_atalocal_transcription_WhisperNative_transcribeJson(
    JNIEnv *env, jclass, jstring model_path, jstring audio_path, jstring language) {
    const char *model = env->GetStringUTFChars(model_path, nullptr);
    const char *audio_path_utf = env->GetStringUTFChars(audio_path, nullptr);
    const char *language_utf = env->GetStringUTFChars(language, nullptr);
    const auto samples = read_pcm16_wav(audio_path_utf);

    whisper_context_params context_params = whisper_context_default_params();
    whisper_context *context = whisper_init_from_file_with_params(model, context_params);
    if (!context) {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "Falha ao carregar modelo Whisper: %s", model);
        throw_illegal_state(env, "Não foi possível carregar o modelo Whisper. Verifique o arquivo instalado.");
        env->ReleaseStringUTFChars(model_path, model);
        env->ReleaseStringUTFChars(audio_path, audio_path_utf);
        env->ReleaseStringUTFChars(language, language_utf);
        return nullptr;
    }
    if (samples.empty()) {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "Áudio WAV vazio ou inválido: %s", audio_path_utf);
        whisper_free(context);
        throw_illegal_state(env, "O áudio está vazio ou em formato inválido.");
        env->ReleaseStringUTFChars(model_path, model);
        env->ReleaseStringUTFChars(audio_path, audio_path_utf);
        env->ReleaseStringUTFChars(language, language_utf);
        return nullptr;
    }

    whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.language = language_utf;
    params.print_progress = false;
    params.print_realtime = false;
    params.print_timestamps = false;
    std::string result = "[";
    const auto result_code = whisper_full(context, params, samples.data(), static_cast<int>(samples.size()));
    if (result_code == 0) {
        for (int index = 0; index < whisper_full_n_segments(context); ++index) {
            if (index > 0) result += ",";
            result += "{\"start_ms\":" + std::to_string(whisper_full_get_segment_t0(context, index) * 10);
            result += ",\"end_ms\":" + std::to_string(whisper_full_get_segment_t1(context, index) * 10);
            result += ",\"text\":\"" + json_escape(whisper_full_get_segment_text(context, index)) + "\"}";
        }
    } else {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "whisper_full falhou com código %d", result_code);
        whisper_free(context);
        env->ReleaseStringUTFChars(model_path, model);
        env->ReleaseStringUTFChars(audio_path, audio_path_utf);
        env->ReleaseStringUTFChars(language, language_utf);
        throw_illegal_state(env, "O Whisper não conseguiu processar este áudio.");
        return nullptr;
    }
    result += "]";
    whisper_free(context);
    env->ReleaseStringUTFChars(model_path, model);
    env->ReleaseStringUTFChars(audio_path, audio_path_utf);
    env->ReleaseStringUTFChars(language, language_utf);
    return env->NewStringUTF(result.c_str());
}
