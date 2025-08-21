package com.example.homebookkeeping

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView // <-- ЭТА СТРОКА БЫЛА ПРОПУЩЕНА
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

class BudgetSelectionActivity : AppCompatActivity() {

    private val db = Firebase.firestore
    private val auth = Firebase.auth
    private lateinit var budgetsAdapter: BudgetAdapter
    private val budgetsList = mutableListOf<Budget>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_budget_selection)

        val budgetsRecyclerView: RecyclerView = findViewById(R.id.budgetsRecyclerView)
        val createBudgetButton: Button = findViewById(R.id.createBudgetButton)
        val joinBudgetButton: Button = findViewById(R.id.joinBudgetButton)

        budgetsAdapter = BudgetAdapter(budgetsList) { selectedBudget ->
            BudgetManager.setCurrentBudget(this, selectedBudget.id)
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
        budgetsRecyclerView.adapter = budgetsAdapter
        budgetsRecyclerView.layoutManager = LinearLayoutManager(this)

        createBudgetButton.setOnClickListener {
            showCreateBudgetDialog()
        }

        joinBudgetButton.setOnClickListener {
            showJoinBudgetDialog()
        }

        loadUserBudgets()
    }

    private fun showJoinBudgetDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Присоединиться к бюджету")

        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.setPadding(48, 24, 48, 24)

        val infoText = TextView(this)
        infoText.text = "Попросите владельца бюджета поделиться с вами ID бюджета и вставьте его сюда."
        layout.addView(infoText)

        val input = EditText(this).apply {
            hint = "ID Бюджета"
            inputType = InputType.TYPE_CLASS_TEXT
            maxLines = 1
        }
        layout.addView(input)

        builder.setView(layout)

        builder.setPositiveButton("Присоединиться") { dialog, _ ->
            val budgetId = input.text.toString().trim()
            if (budgetId.isNotEmpty()) {
                joinBudget(budgetId)
            } else {
                Toast.makeText(this, "ID не может быть пустым", Toast.LENGTH_SHORT).show()
            }
            dialog.dismiss()
        }
        builder.setNegativeButton("Отмена") { dialog, _ -> dialog.cancel() }
        builder.show()
    }

    private fun joinBudget(budgetId: String) {
        val user = auth.currentUser
        if (user == null || user.email == null) {
            Toast.makeText(this, "Ошибка: не удалось определить пользователя", Toast.LENGTH_SHORT).show()
            return
        }

        val budgetRef = db.collection("budgets").document(budgetId)

        db.runTransaction { transaction ->
            val snapshot = transaction.get(budgetRef)
            if (!snapshot.exists()) {
                throw Exception("Бюджет с таким ID не найден.")
            }

            transaction.update(budgetRef, "members.${user.uid}", user.email)
            null
        }.addOnSuccessListener {
            Toast.makeText(this, "Вы успешно присоединились к бюджету!", Toast.LENGTH_SHORT).show()
            loadUserBudgets()
        }.addOnFailureListener { e ->
            Toast.makeText(this, "Ошибка: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun loadUserBudgets() {
        val user = auth.currentUser
        if (user == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }
        val userId = user.uid

        db.collection("budgets")
            .whereGreaterThan("members.$userId", "")
            .get()
            .addOnSuccessListener { documents ->
                budgetsList.clear()
                for (doc in documents) {
                    try {
                        val budget = doc.toObject(Budget::class.java).copy(id = doc.id)
                        budgetsList.add(budget)
                    } catch (e: Exception) {
                        Log.e("BudgetSelection", "Ошибка преобразования документа ${doc.id}", e)
                        Toast.makeText(this, "Ошибка при чтении бюджета: ${doc.id}", Toast.LENGTH_SHORT).show()
                    }
                }
                budgetsAdapter.notifyDataSetChanged()
                if (budgetsList.isEmpty() && documents.isEmpty) {
                    // Toast.makeText(this, "У вас пока нет бюджетов. Создайте новый!", Toast.LENGTH_LONG).show()
                }
            }
            .addOnFailureListener { e ->
                Log.e("BudgetSelection", "Ошибка загрузки бюджетов", e)
                Toast.makeText(this, "Ошибка загрузки бюджетов: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showCreateBudgetDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Создать новый бюджет")

        val input = EditText(this)
        input.hint = "Название бюджета (например, Личный)"
        builder.setView(input)

        builder.setPositiveButton("Создать") { dialog, _ ->
            val budgetName = input.text.toString().trim()
            if (budgetName.isNotEmpty()) {
                createNewBudget(budgetName)
            } else {
                Toast.makeText(this, "Название не может быть пустым", Toast.LENGTH_SHORT).show()
            }
            dialog.dismiss()
        }
        builder.setNegativeButton("Отмена") { dialog, _ -> dialog.cancel() }
        builder.show()
    }

    private fun createNewBudget(name: String) {
        val user = auth.currentUser ?: return
        val newBudget = Budget(
            name = name,
            ownerId = user.uid,
            members = mapOf(user.uid to (user.email ?: "no-email"))
        )

        db.collection("budgets").add(newBudget)
            .addOnSuccessListener { documentReference ->
                Toast.makeText(this, "Бюджет '$name' создан!", Toast.LENGTH_SHORT).show()
                BudgetManager.setCurrentBudget(this, documentReference.id)
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Ошибка создания: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}