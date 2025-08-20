package com.example.homebookkeeping

import android.app.AlertDialog
import android.app.DatePickerDialog
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.Toast
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class EditTransferActivity : AppCompatActivity() {

    private val db = Firebase.firestore
    private var transactionId: String? = null
    private var originalTransaction: Transaction? = null

    private lateinit var amountEditText: EditText
    private lateinit var descriptionEditText: EditText
    private lateinit var fromAccountSpinner: Spinner
    private lateinit var toAccountSpinner: Spinner
    private lateinit var dateButton: Button

    private val accountsList = mutableListOf<Account>()
    private lateinit var accountSpinnerAdapter: ArrayAdapter<String>

    private var selectedDate: Calendar = Calendar.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_transfer)

        transactionId = intent.getStringExtra("TRANSACTION_ID")
        if (transactionId == null) {
            finish()
            return
        }

        initializeUI()
        setupListeners()
        loadInitialData()
    }

    private fun initializeUI() {
        amountEditText = findViewById(R.id.amountEditText)
        descriptionEditText = findViewById(R.id.descriptionEditText)
        fromAccountSpinner = findViewById(R.id.fromAccountSpinner)
        toAccountSpinner = findViewById(R.id.toAccountSpinner)
        dateButton = findViewById(R.id.dateButton)

        accountSpinnerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, mutableListOf())
        accountSpinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        fromAccountSpinner.adapter = accountSpinnerAdapter
        toAccountSpinner.adapter = accountSpinnerAdapter
    }

    private fun setupListeners() {
        findViewById<Button>(R.id.saveTransferButton).setOnClickListener {
            HapticFeedbackHelper.viberate(this)
            saveChanges()
        }
        findViewById<Button>(R.id.deleteTransferButton).setOnClickListener {
            HapticFeedbackHelper.viberate(this)
            confirmAndDelete()
        }
        dateButton.setOnClickListener {
            HapticFeedbackHelper.viberate(this)
            showDatePicker()
        }
    }

    private fun loadInitialData() {
        db.collection("accounts").get().addOnSuccessListener { accountDocs ->
            accountsList.clear()
            val accountNames = mutableListOf<String>()
            for (doc in accountDocs) {
                val account = doc.toObject(Account::class.java).copy(id = doc.id)
                accountsList.add(account)
                accountNames.add(account.name)
            }
            accountSpinnerAdapter.clear()
            accountSpinnerAdapter.addAll(accountNames)
            accountSpinnerAdapter.notifyDataSetChanged()

            loadTransaction()
        }
    }

    private fun loadTransaction() {
        db.collection("transactions").document(transactionId!!).get().addOnSuccessListener { doc ->
            originalTransaction = doc.toObject(Transaction::class.java)?.copy(id = doc.id)
            originalTransaction?.let { populateUI(it) }
        }
    }

    private fun populateUI(transaction: Transaction) {
        amountEditText.setText(transaction.amount.toString())
        descriptionEditText.setText(transaction.description)

        transaction.timestamp?.let {
            selectedDate.time = it
            updateDateButtonText()
        }

        val fromAccountIndex = accountsList.indexOfFirst { it.id == transaction.accountId }
        if (fromAccountIndex != -1) fromAccountSpinner.setSelection(fromAccountIndex)

        val toAccountIndex = accountsList.indexOfFirst { it.id == transaction.toAccountId }
        if (toAccountIndex != -1) toAccountSpinner.setSelection(toAccountIndex)
    }

    private fun saveChanges() {
        val original = originalTransaction ?: return

        val newAmount = amountEditText.text.toString().toDoubleOrNull() ?: 0.0
        if (newAmount <= 0) {
            Toast.makeText(this, "Сумма должна быть больше нуля", Toast.LENGTH_SHORT).show()
            return
        }

        val fromAccount = accountsList[fromAccountSpinner.selectedItemPosition]
        val toAccount = accountsList[toAccountSpinner.selectedItemPosition]

        if (fromAccount.id == toAccount.id) {
            Toast.makeText(this, "Счета должны быть разными", Toast.LENGTH_SHORT).show()
            return
        }

        val updatedTransaction = original.copy(
            amount = newAmount,
            description = descriptionEditText.text.toString(),
            accountId = fromAccount.id,
            toAccountId = toAccount.id,
            timestamp = selectedDate.time
        )

        db.runTransaction { firestoreTransaction ->
            val originalFromRef = db.collection("accounts").document(original.accountId)
            val originalToRef = db.collection("accounts").document(original.toAccountId)
            val newFromRef = db.collection("accounts").document(fromAccount.id)
            val newToRef = db.collection("accounts").document(toAccount.id)

            val originalFromDoc = firestoreTransaction.get(originalFromRef)
            val originalToDoc = firestoreTransaction.get(originalToRef)
            val newFromDoc = firestoreTransaction.get(newFromRef)
            val newToDoc = firestoreTransaction.get(newToRef)

            val originalFromBalance = originalFromDoc.getDouble("balance") ?: 0.0
            firestoreTransaction.update(originalFromRef, "balance", originalFromBalance + original.amount)

            val originalToBalance = originalToDoc.getDouble("balance") ?: 0.0
            firestoreTransaction.update(originalToRef, "balance", originalToBalance - original.amount)

            val currentNewFromBalance = if (newFromRef.path == originalFromRef.path) {
                originalFromBalance + original.amount
            } else if (newFromRef.path == originalToRef.path) {
                originalToBalance - original.amount
            } else {
                newFromDoc.getDouble("balance") ?: 0.0
            }
            firestoreTransaction.update(newFromRef, "balance", currentNewFromBalance - newAmount)

            val currentNewToBalance = if (newToRef.path == originalFromRef.path) {
                originalFromBalance + original.amount
            } else if (newToRef.path == originalToRef.path) {
                originalToBalance - original.amount
            } else {
                newToDoc.getDouble("balance") ?: 0.0
            }
            firestoreTransaction.update(newToRef, "balance", currentNewToBalance + newAmount)

            firestoreTransaction.set(db.collection("transactions").document(original.id), updatedTransaction)
            null
        }.addOnSuccessListener {
            Toast.makeText(this, "Перевод обновлен", Toast.LENGTH_SHORT).show()
            finish()
        }.addOnFailureListener { e ->
            Log.e("EditTransferActivity", "Ошибка обновления перевода", e)
            Toast.makeText(this, "Ошибка обновления: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun confirmAndDelete() {
        AlertDialog.Builder(this)
            .setTitle("Удалить перевод")
            .setMessage("Вы уверены? Балансы счетов будут скорректированы.")
            .setPositiveButton("Удалить") { _, _ -> deleteTransaction() }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun deleteTransaction() {
        val original = originalTransaction ?: return

        db.runTransaction { firestoreTransaction ->
            val fromAccountRef = db.collection("accounts").document(original.accountId)
            val toAccountRef = db.collection("accounts").document(original.toAccountId)

            val fromAccountDoc = firestoreTransaction.get(fromAccountRef)
            val toAccountDoc = firestoreTransaction.get(toAccountRef)

            val fromBalance = fromAccountDoc.getDouble("balance") ?: 0.0
            firestoreTransaction.update(fromAccountRef, "balance", fromBalance + original.amount)

            val toBalance = toAccountDoc.getDouble("balance") ?: 0.0
            firestoreTransaction.update(toAccountRef, "balance", toBalance - original.amount)

            firestoreTransaction.delete(db.collection("transactions").document(original.id))
            null
        }.addOnSuccessListener {
            Toast.makeText(this, "Перевод удален", Toast.LENGTH_SHORT).show()
            finish()
        }.addOnFailureListener { e ->
            Log.e("EditTransferActivity", "Ошибка удаления перевода", e)
            Toast.makeText(this, "Ошибка удаления: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun showDatePicker() {
        val dateSetListener = DatePickerDialog.OnDateSetListener { _, year, month, dayOfMonth ->
            selectedDate.set(Calendar.YEAR, year); selectedDate.set(Calendar.MONTH, month); selectedDate.set(Calendar.DAY_OF_MONTH, dayOfMonth)
            updateDateButtonText()
        }
        DatePickerDialog(this, dateSetListener,
            selectedDate.get(Calendar.YEAR), selectedDate.get(Calendar.MONTH), selectedDate.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun updateDateButtonText() {
        val format = "dd.MM.yyyy"; val sdf = SimpleDateFormat(format, Locale.getDefault()); dateButton.text = sdf.format(selectedDate.time)
    }
}
