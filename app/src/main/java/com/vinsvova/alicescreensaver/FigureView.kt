package com.vinsvova.alicescreensaver

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View

/**
 * Рисунок квадрата или прямоугольника с подписями сторон:
 * длина — сверху, ширина — справа, площадь или периметр — внутри.
 */
class FigureView(context: Context) : View(context) {

    var square = false
    var top = ""
    var side = ""
    var inside = ""

    var lineColor = 0xFFF2C14E.toInt()
    var fillColor = 0xFF26475C.toInt()
    var textColor = 0xFFF4EFE4.toInt()

    private val density = context.resources.displayMetrics.density
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD }
    private val rect = RectF()

    fun set(square: Boolean, top: String, side: String, inside: String) {
        this.square = square
        this.top = top
        this.side = side
        this.inside = inside
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val labelSize = 20f * density
        text.textSize = labelSize
        val gap = 8f * density
        val sideW = text.measureText(side) + gap * 2
        val topH = labelSize + gap

        val availW = w - sideW - gap
        val availH = h - topH - gap
        val (fw, fh) = if (square) {
            val s = minOf(availW, availH)
            s to s
        } else {
            val fh0 = minOf(availH, availW / 2f)
            fh0 * 2f to fh0
        }
        val left = (w - sideW - fw) / 2f
        val topY = topH + (availH - fh) / 2f
        rect.set(left, topY, left + fw, topY + fh)

        fill.color = fillColor
        canvas.drawRect(rect, fill)
        stroke.color = lineColor
        stroke.strokeWidth = 3f * density
        canvas.drawRect(rect, stroke)

        // подпись длины сверху
        text.color = textColor
        text.textAlign = Paint.Align.CENTER
        text.textSize = labelSize
        canvas.drawText(top, rect.centerX(), rect.top - gap, text)

        // подпись ширины справа
        text.textAlign = Paint.Align.LEFT
        canvas.drawText(side, rect.right + gap, rect.centerY() + labelSize / 3f, text)

        // площадь или периметр внутри
        if (inside.isNotEmpty()) {
            text.textAlign = Paint.Align.CENTER
            var size = 22f * density
            text.textSize = size
            while (text.measureText(inside) > fw - gap * 2 && size > 10f * density) {
                size -= density
                text.textSize = size
            }
            text.color = lineColor
            canvas.drawText(inside, rect.centerX(), rect.centerY() + size / 3f, text)
        }
    }
}
