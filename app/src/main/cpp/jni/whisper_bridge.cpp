#include <jni.h>
#include <string>
#include <vector>
#include <cstdint>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <android/log.h>

#include "whisper.h"  // via CMake include dirs

#define LOG_TAG "WhisperJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// --- WAV Reader: PCM 16-bit, mono ---
static bool read_wav_mono16(const char* path, std::vector<float>& out, int& sample_rate) {
    FILE* f = fopen(path, "rb");
    if (!f) { LOGE("WAV open failed: %s", path); return false; }

    auto rd32 = [&](uint32_t& v){
        unsigned char b[4];
        if (fread(b,1,4,f)!=4) return false;
        v=(uint32_t)b[0]|((uint32_t)b[1]<<8)|((uint32_t)b[2]<<16)|((uint32_t)b[3]<<24);
        return true;
    };
    auto rd16 = [&](uint16_t& v){
        unsigned char b[2];
        if (fread(b,1,2,f)!=2) return false;
        v=(uint16_t)b[0]|((uint16_t)b[1]<<8);
        return true;
    };
    auto skip=[&](size_t n){ return fseek(f,(long)n,SEEK_CUR)==0; };

    char riff[4]; if (fread(riff,1,4,f)!=4 || memcmp(riff,"RIFF",4)!=0){ fclose(f); LOGE("Not RIFF"); return false; }
    uint32_t riff_size; if (!rd32(riff_size)) { fclose(f); return false; }
    char wave[4]; if (fread(wave,1,4,f)!=4 || memcmp(wave,"WAVE",4)!=0){ fclose(f); LOGE("Not WAVE"); return false; }

    char fmt[4]; if (fread(fmt,1,4,f)!=4 || memcmp(fmt,"fmt ",4)!=0){ fclose(f); LOGE("No fmt"); return false; }
    uint32_t fmt_size; if (!rd32(fmt_size)) { fclose(f); return false; }
    uint16_t audio_format; if (!rd16(audio_format)) { fclose(f); return false; }
    uint16_t num_channels; if (!rd16(num_channels)) { fclose(f); return false; }
    uint32_t sampleRate;   if (!rd32(sampleRate))   { fclose(f); return false; }
    uint32_t byteRate;     if (!rd32(byteRate))     { fclose(f); return false; }
    uint16_t blockAlign;   if (!rd16(blockAlign))   { fclose(f); return false; }
    uint16_t bitsPerSample;if (!rd16(bitsPerSample)){ fclose(f); return false; }
    if (fmt_size > 16) { if (!skip(fmt_size-16)) { fclose(f); return false; } }

    if (audio_format!=1 || num_channels!=1 || bitsPerSample!=16) {
        fclose(f); LOGE("Need PCM mono 16-bit"); return false;
    }

    char chunk_id[4]; uint32_t chunk_size=0; bool found=false;
    while (fread(chunk_id,1,4,f)==4) {
        if (!rd32(chunk_size)) { fclose(f); return false; }
        if (memcmp(chunk_id,"data",4)==0) { found=true; break; }
        if (!skip(chunk_size)) { fclose(f); return false; }
    }
    if (!found) { fclose(f); LOGE("No data chunk"); return false; }

    size_t n_samples = chunk_size/2;
    std::vector<int16_t> pcm(n_samples);
    size_t read = fread(pcm.data(),2,n_samples,f);
    fclose(f);
    if (read!=n_samples) { LOGE("WAV read mismatch"); return false; }

    out.resize(n_samples);
    const float k = 1.0f/32768.0f;
    for (size_t i=0;i<n_samples;++i) out[i] = (float)pcm[i] * k;
    sample_rate = (int)sampleRate;
    return true;
}

// --- JNI: simple transcription ---
extern "C"
JNIEXPORT jstring JNICALL
Java_com_example_tut2_WhisperBridge_transcribeWav(
        JNIEnv* env, jobject /*thiz*/,
        jstring jModelPath, jstring jWavPath, jstring jLang) {

    const char* modelPath = env->GetStringUTFChars(jModelPath, nullptr);
    const char* wavPath   = env->GetStringUTFChars(jWavPath,   nullptr);
    const char* lang      = env->GetStringUTFChars(jLang,      nullptr);

    whisper_context_params cparams = whisper_context_default_params();
    whisper_context* ctx = whisper_init_from_file_with_params(modelPath, cparams);
    if (!ctx) {
        env->ReleaseStringUTFChars(jModelPath, modelPath);
        env->ReleaseStringUTFChars(jWavPath,   wavPath);
        env->ReleaseStringUTFChars(jLang,      lang);
        return env->NewStringUTF("[whisper] Kontext konnte nicht erstellt werden");
    }

    std::vector<float> samples; int sr=0;
    if (!read_wav_mono16(wavPath, samples, sr)) {
        whisper_free(ctx);
        env->ReleaseStringUTFChars(jModelPath, modelPath);
        env->ReleaseStringUTFChars(jWavPath,   wavPath);
        env->ReleaseStringUTFChars(jLang,      lang);
        return env->NewStringUTF("[whisper] WAV konnte nicht gelesen werden (16-bit/mono)");
    }

    whisper_full_params wparams = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    wparams.print_realtime=false; wparams.print_progress=false;
    wparams.print_timestamps=false; wparams.print_special=false;
    wparams.translate=false; wparams.no_context=true; wparams.single_segment=false;
    wparams.language=lang; wparams.n_threads=4;

    if (whisper_full(ctx, wparams, samples.data(), (int)samples.size()) != 0) {
        whisper_free(ctx);
        env->ReleaseStringUTFChars(jModelPath, modelPath);
        env->ReleaseStringUTFChars(jWavPath,   wavPath);
        env->ReleaseStringUTFChars(jLang,      lang);
        return env->NewStringUTF("[whisper] Transkription fehlgeschlagen");
    }

    std::string text;
    const int n = whisper_full_n_segments(ctx);
    for (int i=0;i<n;++i) {
        const char* seg = whisper_full_get_segment_text(ctx, i);
        if (seg && *seg) { if (!text.empty()) text+=' '; text+=seg; }
    }
    if (text.empty()) text="[whisper] Kein Text erkannt";

    whisper_free(ctx);
    env->ReleaseStringUTFChars(jModelPath, modelPath);
    env->ReleaseStringUTFChars(jWavPath,   wavPath);
    env->ReleaseStringUTFChars(jLang,      lang);
    return env->NewStringUTF(text.c_str());
}

// --- JNI: segmentation with timestamps ---
extern "C"
JNIEXPORT jstring JNICALL
Java_com_example_tut2_WhisperBridge_transcribeWavSegments(
        JNIEnv* env, jobject /*thiz*/,
        jstring jModelPath, jstring jWavPath, jstring jLang) {

    const char* modelPath = env->GetStringUTFChars(jModelPath, nullptr);
    const char* wavPath   = env->GetStringUTFChars(jWavPath,   nullptr);
    const char* lang      = env->GetStringUTFChars(jLang,      nullptr);

    whisper_context_params cparams = whisper_context_default_params();
    whisper_context* ctx = whisper_init_from_file_with_params(modelPath, cparams);
    if (!ctx) {
        env->ReleaseStringUTFChars(jModelPath, modelPath);
        env->ReleaseStringUTFChars(jWavPath,   wavPath);
        env->ReleaseStringUTFChars(jLang,      lang);
        return env->NewStringUTF("[]");
    }

    std::vector<float> samples; int sr=0;
    if (!read_wav_mono16(wavPath, samples, sr)) {
        whisper_free(ctx);
        env->ReleaseStringUTFChars(jModelPath, modelPath);
        env->ReleaseStringUTFChars(jWavPath,   wavPath);
        env->ReleaseStringUTFChars(jLang,      lang);
        return env->NewStringUTF("[]");
    }

    whisper_full_params wparams = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    wparams.print_realtime=false; wparams.print_progress=false;
    wparams.print_timestamps=true; wparams.print_special=false;
    wparams.translate=false; wparams.no_context=true; wparams.single_segment=false;
    wparams.language=lang; wparams.n_threads=4;

    if (whisper_full(ctx, wparams, samples.data(), (int)samples.size()) != 0) {
        whisper_free(ctx);
        env->ReleaseStringUTFChars(jModelPath, modelPath);
        env->ReleaseStringUTFChars(jWavPath,   wavPath);
        env->ReleaseStringUTFChars(jLang,      lang);
        return env->NewStringUTF("[]");
    }

    // JSON bauen: [{"t0_ms":..., "t1_ms":..., "text":"..."}]
    std::string json="[";
    const int n = whisper_full_n_segments(ctx);
    for (int i=0;i<n;++i) {
        const char* seg = whisper_full_get_segment_text(ctx, i);
        const int64_t t0_ms = (int64_t)(1000.0 * whisper_full_get_segment_t0(ctx, i) * 0.01);
        const int64_t t1_ms = (int64_t)(1000.0 * whisper_full_get_segment_t1(ctx, i) * 0.01);

        if (i) json += ",";
        json += "{\"t0_ms\":";
        json += std::to_string((long long)t0_ms);
        json += ",\"t1_ms\":";
        json += std::to_string((long long)t1_ms);
        json += ",\"text\":\"";

        if (seg) {
            for (const char* p=seg; *p; ++p) {
                char c=*p;
                if (c=='"'||c=='\\') { json.push_back('\\'); json.push_back(c); }
                else if (c=='\n'||c=='\r') { /* skip */ }
                else { json.push_back(c); }
            }
        }
        json += "\"}";
    }
    json += "]";

    whisper_free(ctx);
    env->ReleaseStringUTFChars(jModelPath, modelPath);
    env->ReleaseStringUTFChars(jWavPath,   wavPath);
    env->ReleaseStringUTFChars(jLang,      lang);
    return env->NewStringUTF(json.c_str());
}
