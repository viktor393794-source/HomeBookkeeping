package com.example.homebookkeeping

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

class AddCategoryActivity : AppCompatActivity() {

    private val db = Firebase.firestore

    private lateinit var categoryNameEditText: EditText
    private lateinit var selectedIconImageView: ImageView
    private lateinit var iconColorPreview: View
    private lateinit var backgroundColorPreview: View
    private lateinit var limitEditText: EditText
    private lateinit var typeRadioGroup: RadioGroup
    private lateinit var parentCategoryInfo: TextView
    private lateinit var selectIconColorButton: Button
    private lateinit var selectBackgroundColorButton: Button

    private var selectedIconName: String? = null
    private var selectedIconColorHex: String = "#FFFFFF"
    private var selectedBackgroundColorHex: String = "#2196F3"

    private var parentId: String? = null
    private var categoryLevel: Int = 0
    private var categoryType: String = "EXPENSE"

    private val iconPickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.getStringExtra("selectedIconName")?.let {
                selectedIconName = it
                updateIconPreview()
            }
        }
    }
    private val iconColorPickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.getStringExtra("selectedColorHex")?.let {
                selectedIconColorHex = it
                iconColorPreview.setBackgroundColor(Color.parseColor(it))
                updateIconPreview()
            }
        }
    }
    private val backgroundColorPickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.getStringExtra("selectedColorHex")?.let {
                selectedBackgroundColorHex = it
                backgroundColorPreview.setBackgroundColor(Color.parseColor(it))
                updateIconPreview()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_category)

        parentId = intent.getStringExtra("PARENT_ID")
        categoryLevel = intent.getIntExtra("LEVEL", 0)
        val parentName = intent.getStringExtra("PARENT_NAME")
        intent.getStringExtra("CATEGORY_TYPE")?.let { categoryType = it }
        val inheritedIconColor = intent.getStringExtra("ICON_COLOR")
        val inheritedBackgroundColor = intent.getStringExtra("BACKGROUND_COLOR")

        initializeUI()

        if (parentId != null) {
            parentCategoryInfo.visibility = View.VISIBLE
            parentCategoryInfo.text = "Подкатегория для: $parentName"

            findViewById<RadioButton>(R.id.expenseRadioButton).isEnabled = false
            findViewById<RadioButton>(R.id.incomeRadioButton).isEnabled = false

            if (inheritedIconColor != null && inheritedBackgroundColor != null) {
                selectedIconColorHex = inheritedIconColor
                selectedBackgroundColorHex = inheritedBackgroundColor

                selectIconColorButton.isEnabled = false
                selectBackgroundColorButton.isEnabled = false
            }
        }

        if (categoryType == "INCOME") {
            typeRadioGroup.check(R.id.incomeRadioButton)
        } else {
            typeRadioGroup.check(R.id.expenseRadioButton)
        }

        setupListeners()
        updateColorPreviews()
        updateIconPreview()
    }

    private fun initializeUI() {
        categoryNameEditText = findViewById(R.id.categoryNameEditText)
        selectedIconImageView = findViewById(R.id.selectedIconImageView)
        iconColorPreview = findViewById(R.id.iconColorPreview)
        backgroundColorPreview = findViewById(R.id.backgroundColorPreview)
        limitEditText = findViewById(R.id.limitEditText)
        typeRadioGroup = findViewById(R.id.categoryTypeRadioGroup)
        parentCategoryInfo = findViewById(R.id.parentCategoryInfo)
        selectIconColorButton = findViewById(R.id.selectIconColorButton)
        selectBackgroundColorButton = findViewById(R.id.selectBackgroundColorButton)
    }

    private fun setupListeners() {
        findViewById<Button>(R.id.selectIconButton).setOnClickListener {
            iconPickerLauncher.launch(Intent(this, IconPickerActivity::class.java))
        }
        selectIconColorButton.setOnClickListener {
            iconColorPickerLauncher.launch(Intent(this, ColorPickerActivity::class.java))
        }
        selectBackgroundColorButton.setOnClickListener {
            backgroundColorPickerLauncher.launch(Intent(this, ColorPickerActivity::class.java))
        }
        findViewById<Button>(R.id.saveCategoryButton).setOnClickListener {
            saveNewCategory()
        }
    }

    private fun updateIconPreview() {
        if (selectedIconName == null) return
        val resId = getIconResId(this, selectedIconName!!)
        if (resId != 0) {
            selectedIconImageView.setImageResource(resId)
            try {
                val background = GradientDrawable()
                background.shape = GradientDrawable.OVAL
                background.setColor(Color.parseColor(selectedBackgroundColorHex))
                selectedIconImageView.background = background
                selectedIconImageView.setColorFilter(Color.parseColor(selectedIconColorHex))
            } catch (e: Exception) { /* ... */ }
        }
    }

    private fun updateColorPreviews() {
        iconColorPreview.setBackgroundColor(Color.parseColor(selectedIconColorHex))
        backgroundColorPreview.setBackgroundColor(Color.parseColor(selectedBackgroundColorHex))
    }

    private fun saveNewCategory() {
        val budgetId = BudgetManager.currentBudgetId
        if (budgetId == null) {
            Toast.makeText(this, "Ошибка: бюджет не найден", Toast.LENGTH_SHORT).show()
            return
        }

        val name = categoryNameEditText.text.toString()
        val limit = limitEditText.text.toString().toDoubleOrNull() ?: 0.0

        if (name.isBlank() || selectedIconName == null) {
            Toast.makeText(this, "Название и иконка должны быть заполнены", Toast.LENGTH_SHORT).show()
            return
        }

        val newCategory = Category(
            name = name,
            type = categoryType,
            iconName = selectedIconName!!,
            iconColor = selectedIconColorHex,
            backgroundColor = selectedBackgroundColorHex,
            limit = limit,
            parentId = parentId ?: "",
            level = categoryLevel
        )

        db.collection("budgets").document(budgetId).collection("categories").add(newCategory)
            .addOnSuccessListener {
                Toast.makeText(this, "Категория добавлена", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun getIconResId(context: Context, iconName: String): Int {
        return context.resources.getIdentifier(iconName, "drawable", context.packageName)
    }
}