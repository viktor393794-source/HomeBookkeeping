package com.example.homebookkeeping

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Spinner
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

class EditCategoryActivity : AppCompatActivity() {

    private val db = Firebase.firestore
    private var categoryId: String? = null
    private var currentCategory: Category? = null

    private lateinit var categoryNameEditText: EditText
    private lateinit var selectedIconImageView: ImageView
    private lateinit var iconColorPreview: View
    private lateinit var backgroundColorPreview: View
    private lateinit var limitEditText: EditText
    private lateinit var typeRadioGroup: RadioGroup
    private lateinit var parentCategorySpinner: Spinner
    // --- Новое: ссылки на кнопки для блокировки ---
    private lateinit var selectIconColorButton: Button
    private lateinit var selectBackgroundColorButton: Button

    private val allCategories = mutableListOf<Category>()
    private val possibleParents = mutableListOf<Category>()
    private lateinit var parentSpinnerAdapter: ArrayAdapter<String>

    private var selectedIconName: String? = null
    private var selectedIconColorHex: String? = null
    private var selectedBackgroundColorHex: String? = null

    // --- Launchers без изменений ---
    private val iconPickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.getStringExtra("selectedIconName")?.let { selectedIconName = it; updateIconPreview() }
        }
    }
    private val iconColorPickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.getStringExtra("selectedColorHex")?.let { selectedIconColorHex = it; updateColorPreviews() }
        }
    }
    private val backgroundColorPickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.getStringExtra("selectedColorHex")?.let { selectedBackgroundColorHex = it; updateColorPreviews() }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_category)

        categoryId = intent.getStringExtra("CATEGORY_ID")
        if (categoryId == null) {
            finish()
            return
        }

        initializeUI()
        setupListeners()
        loadAllCategoriesAndCurrent()
    }

    private fun initializeUI() {
        categoryNameEditText = findViewById(R.id.categoryNameEditText)
        selectedIconImageView = findViewById(R.id.selectedIconImageView)
        iconColorPreview = findViewById(R.id.iconColorPreview)
        backgroundColorPreview = findViewById(R.id.backgroundColorPreview)
        limitEditText = findViewById(R.id.limitEditText)
        typeRadioGroup = findViewById(R.id.categoryTypeRadioGroup)
        parentCategorySpinner = findViewById(R.id.parentCategorySpinner)
        selectIconColorButton = findViewById(R.id.selectIconColorButton)
        selectBackgroundColorButton = findViewById(R.id.selectBackgroundColorButton)
    }

    private fun setupListeners() {
        findViewById<Button>(R.id.selectIconButton).setOnClickListener { iconPickerLauncher.launch(Intent(this, IconPickerActivity::class.java)) }
        selectIconColorButton.setOnClickListener { iconColorPickerLauncher.launch(Intent(this, ColorPickerActivity::class.java)) }
        selectBackgroundColorButton.setOnClickListener { backgroundColorPickerLauncher.launch(Intent(this, ColorPickerActivity::class.java)) }
        findViewById<Button>(R.id.saveCategoryButton).setOnClickListener { saveChanges() }
        findViewById<Button>(R.id.deleteCategoryButton).setOnClickListener { confirmAndDelete() }

        // --- НОВЫЙ СЛУШАТЕЛЬ: Динамическое перекрашивание при смене родителя ---
        parentCategorySpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedParent = possibleParents[position]
                if (selectedParent.id.isBlank()) { // Если выбрали "Нет (категория верхнего уровня)"
                    selectIconColorButton.isEnabled = true
                    selectBackgroundColorButton.isEnabled = true
                } else { // Если выбрали родителя
                    selectIconColorButton.isEnabled = false
                    selectBackgroundColorButton.isEnabled = false
                    // Находим цвета главного родителя и применяем их
                    var finalParent = selectedParent
                    while (finalParent.level != 0) {
                        finalParent = allCategories.find { it.id == finalParent.parentId } ?: break
                    }
                    selectedIconColorHex = finalParent.iconColor
                    selectedBackgroundColorHex = finalParent.backgroundColor
                    updateColorPreviews()
                    updateIconPreview()
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun loadAllCategoriesAndCurrent() {
        db.collection("categories").get().addOnSuccessListener { snapshot ->
            allCategories.clear()
            allCategories.addAll(snapshot.map { it.toObject(Category::class.java).copy(id = it.id) })

            currentCategory = allCategories.find { it.id == categoryId }
            if (currentCategory == null) {
                finish()
            } else {
                populateUI(currentCategory!!)
                setupParentSpinner()
            }
        }
    }

    private fun setupParentSpinner() {
        val currentCat = currentCategory ?: return
        possibleParents.clear()
        possibleParents.add(Category(id = "", name = "Нет (категория верхнего уровня)"))

        val descendants = mutableSetOf<String>()
        findDescendants(currentCat.id, allCategories, descendants)

        val filteredList = allCategories.filter {
            it.id != currentCat.id && !descendants.contains(it.id) && it.type == currentCat.type && it.level < 2
        }

        val sortedHierarchicalList = buildHierarchicalList(filteredList)
        possibleParents.addAll(sortedHierarchicalList)

        val displayNames = possibleParents.map { "— ".repeat(it.level) + it.name }

        parentSpinnerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, displayNames)
        parentSpinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        parentCategorySpinner.adapter = parentSpinnerAdapter

        val parentIndex = possibleParents.indexOfFirst { it.id == currentCat.parentId }
        if (parentIndex != -1) {
            parentCategorySpinner.setSelection(parentIndex)
        }
    }

    private fun populateUI(category: Category) {
        categoryNameEditText.setText(category.name)
        limitEditText.setText(category.limit.toString())
        if (category.type == "EXPENSE") typeRadioGroup.check(R.id.expenseRadioButton)
        else typeRadioGroup.check(R.id.incomeRadioButton)

        val hasChildren = allCategories.any { it.parentId == category.id }
        if (hasChildren) {
            findViewById<RadioButton>(R.id.expenseRadioButton).isEnabled = false
            findViewById<RadioButton>(R.id.incomeRadioButton).isEnabled = false
        }

        // --- НОВОЕ: Блокируем кнопки, если это подкатегория ---
        if (category.level > 0) {
            selectIconColorButton.isEnabled = false
            selectBackgroundColorButton.isEnabled = false
        }

        selectedIconName = category.iconName
        selectedIconColorHex = category.iconColor
        selectedBackgroundColorHex = category.backgroundColor

        updateColorPreviews()
        updateIconPreview()
    }

    private fun saveChanges() {
        val initialCategory = currentCategory ?: return
        val name = categoryNameEditText.text.toString()
        val type = if (typeRadioGroup.checkedRadioButtonId == R.id.expenseRadioButton) "EXPENSE" else "INCOME"

        val selectedParent = possibleParents[parentCategorySpinner.selectedItemPosition]
        val newParentId = selectedParent.id
        val newLevel = if (newParentId.isBlank()) 0 else (allCategories.find { it.id == newParentId }?.level ?: 0) + 1

        val updatedCategory = initialCategory.copy(
            name = name, type = type,
            iconName = selectedIconName!!,
            iconColor = selectedIconColorHex!!,
            backgroundColor = selectedBackgroundColorHex!!,
            limit = limitEditText.text.toString().toDoubleOrNull() ?: 0.0,
            parentId = newParentId,
            level = newLevel
        )

        // --- НОВОЕ: Логика каскадного обновления цветов ---
        val colorsHaveChanged = initialCategory.iconColor != updatedCategory.iconColor || initialCategory.backgroundColor != updatedCategory.backgroundColor
        val isTopLevel = updatedCategory.level == 0

        if (isTopLevel && colorsHaveChanged) {
            // Если изменился цвет у родителя, обновляем всех потомков
            updateDescendantColors(updatedCategory)
        } else {
            // Иначе просто сохраняем одну категорию
            db.collection("categories").document(updatedCategory.id).set(updatedCategory)
                .addOnSuccessListener {
                    Toast.makeText(this, "Категория обновлена", Toast.LENGTH_SHORT).show()
                    finish()
                }
        }
    }

    private fun updateDescendantColors(parentCategory: Category) {
        val batch = db.batch()
        // Сначала сохраняем саму родительскую категорию
        batch.set(db.collection("categories").document(parentCategory.id), parentCategory)

        val descendants = mutableSetOf<String>()
        findDescendants(parentCategory.id, allCategories, descendants)

        if (descendants.isNotEmpty()) {
            descendants.forEach { descendantId ->
                val docRef = db.collection("categories").document(descendantId)
                batch.update(docRef, mapOf(
                    "iconColor" to parentCategory.iconColor,
                    "backgroundColor" to parentCategory.backgroundColor
                ))
            }
        }

        batch.commit().addOnSuccessListener {
            Toast.makeText(this, "Категория и все подкатегории обновлены", Toast.LENGTH_SHORT).show()
            finish()
        }.addOnFailureListener {
            Toast.makeText(this, "Ошибка обновления", Toast.LENGTH_SHORT).show()
        }
    }

    // --- Вспомогательные функции без существенных изменений ---
    private fun findDescendants(parentId: String, all: List<Category>, result: MutableSet<String>) {
        all.filter { it.parentId == parentId }.forEach { child ->
            result.add(child.id); findDescendants(child.id, all, result)
        }
    }
    private fun buildHierarchicalList(categories: List<Category>): List<Category> {
        val result = mutableListOf<Category>()
        val map = categories.groupBy { it.parentId }
        fun addChildren(parentId: String, level: Int) {
            map[parentId]?.sortedBy { it.name }?.forEach { category ->
                category.level = level; result.add(category); addChildren(category.id, level + 1)
            }
        }
        addChildren("", 0)
        return result
    }
    private fun updateColorPreviews() {
        iconColorPreview.setBackgroundColor(Color.parseColor(selectedIconColorHex))
        backgroundColorPreview.setBackgroundColor(Color.parseColor(selectedBackgroundColorHex))
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
    private fun confirmAndDelete() {
        val hasChildren = allCategories.any { it.parentId == categoryId }
        val message = if (hasChildren) "У этой категории есть подкатегории. Они тоже будут удалены! Это действие нельзя будет отменить."
        else "Вы уверены, что хотите удалить эту категорию? Это действие нельзя будет отменить."
        AlertDialog.Builder(this)
            .setTitle("Удалить категорию").setMessage(message)
            .setPositiveButton("Удалить") { _, _ -> deleteCategoryAndChildren() }
            .setNegativeButton("Отмена", null).show()
    }
    private fun deleteCategoryAndChildren() {
        val descendants = mutableSetOf<String>()
        findDescendants(categoryId!!, allCategories, descendants)
        descendants.add(categoryId!!)
        val batch = db.batch()
        descendants.forEach { idToDelete -> batch.delete(db.collection("categories").document(idToDelete)) }
        batch.commit()
            .addOnSuccessListener { finish(); Toast.makeText(this, "Категория и все подкатегории удалены", Toast.LENGTH_SHORT).show() }
    }
    private fun getIconResId(context: Context, iconName: String): Int = context.resources.getIdentifier(iconName, "drawable", context.packageName)
}