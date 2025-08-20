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
    private lateinit var accountSpinnerAdapter: ArrayAdapter<String>

    private var transactionListener: ListenerRegistration? = null
    private var accountsListener: ListenerRegistration? = null
    private var categoriesListener: ListenerRegistration? = null

    private var currentStartDate: Date? = null
    private var currentEndDate: Date? = null
    private var currentSearchQuery: String = ""
    private var currentTypeFilter: String = "Все"
    private var currentAccountFilterId: String? = null

    // --- УДАЛЕНИЕ: Переменная isInitialLoad больше не нужна ---
    // private var isInitialLoad = true

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

        accountSpinnerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, mutableListOf("Все счета"))
        accountSpinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
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
            // --- ИЗМЕНЕНИЕ: Запускаем перезагрузку данных, а не просто фильтрацию ---
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
                currentAccountFilterId = if (position == 0) null else accountsList[position - 1].id
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
        progressBar.visibility = View.VISIBLE
        transactionsRecyclerView.visibility = View.GONE
        transactionListener?.remove()

        // --- УЛУЧШЕНИЕ: Строим запрос к БД с учетом фильтра по дате ---
        var query: Query = db.collection("transactions")
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

            // --- ИСПРАВЛЕНИЕ: Прячем загрузку каждый раз после успешного получения данных ---
            progressBar.visibility = View.GONE
            transactionsRecyclerView.visibility = View.VISIBLE
        }
    }

    private fun applyFilters() {
        // --- ИЗМЕНЕНИЕ: Неиспользуемая переменная accountsMap удалена. ---
        val categoriesMap = categoriesList.associateBy { it.id }

        // --- ИЗМЕНЕНИЕ: Фильтрация по дате теперь не нужна, т.к. ее делает запрос к БД ---
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
        accountsListener = db.collection("accounts").addSnapshotListener { snapshots, e ->
            if (e != null) { return@addSnapshotListener }
            accountsList.clear()
            val accountNames = mutableListOf("Все счета")
            for (doc in snapshots!!) {
                val account = doc.toObject(Account::class.java).copy(id = doc.id)
                accountsList.add(account)
                accountNames.add(account.name)
            }
            accountSpinnerAdapter.clear()
            accountSpinnerAdapter.addAll(accountNames)
            accountSpinnerAdapter.notifyDataSetChanged()
            applyFilters()
        }
    }

    private fun listenForCategories() {
        categoriesListener = db.collection("categories").addSnapshotListener { snapshots, e ->
            if (e != null) { return@addSnapshotListener }
            categoriesList.clear()
            for (doc in snapshots!!) {
                categoriesList.add(doc.toObject(Category::class.java).copy(id = doc.id))
            }
            applyFilters()
        }
    }
}