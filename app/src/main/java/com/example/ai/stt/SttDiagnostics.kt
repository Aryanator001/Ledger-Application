package com.example.ai.stt

import android.util.Log
import com.example.BuildConfig
import java.util.concurrent.atomic.AtomicInteger

/** Temporary debug-only STT tracing. Never pass credentials, audio, or transcripts. */
internal object SttDiagnostics {
    private val attempt = AtomicInteger()
    fun begin() { attempt.incrementAndGet() }
    fun current(): Int = attempt.get()
    fun androidError(code: Int): String = when (code) {
        1 -> "NETWORK_TIMEOUT"
        2 -> "NETWORK"
        3 -> "AUDIO"
        4 -> "SERVER"
        5 -> "CLIENT"
        6 -> "SPEECH_TIMEOUT"
        7 -> "NO_MATCH"
        8 -> "RECOGNIZER_BUSY"
        9 -> "INSUFFICIENT_PERMISSIONS"
        10 -> "TOO_MANY_REQUESTS"
        11 -> "SERVER_DISCONNECTED"
        12 -> "LANGUAGE_NOT_SUPPORTED"
        13 -> "LANGUAGE_UNAVAILABLE"
        else -> "OTHER"
    }
    fun event(provider: String, event: String, trace: Int = current()) {
        if (BuildConfig.DEBUG) Log.d("LedgerSTT", "attempt=$trace provider=$provider $event")
    }
}
