package com.example.tut2

object WhisperBridge {
    init {
        System.loadLibrary("whisper_jni")
    }

    external fun transcribeWav(modelPath: String, wavPath: String, lang: String): String
    external fun transcribeWavSegments(modelPath: String, wavPath: String, lang: String): String
}
