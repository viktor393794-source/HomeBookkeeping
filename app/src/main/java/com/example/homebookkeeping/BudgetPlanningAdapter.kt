package com.example.homebookkeeping

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.Space
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

class BudgetPlanningAdapter(
    private val context: Context,
    private val categories: List<Category>,
    private val onLimitChanged: () -> Unit
) : RecyclerView.Adapter<BudgetPlanningAdapter.ViewHolder>() {

    private val db = Firebase.firestore

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val indentationSpace: Space = view.findViewById(R.id.indentationSpace)
        val iconContainer: FrameLayout = view.findViewById(R.id.iconContainer)
        val iconImageView: ImageView = view.findViewById(R.id.categoryIconImageView)
        val nameTextView: TextView = view.findViewById(R.id.categoryNameTextView)
        val limitEditText: EditText = view.findViewById(R.id.limitEditText)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_budget_plan, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val category = categories[position]

        holder.nameTextView.text = category.name
        holder.limitEditText.setText(if (category.limit > 0) category.limit.toString() else "")

        val textColor = if (category.type == "EXPENSE") {
            ContextCompat.getColor(context, R.color.colorExpense)
        } else {
            ContextCompat.getColor(context, R.color.colorIncome)
        }
        holder.limitEditText.setTextColor(textColor)

        val indentation = category.level * 48
        holder.indentationSpace.layoutParams.width = indentation
        holder.itemView.requestLayout()

        try {
            val background = holder.iconContainer.background as GradientDrawable
            background.setColor(Color.parseColor(category.backgroundColor))
            val iconResId = getIconResId(context, category.iconName)
            if (iconResId != 0) {
                holder.iconImageView.setImageResource(iconResId)
                holder.iconImageView.setColorFilter(Color.parseColor(category.iconColor))
            }
        } catch (e: Exception) { /* Fallback */ }

        holder.limitEditText.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val newLimit = holder.limitEditText.text.toString().toDoubleOrNull() ?: 0.0
                if (newLimit != category.limit) {
                    db.collection("categories").document(category.id)
                        .update("limit", newLimit)
                        .addOnSuccessListener {
                            category.limit = newLimit
                            onLimitChanged() // Сообщаем активности, что нужно обновить итоги
                        }
                }
            }
        }
    }

    override fun getItemCount() = categories.size

    private fun getIconResId(context: Context, iconName: String): Int {
        return context.resources.getIdentifier(iconName, "drawable", context.packageName)
    }
}
