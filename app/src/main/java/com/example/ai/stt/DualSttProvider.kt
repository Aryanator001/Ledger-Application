package com.example.ai.stt

import android.content.Context
import com.example.ai.state.FailureReason

/**
 * Dual STT provider: Sarvam AI (primary) with AndroidNativeSttProvider (fallback).
 *
 * Start() always attempts Sarvam first. If Sarvam fires onError() due to a network
 * or API failure, DualSttProvider silently transitions to AndroidNativeSttProvider
 * for that same session. The caller's callbacks see a fresh onListeningStarted()
 * when the fallback kicks in, so the UI reacts normally.
 *
 * EmptyTranscript also triggers the fallback: Sarvam can return HTTP 200 with a
 * blank transcript (e.g. noise-only audio past its VAD threshold, or a short/odd
 * utterance its model can't transcribe) even though the mic genuinely captured
 * speech, so it's worth a second attempt via the on-device recognizer rather than
 * surfacing "No voice heard" immediately. Permission errors are still passed
 * through immediately without triggering the fallback.
 */
class DualSttProvider(context: Context) : SttProvider {

    private val sarvamProvider = SarvamSttProvider(context)
    private val androidProvider = AndroidNativeSttProvider(context)

    private enum class Phase { IDLE, SARVAM, FALLBACK }
    @Volatile private var phase = Phase.IDLE

    // -------------------------------------------------------------------------
    // SttProvider interface
    // -------------------------------------------------------------------------

    override fun start(callbacks: SttCallbacks) {
        phase = Phase.SARVAM
        sarvamProvider.start(sarvamCallbacks(callbacks))
    }

    override fun stop() {
        when (phase) {
            Phase.SARVAM -> sarvamProvider.stop()
            Phase.FALLBACK -> androidProvider.stop()
            Phase.IDLE -> {}
        }
    }

    override fun cancel() {
        sarvamProvider.cancel()
        androidProvider.cancel()
        phase = Phase.IDLE
    }

    override fun release() {
        sarvamProvider.release()
        androidProvider.release()
        phase = Phase.IDLE
    }

    // -------------------------------------------------------------------------
    // Callback wrappers
    // -------------------------------------------------------------------------

    private fun sarvamCallbacks(caller: SttCallbacks) = object : SttCallbacks {
        override fun onListeningStarted() = caller.onListeningStarted()
        override fun onPartial(text: String) = caller.onPartial(text)

        override fun onFinal(text: String) {
            phase = Phase.IDLE
            caller.onFinal(text)
        }

        override fun onError(reason: FailureReason) {
            // Fall back on network/API failures and empty transcripts — not on permissions.
            if (phase == Phase.SARVAM && reason.isFallbackCandidate()) {
                phase = Phase.FALLBACK
                androidProvider.start(fallbackCallbacks(caller))
            } else {
                phase = Phase.IDLE
                caller.onError(reason)
            }
        }
    }

    private fun fallbackCallbacks(caller: SttCallbacks) = object : SttCallbacks {
        override fun onListeningStarted() = caller.onListeningStarted()
        override fun onPartial(text: String) = caller.onPartial(text)

        override fun onFinal(text: String) {
            phase = Phase.IDLE
            caller.onFinal(text)
        }

        override fun onError(reason: FailureReason) {
            phase = Phase.IDLE
            caller.onError(reason)
        }
    }

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------

    private fun FailureReason.isFallbackCandidate(): Boolean =
        this is FailureReason.NetworkError ||
        this == FailureReason.SttFailed ||
        this == FailureReason.EmptyTranscript ||
        this is FailureReason.Unknown
}
