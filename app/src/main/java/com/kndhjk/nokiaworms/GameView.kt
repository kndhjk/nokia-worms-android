package com.kndhjk.nokiaworms

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

class GameView(context: Context) : View(context) {
    private val sky = Paint().apply { color = Color.rgb(139, 197, 255) }
    private val ground = Paint().apply { color = Color.rgb(99, 73, 45) }
    private val grass = Paint().apply { color = Color.rgb(95, 160, 66) }
    private val wormA = Paint().apply { color = Color.rgb(255, 170, 64) }
    private val wormB = Paint().apply { color = Color.rgb(88, 222, 120) }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textSize = 42f
    }
    private val hud = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(180, 255, 255, 255)
    }
    private val projectilePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
    }

    private var angleDeg = 45f
    private var power = 0.6f
    private var activePlayer = 0
    private var projectile: Projectile? = null
    private var lastTimeNanos = System.nanoTime()

    private data class Worm(var x: Float, var hp: Int)
    private data class Projectile(var x: Float, var y: Float, var vx: Float, var vy: Float)

    private val worms = arrayOf(
        Worm(0.2f, 100),
        Worm(0.8f, 100)
    )

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat().coerceAtLeast(1f)
        val h = height.toFloat().coerceAtLeast(1f)
        val groundTop = h * 0.72f

        canvas.drawRect(0f, 0f, w, h, sky)
        canvas.drawRect(0f, groundTop, w, h, ground)
        canvas.drawRect(0f, groundTop, w, groundTop + 16f, grass)

        updateProjectile(w, h, groundTop)

        worms.forEachIndexed { index, worm ->
            val cx = worm.x * w
            val cy = groundTop - 24f
            val paint = if (index == 0) wormA else wormB
            canvas.drawCircle(cx, cy, 22f, paint)
            canvas.drawRect(cx - 18f, cy + 16f, cx + 18f, cy + 40f, paint)
            canvas.drawText("${worm.hp}", cx - 18f, cy - 28f, text)
        }

        projectile?.let {
            canvas.drawCircle(it.x, it.y, 10f, projectilePaint)
            invalidate()
        }

        canvas.drawRoundRect(20f, 20f, w - 20f, 145f, 18f, 18f, hud)
        val turn = if (activePlayer == 0) "Orange" else "Green"
        canvas.drawText("Turn: $turn", 40f, 65f, text)
        canvas.drawText("Angle: ${angleDeg.toInt()}°", 40f, 105f, text)
        canvas.drawText("Power: ${(power * 100).toInt()}%", w * 0.45f, 105f, text)
        canvas.drawText("Tap left/right to aim, top/bottom to power, center to fire", 40f, h - 30f, text)
    }

    private fun updateProjectile(w: Float, h: Float, groundTop: Float) {
        val now = System.nanoTime()
        val dt = ((now - lastTimeNanos) / 1_000_000_000f).coerceAtMost(0.033f)
        lastTimeNanos = now
        val p = projectile ?: return
        p.x += p.vx * dt
        p.y += p.vy * dt
        p.vy += 640f * dt

        val enemyIndex = 1 - activePlayer
        val enemy = worms[enemyIndex]
        val enemyX = enemy.x * w
        val enemyY = groundTop - 24f
        val hit = (p.x - enemyX) * (p.x - enemyX) + (p.y - enemyY) * (p.y - enemyY) < 34f * 34f
        if (hit) {
            enemy.hp = (enemy.hp - 25).coerceAtLeast(0)
            projectile = null
            activePlayer = enemyIndex
            return
        }

        if (p.y >= groundTop || p.x !in 0f..w || p.y !in 0f..h) {
            projectile = null
            activePlayer = enemyIndex
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action != MotionEvent.ACTION_DOWN) return true
        if (projectile != null) return true
        val xRatio = event.x / width.coerceAtLeast(1)
        val yRatio = event.y / height.coerceAtLeast(1)
        when {
            yRatio < 0.25f -> power = (power + 0.1f).coerceAtMost(1f)
            yRatio > 0.75f -> power = (power - 0.1f).coerceAtLeast(0.1f)
            xRatio < 0.33f -> angleDeg = (angleDeg + 5f).coerceAtMost(85f)
            xRatio > 0.66f -> angleDeg = (angleDeg - 5f).coerceAtLeast(5f)
            else -> fire()
        }
        invalidate()
        return true
    }

    private fun fire() {
        val direction = if (activePlayer == 0) 1f else -1f
        val startX = worms[activePlayer].x * width
        val startY = height * 0.72f - 48f
        val radians = Math.toRadians(angleDeg.toDouble())
        val speed = 280f + 520f * power
        projectile = Projectile(
            x = startX,
            y = startY,
            vx = (cos(radians) * speed * direction).toFloat(),
            vy = (-sin(radians) * speed).toFloat()
        )
        lastTimeNanos = System.nanoTime()
        invalidate()
    }
}
