#include <jni.h>
#include <android/log.h>
#include <whisper.h>
#include <fstream>
#include <sstream>
#include <vector>

namespace {
constexpr const char *TAG = "AtaLocalWhisper";

std::vector<float> read_pcm16_wav(const char *path) {
    std::ifstream file(path, std::ios::binary);
    if (!file) return {};
    file.seekg(44, std::ios::beg);
    std::vector<int16_t> samples((std::istreambuf_iterator<char>(file)), {});
    std::vector<float> audio;
    audio.reserve(samples.size() / 2);
    for (size_t i = 0; i + 1 < samples.size(); i += 2) {
        const auto value = static_cast<int16_t>(static_cast<uint8_t>(samples[i]) |
            (static_cast<uint16_t>(static_cast<uint8_t>(samples[i + 1])) << 8));
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
    if (!context || samples.empty()) {
        if (context) whisper_free(context);
        env->ReleaseStringUTFChars(model_path, model);
        env->ReleaseStringUTFChars(audio_path, audio_path_utf);
        env->ReleaseStringUTFChars(language, language_utf);
        return env->NewStringUTF("[]");
    }

    whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.language = language_utf;
    params.print_progress = false;
    params.print_realtime = false;
    params.print_timestamps = false;
    std::string result = "[";
    if (whisper_full(context, params, samples.data(), static_cast<int>(samples.size())) == 0) {
        for (int index = 0; index < whisper_full_n_segments(context); ++index) {
            if (index > 0) result += ",";
            result += "{\"start_ms\":" + std::to_string(whisper_full_get_segment_t0(context, index) * 10);
            result += ",\"end_ms\":" + std::to_string(whisper_full_get_segment_t1(context, index) * 10);
            result += ",\"text\":\"" + json_escape(whisper_full_get_segment_text(context, index)) + "\"}";
        }
    }
    result += "]";
    whisper_free(context);
    env->ReleaseStringUTFChars(model_path, model);
    env->ReleaseStringUTFChars(audio_path, audio_path_utf);
    env->ReleaseStringUTFChars(language, language_utf);
    return env->NewStringUTF(result.c_str());
}
