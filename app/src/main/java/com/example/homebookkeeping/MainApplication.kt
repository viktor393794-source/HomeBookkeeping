package com.example.homebookkeeping

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.Bundle
import com.google.firebase.FirebaseApp // <-- ВАЖНЫЙ ИМПОРТ
import java.util.concurrent.TimeUnit

class MainApplication : Application(), Application.ActivityLifecycleCallbacks {

    private var appStopTime: Long = -1
    private var isNavigatingToLockScreen = false

    override fun onCreate() {
        super.onCreate()
        // Принудительная инициализация Firebase при старте приложения
        FirebaseApp.initializeApp(this) // <-- ДОБАВЛЕНА ЭТА СТРОКА
        registerActivityLifecycleCallbacks(this)
    }

    override fun onActivityStarted(activity: Activity) {
        if (activity is LockScreenActivity || activity is CreatePasscodeActivity || activity is SplashActivity) {
            appStopTime = -1
            return
        }

        isNavigatingToLockScreen = false

        if (shouldShowLockScreen()) {
            isNavigatingToLockScreen = true
            val intent = Intent(activity, LockScreenActivity::class.java)
            activity.startActivity(intent)
            return
        }

        appStopTime = -1
    }

    override fun onActivityStopped(activity: Activity) {
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