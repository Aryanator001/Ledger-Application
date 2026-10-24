package com.example.ai.overlay

import android.app.Activity
import android.os.Bundle
import android.widget.Toast
import com.example.R

/**
 * Invisible target for the "Quick Add" static App Shortcut. Exists only to decide between
 * starting the overlay and redirecting to the SYSTEM_ALERT_WINDOW settings screen —
 * QuickAddOverlayService itself cannot launch an Activity-only Settings intent reliably
 * from every launcher/OEM without an Activity already in the chain.
 */
class QuickAddShortcutLauncherActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (OverlayPermissionHelper.canDrawOverlays(this)) {
            QuickAddOverlayService.start(this)
        } else {
            Toast.makeText(this, getString(R.string.quick_add_overlay_permission_rationale), Toast.LENGTH_LONG).show()
            startActivity(OverlayPermissionHelper.buildPermissionIntent(this))
        }
        finish()
    }
}
