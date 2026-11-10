package com.example.poolguide

import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.IBinder
import android.view.*
import android.widget.*
import kotlin.math.*

class GuideOverlayService : Service() {
    private lateinit var wm: WindowManager
    private var panel: View? = null
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (panel != null) return START_NOT_STICKY
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(14,10,14,12)
            background = GradientDrawable().apply {
                setColor(Color.argb(235,16,24,32)); cornerRadius=22f
                setStroke(2,Color.rgb(55,214,165))
            }
        }
        val header = TextView(this).apply {
            text = "POOL GUIDE  ⋮  drag to move     ×"
            textSize=14f; setTextColor(Color.WHITE); setPadding(4,6,4,10)
        }
        box.addView(header)
        val info = TextView(this).apply {
            text = "Manual visual guide\nAdjust the angle below."
            textSize=13f; setTextColor(Color.WHITE)
        }
        box.addView(info)
        val seek = SeekBar(this).apply { max=180; progress=45 }
        box.addView(seek)
        val angle = TextView(this).apply {
            text = "Angle: 45°"; setTextColor(Color.rgb(55,214,165)); textSize=15f
        }
        box.addView(angle)
        seek.setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s:SeekBar?, p:Int, user:Boolean) {
                angle.text="Angle: $p°"
                info.text="Manual visual guide\nDirection: ${p}°\nThis is not linked to the game screen."
            }
            override fun onStartTrackingTouch(s:SeekBar?) {}
            override fun onStopTrackingTouch(s:SeekBar?) {}
        })
        val close = Button(this).apply {
            text="Close guide"
            setOnClickListener { stopSelf() }
        }
        box.addView(close)
        header.setOnTouchListener(object: View.OnTouchListener {
            var sx=0f; var sy=0f; var px=0; var py=0
            override fun onTouch(v:View?, e:MotionEvent):Boolean {
                val lp=panel?.layoutParams as? WindowManager.LayoutParams ?: return false
                when(e.action) {
                    MotionEvent.ACTION_DOWN -> { sx=e.rawX; sy=e.rawY; px=lp.x; py=lp.y; return true }
                    MotionEvent.ACTION_MOVE -> { lp.x=px+(e.rawX-sx).toInt(); lp.y=py+(e.rawY-sy).toInt(); wm.updateViewLayout(panel,lp); return true }
                }
                return false
            }
        })
        val type = if(android.os.Build.VERSION.SDK_INT>=26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE
        val params=WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            type, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply { gravity=Gravity.TOP or Gravity.END; x=20; y=150 }
        panel=box
        wm.addView(box,params)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        panel?.let { try { wm.removeView(it) } catch (_:Exception) {} }
        panel=null
        super.onDestroy()
    }
}
