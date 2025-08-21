package com.example.homebookkeeping

import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

class LoginActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private val db = Firebase.firestore

    private lateinit var emailEditText: EditText
    private lateinit var passwordEditText: EditText
    private lateinit var loginButton: Button
    private lateinit var registerTextView: TextView
    private lateinit var progressBar: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        auth = Firebase.auth

        emailEditText = findViewById(R.id.editTextEmail)
        passwordEditText = findViewById(R.id.editTextPassword)
        loginButton = findViewById(R.id.buttonLogin)
        registerTextView = findViewById(R.id.textViewRegister)
        progressBar = findViewById(R.id.progressBar)

        loginButton.setOnClickListener { handleLogin() }
        registerTextView.setOnClickListener { handleRegistration() }
    }

    override fun onStart() {
        super.onStart()
        // Если пользователь уже вошел в систему, сразу переходим на следующий экран
        val currentUser = auth.currentUser
        if (currentUser != null) {
            navigateToNextScreen()
        }
    }

    private fun handleLogin() {
        val email = emailEditText.text.toString().trim()
        val password = passwordEditText.text.toString().trim()

        if (!validateForm(email, password)) return

        progressBar.visibility = View.VISIBLE
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                progressBar.visibility = View.GONE
                if (task.isSuccessful) {
                    Toast.makeText(this, "Вход выполнен успешно.", Toast.LENGTH_SHORT).show()
                    navigateToNextScreen()
                } else {
                    Toast.makeText(this, "Ошибка входа: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                }
            }
    }

    private fun handleRegistration() {
        val email = emailEditText.text.toString().trim()
        val password = passwordEditText.text.toString().trim()

        if (!validateForm(email, password, isRegistration = true)) return

        progressBar.visibility = View.VISIBLE
        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    // --- ВАЖНОЕ ИЗМЕНЕНИЕ: Создаем запись о пользователе в Firestore ---
                    createUserDocumentInFirestore(user)
                } else {
                    progressBar.visibility = View.GONE
                    Toast.makeText(this, "Ошибка регистрации: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                }
            }
    }

    private fun createUserDocumentInFirestore(user: FirebaseUser?) {
        if (user == null) {
            progressBar.visibility = View.GONE
            Toast.makeText(this, "Не удалось создать пользователя.", Toast.LENGTH_SHORT).show()
            return
        }
        // Создаем Map с данными пользователя (пока только email)
        val userMap = hashMapOf(
            "email" to user.email
        )

        // Сохраняем документ в коллекции "users" с ID, совпадающим с Auth UID
        db.collection("users").document(user.uid)
            .set(userMap)
            .addOnSuccessListener {
                progressBar.visibility = View.GONE
                Toast.makeText(this, "Регистрация выполнена успешно.", Toast.LENGTH_SHORT).show()
                navigateToNextScreen()
            }
            .addOnFailureListener { e ->
                progressBar.visibility = View.GONE
                Log.e("LoginActivity", "Ошибка сохранения пользователя в Firestore", e)
                Toast.makeText(this, "Ошибка регистрации: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun navigateToNextScreen() {
        // После успешного входа или регистрации всегда переходим на экран выбора бюджета
        val intent = Intent(this, BudgetSelectionActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }

    private fun validateForm(email: String, password: String, isRegistration: Boolean = false): Boolean {
        if (TextUtils.isEmpty(email)) {
            emailEditText.error = "Введите email"
            return false
        }
        if (TextUtils.isEmpty(password)) {
            passwordEditText.error = "Введите пароль"
            return false
        }
        if (isRegistration && password.length < 6) {
            passwordEditText.error = "Пароль должен быть не менее 6 символов"
            return false
        }
        return true
    }
}