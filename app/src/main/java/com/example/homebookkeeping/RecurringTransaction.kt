package com.example.homebookkeeping

import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

// Модель для хранения шаблона регулярной операции
data class RecurringTransaction(
    var id: String = "",
    var description: String = "",
    var amount: Double = 0.0,
    var type: String = "EXPENSE", // "EXPENSE" или "INCOME"
    var accountId: String = "",
    var categoryId: String = "",

    // Новые поля для периодичности
    var periodicity: String = "MONTHLY", // "MONTHLY", "WEEKLY", "YEARLY"
    var dayOfMonth: Int = 1, // Число месяца (1-31)
    var dayOfWeek: Int = 1, // День недели (1-7, где 1=Воскресенье) - для еженедельных
    @ServerTimestamp var nextExecutionDate: Date? = null, // Дата следующего исполнения
    var isActive: Boolean = true // Флаг, активен ли шаблон
)
