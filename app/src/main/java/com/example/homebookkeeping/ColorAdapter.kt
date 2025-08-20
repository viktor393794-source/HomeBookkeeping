package com.example.homebookkeeping

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView

class ColorAdapter(
    private val colorHexes: List<String>,
    private val onColorClick: (String) -> Unit
) : RecyclerView.Adapter<ColorAdapter.ColorViewHolder>() {

    class ColorViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val colorCardView: CardView = itemView.findViewById(R.id.colorCardView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ColorViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_color, parent, false)
        return ColorViewHolder(view)
    }

    override fun onBindViewHolder(holder: ColorViewHolder, position: Int) {
        val colorHex = colorHexes[position]
        val context = holder.itemView.context
        holder.colorCardView.setCardBackgroundColor(Color.parseColor(colorHex))
        holder.itemView.setOnClickListener {
            HapticFeedbackHelper.viberate(context)
            onColorClick(colorHex)
        }
    }

    override fun getItemCount() = colorHexes.size
}
