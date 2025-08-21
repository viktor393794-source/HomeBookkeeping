package com.example.homebookkeeping

import android.app.Activity
import android.app.AlertDialog
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

class EditAccountActivity : AppCompatActivity() {

    private val db = Firebase.firestore
    private var accountId: String? = null
    private var currentAccount: Account? = null

    private lateinit var accountNameEditText: EditText
    private lateinit var balanceEditText: EditText
    private lateinit var selectedIconImageView: ImageView
    private lateinit var iconColorPreview: View
    private lateinit var backgroundColorPreview: View
    private lateinit var includeInTotalCheckBox: CheckBox

    private var selectedIconName: String? = null
    private var selectedIconColorHex: String? = null
    private var selectedBackgroundColorHex: String? = null

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
        setContentView(R.layout.activity_edit_account)

        accountId = intent.getStringExtra("ACCOUNT_ID")
        if (accountId == null) {
            Toast.makeText(this, "Ошибка: ID счета не найден", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        initializeUI()
        setupListeners()
        loadAccountData()
    }

    private fun initializeUI() {
        accountNameEditText = findViewById(R.id.accountNameEditText)
        balanceEditText = findViewById(R.id.balanceEditText)
        selectedIconImageView = findViewById(R.id.selectedIconImageView)
        iconColorPreview = findViewById(R.id.iconColorPreview)
        backgroundColorPreview = findViewById(R.id.backgroundColorPreview)
        includeInTotalCheckBox = findViewById(R.id.includeInTotalCheckBox)
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
        findViewById<Button>(R.id.saveAccountButton).setOnClickListener {
            HapticFeedbackHelper.viberate(this)
            saveChanges()
        }
        findViewById<Button>(R.id.deleteAccountButton).setOnClickListener {
            HapticFeedbackHelper.viberate(this)
            confirmAndDelete()
        }
    }

    private fun loadAccountData() {
        val budgetId = BudgetManager.currentBudgetId ?: return
        db.collection("budgets").document(budgetId).collection("accounts").document(accountId!!)
            .get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    currentAccount = document.toObject(Account::class.java)
                    currentAccount?.let { populateUI(it) }
                } else {
                    Toast.makeText(this, "Счет не найден", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
    }

    private fun populateUI(account: Account) {
        accountNameEditText.setText(account.name)
        balanceEditText.setText(account.balance.toString())
        includeInTotalCheckBox.isChecked = account.includeInTotal

        selectedIconName = account.iconName
        selectedIconColorHex = account.iconColor
        selectedBackgroundColorHex = account.backgroundColor

        iconColorPreview.setBackgroundColor(Color.parseColor(selectedIconColorHex))
        backgroundColorPreview.setBackgroundColor(Color.parseColor(selectedBackgroundColorHex))
        updateIconPreview()
    }

    private fun updateIconPreview() {
        if (selectedIconName == null) return
        val resId = getIconResId(this, selectedIconName!!)
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

    private fun saveChanges() {
        val budgetId = BudgetManager.currentBudgetId ?: return
        val name = accountNameEditText.text.toString()
        val balance = balanceEditText.text.toString().toDoubleOrNull()
        val includeInTotal = includeInTotalCheckBox.isChecked

        if (name.isBlank() || balance == null || selectedIconName == null) {
            Toast.makeText(this, "Все поля должны быть заполнены", Toast.LENGTH_SHORT).show()
            return
        }

        val updatedAccount = Account(
            id = accountId!!,
            name = name,
            balance = balance,
            iconName = selectedIconName!!,
            iconColor = selectedIconColorHex!!,
            backgroundColor = selectedBackgroundColorHex!!,
            includeInTotal = includeInTotal
        )

        db.collection("budgets").document(budgetId).collection("accounts").document(accountId!!)
            .set(updatedAccount)
            .addOnSuccessListener {
                Toast.makeText(this, "Счет обновлен", Toast.LENGTH_SHORT).show()
                finish()
            }
    }

    private fun confirmAndDelete() {
        AlertDialog.Builder(this)
            .setTitle("Удалить счет")
            .setMessage("Вы уверены? Все связанные с этим счетом транзакции останутся, но будут отображаться без имени счета.")
            .setPositiveButton("Удалить") { _, _ -> deleteAccount() }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun deleteAccount() {
        val budgetId = BudgetManager.currentBudgetId ?: return
        db.collection("budgets").document(budgetId).collection("accounts").document(accountId!!)
            .delete()
            .addOnSuccessListener {
                Toast.makeText(this, "Счет удален", Toast.LENGTH_SHORT).show()
                finish()
            }
    }

    private fun getIconResId(context: Context, iconName: String): Int {
        return context.resources.getIdentifier(iconName, "drawable", context.packageName)
    }
}
