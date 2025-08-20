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

class TransactionAdapter(
    private val transactions: List<Transaction>,
    private val categories: List<Category>,
    private val accounts: List<Account>
) : RecyclerView.Adapter<TransactionAdapter.TransactionViewHolder>() {

    class TransactionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val iconContainer: FrameLayout = itemView.findViewById(R.id.iconContainer)
        val categoryIconImageView: ImageView = itemView.findViewById(R.id.categoryIconImageView)
        val descriptionTextView: TextView = itemView.findViewById(R.id.descriptionTextView)
        val accountNameTextView: TextView = itemView.findViewById(R.id.accountNameTextView)
        val amountTextView: TextView = itemView.findViewById(R.id.amountTextView)
        val dateTextView: TextView = itemView.findViewById(R.id.dateTextView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TransactionViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_transaction, parent, false)
        return TransactionViewHolder(view)
    }

    override fun onBindViewHolder(holder: TransactionViewHolder, position: Int) {
        val transaction = transactions[position]
        val context = holder.itemView.context

        transaction.timestamp?.let {
            val format = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
            holder.dateTextView.text = format.format(it)
        }

        if (transaction.type == "TRANSFER") {
            val fromAccount = accounts.find { it.id == transaction.accountId }
            val toAccount = accounts.find { it.id == transaction.toAccountId }

            holder.descriptionTextView.text = transaction.description.ifEmpty { "Перевод" }
            holder.accountNameTextView.text = "С: ${fromAccount?.name ?: "?"} → На: ${toAccount?.name ?: "?"}"
            holder.amountTextView.text = String.format(Locale.GERMANY, "%,.2f", transaction.amount)
            holder.amountTextView.setTextColor(Color.DKGRAY)

            val background = holder.iconContainer.background as GradientDrawable
            background.setColor(Color.parseColor("#9E9E9E"))
            holder.categoryIconImageView.setImageResource(R.drawable.ic_transfer)
            holder.categoryIconImageView.setColorFilter(Color.WHITE)

        } else {
            val category = categories.find { it.id == transaction.categoryId }
            val account = accounts.find { it.id == transaction.accountId }

            holder.descriptionTextView.text = transaction.description.ifEmpty { category?.name ?: "Без категории" }
            holder.accountNameTextView.text = account?.name ?: "Счет не найден"

            if (transaction.type == "EXPENSE") {
                holder.amountTextView.text = String.format(Locale.GERMANY, "-%,.2f", transaction.amount)
                holder.amountTextView.setTextColor(ContextCompat.getColor(context, android.R.color.holo_red_dark))
            } else {
                holder.amountTextView.text = String.format(Locale.GERMANY, "+%,.2f", transaction.amount)
                holder.amountTextView.setTextColor(ContextCompat.getColor(context, android.R.color.holo_green_dark))
            }

            if (category != null) {
                val background = holder.iconContainer.background as GradientDrawable
                try {
                    background.setColor(Color.parseColor(category.backgroundColor))
                } catch (e: Exception) {
                    background.setColor(Color.GRAY)
                }

                val iconResId = getIconResId(context, category.iconName)
                if (iconResId != 0) {
                    holder.categoryIconImageView.setImageResource(iconResId)
                    try {
                        holder.categoryIconImageView.setColorFilter(Color.parseColor(category.iconColor))
                    } catch (e: Exception) {
                        holder.categoryIconImageView.setColorFilter(Color.WHITE)
                    }
                } else {
                    holder.categoryIconImageView.setImageDrawable(null)
                }
            } else {
                (holder.iconContainer.background as GradientDrawable).setColor(Color.LTGRAY)
                holder.categoryIconImageView.setImageResource(R.drawable.ic_launcher_background)
            }
        }

        holder.itemView.setOnClickListener {
            HapticFeedbackHelper.viberate(context)
            val intent = if (transaction.type == "TRANSFER") {
                Intent(context, EditTransferActivity::class.java)
            } else {
                Intent(context, EditTransactionActivity::class.java)
            }
            intent.putExtra("TRANSACTION_ID", transaction.id)
            context.startActivity(intent)
        }
    }

    override fun getItemCount() = transactions.size

    private fun getIconResId(context: Context, iconName: String): Int {
        return context.resources.getIdentifier(iconName, "drawable", context.packageName)
    }
}
