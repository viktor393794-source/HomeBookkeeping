package com.example.homebookkeeping

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import java.util.Calendar

class CategoriesActivity : AppCompatActivity(), CategoryClickListener {

    private val db = Firebase.firestore
    private lateinit var categoriesRecyclerView: RecyclerView
    private lateinit var categoryAdapter: CategoryAdapter
    private lateinit var progressBar: ProgressBar

    private val allCategoriesList = mutableListOf<Category>()
    private val displayedCategoryList = mutableListOf<Category>()
    private val expandedCategoryIds = mutableSetOf<String>()

    private val transactionsThisMonth = mutableListOf<Transaction>()
    private var transactionListener: ListenerRegistration? = null
    private var isInitialLoad = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_categories)

        categoriesRecyclerView = findViewById(R.id.categoriesRecyclerView)
        progressBar = findViewById(R.id.categoriesProgressBar)
        val addCategoryFab: FloatingActionButton = findViewById(R.id.addCategoryFab)

        categoriesRecyclerView.layoutManager = LinearLayoutManager(this)
        categoryAdapter = CategoryAdapter(displayedCategoryList, allCategoriesList, transactionsThisMonth, expandedCategoryIds, this)
        categoriesRecyclerView.adapter = categoryAdapter

        addCategoryFab.setOnClickListener {
            HapticFeedbackHelper.viberate(this)
            val intent = Intent(this, AddCategoryActivity::class.java)
            startActivity(intent)
        }

        categoriesRecyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                    HapticFeedbackHelper.viberate(this@CategoriesActivity)
                }
            }
        })

        listenForCategories()
        listenForTransactionsThisMonth()
    }

    override fun onDestroy() {
        super.onDestroy()
        transactionListener?.remove()
    }

    private fun listenForTransactionsThisMonth() {
        val budgetId = BudgetManager.currentBudgetId ?: return
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.DAY_OF_MONTH, 1); calendar.set(Calendar.HOUR_OF_DAY, 0); calendar.set(Calendar.MINUTE, 0); calendar.set(Calendar.SECOND, 0)
        val startDate = calendar.time
        calendar.add(Calendar.MONTH, 1); calendar.add(Calendar.DAY_OF_MONTH, -1); calendar.set(Calendar.HOUR_OF_DAY, 23); calendar.set(Calendar.MINUTE, 59); calendar.set(Calendar.SECOND, 59)
        val endDate = calendar.time

        transactionListener = db.collection("budgets").document(budgetId).collection("transactions")
            .whereEqualTo("type", "EXPENSE")
            .whereGreaterThanOrEqualTo("timestamp", startDate)
            .whereLessThanOrEqualTo("timestamp", endDate)
            .addSnapshotListener { snapshots, e ->
                if (e != null) { return@addSnapshotListener }
                transactionsThisMonth.clear()
                snapshots?.forEach { doc ->
                    transactionsThisMonth.add(doc.toObject(Transaction::class.java).copy(id = doc.id))
                }
                categoryAdapter.notifyDataSetChanged()
            }
    }

    private fun listenForCategories() {
        val budgetId = BudgetManager.currentBudgetId
        if (budgetId == null) {
            Toast.makeText(this, "Ошибка: бюджет не найден", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        progressBar.visibility = View.VISIBLE
        categoriesRecyclerView.visibility = View.GONE

        db.collection("budgets").document(budgetId).collection("categories").addSnapshotListener { snapshots, e ->
            if (e != null) {
                progressBar.visibility = View.GONE
                return@addSnapshotListener
            }
            allCategoriesList.clear()
            allCategoriesList.addAll(snapshots?.map { it.toObject(Category::class.java).copy(id = it.id) } ?: emptyList())

            buildAndDisplayTree()

            if (isInitialLoad) {
                progressBar.visibility = View.GONE
                categoriesRecyclerView.visibility = View.VISIBLE
                isInitialLoad = false
            }
        }
    }

    private fun buildAndDisplayTree() {
        displayedCategoryList.clear()
        val categoryMap = allCategoriesList.groupBy { it.parentId }

        fun addChildren(parentId: String, level: Int) {
            val children = categoryMap[parentId]?.sortedBy { it.name }
            children?.forEach { category ->
                category.level = level
                displayedCategoryList.add(category)
                if (expandedCategoryIds.contains(category.id)) {
                    addChildren(category.id, level + 1)
                }
            }
        }

        addChildren("", 0)
        categoryAdapter.notifyDataSetChanged()
    }

    override fun onCategoryClick(category: Category) {
        val hasChildren = allCategoriesList.any { it.parentId == category.id }
        if (hasChildren) {
            if (expandedCategoryIds.contains(category.id)) {
                expandedCategoryIds.remove(category.id)
            } else {
                expandedCategoryIds.add(category.id)
            }
            buildAndDisplayTree()
        } else {
            onEditCategoryClick(category)
        }
    }

    override fun onEditCategoryClick(category: Category) {
        val intent = Intent(this, EditCategoryActivity::class.java)
        intent.putExtra("CATEGORY_ID", category.id)
        startActivity(intent)
    }

    override fun onAddSubCategoryClick(parentCategory: Category) {
        val intent = Intent(this, AddCategoryActivity::class.java)
        intent.putExtra("PARENT_ID", parentCategory.id)
        intent.putExtra("PARENT_NAME", parentCategory.name)
        intent.putExtra("CATEGORY_TYPE", parentCategory.type)
        intent.putExtra("LEVEL", parentCategory.level + 1)

        var finalParent = parentCategory
        while (finalParent.level != 0) {
            finalParent = allCategoriesList.find { it.id == finalParent.parentId } ?: break
        }
        intent.putExtra("ICON_COLOR", finalParent.iconColor)
        intent.putExtra("BACKGROUND_COLOR", finalParent.backgroundColor)

        startActivity(intent)
    }
}
