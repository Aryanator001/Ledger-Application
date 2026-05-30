package com.example.ai.stt

import com.example.ai.state.FailureReason

/**
 * Abstraction for any STT (Speech-To-Text) provider.
 * Implementations must handle lifecycle cleanly: start → capture → stop/cancel → release.
 * Do not throw exceptions; report errors via callbacks.
 */
interface SttProvider {

    /**
     * Start listening with the given callbacks.
     * Implementation must initialize resources (microphone, network connection, etc.).
     * Callback delivery:
     *  - onListeningStarted() when audio capture begins
     *  - onPartial(text) with live partial results (optional, may be called 0+ times)
     *  - onFinal(text) when speech capture ends and final result is available
     *  - onError(reason) if anything fails
     *
     * Each call to start() must use fresh resources. If start() is called while already
     * listening, implementation is allowed to cancel the previous attempt and start fresh.
     */
    fun start(callbacks: SttCallbacks)

    /**
     * Stop listening gracefully.
     * The provider should finalize the current transcript and deliver onFinal(text) or onError().
     * After this call returns, the next start() call should work normally.
     */
    fun stop()

    /**
     * Cancel listening and discard current capture.
     * No final transcript should be delivered — just clean up resources.
     */
    fun cancel()

    /**
     * Release all resources (audio session, network connection, etc.).
     * After this call, the provider is unusable until a new instance is created.
     */
    fun release()
}

/**
 * Callback interface for STT provider results.
 */
interface SttCallbacks {
    fun onListeningStarted()
    fun onPartial(text: String)
    fun onFinal(text: String)
    fun onError(reason: FailureReason)
}
