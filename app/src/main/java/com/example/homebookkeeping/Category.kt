package com.example.homebookkeeping

// Модель с поддержкой иерархии (подкатегорий)
data class Category(
    var id: String = "",
    var name: String = "",
    var type: String = "EXPENSE",
    var iconName: String = "",
    var iconColor: String = "#FFFFFF",
    var backgroundColor: String = "#2196F3",
    var limit: Double = 0.0, // Лимит трат по категории (0 - лимита нет)

    // --- НОВЫЕ ПОЛЯ ---
    var parentId: String = "", // ID родительской категории. Пусто - значит, это категория верхнего уровня.
    var level: Int = 0 // Уровень вложенности: 0, 1, 2
)