package com.example.homebookkeeping // Убедитесь, что здесь ваш правильный пакет

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase

class SplashActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        auth = Firebase.auth

        // Задержка в 2 секунды, чтобы показать вашу заставку
        Handler(Looper.getMainLooper()).postDelayed({
            // Проверяем, вошел ли пользователь в аккаунт
            val currentUser = auth.currentUser

            if (currentUser != null) {
                // Пользователь вошел. Теперь проверяем, включен ли PIN-код.
                val sharedPreferences = getSharedPreferences("settings", Context.MODE_PRIVATE)
                val isPinLockEnabled = sharedPreferences.getBoolean("is_pin_lock_enabled", false)

                if (isPinLockEnabled) {
                    // Если PIN включен, переходим на экран его ввода
                    startActivity(Intent(this, LockScreenActivity::class.java))
                } else {
                    // Если PIN выключен, переходим на главный экран
                    startActivity(Intent(this, MainActivity::class.java))
                }
            } else {
                // Пользователь не вошел в аккаунт, переходим на экран входа/регистрации
                startActivity(Intent(this, LoginActivity::class.java))
            }

            // Закрываем SplashActivity, чтобы пользователь не мог на него вернуться
            finish()
        }, 2000)
    }
}
