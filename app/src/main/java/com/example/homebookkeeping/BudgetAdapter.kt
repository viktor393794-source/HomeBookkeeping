package com.example.homebookkeeping

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class BudgetAdapter(
    private val budgets: List<Budget>,
    private val onItemClicked: (Budget) -> Unit
) : RecyclerView.Adapter<BudgetAdapter.BudgetViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BudgetViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_budget, parent, false)
        return BudgetViewHolder(view)
    }

    override fun onBindViewHolder(holder: BudgetViewHolder, position: Int) {
        val budget = budgets[position]
        holder.bind(budget)
        holder.itemView.setOnClickListener {
            onItemClicked(budget)
        }
    }

    override fun getItemCount(): Int = budgets.size

    class BudgetViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val budgetNameTextView: TextView = itemView.findViewById(R.id.budgetNameTextView)

        fun bind(budget: Budget) {
            budgetNameTextView.text = budget.name
        }
    }
}