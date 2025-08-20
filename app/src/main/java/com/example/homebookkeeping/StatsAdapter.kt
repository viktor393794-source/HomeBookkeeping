package com.example.homebookkeeping

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.Space
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.recyclerview.widget.RecyclerView

data class CategoryStat(
    val category: Category,
    var totalAmount: Double,
    var percentage: Double
)

class StatsAdapter(
    private val displayedStats: List<CategoryStat>,
    private val allCategories: List<Category>,
    private val expandedIds: Set<String>,
    private val onItemClick: (CategoryStat) -> Unit
) : RecyclerView.Adapter<StatsAdapter.StatsViewHolder>() {

    class StatsViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val iconContainer: FrameLayout = itemView.findViewById(R.id.iconContainer)
        val categoryIconImageView: ImageView = itemView.findViewById(R.id.categoryIconImageView)
        val categoryNameTextView: TextView = itemView.findViewById(R.id.categoryNameTextView)
        val percentageTextView: TextView = itemView.findViewById(R.id.percentageTextView)
        val spentAmountTextView: TextView = itemView.findViewById(R.id.spentAmountTextView)
        val limitTextView: TextView = itemView.findViewById(R.id.limitTextView)
        val limitProgressBar: ProgressBar = itemView.findViewById(R.id.limitProgressBar)
        val indentationSpace: Space = itemView.findViewById(R.id.indentationSpace)
        val expandIndicator: ImageView = itemView.findViewById(R.id.expandIndicator) // Индикатор
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StatsViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_stat, parent, false)
        return StatsViewHolder(view)
    }

    override fun onBindViewHolder(holder: StatsViewHolder, position: Int) {
        val stat = displayedStats[position]
        val context = holder.itemView.context

        val indentation = stat.category.level * 48
        holder.indentationSpace.layoutParams.width = indentation

        val hasChildren = allCategories.any { it.parentId == stat.category.id }
        if (hasChildren) {
            holder.expandIndicator.visibility = View.VISIBLE
            holder.expandIndicator.rotation = if (expandedIds.contains(stat.category.id)) 90f else 0f
        } else {
            holder.expandIndicator.visibility = View.INVISIBLE
        }

        holder.categoryNameTextView.text = stat.category.name
        holder.percentageTextView.text = String.format("%.1f%%", stat.percentage)
        holder.spentAmountTextView.text = String.format("%,.0f Р", stat.totalAmount)

        if (stat.category.limit > 0 && stat.category.type == "EXPENSE") {
            holder.limitTextView.visibility = View.VISIBLE
            holder.limitTextView.text = String.format("из %,.0f тыс. Р", stat.category.limit / 1000)
            holder.limitProgressBar.visibility = View.VISIBLE
            holder.limitProgressBar.max = stat.category.limit.toInt()
            holder.limitProgressBar.progress = stat.totalAmount.toInt()

            holder.spentAmountTextView.setTextColor(
                if (stat.totalAmount > stat.category.limit) ContextCompat.getColor(context, R.color.colorExpense)
                else ContextCompat.getColor(context, R.color.colorIncome)
            )
        } else {
            holder.limitTextView.visibility = View.GONE
            holder.limitProgressBar.visibility = View.GONE
            val defaultColor = if (stat.category.type == "INCOME") ContextCompat.getColor(context, R.color.colorIncome) else Color.BLACK
            holder.spentAmountTextView.setTextColor(defaultColor)
        }

        try {
            val bgColor = Color.parseColor(stat.category.backgroundColor)
            (holder.iconContainer.background as GradientDrawable).setColor(bgColor)
            val iconResId = getIconResId(context, stat.category.iconName)
            if (iconResId != 0) {
                holder.categoryIconImageView.setImageResource(iconResId)
                holder.categoryIconImageView.setColorFilter(Color.parseColor(stat.category.iconColor))
            }
            val progressBarDrawable = holder.limitProgressBar.progressDrawable as LayerDrawable
            val progressDrawable = progressBarDrawable.findDrawableByLayerId(android.R.id.progress)
            DrawableCompat.setTint(progressDrawable, bgColor)
        } catch (e: Exception) { /* fallback */ }

        holder.itemView.setOnClickListener {
            HapticFeedbackHelper.viberate(context)
            onItemClick(stat)
        }
    }

    override fun getItemCount() = displayedStats.size

    private fun getIconResId(context: Context, iconName: String): Int {
        return context.resources.getIdentifier(iconName, "drawable", context.packageName)
    }
}