package com.example.homebookkeeping

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.appbar.MaterialToolbar
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private val db = Firebase.firestore
    private val auth = Firebase.auth

    private lateinit var amountEditText: EditText
    private lateinit var descriptionEditText: EditText
    private lateinit var operationTypeRadioGroup: RadioGroup
    private lateinit var fromAccountSpinner: Spinner
    private lateinit var toAccountSpinner: Spinner
    private lateinit var categorySpinner: Spinner
    private lateinit var dateButton: Button
    private lateinit var executeOperationButton: Button
    private lateinit var toAccountLayout: LinearLayout
    private lateinit var categoryLayout: LinearLayout
    private lateinit var fromAccountLabel: TextView
    private lateinit var totalBalanceTextView: TextView

    private val accountsList = mutableListOf<Account>()
    private val allCategories = mutableListOf<Category>()
    private val hierarchicalCategories = mutableListOf<Category>()

    private lateinit var accountSpinnerAdapter: AccountSpinnerAdapter
    private lateinit var categorySpinnerAdapter: CategorySpinnerAdapter

    private var selectedDate: Calendar = Calendar.getInstance()

    private var areAccountsLoaded = false
    private var areCategoriesLoaded = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        BudgetManager.loadCurrentBudget(this)
        if (BudgetManager.currentBudgetId == null || auth.currentUser == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        // --- ИСПРАВЛЕННАЯ ПОСЛЕДОВАТЕЛЬНОСТЬ ---
        // 1. Сначала находим все UI элементы
        initializeUI()

        // 2. Теперь безопасно настраиваем Toolbar
        setSupportActionBar(findViewById(R.id.mainToolbar))
        supportActionBar?.setDisplayShowTitleEnabled(false)

        // 3. Блокируем форму до загрузки данных
        setFormEnabled(false)

        // 4. Настраиваем спиннеры
        setupSpinners()

        // 5. И только теперь, когда все элементы найдены, назначаем им действия
        setupListeners()
        // --- КОНЕЦ ИСПРАВЛЕНИЯ ---

        listenForAccounts()
        listenForCategories()
        updateDateButtonText()
    }

    private fun initializeUI() {
        amountEditText = findViewById(R.id.amountEditText)
        descriptionEditText = findViewById(R.id.descriptionEditText)
        operationTypeRadioGroup = findViewById(R.id.operationTypeRadioGroup)
        fromAccountSpinner = findViewById(R.id.fromAccountSpinner)
        toAccountSpinner = findViewById(R.id.toAccountSpinner)
        categorySpinner = findViewById(R.id.categorySpinner)
        dateButton = findViewById(R.id.dateButton)
        executeOperationButton = findViewById(R.id.executeOperationButton)
        toAccountLayout = findViewById(R.id.toAccountLayout)
        categoryLayout = findViewById(R.id.categoryLayout)
        fromAccountLabel = findViewById(R.id.fromAccountLabel)
        totalBalanceTextView = findViewById(R.id.totalBalanceTextView)
    }

    private fun setFormEnabled(isEnabled: Boolean) {
        amountEditText.isEnabled = isEnabled
        fromAccountSpinner.isEnabled = isEnabled
        toAccountSpinner.isEnabled = isEnabled
        categorySpinner.isEnabled = isEnabled
        dateButton.isEnabled = isEnabled
        descriptionEditText.isEnabled = isEnabled
        executeOperationButton.isEnabled = isEnabled

        if (!isEnabled && areAccountsLoaded && areCategoriesLoaded) {
            if (accountsList.isEmpty()) {
                Toast.makeText(this, "Сначала создайте счет в разделе 'Счета'", Toast.LENGTH_LONG).show()
            } else if (allCategories.isEmpty()) {
                Toast.makeText(this, "Сначала создайте категорию в разделе 'Категории'", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun checkIfDataIsReady() {
        if (areAccountsLoaded && areCategoriesLoaded) {
            if (accountsList.isNotEmpty() && allCategories.isNotEmpty()) {
                setFormEnabled(true)
            } else {
                setFormEnabled(false)
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun listenForAccounts() {
        val budgetId = BudgetManager.currentBudgetId ?: return
        db.collection("budgets").document(budgetId).collection("accounts").addSnapshotListener { snapshots, e ->
            if (e != null) {
                Log.w("Firestore", "Ошибка прослушивания счетов.", e)
                areAccountsLoaded = true
                checkIfDataIsReady()
                return@addSnapshotListener
            }
            accountsList.clear()
            for (doc in snapshots!!) {
                accountsList.add(doc.toObject(Account::class.java).copy(id = doc.id))
            }

            val totalBalance = accountsList.filter { it.includeInTotal }.sumOf { it.balance }
            totalBalanceTextView.text = String.format(Locale.GERMANY, "%,.2f", totalBalance)
            val balanceColor = if (totalBalance < 0) ContextCompat.getColor(this, R.color.colorExpense) else ContextCompat.getColor(this, R.color.colorIncome)
            totalBalanceTextView.setTextColor(balanceColor)

            accountSpinnerAdapter.notifyDataSetChanged()

            areAccountsLoaded = true
            checkIfDataIsReady()
        }
    }

    private fun listenForCategories() {
        val budgetId = BudgetManager.currentBudgetId ?: return
        db.collection("budgets").document(budgetId).collection("categories").addSnapshotListener { snapshots, e ->
            if (e != null) {
                Log.w("Firestore", "Ошибка прослушивания категорий.", e)
                areCategoriesLoaded = true
                checkIfDataIsReady()
                return@addSnapshotListener
            }
            allCategories.clear()
            snapshots?.forEach { doc ->
                allCategories.add(doc.toObject(Category::class.java).copy(id = doc.id))
            }
            val type = if (operationTypeRadioGroup.checkedRadioButtonId == R.id.expenseRadioButton) "EXPENSE" else "INCOME"
            updateCategorySpinner(type)

            areCategoriesLoaded = true
            checkIfDataIsReady()
        }
    }

    private fun setupSpinners() {
        accountSpinnerAdapter = AccountSpinnerAdapter(this, accountsList)
        fromAccountSpinner.adapter = accountSpinnerAdapter
        toAccountSpinner.adapter = accountSpinnerAdapter
        categorySpinnerAdapter = CategorySpinnerAdapter(this, hierarchicalCategories)
        categorySpinner.adapter = categorySpinnerAdapter
    }

    private fun setupListeners() {
        dateButton.setOnClickListener { HapticFeedbackHelper.viberate(this); showDatePicker() }
        executeOperationButton.setOnClickListener { HapticFeedbackHelper.viberate(this); executeOperation() }
        findViewById<Button>(R.id.manageAccountsButton).setOnClickListener { HapticFeedbackHelper.viberate(this); startActivity(Intent(this, AccountsActivity::class.java)) }
        findViewById<Button>(R.id.manageCategoriesButton).setOnClickListener { HapticFeedbackHelper.viberate(this); startActivity(Intent(this, CategoriesActivity::class.java)) }
        findViewById<Button>(R.id.statsButton).setOnClickListener { HapticFeedbackHelper.viberate(this); startActivity(Intent(this, StatsActivity::class.java)) }
        findViewById<Button>(R.id.allTransactionsButton).setOnClickListener { HapticFeedbackHelper.viberate(this); startActivity(Intent(this, TransactionsActivity::class.java)) }
        findViewById<Button>(R.id.planningButton).setOnClickListener { HapticFeedbackHelper.viberate(this); startActivity(Intent(this, BudgetPlanningActivity::class.java)) }
        findViewById<Button>(R.id.recurringButton).setOnClickListener { HapticFeedbackHelper.viberate(this); startActivity(Intent(this, RecurringTransactionsActivity::class.java)) }
        operationTypeRadioGroup.setOnCheckedChangeListener { _, checkedId ->
            HapticFeedbackHelper.viberate(this)
            when (checkedId) {
                R.id.expenseRadioButton, R.id.incomeRadioButton -> {
                    toAccountLayout.visibility = View.GONE
                    categoryLayout.visibility = View.VISIBLE
                    fromAccountLabel.text = if (checkedId == R.id.expenseRadioButton) "Со счета:" else "На счет:"
                    val type = if (checkedId == R.id.expenseRadioButton) "EXPENSE" else "INCOME"
                    updateCategorySpinner(type)
                }
                R.id.transferRadioButton -> {
                    toAccountLayout.visibility = View.VISIBLE
                    categoryLayout.visibility = View.GONE
                    fromAccountLabel.text = "Со счета:"
                }
            }
        }
    }

    private fun executeOperation() {
        when (operationTypeRadioGroup.checkedRadioButtonId) {
            R.id.expenseRadioButton, R.id.incomeRadioButton -> handleIncomeExpense()
            R.id.transferRadioButton -> handleTransfer()
        }
    }

    private fun handleIncomeExpense() {
        val budgetId = BudgetManager.currentBudgetId ?: return
        val amountStr = amountEditText.text.toString()
        val description = descriptionEditText.text.toString()
        val type = if (operationTypeRadioGroup.checkedRadioButtonId == R.id.expenseRadioButton) "EXPENSE" else "INCOME"

        if (amountStr.isBlank() || amountStr.toDoubleOrNull() ?: 0.0 <= 0) { Toast.makeText(this, "Введите корректную сумму", Toast.LENGTH_SHORT).show(); return }
        val amount = amountStr.toDouble()
        if (fromAccountSpinner.selectedItemPosition < 0) { Toast.makeText(this, "Выберите счет", Toast.LENGTH_SHORT).show(); return }
        val selectedAccount = fromAccountSpinner.selectedItem as Account
        if (categorySpinner.selectedItemPosition < 0) { Toast.makeText(this, "Выберите категорию", Toast.LENGTH_SHORT).show(); return }
        val selectedCategory = categorySpinner.selectedItem as Category
        val isLeafCategory = allCategories.none { it.parentId == selectedCategory.id }
        if (!isLeafCategory) { Toast.makeText(this, "Выберите подкатегорию. Нельзя присваивать операции родительским категориям.", Toast.LENGTH_LONG).show(); return }

        db.runTransaction { firestoreTransaction ->
            val accountRef = db.collection("budgets").document(budgetId).collection("accounts").document(selectedAccount.id)
            val accountDoc = firestoreTransaction.get(accountRef)
            val currentBalance = accountDoc.getDouble("balance") ?: 0.0
            val newBalance = if (type == "EXPENSE") currentBalance - amount else currentBalance + amount
            firestoreTransaction.update(accountRef, "balance", newBalance)

            val newTransactionRef = db.collection("budgets").document(budgetId).collection("transactions").document()
            val newTransaction = Transaction(amount = amount, description = description, timestamp = selectedDate.time, type = type, accountId = selectedAccount.id, categoryId = selectedCategory.id)
            firestoreTransaction.set(newTransactionRef, newTransaction)
            null
        }.addOnSuccessListener { Toast.makeText(this, "Операция успешно добавлена!", Toast.LENGTH_SHORT).show(); clearInputFields()
        }.addOnFailureListener { e -> Log.e("MainActivity", "Ошибка добавления операции: ${e.message}", e); Toast.makeText(this, "Ошибка: ${e.message}", Toast.LENGTH_LONG).show() }
    }

    private fun handleTransfer() {
        val budgetId = BudgetManager.currentBudgetId ?: return
        val amountStr = amountEditText.text.toString()
        val description = descriptionEditText.text.toString()

        if (amountStr.isBlank() || amountStr.toDoubleOrNull() ?: 0.0 <= 0) { Toast.makeText(this, "Введите корректную сумму", Toast.LENGTH_SHORT).show(); return }
        val amount = amountStr.toDouble()
        if (fromAccountSpinner.selectedItemPosition < 0 || toAccountSpinner.selectedItemPosition < 0) { Toast.makeText(this, "Выберите оба счета", Toast.LENGTH_SHORT).show(); return }
        if (fromAccountSpinner.selectedItemPosition == toAccountSpinner.selectedItemPosition) { Toast.makeText(this, "Счета должны быть разными", Toast.LENGTH_SHORT).show(); return }
        val fromAccount = fromAccountSpinner.selectedItem as Account
        val toAccount = toAccountSpinner.selectedItem as Account

        db.runTransaction { firestoreTransaction ->
            val fromAccountRef = db.collection("budgets").document(budgetId).collection("accounts").document(fromAccount.id)
            val toAccountRef = db.collection("budgets").document(budgetId).collection("accounts").document(toAccount.id)
            val fromAccountDoc = firestoreTransaction.get(fromAccountRef)
            val fromBalance = fromAccountDoc.getDouble("balance") ?: 0.0
            val toAccountDoc = firestoreTransaction.get(toAccountRef)
            val toBalance = toAccountDoc.getDouble("balance") ?: 0.0
            firestoreTransaction.update(fromAccountRef, "balance", fromBalance - amount)
            firestoreTransaction.update(toAccountRef, "balance", toBalance + amount)
            val newTransactionRef = db.collection("budgets").document(budgetId).collection("transactions").document()
            val transferTransaction = Transaction(amount = amount, description = description.ifEmpty { "Перевод" }, timestamp = selectedDate.time, type = "TRANSFER", accountId = fromAccount.id, toAccountId = toAccount.id)
            firestoreTransaction.set(newTransactionRef, transferTransaction)
            null
        }.addOnSuccessListener { Toast.makeText(this, "Перевод успешно выполнен!", Toast.LENGTH_SHORT).show(); clearInputFields() }
    }

    private fun clearInputFields() { amountEditText.text.clear(); descriptionEditText.text.clear() }

    private fun showDatePicker() {
        val dateSetListener = DatePickerDialog.OnDateSetListener { _, year, month, dayOfMonth ->
            selectedDate.set(Calendar.YEAR, year); selectedDate.set(Calendar.MONTH, month); selectedDate.set(Calendar.DAY_OF_MONTH, dayOfMonth)
            updateDateButtonText()
        }
        DatePickerDialog(this, dateSetListener, selectedDate.get(Calendar.YEAR), selectedDate.get(Calendar.MONTH), selectedDate.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun updateDateButtonText() {
        val format = "dd.MM.yyyy"
        val sdf = SimpleDateFormat(format, Locale.getDefault())
        dateButton.text = sdf.format(selectedDate.time)
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