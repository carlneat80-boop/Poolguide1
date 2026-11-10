
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
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import kotlin.math.abs

class FloatingOverlayService : Service() {

    private lateinit var wm: WindowManager
    private var bubble: TextView? = null
    private var panel: LinearLayout? = null
    private var angle = 0

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun windowType(): Int =
        if (Build.VERSION.SDK_INT >= 26)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            WindowManager.LayoutParams.TYPE_PHONE

    override fun onCreate() {
        super.onCreate()

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
                .setContentText("Floating practice controls are ready")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .build()
        } else {
            Notification.Builder(this)
                .setContentTitle("PoolGuide is active")
                .setContentText("Floating practice controls are ready")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .build()
        }

        startForeground(1, notification)

        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        addBubble()
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
            when (event.action) {
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

                    if (abs(dx) > dp(5) || abs(dy) > dp(5)) {
                        dragged = true
                    }

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
            panel?.let { wm.removeView(it) }
            panel = null
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
            text = "Practice angle: $angle°"
            setTextColor(Color.WHITE)
        }

        box.addView(label)

        box.addView(SeekBar(this).apply {
            max = 360
            progress = angle

            setOnSeekBarChangeListener(
                object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(
                        seekBar: SeekBar?,
                        value: Int,
                        fromUser: Boolean
                    ) {
                        angle = value
                        label.text = "Practice angle: $angle°"
                    }

                    override fun onStartTrackingTouch(seekBar: SeekBar?) {}

                    override fun onStopTrackingTouch(seekBar: SeekBar?) {}
                }
            )
        })

        box.addView(TextView(this).apply {
            text = "Manual practice reference. This does not read the game table."
            setTextColor(Color.LTGRAY)
            textSize = 12f
        })

        box.addView(Button(this).apply {
            text = "Close"
            setOnClickListener { togglePanel() }
        })

        box.addView(Button(this).apply {
            text = "Stop screen capture"
            setOnClickListener {
                stopService(
                    Intent(
                        this@FloatingOverlayService,
                        ScreenCaptureService::class.java
                    )
                )
            }
        })

        val params = WindowManager.LayoutParams(
            dp(260),
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

    override fun onTaskRemoved(rootIntent: Intent?) {
        stopService(
            Intent(this, ScreenCaptureService::class.java)
        )
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        panel?.let { wm.removeView(it) }
        bubble?.let { wm.removeView(it) }

        panel = null
        bubble = null

        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
