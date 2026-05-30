package com.example.ai.stt

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.core.content.ContextCompat

class RecordAudioPermissionState(
    val isGranted: () -> Boolean,
    val request: (onResult: (Boolean) -> Unit) -> Unit
)

@Composable
fun rememberRecordAudioPermissionState(context: Context): RecordAudioPermissionState {
    var pendingCallback: ((Boolean) -> Unit)? = null

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        pendingCallback?.invoke(granted)
        pendingCallback = null
    }

    return remember {
        RecordAudioPermissionState(
            isGranted = {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED
            },
            request = { onResult ->
                pendingCallback = onResult
                launcher.launch(Manifest.permission.RECORD_AUDIO)
            }
        )
    }
}
