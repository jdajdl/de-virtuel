package com.example.de

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import android.view.animation.DecelerateInterpolator
import java.security.SecureRandom
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

/** Vue qui dessine 1 à 3 dés et gère l'animation de lancer. */
class DiceView(context: Context) : View(context) {

    var count = 1
        private set
    var rolling = false
        private set

    private val rnd = SecureRandom()
    private val values = IntArray(MAX_DICE) { rnd.nextInt(6) + 1 }
    private val rotations = FloatArray(MAX_DICE)
    private var scale = 1f

    private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(70, 0, 0, 0) }
    private val pipPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#14213D") }

    // Positions des points (colonne, ligne) dans une grille 3x3 de -1 à 1, à plat.
    private val patterns = arrayOf(
        intArrayOf(0, 0),
        intArrayOf(-1, -1, 1, 1),
        intArrayOf(-1, -1, 0, 0, 1, 1),
        intArrayOf(-1, -1, 1, -1, -1, 1, 1, 1),
        intArrayOf(-1, -1, 1, -1, 0, 0, -1, 1, 1, 1),
        intArrayOf(-1, -1, 1, -1, -1, 0, 1, 0, -1, 1, 1, 1),
    )

    fun setCount(n: Int) {
        count = n.coerceIn(1, MAX_DICE)
        invalidate()
    }

    /** Lance les dés. Le résultat est tiré dès le départ puis révélé à la fin de l'animation. */
    fun roll(onEnd: (IntArray) -> Unit) {
        if (rolling) return
        rolling = true

        val n = count
        val finals = IntArray(n) { rnd.nextInt(6) + 1 }
        val spins = FloatArray(n) { (2 + rnd.nextInt(2)) * (if (rnd.nextBoolean()) 1 else -1) * 360f }
        var lastStep = -1

        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 900
            interpolator = DecelerateInterpolator(1.5f)
            addUpdateListener { a ->
                val t = a.animatedValue as Float
                for (i in 0 until n) rotations[i] = spins[i] * t
                scale = 1f + 0.18f * sin(PI * t).toFloat()
                val step = (a.animatedFraction * 12).toInt()
                if (step != lastStep && t < 1f) {
                    lastStep = step
                    for (i in 0 until n) values[i] = rnd.nextInt(6) + 1
                }
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    for (i in 0 until n) {
                        values[i] = finals[i]
                        rotations[i] = 0f
                    }
                    scale = 1f
                    rolling = false
                    invalidate()
                    onEnd(finals)
                }
            })
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cellW = width.toFloat() / count
        val size = min(cellW * 0.8f, height * 0.8f)
        val half = size / 2f
        val corner = size * 0.18f
        val pipR = size * 0.075f
        val pipOffset = size * 0.27f

        for (i in 0 until count) {
            canvas.save()
            canvas.translate(cellW * (i + 0.5f), height / 2f)
            canvas.rotate(rotations[i])
            canvas.scale(scale, scale)

            canvas.drawRoundRect(-half + 6f, -half + 12f, half + 6f, half + 12f, corner, corner, shadowPaint)
            canvas.drawRoundRect(-half, -half, half, half, corner, corner, bodyPaint)

            val p = patterns[values[i] - 1]
            var k = 0
            while (k < p.size) {
                canvas.drawCircle(p[k] * pipOffset, p[k + 1] * pipOffset, pipR, pipPaint)
                k += 2
            }
            canvas.restore()
        }
    }

    companion object {
        const val MAX_DICE = 3
    }
}
