package com.example.homebookkeeping

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import java.util.Calendar

class CategoryDetailsActivity : AppCompatActivity() {

    private val db = Firebase.firestore
    private lateinit var titleTextView: TextView
    private lateinit var transactionsRecyclerView: RecyclerView
    private lateinit var transactionAdapter: TransactionAdapter
    private val transactionList = mutableListOf<Transaction>()
    private val accountsList = mutableListOf<Account>()
    private val categoriesList = mutableListOf<Category>()
    private var transactionListener: ListenerRegistration? = null
    private var accountsListener: ListenerRegistration? = null
    private var categoriesListener: ListenerRegistration? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_category_details)

        val categoryId = intent.getStringExtra("CATEGORY_ID")
        val categoryName = intent.getStringExtra("CATEGORY_NAME")
        val selectedMonthMillis = intent.getLongExtra("SELECTED_MONTH", -1)
        val statType = intent.getStringExtra("STAT_TYPE")

        if (categoryId == null || selectedMonthMillis == -1L || statType == null) {
            Toast.makeText(this, "Ошибка: не удалось загрузить данные", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        initializeUI()
        titleTextView.text = "Операции по категории \"$categoryName\""

        listenForAccounts()
        listenForCategories()
        loadTransactionsForCategory(categoryId, selectedMonthMillis, statType)
    }

    override fun onDestroy() {
        super.onDestroy()
        transactionListener?.remove()
        accountsListener?.remove()
        categoriesListener?.remove()
    }

    private fun initializeUI() {
        titleTextView = findViewById(R.id.categoryDetailsTitleTextView)
        transactionsRecyclerView = findViewById(R.id.categoryTransactionsRecyclerView)

        transactionsRecyclerView.layoutManager = LinearLayoutManager(this)
        transactionAdapter = TransactionAdapter(transactionList, categoriesList, accountsList)
        transactionsRecyclerView.adapter = transactionAdapter
    }

    private fun loadTransactionsForCategory(categoryId: String, monthMillis: Long, type: String) {
        val budgetId = BudgetManager.currentBudgetId ?: return
        val calendar = Calendar.getInstance().apply { timeInMillis = monthMillis }
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        calendar.set(Calendar.HOUR_OF_DAY, 0); calendar.set(Calendar.MINUTE, 0); calendar.set(Calendar.SECOND, 0)
        val startDate = calendar.time

        calendar.add(Calendar.MONTH, 1); calendar.add(Calendar.DAY_OF_MONTH, -1)
        calendar.set(Calendar.HOUR_OF_DAY, 23); calendar.set(Calendar.MINUTE, 59); calendar.set(Calendar.SECOND, 59)
        val endDate = calendar.time

        transactionListener = db.collection("budgets").document(budgetId).collection("transactions")
            .whereEqualTo("categoryId", categoryId)
            .whereEqualTo("type", type)
            .whereGreaterThanOrEqualTo("timestamp", startDate)
            .whereLessThanOrEqualTo("timestamp", endDate)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshots, e ->
                if (e != null) { return@addSnapshotListener }
                transactionList.clear()
                for (doc in snapshots!!) {
                    transactionList.add(doc.toObject(Transaction::class.java).copy(id = doc.id))
                }
                transactionAdapter.notifyDataSetChanged()
            }
    }

    private fun listenForAccounts() {
        val budgetId = BudgetManager.currentBudgetId ?: return
        accountsListener = db.collection("budgets").document(budgetId).collection("accounts").addSnapshotListener { snapshots, e ->
            if (e != null) { return@addSnapshotListener }
            accountsList.clear()
            for (doc in snapshots!!) {
                accountsList.add(doc.toObject(Account::class.java).copy(id = doc.id))
            }
            transactionAdapter.notifyDataSetChanged()
        }
    }

    private fun listenForCategories() {
        val budgetId = BudgetManager.currentBudgetId ?: return
        categoriesListener = db.collection("budgets").document(budgetId).collection("categories").addSnapshotListener { snapshots, e ->
            if (e != null) { return@addSnapshotListener }
            categoriesList.clear()
            for (doc in snapshots!!) {
                categoriesList.add(doc.toObject(Category::class.java).copy(id = doc.id))
            }
            transactionAdapter.notifyDataSetChanged()
        }
    }
}
