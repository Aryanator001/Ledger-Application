package com.example.ai.overlay

import android.content.Intent
import android.speech.RecognitionService
import android.speech.SpeechRecognizer

/**
 * Stub required only because [voice_interaction_service] must reference a valid
 * `recognitionService`. Our own Quick Add flow uses `DualSttProvider` directly and never
 * exercises this class. If another app's `SpeechRecognizer` call happens to get routed here,
 * fail fast rather than hang.
 */
class QuickAddRecognitionService : RecognitionService() {

    override fun onStartListening(recognizerIntent: Intent?, listener: Callback) {
        listener.error(SpeechRecognizer.ERROR_CLIENT)
    }

    override fun onCancel(listener: Callback) = Unit

    override fun onStopListening(listener: Callback) = Unit
}
