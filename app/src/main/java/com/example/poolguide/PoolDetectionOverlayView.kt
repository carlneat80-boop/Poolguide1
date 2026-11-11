package com.example.poolguide

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import kotlin.math.max

/** Draws the screen-space results from PoolTableVision without intercepting game input. */
class PoolDetectionOverlayView(context: Context) : View(context) {

    private var detection: PoolDetection? = null

    private val tablePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(220, 0, 255, 155)
        style = Paint.Style.STROKE
        strokeWidth = dp(2)
    }

    private val ballPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.YELLOW
        style = Paint.Style.STROKE
        strokeWidth = dp(2)
    }

    private val cuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = dp(3)
    }

    private val pocketPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.CYAN
        style = Paint.Style.STROKE
        strokeWidth = dp(2)
    }

    private val labelBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(190, 0, 0, 0)
        style = Paint.Style.FILL
    }

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = dp(14)
    }

    init {
        setBackgroundColor(Color.TRANSPARENT)
        isClickable = false
    }

    private fun dp(value: Int): Float = value * resources.displayMetrics.density

    fun setDetection(value: PoolDetection?) {
        detection = value
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val result = detection ?: return

        val bounds = RectF(
            result.table.left * width,
            result.table.top * height,
            result.table.right * width,
            result.table.bottom * height
        )
        canvas.drawRect(bounds, tablePaint)

        val ballRadius = max(dp(6), bounds.width() * 0.022f)
        result.pockets.forEach { point ->
            canvas.drawCircle(point.x * width, point.y * height, ballRadius * 1.15f, pocketPaint)
        }

        result.objectBalls.forEachIndexed { index, point ->
            val x = point.x * width
            val y = point.y * height
            canvas.drawCircle(x, y, ballRadius, ballPaint)
            canvas.drawText((index + 1).toString(), x + ballRadius, y - ballRadius, labelPaint)
        }

        result.cueBall?.let { point ->
            val x = point.x * width
            val y = point.y * height
            canvas.drawCircle(x, y, ballRadius * 1.2f, cuePaint)
            canvas.drawLine(x - ballRadius, y, x + ballRadius, y, cuePaint)
            canvas.drawLine(x, y - ballRadius, x, y + ballRadius, cuePaint)
        }

        val cueStatus = if (result.cueBall == null) "cue ball not found" else "cue ball found"
        val text = "PoolGuide • $cueStatus • ${result.objectBalls.size} candidates"
        val left = dp(10)
        val top = dp(34)
        val right = left + labelPaint.measureText(text) + dp(16)
        val bottom = top + dp(30)
        canvas.drawRoundRect(left, top, right, bottom, dp(7), dp(7), labelBg)
        canvas.drawText(text, left + dp(8), top + dp(20), labelPaint)
    }
}

/*
 * =========================================
 * POOLGUIDE
 * Created by: Carl Neat
 * Developer: Carl Neat
 * Copyright © 2026 Carl Neat
 * =========================================
 */
