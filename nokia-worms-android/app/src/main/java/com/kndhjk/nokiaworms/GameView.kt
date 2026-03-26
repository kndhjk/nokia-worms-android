package com.kndhjk.nokiaworms

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.view.MotionEvent
import android.view.View
import kotlin.math.atan2
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

class GameView(context: Context) : View(context) {
    private val legacyLogo = loadBitmap("legacy/mgilog.png")
    private val legacyTitle = loadBitmap("legacy/mgi24171.png")
    private val legacyWorm = loadBitmap("legacy/icon.png")
    private val legacyTileA = loadBitmap("legacy/mgc27804.png")
    private val legacyTileB = loadBitmap("legacy/mgc30403.png")
    private val legacyTileC = loadBitmap("legacy/mgc31222.png")

    private val sky = Paint().apply { color = Color.rgb(139, 197, 255) }
    private val mountainBack = Paint().apply { color = Color.rgb(156, 190, 118) }
    private val mountainFront = Paint().apply { color = Color.rgb(122, 157, 83) }
    private val ground = Paint().apply { color = Color.rgb(99, 73, 45) }
    private val grass = Paint().apply { color = Color.rgb(95, 160, 66) }
    private val tilePaintA = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tilePaintB = Paint(Paint.ANTI_ALIAS_FLAG)
    private val iconTilePaint = Paint(Paint.ANTI_ALIAS_FLAG)
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
    private val hudBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val selectBar = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(120, 255, 215, 64)
    }
    private val hpBarBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(70, 70, 70) }
    private val hpBarFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(96, 210, 96) }
    private val windBarBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(70, 70, 70) }
    private val windBarFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(90, 150, 255) }
    private val projectilePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK }
    private val trajectoryPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(120, 0, 0, 0) }
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
    private var moveInput = 0f
    private var aimActive = false
    private var chargingShot = false
    private var chargeStartMs = 0L

    private val weapons = listOf(
        Weapon("Bazooka", 48f, 55, 1.00f),
        Weapon("Grenade", 64f, 70, 0.85f),
        Weapon("Missile", 34f, 40, 1.20f)
    )

    private enum class GameMode { TITLE, PVP, PVE }

    private data class Worm(
        var x: Float,
        var yOffset: Float = 0f,
        var hp: Int,
        var jumpsLeft: Int = 1,
        var movePoints: Int = 100,
        var fallDistance: Float = 0f
    )
    private data class Projectile(var x: Float, var y: Float, var vx: Float, var vy: Float, val weapon: Weapon)
    private data class Explosion(var x: Float, var y: Float, var radius: Float, var age: Float = 0f)
    private data class Weapon(val name: String, val radius: Float, val maxDamage: Int, val speedFactor: Float)

    private val worms = arrayOf(
        Worm(0.18f, hp = 100),
        Worm(0.82f, hp = 100)
    )

    init {
        if (legacyTileA != null) tilePaintA.shader = BitmapShader(legacyTileA, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
        if (legacyTileB != null) tilePaintB.shader = BitmapShader(legacyTileB, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
        if (legacyTileC != null) iconTilePaint.shader = BitmapShader(legacyTileC, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
    }

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
            drawAimPreview(canvas, w, h)
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
            updateTouchControls(dt, w)
            updateWormPhysics(dt, w)
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
        legacyTitle?.let {
            val dest = RectF(w * 0.70f, h * 0.08f, w * 0.96f, h * 0.40f)
            canvas.drawBitmap(it, null, dest, null)
        }
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
        tilePaintA.shader?.let { canvas.drawPath(path, tilePaintA) }
        val grassPath = Path().apply {
            moveTo(0f, terrainLine.first())
            for (x in terrainLine.indices step 2) lineTo(x.toFloat(), terrainLine[x] - 6f)
        }
        canvas.drawPath(grassPath, grass)
        tilePaintB.shader?.let { canvas.drawPath(grassPath, tilePaintB) }
    }

    private fun drawTitleScreen(canvas: Canvas, w: Float, h: Float) {
        val panel = RectF(w * 0.12f, h * 0.16f, w * 0.88f, h * 0.82f)
        canvas.drawRoundRect(panel, 28f, 28f, panelPaint)
        canvas.drawRoundRect(panel, 28f, 28f, hudBorder)
        legacyLogo?.let {
            val dest = RectF(w * 0.18f, h * 0.18f, w * 0.82f, h * 0.48f)
            canvas.drawBitmap(it, null, dest, null)
        } ?: canvas.drawText("Nokia Worms", w / 2f, h * 0.32f, titlePaint)
        legacyWorm?.let {
            canvas.drawBitmap(it, null, RectF(w * 0.20f, h * 0.50f, w * 0.30f, h * 0.62f), null)
            canvas.drawBitmap(it, null, RectF(w * 0.70f, h * 0.50f, w * 0.80f, h * 0.62f), null)
        }
        val row1 = RectF(w * 0.20f, h * 0.53f, w * 0.80f, h * 0.61f)
        val row2 = RectF(w * 0.20f, h * 0.63f, w * 0.80f, h * 0.71f)
        canvas.drawRoundRect(row1, 12f, 12f, selectBar)
        iconTilePaint.shader?.let { canvas.drawRoundRect(row1, 12f, 12f, iconTilePaint) }
        canvas.drawRoundRect(row1, 12f, 12f, hudBorder)
        canvas.drawRoundRect(row2, 12f, 12f, hud)
        canvas.drawRoundRect(row2, 12f, 12f, hudBorder)
        canvas.drawText("1 Player vs AI", w / 2f, h * 0.585f, text)
        canvas.drawText("2 Players", w / 2f, h * 0.685f, text)
        canvas.drawText("Legacy graphics imported from your Java ME repo", w / 2f, h * 0.77f, subtitlePaint)
    }

    private fun drawTouchZones(canvas: Canvas, w: Float, h: Float) {
        val pad = RectF(24f, h - 150f, 174f, h - 20f)
        canvas.drawOval(pad, moveHintPaint)
        canvas.drawOval(pad, hudBorder)
        canvas.drawText("MOVE", 62f, h - 80f, smallText)
        canvas.drawText("◀", 38f, h - 78f, text)
        canvas.drawText("▶", 132f, h - 78f, text)

        val jump = RectF(w - 320f, h - 150f, w - 210f, h - 40f)
        val weapon = RectF(w - 190f, h - 150f, w - 80f, h - 40f)
        canvas.drawOval(jump, selectBar)
        canvas.drawOval(jump, hudBorder)
        canvas.drawOval(weapon, hud)
        canvas.drawOval(weapon, hudBorder)
        canvas.drawText("JUMP", jump.left + 14f, jump.centerY() + 8f, smallText)
        canvas.drawText("ARM", weapon.left + 22f, weapon.centerY() + 8f, smallText)

        val aim = RectF(w - 280f, h - 340f, w - 24f, h - 170f)
        canvas.drawRoundRect(aim, 18f, 18f, moveHintPaint)
        canvas.drawRoundRect(aim, 18f, 18f, hudBorder)
        canvas.drawText("DRAG TO AIM", aim.left + 26f, aim.centerY() + 8f, smallText)
    }

    private fun drawWorms(canvas: Canvas, w: Float) {
        worms.forEachIndexed { index, worm ->
            if (worm.hp <= 0) return@forEachIndexed
            val cx = worm.x * w
            val cy = wormY(index, w)
            if (legacyWorm != null) {
                val size = 56f
                val dest = RectF(cx - size / 2, cy - size / 2, cx + size / 2, cy + size / 2)
                canvas.drawBitmap(legacyWorm, null, dest, null)
                if (index == 1) {
                    val overlay = Paint().apply { color = Color.argb(90, 80, 255, 120) }
                    canvas.drawOval(dest, overlay)
                }
            } else {
                val paint = if (index == 0) wormA else wormB
                canvas.drawCircle(cx, cy, 22f, paint)
                canvas.drawRect(cx - 16f, cy + 14f, cx + 16f, cy + 38f, paint)
            }
            canvas.drawCircle(cx, cy, 22f, wormOutline)
            canvas.drawText("${worm.hp}", cx - 18f, cy - 34f, text)
            if (index == activePlayer && winner == null) canvas.drawText("◀", cx - 40f, cy - 10f, text)
        }
    }

    private fun drawAimPreview(canvas: Canvas, w: Float, h: Float) {
        if (gameMode == GameMode.TITLE || projectile != null || explosion != null || winner != null) return
        if (gameMode == GameMode.PVE && activePlayer == 1) return
        val direction = if (activePlayer == 0) 1f else -1f
        val weapon = weapons[selectedWeapon]
        var x = worms[activePlayer].x * w
        var y = wormY(activePlayer, w) - 16f
        val radians = Math.toRadians(angleDeg.toDouble())
        var vx = (cos(radians) * (280f + 520f * power) * weapon.speedFactor * direction).toFloat()
        var vy = (-sin(radians) * (280f + 520f * power) * weapon.speedFactor).toFloat()
        repeat(18) {
            x += vx * 0.08f
            y += vy * 0.08f
            vx += wind * 0.08f
            vy += 640f * 0.08f
            if (x !in 0f..w || y !in 0f..h || y >= terrainHeightAt(x)) return
            canvas.drawCircle(x, y, 3f, trajectoryPaint)
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
        val box = RectF(14f, 14f, w - 14f, 126f)
        canvas.drawRoundRect(box, 14f, 14f, hud)
        canvas.drawRoundRect(box, 14f, 14f, hudBorder)
        val turn = if (activePlayer == 0) "P1" else if (gameMode == GameMode.PVE) "AI" else "P2"
        val leftMs = max(0, turnDurationMs - (System.currentTimeMillis() - turnStartMs))
        val seconds = leftMs / 1000 + 1
        legacyWorm?.let {
            val dest = RectF(20f, 24f, 56f, 60f)
            canvas.drawBitmap(it, null, dest, null)
        }
        canvas.drawText("$turn", 64f, 46f, smallText)
        canvas.drawText("T:${seconds}s", 120f, 46f, smallText)
        canvas.drawText("A:${angleDeg.toInt()}°", 200f, 46f, smallText)
        canvas.drawText("P:${(power * 100).toInt()}%", 300f, 46f, smallText)
        canvas.drawText("J:${worms[activePlayer].jumpsLeft}", 420f, 46f, smallText)
        canvas.drawText("M:${worms[activePlayer].movePoints}", 500f, 46f, smallText)
        drawHealthBars(canvas, w)
        drawCompactWindMeter(canvas, w)
        drawWeaponStrip(canvas, w)
        val footer = winner?.let {
            when {
                gameMode == GameMode.PVE && it == 1 -> "AI wins — tap to restart"
                it == 0 -> "P1 wins — tap to restart"
                else -> "P2 wins — tap to restart"
            }
        } ?: if (gameMode == GameMode.PVE && activePlayer == 1) {
            "AI aiming..."
        } else {
            "Top: power/weapon  Mid: aim/fire  Bottom: move/jump"
        }
        canvas.drawText(footer, 20f, h - 40f, smallText)
    }

    private fun drawHealthBars(canvas: Canvas, w: Float) {
        drawSingleHealthBar(canvas, 20f, 58f, w * 0.22f, worms[0].hp, "P1")
        val p2Label = if (gameMode == GameMode.PVE) "AI" else "P2"
        drawSingleHealthBar(canvas, w * 0.26f, 58f, w * 0.22f, worms[1].hp, p2Label)
    }

    private fun drawSingleHealthBar(canvas: Canvas, x: Float, y: Float, width: Float, hp: Int, label: String) {
        val outer = RectF(x, y, x + width, y + 18f)
        val fillWidth = (width - 4f) * (hp.coerceIn(0, 100) / 100f)
        canvas.drawRoundRect(outer, 8f, 8f, hpBarBg)
        canvas.drawRoundRect(outer, 8f, 8f, hudBorder)
        canvas.drawRoundRect(RectF(x + 2f, y + 2f, x + 2f + fillWidth, y + 16f), 6f, 6f, hpBarFill)
        canvas.drawText("$label $hp", x, y - 6f, smallText)
    }

    private fun drawWeaponStrip(canvas: Canvas, w: Float) {
        val startX = w * 0.52f
        val y = 58f
        val itemW = min(112f, (w - startX - 24f) / weapons.size.coerceAtLeast(1))
        weapons.forEachIndexed { index, weapon ->
            val left = startX + index * (itemW + 12f)
            val rect = RectF(left, y, left + itemW, y + 28f)
            val bg = if (index == selectedWeapon) selectBar else hud
            canvas.drawRoundRect(rect, 10f, 10f, bg)
            if (index == selectedWeapon) {
                iconTilePaint.shader?.let { canvas.drawRoundRect(rect, 10f, 10f, iconTilePaint) }
            }
            canvas.drawRoundRect(rect, 10f, 10f, hudBorder)

            val iconRect = RectF(left + 4f, y + 4f, left + 24f, y + 24f)
            when (index) {
                0 -> legacyTileA?.let { canvas.drawBitmap(it, null, iconRect, null) } ?: canvas.drawOval(iconRect, projectilePaint)
                1 -> legacyTileB?.let { canvas.drawBitmap(it, null, iconRect, null) } ?: canvas.drawRect(iconRect, projectilePaint)
                else -> legacyTileC?.let { canvas.drawBitmap(it, null, iconRect, null) } ?: canvas.drawCircle(iconRect.centerX(), iconRect.centerY(), 10f, projectilePaint)
            }
            canvas.drawRoundRect(iconRect, 6f, 6f, hudBorder)
            canvas.drawText(weapon.name, left + 28f, y + 19f, smallText)
        }
    }

    private fun drawCompactWindMeter(canvas: Canvas, w: Float) {
        val x = w * 0.26f
        val y = 92f
        val width = w * 0.20f
        val outer = RectF(x, y, x + width, y + 14f)
        canvas.drawRoundRect(outer, 7f, 7f, windBarBg)
        canvas.drawRoundRect(outer, 7f, 7f, hudBorder)
        val normalized = ((wind + 40f) / 80f).coerceIn(0f, 1f)
        canvas.drawRoundRect(RectF(x + 2f, y + 2f, x + 2f + (width - 4f) * normalized, y + 12f), 5f, 5f, windBarFill)
        canvas.drawText("W ${"%+.1f".format(wind / 14f)}", x + width + 12f, y + 12f, smallText)
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

    private fun updateWormPhysics(dt: Float, w: Float) {
        worms.forEachIndexed { _, worm ->
            if (worm.hp <= 0) return@forEachIndexed
            val targetY = terrainHeightAt(worm.x * w) - 26f
            if (worm.yOffset == 0f) {
                worm.yOffset = targetY
                worm.fallDistance = 0f
                return@forEachIndexed
            }
            if (worm.yOffset < targetY - 1f) {
                val fall = min(targetY - worm.yOffset, 240f * dt)
                worm.yOffset += fall
                worm.fallDistance += fall
                if (worm.yOffset >= targetY - 1f) {
                    worm.yOffset = targetY
                    if (worm.fallDistance > 36f) {
                        worm.hp = (worm.hp - ((worm.fallDistance - 36f) / 6f).toInt()).coerceAtLeast(0)
                    }
                    worm.fallDistance = 0f
                }
            } else {
                worm.yOffset = targetY
                worm.fallDistance = 0f
            }
        }
    }

    private fun advanceTurn() {
        activePlayer = 1 - activePlayer
        angleDeg = 45f
        power = 0.6f
        wind = randomWind()
        turnStartMs = System.currentTimeMillis()
        worms[activePlayer].jumpsLeft = 1
        worms[activePlayer].movePoints = 100
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
        if (Random.nextFloat() < 0.20f && worms[1].jumpsLeft > 0) jumpWorm()
        chooseBestAiShot(w)
        fire()
        aiShotAtMs = 0L
    }

    private fun chooseBestAiShot(w: Float) {
        val startX = worms[1].x * w
        val startY = wormY(1, w) - 16f
        val targetX = worms[0].x * w
        val targetY = wormY(0, w)
        var bestScore = Float.MAX_VALUE
        var bestWeapon = 0
        var bestAngle = 45f
        var bestPower = 0.6f
        weapons.forEachIndexed { weaponIndex, weapon ->
            for (angle in 20..75 step 5) {
                var powerLevel = 35
                while (powerLevel <= 100) {
                    val score = simulateShotScore(startX, startY, targetX, targetY, angle.toFloat(), powerLevel / 100f, weapon, w)
                    if (score < bestScore) {
                        bestScore = score
                        bestWeapon = weaponIndex
                        bestAngle = angle.toFloat()
                        bestPower = powerLevel / 100f
                    }
                    powerLevel += 5
                }
            }
        }
        selectedWeapon = bestWeapon
        angleDeg = bestAngle
        power = bestPower
    }

    private fun simulateShotScore(startX: Float, startY: Float, targetX: Float, targetY: Float, angle: Float, power: Float, weapon: Weapon, w: Float): Float {
        var x = startX
        var y = startY
        val radians = Math.toRadians(angle.toDouble())
        var vx = (-cos(radians) * (280f + 520f * power) * weapon.speedFactor).toFloat()
        var vy = (-sin(radians) * (280f + 520f * power) * weapon.speedFactor).toFloat()
        repeat(50) {
            x += vx * 0.08f
            y += vy * 0.08f
            vx += wind * 0.08f
            vy += 640f * 0.08f
            if (x !in 0f..w || y > height || y < -40f || y >= terrainHeightAt(x)) {
                return sqrt((x - targetX) * (x - targetX) + (y - targetY) * (y - targetY))
            }
            val d = sqrt((x - targetX) * (x - targetX) + (y - targetY) * (y - targetY))
            if (d < weapon.radius) return d * 0.5f
        }
        return sqrt((x - targetX) * (x - targetX) + (y - targetY) * (y - targetY))
    }

    private fun snapWormsToTerrain(w: Float) {
        worms.forEachIndexed { _, worm ->
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
        val w = width.toFloat().coerceAtLeast(1f)
        val h = height.toFloat().coerceAtLeast(1f)
        val xRatio = event.x / w
        val yRatio = event.y / h
        if (gameMode == GameMode.TITLE) {
            if (event.action == MotionEvent.ACTION_DOWN) startGame(if (yRatio < 0.5f) GameMode.PVE else GameMode.PVP)
            return true
        }
        if (winner != null) {
            if (event.action == MotionEvent.ACTION_DOWN) {
                gameMode = GameMode.TITLE
                restart()
            }
            return true
        }
        if (projectile != null || explosion != null) return true
        if (gameMode == GameMode.PVE && activePlayer == 1) return true

        val movePad = RectF(24f, h - 150f, 174f, h - 20f)
        val jumpBtn = RectF(w - 320f, h - 150f, w - 210f, h - 40f)
        val weaponBtn = RectF(w - 190f, h - 150f, w - 80f, h - 40f)
        val aimZone = RectF(w - 280f, h - 340f, w - 24f, h - 170f)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                when {
                    movePad.contains(event.x, event.y) -> {
                        val center = movePad.centerX()
                        moveInput = ((event.x - center) / (movePad.width() / 2f)).coerceIn(-1f, 1f)
                    }
                    jumpBtn.contains(event.x, event.y) && event.actionMasked == MotionEvent.ACTION_DOWN -> jumpWorm()
                    weaponBtn.contains(event.x, event.y) && event.actionMasked == MotionEvent.ACTION_DOWN -> cycleWeapon()
                    aimZone.contains(event.x, event.y) -> {
                        aimActive = true
                        updateAimFromTouch(event.x, event.y, w)
                        if (!chargingShot && event.actionMasked == MotionEvent.ACTION_DOWN) {
                            chargingShot = true
                            chargeStartMs = System.currentTimeMillis()
                        }
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (chargingShot && aimActive) {
                    val held = (System.currentTimeMillis() - chargeStartMs).coerceAtMost(1600L)
                    power = (0.2f + held / 1600f * 0.8f).coerceIn(0.2f, 1f)
                    fire()
                }
                moveInput = 0f
                aimActive = false
                chargingShot = false
            }
        }
        invalidate()
        return true
    }

    private fun updateTouchControls(dt: Float, w: Float) {
        if (moveInput == 0f || projectile != null || explosion != null) return
        val delta = moveInput * dt * 0.09f
        if (delta != 0f) moveWorm(delta)
    }

    private fun updateAimFromTouch(x: Float, y: Float, w: Float) {
        val wormX = worms[activePlayer].x * w
        val wormY = wormY(activePlayer, w) - 16f
        val dx = x - wormX
        val dy = wormY - y
        val facingDx = if (activePlayer == 0) dx.coerceAtLeast(1f) else (-dx).coerceAtLeast(1f)
        angleDeg = Math.toDegrees(atan2(dy.toDouble(), facingDx.toDouble())).toFloat().coerceIn(5f, 85f)
        if (chargingShot) {
            val distance = sqrt(dx * dx + (wormY - y) * (wormY - y))
            power = (distance / (w * 0.35f)).coerceIn(0.15f, 1f)
        }
    }

    private fun cycleWeapon() {
        selectedWeapon = (selectedWeapon + 1) % weapons.size
    }

    private fun moveWorm(delta: Float) {
        val worm = worms[activePlayer]
        if (worm.movePoints <= 0) return
        val currentW = width.toFloat().coerceAtLeast(1f)
        val newX = (worm.x + delta).coerceIn(0.05f, 0.95f)
        val currentY = terrainHeightAt(worm.x * currentW)
        val nextY = terrainHeightAt(newX * currentW)
        if (abs(nextY - currentY) > 22f) return
        worm.x = newX
        worm.movePoints = (worm.movePoints - (abs(delta) * 2200).toInt()).coerceAtLeast(0)
        snapWormsToTerrain(currentW)
    }

    private fun jumpWorm() {
        val worm = worms[activePlayer]
        if (worm.jumpsLeft <= 0) return
        val direction = if (activePlayer == 0) 1f else -1f
        worm.x = (worm.x + 0.05f * direction).coerceIn(0.05f, 0.95f)
        worm.yOffset -= 34f
        worm.jumpsLeft -= 1
        worm.movePoints = (worm.movePoints - 30).coerceAtLeast(0)
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
        worms[0] = Worm(0.18f, hp = 100, movePoints = 100)
        worms[1] = Worm(0.82f, hp = 100, movePoints = 100)
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

    private fun loadBitmap(path: String): Bitmap? = runCatching {
        context.assets.open(path).use { BitmapFactory.decodeStream(it) }
    }.getOrNull()
}
