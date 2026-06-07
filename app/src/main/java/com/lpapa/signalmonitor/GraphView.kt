package com.lpapa.signalmonitor

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Gráfico de líneas que se desplaza mostrando las últimas lecturas de señal.
 * - Eje vertical autoescalable según los datos visibles.
 * - Cada segmento se colorea según el nivel de calidad (0-4) del punto.
 */
class GraphView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    private data class Point(val value: Float, val level: Int)

    private val maxPoints = 120
    private val points = ArrayDeque<Point>()

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 5f
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#33888888")
        strokeWidth = 1f
    }
    private val axisTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#999999")
        textSize = 26f
    }

    /** Añade una lectura (valor en dBm y nivel 0-4) y redibuja. */
    fun addValue(value: Int, level: Int) {
        points.addLast(Point(value.toFloat(), level))
        while (points.size > maxPoints) points.removeFirst()
        invalidate()
    }

    /** Vacía el gráfico (p. ej. al cambiar de modo Móvil/WiFi). */
    fun clear() {
        points.clear()
        invalidate()
    }

    /**
     * Estadísticas de la ventana visible: [mínimo, promedio, máximo, nº de muestras].
     * Devuelve null si todavía no hay datos.
     */
    fun stats(): IntArray? {
        if (points.isEmpty()) return null
        var min = Int.MAX_VALUE
        var max = Int.MIN_VALUE
        var sum = 0
        for (p in points) {
            val v = p.value.toInt()
            if (v < min) min = v
            if (v > max) max = v
            sum += v
        }
        return intArrayOf(min, sum / points.size, max, points.size)
    }

    private fun colorFor(level: Int): Int = when (level) {
        0 -> Color.parseColor("#D32F2F") // rojo
        1 -> Color.parseColor("#F57C00") // naranja
        2 -> Color.parseColor("#FBC02D") // amarillo
        3 -> Color.parseColor("#7CB342") // verde claro
        else -> Color.parseColor("#2E7D32") // verde
    }

    /** Rango [lo, hi] del eje Y autoescalado a múltiplos de 5, con span mínimo de 20. */
    private fun computeRange(): Pair<Float, Float> {
        if (points.isEmpty()) return Pair(-120f, -50f)
        val values = points.map { it.value }
        var lo = floor((values.min() - 4f) / 5f) * 5f
        var hi = ceil((values.max() + 4f) / 5f) * 5f
        if (hi - lo < 20f) {
            val mid = (hi + lo) / 2f
            lo = mid - 10f
            hi = mid + 10f
        }
        return Pair(lo, hi)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val padL = 100f
        val padR = 24f
        val padT = 24f
        val padB = 24f
        val w = width.toFloat()
        val h = height.toFloat()
        val plotW = w - padL - padR
        val plotH = h - padT - padB
        val bottom = padT + plotH

        val (lo, hi) = computeRange()

        // Rejilla horizontal + etiquetas (dBm) de hi (arriba) a lo (abajo).
        val divisions = 5
        for (i in 0..divisions) {
            val frac = i / divisions.toFloat()
            val y = padT + plotH * frac
            canvas.drawLine(padL, y, w - padR, y, gridPaint)
            val label = (hi - (hi - lo) * frac).toInt()
            canvas.drawText(label.toString(), 6f, y + 9f, axisTextPaint)
        }

        val n = points.size
        if (n < 2) return

        val stepX = plotW / (maxPoints - 1)
        val list = points.toList()

        fun xAt(idx: Int) = padL + stepX * (idx + (maxPoints - n))
        fun yAt(value: Float): Float {
            val clamped = value.coerceIn(lo, hi)
            return padT + plotH * ((hi - clamped) / (hi - lo))
        }

        for (i in 1 until n) {
            val p0 = list[i - 1]
            val p1 = list[i]
            val x0 = xAt(i - 1)
            val y0 = yAt(p0.value)
            val x1 = xAt(i)
            val y1 = yAt(p1.value)
            val color = colorFor(p1.level)

            // Relleno translúcido bajo el segmento.
            fillPaint.color = (0x33 shl 24) or (color and 0x00FFFFFF)
            val fill = Path().apply {
                moveTo(x0, y0)
                lineTo(x1, y1)
                lineTo(x1, bottom)
                lineTo(x0, bottom)
                close()
            }
            canvas.drawPath(fill, fillPaint)

            // Línea coloreada según la calidad del punto.
            linePaint.color = color
            canvas.drawLine(x0, y0, x1, y1, linePaint)
        }
    }
}
