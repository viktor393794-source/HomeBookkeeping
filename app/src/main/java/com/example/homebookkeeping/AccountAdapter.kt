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
import java.util.Locale

class AccountAdapter(private val accounts: List<Account>) :
    RecyclerView.Adapter<AccountAdapter.AccountViewHolder>() {

    class AccountViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val iconContainer: FrameLayout = itemView.findViewById(R.id.iconContainer)
        val iconImageView: ImageView = itemView.findViewById(R.id.accountIconImageView)
        val nameTextView: TextView = itemView.findViewById(R.id.accountNameTextView)
        val balanceTextView: TextView = itemView.findViewById(R.id.accountBalanceTextView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AccountViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_account, parent, false)
        return AccountViewHolder(view)
    }

    override fun onBindViewHolder(holder: AccountViewHolder, position: Int) {
        val account = accounts[position]
        val context = holder.itemView.context

        holder.nameTextView.text = account.name
        holder.balanceTextView.text = String.format(Locale.GERMANY, "%,.2f", account.balance)

        // --- НОВАЯ ЛОГИКА: РАСКРАШИВАЕМ БАЛАНС ---
        val balanceColor = if (account.balance < 0) {
            ContextCompat.getColor(context, R.color.colorExpense) // Красный для отрицательного
        } else {
            ContextCompat.getColor(context, R.color.colorIncome)  // Зеленый для положительного
        }
        holder.balanceTextView.setTextColor(balanceColor)
        // --- КОНЕЦ НОВОЙ ЛОГИКИ ---

        try {
            val background = holder.iconContainer.background as GradientDrawable
            background.setColor(Color.parseColor(account.backgroundColor))

            val iconResId = getIconResId(context, account.iconName)
            if (iconResId != 0) {
                holder.iconImageView.setImageResource(iconResId)
                holder.iconImageView.setColorFilter(Color.parseColor(account.iconColor))
            }
        } catch (e: Exception) {
            // Fallback
        }

        holder.itemView.setOnClickListener {
            HapticFeedbackHelper.viberate(context)
            val intent = Intent(context, EditAccountActivity::class.java)
            intent.putExtra("ACCOUNT_ID", account.id)
            context.startActivity(intent)
        }
    }

    override fun getItemCount() = accounts.size

    private fun getIconResId(context: Context, iconName: String): Int {
        return context.resources.getIdentifier(iconName, "drawable", context.packageName)
    }
}
