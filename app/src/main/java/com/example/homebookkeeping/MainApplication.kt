package com.example.homebookkeeping

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.Bundle
import java.util.concurrent.TimeUnit

class MainApplication : Application(), Application.ActivityLifecycleCallbacks {

    private var appStopTime: Long = -1
    // Флаг, который предотвращает "гонку состояний"
    private var isNavigatingToLockScreen = false

    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(this)
    }

    override fun onActivityStarted(activity: Activity) {
        // Исключаем служебные экраны, которые не должны вызывать блокировку
        if (activity is LockScreenActivity || activity is CreatePasscodeActivity || activity is SplashActivity) {
            appStopTime = -1
            return
        }

        // Если мы вернулись на обычный экран, сбрасываем флаг
        isNavigatingToLockScreen = false

        if (shouldShowLockScreen()) {
            // Устанавливаем флаг ПЕРЕД запуском экрана блокировки
            isNavigatingToLockScreen = true
            val intent = Intent(activity, LockScreenActivity::class.java)
            activity.startActivity(intent)
            // Важно: не сбрасываем таймер здесь, чтобы избежать цикла
            return
        }

        // Сбрасываем таймер только если блокировка не нужна
        appStopTime = -1
    }

    override fun onActivityStopped(activity: Activity) {
        // Устанавливаем таймер, только если мы НЕ переключаемся на экран блокировки намеренно
        if (!isNavigatingToLockScreen) {
            appStopTime = System.currentTimeMillis()
        }
    }

    private fun shouldShowLockScreen(): Boolean {
        val sharedPreferences = getSharedPreferences(SettingsActivity.PREFS_NAME, MODE_PRIVATE)
        val isSecurityEnabled = sharedPreferences.getBoolean(SettingsActivity.KEY_SECURITY_ENABLED, false)
        if (!isSecurityEnabled) {
            return false
        }

        if (appStopTime == -1L) {
            return false
        }

        val timeSinceStopped = System.currentTimeMillis() - appStopTime
        return timeSinceStopped > TimeUnit.SECONDS.toMillis(10)
    }

    // Неиспользуемые методы
    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivityResumed(activity: Activity) {}
    override fun onActivityPaused(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {}
}
