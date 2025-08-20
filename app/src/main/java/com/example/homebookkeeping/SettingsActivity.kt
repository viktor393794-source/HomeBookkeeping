package com.example.homebookkeeping

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.switchmaterial.SwitchMaterial

class SettingsActivity : AppCompatActivity() {

    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var vibrationSwitch: SwitchMaterial
    private lateinit var securitySwitch: SwitchMaterial
    private lateinit var biometricsSwitch: SwitchMaterial // Новый переключатель
    private lateinit var changePinButton: TextView

    companion object {
        const val PREFS_NAME = "app_settings"
        const val KEY_VIBRATION_ENABLED = "vibration_enabled"
        const val KEY_SECURITY_ENABLED = "security_enabled"
        const val KEY_BIOMETRICS_ENABLED = "biometrics_enabled" // Новый ключ

        const val ENCRYPTED_PREFS_NAME = "secure_app_settings"
        const val KEY_PASSCODE = "passcode"
    }

    private val createPasscodeLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            saveSecuritySetting(true)
            updateUiState()
        } else {
            saveSecuritySetting(false)
            updateUiState()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        val toolbar: MaterialToolbar = findViewById(R.id.settingsToolbar)
        vibrationSwitch = findViewById(R.id.vibrationSwitch)
        securitySwitch = findViewById(R.id.securitySwitch)
        biometricsSwitch = findViewById(R.id.biometricsSwitch) // Находим новый переключатель
        changePinButton = findViewById(R.id.changePinButton)

        toolbar.setNavigationOnClickListener { finish() }

        loadSettings()
        setupListeners()
        updateUiState()
    }

    private fun loadSettings() {
        vibrationSwitch.isChecked = sharedPreferences.getBoolean(KEY_VIBRATION_ENABLED, true)
        securitySwitch.isChecked = sharedPreferences.getBoolean(KEY_SECURITY_ENABLED, false)
        // Загружаем настройку биометрии, по умолчанию включена, если доступна
        biometricsSwitch.isChecked = sharedPreferences.getBoolean(KEY_BIOMETRICS_ENABLED, true)
    }

    private fun setupListeners() {
        vibrationSwitch.setOnCheckedChangeListener { _, isChecked ->
            saveVibrationSetting(isChecked)
        }

        securitySwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                createPasscodeLauncher.launch(Intent(this, CreatePasscodeActivity::class.java))
            } else {
                clearPasscode()
                saveSecuritySetting(false)
                // Также сбрасываем настройку биометрии
                saveBiometricsSetting(false)
                updateUiState()
            }
        }

        // Слушатель для нового переключателя
        biometricsSwitch.setOnCheckedChangeListener { _, isChecked ->
            saveBiometricsSetting(isChecked)
        }

        changePinButton.setOnClickListener {
            createPasscodeLauncher.launch(Intent(this, CreatePasscodeActivity::class.java))
        }
    }

    private fun updateUiState() {
        val isSecurityEnabled = sharedPreferences.getBoolean(KEY_SECURITY_ENABLED, false)
        val hasBiometrics = packageManager.hasSystemFeature(PackageManager.FEATURE_FINGERPRINT)

        securitySwitch.isChecked = isSecurityEnabled
        changePinButton.isVisible = isSecurityEnabled

        // Показываем переключатель биометрии, только если защита включена И телефон поддерживает отпечаток
        biometricsSwitch.isVisible = isSecurityEnabled && hasBiometrics
    }

    private fun saveVibrationSetting(isEnabled: Boolean) {
        sharedPreferences.edit().putBoolean(KEY_VIBRATION_ENABLED, isEnabled).apply()
    }

    private fun saveSecuritySetting(isEnabled: Boolean) {
        sharedPreferences.edit().putBoolean(KEY_SECURITY_ENABLED, isEnabled).apply()
    }

    // Новая функция для сохранения настройки биометрии
    private fun saveBiometricsSetting(isEnabled: Boolean) {
        sharedPreferences.edit().putBoolean(KEY_BIOMETRICS_ENABLED, isEnabled).apply()
    }

    private fun clearPasscode() {
        try {
            val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
            val encryptedPrefs: SharedPreferences = EncryptedSharedPreferences.create(
                ENCRYPTED_PREFS_NAME, masterKeyAlias, this,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
            encryptedPrefs.edit().remove(KEY_PASSCODE).apply()
            Toast.makeText(this, "Защита отключена", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Ошибка отключения защиты", Toast.LENGTH_SHORT).show()
        }
    }
}
