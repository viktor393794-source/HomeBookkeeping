package com.example.homebookkeeping

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.Space
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView

interface CategoryClickListener {
    fun onCategoryClick(category: Category)
    fun onEditCategoryClick(category: Category) // Изменили, чтобы клик по всей строке раскрывал/сворачивал
    fun onAddSubCategoryClick(parentCategory: Category)
}

class CategoryAdapter(
    private val displayedCategories: List<Category>, // Список видимых категорий
    private val allCategories: List<Category>, // Полный список всех категорий
    private val transactions: List<Transaction>,
    private val expandedIds: Set<String>, // Набор ID раскрытых категорий
    private val clickListener: CategoryClickListener
) : RecyclerView.Adapter<CategoryAdapter.CategoryViewHolder>() {

    class CategoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val iconContainer: FrameLayout = itemView.findViewById(R.id.iconContainer)
        val iconImageView: ImageView = itemView.findViewById(R.id.categoryIconImageView)
        val nameTextView: TextView = itemView.findViewById(R.id.categoryNameTextView)
        val typeTextView: TextView = itemView.findViewById(R.id.categoryTypeTextView)
        val limitInfoTextView: TextView = itemView.findViewById(R.id.limitInfoTextView)
        val indentationSpace: Space = itemView.findViewById(R.id.indentationSpace)
        val addSubCategoryButton: ImageButton = itemView.findViewById(R.id.addSubCategoryButton)
        val editCategoryButton: ImageButton = itemView.findViewById(R.id.editCategoryButton)
        val expandIndicator: ImageView = itemView.findViewById(R.id.expandIndicator) // Индикатор
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_category, parent, false)
        return CategoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: CategoryViewHolder, position: Int) {
        val category = displayedCategories[position]
        val context = holder.itemView.context

        // --- ЛОГИКА ОТОБРАЖЕНИЯ ---
        val indentation = category.level * 48
        holder.indentationSpace.layoutParams.width = indentation
        holder.nameTextView.text = category.name
        holder.typeTextView.text = if (category.type == "EXPENSE") "Расход" else "Доход"

        // Проверяем, есть ли у категории дочерние элементы
        val hasChildren = allCategories.any { it.parentId == category.id }

        if (hasChildren) {
            holder.expandIndicator.visibility = View.VISIBLE
            // Поворачиваем стрелочку в зависимости от состояния
            holder.expandIndicator.rotation = if (expandedIds.contains(category.id)) 90f else 0f
        } else {
            holder.expandIndicator.visibility = View.INVISIBLE
        }

        // --- Отрисовка иконки и лимитов (без изменений) ---
        try {
            val background = holder.iconContainer.background as GradientDrawable
            background.setColor(Color.parseColor(category.backgroundColor))
            val iconResId = getIconResId(context, category.iconName)
            if (iconResId != 0) {
                holder.iconImageView.setImageResource(iconResId)
                holder.iconImageView.setColorFilter(Color.parseColor(category.iconColor))
            }
        } catch (e: Exception) { /* ... */ }

        if (category.type == "EXPENSE" && category.limit > 0) {
            val spentThisMonth = transactions.filter { it.categoryId == category.id }.sumOf { it.amount }
            val remainder = category.limit - spentThisMonth
            holder.limitInfoTextView.visibility = View.VISIBLE
            holder.limitInfoTextView.text = String.format("Остаток: %.2f", remainder)
            holder.limitInfoTextView.setTextColor(if (remainder < 0) Color.RED else ContextCompat.getColor(context, android.R.color.darker_gray))
        } else {
            holder.limitInfoTextView.visibility = View.GONE
        }

        holder.addSubCategoryButton.visibility = if (category.level >= 2) View.GONE else View.VISIBLE

        // --- ОБРАБОТКА КЛИКОВ ---
        holder.itemView.setOnClickListener {
            HapticFeedbackHelper.viberate(context)
            clickListener.onCategoryClick(category) // Клик по всей строке для expand/collapse
        }
        holder.editCategoryButton.setOnClickListener {
            HapticFeedbackHelper.viberate(context)
            clickListener.onEditCategoryClick(category) // Клик по кнопке "изменить"
        }
        holder.addSubCategoryButton.setOnClickListener {
            HapticFeedbackHelper.viberate(context)
            clickListener.onAddSubCategoryClick(category)
        }
    }

    override fun getItemCount() = displayedCategories.size

    private fun getIconResId(context: Context, iconName: String): Int {
        return context.resources.getIdentifier(iconName, "drawable", context.packageName)
    }
}