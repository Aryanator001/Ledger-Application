package com.example.ai.overlay

import android.service.voice.VoiceInteractionService

/**
 * Manifest-registered anchor for the default-Assistant role. All trigger logic lives in
 * [QuickAddVoiceInteractionSession], reached via [QuickAddVoiceInteractionSessionService].
 */
class QuickAddVoiceInteractionService : VoiceInteractionService()
