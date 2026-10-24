package com.example.ai.overlay

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build

/** Status + request-intent helpers for the default-Assistant role, mirroring [OverlayPermissionHelper]. */
object AssistantRoleHelper {

    fun isDefaultAssistant(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        val roleManager = context.getSystemService(RoleManager::class.java) ?: return false
        return roleManager.isRoleHeld(RoleManager.ROLE_ASSISTANT)
    }

    fun buildRequestRoleIntent(context: Context): Intent? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        val roleManager = context.getSystemService(RoleManager::class.java) ?: return null
        if (!roleManager.isRoleAvailable(RoleManager.ROLE_ASSISTANT)) return null
        return roleManager.createRequestRoleIntent(RoleManager.ROLE_ASSISTANT)
    }
}
