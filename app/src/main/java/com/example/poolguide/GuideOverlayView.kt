package com.example.poolguide

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * A transparent, touch-through practice guide. The user places the table
 * corners, cue ball, object ball and pocket manually; this class does not
 * inspect the game image or automate game input.
 */
class GuideOverlayView(context: Context) : View(context) {

    private val tapPoints = mutableListOf<PointF>()
    private var tableBounds: RectF? = null
    private var cueBall: PointF? = null
    private var objectBall: PointF? = null
    private var pocket: PointF? = null
    private var placementComplete: (() -> Unit)? = null
    private var angleAdjustment = 0f

    var isPlacing: Boolean = false
        private set

    private val tablePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(205, 50, 255, 145)
        style = Paint.Style.STROKE
        strokeWidth = dp(2)
    }

    private val cuePathPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = dp(3)
    }

    private val objectPathPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(230, 65, 220, 255)
        style = Paint.Style.STROKE
        strokeWidth = dp(2)
        pathEffect = DashPathEffect(floatArrayOf(dp(9), dp(7)), 0f)
    }

    private val ballFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }

    private val objectFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(255, 176, 60)
        style = Paint.Style.FILL
    }

    private val markerStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.STROKE
        strokeWidth = dp(2)
    }

    private val pocketPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.CYAN
        style = Paint.Style.STROKE
        strokeWidth = dp(3)
    }

    private val hintBackgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(190, 0, 0, 0)
        style = Paint.Style.FILL
    }

    private val hintTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = dp(14)
        style = Paint.Style.FILL
    }

    init {
        setBackgroundColor(Color.TRANSPARENT)
        isClickable = true
    }

    private fun dp(value: Int): Float =
        value * resources.displayMetrics.density

    fun beginPlacement(onComplete: (() -> Unit)? = null) {
        tapPoints.clear()
        placementComplete = onComplete
        isPlacing = true
        invalidate()
    }

    fun cancelPlacement() {
        isPlacing = false
        tapPoints.clear()
        placementComplete = null
        invalidate()
    }

    fun clearGuide() {
        cancelPlacement()
        tableBounds = null
        cueBall = null
        objectBall = null
        pocket = null
        invalidate()
    }

    fun setAngleAdjustment(degrees: Float) {
        angleAdjustment = degrees.coerceIn(-45f, 45f)
        invalidate()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isPlacing) return false

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> return true
            MotionEvent.ACTION_UP -> {
                tapPoints.add(PointF(event.x, event.y))
                if (tapPoints.size >= 5) {
                    tableBounds = RectF(
                        min(tapPoints[0].x, tapPoints[1].x),
                        min(tapPoints[0].y, tapPoints[1].y),
                        maxOf(tapPoints[0].x, tapPoints[1].x),
                        maxOf(tapPoints[0].y, tapPoints[1].y)
                    )
                    cueBall = tapPoints[2]
                    objectBall = tapPoints[3]
                    pocket = tapPoints[4]
                    isPlacing = false
                    val callback = placementComplete
                    placementComplete = null
                    invalidate()
                    callback?.invoke()
                } else {
                    invalidate()
                }
                return true
            }
        }
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val table = tableBounds
        if (table != null) {
            canvas.drawRect(table, tablePaint)
            drawGuide(canvas, table)
        }

        drawPlacedMarkers(canvas)

        if (isPlacing) {
            val nextStep = when (tapPoints.size) {
                0 -> "1/5: tap inside the table's top-left corner"
                1 -> "2/5: tap inside the table's bottom-right corner"
                2 -> "3/5: tap the cue-ball centre"
                3 -> "4/5: tap the object-ball centre"
                else -> "5/5: tap the pocket centre"
            }
            drawHint(canvas, nextStep)
            drawPlacementDots(canvas)
        }
    }

    private fun drawGuide(canvas: Canvas, table: RectF) {
        val cue = cueBall ?: return
        val obj = objectBall ?: return
        val targetPocket = pocket ?: return

        val ballRadius = (table.width() * 0.014f).coerceAtLeast(dp(7))
        val pocketDx = targetPocket.x - obj.x
        val pocketDy = targetPocket.y - obj.y
        val pocketLength = kotlin.math.hypot(pocketDx, pocketDy)
        if (pocketLength <= 1f) return

        val objectDirX = pocketDx / pocketLength
        val objectDirY = pocketDy / pocketLength

        val contact = PointF(
            obj.x - objectDirX * ballRadius * 2f,
            obj.y - objectDirY * ballRadius * 2f
        )

        var cueDx = contact.x - cue.x
        var cueDy = contact.y - cue.y
        val cueLength = kotlin.math.hypot(cueDx, cueDy)
        if (cueLength <= 1f) return
        cueDx /= cueLength
        cueDy /= cueLength

        val radians = Math.toRadians(angleAdjustment.toDouble())
        val adjustedDx = (cueDx * cos(radians) - cueDy * sin(radians)).toFloat()
        val adjustedDy = (cueDx * sin(radians) + cueDy * cos(radians)).toFloat()

        val cueEnd = rayExitPoint(cue, adjustedDx, adjustedDy, table)
        if (cueEnd != null) {
            canvas.drawLine(cue.x, cue.y, cueEnd.x, cueEnd.y, cuePathPaint)
        }

        canvas.drawLine(obj.x, obj.y, targetPocket.x, targetPocket.y, objectPathPaint)
        canvas.drawCircle(targetPocket.x, targetPocket.y, ballRadius * 1.45f, pocketPaint)
    }

    private fun rayExitPoint(
        origin: PointF,
        dx: Float,
        dy: Float,
        bounds: RectF
    ): PointF? {
        var distance = Float.POSITIVE_INFINITY
        if (dx > 0.0001f) distance = min(distance, (bounds.right - origin.x) / dx)
        if (dx < -0.0001f) distance = min(distance, (bounds.left - origin.x) / dx)
        if (dy > 0.0001f) distance = min(distance, (bounds.bottom - origin.y) / dy)
        if (dy < -0.0001f) distance = min(distance, (bounds.top - origin.y) / dy)
        if (!distance.isFinite() || distance <= 0f) return null
        return PointF(origin.x + dx * distance, origin.y + dy * distance)
    }

    private fun drawPlacedMarkers(canvas: Canvas) {
        val table = tableBounds ?: return
        val radius = (table.width() * 0.014f).coerceAtLeast(dp(7))

        cueBall?.let {
            canvas.drawCircle(it.x, it.y, radius, ballFillPaint)
            canvas.drawCircle(it.x, it.y, radius, markerStrokePaint)
        }
        objectBall?.let {
            canvas.drawCircle(it.x, it.y, radius, objectFillPaint)
            canvas.drawCircle(it.x, it.y, radius, markerStrokePaint)
        }
        pocket?.let {
            canvas.drawCircle(it.x, it.y, radius * 1.35f, pocketPaint)
        }
    }

    private fun drawPlacementDots(canvas: Canvas) {
        val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.YELLOW
            style = Paint.Style.FILL
        }
        tapPoints.forEachIndexed { index, point ->
            canvas.drawCircle(point.x, point.y, dp(6), dotPaint)
            canvas.drawText(
                (index + 1).toString(),
                point.x + dp(7),
                point.y - dp(7),
                hintTextPaint
            )
        }
    }

    private fun drawHint(canvas: Canvas, text: String) {
        val left = dp(12)
        val top = dp(36)
        val right = left + hintTextPaint.measureText(text) + dp(20)
        val bottom = top + dp(34)
        canvas.drawRoundRect(left, top, right, bottom, dp(8), dp(8), hintBackgroundPaint)
        canvas.drawText(text, left + dp(10), top + dp(22), hintTextPaint)
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
