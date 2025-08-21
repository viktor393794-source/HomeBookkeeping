package com.example.homebookkeeping

import android.content.Context

// Простой менеджер для хранения ID текущего активного бюджета
object BudgetManager {
    var currentBudgetId: String? = null
        private set

    private const val PREFS_NAME = "budget_prefs"
    private const val KEY_BUDGET_ID = "current_budget_id"

    fun loadCurrentBudget(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        currentBudgetId = prefs.getString(KEY_BUDGET_ID, null)
    }

    fun setCurrentBudget(context: Context, budgetId: String) {
        currentBudgetId = budgetId
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_BUDGET_ID, budgetId).apply()
    }

    fun clearCurrentBudget(context: Context) {
        currentBudgetId = null
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_BUDGET_ID).apply()
    }
}
