package com.example.ai.overlay

import android.content.Context
import android.os.Bundle
import android.service.voice.VoiceInteractionSession

/**
 * Triggered by the system assist gesture when this app is the default Assistant. Immediately
 * forwards to the same Quick Add overlay used by the QS Tile and home-screen shortcut, then
 * hides itself — no assistant UI is ever shown to the user.
 */
class QuickAddVoiceInteractionSession(context: Context) : VoiceInteractionSession(context) {

    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        if (OverlayPermissionHelper.canDrawOverlays(context)) {
            QuickAddOverlayService.start(context)
        }
        hide()
    }
}
