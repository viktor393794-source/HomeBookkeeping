package com.example.homebookkeeping

import android.content.Intent
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class SplashActivity : AppCompatActivity() {

    // Лаунчер для экрана блокировки, который ждет результат (успех или отмена)
    private val lockScreenLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            // Если пользователь успешно прошел аутентификацию, запускаем главный экран
            goToMainActivity()
        } else {
            // Если пользователь не прошел (например, свернул приложение), закрываем Splash
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        // Проверяем, нужно ли показывать экран блокировки
        val sharedPreferences = getSharedPreferences(SettingsActivity.PREFS_NAME, MODE_PRIVATE)
        val isSecurityEnabled = sharedPreferences.getBoolean(SettingsActivity.KEY_SECURITY_ENABLED, false)

        if (isSecurityEnabled) {
            // Если защита включена, запускаем LockScreenActivity и ждем результат
            lockScreenLauncher.launch(Intent(this, LockScreenActivity::class.java))
        } else {
            // Если защита выключена, просто переходим на главный экран
            goToMainActivity()
        }
    }

    private fun goToMainActivity() {
        startActivity(Intent(this, MainActivity::class.java))
        // Закрываем SplashActivity, чтобы пользователь не мог вернуться на него кнопкой "Назад"
        finish()
    }
}
