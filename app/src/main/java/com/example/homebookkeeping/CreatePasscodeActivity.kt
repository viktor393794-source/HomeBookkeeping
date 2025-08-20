package com.example.homebookkeeping

import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.google.android.material.appbar.MaterialToolbar

class CreatePasscodeActivity : AppCompatActivity() {

    private lateinit var titleTextView: TextView
    private lateinit var indicators: List<ImageView>
    private var enteredPin = ""
    private var firstPin = ""
    private var isConfirming = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_passcode)

        titleTextView = findViewById(R.id.titleTextView)
        indicators = listOf(
            findViewById(R.id.indicator1),
            findViewById(R.id.indicator2),
            findViewById(R.id.indicator3),
            findViewById(R.id.indicator4)
        )

        findViewById<MaterialToolbar>(R.id.createPasscodeToolbar).setNavigationOnClickListener { finish() }

        setupKeypad()
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
                processPin()
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

    private fun processPin() {
        if (!isConfirming) {
            firstPin = enteredPin
            isConfirming = true
            titleTextView.text = "Повторите пин-код"
            resetInput()
        } else {
            if (enteredPin == firstPin) {
                saveEncryptedPin(enteredPin)
                setResult(RESULT_OK) // Сообщаем, что пин-код успешно установлен
                finish()
            } else {
                Toast.makeText(this, "Пин-коды не совпадают. Попробуйте снова.", Toast.LENGTH_SHORT).show()
                titleTextView.text = "Создайте пин-код"
                isConfirming = false
                firstPin = ""
                resetInput()
            }
        }
    }

    private fun resetInput() {
        enteredPin = ""
        updateIndicators()
    }

    private fun saveEncryptedPin(pin: String) {
        try {
            val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
            val sharedPreferences: SharedPreferences = EncryptedSharedPreferences.create(
                SettingsActivity.ENCRYPTED_PREFS_NAME,
                masterKeyAlias,
                this,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
            with(sharedPreferences.edit()) {
                putString(SettingsActivity.KEY_PASSCODE, pin)
                apply()
            }
        } catch (e: Exception) {
            // Обработка ошибок безопасности
            Toast.makeText(this, "Не удалось сохранить пин-код. Ошибка безопасности.", Toast.LENGTH_LONG).show()
        }
    }
}