package com.example.homebookkeeping

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

class AddAccountActivity : AppCompatActivity() {

    private val db = Firebase.firestore

    private lateinit var accountNameEditText: EditText
    private lateinit var initialBalanceEditText: EditText
    private lateinit var selectedIconImageView: ImageView
    private lateinit var iconColorPreview: View
    private lateinit var backgroundColorPreview: View
    private lateinit var includeInTotalCheckBox: CheckBox

    private var selectedIconName: String = "ic_default_wallet"
    private var selectedIconColorHex: String = "#FFFFFF"
    private var selectedBackgroundColorHex: String = "#607D8B"

    private val iconPickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.getStringExtra("selectedIconName")?.let {
                selectedIconName = it
                updateIconPreview()
            }
        }
    }
    private val iconColorPickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.getStringExtra("selectedColorHex")?.let {
                selectedIconColorHex = it
                iconColorPreview.setBackgroundColor(Color.parseColor(it))
                updateIconPreview()
            }
        }
    }
    private val backgroundColorPickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.getStringExtra("selectedColorHex")?.let {
                selectedBackgroundColorHex = it
                backgroundColorPreview.setBackgroundColor(Color.parseColor(it))
                updateIconPreview()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_account)
        initializeUI()
        setupListeners()
    }

    private fun initializeUI() {
        accountNameEditText = findViewById(R.id.accountNameEditText)
        initialBalanceEditText = findViewById(R.id.initialBalanceEditText)
        selectedIconImageView = findViewById(R.id.selectedIconImageView)
        iconColorPreview = findViewById(R.id.iconColorPreview)
        backgroundColorPreview = findViewById(R.id.backgroundColorPreview)
        includeInTotalCheckBox = findViewById(R.id.includeInTotalCheckBox)

        iconColorPreview.setBackgroundColor(Color.parseColor(selectedIconColorHex))
        backgroundColorPreview.setBackgroundColor(Color.parseColor(selectedBackgroundColorHex))
        updateIconPreview()
    }

    private fun setupListeners() {
        findViewById<Button>(R.id.selectIconButton).setOnClickListener {
            HapticFeedbackHelper.viberate(this)
            iconPickerLauncher.launch(Intent(this, IconPickerActivity::class.java))
        }
        findViewById<Button>(R.id.selectIconColorButton).setOnClickListener {
            HapticFeedbackHelper.viberate(this)
            iconColorPickerLauncher.launch(Intent(this, ColorPickerActivity::class.java))
        }
        findViewById<Button>(R.id.selectBackgroundColorButton).setOnClickListener {
            HapticFeedbackHelper.viberate(this)
            backgroundColorPickerLauncher.launch(Intent(this, ColorPickerActivity::class.java))
        }
        findViewById<Button>(R.id.addAccountButton).setOnClickListener {
            HapticFeedbackHelper.viberate(this)
            addAccount()
        }
    }

    private fun addAccount() {
        val budgetId = BudgetManager.currentBudgetId
        if (budgetId == null) {
            Toast.makeText(this, "Ошибка: бюджет не найден", Toast.LENGTH_SHORT).show()
            return
        }

        val name = accountNameEditText.text.toString()
        val balance = initialBalanceEditText.text.toString().toDoubleOrNull()
        val includeInTotal = includeInTotalCheckBox.isChecked

        if (name.isNotBlank() && balance != null) {
            val account = Account(
                name = name,
                balance = balance,
                iconName = selectedIconName,
                iconColor = selectedIconColorHex,
                backgroundColor = selectedBackgroundColorHex,
                includeInTotal = includeInTotal
            )
            db.collection("budgets").document(budgetId).collection("accounts").add(account)
                .addOnSuccessListener {
                    Toast.makeText(this, "Счет успешно добавлен!", Toast.LENGTH_SHORT).show()
                    finish()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        } else {
            Toast.makeText(this, "Заполните все поля", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateIconPreview() {
        val resId = getIconResId(this, selectedIconName)
        if (resId != 0) {
            selectedIconImageView.setImageResource(resId)
            try {
                val background = GradientDrawable()
                background.shape = GradientDrawable.OVAL
                background.setColor(Color.parseColor(selectedBackgroundColorHex))
                selectedIconImageView.background = background
                selectedIconImageView.setColorFilter(Color.parseColor(selectedIconColorHex))
            } catch (e: Exception) { /* handle error */ }
        }
    }

    private fun getIconResId(context: Context, iconName: String): Int {
        return context.resources.getIdentifier(iconName, "drawable", context.packageName)
    }
}
