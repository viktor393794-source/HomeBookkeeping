package com.example.homebookkeeping

import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.NumberPicker
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class BudgetPlanningActivity : AppCompatActivity() {

    private val db = Firebase.firestore
    private val allCategories = mutableListOf<Category>()

    private lateinit var selectMonthButton: Button
    private lateinit var expensesRecyclerView: RecyclerView
    private lateinit var incomesRecyclerView: RecyclerView
    private lateinit var totalExpensesTextView: TextView
    private lateinit var totalIncomesTextView: TextView

    private var selectedMonth: Calendar = Calendar.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_budget_planning)

        initializeUI()
        setupListeners()
        updateMonthButtonText()
        loadCategories()
    }

    private fun initializeUI() {
        selectMonthButton = findViewById(R.id.selectMonthButton)
        expensesRecyclerView = findViewById(R.id.expensesRecyclerView)
        incomesRecyclerView = findViewById(R.id.incomesRecyclerView)
        totalExpensesTextView = findViewById(R.id.totalExpensesTextView)
        totalIncomesTextView = findViewById(R.id.totalIncomesTextView)

        expensesRecyclerView.layoutManager = LinearLayoutManager(this)
        incomesRecyclerView.layoutManager = LinearLayoutManager(this)
    }

    private fun setupListeners() {
        selectMonthButton.setOnClickListener { showMonthYearPicker() }
    }

    private fun loadCategories() {
        val budgetId = BudgetManager.currentBudgetId
        if (budgetId == null) {
            Toast.makeText(this, "Ошибка: бюджет не найден", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        db.collection("budgets").document(budgetId).collection("categories").addSnapshotListener { snapshots, e ->
            if (e != null) { return@addSnapshotListener }
            allCategories.clear()
            snapshots?.forEach { doc ->
                allCategories.add(doc.toObject(Category::class.java).copy(id = doc.id))
            }
            updateAdapters()
        }
    }

    private fun updateAdapters() {
        val expenseCategories = buildHierarchicalList(allCategories.filter { it.type == "EXPENSE" })
        val incomeCategories = buildHierarchicalList(allCategories.filter { it.type == "INCOME" })

        expensesRecyclerView.adapter = BudgetPlanningAdapter(this, expenseCategories) { updateTotalSums() }
        incomesRecyclerView.adapter = BudgetPlanningAdapter(this, incomeCategories) { updateTotalSums() }

        updateTotalSums()
    }

    private fun updateTotalSums() {
        val totalExpenses = allCategories.filter { it.type == "EXPENSE" }.sumOf { it.limit }
        val totalIncomes = allCategories.filter { it.type == "INCOME" }.sumOf { it.limit }

        totalExpensesTextView.text = String.format(Locale.GERMANY, "Итого: %,.2f", totalExpenses)
        totalIncomesTextView.text = String.format(Locale.GERMANY, "Итого: %,.2f", totalIncomes)
    }

    private fun buildHierarchicalList(categories: List<Category>): List<Category> {
        val result = mutableListOf<Category>()
        val map = categories.groupBy { it.parentId }
        fun addChildren(parentId: String, level: Int) {
            map[parentId]?.sortedBy { it.name }?.forEach { category ->
                category.level = level
                result.add(category)
                addChildren(category.id, level + 1)
            }
        }
        addChildren("", 0)
        return result
    }

    private fun showMonthYearPicker() {
        val linearLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        val monthPicker = NumberPicker(this).apply {
            minValue = 0
            maxValue = 11
            displayedValues = arrayOf("Январь", "Февраль", "Март", "Апрель", "Май", "Июнь", "Июль", "Август", "Сентябрь", "Октябрь", "Ноябрь", "Декабрь")
            value = selectedMonth.get(Calendar.MONTH)
        }
        val yearPicker = NumberPicker(this).apply {
            val currentYear = Calendar.getInstance().get(Calendar.YEAR)
            minValue = currentYear - 10
            maxValue = currentYear + 10
            value = selectedMonth.get(Calendar.YEAR)
        }
        linearLayout.addView(monthPicker)
        linearLayout.addView(yearPicker)
        AlertDialog.Builder(this)
            .setTitle("Выберите месяц и год")
            .setView(linearLayout)
            .setPositiveButton("OK") { _, _ ->
                selectedMonth.set(Calendar.MONTH, monthPicker.value)
                selectedMonth.set(Calendar.YEAR, yearPicker.value)
                updateMonthButtonText()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun updateMonthButtonText() {
        val monthFormat = SimpleDateFormat("LLLL yyyy", Locale("ru"))
        selectMonthButton.text = monthFormat.format(selectedMonth.time)
    }
}
