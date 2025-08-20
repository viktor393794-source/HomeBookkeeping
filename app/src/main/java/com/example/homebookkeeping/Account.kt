package com.example.homebookkeeping

// Обновленный шаблон с полем includeInTotal
data class Account(
    var id: String = "",
    var name: String = "",
    var balance: Double = 0.0,
    var iconName: String = "ic_default_wallet",
    var iconColor: String = "#FFFFFF",
    var backgroundColor: String = "#607D8B",
    var includeInTotal: Boolean = true // Новое поле: true - учитывать, false - не учитывать
)
