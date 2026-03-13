package com.kndhjk.nokiaworms

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

class GameView(context: Context) : View(context) {
    private val sky = Paint().apply { color = Color.rgb(139, 197, 255) }
    private val mountainBack = Paint().apply { color = Color.rgb(156, 190, 118) }
    private val mountainFront = Paint().apply { color = Color.rgb(122, 157, 83) }
    private val ground = Paint().apply { color = Color.rgb(99, 73, 45) }
    private val grass = Paint().apply { color = Color.rgb(95, 160, 66) }
    private val wormA = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(255, 170, 64) }
    private val wormB = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(88, 222, 120) }
    private val wormOutline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textSize = 40f
    }
    private val smallText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textSize = 28f
    }
    private val hud = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(190, 255, 255, 255)
    }
    private val projectilePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK }
    private val blastPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(180, 255, 215, 64)
    }
    private val windPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(60, 90, 140)
        strokeWidth = 6f
    }

    private var angleDeg = 45f
    private var power = 0.6f
    private var activePlayer = 0
    private var projectile: Projectile? = null
    private var explosion: Explosion? = null
    private var lastTimeNanos = System.nanoTime()
    private var winner: Int? = null
    private var wind = randomWind()
    private var turnStartMs = System.currentTimeMillis()
    private val turnDurationMs = 20_000L
    private var terrain: FloatArray? = null

    private data class Worm(var x: Float, var yOffset: Float = 0f, var hp: Int)
    private data class Projectile(var x: Float, var y: Float, var vx: Float, var vy: Float)
    private data class Explosion(var x: Float, var y: Float, var radius: Float, var age: Float = 0f)

    private val worms = arrayOf(
        Worm(0.18f, hp = 100),
        Worm(0.82f, hp = 100)
    )

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat().coerceAtLeast(1f)
        val h = height.toFloat().coerceAtLeast(1f)
        ensureTerrain(w, h)
        updateFrame(w, h)

        drawBackground(canvas, w, h)
        drawTerrain(canvas, w, h)
        drawWorms(canvas, w)
        drawProjectile(canvas)
        drawExplosion(canvas)
        drawHud(canvas, w, h)

        if (projectile != null || explosion != null || winner != null) {
            invalidate()
        }
    }

    private fun ensureTerrain(w: Float, h: Float) {
        val widthInt = w.toInt().coerceAtLeast(1)
        if (terrain?.size == widthInt) return
        terrain = FloatArray(widthInt)
        val base = h * 0.72f
        for (x in 0 until widthInt) {
            val xf = x / w
            val wave = sin(xf * Math.PI * 1.7).toFloat() * 40f
            val ripple = sin(xf * Math.PI * 7.5).toFloat() * 14f
            terrain!![x] = base + wave + ripple
        }
        terrain!![0] = base
        terrain!![widthInt - 1] = base
        snapWormsToTerrain(w)
    }

    private fun updateFrame(w: Float, h: Float) {
        val now = System.nanoTime()
        val dt = ((now - lastTimeNanos) / 1_000_000_000f).coerceAtMost(0.033f)
        lastTimeNanos = now

        projectile?.let { updateProjectile(it, dt, w, h) }
        explosion?.let { updateExplosion(it, dt) }

        if (winner == null && projectile == null && explosion == null) {
            val leftMs = turnDurationMs - (System.currentTimeMillis() - turnStartMs)
            if (leftMs <= 0) advanceTurn()
        }
    }

    private fun drawBackground(canvas: Canvas, w: Float, h: Float) {
        canvas.drawRect(0f, 0f, w, h, sky)

        val back = Path().apply {
            moveTo(0f, h * 0.70f)
            cubicTo(w * 0.15f, h * 0.35f, w * 0.32f, h * 0.55f, w * 0.45f, h * 0.42f)
            cubicTo(w * 0.60f, h * 0.26f, w * 0.75f, h * 0.58f, w, h * 0.40f)
            lineTo(w, h)
            lineTo(0f, h)
            close()
        }
        canvas.drawPath(back, mountainBack)

        val front = Path().apply {
            moveTo(0f, h * 0.78f)
            cubicTo(w * 0.20f, h * 0.48f, w * 0.35f, h * 0.66f, w * 0.55f, h * 0.46f)
            cubicTo(w * 0.70f, h * 0.30f, w * 0.82f, h * 0.62f, w, h * 0.50f)
            lineTo(w, h)
            lineTo(0f, h)
            close()
        }
        canvas.drawPath(front, mountainFront)
    }

    private fun drawTerrain(canvas: Canvas, w: Float, h: Float) {
        val terrainLine = terrain ?: return
        val path = Path().apply {
            moveTo(0f, h)
            lineTo(0f, terrainLine.first())
            for (x in terrainLine.indices) lineTo(x.toFloat(), terrainLine[x])
            lineTo(w, h)
            close()
        }
        canvas.drawPath(path, ground)

        val grassPath = Path().apply {
            moveTo(0f, terrainLine.first())
            for (x in terrainLine.indices step 2) {
                lineTo(x.toFloat(), terrainLine[x] - 6f)
            }
        }
        canvas.drawPath(grassPath, grass)
    }

    private fun drawWorms(canvas: Canvas, w: Float) {
        worms.forEachIndexed { index, worm ->
            if (worm.hp <= 0) return@forEachIndexed
            val cx = worm.x * w
            val cy = wormY(index, w)
            val paint = if (index == 0) wormA else wormB
            canvas.drawCircle(cx, cy, 22f, paint)
            canvas.drawRect(cx - 16f, cy + 14f, cx + 16f, cy + 38f, paint)
            canvas.drawCircle(cx, cy, 22f, wormOutline)
            canvas.drawRect(cx - 16f, cy + 14f, cx + 16f, cy + 38f, wormOutline)
            canvas.drawText("${worm.hp}", cx - 18f, cy - 28f, text)
            if (index == activePlayer && winner == null) {
                canvas.drawText("◀", cx - 40f, cy - 10f, text)
            }
        }
    }

    private fun drawProjectile(canvas: Canvas) {
        projectile?.let { canvas.drawCircle(it.x, it.y, 10f, projectilePaint) }
    }

    private fun drawExplosion(canvas: Canvas) {
        explosion?.let {
            val alpha = ((1f - it.age / 0.35f) * 180).toInt().coerceIn(0, 180)
            blastPaint.alpha = alpha
            canvas.drawCircle(it.x, it.y, it.radius * (0.55f + it.age * 1.2f), blastPaint)
        }
    }

    private fun drawHud(canvas: Canvas, w: Float, h: Float) {
        canvas.drawRoundRect(20f, 20f, w - 20f, 175f, 18f, 18f, hud)
        val turn = if (activePlayer == 0) "Orange" else "Green"
        val leftMs = max(0, turnDurationMs - (System.currentTimeMillis() - turnStartMs))
        val seconds = leftMs / 1000 + 1
        canvas.drawText("Turn: $turn", 40f, 62f, text)
        canvas.drawText("Angle: ${angleDeg.toInt()}°", 40f, 104f, text)
        canvas.drawText("Power: ${(power * 100).toInt()}%", w * 0.34f, 104f, text)
        canvas.drawText("Wind: ${"%+.1f".format(wind / 14f)}", w * 0.60f, 104f, text)
        canvas.drawText("Time: ${seconds}s", w * 0.80f, 62f, text)

        drawWindArrow(canvas, w * 0.78f, 108f)

        val footer = winner?.let { idx ->
            if (idx == 0) "Orange wins — tap to restart"
            else "Green wins — tap to restart"
        } ?: "Left/right aim  Top/bottom power  Center fire"
        canvas.drawText(footer, 40f, h - 28f, smallText)
    }

    private fun drawWindArrow(canvas: Canvas, x: Float, y: Float) {
        val length = abs(wind) * 7f + 14f
        val dir = if (wind >= 0f) 1f else -1f
        canvas.drawLine(x, y, x + length * dir, y, windPaint)
        canvas.drawLine(x + length * dir, y, x + (length - 12f) * dir, y - 8f, windPaint)
        canvas.drawLine(x + length * dir, y, x + (length - 12f) * dir, y + 8f, windPaint)
    }

    private fun updateProjectile(p: Projectile, dt: Float, w: Float, h: Float) {
        p.x += p.vx * dt
        p.y += p.vy * dt
        p.vx += wind * dt
        p.vy += 640f * dt

        if (p.x !in 0f..w || p.y > h || p.y < -40f) {
            explodeAt(p.x.coerceIn(0f, w), p.y.coerceIn(0f, h))
            return
        }

        val terrainLine = terrain ?: return
        val groundY = terrainHeightAt(p.x)
        if (p.y >= groundY) {
            explodeAt(p.x, groundY)
            return
        }

        worms.forEachIndexed { index, worm ->
            if (worm.hp <= 0) return@forEachIndexed
            val wx = worm.x * w
            val wy = wormY(index, w)
            val dx = p.x - wx
            val dy = p.y - wy
            if (dx * dx + dy * dy <= 34f * 34f) {
                explodeAt(p.x, p.y)
                return
            }
        }
    }

    private fun updateExplosion(explosion: Explosion, dt: Float) {
        explosion.age += dt
        if (explosion.age >= 0.35f) {
            this.explosion = null
            evaluateWinner()
            if (winner == null) advanceTurn()
        }
    }

    private fun explodeAt(x: Float, y: Float) {
        val blastRadius = 48f
        projectile = null
        explosion = Explosion(x, y, blastRadius)
        carveTerrain(x, y, blastRadius)
        damageWorms(x, y, blastRadius)
    }

    private fun carveTerrain(x: Float, y: Float, radius: Float) {
        val terrainLine = terrain ?: return
        val start = max(0, (x - radius - 2).toInt())
        val end = min(terrainLine.lastIndex, (x + radius + 2).toInt())
        for (ix in start..end) {
            val dx = ix - x
            val inside = radius * radius - dx * dx
            if (inside <= 0f) continue
            val cutDepth = kotlin.math.sqrt(inside)
            val newTop = y + cutDepth
            if (newTop > terrainLine[ix]) terrainLine[ix] = newTop
        }
        snapWormsToTerrain(width.toFloat().coerceAtLeast(1f))
    }

    private fun damageWorms(x: Float, y: Float, radius: Float) {
        val w = width.toFloat().coerceAtLeast(1f)
        worms.forEachIndexed { index, worm ->
            if (worm.hp <= 0) return@forEachIndexed
            val wx = worm.x * w
            val wy = wormY(index, w)
            val distance = kotlin.math.sqrt((wx - x) * (wx - x) + (wy - y) * (wy - y))
            if (distance <= radius * 1.25f) {
                val damage = ((1f - distance / (radius * 1.25f)) * 55f).toInt().coerceAtLeast(8)
                worm.hp = (worm.hp - damage).coerceAtLeast(0)
            }
        }
    }

    private fun evaluateWinner() {
        val alive = worms.mapIndexedNotNull { index, worm -> if (worm.hp > 0) index else null }
        winner = when (alive.size) {
            0 -> 1 - activePlayer
            1 -> alive.first()
            else -> null
        }
    }

    private fun advanceTurn() {
        activePlayer = 1 - activePlayer
        angleDeg = 45f
        power = 0.6f
        wind = randomWind()
        turnStartMs = System.currentTimeMillis()
        invalidate()
    }

    private fun snapWormsToTerrain(w: Float) {
        worms.forEachIndexed { index, worm ->
            if (worm.hp <= 0) return@forEachIndexed
            val xPx = worm.x * w
            val y = terrainHeightAt(xPx) - 26f
            worm.yOffset = y
        }
    }

    private fun terrainHeightAt(x: Float): Float {
        val terrainLine = terrain ?: return height * 0.72f
        val i = x.toInt().coerceIn(0, terrainLine.lastIndex)
        return terrainLine[i]
    }

    private fun wormY(index: Int, w: Float): Float {
        val worm = worms[index]
        if (worm.yOffset == 0f) snapWormsToTerrain(w)
        return worm.yOffset
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action != MotionEvent.ACTION_DOWN) return true
        if (winner != null) {
            restart()
            return true
        }
        if (projectile != null || explosion != null) return true
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
        val startY = wormY(activePlayer, width.toFloat().coerceAtLeast(1f)) - 16f
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

    private fun restart() {
        worms[0].hp = 100
        worms[1].hp = 100
        worms[0].x = 0.18f
        worms[1].x = 0.82f
        winner = null
        projectile = null
        explosion = null
        terrain = null
        activePlayer = 0
        angleDeg = 45f
        power = 0.6f
        wind = randomWind()
        turnStartMs = System.currentTimeMillis()
        invalidate()
    }

    private fun randomWind(): Float = Random.nextFloat() * 80f - 40f
}
