package com.example.homebookkeeping

import android.app.Activity
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

class ColorPickerActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_color_picker)

        val colorsRecyclerView: RecyclerView = findViewById(R.id.colorsRecyclerView)

        // Наша палитра. Вы можете добавлять сюда любые цвета в формате #RRGGBB
        val colorList = listOf(
            "#FFFFFF", "#F44336", "#E91E63", "#9C27B0", "#673AB7", // Добавили белый
            "#3F51B5", "#2196F3", "#03A9F4", "#00BCD4", "#009688",
            "#4CAF50", "#8BC34A", "#CDDC39", "#FFEB3B", "#FFC107",
            "#FF9800", "#FF5722", "#795548", "#9E9E9E", "#607D8B",
            "#000000"
        )

        val adapter = ColorAdapter(colorList) { selectedColorHex ->
            val resultIntent = Intent()
            resultIntent.putExtra("selectedColorHex", selectedColorHex)
            setResult(Activity.RESULT_OK, resultIntent)
            finish()
        }

        colorsRecyclerView.layoutManager = GridLayoutManager(this, 5)
        colorsRecyclerView.adapter = adapter
    }
}
