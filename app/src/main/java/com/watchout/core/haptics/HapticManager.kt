package com.watchout.core.haptics

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.VibratorManager

/**
 * Gere os padrões de vibração háptica.
 * Padrões: informação, atenção, perigo, erro.
 */
class HapticManager(private val context: Context) {

    private val vibrator by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            manager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
        }
    }

    var isEnabled = true

    /** 1 pulso curto — informação */
    fun vibrateInfo() {
        if (!isEnabled) return
        vibrate(longArrayOf(0, 80))
    }

    /** 2 pulsos — atenção */
    fun vibrateAttention() {
        if (!isEnabled) return
        vibrate(longArrayOf(0, 100, 100, 100))
    }

    /** 3 pulsos rápidos — perigo */
    fun vibrateDanger() {
        if (!isEnabled) return
        vibrate(longArrayOf(0, 150, 80, 150, 80, 150))
    }

    /** 1 pulso longo — erro */
    fun vibrateError() {
        if (!isEnabled) return
        vibrate(longArrayOf(0, 500))
    }

    private fun vibrate(pattern: LongArray) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(pattern, -1)
        }
    }
}
