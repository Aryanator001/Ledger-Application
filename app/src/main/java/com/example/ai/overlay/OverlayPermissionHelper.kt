package com.example.ai.overlay

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

/**
 * SYSTEM_ALERT_WINDOW has no runtime permission dialog — the user must be sent to a
 * dedicated Settings screen to grant it manually.
 */
object OverlayPermissionHelper {

    fun canDrawOverlays(context: Context): Boolean = Settings.canDrawOverlays(context)

    fun buildPermissionIntent(context: Context): Intent {
        return Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
