package com.example.homebookkeeping

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import java.util.Calendar
import java.util.Date
import java.util.Locale

class TransactionsActivity : AppCompatActivity() {

    private val db = Firebase.firestore

    private lateinit var transactionsRecyclerView: RecyclerView
    private lateinit var transactionsHeaderTextView: TextView
    private lateinit var searchEditText: EditText
    private lateinit var typeFilterSpinner: Spinner
    private lateinit var accountFilterSpinner: Spinner
    private lateinit var progressBar: ProgressBar

    private val accountsList = mutableListOf<Account>()
    private val categoriesList = mutableListOf<Category>()
    private val allTransactionsList = mutableListOf<Transaction>()
    private val transactionDisplayList = mutableListOf<Transaction>()

    private lateinit var transactionAdapter: TransactionAdapter
    private lateinit var typeSpinnerAdapter: ArrayAdapter<String>
    private lateinit var accountSpinnerAdapter: AccountSpinnerAdapter

    private var transactionListener: ListenerRegistration? = null
    private var accountsListener: ListenerRegistration? = null
    private var categoriesListener: ListenerRegistration? = null

    private var currentStartDate: Date? = null
    private var currentEndDate: Date? = null
    private var currentSearchQuery: String = ""
    private var currentTypeFilter: String = "Все"
    private var currentAccountFilterId: String? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_transactions)

        initializeUI()
        setupListeners()

        listenForAccounts()
        listenForCategories()
        setFilterForThisMonth()
    }

    override fun onDestroy() {
        super.onDestroy()
        transactionListener?.remove()
        accountsListener?.remove()
        categoriesListener?.remove()
    }

    private fun initializeUI() {
        transactionsHeaderTextView = findViewById(R.id.transactionsHeaderTextView)
        transactionsRecyclerView = findViewById(R.id.transactionsRecyclerView)
        searchEditText = findViewById(R.id.searchEditText)
        typeFilterSpinner = findViewById(R.id.typeFilterSpinner)
        accountFilterSpinner = findViewById(R.id.accountFilterSpinner)
        progressBar = findViewById(R.id.transactionsProgressBar)

        setupSpinners()

        transactionsRecyclerView.layoutManager = LinearLayoutManager(this)
        transactionAdapter = TransactionAdapter(transactionDisplayList, categoriesList, accountsList)
        transactionsRecyclerView.adapter = transactionAdapter
    }

    private fun setupSpinners() {
        typeSpinnerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, mutableListOf("Все типы", "Расходы", "Доходы", "Переводы"))
        typeSpinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        typeFilterSpinner.adapter = typeSpinnerAdapter

        accountSpinnerAdapter = AccountSpinnerAdapter(this, listOf())
        accountFilterSpinner.adapter = accountSpinnerAdapter
    }

    private fun setupListeners() {
        findViewById<Button>(R.id.filterThisMonthButton).setOnClickListener {
            HapticFeedbackHelper.viberate(this)
            setFilterForThisMonth()
        }
        findViewById<Button>(R.id.filterAllTimeButton).setOnClickListener {
            HapticFeedbackHelper.viberate(this)
            transactionsHeaderTextView.text = "Все операции"
            currentStartDate = null
            currentEndDate = null
            listenForTransactions()
        }

        searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                currentSearchQuery = s.toString().lowercase(Locale.getDefault())
                applyFilters()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        typeFilterSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                HapticFeedbackHelper.viberate(this@TransactionsActivity)
                currentTypeFilter = when (position) {
                    1 -> "EXPENSE"
                    2 -> "INCOME"
                    3 -> "TRANSFER"
                    else -> "Все"
                }
                applyFilters()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        accountFilterSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                HapticFeedbackHelper.viberate(this@TransactionsActivity)
                val selectedAccount = parent?.getItemAtPosition(position) as? Account
                if (selectedAccount != null && selectedAccount.id != "ALL_ACCOUNTS_DUMMY_ID") {
                    currentAccountFilterId = selectedAccount.id
                } else {
                    currentAccountFilterId = null
                }
                applyFilters()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        transactionsRecyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                    HapticFeedbackHelper.viberate(this@TransactionsActivity)
                }
            }
        })
    }

    private fun setFilterForThisMonth() {
        transactionsHeaderTextView.text = "Операции за этот месяц"
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        calendar.set(Calendar.HOUR_OF_DAY, 0); calendar.set(Calendar.MINUTE, 0); calendar.set(Calendar.SECOND, 0)
        currentStartDate = calendar.time

        calendar.add(Calendar.MONTH, 1); calendar.add(Calendar.DAY_OF_MONTH, -1)
        calendar.set(Calendar.HOUR_OF_DAY, 23); calendar.set(Calendar.MINUTE, 59); calendar.set(Calendar.SECOND, 59)
        currentEndDate = calendar.time

        listenForTransactions()
    }

    private fun listenForTransactions() {
        val budgetId = BudgetManager.currentBudgetId ?: return
        progressBar.visibility = View.VISIBLE
        transactionsRecyclerView.visibility = View.GONE
        transactionListener?.remove()

        var query: Query = db.collection("budgets").document(budgetId).collection("transactions")
        if (currentStartDate != null && currentEndDate != null) {
            query = query.whereGreaterThanOrEqualTo("timestamp", currentStartDate!!)
                .whereLessThanOrEqualTo("timestamp", currentEndDate!!)
        }
        query = query.orderBy("timestamp", Query.Direction.DESCENDING)


        transactionListener = query.addSnapshotListener { snapshots, e ->
            if (e != null) {
                Log.w("TransactionsActivity", "Ошибка прослушивания транзакций.", e)
                progressBar.visibility = View.GONE
                return@addSnapshotListener
            }
            allTransactionsList.clear()
            for (doc in snapshots!!) {
                allTransactionsList.add(doc.toObject(Transaction::class.java).copy(id = doc.id))
            }
            applyFilters()

            progressBar.visibility = View.GONE
            transactionsRecyclerView.visibility = View.VISIBLE
        }
    }

    private fun applyFilters() {
        val categoriesMap = categoriesList.associateBy { it.id }

        val filteredList = allTransactionsList.filter { transaction ->
            val matchesType = currentTypeFilter == "Все" || transaction.type == currentTypeFilter
            val matchesAccount = currentAccountFilterId == null || transaction.accountId == currentAccountFilterId || transaction.toAccountId == currentAccountFilterId
            val matchesSearch = currentSearchQuery.isBlank() ||
                    transaction.description.lowercase(Locale.getDefault()).contains(currentSearchQuery) ||
                    (categoriesMap[transaction.categoryId]?.name?.lowercase(Locale.getDefault())?.contains(currentSearchQuery) == true)

            matchesType && matchesAccount && matchesSearch
        }

        transactionDisplayList.clear()
        transactionDisplayList.addAll(filteredList)
        transactionAdapter.notifyDataSetChanged()
    }

    private fun listenForAccounts() {
        val budgetId = BudgetManager.currentBudgetId ?: return
        accountsListener = db.collection("budgets").document(budgetId).collection("accounts").addSnapshotListener { snapshots, e ->
            if (e != null) { return@addSnapshotListener }
            accountsList.clear()
            val spinnerAccounts = mutableListOf<Account>()
            spinnerAccounts.add(Account(id = "ALL_ACCOUNTS_DUMMY_ID", name = "Все счета"))

            for (doc in snapshots!!) {
                val account = doc.toObject(Account::class.java).copy(id = doc.id)
                accountsList.add(account)
                spinnerAccounts.add(account)
            }

            accountSpinnerAdapter = AccountSpinnerAdapter(this, spinnerAccounts)
            accountFilterSpinner.adapter = accountSpinnerAdapter

            applyFilters()
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
            applyFilters()
        }
    }
}
