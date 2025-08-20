package com.example.homebookkeeping

import android.Manifest
import android.app.AlarmManager
import android.app.AlertDialog
import android.app.DownloadManager
import android.app.PendingIntent
import android.app.TimePickerDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.isVisible
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.switchmaterial.SwitchMaterial
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.URL
import java.util.Calendar
import javax.net.ssl.HttpsURLConnection
import kotlin.concurrent.thread

class SettingsActivity : AppCompatActivity() {

    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var vibrationSwitch: SwitchMaterial
    private lateinit var securitySwitch: SwitchMaterial
    private lateinit var biometricsSwitch: SwitchMaterial
    private lateinit var changePinButton: TextView
    private lateinit var checkUpdateButton: TextView
    private lateinit var versionTextView: TextView
    // --- ИСПРАВЛЕНИЕ: Добавлены недостающие объявления ---
    private lateinit var remindersSwitch: SwitchMaterial
    private lateinit var reminderTimeButton: TextView

    companion object {
        const val PREFS_NAME = "app_settings"
        const val KEY_VIBRATION_ENABLED = "vibration_enabled"
        const val KEY_SECURITY_ENABLED = "security_enabled"
        const val KEY_BIOMETRICS_ENABLED = "biometrics_enabled"
        const val KEY_REMINDERS_ENABLED = "reminders_enabled"
        const val KEY_REMINDER_HOUR = "reminder_hour"
        const val KEY_REMINDER_MINUTE = "reminder_minute"
        const val ENCRYPTED_PREFS_NAME = "secure_app_settings"
        const val KEY_PASSCODE = "passcode"
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            showTimePicker()
        } else {
            Toast.makeText(this, "Разрешение на уведомления не предоставлено", Toast.LENGTH_SHORT).show()
            remindersSwitch.isChecked = false
        }
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
        biometricsSwitch = findViewById(R.id.biometricsSwitch)
        changePinButton = findViewById(R.id.changePinButton)
        checkUpdateButton = findViewById(R.id.checkUpdateButton)
        versionTextView = findViewById(R.id.versionTextView)
        // --- ИСПРАВЛЕНИЕ: Добавлены недостающие findViewById ---
        remindersSwitch = findViewById(R.id.remindersSwitch)
        reminderTimeButton = findViewById(R.id.reminderTimeButton)

        toolbar.setNavigationOnClickListener { finish() }

        loadSettings()
        setupListeners()
        updateUiState()
        displayAppVersion()
    }

    private fun loadSettings() {
        vibrationSwitch.isChecked = sharedPreferences.getBoolean(KEY_VIBRATION_ENABLED, true)
        securitySwitch.isChecked = sharedPreferences.getBoolean(KEY_SECURITY_ENABLED, false)
        biometricsSwitch.isChecked = sharedPreferences.getBoolean(KEY_BIOMETRICS_ENABLED, true)
        remindersSwitch.isChecked = sharedPreferences.getBoolean(KEY_REMINDERS_ENABLED, false)
        updateReminderTimeText()
    }

    private fun setupListeners() {
        vibrationSwitch.setOnCheckedChangeListener { _, isChecked -> saveVibrationSetting(isChecked) }
        securitySwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                createPasscodeLauncher.launch(Intent(this, CreatePasscodeActivity::class.java))
            } else {
                clearPasscode()
                saveSecuritySetting(false)
                saveBiometricsSetting(false)
                updateUiState()
            }
        }
        biometricsSwitch.setOnCheckedChangeListener { _, isChecked -> saveBiometricsSetting(isChecked) }
        changePinButton.setOnClickListener { createPasscodeLauncher.launch(Intent(this, CreatePasscodeActivity::class.java)) }
        checkUpdateButton.setOnClickListener { checkForUpdates() }

        remindersSwitch.setOnCheckedChangeListener { _, isChecked ->
            sharedPreferences.edit().putBoolean(KEY_REMINDERS_ENABLED, isChecked).apply()
            if (isChecked) {
                checkNotificationPermission()
            } else {
                cancelNotification()
                Toast.makeText(this, "Напоминания отключены", Toast.LENGTH_SHORT).show()
            }
            updateUiState()
        }
        reminderTimeButton.setOnClickListener { showTimePicker() }
    }

    private fun updateUiState() {
        val isSecurityEnabled = sharedPreferences.getBoolean(KEY_SECURITY_ENABLED, false)
        val hasBiometrics = packageManager.hasSystemFeature(PackageManager.FEATURE_FINGERPRINT)
        val areRemindersEnabled = sharedPreferences.getBoolean(KEY_REMINDERS_ENABLED, false)

        securitySwitch.isChecked = isSecurityEnabled
        changePinButton.isVisible = isSecurityEnabled
        biometricsSwitch.isVisible = isSecurityEnabled && hasBiometrics
        reminderTimeButton.isVisible = areRemindersEnabled
    }

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                showTimePicker()
            } else {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            showTimePicker()
        }
    }

    private fun showTimePicker() {
        val hour = sharedPreferences.getInt(KEY_REMINDER_HOUR, 20)
        val minute = sharedPreferences.getInt(KEY_REMINDER_MINUTE, 0)

        TimePickerDialog(this, { _, selectedHour, selectedMinute ->
            sharedPreferences.edit()
                .putInt(KEY_REMINDER_HOUR, selectedHour)
                .putInt(KEY_REMINDER_MINUTE, selectedMinute)
                .apply()
            scheduleNotification(selectedHour, selectedMinute)
            updateReminderTimeText()
        }, hour, minute, true).show()
    }

    private fun scheduleNotification(hour: Int, minute: Int) {
        val intent = Intent(applicationContext, NotificationReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            applicationContext, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            if (before(Calendar.getInstance())) {
                add(Calendar.DATE, 1)
            }
        }

        alarmManager.setInexactRepeating(
            AlarmManager.RTC_WAKEUP,
            calendar.timeInMillis,
            AlarmManager.INTERVAL_DAY,
            pendingIntent
        )
        Toast.makeText(this, "Напоминание установлено на ${String.format("%02d:%02d", hour, minute)}", Toast.LENGTH_SHORT).show()
    }

    private fun cancelNotification() {
        val intent = Intent(applicationContext, NotificationReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            applicationContext, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE
        )
        if (pendingIntent != null) {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.cancel(pendingIntent)
        }
    }

    private fun updateReminderTimeText() {
        val hour = sharedPreferences.getInt(KEY_REMINDER_HOUR, 20)
        val minute = sharedPreferences.getInt(KEY_REMINDER_MINUTE, 0)
        reminderTimeButton.text = "Время напоминания: ${String.format("%02d:%02d", hour, minute)}"
    }

    private fun checkForUpdates() {
        Toast.makeText(this, "Проверка обновлений...", Toast.LENGTH_SHORT).show()

        thread {
            try {
                val url = URL("https://api.github.com/repos/YOUR_USERNAME/YOUR_REPOSITORY/releases/latest")
                val connection = url.openConnection() as HttpsURLConnection
                connection.requestMethod = "GET"
                connection.setRequestProperty("Accept", "application/vnd.github.v3+json")

                if (connection.responseCode == HttpsURLConnection.HTTP_OK) {
                    val reader = BufferedReader(InputStreamReader(connection.inputStream))
                    val response = reader.readText()
                    reader.close()

                    val jsonObject = JSONObject(response)
                    val latestVersionName = jsonObject.getString("tag_name").removePrefix("v")
                    val assets = jsonObject.getJSONArray("assets")
                    var downloadUrl: String? = null
                    if (assets.length() > 0) {
                        for (i in 0 until assets.length()) {
                            val asset = assets.getJSONObject(i)
                            if (asset.getString("name").endsWith(".apk")) {
                                downloadUrl = asset.getString("browser_download_url")
                                break
                            }
                        }
                    }

                    val pInfo: PackageInfo = packageManager.getPackageInfo(packageName, 0)
                    val currentVersionName = pInfo.versionName

                    runOnUiThread {
                        if (downloadUrl != null && isNewerVersion(latestVersionName, currentVersionName)) {
                            showUpdateDialog(latestVersionName, downloadUrl)
                        } else {
                            Toast.makeText(this, "У вас установлена последняя версия.", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    runOnUiThread {
                        Toast.makeText(this, "Ошибка проверки. Код: ${connection.responseCode}", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    Toast.makeText(this, "Ошибка сети: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun showUpdateDialog(versionName: String, downloadUrl: String) {
        AlertDialog.Builder(this)
            .setTitle("Доступно обновление")
            .setMessage("Новая версия: $versionName. Хотите скачать и установить?")
            .setPositiveButton("Обновить") { _, _ ->
                downloadAndInstallApk(downloadUrl)
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun downloadAndInstallApk(apkUrl: String) {
        val downloadManager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val request = DownloadManager.Request(Uri.parse(apkUrl))
            .setTitle("Обновление Свиной бюджет")
            .setDescription("Скачивание новой версии...")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "app-release.apk")

        val destinationFile = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "app-release.apk")
        if (destinationFile.exists()) {
            destinationFile.delete()
        }

        val downloadId = downloadManager.enqueue(request)

        val onComplete = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == DownloadManager.ACTION_DOWNLOAD_COMPLETE) {
                    val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                    if (id == downloadId) {
                        val fileUri: Uri = FileProvider.getUriForFile(
                            this@SettingsActivity,
                            "${applicationContext.packageName}.provider",
                            destinationFile
                        )
                        val installIntent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(fileUri, "application/vnd.android.package-archive")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        startActivity(installIntent)
                        unregisterReceiver(this)
                    }
                }
            }
        }
        registerReceiver(onComplete, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
    }

    private fun isNewerVersion(latestVersion: String, currentVersion: String): Boolean { return latestVersion > currentVersion }

    private fun displayAppVersion() {
        try {
            val pInfo: PackageInfo = packageManager.getPackageInfo(packageName, 0)
            val version = pInfo.versionName
            versionTextView.text = "Версия $version"
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
            versionTextView.text = "Версия не найдена"
        }
    }

    private fun saveVibrationSetting(isEnabled: Boolean) {
        sharedPreferences.edit().putBoolean(KEY_VIBRATION_ENABLED, isEnabled).apply()
    }

    private fun saveSecuritySetting(isEnabled: Boolean) {
        sharedPreferences.edit().putBoolean(KEY_SECURITY_ENABLED, isEnabled).apply()
    }

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
