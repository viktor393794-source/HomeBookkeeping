package com.example.homebookkeeping

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

object HapticFeedbackHelper {

    fun viberate(context: Context) {
        // --- НОВАЯ ПРОВЕРКА ---
        // Получаем доступ к SharedPreferences
        val sharedPreferences = context.getSharedPreferences(SettingsActivity.PREFS_NAME, Context.MODE_PRIVATE)
        // Проверяем, включена ли вибрация. Если нет - выходим из функции.
        val isVibrationEnabled = sharedPreferences.getBoolean(SettingsActivity.KEY_VIBRATION_ENABLED, true)
        if (!isVibrationEnabled) {
            return
        }
        // --- КОНЕЦ ПРОВЕРКИ ---

        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(50)
        }
    }
}