package com.example.homebookkeeping

import android.content.Context
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

class AccountSummaryAdapter(private val accounts: List<Account>) :
    RecyclerView.Adapter<AccountSummaryAdapter.AccountSummaryViewHolder>() {

    class AccountSummaryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val iconContainer: FrameLayout = itemView.findViewById(R.id.iconContainer)
        val iconImageView: ImageView = itemView.findViewById(R.id.accountIconImageView)
        val nameTextView: TextView = itemView.findViewById(R.id.accountNameTextView)
        val balanceTextView: TextView = itemView.findViewById(R.id.accountBalanceTextView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AccountSummaryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_account_summary, parent, false)
        return AccountSummaryViewHolder(view)
    }

    override fun onBindViewHolder(holder: AccountSummaryViewHolder, position: Int) {
        val account = accounts[position]
        val context = holder.itemView.context

        holder.nameTextView.text = account.name
        holder.balanceTextView.text = String.format(Locale.GERMANY, "%,.2f", account.balance)

        // Раскрашиваем баланс
        val balanceColor = if (account.balance < 0) {
            ContextCompat.getColor(context, R.color.colorExpense)
        } else {
            ContextCompat.getColor(context, R.color.colorIncome)
        }
        holder.balanceTextView.setTextColor(balanceColor)

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
    }

    override fun getItemCount() = accounts.size

    private fun getIconResId(context: Context, iconName: String): Int {
        return context.resources.getIdentifier(iconName, "drawable", context.packageName)
    }
}
