package com.example.poolguide

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.*
import kotlin.math.*

class MainActivity : Activity() {
    private lateinit var angleResult: TextView
    private lateinit var bankResult: TextView
    private lateinit var practice: PracticeView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(16,24,32)
        window.navigationBarColor = Color.rgb(16,24,32)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 24, 20, 20)
            setBackgroundColor(Color.rgb(16,24,32))
        }
        val title = TextView(this).apply {
            text = "POOL GUIDE"
            textSize = 25f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, 8, 0, 8)
        }
        root.addView(title)
        val subtitle = TextView(this).apply {
            text = "Manual practice companion • No game automation"
            textSize = 13f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 18)
        }
        root.addView(subtitle)

        val overlayButton = Button(this).apply {
            text = "Enable floating guide"
            setOnClickListener { enableOverlay() }
        }
        root.addView(overlayButton, matchWidth())
        val stopButton = Button(this).apply {
            text = "Close floating guide"
            setOnClickListener { stopService(Intent(this@MainActivity, GuideOverlayService::class.java)) }
        }
        root.addView(stopButton, matchWidth())

        val section = TextView(this).apply {
            text = "ANGLE & BANK ESTIMATOR"
            textSize = 17f
            setTextColor(Color.rgb(55,214,165))
            setPadding(0, 20, 0, 8)
        }
        root.addView(section)
        val angleInput = EditText(this).apply {
            hint = "Enter aiming angle (degrees)"
            inputType =  numberInputType()
            setTextColor(Color.WHITE); setHintTextColor(Color.GRAY)
        }
        root.addView(angleInput, matchWidth())
        angleResult = resultText()
        root.addView(angleResult)
        val calcAngle = Button(this).apply {
            text = "Calculate angle"
            setOnClickListener {
                val a = angleInput.text.toString().toDoubleOrNull()
                angleResult.text = if (a == null) "Enter an angle from 0 to 180." else
                    "Angle: ${"%.1f".format(a.coerceIn(0.0,180.0))}°  •  Complement: ${"%.1f".format(180.0-a.coerceIn(0.0,180.0))}°"
            }
        }
        root.addView(calcAngle, matchWidth())

        val bankInput = EditText(this).apply {
            hint = "Cushion offset (0–100)"
            inputType = numberInputType()
            setTextColor(Color.WHITE); setHintTextColor(Color.GRAY)
        }
        root.addView(bankInput, matchWidth())
        bankResult = resultText()
        root.addView(bankResult)
        val calcBank = Button(this).apply {
            text = "Estimate bank path"
            setOnClickListener {
                val n = bankInput.text.toString().toDoubleOrNull()
                bankResult.text = if (n == null) "Enter a cushion offset." else {
                    val v = n.coerceIn(0.0,100.0)
                    "Illustrative mirror estimate: ${"%.1f".format(100.0-v)} / ${"%.1f".format(v)}"
                }
            }
        }
        root.addView(calcBank, matchWidth())

        val practiceTitle = TextView(this).apply {
            text = "PRACTICE TABLE"
            textSize = 17f; setTextColor(Color.rgb(55,214,165)); setPadding(0,20,0,8)
        }
        root.addView(practiceTitle)
        practice = PracticeView(this)
        root.addView(practice, LinearLayout.LayoutParams(-1, 250))
        val reset = Button(this).apply {
            text = "Reset practice markers"
            setOnClickListener { practice.reset() }
        }
        root.addView(reset, matchWidth())
        val note = TextView(this).apply {
            text = "Drag the white cue ball and yellow object ball. The line is a visual exercise only; it is not calibrated to a live game board."
            textSize = 12f; setTextColor(Color.LTGRAY); setPadding(0,8,0,8)
        }
        root.addView(note)
        setContentView(ScrollView(this).apply { addView(root) })
    }

    private fun enableOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            val i = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            startActivity(i)
        } else {
            startService(Intent(this, GuideOverlayService::class.java))
            Toast.makeText(this, "Floating guide enabled", Toast.LENGTH_SHORT).show()
        }
    }
    private fun matchWidth() = LinearLayout.LayoutParams(-1, -2)
    private fun resultText() = TextView(this).apply {
        text = "Result appears here."
        textSize = 14f; setTextColor(Color.WHITE); setPadding(4,8,4,8)
    }
    private fun numberInputType() = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL or android.text.InputType.TYPE_NUMBER_FLAG_SIGNED
}

class PracticeView(context: android.content.Context) : View(context) {
    private var cueX = 0.28f
    private var cueY = 0.55f
    private var objX = 0.72f
    private var objY = 0.42f
    private var draggingCue = true
    private val paint = android.graphics.Paint(3)
    private var w = 1f
    private var h = 1f

    fun reset() { cueX=.28f; cueY=.55f; objX=.72f; objY=.42f; invalidate() }

    override fun onDraw(c: android.graphics.Canvas) {
        super.onDraw(c)
        w = width.toFloat(); h = height.toFloat()
        paint.color = Color.rgb(20,105,73); c.drawRoundRect(2f,2f,w-2,h-2,18f,18f,paint)
        paint.style = android.graphics.Paint.Style.STROKE
        paint.strokeWidth = 5f; paint.color = Color.rgb(180,220,190)
        c.drawRoundRect(8f,8f,w-8,h-8,12f,12f,paint)
        paint.style = android.graphics.Paint.Style.FILL
        val x1=cueX*w; val y1=cueY*h; val x2=objX*w; val y2=objY*h
        paint.color=Color.WHITE; paint.strokeWidth=3f
        c.drawLine(x1,y1,x2,y2,paint)
        paint.color=Color.WHITE; c.drawCircle(x1,y1,16f,paint)
        paint.color=Color.YELLOW; c.drawCircle(x2,y2,16f,paint)
        paint.color=Color.BLACK; paint.textSize=12f
        c.drawText("CUE",x1-12,y1+4,paint); c.drawText("OBJECT",x2-22,y2+4,paint)
    }

    override fun onTouchEvent(e: android.view.MotionEvent): Boolean {
        when(e.action) {
            android.view.MotionEvent.ACTION_DOWN -> {
                val dc=hypot(e.x-cueX*w,e.y-cueY*h)
                val ob=hypot(e.x-objX*w,e.y-objY*h)
                draggingCue=dc<=ob
                return true
            }
            android.view.MotionEvent.ACTION_MOVE -> {
                val nx=(e.x/w).coerceIn(.06f,.94f); val ny=(e.y/h).coerceIn(.08f,.92f)
                if(draggingCue){cueX=nx;cueY=ny}else{objX=nx;objY=ny}
                invalidate(); return true
            }
        }
        return true
    }
}
