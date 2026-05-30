package com.example.ai.stt

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.example.ai.state.FailureReason

class SpeechRecognizerManager(private val context: Context) {

    interface Callbacks {
        fun onListeningStarted()
        fun onPartial(text: String)
        fun onFinal(text: String)
        fun onError(reason: FailureReason)
    }

    private var recognizer: SpeechRecognizer? = null
    private var callbacks: Callbacks? = null

    val isAvailable: Boolean
        get() = SpeechRecognizer.isRecognitionAvailable(context)

    fun start(callbacks: Callbacks) {
        if (!isAvailable) {
            callbacks.onError(FailureReason.SttFailed)
            return
        }
        this.callbacks = callbacks

        // Always release a previous instance before creating a new one.
        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(buildListener())
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "en-IN")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        }

        recognizer?.startListening(intent)
    }

    /** Stop listening and treat current partial as final. */
    fun stop() {
        recognizer?.stopListening()
    }

    /** Cancel listening and discard current capture. */
    fun cancel() {
        recognizer?.cancel()
        callbacks = null
    }

    fun release() {
        recognizer?.destroy()
        recognizer = null
        callbacks = null
    }

    private fun buildListener() = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            callbacks?.onListeningStarted()
        }

        override fun onBeginningOfSpeech() {
            callbacks?.onPartial("")
        }

        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}

        override fun onError(error: Int) {
            val reason = mapErrorCode(error)
            callbacks?.onError(reason)
        }

        override fun onResults(results: Bundle?) {
            val list = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
            val text = list.firstOrNull().orEmpty()
            callbacks?.onFinal(text)
        }

        override fun onPartialResults(partialResults: Bundle?) {
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
