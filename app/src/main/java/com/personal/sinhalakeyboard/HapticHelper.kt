package com.personal.sinhalakeyboard

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.HapticFeedbackConstants
import android.view.View

object HapticHelper {

    fun keyTap(context: Context, source: View?) {
        if (!Prefs.isHapticEnabled(context)) return
        val view = source ?: return
        if (view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)) return
        vibrateFallback(context)
    }

    private fun vibrateFallback(context: Context) {
        val vibrator = context.getSystemService(Vibrator::class.java) ?: return
        if (!vibrator.hasVibrator()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(
                VibrationEffect.createOneShot(12, VibrationEffect.DEFAULT_AMPLITUDE),
            )
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(12)
        }
    }
}
