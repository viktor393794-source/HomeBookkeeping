package com.example.homebookkeeping

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FieldValue
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_budget_management)

        findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener { finish() }
        budgetNameEditText = findViewById(R.id.budgetNameEditText)
        membersRecyclerView = findViewById(R.id.membersRecyclerView)
        inviteEmailEditText = findViewById(R.id.inviteEmailEditText)
        inviteButton = findViewById(R.id.inviteButton)

        membersRecyclerView.layoutManager = LinearLayoutManager(this)

        loadBudget()
        setupListeners()
    }

    private fun setupListeners() {
        budgetNameEditText.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val newName = budgetNameEditText.text.toString()
                if (newName.isNotBlank() && newName != currentBudget?.name) {
                    db.collection("budgets").document(currentBudget!!.id)
                        .update("name", newName)
                        .addOnSuccessListener {
                            Toast.makeText(this, "Название бюджета обновлено", Toast.LENGTH_SHORT).show()
                        }
                }
            }
        }

        inviteButton.setOnClickListener {
            inviteUser()
        }
    }

    private fun loadBudget() {
        val budgetId = BudgetManager.currentBudgetId ?: return
        db.collection("budgets").document(budgetId).addSnapshotListener { snapshot, _ ->
            if (snapshot != null && snapshot.exists()) {
                currentBudget = snapshot.toObject(Budget::class.java)
                updateUI()
            }
        }
    }

    private fun updateUI() {
        currentBudget?.let {
            budgetNameEditText.setText(it.name)
            val membersList = it.members.values.toList()
            membersRecyclerView.adapter = MemberAdapter(membersList)

            // Только владелец может приглашать
            inviteButton.isEnabled = auth.currentUser?.uid == it.ownerId
        }
    }

    private fun inviteUser() {
        val email = inviteEmailEditText.text.toString().trim()
        if (email.isBlank()) {
            Toast.makeText(this, "Введите email", Toast.LENGTH_SHORT).show()
            return
        }

        // 1. Находим пользователя по email
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

                // 2. Добавляем пользователя в бюджет
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
