#include <jni.h>
#include <vector>
#include <string>
#include <memory>
#include <cstring>
#include <android/log.h>

extern "C" {
#include "whisper.h"   // aus whisper.cpp-Projekt
}

#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  "Whisper", __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "Whisper", __VA_ARGS__)

extern "C" JNIEXPORT jlong JNICALL
Java_com_example_tut2_whisper_WhisperBridge_init(
        JNIEnv* env, jobject /*thiz*/, jstring jModelPath) {
    const char* path = env->GetStringUTFChars(jModelPath, nullptr);
    auto* ctx = whisper_init_from_file(path);
    env->ReleaseStringUTFChars(jModelPath, path);
    return reinterpret_cast<jlong>(ctx);
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_tut2_whisper_WhisperBridge_transcribe(
        JNIEnv* env, jobject /*thiz*/, jlong ctxPtr, jshortArray jPcm16, jint sampleRate) {

    auto* ctx = reinterpret_cast<whisper_context*>(ctxPtr);
    if (!ctx) return env->NewStringUTF("");

    const jsize n = env->GetArrayLength(jPcm16);
    std::vector<short> pcm16(n);
    env->GetShortArrayRegion(jPcm16, 0, n, pcm16.data());

    // in float [-1..1] konvertieren
    std::vector<float> pcmf(n);
    for (int i = 0; i < n; ++i) pcmf[i] = pcm16[i] / 32768.0f;

    // Whisper erwartet 16 kHz. Falls dein AudioRecord nicht 16k liefert, vorher resamplen.
    // Hier vereinfachend: wir gehen von 16000 aus.
    struct whisper_full_params wparams = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    wparams.print_realtime   = false;
    wparams.print_progress   = false;
    wparams.print_timestamps = true;
    wparams.translate        = false; // true => Übersetzung ins Englische
    wparams.single_segment   = true;  // kurze Prompts → ein Segment reicht meist
    wparams.no_context       = true;

    int ret = whisper_full(ctx, wparams, pcmf.data(), pcmf.size());
    if (ret != 0) {
        LOGE("whisper_full failed: %d", ret);
        return env->NewStringUTF("");
    }

    // Resultat zusammensetzen
    std::string text;
    int n_segments = whisper_full_n_segments(ctx);
    for (int i = 0; i < n_segments; ++i) {
        const char* seg = whisper_full_get_segment_text(ctx, i);
        if (seg) {
            if (!text.empty()) text += ' ';
            text += seg;
        }
    }
    return env->NewStringUTF(text.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_tut2_whisper_WhisperBridge_free(
        JNIEnv*, jobject /*thiz*/, jlong ctxPtr) {
    auto* ctx = reinterpret_cast<whisper_context*>(ctxPtr);
    if (ctx) whisper_free(ctx);
}
