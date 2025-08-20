package com.example.homebookkeeping

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Locale

class RecurringTransactionAdapter(
    private val context: Context,
    private val transactions: List<RecurringTransaction>,
    private val categories: List<Category>
) : RecyclerView.Adapter<RecurringTransactionAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val iconContainer: FrameLayout = view.findViewById(R.id.iconContainer)
        val iconImageView: ImageView = view.findViewById(R.id.categoryIconImageView)
        val descriptionTextView: TextView = view.findViewById(R.id.descriptionTextView)
        val periodicityTextView: TextView = view.findViewById(R.id.periodicityTextView)
        val nextExecutionTextView: TextView = view.findViewById(R.id.nextExecutionTextView)
        val amountTextView: TextView = view.findViewById(R.id.amountTextView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_recurring_transaction, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val transaction = transactions[position]
        val category = categories.find { it.id == transaction.categoryId }

        holder.descriptionTextView.text = transaction.description
        holder.periodicityTextView.text = formatPeriodicity(transaction)
        val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
        holder.nextExecutionTextView.text = "Следующее: ${transaction.nextExecutionDate?.let { dateFormat.format(it) } ?: "N/A"}"

        if (transaction.type == "EXPENSE") {
            holder.amountTextView.text = String.format(Locale.GERMANY, "-%,.2f", transaction.amount)
            holder.amountTextView.setTextColor(ContextCompat.getColor(context, R.color.colorExpense))
        } else {
            holder.amountTextView.text = String.format(Locale.GERMANY, "+%,.2f", transaction.amount)
            holder.amountTextView.setTextColor(ContextCompat.getColor(context, R.color.colorIncome))
        }

        if (category != null) {
            try {
                val background = holder.iconContainer.background as GradientDrawable
                background.setColor(Color.parseColor(category.backgroundColor))
                val iconResId = getIconResId(context, category.iconName)
                if (iconResId != 0) {
                    holder.iconImageView.setImageResource(iconResId)
                    holder.iconImageView.setColorFilter(Color.parseColor(category.iconColor))
                }
            } catch (e: Exception) { /* Fallback */ }
        }

        holder.itemView.setOnClickListener {
            val intent = Intent(context, AddRecurringTransactionActivity::class.java)
            intent.putExtra("TRANSACTION_ID", transaction.id)
            context.startActivity(intent)
        }
    }

    override fun getItemCount() = transactions.size

    private fun formatPeriodicity(transaction: RecurringTransaction): String {
        return when (transaction.periodicity) {
            "MONTHLY" -> "Ежемесячно, ${transaction.dayOfMonth} числа"
            "WEEKLY" -> {
                val days = arrayOf("Вс", "Пн", "Вт", "Ср", "Чт", "Пт", "Сб")
                "Еженедельно, по ${days[transaction.dayOfWeek - 1]}"
            }
            "YEARLY" -> "Ежегодно"
            else -> "Неизвестно"
        }
    }

    private fun getIconResId(context: Context, iconName: String): Int {
        return context.resources.getIdentifier(iconName, "drawable", context.packageName)
    }
}
