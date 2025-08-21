package com.example.homebookkeeping

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import java.util.Calendar
import java.util.Date

class SplashActivity : AppCompatActivity() {

    private val db = Firebase.firestore
    private val auth = Firebase.auth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        val currentUser = auth.currentUser
        if (currentUser == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        } else {
            createUserProfileIfNotExists {
                prepareUserBudget {
                    executeRecurringTransactions {
                        startActivity(Intent(this, MainActivity::class.java))
                        finish()
                    }
                }
            }
        }
    }

    private fun createUserProfileIfNotExists(onComplete: () -> Unit) {
        val user = auth.currentUser!!
        val userRef = db.collection("users").document(user.uid)
        userRef.get().addOnSuccessListener {
            if (!it.exists()) {
                val userData = mapOf("email" to user.email)
                userRef.set(userData).addOnCompleteListener { onComplete() }
            } else {
                onComplete()
            }
        }
    }

    private fun prepareUserBudget(onComplete: () -> Unit) {
        val userId = auth.currentUser!!.uid
        BudgetManager.loadCurrentBudget(this)

        if (BudgetManager.currentBudgetId != null) {
            onComplete()
            return
        }

        db.collection("budgets")
            .whereArrayContains("members", userId)
            .get()
            .addOnSuccessListener { querySnapshot ->
                if (!querySnapshot.isEmpty) {
                    val budget = querySnapshot.documents.first()
                    BudgetManager.setCurrentBudget(this, budget.id)
                    onComplete()
                } else {
                    migrateOldDataOrCreateNewBudget(onComplete)
                }
            }
    }

    private fun migrateOldDataOrCreateNewBudget(onComplete: () -> Unit) {
        db.collection("accounts").limit(1).get().addOnSuccessListener { oldAccounts ->
            val user = auth.currentUser!!
            if (!oldAccounts.isEmpty) {
                Log.d("Migration", "Old data found. Starting migration.")
                val newBudgetId = db.collection("budgets").document().id
                val newBudget = Budget(
                    id = newBudgetId,
                    name = "Личный бюджет",
                    ownerId = user.uid,
                    members = mapOf(user.uid to user.email!!)
                )

                val batch = db.batch()
                batch.set(db.collection("budgets").document(newBudgetId), newBudget)

                val migrationTasks = mutableListOf<com.google.android.gms.tasks.Task<*>>()
                val collectionsToMigrate = listOf("accounts", "categories", "transactions", "recurring_transactions")
                for (collectionName in collectionsToMigrate) {
                    val task = db.collection(collectionName).get().addOnSuccessListener { snapshot ->
                        for (doc in snapshot.documents) {
                            val newDocRef = db.collection("budgets").document(newBudgetId).collection(collectionName).document(doc.id)
                            batch.set(newDocRef, doc.data!!)
                            batch.delete(doc.reference)
                        }
                    }
                    migrationTasks.add(task)
                }

                com.google.android.gms.tasks.Tasks.whenAll(migrationTasks).addOnSuccessListener {
                    batch.commit().addOnSuccessListener {
                        Log.d("Migration", "Migration successful!")
                        BudgetManager.setCurrentBudget(this, newBudgetId)
                        onComplete()
                    }
                }

            } else {
                Log.d("Migration", "No old data. Creating new budget.")
                val newBudgetId = db.collection("budgets").document().id
                val newBudget = Budget(
                    id = newBudgetId,
                    name = "Личный бюджет",
                    ownerId = user.uid,
                    members = mapOf(user.uid to user.email!!)
                )
                db.collection("budgets").document(newBudgetId).set(newBudget).addOnSuccessListener {
                    BudgetManager.setCurrentBudget(this, newBudgetId)
                    onComplete()
                }
            }
        }
    }


    private fun executeRecurringTransactions(onComplete: () -> Unit) {
        val budgetId = BudgetManager.currentBudgetId
        if (budgetId == null) {
            onComplete()
            return
        }

        val now = Date()
        db.collection("budgets").document(budgetId).collection("recurring_transactions")
            .whereLessThanOrEqualTo("nextExecutionDate", now)
            .whereEqualTo("isActive", true)
            .get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.isEmpty) {
                    onComplete()
                    return@addOnSuccessListener
                }

                val batch = db.batch()
                val calendar = Calendar.getInstance()

                for (doc in snapshot.documents) {
                    val recurring = doc.toObject(RecurringTransaction::class.java)?.copy(id = doc.id) ?: continue
                    val newTransaction = Transaction(
                        description = recurring.description,
                        amount = recurring.amount,
                        timestamp = recurring.nextExecutionDate,
                        type = recurring.type,
                        accountId = recurring.accountId,
                        categoryId = recurring.categoryId
                    )
                    val newTransactionRef = db.collection("budgets").document(budgetId).collection("transactions").document()
                    batch.set(newTransactionRef, newTransaction)

                    val accountRef = db.collection("budgets").document(budgetId).collection("accounts").document(recurring.accountId)
                    val amountChange = if (recurring.type == "EXPENSE") -recurring.amount else recurring.amount
                    batch.update(accountRef, "balance", FieldValue.increment(amountChange))

                    calendar.time = recurring.nextExecutionDate!!
                    if (recurring.periodicity == "MONTHLY") {
                        calendar.add(Calendar.MONTH, 1)
                    } else if (recurring.periodicity == "WEEKLY") {
                        calendar.add(Calendar.WEEK_OF_YEAR, 1)
                    }
                    val newNextDate = calendar.time
                    val recurringRef = db.collection("budgets").document(budgetId).collection("recurring_transactions").document(recurring.id)
                    batch.update(recurringRef, "nextExecutionDate", newNextDate)
                }

                batch.commit().addOnCompleteListener {
                    onComplete()
                }
            }
            .addOnFailureListener {
                Log.e("SplashActivity", "Error executing recurring transactions", it)
                onComplete()
            }
    }
}
