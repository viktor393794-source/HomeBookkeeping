package com.example.homebookkeeping

import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

// Финальная версия шаблона для всех операций
data class Transaction(
    var id: String = "",
    var description: String = "",
    var amount: Double = 0.0,
    @ServerTimestamp var timestamp: Date? = null,
    var type: String = "EXPENSE", // "EXPENSE", "INCOME" или "TRANSFER"
    var accountId: String = "",   // Для Дохода/Расхода: счет. Для Перевода: счет "ОТКУДА".
    var categoryId: String = "",  // Для Перевода это поле будет пустым.
    var toAccountId: String = "" // Для Перевода: счет "КУДА".
)
