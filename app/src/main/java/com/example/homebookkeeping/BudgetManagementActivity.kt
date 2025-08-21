package com.example.homebookkeeping

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

class BudgetManagementActivity : AppCompatActivity() {

    private val db = Firebase.firestore
    private val auth = Firebase.auth
    private var currentBudget: Budget? = null

    private lateinit var budgetNameEditText: EditText
    private lateinit var membersRecyclerView: RecyclerView
    private lateinit var inviteEmailEditText: EditText
    private lateinit var inviteButton: Button
    private lateinit var budgetIdTextView: TextView
    private lateinit var shareBudgetIdButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_budget_management)

        findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener { finish() }
        budgetNameEditText = findViewById(R.id.budgetNameEditText)
        membersRecyclerView = findViewById(R.id.membersRecyclerView)
        inviteEmailEditText = findViewById(R.id.inviteEmailEditText)
        inviteButton = findViewById(R.id.inviteButton)
        budgetIdTextView = findViewById(R.id.budgetIdTextView)
        shareBudgetIdButton = findViewById(R.id.shareBudgetIdButton)

        membersRecyclerView.layoutManager = LinearLayoutManager(this)

        loadBudget()
        setupListeners()
    }

    private fun setupListeners() {
        budgetNameEditText.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val newName = budgetNameEditText.text.toString()
                if (newName.isNotBlank() && newName != currentBudget?.name) {
                    currentBudget?.id?.let {
                        db.collection("budgets").document(it)
                            .update("name", newName)
                            .addOnSuccessListener {
                                Toast.makeText(this, "Название бюджета обновлено", Toast.LENGTH_SHORT).show()
                            }
                    }
                }
            }
        }

        inviteButton.setOnClickListener {
            inviteUserByEmail()
        }

        shareBudgetIdButton.setOnClickListener {
            val budgetId = budgetIdTextView.text.toString()
            if (budgetId.isNotEmpty()) {
                val sendIntent: Intent = Intent().apply {
                    action = Intent.ACTION_SEND
                    putExtra(Intent.EXTRA_TEXT, "Привет! Присоединяйся к моему бюджету в приложении 'Свиной Бюджет'. Вот ID для подключения: $budgetId")
                    type = "text/plain"
                }
                val shareIntent = Intent.createChooser(sendIntent, "Поделиться ID бюджета через")
                startActivity(shareIntent)
            }
        }
    }

    private fun loadBudget() {
        val budgetId = BudgetManager.currentBudgetId ?: return
        db.collection("budgets").document(budgetId).addSnapshotListener { snapshot, error ->
            if (error != null) {
                // Обработка ошибки загрузки
                return@addSnapshotListener
            }

            if (snapshot != null && snapshot.exists()) {
                // --- ГЛАВНОЕ ИЗМЕНЕНИЕ ЗДЕСЬ ---
                // 1. Преобразуем документ в объект Budget
                // 2. С помощью .copy() сразу же вставляем в него ID самого документа
                currentBudget = snapshot.toObject(Budget::class.java)?.copy(id = snapshot.id)
                updateUI()
            }
        }
    }

    private fun updateUI() {
        currentBudget?.let { budget ->
            budgetNameEditText.setText(budget.name)
            // Теперь в поле id гарантированно будет правильный ID
            budgetIdTextView.text = budget.id

            val membersList = budget.members.values.toList()
            membersRecyclerView.adapter = MemberAdapter(membersList)

            val isOwner = auth.currentUser?.uid == budget.ownerId
            inviteButton.isEnabled = isOwner
            shareBudgetIdButton.isEnabled = isOwner
        }
    }

    private fun inviteUserByEmail() {
        val email = inviteEmailEditText.text.toString().trim()
        if (email.isBlank()) {
            Toast.makeText(this, "Введите email", Toast.LENGTH_SHORT).show()
            return
        }

        db.collection("users").whereEqualTo("email", email).limit(1).get()
            .addOnSuccessListener { userSnapshot ->
                if (userSnapshot.isEmpty) {
                    Toast.makeText(this, "Пользователь с таким email не найден", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }
                val invitedUser = userSnapshot.documents.first()
                val invitedUserId = invitedUser.id

                if (currentBudget?.members?.containsKey(invitedUserId) == true) {
                    Toast.makeText(this, "Пользователь уже в этом бюджете", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                val budgetId = BudgetManager.currentBudgetId!!
                db.collection("budgets").document(budgetId)
                    .update("members.${invitedUserId}", email)
                    .addOnSuccessListener {
                        Toast.makeText(this, "Пользователь $email приглашен!", Toast.LENGTH_SHORT).show()
                        inviteEmailEditText.text.clear()
                    }
            }
    }
}