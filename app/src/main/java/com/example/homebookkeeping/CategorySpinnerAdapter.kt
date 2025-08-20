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
import android.widget.Space
import android.widget.TextView

class CategorySpinnerAdapter(context: Context, private val categories: List<Category>) :
    ArrayAdapter<Category>(context, 0, categories) {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        return createView(position, convertView, parent)
    }

    override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
        return createView(position, convertView, parent)
    }

    private fun createView(position: Int, recycledView: View?, parent: ViewGroup): View {
        val category = getItem(position) ?: return View(context)

        val view = recycledView ?: LayoutInflater.from(context).inflate(
            R.layout.spinner_item_category, parent, false
        )

        val indentationSpace: Space = view.findViewById(R.id.indentationSpace)
        val iconContainer: FrameLayout = view.findViewById(R.id.iconContainer)
        val iconImageView: ImageView = view.findViewById(R.id.categoryIconImageView)
        val nameTextView: TextView = view.findViewById(R.id.categoryNameTextView)

        nameTextView.text = category.name

        val indentation = category.level * 48 // 48 пикселей на каждый уровень вложенности
        indentationSpace.layoutParams.width = indentation
        view.requestLayout()

        try {
            val background = iconContainer.background as GradientDrawable
            background.setColor(Color.parseColor(category.backgroundColor))
            val iconResId = getIconResId(context, category.iconName)
            if (iconResId != 0) {
                iconImageView.setImageResource(iconResId)
                iconImageView.setColorFilter(Color.parseColor(category.iconColor))
            }
        } catch (e: Exception) { /* Fallback */ }

        return view
    }

    private fun getIconResId(context: Context, iconName: String): Int {
        return context.resources.getIdentifier(iconName, "drawable", context.packageName)
    }
}
