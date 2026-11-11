package com.example.poolguide

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import kotlin.math.abs

class FloatingOverlayService : Service() {

    private lateinit var wm: WindowManager
    private lateinit var guideView: GuideOverlayView
    private lateinit var detectionView: PoolDetectionOverlayView
    private lateinit var guideParams: WindowManager.LayoutParams
    private lateinit var detectionParams: WindowManager.LayoutParams

    private var bubble: TextView? = null
    private var panel: LinearLayout? = null
    private var angleAdjustment = 0
    private var guideVisible = true
    private var detectionVisible = true

    private val detectionListener: (PoolDetection?) -> Unit = { detection ->
        if (::detectionView.isInitialized) {
            detectionView.post {
                if (::detectionView.isInitialized) detectionView.setDetection(detection)
            }
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun windowType(): Int =
        if (Build.VERSION.SDK_INT >= 26)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            WindowManager.LayoutParams.TYPE_PHONE

    override fun onCreate() {
        super.onCreate()
        startOverlayForeground()

        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        addGuideLayer()
        addDetectionLayer()
        addBubble()
        PoolGuideDetectionBus.addListener(detectionListener)
    }

    private fun startOverlayForeground() {
        val channelId = "poolguide_overlay"

        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(
                channelId,
                "PoolGuide floating controls",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }

        val notification = if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(this, channelId)
                .setContentTitle("PoolGuide is active")
                .setContentText("Floating practice guide is ready")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .build()
        } else {
            Notification.Builder(this)
                .setContentTitle("PoolGuide is active")
                .setContentText("Floating practice guide is ready")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .build()
        }

        startForeground(1, notification)
    }

    private fun addGuideLayer() {
        guideView = GuideOverlayView(this)
        guideParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            windowType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
            // Android 12+ limits touches passing through translucent app overlays.
            // Both full-screen guide layers overlap, so keep each at 0.5 alpha.
            alpha = 0.5f
        }

        wm.addView(guideView, guideParams)
    }

    private fun setGuidePlacementTouch(enabled: Boolean) {
        if (!::guideParams.isInitialized) return

        guideParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            (if (!enabled) WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE else 0)

        try {
            wm.updateViewLayout(guideView, guideParams)
        } catch (_: IllegalArgumentException) {
            // The service may be closing while a placement callback arrives.
        }
    }

    private fun addDetectionLayer() {
        detectionView = PoolDetectionOverlayView(this)
        detectionParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            windowType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
            // Combined with the guide window, the overlapping opacity stays below 0.8.
            alpha = 0.5f
        }
        wm.addView(detectionView, detectionParams)
    }

    private fun addBubble() {
        val view = TextView(this).apply {
            text = "PG"
            textSize = 17f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.rgb(10, 105, 72))
                setStroke(dp(2), Color.WHITE)
            }
        }

        val params = WindowManager.LayoutParams(
            dp(58),
            dp(58),
            windowType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(18)
            y = dp(220)
        }

        var startX = 0
        var startY = 0
        var downX = 0f
        var downY = 0f
        var dragged = false

        view.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    startX = params.x
                    startY = params.y
                    dragged = false
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - downX).toInt()
                    val dy = (event.rawY - downY).toInt()

                    if (abs(dx) > dp(5) || abs(dy) > dp(5)) dragged = true

                    params.x = startX + dx
                    params.y = startY + dy
                    wm.updateViewLayout(view, params)
                    true
                }

                MotionEvent.ACTION_UP -> {
                    if (!dragged) togglePanel()
                    true
                }

                else -> true
            }
        }

        bubble = view
        wm.addView(view, params)
    }

    private fun togglePanel() {
        if (panel != null) {
            closePanel()
            return
        }

        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = GradientDrawable().apply {
                setColor(Color.rgb(22, 35, 31))
                cornerRadius = dp(14).toFloat()
                setStroke(dp(1), Color.GREEN)
            }
        }

        box.addView(TextView(this).apply {
            text = "POOL GUIDE PRACTICE"
            textSize = 16f
            setTextColor(Color.WHITE)
        })

        val label = TextView(this).apply {
            text = "Aim adjustment: ${angleAdjustment}°"
            setTextColor(Color.WHITE)
        }
        box.addView(label)

        box.addView(SeekBar(this).apply {
            max = 90
            progress = angleAdjustment + 45
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(
                    seekBar: SeekBar?,
                    value: Int,
                    fromUser: Boolean
                ) {
                    angleAdjustment = value - 45
                    guideView.setAngleAdjustment(angleAdjustment.toFloat())
                    label.text = "Aim adjustment: ${angleAdjustment}°"
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        })

        box.addView(TextView(this).apply {
            text = "Manual guide only. Place the table and balls yourself."
            setTextColor(Color.LTGRAY)
            textSize = 12f
        })

        addPanelButton(box, if (guideVisible) "Hide shot guide" else "Show shot guide") {
            guideVisible = !guideVisible
            guideView.visibility = if (guideVisible) View.VISIBLE else View.GONE
            if (!guideVisible && guideView.isPlacing) cancelPlacement()
            closePanel()
        }

        addPanelButton(box, if (detectionVisible) "Hide detected table" else "Show detected table") {
            detectionVisible = !detectionVisible
            detectionView.visibility = if (detectionVisible) View.VISIBLE else View.GONE
            closePanel()
        }

        addPanelButton(box, "Place table, balls and pocket") {
            beginPlacement()
        }

        if (guideView.isPlacing) {
            addPanelButton(box, "Cancel placement") {
                cancelPlacement()
                closePanel()
            }
        }

        addPanelButton(box, "Clear guide") {
            guideView.clearGuide()
            setGuidePlacementTouch(false)
            closePanel()
        }

        addPanelButton(box, "Stop screen capture") {
            if (guideView.isPlacing) cancelPlacement()
            stopService(Intent(this, ScreenCaptureService::class.java))
            closePanel()
        }

        addPanelButton(box, "Close panel") { closePanel() }

        val params = WindowManager.LayoutParams(
            dp(280),
            WindowManager.LayoutParams.WRAP_CONTENT,
            windowType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(18)
            y = dp(290)
        }

        panel = box
        wm.addView(box, params)
    }

    private fun addPanelButton(
        parent: LinearLayout,
        title: String,
        action: () -> Unit
    ) {
        parent.addView(Button(this).apply {
            text = title
            isAllCaps = false
            setOnClickListener { action() }
        })
    }

    private fun beginPlacement() {
        guideVisible = true
        guideView.visibility = View.VISIBLE
        guideView.beginPlacement {
            setGuidePlacementTouch(false)
            Toast.makeText(
                this,
                "Guide placed. Game touches pass through again.",
                Toast.LENGTH_SHORT
            ).show()
        }
        setGuidePlacementTouch(true)
        closePanel()
    }

    private fun cancelPlacement() {
        guideView.cancelPlacement()
        setGuidePlacementTouch(false)
    }

    private fun closePanel() {
        val currentPanel = panel ?: return
        try {
            wm.removeView(currentPanel)
        } catch (_: IllegalArgumentException) {
            // The panel was already removed during service shutdown.
        }
        panel = null
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        stopService(Intent(this, ScreenCaptureService::class.java))
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        closePanel()
        if (::guideView.isInitialized) {
            try {
                wm.removeView(guideView)
            } catch (_: IllegalArgumentException) {
                // The guide window is already detached.
            }
        }
        PoolGuideDetectionBus.removeListener(detectionListener)
        if (::detectionView.isInitialized) {
            try {
                wm.removeView(detectionView)
            } catch (_: IllegalArgumentException) {
                // The detection window is already detached.
            }
        }
        bubble?.let {
            try {
                wm.removeView(it)
            } catch (_: IllegalArgumentException) {
                // The bubble is already detached.
            }
        }
        bubble = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

/*
 * =========================================
 * POOLGUIDE
 * Created by: Carl Neat
 * Developer: Carl Neat
 * Copyright © 2026 Carl Neat
 * =========================================
 */
