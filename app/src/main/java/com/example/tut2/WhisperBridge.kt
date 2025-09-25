package com.example.tut2.whisper

object WhisperBridge {
    init { System.loadLibrary("whisper_bridge") } // Name der .so aus CMake
    @JvmStatic external fun init(modelPath: String): Long
    @JvmStatic external fun transcribe(ctxPtr: Long, pcm16: ShortArray, sampleRate: Int): String
    @JvmStatic external fun free(ctxPtr: Long)
}
