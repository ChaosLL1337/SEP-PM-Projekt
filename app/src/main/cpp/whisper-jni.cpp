#include <jni.h>
#include <string>
#include <fstream>
#include <vector>
#include "whisper.h"

extern "C"
JNIEXPORT jstring JNICALL
Java_com_example_whisperdemo_MainActivity_transcribeAudio(
        JNIEnv* env,
        jobject /* this */,
        jstring pcmPath_,
        jstring modelPath_) {

    const char* pcmPath = env->GetStringUTFChars(pcmPath_, nullptr);
    const char* modelPath = env->GetStringUTFChars(modelPath_, nullptr);

    // Whisper Kontext laden
    struct whisper_context* ctx = whisper_init_from_file(modelPath);

    if (!ctx) {
        return env->NewStringUTF("Konnte Modell nicht laden");
    }

    // PCM-Daten laden
    std::ifstream file(pcmPath, std::ios::binary);
    std::vector<int16_t> pcm16;
    int16_t sample;
    while (file.read((char*)&sample, sizeof(int16_t))) {
        pcm16.push_back(sample);
    }

    // In float konvertieren
    std::vector<float> pcmf;
    pcmf.reserve(pcm16.size());
    for (auto s : pcm16) {
        pcmf.push_back(s / 32768.0f);
    }

    // Transkription ausführen
    whisper_full_params wparams = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    wparams.print_progress = false;
    wparams.print_realtime = false;
    wparams.print_special = false;

    if (whisper_full(ctx, wparams, pcmf.data(), pcmf.size()) != 0) {
        whisper_free(ctx);
        return env->NewStringUTF("Fehler beim Transkribieren");
    }

    std::string result;
    int n_segments = whisper_full_n_segments(ctx);
    for (int i = 0; i < n_segments; i++) {
        result += whisper_full_get_segment_text(ctx, i);
    }

    whisper_free(ctx);
    env->ReleaseStringUTFChars(pcmPath_, pcmPath);
    env->ReleaseStringUTFChars(modelPath_, modelPath);

    return env->NewStringUTF(result.c_str());
}
