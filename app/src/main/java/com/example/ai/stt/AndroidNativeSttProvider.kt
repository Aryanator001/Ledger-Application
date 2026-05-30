package com.example.ai.stt

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.example.ai.state.FailureReason

/**
 * Android's native SpeechRecognizer wrapped as an SttProvider.
 * Configured for en-IN locale to handle Hinglish code-switching.
 */
class AndroidNativeSttProvider(private val context: Context) : SttProvider {

    private var recognizer: SpeechRecognizer? = null
    private var callbacks: SttCallbacks? = null
    private var isListening = false
    private var lastRecognitionEndTime = 0L
    private val handler = Handler(Looper.getMainLooper())
    private var pendingStart: Runnable? = null

    private fun getOrCreateRecognizer(): SpeechRecognizer {
        return recognizer ?: SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(buildListener())
        }.also { recognizer = it }
    }

    override fun start(callbacks: SttCallbacks) {
        pendingStart?.let { handler.removeCallbacks(it) }
        pendingStart = null

        // If already listening from a previous incomplete stop(), cancel it first.
        if (isListening) {
            cancel()
        }

        val doStart = Runnable {
            if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                callbacks.onError(FailureReason.SttFailed)
                return@Runnable
            }

            this.callbacks = callbacks
            isListening = true

            try {
                // Reuse existing recognizer if possible. Destroy/create cycles 
                // frequently cause ERROR_CLIENT(5) or ERROR_RECOGNIZER_BUSY(8).
                val rec = getOrCreateRecognizer()

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "en-IN")
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                    putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
                }

                rec.startListening(intent)
            } catch (e: Exception) {
                isListening = false
                callbacks.onError(FailureReason.Unknown("STT initialization failed: ${e.message}"))
            }
        }

        // Audio focus settling guard: if the last recognition ended recently,
        // wait briefly to let Android's audio system fully release the session.
        val timeSinceLastEnd = System.currentTimeMillis() - lastRecognitionEndTime
        val gracePeriodMs = 300L
        if (timeSinceLastEnd < gracePeriodMs) {
            pendingStart = doStart
            handler.postDelayed(doStart, gracePeriodMs - timeSinceLastEnd)
        } else {
            doStart.run()
        }
    }

    override fun stop() {
        pendingStart?.let { handler.removeCallbacks(it) }
        pendingStart = null

        if (!isListening) return
        try {
            recognizer?.stopListening()
            // Don't set isListening = false here; wait for callback.
            // The callback (onResults or onError) will finalize the state.
        } catch (e: Exception) {
            isListening = false
            callbacks?.onError(FailureReason.Unknown("Stop failed: ${e.message}"))
        }
    }

    override fun cancel() {
        pendingStart?.let { handler.removeCallbacks(it) }
        pendingStart = null

        isListening = false
        callbacks = null
        lastRecognitionEndTime = System.currentTimeMillis()
        try {
            recognizer?.cancel()
        } catch (e: Exception) {
            // Silently ignore; we're cleaning up anyway.
        }
    }

    override fun release() {
        pendingStart?.let { handler.removeCallbacks(it) }
        pendingStart = null

        isListening = false
        callbacks = null
        try {
            recognizer?.destroy()
        } catch (e: Exception) {
            // Silently ignore; cleanup.
        }
        recognizer = null
    }

    private fun buildListener() = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            if (isListening) callbacks?.onListeningStarted()
        }

        override fun onBeginningOfSpeech() {
            if (isListening) callbacks?.onPartial("")
        }

        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}

        override fun onError(error: Int) {
            if (!isListening) return  // Stale callback; ignore.
            isListening = false
            lastRecognitionEndTime = System.currentTimeMillis()

            // ERROR_CLIENT(5) or ERROR_RECOGNIZER_BUSY(8) can leave the service in a bad state.
            // If we get these, destroy the recognizer so it is fully recreated next time.
            if (error == SpeechRecognizer.ERROR_CLIENT || error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) {
                try { recognizer?.destroy() } catch (e: Exception) {}
                recognizer = null
            }

            val reason = mapErrorCode(error)
            callbacks?.onError(reason)
        }

        override fun onResults(results: Bundle?) {
            if (!isListening) return  // Stale callback; ignore.
            isListening = false
            lastRecognitionEndTime = System.currentTimeMillis()
            val list = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
            val text = list.firstOrNull().orEmpty()
            callbacks?.onFinal(text)
        }

        override fun onPartialResults(partialResults: Bundle?) {
            if (!isListening) return  // Stale callback; ignore.
            val list = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
            val text = list.firstOrNull().orEmpty()
            if (text.isNotBlank()) callbacks?.onPartial(text)
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    private fun mapErrorCode(code: Int): FailureReason = when (code) {
        SpeechRecognizer.ERROR_NETWORK,
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> FailureReason.NetworkError("STT network error")
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> FailureReason.SttPermissionDenied
        SpeechRecognizer.ERROR_NO_MATCH,
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> FailureReason.EmptyTranscript
        else -> FailureReason.SttFailed
    }
}

