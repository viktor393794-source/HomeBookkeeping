package com.example.homebookkeeping

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import java.util.Locale

class AccountSpinnerAdapter(context: Context, private val accounts: List<Account>) :
    ArrayAdapter<Account>(context, 0, accounts) {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        return createView(position, convertView, parent, false)
    }

    override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
        return createView(position, convertView, parent, true)
    }

    private fun createView(position: Int, recycledView: View?, parent: ViewGroup, isDropDown: Boolean): View {
        val account = getItem(position) ?: return View(context)

        val view = recycledView ?: LayoutInflater.from(context).inflate(
            R.layout.spinner_item_account, parent, false
        )

        val iconContainer: FrameLayout = view.findViewById(R.id.iconContainer)
        val iconImageView: ImageView = view.findViewById(R.id.accountIconImageView)
        val nameTextView: TextView = view.findViewById(R.id.accountNameTextView)
        val balanceTextView: TextView = view.findViewById(R.id.accountBalanceTextView)

        nameTextView.text = account.name
        balanceTextView.text = String.format(Locale.GERMANY, "%,.2f Р", account.balance)

        val balanceColor = if (account.balance < 0) {
            ContextCompat.getColor(context, R.color.colorExpense)
        } else {
            ContextCompat.getColor(context, R.color.colorIncome)
        }
        balanceTextView.setTextColor(balanceColor)

        // Для уже выбранного элемента (не в выпадающем списке) баланс можно скрыть, чтобы не мешал
        balanceTextView.visibility = if (isDropDown) View.VISIBLE else View.GONE

        try {
            val background = iconContainer.background as GradientDrawable
            background.setColor(Color.parseColor(account.backgroundColor))
            val iconResId = getIconResId(context, account.iconName)
            if (iconResId != 0) {
                iconImageView.setImageResource(iconResId)
                iconImageView.setColorFilter(Color.parseColor(account.iconColor))
            }
        } catch (e: Exception) { /* Fallback */ }

        return view
    }

    private fun getIconResId(context: Context, iconName: String): Int {
        return context.resources.getIdentifier(iconName, "drawable", context.packageName)
    }
}
