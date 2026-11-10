
package com.example.poolguide

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.ScrollView
import kotlin.math.atan2
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.cos

class MainActivity : Activity() {
    private lateinit var board: PracticeBoard
    private lateinit var angleLabel: TextView
    private var waitingForOverlayPermission = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 24, 20, 24)
            setBackgroundColor(Color.rgb(18, 28, 25))
        }

        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        scroll.addView(content)

        content.addView(TextView(this).apply {
            text = "POOL GUIDE"
            textSize = 28f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        })

        content.addView(TextView(this).apply {
            text = "Practice tools"
            textSize = 15f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
        })

        addToolButton(content, "Start floating PG button") {
            startFloatingButton()
        }

        board = PracticeBoard()
        content.addView(board, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 230
        ))

        angleLabel = TextView(this).apply {
            text = "Aim angle: 0°"
            textSize = 17f
            setTextColor(Color.WHITE)
        }
        content.addView(angleLabel)

        val slider = SeekBar(this).apply {
            max = 360
            setOnSeekBarChangeListener(
                object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(
                        seekBar: SeekBar?,
                        value: Int,
                        fromUser: Boolean
                    ) {
                        angleLabel.text = "Aim angle: $value°"
                        board.angle = value.toFloat()
                        board.invalidate()
                    }

                    override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                    override fun onStopTrackingTouch(seekBar: SeekBar?) {}
                }
            )
        }
        content.addView(slider)

        addToolButton(content, "Angle calculator") {
            showAngleCalculator()
        }

        addToolButton(content, "Bank shot calculator") {
            showBankCalculator()
        }

        addToolButton(content, "Practice shot simulator") {
            AlertDialog.Builder(this)
                .setTitle("Practice shot")
                .setMessage(
                    "Adjust the aim angle using the slider. " +
                    "Use the guideline to practise different shots."
                )
                .setPositiveButton("OK", null)
                .show()
        }

        addToolButton(content, "Guideline practice") {
            board.showGuide = !board.showGuide
            board.invalidate()
        }

        page.addView(scroll)
        setContentView(page)
    }

    override fun onResume() {
        super.onResume()

        if (waitingForOverlayPermission &&
            Build.VERSION.SDK_INT >= 23 &&
            Settings.canDrawOverlays(this)
        ) {
            waitingForOverlayPermission = false
            startOverlayService()
        }
    }

    private fun startFloatingButton() {
        if (Build.VERSION.SDK_INT >= 23 &&
            !Settings.canDrawOverlays(this)
        ) {
            waitingForOverlayPermission = true

            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        } else {
            startOverlayService()
        }
    }

    private fun startOverlayService() {
        val intent = Intent(this, FloatingOverlayService::class.java)

        if (Build.VERSION.SDK_INT >= 26) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun addToolButton(
        parent: LinearLayout,
        label: String,
        action: () -> Unit
    ) {
        val button = Button(this).apply {
            text = label
            isAllCaps = false
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.rgb(10, 76, 54))
            setOnClickListener { action() }
        }

        parent.addView(button)
    }

    private fun showAngleCalculator() {
        val distance = EditText(this).apply {
            hint = "Forward distance"
            inputType = 8194
        }

        val offset = EditText(this).apply {
            hint = "Side offset"
            inputType = 8194
        }

        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 8, 24, 0)
            addView(distance)
            addView(offset)
        }

        AlertDialog.Builder(this)
            .setTitle("Angle calculator")
            .setView(box)
            .setPositiveButton("Calculate") { _, _ ->
                val x = offset.text.toString().toFloatOrNull() ?: 0f
                val y = distance.text.toString().toFloatOrNull() ?: 0f

                val degrees = Math.toDegrees(
                    atan2(x, y).toDouble()
                )

                AlertDialog.Builder(this)
                    .setMessage(
                        "Aim angle: ${
                            (degrees * 10).roundToInt() / 10.0
                        }°"
                    )
                    .setPositiveButton("OK", null)
                    .show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showBankCalculator() {
        val width = EditText(this).apply {
            hint = "Table width"
            inputType = 8194
        }

        val target = EditText(this).apply {
            hint = "Target distance from left cushion"
            inputType = 8194
        }

        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 8, 24, 0)
            addView(width)
            addView(target)
        }

        AlertDialog.Builder(this)
            .setTitle("Bank shot calculator")
            .setView(box)
            .setPositiveButton("Calculate") { _, _ ->
                val w = width.text.toString().toFloatOrNull()
                val x = target.text.toString().toFloatOrNull()

                val result = if (w != null && x != null && w > 0f) {
                    "Mirror point: ${
                        (w - x).coerceIn(0f, w).roundToInt()
                    } units"
                } else {
                    "Please enter valid numbers."
                }

                AlertDialog.Builder(this)
                    .setMessage(result)
                    .setPositiveButton("OK", null)
                    .show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private inner class PracticeBoard : View(this@MainActivity) {
        var angle = 0f
        var showGuide = true

        private val felt = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(20, 112, 75)
        }

        private val rail = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(105, 65, 38)
        }

        private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            strokeWidth = 3f
            style = Paint.Style.STROKE
        }

        private val ball = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)

            canvas.drawRoundRect(
                12f, 12f,
                width - 12f, height - 12f,
                18f, 18f, rail
            )

            canvas.drawRoundRect(
                26f, 26f,
                width - 26f, height - 26f,
                10f, 10f, felt
            )

            val startX = width / 2f
            val startY = height * 0.72f

            canvas.drawCircle(startX, startY, 9f, ball)

            if (showGuide) {
                val radians = Math.toRadians(angle.toDouble())
                val length = width * 0.42f

                val endX = startX +
                    (sin(radians) * length).toFloat()

                val endY = startY -
                    (cos(radians) * length).toFloat()

                canvas.drawLine(
                    startX, startY, endX, endY, line
                )
            }

            canvas.drawCircle(
                width * 0.68f,
                height * 0.34f,
                8f,
                ball
            )
        }
    }
}
