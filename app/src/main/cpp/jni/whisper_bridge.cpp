#include <jni.h>
#include <string>
#include <vector>
#include <cstdint>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <android/log.h>

#include "whisper.cpp/whisper.h"

#define LOG_TAG "WhisperJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// --- Kleiner, robuster WAV-Reader (nur PCM 16-bit, mono, 16 kHz) ----------------

static bool read_wav_mono16(const char* path, std::vector<float>& out, int& sample_rate) {
    FILE* f = fopen(path, "rb");
    if (!f) { LOGE("WAV open failed: %s", path); return false; }

    auto rd32 = [&](uint32_t& v) {
        unsigned char b[4];
        if (fread(b, 1, 4, f) != 4) return false;
        v = (uint32_t)b[0] | ((uint32_t)b[1] << 8) | ((uint32_t)b[2] << 16) | ((uint32_t)b[3] << 24);
        return true;
    };
    auto rd16 = [&](uint16_t& v) {
        unsigned char b[2];
        if (fread(b, 1, 2, f) != 2) return false;
        v = (uint16_t)b[0] | ((uint16_t)b[1] << 8);
        return true;
    };
    auto skip = [&](size_t n) { return fseek(f, (long)n, SEEK_CUR) == 0; };

    // RIFF header
    char riff[4]; if (fread(riff,1,4,f)!=4 || memcmp(riff,"RIFF",4)!=0) { fclose(f); LOGE("Not RIFF"); return false; }
    uint32_t riff_size; if (!rd32(riff_size)) { fclose(f); return false; }
    char wave[4]; if (fread(wave,1,4,f)!=4 || memcmp(wave,"WAVE",4)!=0) { fclose(f); LOGE("Not WAVE"); return false; }

    // fmt chunk
    char fmt[4]; if (fread(fmt,1,4,f)!=4 || memcmp(fmt,"fmt ",4)!=0) { fclose(f); LOGE("No fmt chunk"); return false; }
    uint32_t fmt_size; if (!rd32(fmt_size)) { fclose(f); return false; }

    uint16_t audio_format; if (!rd16(audio_format)) { fclose(f); return false; }
    uint16_t num_channels; if (!rd16(num_channels)) { fclose(f); return false; }
    uint32_t sampleRate;   if (!rd32(sampleRate))   { fclose(f); return false; }
    uint32_t byteRate;     if (!rd32(byteRate))     { fclose(f); return false; }
    uint16_t blockAlign;   if (!rd16(blockAlign))   { fclose(f); return false; }
    uint16_t bitsPerSample;if (!rd16(bitsPerSample)){ fclose(f); return false; }

    // ggf. rest vom fmt-Chunk überspringen (erweiterte Header)
    if (fmt_size > 16) {
        if (!skip(fmt_size - 16)) { fclose(f); return false; }
    }

    if (audio_format != 1)       { fclose(f); LOGE("Only PCM supported"); return false; }
    if (num_channels != 1)       { fclose(f); LOGE("Only mono supported"); return false; }
    if (bitsPerSample != 16)     { fclose(f); LOGE("Only 16-bit supported"); return false; }

    // data chunk finden (es kann noch andere Chunks geben)
    char chunk_id[4];
    uint32_t chunk_size = 0;
    bool found_data = false;
    while (fread(chunk_id,1,4,f)==4) {
        if (!rd32(chunk_size)) { fclose(f); return false; }
        if (memcmp(chunk_id,"data",4)==0) { found_data = true; break; }
        // anderen Chunk überspringen
        if (!skip(chunk_size)) { fclose(f); return false; }
    }
    if (!found_data) { fclose(f); LOGE("No data chunk"); return false; }

    // PCM-Daten lesen
    size_t n_samples = chunk_size / 2; // 16-bit
    std::vector<int16_t> pcm(n_samples);
    size_t read = fread(pcm.data(), 2, n_samples, f);
    fclose(f);
    if (read != n_samples) { LOGE("WAV read size mismatch"); return false; }

    // in float [-1,1] konvertieren
    out.resize(n_samples);
    const float scale = 1.0f / 32768.0f;
    for (size_t i = 0; i < n_samples; ++i) {
        out[i] = (float)pcm[i] * scale;
    }

    sample_rate = (int)sampleRate;
    return true;
}

// --- JNI: modelPath, wavPath, lang ------------------------------------------

extern "C"
JNIEXPORT jstring JNICALL
Java_com_example_tut2_WhisperBridge_transcribeWav(
        JNIEnv* env, jobject /*thiz*/,
        jstring jModelPath, jstring jWavPath, jstring jLang) {

    const char* modelPath = env->GetStringUTFChars(jModelPath, nullptr);
    const char* wavPath   = env->GetStringUTFChars(jWavPath,   nullptr);
    const char* lang      = env->GetStringUTFChars(jLang,      nullptr);

    std::string result;

    // 1) Modell laden
    whisper_context_params cparams = whisper_context_default_params();
    // optional: GPU/OpenCL/Vulkan-Backends konfigurieren, falls vorhanden
    struct whisper_context* ctx = whisper_init_from_file_with_params(modelPath, cparams);
    if (!ctx) {
        result = "[whisper] Kontext konnte nicht erstellt werden";
        goto done;
    }

    // 2) WAV lesen
    std::vector<float> samples;
    int sr = 0;
    if (!read_wav_mono16(wavPath, samples, sr)) {
        result = "[whisper] WAV konnte nicht gelesen werden (erwarte 16-bit/mono)";
        whisper_free(ctx);
        goto done;
    }
    if (sr != 16000) {
        // whisper.cpp kann auch mit anderen sr umgehen, aber 16k ist optimal.
        LOGI("WAV sample rate = %d (Whisper erwartet 16000, wird resampled internal)", sr);
    }

    // 3) Params setzen
    whisper_full_params wparams = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    wparams.print_realtime   = false;
    wparams.print_progress   = false;
    wparams.print_timestamps = false;
    wparams.translate        = false;         // nicht übersetzen, nur transkribieren
    wparams.no_context       = true;          // schneller, kein KV-Cache reuse
    wparams.single_segment   = false;         // mehrere Segmente erlauben
    wparams.language         = lang;          // z. B. "de"

    // 4) Transkription
    {
        int rc = whisper_full(ctx, wparams, samples.data(), (int)samples.size());
        if (rc != 0) {
            result = "[whisper] Transkription fehlgeschlagen";
            whisper_free(ctx);
            goto done;
        }
    }

    // 5) Segmente einsammeln
    {
        const int n = whisper_full_n_segments(ctx);
        std::string text;
        text.reserve(4096);
        for (int i = 0; i < n; ++i) {
            const char* seg = whisper_full_get_segment_text(ctx, i);
            if (seg) {
                if (!text.empty()) text += ' ';
                text += seg;
            }
        }
        result = text.empty() ? std::string("[whisper] Kein Text erkannt") : text;
    }

    whisper_free(ctx);

done:
    env->ReleaseStringUTFChars(jModelPath, modelPath);
    env->ReleaseStringUTFChars(jWavPath,   wavPath);
    env->ReleaseStringUTFChars(jLang,      lang);
    return env->NewStringUTF(result.c_str());
}
