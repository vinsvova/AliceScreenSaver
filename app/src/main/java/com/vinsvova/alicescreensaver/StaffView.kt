package com.vinsvova.alicescreensaver

import android.content.Context
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.view.View
import androidx.core.graphics.PathParser

/**
 * Нотный стан: пять линеек, скрипичный или басовый ключ и одна целая нота.
 * position — положение ноты относительно нижней линейки: 0 — на нижней линейке,
 * 1 — в первом промежутке, 8 — на верхней линейке, −2 — на первой добавочной снизу и т.д.
 */
class StaffView(context: Context) : View(context) {

    var treble: Boolean = true
        set(value) { field = value; invalidate() }

    var position: Int = 0
        set(value) { field = value; invalidate() }

    var color: Int = 0xFFF4EFE4.toInt()
        set(value) {
            field = value
            linePaint.color = value
            glyphPaint.color = value
            invalidate()
        }

    private val gClef: Path = PathParser.createPathFromPathData(MusicGlyphs.G_CLEF)
    private val fClef: Path = PathParser.createPathFromPathData(MusicGlyphs.F_CLEF)
    private val note: Path = PathParser.createPathFromPathData(MusicGlyphs.WHOLE_NOTE)

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = this@StaffView.color
        strokeCap = Paint.Cap.ROUND
    }
    private val glyphPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = this@StaffView.color
    }
    private val tmp = Path()
    private val matrix = Matrix()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        // Промежуток между линейками; запас сверху и снизу — под добавочные линейки и ключ.
        val space = minOf(h / 10f, w / 13f)
        val scale = space / 250f
        val middle = h / 2f + space * 0.25f
        fun y(p: Int): Float = middle + (4 - p) * space / 2f

        val left = space * 0.6f
        val right = w - space * 0.6f

        linePaint.strokeWidth = space * 0.09f
        for (i in 0..4) {
            val yy = y(i * 2)
            canvas.drawLine(left, yy, right, yy, linePaint)
        }

        // Ключ: скрипичный стоит на второй линейке (соль), басовый — на четвёртой (фа).
        val clefX = left + space * 0.3f
        drawGlyph(canvas, if (treble) gClef else fClef, clefX, if (treble) y(2) else y(6), scale)

        // Нота и добавочные линейки.
        val noteW = MusicGlyphs.WHOLE_NOTE_WIDTH * scale
        val cx = left + (right - left) * 0.62f
        linePaint.strokeWidth = space * 0.11f
        val half = noteW * 0.85f
        if (position <= -2) {
            var q = -2
            while (q >= position) {
                canvas.drawLine(cx - half, y(q), cx + half, y(q), linePaint)
                q -= 2
            }
        }
        if (position >= 10) {
            var q = 10
            while (q <= position) {
                canvas.drawLine(cx - half, y(q), cx + half, y(q), linePaint)
                q += 2
            }
        }
        drawGlyph(canvas, note, cx - noteW / 2f, y(position), scale)
    }

    private fun drawGlyph(canvas: Canvas, glyph: Path, x: Float, yOrigin: Float, scale: Float) {
        matrix.reset()
        matrix.setScale(scale, -scale)
        matrix.postTranslate(x, yOrigin)
        tmp.reset()
        glyph.transform(matrix, tmp)
        canvas.drawPath(tmp, glyphPaint)
    }
}
