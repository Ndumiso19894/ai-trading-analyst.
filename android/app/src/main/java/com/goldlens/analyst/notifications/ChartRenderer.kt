package com.goldlens.analyst.notifications

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import com.goldlens.analyst.core.Candle

/**
 * Renders a candlestick chart to a PNG-compatible Bitmap for Telegram signal photos.
 * Draws OHLC candles, support/resistance zones (if supplied), and the last-price line.
 */
object ChartRenderer {
    private const val WIDTH = 800
    private const val HEIGHT = 450
    private const val PADDING = 40f

    private val bgPaint = Paint().apply { color = Color.rgb(9, 12, 17) }
    private val gridPaint = Paint().apply {
        color = Color.rgb(34, 45, 58)
        style = Paint.Style.STROKE
        strokeWidth = 1f
    }
    private val textPaint = Paint().apply {
        color = Color.rgb(154, 164, 179)
        textSize = 14f
        isAntiAlias = true
    }
    private val titlePaint = Paint().apply {
        color = Color.rgb(217, 180, 91)
        textSize = 18f
        isAntiAlias = true
        textAlign = Paint.Align.LEFT
    }
    private val lastPricePaint = Paint().apply {
        color = Color.rgb(217, 180, 91)
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
        pathEffect = DashPathEffect(floatArrayOf(6f, 5f), 0f)
    }
    private val borderPaint = Paint().apply {
        color = Color.rgb(58, 65, 77)
        style = Paint.Style.STROKE
        strokeWidth = 1f
    }

    fun render(
        candles: List<Candle>,
        symbol: String,
        timeframe: String,
        direction: String?,
        lastPrice: Double?,
        zones: List<ZoneRect>? = null,
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        canvas.drawRect(0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat(), bgPaint)

        if (candles.isEmpty()) return bitmap

        val visible = candles.takeLast(80)
        val lo = visible.minOf { it.low }
        val hi = visible.maxOf { it.high }
        val span = (hi - lo).coerceAtLeast(1e-9)

        fun y(v: Double): Float = (HEIGHT - PADDING - ((v - lo) / span * (HEIGHT - 2 * PADDING))).toFloat()

        // Draw grid lines
        val steps = 5
        for (i in 0..steps) {
            val v = lo + (hi - lo) * i / steps
            val yy = y(v)
            canvas.drawLine(PADDING, yy, WIDTH - PADDING, yy, gridPaint)
            textPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText("%.2f".format(v), PADDING - 8, yy + 4, textPaint)
        }

        // Draw zones
        zones?.forEach { zone ->
            val c = if (zone.side == "SUPPORT") Color.rgb(64, 201, 154) else Color.rgb(255, 119, 119)
            val zonePaint = Paint().apply {
                color = c
                alpha = 40
            }
            val top = y(zone.upper)
            val bottom = y(zone.lower)
            canvas.drawRect(PADDING, top, WIDTH - PADDING, bottom, zonePaint)
        }

        // Draw candles
        val step = (WIDTH - 2 * PADDING) / visible.size
        visible.forEachIndexed { index, candle ->
            val centerX = PADDING + index * step + step / 2f
            val candleColor = if (candle.close >= candle.open) Color.rgb(64, 201, 154) else Color.rgb(255, 119, 119)
            val wickPaint = Paint().apply {
                color = candleColor
                style = Paint.Style.STROKE
                strokeWidth = 1.4f
                isAntiAlias = true
            }
            val bodyPaint = Paint().apply {
                color = candleColor
                style = Paint.Style.FILL
                isAntiAlias = true
            }
            // Wick
            canvas.drawLine(centerX, y(candle.high), centerX, y(candle.low), wickPaint)
            // Body
            val top = y(maxOf(candle.open, candle.close))
            val bottom = y(minOf(candle.open, candle.close))
            canvas.drawRect(centerX - step * 0.28f, top, centerX + step * 0.28f, bottom, bodyPaint)
        }

        // Last price line
        lastPrice?.let { lp ->
            if (lp in lo..hi) {
                val yy = y(lp)
                canvas.drawLine(PADDING, yy, WIDTH - PADDING, yy, lastPricePaint)
            }
        }

        // Border
        canvas.drawRect(0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat(), borderPaint)

        // Title
        canvas.drawText("$symbol · $timeframe · ${direction ?: "—"}", PADDING, PADDING - 8, titlePaint)

        return bitmap
    }

    fun bitmapToPngBytes(bitmap: Bitmap): ByteArray {
        val stream = java.io.ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 90, stream)
        return stream.toByteArray()
    }

    data class ZoneRect(val side: String, val lower: Double, val upper: Double)
}
