package com.example.homebookkeeping

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase

class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        // Используем задержку, чтобы показать экран-заставку
        Handler(Looper.getMainLooper()).postDelayed({

            // Проверяем, вошел ли пользователь в Firebase
            val currentUser = Firebase.auth.currentUser

            if (currentUser != null) {
                // Если пользователь авторизован, его нужно направить на экран выбора бюджета.
                // Это единственно правильный путь после входа.
                startActivity(Intent(this, BudgetSelectionActivity::class.java))
            } else {
                // Если пользователь не авторизован, отправляем его на экран входа.
                startActivity(Intent(this, LoginActivity::class.java))
            }

            // Закрываем SplashActivity, чтобы пользователь не мог на него вернуться.
            finish()
        }, 2000) // 2-секундная задержка
    }
}