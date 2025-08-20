package com.example.homebookkeeping

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

/**
 * Custom View для отображения круговой диаграммы расходов/доходов по категориям.
 */
class PieChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var categoryStats: List<CategoryStat> = emptyList()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rectF = RectF()

    /**
     * Устанавливает данные для отображения на круговой диаграмме.
     * @param stats Список объектов CategoryStat, содержащих информацию о категориях и их суммах.
     */
    fun setData(stats: List<CategoryStat>) {
        this.categoryStats = stats
        invalidate() // Перерисовываем View при изменении данных
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (categoryStats.isEmpty()) {
            // Если данных нет, рисуем серый круг
            paint.color = Color.LTGRAY
            paint.style = Paint.Style.FILL
            val centerX = width / 2f
            val centerY = height / 2f
            val radius = Math.min(width, height) / 2f * 0.8f // 80% от меньшей стороны

            canvas.drawCircle(centerX, centerY, radius, paint)

            // Добавляем текст "Нет данных"
            paint.color = Color.DKGRAY
            paint.textSize = 30f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("Нет данных", centerX, centerY + 15, paint)
            return
        }

        val totalAmount = categoryStats.sumOf { it.totalAmount }
        if (totalAmount == 0.0) {
            // Если общая сумма 0, но категории есть, рисуем пустой круг
            paint.color = Color.LTGRAY
            paint.style = Paint.Style.FILL
            val centerX = width / 2f
            val centerY = height / 2f
            val radius = Math.min(width, height) / 2f * 0.8f

            canvas.drawCircle(centerX, centerY, radius, paint)

            // Добавляем текст "Нет транзакций"
            paint.color = Color.DKGRAY
            paint.textSize = 30f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("Нет транзакций", centerX, centerY + 15, paint)
            return
        }

        // Определяем размеры области для рисования диаграммы
        val size = Math.min(width, height).toFloat()
        val padding = 20f // Отступ от краев
        rectF.set(padding, padding, size - padding, size - padding)

        var startAngle = 0f

        for (stat in categoryStats) {
            val sweepAngle = (stat.totalAmount / totalAmount * 360).toFloat()
            try {
                // Устанавливаем цвет сектора из backgroundColor категории
                paint.color = Color.parseColor(stat.category.backgroundColor)
            } catch (e: IllegalArgumentException) {
                // На случай, если цвет некорректен
                paint.color = Color.GRAY
            }
            canvas.drawArc(rectF, startAngle, sweepAngle, true, paint)
            startAngle += sweepAngle
        }
    }
}
