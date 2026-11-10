
package com.example.poolguide

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

    private fun dp(n: Int) =
        (n * resources.displayMetrics.density).toInt()

    private fun windowType() =
        if (Build.VERSION.SDK_INT >= 26)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            WindowManager.LayoutParams.TYPE_PHONE

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        addBubble()
    }

    private fun addBubble() {
        val v = TextView(this).apply {
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

        val p = WindowManager.LayoutParams(
            dp(58), dp(58), windowType(),
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

        v.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX
                    downY = e.rawY
                    startX = p.x
                    startY = p.y
                    dragged = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (e.rawX - downX).toInt()
                    val dy = (e.rawY - downY).toInt()
                    if (abs(dx) > dp(5) || abs(dy) > dp(5))
                        dragged = true
                    p.x = startX + dx
                    p.y = startY + dy
                    wm.updateViewLayout(v, p)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!dragged) togglePanel()
                    true
                }
                else -> true
            }
        }

        bubble = v
        wm.addView(v, p)
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
                        s: SeekBar?, value: Int, user: Boolean
                    ) {
                        angle = value
                        label.text = "Practice angle: $angle°"
                    }
                    override fun onStartTrackingTouch(s: SeekBar?) {}
                    override fun onStopTrackingTouch(s: SeekBar?) {}
                }
            )
        })

        box.addView(TextView(this).apply {
            text = "Use this as a manual practice reference. It does not read the game table."
            setTextColor(Color.LTGRAY)
            textSize = 12f
        })

        box.addView(Button(this).apply {
            text = "Close"
            setOnClickListener { togglePanel() }
        })

        box.addView(Button(this).apply {
            text = "Turn off PG"
            setOnClickListener { stopSelf() }
        })

        val p = WindowManager.LayoutParams(
            dp(260), WindowManager.LayoutParams.WRAP_CONTENT,
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
        wm.addView(box, p)
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
