package com.example.ai.stt

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import com.example.ai.state.FailureReason

/**
 * Android's native SpeechRecognizer wrapped as an SttProvider.
 *
 * Language: en-IN primary + hi-IN secondary so Google's cloud STT scores both
 * English and Hindi phonemes correctly, which is the right model for Hinglish
 * code-switching (e.g. "paid 500 rupaye to Ramu cash mein").
 *
 * Continuation mode: after onResults() fires the recognizer restarts briefly.
 * If the user keeps talking their words are appended to the accumulated transcript.
 * If they stay silent, onError(NO_MATCH/TIMEOUT) fires and the full accumulated
 * text is delivered. A safety-net timer covers the rare case where no error fires.
 *
 * This eliminates the most common UX complaint — the transcript cutting off mid-thought
 * when the user pauses briefly between words or corrects themselves.
 */
class AndroidNativeSttProvider(private val context: Context) : SttProvider {

    private var recognizer: SpeechRecognizer? = null
    private var callbacks: SttCallbacks? = null
    private var isListening = false
    private var lastRecognitionEndTime = 0L
    private var sessionStartMs = 0L
    private val handler = Handler(Looper.getMainLooper())
    private var pendingStart: Runnable? = null

    // Continuation / accumulation state
    private val accumulatedText = StringBuilder()
    private var bestPartial = ""
    private var isContinuing = false
    private var continuationCount = 0
    private var finalizePending: Runnable? = null

    private companion object {
        const val AUDIO_FOCUS_GRACE_MS = 300L
        // How long to wait at most before forcing finalization (safety net).
        const val CONTINUATION_SAFETY_NET_MS = 4500L
        // Maximum number of silent restarts before giving up and delivering.
        const val MAX_CONTINUATIONS = 4
    }

    // -------------------------------------------------------------------------
    // SttProvider interface
    // -------------------------------------------------------------------------

    override fun start(callbacks: SttCallbacks) {
        pendingStart?.let { handler.removeCallbacks(it) }
        pendingStart = null

        if (isListening) cancel()

        // Fresh user-initiated start: reset accumulation.
        if (!isContinuing) {
            accumulatedText.clear()
            bestPartial = ""
            continuationCount = 0
            sessionStartMs = System.currentTimeMillis()
        }

        val doStart = Runnable {
            if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                callbacks.onError(FailureReason.SttFailed)
                return@Runnable
            }
            this.callbacks = callbacks
            isListening = true
            try {
                // Reuse existing recognizer: destroy/create cycles cause ERROR_CLIENT(5)
                // and ERROR_RECOGNIZER_BUSY(8).
                getOrCreateRecognizer().startListening(buildIntent())
            } catch (e: Exception) {
                isListening = false
                callbacks.onError(FailureReason.Unknown("STT initialization failed: ${e.message}"))
            }
        }

        val timeSinceLastEnd = System.currentTimeMillis() - lastRecognitionEndTime
        if (timeSinceLastEnd < AUDIO_FOCUS_GRACE_MS) {
            pendingStart = doStart
            handler.postDelayed(doStart, AUDIO_FOCUS_GRACE_MS - timeSinceLastEnd)
        } else {
            doStart.run()
        }
    }

    override fun stop() {
        pendingStart?.let { handler.removeCallbacks(it) }
        pendingStart = null
        cancelFinalizePending()

        // User explicitly stopped: deliver everything accumulated so far immediately.
        if (isContinuing || accumulatedText.isNotEmpty()) {
            isContinuing = false
            isListening = false
            try { recognizer?.cancel() } catch (_: Exception) {}
            deliverAccumulated()
            return
        }

        if (!isListening) return
        try {
            recognizer?.stopListening()
            // Don't flip isListening here; wait for onResults/onError callback.
        } catch (e: Exception) {
            isListening = false
            callbacks?.onError(FailureReason.Unknown("Stop failed: ${e.message}"))
        }
    }

    override fun cancel() {
        pendingStart?.let { handler.removeCallbacks(it) }
        pendingStart = null
        cancelFinalizePending()

        isContinuing = false
        accumulatedText.clear()
        bestPartial = ""
        continuationCount = 0
        isListening = false
        callbacks = null
        lastRecognitionEndTime = System.currentTimeMillis()
        try { recognizer?.cancel() } catch (_: Exception) {}
    }

    override fun release() {
        pendingStart?.let { handler.removeCallbacks(it) }
        pendingStart = null
        cancelFinalizePending()

        isContinuing = false
        accumulatedText.clear()
        bestPartial = ""
        continuationCount = 0
        isListening = false
        callbacks = null
        try { recognizer?.destroy() } catch (_: Exception) {}
        recognizer = null
    }

    // -------------------------------------------------------------------------
    // Intent builder
    // -------------------------------------------------------------------------

    private fun buildIntent(): Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)

        // en-IN as primary handles Hinglish code-switching.
        // hi-IN as secondary ensures Hindi phonemes (diya, hazaar, kitna) are scored by the
        // cloud model — without this, pure Hindi words get badly mangled.
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "en-IN")
        putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", arrayOf("hi-IN"))

        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false)
        // 3 alternatives allow confidence-based best-pick instead of always taking first.
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)

        // Extend silence detection windows. Default is ~800ms; 1500ms gives users
        // more time to pause and think before Android auto-finalizes.
        putExtra("android.speech.extra.SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS", 200L)
        putExtra("android.speech.extra.SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS", 250L)
        putExtra("android.speech.extra.SPEECH_INPUT_MINIMUM_LENGTH_MILLIS", 300L)
    }

    // -------------------------------------------------------------------------
    // Recognition listener
    // -------------------------------------------------------------------------

    private fun getOrCreateRecognizer(): SpeechRecognizer {
        return recognizer ?: SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(buildListener())
        }.also { recognizer = it }
    }

    private fun buildListener() = object : RecognitionListener {

        override fun onReadyForSpeech(params: Bundle?) {
            if (isListening) callbacks?.onListeningStarted()
        }

        override fun onBeginningOfSpeech() {
            if (!isListening) return
            // User started speaking during the continuation window.
            // Cancel the safety-net so we don't finalize while they're mid-sentence.
            cancelFinalizePending()
        }

        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}

        override fun onError(error: Int) {
            if (!isListening) return
            isListening = false
            lastRecognitionEndTime = System.currentTimeMillis()

            if (error == SpeechRecognizer.ERROR_CLIENT || error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) {
                try { recognizer?.destroy() } catch (_: Exception) {}
                recognizer = null
            }

            // In continuation mode, no-match / timeout means the user went silent.
            // Deliver the accumulated transcript from earlier segments.
            if (isContinuing && (error == SpeechRecognizer.ERROR_NO_MATCH ||
                        error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT)) {
                cancelFinalizePending()
                deliverAccumulated()
                return
            }

            val totalMs = System.currentTimeMillis() - sessionStartMs
            Log.d("AiPipelineTiming", "stt=androidNative total=${totalMs}ms result=error($error)")
            callbacks?.onError(mapErrorCode(error))
        }

        override fun onResults(results: Bundle?) {
            if (!isListening) return
            isListening = false
            lastRecognitionEndTime = System.currentTimeMillis()

            val text = pickBestResult(results)

            if (text.isNotBlank()) {
                if (accumulatedText.isNotEmpty()) accumulatedText.append(" ")
                accumulatedText.append(text)
                bestPartial = ""
                // Push the full accumulated text so the UI stays live during the gap.
                callbacks?.onPartial(accumulatedText.toString())
            }

            scheduleContinuation()
        }

        override fun onPartialResults(partialResults: Bundle?) {
            if (!isListening) return
            val text = partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull().orEmpty()
            if (text.isNotBlank()) {
                bestPartial = text
                // Display accumulated + live partial as one line.
                val display = buildString {
                    if (accumulatedText.isNotEmpty()) {
                        append(accumulatedText)
                        append(" ")
                    }
                    append(text)
                }
                callbacks?.onPartial(display)
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    // -------------------------------------------------------------------------
    // Continuation logic
    // -------------------------------------------------------------------------

    /**
     * After a result arrives, restart listening to catch continuation speech.
     * The safety-net timer fires if onError() never comes (abnormal; rare).
     * Normal path: user stays silent → onError(NO_MATCH/TIMEOUT) → deliverAccumulated().
     * Normal path: user speaks again → onBeginningOfSpeech cancels safety-net → new result.
     */
    private fun scheduleContinuation() {
        cancelFinalizePending()

        if (continuationCount >= MAX_CONTINUATIONS || callbacks == null) {
            deliverAccumulated()
            return
        }
        continuationCount++
        isContinuing = true

        val safetyNet = Runnable { deliverAccumulated() }
        finalizePending = safetyNet
        handler.postDelayed(safetyNet, CONTINUATION_SAFETY_NET_MS)

        val timeSinceLastEnd = System.currentTimeMillis() - lastRecognitionEndTime
        val restartDelay = (AUDIO_FOCUS_GRACE_MS - timeSinceLastEnd).coerceAtLeast(50L)

        handler.postDelayed({
            if (!isContinuing) return@postDelayed
            if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                cancelFinalizePending()
                deliverAccumulated()
                return@postDelayed
            }
            isListening = true
            try {
                getOrCreateRecognizer().startListening(buildIntent())
            } catch (e: Exception) {
                isListening = false
                cancelFinalizePending()
                deliverAccumulated()
            }
        }, restartDelay)
    }

    /**
     * Deliver accumulated transcript and reset continuation state.
     * Guarded so double-calls (safety-net + error callback) are no-ops.
     *
     * Cancels any running recognizer session before delivering. Without this,
     * the safety-net can fire while the continuation recognizer is still active
     * (isListening = true). The recognizer then fires ERROR_SPEECH_TIMEOUT after
     * delivery, passes the !isListening guard, and overrides QueryAnswered with
     * "No voice heard" because isContinuing is already false by that point.
     */
    private fun deliverAccumulated() {
        if (!isContinuing && accumulatedText.isEmpty() && bestPartial.isEmpty()) return

        val final = accumulatedText.toString().trim().ifEmpty { bestPartial.trim() }
        isContinuing = false
        continuationCount = 0
        accumulatedText.clear()
        bestPartial = ""

        // Kill any active session so stale callbacks can't fire after this point.
        if (isListening) {
            isListening = false
            try { recognizer?.cancel() } catch (_: Exception) {}
        }

        val totalMs = System.currentTimeMillis() - sessionStartMs
        if (final.isNotBlank()) {
            Log.d("AiPipelineTiming", "stt=androidNative total=${totalMs}ms result=ok")
            callbacks?.onFinal(final)
        } else {
            Log.d("AiPipelineTiming", "stt=androidNative total=${totalMs}ms result=emptyTranscript")
            callbacks?.onError(FailureReason.EmptyTranscript)
        }
    }

    private fun cancelFinalizePending() {
        finalizePending?.let { handler.removeCallbacks(it) }
        finalizePending = null
    }

    // -------------------------------------------------------------------------
    // Result picking
    // -------------------------------------------------------------------------

    /**
     * Choose the best from EXTRA_MAX_RESULTS alternatives.
     * Prefers confidence scores; falls back to longest result; falls back to bestPartial.
     */
    private fun pickBestResult(results: Bundle?): String {
        val alternatives = results
            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?.filter { it.isNotBlank() }
            ?: emptyList()

        if (alternatives.isEmpty()) return bestPartial.trim()

        val confidences = results?.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)

        return if (confidences != null && confidences.isNotEmpty()) {
            alternatives.zip(confidences.toList())
                .maxByOrNull { (_, conf) -> conf }
                ?.first ?: alternatives.first()
        } else {
            alternatives.maxByOrNull { it.length } ?: alternatives.first()
        }
    }

    // -------------------------------------------------------------------------
    // Error mapping
    // -------------------------------------------------------------------------

    private fun mapErrorCode(code: Int): FailureReason = when (code) {
        SpeechRecognizer.ERROR_NETWORK,
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> FailureReason.NetworkError("STT network error")
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> FailureReason.SttPermissionDenied
        SpeechRecognizer.ERROR_NO_MATCH,
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> FailureReason.EmptyTranscript
        else -> FailureReason.SttFailed
    }
}
