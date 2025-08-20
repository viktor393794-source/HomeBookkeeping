package com.example.homebookkeeping

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.NumberPicker
import android.widget.RadioGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.AppBarLayout
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs

class StatsActivity : AppCompatActivity() {

    private val db = Firebase.firestore
    private lateinit var statsRecyclerView: RecyclerView
    private lateinit var statsAdapter: StatsAdapter

    private val allCategories = mutableListOf<Category>()
    private var fullStatsMap = mapOf<String, CategoryStat>()
    private val hierarchicalStatsList = mutableListOf<CategoryStat>()
    private val topLevelStatsList = mutableListOf<CategoryStat>()
    private val expandedCategoryIds = mutableSetOf<String>()

    private lateinit var selectMonthButton: Button
    private lateinit var totalAmountTextView: TextView
    private lateinit var statsTypeRadioGroup: RadioGroup
    private lateinit var pieChartView: PieChartView
    private lateinit var legendLayout: LinearLayout
    private lateinit var appBarLayout: AppBarLayout
    private lateinit var pieChartContainer: LinearLayout
    private lateinit var collapsedBar: LinearLayout

    private var selectedMonth: Calendar = Calendar.getInstance()
    private var currentStatType = "EXPENSE"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_stats)

        initializeViews()

        statsRecyclerView.layoutManager = LinearLayoutManager(this)
        statsAdapter = StatsAdapter(hierarchicalStatsList, allCategories, expandedCategoryIds) { stat ->
            val hasChildren = allCategories.any { it.parentId == stat.category.id }
            if (hasChildren) {
                if (expandedCategoryIds.contains(stat.category.id)) {
                    expandedCategoryIds.remove(stat.category.id)
                } else {
                    expandedCategoryIds.add(stat.category.id)
                }
                buildAndDisplayTree()
            } else {
                val intent = Intent(this, CategoryDetailsActivity::class.java).apply {
                    putExtra("CATEGORY_ID", stat.category.id)
                    putExtra("CATEGORY_NAME", stat.category.name)
                    putExtra("SELECTED_MONTH", selectedMonth.timeInMillis)
                    putExtra("STAT_TYPE", currentStatType)
                }
                startActivity(intent)
            }
        }
        statsRecyclerView.adapter = statsAdapter

        setupListeners()
        updateStats()
    }

    private fun initializeViews() {
        selectMonthButton = findViewById(R.id.selectMonthButton)
        totalAmountTextView = findViewById(R.id.totalAmountTextView)
        statsTypeRadioGroup = findViewById(R.id.statsTypeRadioGroup)
        statsRecyclerView = findViewById(R.id.statsRecyclerView)
        pieChartView = findViewById(R.id.pieChartView)
        legendLayout = findViewById(R.id.legendLayout)
        appBarLayout = findViewById(R.id.appBarLayout)
        pieChartContainer = findViewById(R.id.pieChartContainer)
        collapsedBar = findViewById(R.id.collapsedBar)
    }

    private fun setupListeners() {
        selectMonthButton.setOnClickListener { showMonthYearPicker() }
        statsTypeRadioGroup.setOnCheckedChangeListener { _, checkedId ->
            currentStatType = if (checkedId == R.id.expenseStatsRadioButton) "EXPENSE" else "INCOME"
            updateStats()
        }
        appBarLayout.addOnOffsetChangedListener(AppBarLayout.OnOffsetChangedListener { appBar, verticalOffset ->
            val scrollRange = appBar.totalScrollRange
            if (scrollRange > 0) {
                val collapseFraction = abs(verticalOffset).toFloat() / scrollRange
                pieChartContainer.alpha = 1 - collapseFraction
                collapsedBar.alpha = collapseFraction
                pieChartContainer.visibility = if (collapseFraction > 0.95) View.INVISIBLE else View.VISIBLE
                collapsedBar.visibility = if (collapseFraction < 0.95) View.INVISIBLE else View.VISIBLE
            }
        })
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
                updateStats()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun updateStats() {
        val monthFormat = SimpleDateFormat("LLLL yyyy", Locale("ru"))
        selectMonthButton.text = monthFormat.format(selectedMonth.time)

        val calendar = selectedMonth.clone() as Calendar
        calendar.set(Calendar.DAY_OF_MONTH, 1); calendar.set(Calendar.HOUR_OF_DAY, 0); calendar.set(Calendar.MINUTE, 0); calendar.set(Calendar.SECOND, 0)
        val startDate = calendar.time
        calendar.add(Calendar.MONTH, 1); calendar.add(Calendar.DAY_OF_MONTH, -1); calendar.set(Calendar.HOUR_OF_DAY, 23); calendar.set(Calendar.MINUTE, 59); calendar.set(Calendar.SECOND, 59)
        val endDate = calendar.time
        loadStatsForPeriod(startDate, endDate)
    }

    private fun loadStatsForPeriod(startDate: Date, endDate: Date) {
        db.collection("categories")
            .whereEqualTo("type", currentStatType)
            .get()
            .addOnSuccessListener { categorySnapshot ->
                allCategories.clear()
                allCategories.addAll(categorySnapshot.documents.mapNotNull { it.toObject(Category::class.java)?.copy(id = it.id) })

                db.collection("transactions")
                    .whereGreaterThanOrEqualTo("timestamp", startDate)
                    .whereLessThanOrEqualTo("timestamp", endDate)
                    .get()
                    .addOnSuccessListener { transactionSnapshot ->
                        val allTransactions = transactionSnapshot.documents.mapNotNull { it.toObject(Transaction::class.java) }
                        val filteredTransactions = allTransactions.filter { it.type == currentStatType }
                        processAndDisplayStats(filteredTransactions)
                    }
            }
    }

    private fun processAndDisplayStats(transactions: List<Transaction>) {
        val totalAmount = transactions.sumOf { it.amount }
        totalAmountTextView.text = String.format("Всего: %,.2f", totalAmount)

        topLevelStatsList.clear()

        if (totalAmount > 0) {
            val statsMap = allCategories.associate { it.id to CategoryStat(it, 0.0, 0.0) }
            transactions.forEach { transaction -> statsMap[transaction.categoryId]?.let { it.totalAmount += transaction.amount } }

            for (level in 2 downTo 0) {
                allCategories.filter { it.level == level }.forEach { category ->
                    allCategories.filter { it.parentId == category.id }.forEach { child ->
                        statsMap[category.id]!!.totalAmount += statsMap[child.id]!!.totalAmount
                    }
                }
            }

            statsMap.values.filter { it.totalAmount > 0 }.forEach { it.percentage = (it.totalAmount / totalAmount) * 100 }
            fullStatsMap = statsMap
            topLevelStatsList.addAll(statsMap.values.filter { it.category.level == 0 && it.totalAmount > 0 })
        } else {
            fullStatsMap = emptyMap()
        }

        buildAndDisplayTree()
        pieChartView.setData(topLevelStatsList)
        updateLegend()
        updateCollapsedBar()
    }

    // --- ОКОНЧАТЕЛЬНО ИСПРАВЛЕННАЯ ЛОГИКА ПОСТРОЕНИЯ ДЕРЕВА ---
    private fun buildAndDisplayTree() {
        hierarchicalStatsList.clear()
        // Группируем все категории, а не статистику
        val categoryMap = allCategories.groupBy { it.parentId }

        fun addChildren(parentId: String) {
            // Ищем дочерние категории и сортируем их по сумме из полной, правильной карты статистики
            val children = categoryMap[parentId]?.sortedByDescending { cat ->
                fullStatsMap[cat.id]?.totalAmount ?: 0.0
            }

            children?.forEach { category ->
                // Берем статистику из полной карты
                fullStatsMap[category.id]?.let { stat ->
                    if (stat.totalAmount > 0) {
                        hierarchicalStatsList.add(stat)
                        if (expandedCategoryIds.contains(category.id)) {
                            addChildren(category.id)
                        }
                    }
                }
            }
        }
        addChildren("") // Начинаем с верхнего уровня
        statsAdapter.notifyDataSetChanged()
    }

    private fun updateLegend() {
        legendLayout.removeAllViews()
        val topCategories = topLevelStatsList.sortedByDescending { it.totalAmount }.take(4)

        for (stat in topCategories) {
            val legendRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = 8 }
            }
            val colorBox = View(this).apply {
                layoutParams = ViewGroup.LayoutParams(32, 32)
                try { setBackgroundColor(Color.parseColor(stat.category.backgroundColor)) }
                catch (e: Exception) { setBackgroundColor(Color.GRAY) }
            }
            val categoryName = TextView(this).apply {
                text = stat.category.name
                textSize = 14f
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { marginStart = 16 }
            }
            legendRow.addView(colorBox)
            legendRow.addView(categoryName)
            legendLayout.addView(legendRow)
        }
    }

    private fun updateCollapsedBar() {
        collapsedBar.removeAllViews()
        if (topLevelStatsList.isEmpty()) return

        topLevelStatsList.forEach { stat ->
            val segment = View(this)
            try {
                segment.setBackgroundColor(Color.parseColor(stat.category.backgroundColor))
            } catch (e: Exception) {
                segment.setBackgroundColor(Color.GRAY)
            }
            val params = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, stat.percentage.toFloat())
            segment.layoutParams = params
            collapsedBar.addView(segment)
        }
    }
}