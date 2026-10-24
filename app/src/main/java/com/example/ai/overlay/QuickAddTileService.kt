package com.example.ai.overlay

import android.app.PendingIntent
import android.os.Build
import android.service.quicksettings.TileService

class QuickAddTileService : TileService() {

    override fun onClick() {
        super.onClick()
        if (OverlayPermissionHelper.canDrawOverlays(this)) {
            if (isLocked) {
                unlockAndRun { QuickAddOverlayService.start(this) }
            } else {
                QuickAddOverlayService.start(this)
            }
            return
        }

        val permissionIntent = OverlayPermissionHelper.buildPermissionIntent(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pendingIntent = PendingIntent.getActivity(
                this, 0, permissionIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            startActivityAndCollapse(pendingIntent)
        } else {
            @Suppress("DEPRECATION", "StartActivityAndCollapseDeprecated")
            startActivityAndCollapse(permissionIntent)
        }
    }
}
