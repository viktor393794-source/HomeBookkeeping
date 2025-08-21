package com.example.homebookkeeping

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

class RecurringTransactionsActivity : AppCompatActivity() {

    private val db = Firebase.firestore
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyView: TextView
    private val recurringTransactions = mutableListOf<RecurringTransaction>()
    private val categories = mutableListOf<Category>()
    private lateinit var adapter: RecurringTransactionAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_recurring_transactions)

        recyclerView = findViewById(R.id.recurringTransactionsRecyclerView)
        emptyView = findViewById(R.id.emptyView)
        val fab: FloatingActionButton = findViewById(R.id.addRecurringTransactionFab)

        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = RecurringTransactionAdapter(this, recurringTransactions, categories)
        recyclerView.adapter = adapter

        fab.setOnClickListener {
            startActivity(Intent(this, AddRecurringTransactionActivity::class.java))
        }

        loadData()
    }

    private fun loadData() {
        val budgetId = BudgetManager.currentBudgetId
        if (budgetId == null) {
            Toast.makeText(this, "Ошибка: бюджет не найден", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        db.collection("budgets").document(budgetId).collection("categories").get().addOnSuccessListener { categorySnapshot ->
            categories.clear()
            categorySnapshot.documents.forEach { doc ->
                categories.add(doc.toObject(Category::class.java)!!.copy(id = doc.id))
            }

            db.collection("budgets").document(budgetId).collection("recurring_transactions")
                .orderBy("nextExecutionDate")
                .addSnapshotListener { snapshot, _ ->
                    recurringTransactions.clear()
                    snapshot?.documents?.forEach { doc ->
                        recurringTransactions.add(doc.toObject(RecurringTransaction::class.java)!!.copy(id = doc.id))
                    }
                    adapter.notifyDataSetChanged()
                    checkIfEmpty()
                }
        }
    }

    private fun checkIfEmpty() {
        if (recurringTransactions.isEmpty()) {
            recyclerView.visibility = View.GONE
            emptyView.visibility = View.VISIBLE
        } else {
            recyclerView.visibility = View.VISIBLE
            emptyView.visibility = View.GONE
        }
    }
}
