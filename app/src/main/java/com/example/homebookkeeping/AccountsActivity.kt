package com.example.homebookkeeping

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import java.util.Locale

class AccountsActivity : AppCompatActivity() {

    private val db = Firebase.firestore
    private lateinit var accountsRecyclerView: RecyclerView
    private lateinit var accountAdapter: AccountAdapter
    private val accountList = mutableListOf<Account>()
    private lateinit var totalBalanceTextView: TextView
    private lateinit var progressBar: ProgressBar
    private var isInitialLoad = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_accounts)

        accountsRecyclerView = findViewById(R.id.accountsRecyclerView)
        totalBalanceTextView = findViewById(R.id.totalBalanceTextView)
        progressBar = findViewById(R.id.accountsProgressBar)
        val addAccountFab: FloatingActionButton = findViewById(R.id.addAccountFab)

        accountsRecyclerView.layoutManager = LinearLayoutManager(this)
        accountAdapter = AccountAdapter(accountList)
        accountsRecyclerView.adapter = accountAdapter

        addAccountFab.setOnClickListener {
            HapticFeedbackHelper.viberate(this)
            startActivity(Intent(this, AddAccountActivity::class.java))
        }

        accountsRecyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                    HapticFeedbackHelper.viberate(this@AccountsActivity)
                }
            }
        })

        listenForAccounts()
    }

    private fun listenForAccounts() {
        val budgetId = BudgetManager.currentBudgetId
        if (budgetId == null) {
            Toast.makeText(this, "Ошибка: бюджет не найден", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        progressBar.visibility = View.VISIBLE
        accountsRecyclerView.visibility = View.GONE

        db.collection("budgets").document(budgetId).collection("accounts")
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    Log.w("Firestore", "Ошибка прослушивания счетов.", e)
                    progressBar.visibility = View.GONE
                    return@addSnapshotListener
                }
                accountList.clear()
                for (doc in snapshots!!) {
                    val account = doc.toObject(Account::class.java).copy(id = doc.id)
                    accountList.add(account)
                }

                val totalBalance = accountList.filter { it.includeInTotal }.sumOf { it.balance }
                totalBalanceTextView.text = String.format(Locale.GERMANY, "Общий баланс: %,.2f", totalBalance)

                val balanceColor = if (totalBalance < 0) {
                    ContextCompat.getColor(this, R.color.colorExpense)
                } else {
                    ContextCompat.getColor(this, R.color.colorIncome)
                }
                totalBalanceTextView.setTextColor(balanceColor)

                accountAdapter.notifyDataSetChanged()

                if (isInitialLoad) {
                    progressBar.visibility = View.GONE
                    accountsRecyclerView.visibility = View.VISIBLE
                    isInitialLoad = false
                }
            }
    }
}
