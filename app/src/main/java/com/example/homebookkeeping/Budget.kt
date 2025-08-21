package com.example.homebookkeeping

// Финальная модель для хранения информации о бюджете
data class Budget(
    var id: String = "",
    var name: String = "Личный бюджет",
    var ownerId: String = "", // UID пользователя, который создал бюджет
    var members: Map<String, String> = mapOf() // Map<UID, Email> всех участников
)
