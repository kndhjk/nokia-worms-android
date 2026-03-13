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
import kotlin.math.sqrt
import kotlin.random.Random

class GameView(context: Context) : View(context) {
    private val sky = Paint().apply { color = Color.rgb(139, 197, 255) }
    private val mountainBack = Paint().apply { color = Color.rgb(156, 190, 118) }
    private val mountainFront = Paint().apply { color = Color.rgb(122, 157, 83) }
    private val ground = Paint().apply { color = Color.rgb(99, 73, 45) }
    private val grass = Paint().apply { color = Color.rgb(95, 160, 66) }
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textSize = 72f
        textAlign = Paint.Align.CENTER
    }
    private val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.DKGRAY
        textSize = 30f
        textAlign = Paint.Align.CENTER
    }
    private val panelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(215, 255, 255, 255)
    }
    private val wormA = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(255, 170, 64) }
    private val wormB = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(88, 222, 120) }
    private val wormOutline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textSize = 38f
    }
    private val smallText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textSize = 24f
    }
    private val hud = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(190, 255, 255, 255)
    }
    private val projectilePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK }
    private val blastPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(180, 255, 215, 64) }
    private val windPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(60, 90, 140)
        strokeWidth = 6f
    }
    private val moveHintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(120, 0, 0, 0)
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
    private var selectedWeapon = 0
    private var gameMode = GameMode.TITLE
    private var aiShotAtMs = 0L

    private val weapons = listOf(
        Weapon("Bazooka", 48f, 55, 1.00f),
        Weapon("Grenade", 64f, 70, 0.85f),
        Weapon("Missile", 34f, 40, 1.20f)
    )

    private enum class GameMode { TITLE, PVP, PVE }

    private data class Worm(var x: Float, var yOffset: Float = 0f, var hp: Int, var jumpsLeft: Int = 1)
    private data class Projectile(var x: Float, var y: Float, var vx: Float, var vy: Float, val weapon: Weapon)
    private data class Explosion(var x: Float, var y: Float, var radius: Float, var age: Float = 0f)
    private data class Weapon(val name: String, val radius: Float, val maxDamage: Int, val speedFactor: Float)

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

        if (gameMode == GameMode.TITLE) {
            drawTitleScreen(canvas, w, h)
        } else {
            drawTouchZones(canvas, w, h)
            drawWorms(canvas, w)
            drawProjectile(canvas)
            drawExplosion(canvas)
            drawHud(canvas, w, h)
        }

        if (projectile != null || explosion != null || winner != null || gameMode == GameMode.TITLE) invalidate()
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

        if (gameMode != GameMode.TITLE) {
            projectile?.let { updateProjectile(it, dt, w, h) }
            explosion?.let { updateExplosion(it, dt) }
            if (winner == null && projectile == null && explosion == null) {
                maybeRunAiTurn(w)
                val leftMs = turnDurationMs - (System.currentTimeMillis() - turnStartMs)
                if (leftMs <= 0) advanceTurn()
            }
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
            for (x in terrainLine.indices step 2) lineTo(x.toFloat(), terrainLine[x] - 6f)
        }
        canvas.drawPath(grassPath, grass)
    }

    private fun drawTitleScreen(canvas: Canvas, w: Float, h: Float) {
        canvas.drawRoundRect(w * 0.18f, h * 0.20f, w * 0.82f, h * 0.76f, 28f, 28f, panelPaint)
        canvas.drawText("Nokia Worms", w / 2f, h * 0.32f, titlePaint)
        canvas.drawText("Termux prototype", w / 2f, h * 0.39f, subtitlePaint)
        canvas.drawText("Tap upper half: 1 Player vs AI", w / 2f, h * 0.52f, text)
        canvas.drawText("Tap lower half: 2 Players", w / 2f, h * 0.62f, text)
        canvas.drawText("Legacy feel, new Android build", w / 2f, h * 0.71f, subtitlePaint)
    }

    private fun drawTouchZones(canvas: Canvas, w: Float, h: Float) {
        canvas.drawRect(0f, h * 0.78f, w * 0.22f, h, moveHintPaint)
        canvas.drawRect(w * 0.22f, h * 0.78f, w * 0.44f, h, moveHintPaint)
        canvas.drawRect(w * 0.78f, h * 0.78f, w, h, moveHintPaint)
        canvas.drawText("MOVE◀", 18f, h - 18f, smallText)
        canvas.drawText("JUMP", w * 0.26f, h - 18f, smallText)
        canvas.drawText("▶MOVE", w * 0.82f, h - 18f, smallText)
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
            if (index == activePlayer && winner == null) canvas.drawText("◀", cx - 40f, cy - 10f, text)
        }
    }

    private fun drawProjectile(canvas: Canvas) { projectile?.let { canvas.drawCircle(it.x, it.y, 10f, projectilePaint) } }

    private fun drawExplosion(canvas: Canvas) {
        explosion?.let {
            val alpha = ((1f - it.age / 0.35f) * 180).toInt().coerceIn(0, 180)
            blastPaint.alpha = alpha
            canvas.drawCircle(it.x, it.y, it.radius * (0.55f + it.age * 1.2f), blastPaint)
        }
    }

    private fun drawHud(canvas: Canvas, w: Float, h: Float) {
        canvas.drawRoundRect(20f, 20f, w - 20f, 190f, 18f, 18f, hud)
        val turn = if (activePlayer == 0) "Orange" else if (gameMode == GameMode.PVE) "AI" else "Green"
        val leftMs = max(0, turnDurationMs - (System.currentTimeMillis() - turnStartMs))
        val seconds = leftMs / 1000 + 1
        canvas.drawText("Turn: $turn", 40f, 58f, text)
        canvas.drawText("Weapon: ${weapons[selectedWeapon].name}", 40f, 98f, text)
        canvas.drawText("Angle: ${angleDeg.toInt()}°", 40f, 138f, text)
        canvas.drawText("Power: ${(power * 100).toInt()}%", w * 0.32f, 138f, text)
        canvas.drawText("Wind: ${"%+.1f".format(wind / 14f)}", w * 0.58f, 138f, text)
        canvas.drawText("Time: ${seconds}s", w * 0.80f, 58f, text)
        canvas.drawText("Jumps: ${worms[activePlayer].jumpsLeft}", w * 0.76f, 98f, text)
        drawWindArrow(canvas, w * 0.76f, 142f)
        val footer = winner?.let { idx ->
            when {
                gameMode == GameMode.PVE && idx == 1 -> "AI wins — tap to restart"
                idx == 0 -> "Orange wins — tap to restart"
                else -> "Green wins — tap to restart"
            }
        } ?: if (gameMode == GameMode.PVE && activePlayer == 1) {
            "AI is aiming..."
        } else {
            "Top: power/weapon   Middle: aim/fire   Bottom: move/jump"
        }
        canvas.drawText(footer, 40f, h - 54f, smallText)
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
            explodeAt(p.x.coerceIn(0f, w), p.y.coerceIn(0f, h), p.weapon)
            return
        }
        val groundY = terrainHeightAt(p.x)
        if (p.y >= groundY) {
            explodeAt(p.x, groundY, p.weapon)
            return
        }
        worms.forEachIndexed { index, worm ->
            if (worm.hp <= 0) return@forEachIndexed
            val wx = worm.x * w
            val wy = wormY(index, w)
            val dx = p.x - wx
            val dy = p.y - wy
            if (dx * dx + dy * dy <= 34f * 34f) {
                explodeAt(p.x, p.y, p.weapon)
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

    private fun explodeAt(x: Float, y: Float, weapon: Weapon) {
        projectile = null
        explosion = Explosion(x, y, weapon.radius)
        carveTerrain(x, y, weapon.radius)
        damageWorms(x, y, weapon.radius, weapon.maxDamage)
    }

    private fun carveTerrain(x: Float, y: Float, radius: Float) {
        val terrainLine = terrain ?: return
        val start = max(0, (x - radius - 2).toInt())
        val end = min(terrainLine.lastIndex, (x + radius + 2).toInt())
        for (ix in start..end) {
            val dx = ix - x
            val inside = radius * radius - dx * dx
            if (inside <= 0f) continue
            val cutDepth = sqrt(inside)
            val newTop = y + cutDepth
            if (newTop > terrainLine[ix]) terrainLine[ix] = newTop
        }
        snapWormsToTerrain(width.toFloat().coerceAtLeast(1f))
    }

    private fun damageWorms(x: Float, y: Float, radius: Float, maxDamage: Int) {
        val w = width.toFloat().coerceAtLeast(1f)
        worms.forEachIndexed { index, worm ->
            if (worm.hp <= 0) return@forEachIndexed
            val wx = worm.x * w
            val wy = wormY(index, w)
            val distance = sqrt((wx - x) * (wx - x) + (wy - y) * (wy - y))
            if (distance <= radius * 1.25f) {
                val damage = ((1f - distance / (radius * 1.25f)) * maxDamage).toInt().coerceAtLeast(6)
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
        worms[activePlayer].jumpsLeft = 1
        aiShotAtMs = 0L
        invalidate()
    }

    private fun maybeRunAiTurn(w: Float) {
        if (gameMode != GameMode.PVE || activePlayer != 1 || winner != null) return
        val now = System.currentTimeMillis()
        if (aiShotAtMs == 0L) {
            aiShotAtMs = now + 900L
            return
        }
        if (now < aiShotAtMs) return
        val player = worms[0]
        val ai = worms[1]
        val distance = abs((player.x - ai.x) * w)
        selectedWeapon = when {
            distance > w * 0.45f -> 2
            distance > w * 0.28f -> 0
            else -> 1
        }
        angleDeg = (35 + Random.nextInt(30)).toFloat()
        power = (distance / (w * 0.70f)).coerceIn(0.35f, 0.95f)
        if (Random.nextFloat() < 0.25f && worms[1].jumpsLeft > 0) jumpWorm()
        fire()
        aiShotAtMs = 0L
    }

    private fun snapWormsToTerrain(w: Float) {
        worms.forEachIndexed { index, worm ->
            if (worm.hp <= 0) return@forEachIndexed
            worm.yOffset = terrainHeightAt(worm.x * w) - 26f
        }
    }

    private fun terrainHeightAt(x: Float): Float {
        val terrainLine = terrain ?: return height * 0.72f
        return terrainLine[x.toInt().coerceIn(0, terrainLine.lastIndex)]
    }

    private fun wormY(index: Int, w: Float): Float {
        val worm = worms[index]
        if (worm.yOffset == 0f) snapWormsToTerrain(w)
        return worm.yOffset
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action != MotionEvent.ACTION_DOWN) return true
        val xRatio = event.x / width.coerceAtLeast(1)
        val yRatio = event.y / height.coerceAtLeast(1)
        if (gameMode == GameMode.TITLE) {
            startGame(if (yRatio < 0.5f) GameMode.PVE else GameMode.PVP)
            return true
        }
        if (winner != null) {
            gameMode = GameMode.TITLE
            restart()
            return true
        }
        if (projectile != null || explosion != null) return true
        if (gameMode == GameMode.PVE && activePlayer == 1) return true
        when {
            yRatio < 0.20f && xRatio < 0.5f -> power = (power + 0.1f).coerceAtMost(1f)
            yRatio < 0.20f && xRatio >= 0.5f -> cycleWeapon()
            yRatio > 0.78f && xRatio < 0.22f -> moveWorm(-0.018f)
            yRatio > 0.78f && xRatio < 0.44f -> jumpWorm()
            yRatio > 0.78f && xRatio > 0.78f -> moveWorm(0.018f)
            yRatio > 0.55f -> power = (power - 0.1f).coerceAtLeast(0.1f)
            xRatio < 0.33f -> angleDeg = (angleDeg + 5f).coerceAtMost(85f)
            xRatio > 0.66f -> angleDeg = (angleDeg - 5f).coerceAtLeast(5f)
            else -> fire()
        }
        invalidate()
        return true
    }

    private fun cycleWeapon() {
        selectedWeapon = (selectedWeapon + 1) % weapons.size
    }

    private fun moveWorm(delta: Float) {
        val worm = worms[activePlayer]
        worm.x = (worm.x + delta).coerceIn(0.05f, 0.95f)
        snapWormsToTerrain(width.toFloat().coerceAtLeast(1f))
    }

    private fun jumpWorm() {
        val worm = worms[activePlayer]
        if (worm.jumpsLeft <= 0) return
        val direction = if (activePlayer == 0) 1f else -1f
        worm.x = (worm.x + 0.05f * direction).coerceIn(0.05f, 0.95f)
        worm.jumpsLeft -= 1
        snapWormsToTerrain(width.toFloat().coerceAtLeast(1f))
    }

    private fun fire() {
        val direction = if (activePlayer == 0) 1f else -1f
        val weapon = weapons[selectedWeapon]
        val startX = worms[activePlayer].x * width
        val startY = wormY(activePlayer, width.toFloat().coerceAtLeast(1f)) - 16f
        val radians = Math.toRadians(angleDeg.toDouble())
        val speed = (280f + 520f * power) * weapon.speedFactor
        projectile = Projectile(
            x = startX,
            y = startY,
            vx = (cos(radians) * speed * direction).toFloat(),
            vy = (-sin(radians) * speed).toFloat(),
            weapon = weapon
        )
        lastTimeNanos = System.nanoTime()
        invalidate()
    }

    private fun startGame(mode: GameMode) {
        gameMode = mode
        restart()
    }

    private fun restart() {
        worms[0] = Worm(0.18f, hp = 100)
        worms[1] = Worm(0.82f, hp = 100)
        winner = null
        projectile = null
        explosion = null
        terrain = null
        activePlayer = 0
        selectedWeapon = 0
        angleDeg = 45f
        power = 0.6f
        wind = randomWind()
        turnStartMs = System.currentTimeMillis()
        aiShotAtMs = 0L
        invalidate()
    }

    private fun randomWind(): Float = Random.nextFloat() * 80f - 40f
}
