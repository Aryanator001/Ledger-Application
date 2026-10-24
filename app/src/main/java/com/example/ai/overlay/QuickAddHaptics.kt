package com.example.ai.overlay

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/** Three distinct feedback signatures for the Quick Add pill's lifecycle events. */
object QuickAddHaptics {

    fun onOpen(context: Context) = vibrate(context, 20)

    fun onCommit(context: Context) = vibrate(context, longArrayOf(0, 20, 60, 30))

    fun onError(context: Context) = vibrate(context, longArrayOf(0, 40, 40, 40, 40, 40))

    private fun vibrator(context: Context): Vibrator =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

    private fun vibrate(context: Context, durationMs: Long) {
        val v = vibrator(context)
        if (!v.hasVibrator()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(durationMs)
        }
    }

    private fun vibrate(context: Context, pattern: LongArray) {
        val v = vibrator(context)
        if (!v.hasVibrator()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(VibrationEffect.createWaveform(pattern, -1))
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(pattern, -1)
        }
    }
}
