package com.example.homebookkeeping

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView

class IconAdapter(
    private val iconNames: List<String>,
    private val onIconClick: (String) -> Unit
) : RecyclerView.Adapter<IconAdapter.IconViewHolder>() {

    class IconViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val iconImageView: ImageView = itemView.findViewById(R.id.iconImageView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): IconViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_icon, parent, false)
        return IconViewHolder(view)
    }

    override fun onBindViewHolder(holder: IconViewHolder, position: Int) {
        val iconName = iconNames[position]
        val context = holder.itemView.context
        holder.iconImageView.setImageResource(getIconResId(context, iconName))
        holder.itemView.setOnClickListener {
            HapticFeedbackHelper.viberate(context)
            onIconClick(iconName)
        }
    }

    override fun getItemCount() = iconNames.size

    private fun getIconResId(context: Context, iconName: String): Int {
        return context.resources.getIdentifier(iconName, "drawable", context.packageName)
    }
}
