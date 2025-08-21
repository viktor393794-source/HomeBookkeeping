package com.example.homebookkeeping

import android.app.AlertDialog
import android.app.DatePickerDialog
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.Spinner
import android.widget.Toast
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import android.util.Log

class EditTransactionActivity : AppCompatActivity() {

    private val db = Firebase.firestore
    private var transactionId: String? = null
    private var originalTransaction: Transaction? = null

    private lateinit var amountEditText: EditText
    private lateinit var descriptionEditText: EditText
    private lateinit var operationTypeRadioGroup: RadioGroup
    private lateinit var accountSpinner: Spinner
    private lateinit var categorySpinner: Spinner
    private lateinit var dateButton: Button

    private val accountsList = mutableListOf<Account>()
    private val allCategories = mutableListOf<Category>()
    private val hierarchicalCategories = mutableListOf<Category>()

    private lateinit var accountSpinnerAdapter: AccountSpinnerAdapter
    private lateinit var categorySpinnerAdapter: CategorySpinnerAdapter

    private var selectedDate: Calendar = Calendar.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_transaction)

        transactionId = intent.getStringExtra("TRANSACTION_ID")
        if (transactionId == null) {
            finish()
            return
        }

        initializeUI()
        setupSpinners()
        setupListeners()
        loadInitialData()
    }

    private fun initializeUI() {
        amountEditText = findViewById(R.id.amountEditText)
        descriptionEditText = findViewById(R.id.descriptionEditText)
        operationTypeRadioGroup = findViewById(R.id.operationTypeRadioGroup)
        accountSpinner = findViewById(R.id.accountSpinner)
        categorySpinner = findViewById(R.id.categorySpinner)
        dateButton = findViewById(R.id.dateButton)
    }

    private fun setupSpinners() {
        accountSpinnerAdapter = AccountSpinnerAdapter(this, accountsList)
        accountSpinner.adapter = accountSpinnerAdapter

        categorySpinnerAdapter = CategorySpinnerAdapter(this, hierarchicalCategories)
        categorySpinner.adapter = categorySpinnerAdapter
    }

    private fun setupListeners() {
        findViewById<Button>(R.id.saveTransactionButton).setOnClickListener {
            HapticFeedbackHelper.viberate(this)
            saveChanges()
        }
        findViewById<Button>(R.id.deleteTransactionButton).setOnClickListener {
            HapticFeedbackHelper.viberate(this)
            confirmAndDelete()
        }
        findViewById<Button>(R.id.dateButton).setOnClickListener {
            HapticFeedbackHelper.viberate(this)
            showDatePicker()
        }

        operationTypeRadioGroup.setOnCheckedChangeListener { _, checkedId ->
            val type = if (checkedId == R.id.expenseRadioButton) "EXPENSE" else "INCOME"
            updateCategorySpinner(type)
        }
    }

    private fun loadInitialData() {
        val budgetId = BudgetManager.currentBudgetId ?: return
        db.collection("budgets").document(budgetId).collection("accounts").get().addOnSuccessListener { accountDocs ->
            accountsList.clear()
            accountsList.addAll(accountDocs.map { it.toObject(Account::class.java).copy(id = it.id) })
            accountSpinnerAdapter.notifyDataSetChanged()

            db.collection("budgets").document(budgetId).collection("categories").get().addOnSuccessListener { categoryDocs ->
                allCategories.clear()
                allCategories.addAll(categoryDocs.map { it.toObject(Category::class.java).copy(id = it.id) })
                loadTransaction()
            }
        }
    }

    private fun loadTransaction() {
        val budgetId = BudgetManager.currentBudgetId ?: return
        db.collection("budgets").document(budgetId).collection("transactions").document(transactionId!!).get().addOnSuccessListener { doc ->
            originalTransaction = doc.toObject(Transaction::class.java)?.copy(id = doc.id)
            originalTransaction?.let { populateUI(it) }
        }
    }

    private fun populateUI(transaction: Transaction) {
        amountEditText.setText(transaction.amount.toString())
        descriptionEditText.setText(transaction.description)

        if (transaction.type == "EXPENSE") operationTypeRadioGroup.check(R.id.expenseRadioButton)
        else operationTypeRadioGroup.check(R.id.incomeRadioButton)

        transaction.timestamp?.let { selectedDate.time = it; updateDateButtonText() }

        val accountIndex = accountsList.indexOfFirst { it.id == transaction.accountId }
        if (accountIndex != -1) accountSpinner.setSelection(accountIndex)

        updateCategorySpinner(transaction.type)
        val categoryIndex = hierarchicalCategories.indexOfFirst { it.id == transaction.categoryId }
        if (categoryIndex != -1) categorySpinner.setSelection(categoryIndex)
    }

    private fun saveChanges() {
        val budgetId = BudgetManager.currentBudgetId ?: return
        val original = originalTransaction ?: return
        val newAmount = amountEditText.text.toString().toDoubleOrNull() ?: 0.0
        val newType = if (operationTypeRadioGroup.checkedRadioButtonId == R.id.expenseRadioButton) "EXPENSE" else "INCOME"

        if (newAmount <= 0 || accountsList.isEmpty() || hierarchicalCategories.isEmpty()) {
            Toast.makeText(this, "Заполните все поля", Toast.LENGTH_SHORT).show()
            return
        }
        val newAccount = accountSpinner.selectedItem as Account
        val newCategory = categorySpinner.selectedItem as Category

        val isLeafCategory = allCategories.none { it.parentId == newCategory.id }
        if (!isLeafCategory) {
            Toast.makeText(this, "Выберите подкатегорию. Нельзя присваивать операции родительским категориям.", Toast.LENGTH_LONG).show()
            return
        }

        val updatedTransaction = original.copy(
            amount = newAmount, description = descriptionEditText.text.toString(), type = newType,
            accountId = newAccount.id, categoryId = newCategory.id, timestamp = selectedDate.time
        )

        db.runTransaction { firestoreTransaction ->
            val transactionRef = db.collection("budgets").document(budgetId).collection("transactions").document(original.id)
            val originalAccountRef = db.collection("budgets").document(budgetId).collection("accounts").document(original.accountId)
            val newAccountRef = db.collection("budgets").document(budgetId).collection("accounts").document(newAccount.id)

            val originalAccountDoc = if (original.accountId.isNotBlank()) firestoreTransaction.get(originalAccountRef) else null
            val newAccountDoc = firestoreTransaction.get(newAccountRef)

            if (original.accountId.isNotBlank() && originalAccountDoc != null && originalAccountDoc.exists()) {
                val originalAccountBalance = originalAccountDoc.getDouble("balance")
                if (originalAccountBalance != null) {
                    val revertedBalance = if (original.type == "EXPENSE") originalAccountBalance + original.amount else originalAccountBalance - original.amount
                    firestoreTransaction.update(originalAccountRef, "balance", revertedBalance)
                } else {
                    throw Exception("Баланс исходного счета равен null")
                }
            }

            if (newAccountDoc.exists()) {
                val currentNewAccountBalance = newAccountDoc.getDouble("balance")
                if (currentNewAccountBalance != null) {
                    val finalNewBalance = if (newType == "EXPENSE") currentNewAccountBalance - newAmount else currentNewAccountBalance + newAmount
                    firestoreTransaction.update(newAccountRef, "balance", finalNewBalance)
                } else {
                    throw Exception("Баланс нового счета равен null")
                }
            } else {
                throw Exception("Новый счет не найден")
            }

            firestoreTransaction.set(transactionRef, updatedTransaction)
            null
        }.addOnSuccessListener {
            Toast.makeText(this, "Операция обновлена", Toast.LENGTH_SHORT).show()
            finish()
        }.addOnFailureListener { e ->
            Log.e("EditTransactionActivity", "Ошибка обновления операции: ${e.message}", e)
            Toast.makeText(this, "Ошибка обновления: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun confirmAndDelete() {
        AlertDialog.Builder(this)
            .setTitle("Удалить операцию")
            .setMessage("Вы уверены? Баланс счета будет скорректирован.")
            .setPositiveButton("Удалить") { _, _ -> deleteTransaction() }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun deleteTransaction() {
        val budgetId = BudgetManager.currentBudgetId ?: return
        val original = originalTransaction ?: return

        db.runTransaction { firestoreTransaction ->
            val transactionRef = db.collection("budgets").document(budgetId).collection("transactions").document(original.id)
            val accountRef = db.collection("budgets").document(budgetId).collection("accounts").document(original.accountId)
            val accountDoc = if (original.accountId.isNotBlank()) firestoreTransaction.get(accountRef) else null

            if (original.accountId.isNotBlank() && accountDoc != null && accountDoc.exists()) {
                val currentBalance = accountDoc.getDouble("balance")
                if (currentBalance != null) {
                    val newBalance = if (original.type == "EXPENSE") currentBalance + original.amount else currentBalance - original.amount
                    firestoreTransaction.update(accountRef, "balance", newBalance)
                } else {
                    throw Exception("Баланс счета равен null")
                }
            }
            firestoreTransaction.delete(transactionRef)
            null
        }.addOnSuccessListener {
            Toast.makeText(this, "Операция удалена", Toast.LENGTH_SHORT).show()
            finish()
        }.addOnFailureListener { e ->
            Log.e("EditTransactionActivity", "Ошибка удаления операции: ${e.message}", e)
            Toast.makeText(this, "Ошибка удаления: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showDatePicker() {
        val dateSetListener = DatePickerDialog.OnDateSetListener { _, year, month, dayOfMonth ->
            selectedDate.set(Calendar.YEAR, year); selectedDate.set(Calendar.MONTH, month); selectedDate.set(Calendar.DAY_OF_MONTH, dayOfMonth)
            updateDateButtonText()
        }
        DatePickerDialog(this, dateSetListener,
            selectedDate.get(Calendar.YEAR), selectedDate.get(Calendar.MONTH), selectedDate.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun updateDateButtonText() {
        val format = "dd.MM.yyyy"; val sdf = SimpleDateFormat(format, Locale.getDefault()); dateButton.text = sdf.format(selectedDate.time)
    }

    private fun updateCategorySpinner(type: String) {
        hierarchicalCategories.clear()
        val filteredCategories = allCategories.filter { it.type == type }
        val categoryMap = filteredCategories.groupBy { it.parentId }

        fun addChildren(parentId: String, level: Int) {
            categoryMap[parentId]?.sortedBy { it.name }?.forEach { category ->
                category.level = level
                hierarchicalCategories.add(category)
                addChildren(category.id, level + 1)
            }
        }
        addChildren("", 0)
        categorySpinnerAdapter.notifyDataSetChanged()
    }
}
