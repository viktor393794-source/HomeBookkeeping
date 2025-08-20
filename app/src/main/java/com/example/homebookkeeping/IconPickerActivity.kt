package com.example.homebookkeeping

import android.app.Activity
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.lang.reflect.Field

class IconPickerActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_icon_picker)

        val iconsRecyclerView: RecyclerView = findViewById(R.id.iconsRecyclerView)

        // Вызываем новую функцию для автоматической загрузки иконок
        val iconList = loadDrawableIcons()

        val adapter = IconAdapter(iconList) { selectedIconName ->
            val resultIntent = Intent()
            resultIntent.putExtra("selectedIconName", selectedIconName)
            setResult(Activity.RESULT_OK, resultIntent)
            finish() // Закрываем экран и возвращаем результат
        }

        iconsRecyclerView.layoutManager = GridLayoutManager(this, 5) // 5 иконок в ряду
        iconsRecyclerView.adapter = adapter
    }

    // НОВАЯ ФУНКЦИЯ для автоматического сканирования иконок
    private fun loadDrawableIcons(): List<String> {
        val iconNames = mutableListOf<String>()
        // Используем "рефлексию", чтобы заглянуть внутрь папки drawable
        val fields: Array<Field> = R.drawable::class.java.fields
        for (field in fields) {
            // Отбираем только те файлы, имена которых начинаются с "ic_"
            // и не являются стандартными иконками лаунчера
            if (field.name.startsWith("ic_") && !field.name.contains("launcher")) {
                iconNames.add(field.name)
            }
        }
        return iconNames.sorted() // Сортируем по алфавиту для порядка
    }
}
