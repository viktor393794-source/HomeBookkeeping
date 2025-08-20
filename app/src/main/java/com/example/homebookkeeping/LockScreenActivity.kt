package com.example.homebookkeeping

import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import java.util.concurrent.Executor

class LockScreenActivity : AppCompatActivity() {

    private lateinit var indicators: List<ImageView>
    private var enteredPin = ""
    private var savedPin: String? = null

    private lateinit var executor: Executor
    private lateinit var biometricPrompt: BiometricPrompt
    private lateinit var promptInfo: BiometricPrompt.PromptInfo

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_lock_screen)

        indicators = listOf(
            findViewById(R.id.indicator1),
            findViewById(R.id.indicator2),
            findViewById(R.id.indicator3),
            findViewById(R.id.indicator4)
        )

        savedPin = getSavedPin()
        // Если пин-кода нет, значит защита не установлена. Сразу разблокируем.
        if (savedPin == null) {
            unlock()
            return
        }

        setupKeypad()
        setupBiometrics()
    }

    private fun setupKeypad() {
        val buttonIds = listOf(R.id.button0, R.id.button1, R.id.button2, R.id.button3, R.id.button4, R.id.button5, R.id.button6, R.id.button7, R.id.button8, R.id.button9)
        buttonIds.forEachIndexed { index, id ->
            findViewById<Button>(id).setOnClickListener { onNumberClick(index) }
        }
        findViewById<ImageButton>(R.id.buttonBackspace).setOnClickListener { onBackspaceClick() }
    }

    private fun onNumberClick(number: Int) {
        if (enteredPin.length < 4) {
            enteredPin += number
            updateIndicators()
            if (enteredPin.length == 4) {
                checkPin()
            }
        }
    }

    private fun onBackspaceClick() {
        if (enteredPin.isNotEmpty()) {
            enteredPin = enteredPin.dropLast(1)
            updateIndicators()
        }
    }

    private fun updateIndicators() {
        for (i in indicators.indices) {
            val drawableId = if (i < enteredPin.length) R.drawable.ic_pin_indicator_filled else R.drawable.ic_pin_indicator_empty
            indicators[i].setImageDrawable(ContextCompat.getDrawable(this, drawableId))
        }
    }

    private fun checkPin() {
        if (enteredPin == savedPin) {
            unlock()
        } else {
            Toast.makeText(this, "Неверный пин-код", Toast.LENGTH_SHORT).show()
            enteredPin = ""
            updateIndicators()
        }
    }

    private fun getSavedPin(): String? {
        return try {
            val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
            val sharedPreferences: SharedPreferences = EncryptedSharedPreferences.create(
                SettingsActivity.ENCRYPTED_PREFS_NAME, masterKeyAlias, this,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
            sharedPreferences.getString(SettingsActivity.KEY_PASSCODE, null)
        } catch (e: Exception) {
            null
        }
    }

    private fun setupBiometrics() {
        executor = ContextCompat.getMainExecutor(this)
        biometricPrompt = BiometricPrompt(this, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    unlock()
                }
            })

        promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Вход в приложение")
            .setSubtitle("Используйте отпечаток пальца для входа")
            .setNegativeButtonText("Использовать пин-код")
            .build()

        val biometricButton: ImageButton = findViewById(R.id.buttonBiometrics)
        val sharedPreferences = getSharedPreferences(SettingsActivity.PREFS_NAME, MODE_PRIVATE)
        val useBiometrics = sharedPreferences.getBoolean(SettingsActivity.KEY_BIOMETRICS_ENABLED, true)
        val hasBiometricsFeature = packageManager.hasSystemFeature(PackageManager.FEATURE_FINGERPRINT)

        if (useBiometrics && hasBiometricsFeature) {
            biometricButton.visibility = View.VISIBLE
            biometricButton.setOnClickListener { biometricPrompt.authenticate(promptInfo) }
            // Автоматически показываем диалог биометрии при входе на экран
            biometricPrompt.authenticate(promptInfo)
        } else {
            biometricButton.visibility = View.GONE
        }
    }

    private fun unlock() {
        // Устанавливаем результат OK, чтобы предыдущий экран знал, что вход успешен
        setResult(RESULT_OK)
        finish() // Закрываем экран блокировки
    }

    override fun onBackPressed() {
        // Запрещаем закрывать экран блокировки кнопкой "назад"
        // Вместо этого, "сворачиваем" приложение, чтобы пользователь не мог обойти защиту
        moveTaskToBack(true)
    }
}
