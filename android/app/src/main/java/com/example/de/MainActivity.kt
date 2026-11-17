package com.example.de

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.os.Build
import android.os.SystemClock
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.sqrt

class MainActivity : Activity(), SensorEventListener {

    private lateinit var dice: DiceView
    private lateinit var totalText: TextView
    private lateinit var historyText: TextView
    private val countButtons = mutableListOf<Button>()
    private val history = ArrayDeque<String>()

    private var sensorManager: SensorManager? = null
    private var lastShake = 0L

    private val bgColor = Color.parseColor("#14213D")
    private val accent = Color.parseColor("#FCA311")
    private val panel = Color.parseColor("#1F3358")

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bgColor)
            setPadding(dp(20), dp(20), dp(20), dp(20))
            setOnApplyWindowInsetsListener { v, insets ->
                v.setPadding(
                    dp(20) + insets.systemWindowInsetLeft,
                    dp(20) + insets.systemWindowInsetTop,
                    dp(20) + insets.systemWindowInsetRight,
                    dp(20) + insets.systemWindowInsetBottom
                )
                insets
            }
        }

        val title = TextView(this).apply {
            text = "Dé virtuel"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f)
            gravity = Gravity.CENTER
        }
        root.addView(title, LinearLayout.LayoutParams(-1, -2))

        dice = DiceView(this).apply {
            contentDescription = "Dés. Touchez pour lancer."
            setOnClickListener { doRoll() }
        }
        root.addView(dice, LinearLayout.LayoutParams(-1, 0, 1f))

        totalText = TextView(this).apply {
            text = "Touchez le dé ou secouez le téléphone"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            gravity = Gravity.CENTER
        }
        root.addView(totalText, LinearLayout.LayoutParams(-1, -2))

        historyText = TextView(this).apply {
            setTextColor(Color.parseColor("#B0BCD4"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, dp(16))
        }
        root.addView(historyText, LinearLayout.LayoutParams(-1, -2))

        // Choix du nombre de dés
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        for (n in 1..DiceView.MAX_DICE) {
            val b = Button(this).apply {
                text = if (n == 1) "1 dé" else "$n dés"
                isAllCaps = false
                setOnClickListener {
                    if (!dice.rolling) {
                        dice.setCount(n)
                        totalText.text = "Touchez le dé ou secouez le téléphone"
                        styleCountButtons()
                    }
                }
            }
            countButtons.add(b)
            row.addView(b, LinearLayout.LayoutParams(0, dp(48), 1f).apply {
                setMargins(dp(4), 0, dp(4), 0)
            })
        }
        root.addView(row, LinearLayout.LayoutParams(-1, -2))

        val roll = Button(this).apply {
            text = "LANCER"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            setTextColor(bgColor)
            background = rounded(accent)
            setOnClickListener { doRoll() }
        }
        root.addView(roll, LinearLayout.LayoutParams(-1, dp(64)).apply { topMargin = dp(12) })

        styleCountButtons()
        setContentView(root, ViewGroup.LayoutParams(-1, -1))

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
    }

    private fun rounded(color: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(14).toFloat()
    }

    private fun styleCountButtons() {
        countButtons.forEachIndexed { i, b ->
            val selected = (i + 1) == dice.count
            b.background = rounded(if (selected) accent else panel)
            b.setTextColor(if (selected) bgColor else Color.WHITE)
        }
    }

    private fun doRoll() {
        if (dice.rolling) return
        vibrate()
        totalText.text = "…"
        dice.roll { values ->
            val total = values.sum()
            totalText.text = if (values.size == 1) "Résultat : $total"
            else "Total : $total  (${values.joinToString(" + ")})"
            history.addFirst(total.toString())
            while (history.size > 10) history.removeLast()
            historyText.text = "Derniers lancers : " + history.joinToString(" · ")
        }
    }

    private fun vibrate() {
        val v: Vibrator? = if (Build.VERSION.SDK_INT >= 31) {
            (getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(VIBRATOR_SERVICE) as Vibrator
        }
        v?.vibrate(VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    override fun onResume() {
        super.onResume()
        sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager?.unregisterListener(this)
    }

    override fun onSensorChanged(e: SensorEvent) {
        val g = sqrt(e.values[0] * e.values[0] + e.values[1] * e.values[1] + e.values[2] * e.values[2]) /
            SensorManager.GRAVITY_EARTH
        val now = SystemClock.elapsedRealtime()
        if (g > 2.5f && now - lastShake > 1000) {
            lastShake = now
            doRoll()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
