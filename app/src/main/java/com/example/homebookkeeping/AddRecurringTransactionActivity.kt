package com.example.homebookkeeping

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class AddRecurringTransactionActivity : AppCompatActivity() {

    private val db = Firebase.firestore
    private var transactionId: String? = null
    private var currentTransaction: RecurringTransaction? = null

    private lateinit var titleTextView: TextView
    private lateinit var typeRadioGroup: RadioGroup
    private lateinit var amountEditText: EditText
    private lateinit var accountSpinner: Spinner
    private lateinit var categorySpinner: Spinner
    private lateinit var descriptionEditText: EditText
    private lateinit var periodicitySpinner: Spinner
    private lateinit var daySpinner: Spinner
    private lateinit var startDateButton: Button
    private lateinit var saveButton: Button
    private lateinit var deleteButton: Button

    private val accountsList = mutableListOf<Account>()
    private val allCategories = mutableListOf<Category>()
    private val hierarchicalCategories = mutableListOf<Category>()

    private lateinit var accountSpinnerAdapter: AccountSpinnerAdapter
    private lateinit var categorySpinnerAdapter: CategorySpinnerAdapter

    private var selectedDate: Calendar = Calendar.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_recurring_transaction)
        transactionId = intent.getStringExtra("TRANSACTION_ID")

        initializeUI()
        setupSpinners()
        setupListeners()
        loadInitialData()
    }

    private fun initializeUI() {
        titleTextView = findViewById(R.id.titleTextView)
        typeRadioGroup = findViewById(R.id.operationTypeRadioGroup)
        amountEditText = findViewById(R.id.amountEditText)
        accountSpinner = findViewById(R.id.accountSpinner)
        categorySpinner = findViewById(R.id.categorySpinner)
        descriptionEditText = findViewById(R.id.descriptionEditText)
        periodicitySpinner = findViewById(R.id.periodicitySpinner)
        daySpinner = findViewById(R.id.daySpinner)
        startDateButton = findViewById(R.id.startDateButton)
        saveButton = findViewById(R.id.saveButton)
        deleteButton = findViewById(R.id.deleteButton)
    }

    private fun setupSpinners() {
        accountSpinnerAdapter = AccountSpinnerAdapter(this, accountsList)
        accountSpinner.adapter = accountSpinnerAdapter

        categorySpinnerAdapter = CategorySpinnerAdapter(this, hierarchicalCategories)
        categorySpinner.adapter = categorySpinnerAdapter

        val periodicityAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, listOf("Ежемесячно", "Еженедельно"))
        periodicityAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        periodicitySpinner.adapter = periodicityAdapter
    }

    private fun setupListeners() {
        typeRadioGroup.setOnCheckedChangeListener { _, checkedId ->
            val type = if (checkedId == R.id.expenseRadioButton) "EXPENSE" else "INCOME"
            updateCategorySpinner(type)
        }

        periodicitySpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                updateDaySpinner(position)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        startDateButton.setOnClickListener { showDatePicker() }
        saveButton.setOnClickListener { saveTransaction() }
        deleteButton.setOnClickListener { confirmAndDelete() }
    }

    private fun loadInitialData() {
        db.collection("accounts").get().addOnSuccessListener { accountDocs ->
            accountsList.clear()
            accountsList.addAll(accountDocs.map { it.toObject(Account::class.java).copy(id = it.id) })
            accountSpinnerAdapter.notifyDataSetChanged()

            db.collection("categories").get().addOnSuccessListener { categoryDocs ->
                allCategories.clear()
                allCategories.addAll(categoryDocs.map { it.toObject(Category::class.java).copy(id = it.id) })

                if (transactionId != null) {
                    titleTextView.text = "Редактировать операцию"
                    deleteButton.visibility = View.VISIBLE
                    loadTransactionData()
                } else {
                    updateCategorySpinner("EXPENSE")
                    updateDateButtonText()
                }
            }
        }
    }

    private fun loadTransactionData() {
        db.collection("recurring_transactions").document(transactionId!!).get().addOnSuccessListener { doc ->
            currentTransaction = doc.toObject(RecurringTransaction::class.java)
            currentTransaction?.let { populateUI(it) }
        }
    }

    private fun populateUI(transaction: RecurringTransaction) {
        if (transaction.type == "INCOME") typeRadioGroup.check(R.id.incomeRadioButton)
        else typeRadioGroup.check(R.id.expenseRadioButton)

        amountEditText.setText(transaction.amount.toString())
        descriptionEditText.setText(transaction.description)

        transaction.nextExecutionDate?.let { selectedDate.time = it; updateDateButtonText() }

        val accountIndex = accountsList.indexOfFirst { it.id == transaction.accountId }
        if (accountIndex != -1) accountSpinner.setSelection(accountIndex)

        updateCategorySpinner(transaction.type)
        val categoryIndex = hierarchicalCategories.indexOfFirst { it.id == transaction.categoryId }
        if (categoryIndex != -1) categorySpinner.setSelection(categoryIndex)

        if (transaction.periodicity == "WEEKLY") {
            periodicitySpinner.setSelection(1)
            updateDaySpinner(1)
            daySpinner.setSelection(transaction.dayOfWeek - 1)
        } else {
            periodicitySpinner.setSelection(0)
            updateDaySpinner(0)
            daySpinner.setSelection(transaction.dayOfMonth - 1)
        }
    }

    private fun saveTransaction() {
        val amount = amountEditText.text.toString().toDoubleOrNull()
        if (amount == null || amount <= 0) {
            Toast.makeText(this, "Введите корректную сумму", Toast.LENGTH_SHORT).show()
            return
        }
        if (accountSpinner.selectedItemPosition < 0 || categorySpinner.selectedItemPosition < 0) {
            Toast.makeText(this, "Выберите счет и категорию", Toast.LENGTH_SHORT).show()
            return
        }

        val transaction = currentTransaction ?: RecurringTransaction()
        transaction.amount = amount
        transaction.description = descriptionEditText.text.toString()
        transaction.type = if (typeRadioGroup.checkedRadioButtonId == R.id.expenseRadioButton) "EXPENSE" else "INCOME"
        transaction.accountId = (accountSpinner.selectedItem as Account).id
        transaction.categoryId = (categorySpinner.selectedItem as Category).id
        transaction.nextExecutionDate = selectedDate.time
        transaction.periodicity = if (periodicitySpinner.selectedItemPosition == 0) "MONTHLY" else "WEEKLY"
        if (transaction.periodicity == "MONTHLY") {
            transaction.dayOfMonth = daySpinner.selectedItemPosition + 1
        } else {
            transaction.dayOfWeek = daySpinner.selectedItemPosition + 1
        }

        val collection = db.collection("recurring_transactions")
        val task = if (transaction.id.isBlank()) collection.add(transaction) else collection.document(transaction.id).set(transaction)

        task.addOnSuccessListener {
            Toast.makeText(this, "Сохранено", Toast.LENGTH_SHORT).show()
            finish()
        }.addOnFailureListener {
            Toast.makeText(this, "Ошибка: ${it.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun confirmAndDelete() {
        AlertDialog.Builder(this)
            .setTitle("Удалить шаблон")
            .setMessage("Вы уверены, что хотите удалить этот шаблон регулярной операции?")
            .setPositiveButton("Удалить") { _, _ ->
                db.collection("recurring_transactions").document(transactionId!!).delete()
                    .addOnSuccessListener {
                        Toast.makeText(this, "Шаблон удален", Toast.LENGTH_SHORT).show()
                        finish()
                    }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun updateDaySpinner(periodicityPosition: Int) {
        val daysAdapter = if (periodicityPosition == 0) { // Ежемесячно
            ArrayAdapter(this, android.R.layout.simple_spinner_item, (1..31).map { "$it числа" })
        } else { // Еженедельно
            ArrayAdapter(this, android.R.layout.simple_spinner_item, listOf("по Воскресеньям", "по Понедельникам", "по Вторникам", "по Средам", "по Четвергам", "по Пятницам", "по Субботам"))
        }
        daysAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        daySpinner.adapter = daysAdapter
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

    private fun showDatePicker() {
        DatePickerDialog(this, { _, year, month, dayOfMonth ->
            selectedDate.set(Calendar.YEAR, year)
            selectedDate.set(Calendar.MONTH, month)
            selectedDate.set(Calendar.DAY_OF_MONTH, dayOfMonth)
            updateDateButtonText()
        }, selectedDate.get(Calendar.YEAR), selectedDate.get(Calendar.MONTH), selectedDate.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun updateDateButtonText() {
        val format = "dd.MM.yyyy"
        val sdf = SimpleDateFormat(format, Locale.getDefault())
        startDateButton.text = sdf.format(selectedDate.time)
    }
}
