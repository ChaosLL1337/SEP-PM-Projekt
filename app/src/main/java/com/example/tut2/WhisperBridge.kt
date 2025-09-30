package com.example.tut2

object WhisperBridge {
    init {
        System.loadLibrary("whisper_jni")
    }

    /** Liefert reinen Text (Fallback) */
    external fun transcribeWav(modelPath: String, wavPath: String, lang: String): String

    /** Liefert JSON-Array von Segmenten: [{ "t0_ms": Long, "t1_ms": Long, "text": String }, ...] */
    external fun transcribeWavSegments(modelPath: String, wavPath: String, lang: String): String
}
