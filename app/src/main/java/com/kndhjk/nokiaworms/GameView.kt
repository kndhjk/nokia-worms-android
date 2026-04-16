package com.kndhjk.nokiaworms

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Bitmap.Config
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
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

    private val skyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(126, 196, 255) }
    private val hillBackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(130, 167, 101) }
    private val hillFrontPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(95, 140, 72) }
    private val terrainPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(92, 64, 38) }
    private val grassPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(99, 173, 71) }
    private val titleText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textSize = 66f
        textAlign = Paint.Align.CENTER
    }
    private val bodyText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textSize = 28f
    }
    private val smallText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textSize = 22f
    }
    private val panelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(225, 255, 255, 255) }
    private val panelBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val activePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(125, 255, 220, 100) }
    private val projectilePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK }
    private val blastPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(160, 255, 210, 80) }
    private val aimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(120, 0, 0, 0) }
    private val hpBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(65, 65, 65) }
    private val hpGoodPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(101, 220, 111) }
    private val hpBadPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(255, 110, 110) }
    private val windPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(80, 125, 230)
        strokeWidth = 5f
    }
    private val cratePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(181, 129, 60) }
    private val crateBandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(83, 55, 28) }
    private val wormTintA = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(80, 255, 150, 70) }
    private val wormTintB = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(80, 80, 225, 110) }

    private enum class GameMode { TITLE, PVP, PVE }
    private enum class CrateType { HEALTH, WEAPON }

    private data class Weapon(
        val name: String,
        val speed: Float,
        val radius: Float,
        val damage: Int,
        val gravityScale: Float,
        val ammoLabel: String
    )

    private data class Worm(
        val team: Int,
        val name: String,
        var x: Float,
        var y: Float = 0f,
        var hp: Int = 100,
        var alive: Boolean = true,
        var moveLeft: Float = 1f,
        var jumpsLeft: Int = 1,
        var fallDistance: Float = 0f
    )

    private data class Projectile(
        var x: Float,
        var y: Float,
        var vx: Float,
        var vy: Float,
        val weapon: Weapon,
        val ownerTeam: Int
    )

    private data class Explosion(var x: Float, var y: Float, var radius: Float, var age: Float = 0f)
    private data class SupplyCrate(var x: Float, var y: Float, val type: CrateType, var active: Boolean = true)

    private val weapons = listOf(
        Weapon("Bazooka", speed = 430f, radius = 34f, damage = 48, gravityScale = 1f, ammoLabel = "ATK"),
        Weapon("Grenade", speed = 360f, radius = 42f, damage = 60, gravityScale = 1.18f, ammoLabel = "ARC"),
        Weapon("Missile", speed = 520f, radius = 28f, damage = 36, gravityScale = 0.9f, ammoLabel = "SPD"),
        Weapon("Mortar", speed = 300f, radius = 54f, damage = 74, gravityScale = 1.32f, ammoLabel = "LOB"),
        Weapon("Shotgun", speed = 610f, radius = 18f, damage = 24, gravityScale = 0.76f, ammoLabel = "HIT"),
        Weapon("Dynamite", speed = 240f, radius = 62f, damage = 86, gravityScale = 1.45f, ammoLabel = "BIG")
    )

    private var worms = mutableListOf<Worm>()
    private var activeWormIndex = 0
    private var selectedWeapon = 0
    private var angleDeg = 45f
    private var power = 0.62f
    private var wind = 0f
    private var mode = GameMode.TITLE
    private var winnerTeam: Int? = null
    private var lastFrameNanos = System.nanoTime()
    private var turnStartedMs = System.currentTimeMillis()
    private var projectile: Projectile? = null
    private var explosion: Explosion? = null
    private var crates = mutableListOf<SupplyCrate>()
    private var aiFireAt = 0L
    private var terrainBitmap: Bitmap? = null
    private var terrainCanvas: Canvas? = null
    private var terrainHeight = IntArray(0)
    private var terrainDirty = true
    private var moveInput = 0f
    private var aimActive = false
    private var charging = false
    private var chargeStartedMs = 0L

    private val terrainTop get() = (height * 0.26f).roundToInt()
    private val turnDurationMs = 25_000L
    private val wormRadius get() = max(16f, width * 0.025f)
    private val crateSize get() = wormRadius * 1.35f

    init {
        setupMatch()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat().coerceAtLeast(1f)
        val h = height.toFloat().coerceAtLeast(1f)
        ensureTerrain()
        updateFrame()
        drawBackground(canvas, w, h)
        drawTerrain(canvas)
        if (mode == GameMode.TITLE) {
            drawTitle(canvas, w, h)
        } else {
            drawCrates(canvas)
            drawWorms(canvas)
            drawAimPreview(canvas)
            drawProjectile(canvas)
            drawExplosion(canvas)
            drawHud(canvas, w, h)
            drawControls(canvas, w, h)
        }
        if (mode == GameMode.TITLE || projectile != null || explosion != null || winnerTeam != null || (mode == GameMode.PVE && activeTeam() == 1)) {
            invalidate()
        }
    }

    private fun setupMatch() {
        worms = mutableListOf(
            Worm(0, "A-1", 0.16f),
            Worm(0, "A-2", 0.29f),
            Worm(0, "A-3", 0.39f),
            Worm(1, "B-1", 0.62f),
            Worm(1, "B-2", 0.74f),
            Worm(1, "B-3", 0.86f)
        )
        activeWormIndex = 0
        selectedWeapon = 0
        angleDeg = 45f
        power = 0.62f
        wind = randomWind()
        winnerTeam = null
        projectile = null
        explosion = null
        crates.clear()
        lastFrameNanos = System.nanoTime()
        turnStartedMs = System.currentTimeMillis()
        aiFireAt = 0L
        terrainDirty = true
        terrainBitmap = null
        terrainCanvas = null
        terrainHeight = IntArray(0)
    }

    private fun ensureTerrain() {
        if (width <= 0 || height <= 0) return
        if (!terrainDirty && terrainBitmap?.width == width && terrainBitmap?.height == height) return
        if (terrainBitmap == null || terrainBitmap?.width != width || terrainBitmap?.height != height) {
            terrainBitmap = Bitmap.createBitmap(width, height, Config.ARGB_8888)
            terrainCanvas = Canvas(terrainBitmap!!)
        }
        terrainBitmap?.eraseColor(Color.TRANSPARENT)
        val canvas = terrainCanvas ?: return
        val terrainPath = Path().apply {
            moveTo(0f, height.toFloat())
            lineTo(0f, sampleTerrainY(0f))
            for (x in 1 until width) {
                lineTo(x.toFloat(), sampleTerrainY(x.toFloat()))
            }
            lineTo(width.toFloat(), height.toFloat())
            close()
        }
        canvas.drawPath(terrainPath, terrainPaint)
        val grassPath = Path().apply {
            moveTo(0f, sampleTerrainY(0f) - 4f)
            for (x in 2 until width step 2) {
                lineTo(x.toFloat(), sampleTerrainY(x.toFloat()) - 4f)
            }
        }
        canvas.drawPath(grassPath, grassPaint)
        rebuildTerrainHeight()
        snapAllWormsToGround()
        maybeSpawnCrates()
        terrainDirty = false
    }

    private fun sampleTerrainY(x: Float): Float {
        val xf = x / width.toFloat().coerceAtLeast(1f)
        val base = height * 0.68f
        val big = sin(xf * PI * 1.4).toFloat() * (height * 0.08f)
        val medium = sin(xf * PI * 4.9).toFloat() * (height * 0.03f)
        val detail = sin(xf * PI * 10.4).toFloat() * (height * 0.012f)
        return (base + big + medium + detail).coerceIn(height * 0.42f, height * 0.84f)
    }

    private fun rebuildTerrainHeight() {
        val bmp = terrainBitmap ?: return
        if (terrainHeight.size != bmp.width) terrainHeight = IntArray(bmp.width)
        for (x in 0 until bmp.width) {
            var found = bmp.height - 1
            for (y in terrainTop until bmp.height) {
                if (Color.alpha(bmp.getPixel(x, y)) > 0) {
                    found = y
                    break
                }
            }
            terrainHeight[x] = found
        }
    }

    private fun drawBackground(canvas: Canvas, w: Float, h: Float) {
        canvas.drawRect(0f, 0f, w, h, skyPaint)
        val back = Path().apply {
            moveTo(0f, h * 0.70f)
            cubicTo(w * 0.10f, h * 0.36f, w * 0.32f, h * 0.56f, w * 0.52f, h * 0.40f)
            cubicTo(w * 0.64f, h * 0.28f, w * 0.82f, h * 0.56f, w, h * 0.42f)
            lineTo(w, h)
            lineTo(0f, h)
            close()
        }
        val front = Path().apply {
            moveTo(0f, h * 0.78f)
            cubicTo(w * 0.18f, h * 0.50f, w * 0.38f, h * 0.66f, w * 0.55f, h * 0.50f)
            cubicTo(w * 0.70f, h * 0.34f, w * 0.88f, h * 0.58f, w, h * 0.50f)
            lineTo(w, h)
            lineTo(0f, h)
            close()
        }
        canvas.drawPath(back, hillBackPaint)
        canvas.drawPath(front, hillFrontPaint)
    }

    private fun drawTerrain(canvas: Canvas) {
        terrainBitmap?.let { canvas.drawBitmap(it, 0f, 0f, null) }
    }

    private fun drawTitle(canvas: Canvas, w: Float, h: Float) {
        val panel = RectF(w * 0.12f, h * 0.16f, w * 0.88f, h * 0.82f)
        canvas.drawRoundRect(panel, 30f, 30f, panelPaint)
        canvas.drawRoundRect(panel, 30f, 30f, panelBorder)
        legacyLogo?.let {
            canvas.drawBitmap(it, null, RectF(w * 0.20f, h * 0.20f, w * 0.80f, h * 0.46f), null)
        } ?: canvas.drawText("Nokia Worms", w / 2f, h * 0.32f, titleText)
        legacyTitle?.let {
            canvas.drawBitmap(it, null, RectF(w * 0.70f, h * 0.08f, w * 0.95f, h * 0.36f), null)
        }
        val pve = RectF(w * 0.20f, h * 0.54f, w * 0.80f, h * 0.62f)
        val pvp = RectF(w * 0.20f, h * 0.65f, w * 0.80f, h * 0.73f)
        canvas.drawRoundRect(pve, 16f, 16f, activePaint)
        canvas.drawRoundRect(pvp, 16f, 16f, panelPaint)
        canvas.drawRoundRect(pve, 16f, 16f, panelBorder)
        canvas.drawRoundRect(pvp, 16f, 16f, panelBorder)
        canvas.drawText("1 Player vs AI", w / 2f, h * 0.595f, bodyText)
        canvas.drawText("2 Players", w / 2f, h * 0.705f, bodyText)
        canvas.drawText("新版目标：多虫队伍 + 像素地形 + 更多武器", w / 2f, h * 0.79f, smallText)
    }

    private fun drawWorms(canvas: Canvas) {
        val activeIndex = activeWormIndex
        worms.forEachIndexed { index, worm ->
            if (!worm.alive) return@forEachIndexed
            val cx = worm.x * width
            val cy = worm.y
            val size = wormRadius * 2.4f
            val dst = RectF(cx - size / 2f, cy - size / 2f, cx + size / 2f, cy + size / 2f)
            if (legacyWorm != null) {
                canvas.drawBitmap(legacyWorm, null, dst, null)
                canvas.drawOval(dst, if (worm.team == 0) wormTintA else wormTintB)
            } else {
                canvas.drawCircle(cx, cy, wormRadius, if (worm.team == 0) wormTintA else wormTintB)
            }
            if (index == activeIndex && winnerTeam == null) {
                val marker = RectF(dst.left - 10f, dst.top - 16f, dst.right + 10f, dst.top + 8f)
                canvas.drawRoundRect(marker, 12f, 12f, activePaint)
                canvas.drawRoundRect(marker, 12f, 12f, panelBorder)
            }
            drawHpMini(canvas, worm, cx, cy)
        }
    }

    private fun drawHpMini(canvas: Canvas, worm: Worm, cx: Float, cy: Float) {
        val width = wormRadius * 2.6f
        val top = cy - wormRadius - 18f
        val left = cx - width / 2f
        val outer = RectF(left, top, left + width, top + 10f)
        val fill = (width - 4f) * (worm.hp.coerceIn(0, 100) / 100f)
        canvas.drawRoundRect(outer, 6f, 6f, hpBgPaint)
        canvas.drawRoundRect(outer, 6f, 6f, panelBorder)
        val fillPaint = if (worm.team == 0) hpGoodPaint else hpBadPaint
        canvas.drawRoundRect(RectF(left + 2f, top + 2f, left + 2f + fill, top + 8f), 5f, 5f, fillPaint)
        canvas.drawText("${worm.name} ${worm.hp}", left, top - 4f, smallText)
    }

    private fun drawCrates(canvas: Canvas) {
        crates.filter { it.active }.forEach { crate ->
            val rect = RectF(crate.x - crateSize / 2f, crate.y - crateSize / 2f, crate.x + crateSize / 2f, crate.y + crateSize / 2f)
            canvas.drawRoundRect(rect, 8f, 8f, cratePaint)
            canvas.drawRoundRect(rect, 8f, 8f, panelBorder)
            canvas.drawLine(rect.left + 6f, rect.centerY(), rect.right - 6f, rect.centerY(), crateBandPaint)
            canvas.drawLine(rect.centerX(), rect.top + 6f, rect.centerX(), rect.bottom - 6f, crateBandPaint)
            val label = if (crate.type == CrateType.HEALTH) "+" else "?"
            canvas.drawText(label, rect.centerX() - 6f, rect.centerY() + 8f, bodyText)
        }
    }

    private fun drawAimPreview(canvas: Canvas) {
        if (winnerTeam != null || projectile != null || explosion != null || mode == GameMode.TITLE) return
        if (mode == GameMode.PVE && activeTeam() == 1) return
        val worm = worms[activeWormIndex]
        if (!worm.alive) return
        val direction = if (worm.team == 0) 1f else -1f
        val weapon = weapons[selectedWeapon]
        var x = worm.x * width
        var y = worm.y - wormRadius
        val radians = Math.toRadians(angleDeg.toDouble())
        var vx = (cos(radians) * weapon.speed * power * direction).toFloat()
        var vy = (-sin(radians) * weapon.speed * power).toFloat()
        repeat(22) {
            x += vx * 0.08f
            y += vy * 0.08f
            vx += wind * 0.08f
            vy += 600f * weapon.gravityScale * 0.08f
            if (x !in 0f..width.toFloat() || y !in 0f..height.toFloat() || isTerrainAt(x, y)) return
            canvas.drawCircle(x, y, 3f, aimPaint)
        }
    }

    private fun drawProjectile(canvas: Canvas) {
        projectile?.let { canvas.drawCircle(it.x, it.y, wormRadius * 0.35f, projectilePaint) }
    }

    private fun drawExplosion(canvas: Canvas) {
        explosion?.let {
            val alpha = ((1f - it.age / 0.40f) * 180f).roundToInt().coerceIn(0, 180)
            blastPaint.alpha = alpha
            canvas.drawCircle(it.x, it.y, it.radius * (0.55f + it.age * 1.5f), blastPaint)
        }
    }

    private fun drawHud(canvas: Canvas, w: Float, h: Float) {
        val panel = RectF(12f, 12f, w - 12f, 120f)
        canvas.drawRoundRect(panel, 16f, 16f, panelPaint)
        canvas.drawRoundRect(panel, 16f, 16f, panelBorder)
        val active = worms[activeWormIndex]
        val turnLeft = (turnDurationMs - (System.currentTimeMillis() - turnStartedMs)).coerceAtLeast(0L)
        val seconds = turnLeft / 1000 + 1
        canvas.drawText("Turn: ${if (active.team == 0) "Team A" else if (mode == GameMode.PVE) "AI" else "Team B"}", 24f, 40f, smallText)
        canvas.drawText("Worm: ${active.name}", 24f, 68f, smallText)
        canvas.drawText("Timer: ${seconds}s", 24f, 96f, smallText)
        canvas.drawText("Weapon: ${weapons[selectedWeapon].name}", w * 0.28f, 40f, smallText)
        canvas.drawText("Angle: ${angleDeg.roundToInt()}°", w * 0.28f, 68f, smallText)
        canvas.drawText("Power: ${(power * 100).roundToInt()}%", w * 0.28f, 96f, smallText)
        canvas.drawText("Move: ${(active.moveLeft * 100).roundToInt()}%", w * 0.54f, 40f, smallText)
        canvas.drawText("Jump: ${active.jumpsLeft}", w * 0.54f, 68f, smallText)
        canvas.drawText("Alive: ${worms.count { it.team == 0 && it.alive }} / ${worms.count { it.team == 1 && it.alive }}", w * 0.54f, 96f, smallText)
        drawWind(canvas, w * 0.84f, 62f)
        winnerTeam?.let {
            val msg = if (mode == GameMode.PVE && it == 1) "AI wins, tap to restart" else "Team ${if (it == 0) "A" else "B"} wins, tap to restart"
            canvas.drawText(msg, 24f, h - 28f, bodyText)
        }
    }

    private fun drawWind(canvas: Canvas, x: Float, y: Float) {
        val len = abs(wind) * 0.5f + 20f
        val dir = if (wind >= 0f) 1f else -1f
        canvas.drawLine(x, y, x + len * dir, y, windPaint)
        canvas.drawLine(x + len * dir, y, x + (len - 10f) * dir, y - 6f, windPaint)
        canvas.drawLine(x + len * dir, y, x + (len - 10f) * dir, y + 6f, windPaint)
        canvas.drawText("Wind ${"%+.1f".format(wind / 18f)}", x - 12f, y - 10f, smallText)
    }

    private fun drawControls(canvas: Canvas, w: Float, h: Float) {
        if (winnerTeam != null) return
        val movePad = movePadRect(w, h)
        val jump = jumpRect(w, h)
        val weapon = weaponRect(w, h)
        val aim = aimRect(w, h)
        val fire = fireRect(w, h)
        canvas.drawOval(movePad, panelPaint)
        canvas.drawOval(movePad, panelBorder)
        canvas.drawText("MOVE", movePad.centerX() - 30f, movePad.centerY() + 8f, smallText)
        canvas.drawRoundRect(jump, 14f, 14f, activePaint)
        canvas.drawRoundRect(jump, 14f, 14f, panelBorder)
        canvas.drawRoundRect(weapon, 14f, 14f, panelPaint)
        canvas.drawRoundRect(weapon, 14f, 14f, panelBorder)
        canvas.drawRoundRect(fire, 14f, 14f, activePaint)
        canvas.drawRoundRect(fire, 14f, 14f, panelBorder)
        canvas.drawRoundRect(aim, 18f, 18f, panelPaint)
        canvas.drawRoundRect(aim, 18f, 18f, panelBorder)
        canvas.drawText("JUMP", jump.left + 16f, jump.centerY() + 8f, smallText)
        canvas.drawText("WEAPON", weapon.left + 10f, weapon.centerY() + 8f, smallText)
        canvas.drawText("FIRE", fire.left + 26f, fire.centerY() + 8f, bodyText)
        canvas.drawText("DRAG TO AIM", aim.left + 18f, aim.centerY() + 8f, smallText)
    }

    private fun updateFrame() {
        val now = System.nanoTime()
        val dt = ((now - lastFrameNanos) / 1_000_000_000f).coerceAtMost(0.033f)
        lastFrameNanos = now
        if (mode == GameMode.TITLE) return
        updateMovement(dt)
        updatePhysics(dt)
        projectile?.let { updateProjectile(it, dt) }
        explosion?.let { updateExplosion(it, dt) }
        if (winnerTeam == null && projectile == null && explosion == null) {
            if (System.currentTimeMillis() - turnStartedMs >= turnDurationMs) {
                nextTurn()
            }
            maybeRunAi()
            collectCrates()
        }
    }

    private fun updateMovement(dt: Float) {
        if (moveInput == 0f || projectile != null || explosion != null || winnerTeam != null) return
        val worm = worms[activeWormIndex]
        if (!worm.alive || worm.moveLeft <= 0f) return
        val delta = moveInput * dt * 0.12f
        val nextX = (worm.x + delta).coerceIn(0.04f, 0.96f)
        val currentGround = groundYAtPx(worm.x * width)
        val nextGround = groundYAtPx(nextX * width)
        if (abs(nextGround - currentGround) > wormRadius * 1.15f) return
        worm.x = nextX
        worm.moveLeft = (worm.moveLeft - abs(delta) * 6f).coerceAtLeast(0f)
        worm.y = groundYAtPx(worm.x * width) - wormRadius
    }

    private fun updatePhysics(dt: Float) {
        worms.filter { it.alive }.forEach { worm ->
            val targetY = groundYAtPx(worm.x * width) - wormRadius
            if (worm.y < targetY - 1f) {
                val fall = min(targetY - worm.y, dt * 250f)
                worm.y += fall
                worm.fallDistance += fall
                if (worm.y >= targetY - 1f) {
                    worm.y = targetY
                    if (worm.fallDistance > 36f) {
                        worm.hp = (worm.hp - ((worm.fallDistance - 36f) / 5f).roundToInt()).coerceAtLeast(0)
                        if (worm.hp <= 0) worm.alive = false
                    }
                    worm.fallDistance = 0f
                }
            } else {
                worm.y = targetY
                worm.fallDistance = 0f
            }
        }
        evaluateWinner()
    }

    private fun updateProjectile(p: Projectile, dt: Float) {
        p.x += p.vx * dt
        p.y += p.vy * dt
        p.vx += wind * dt
        p.vy += 600f * p.weapon.gravityScale * dt
        if (p.x < 0f || p.x >= width || p.y < -40f || p.y >= height) {
            explode(p.x.coerceIn(0f, width.toFloat()), p.y.coerceIn(0f, height.toFloat()), p.weapon)
            return
        }
        if (isTerrainAt(p.x, p.y)) {
            explode(p.x, p.y, p.weapon)
            return
        }
        val hit = worms.firstOrNull { it.alive && hypot(it.x * width - p.x, it.y - p.y) < wormRadius * 1.2f }
        if (hit != null) explode(p.x, p.y, p.weapon)
    }

    private fun updateExplosion(exp: Explosion, dt: Float) {
        exp.age += dt
        if (exp.age >= 0.40f) {
            explosion = null
            evaluateWinner()
            if (winnerTeam == null) nextTurn()
        }
    }

    private fun explode(x: Float, y: Float, weapon: Weapon) {
        projectile = null
        explosion = Explosion(x, y, weapon.radius)
        carveTerrain(x, y, weapon.radius)
        damageWorms(x, y, weapon.radius, weapon.damage)
        terrainDirty = false
        rebuildTerrainHeight()
        snapAllWormsToGround()
    }

    private fun carveTerrain(cx: Float, cy: Float, radius: Float) {
        val bmp = terrainBitmap ?: return
        val left = max(0, (cx - radius).roundToInt())
        val right = min(bmp.width - 1, (cx + radius).roundToInt())
        val top = max(terrainTop, (cy - radius).roundToInt())
        val bottom = min(bmp.height - 1, (cy + radius).roundToInt())
        for (x in left..right) {
            for (y in top..bottom) {
                val dx = x - cx
                val dy = y - cy
                if (dx * dx + dy * dy <= radius * radius) {
                    bmp.setPixel(x, y, Color.TRANSPARENT)
                }
            }
        }
    }

    private fun damageWorms(cx: Float, cy: Float, radius: Float, maxDamage: Int) {
        worms.filter { it.alive }.forEach { worm ->
            val dist = hypot(worm.x * width - cx, worm.y - cy)
            if (dist <= radius * 1.35f) {
                val damage = ((1f - dist / (radius * 1.35f)) * maxDamage).roundToInt().coerceAtLeast(6)
                worm.hp = (worm.hp - damage).coerceAtLeast(0)
                if (worm.hp <= 0) worm.alive = false
            }
        }
    }

    private fun nextTurn() {
        moveInput = 0f
        charging = false
        aimActive = false
        val currentTeam = activeTeam()
        val nextTeam = if (winnerTeam == null) 1 - currentTeam else currentTeam
        activeWormIndex = nextAliveWormIndex(nextTeam)
        val worm = worms[activeWormIndex]
        worm.moveLeft = 1f
        worm.jumpsLeft = 1
        angleDeg = if (worm.team == 0) 45f else 50f
        power = 0.62f
        wind = randomWind()
        turnStartedMs = System.currentTimeMillis()
        aiFireAt = 0L
    }

    private fun nextAliveWormIndex(team: Int): Int {
        val start = (activeWormIndex + 1) % worms.size
        for (offset in worms.indices) {
            val idx = (start + offset) % worms.size
            if (worms[idx].alive && worms[idx].team == team) return idx
        }
        return worms.indexOfFirst { it.alive }.coerceAtLeast(0)
    }

    private fun maybeRunAi() {
        if (mode != GameMode.PVE || activeTeam() != 1 || projectile != null || explosion != null || winnerTeam != null) return
        val now = System.currentTimeMillis()
        if (aiFireAt == 0L) {
            aiFireAt = now + 900L
            chooseAiTurn()
            return
        }
        if (now >= aiFireAt) {
            fireCurrentWeapon()
            aiFireAt = 0L
        }
    }

    private fun chooseAiTurn() {
        val shooter = worms[activeWormIndex]
        val target = worms.filter { it.alive && it.team != shooter.team }.minByOrNull { abs(it.x - shooter.x) } ?: return
        val dx = abs(target.x - shooter.x) * width
        selectedWeapon = when {
            dx > width * 0.35f -> 2
            dx > width * 0.22f -> 0
            else -> 3
        }
        angleDeg = (30f + dx / width * 50f).coerceIn(24f, 74f)
        power = (0.42f + dx / width * 0.7f).coerceIn(0.35f, 1f)
        if (Random.nextFloat() < 0.18f && shooter.jumpsLeft > 0) jumpCurrentWorm()
    }

    private fun collectCrates() {
        val active = worms[activeWormIndex]
        crates.filter { it.active }.forEach { crate ->
            if (hypot(active.x * width - crate.x, active.y - crate.y) <= wormRadius + crateSize * 0.55f) {
                crate.active = false
                when (crate.type) {
                    CrateType.HEALTH -> active.hp = (active.hp + 25).coerceAtMost(100)
                    CrateType.WEAPON -> selectedWeapon = (selectedWeapon + 1) % weapons.size
                }
            }
        }
    }

    private fun maybeSpawnCrates() {
        if (crates.isNotEmpty()) return
        val healthX = width * 0.48f
        val weaponX = width * 0.56f
        crates += SupplyCrate(healthX, groundYAtPx(healthX) - crateSize / 2f, CrateType.HEALTH)
        crates += SupplyCrate(weaponX, groundYAtPx(weaponX) - crateSize / 2f, CrateType.WEAPON)
    }

    private fun evaluateWinner() {
        val aliveA = worms.any { it.alive && it.team == 0 }
        val aliveB = worms.any { it.alive && it.team == 1 }
        winnerTeam = when {
            aliveA && aliveB -> null
            aliveA -> 0
            aliveB -> 1
            else -> 1 - activeTeam()
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val w = width.toFloat().coerceAtLeast(1f)
        val h = height.toFloat().coerceAtLeast(1f)
        if (mode == GameMode.TITLE) {
            if (event.action == MotionEvent.ACTION_DOWN) {
                mode = if (event.y < h * 0.63f) GameMode.PVE else GameMode.PVP
                setupMatch()
            }
            return true
        }
        if (winnerTeam != null) {
            if (event.action == MotionEvent.ACTION_DOWN) {
                mode = GameMode.TITLE
                setupMatch()
            }
            return true
        }
        if (projectile != null || explosion != null) return true
        if (mode == GameMode.PVE && activeTeam() == 1) return true

        val movePad = movePadRect(w, h)
        val jump = jumpRect(w, h)
        val weapon = weaponRect(w, h)
        val aim = aimRect(w, h)
        val fire = fireRect(w, h)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                when {
                    movePad.contains(event.x, event.y) -> {
                        moveInput = ((event.x - movePad.centerX()) / (movePad.width() / 2f)).coerceIn(-1f, 1f)
                    }
                    jump.contains(event.x, event.y) && event.actionMasked == MotionEvent.ACTION_DOWN -> jumpCurrentWorm()
                    weapon.contains(event.x, event.y) && event.actionMasked == MotionEvent.ACTION_DOWN -> selectedWeapon = (selectedWeapon + 1) % weapons.size
                    fire.contains(event.x, event.y) && event.actionMasked == MotionEvent.ACTION_DOWN -> fireCurrentWeapon()
                    aim.contains(event.x, event.y) -> {
                        aimActive = true
                        updateAim(event.x, event.y)
                        if (!charging && event.actionMasked == MotionEvent.ACTION_DOWN) {
                            charging = true
                            chargeStartedMs = System.currentTimeMillis()
                        }
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (charging && aimActive) {
                    val held = (System.currentTimeMillis() - chargeStartedMs).coerceAtMost(1600L)
                    power = (0.20f + held / 1600f * 0.80f).coerceIn(0.20f, 1f)
                }
                moveInput = 0f
                charging = false
                aimActive = false
            }
        }
        invalidate()
        return true
    }

    private fun updateAim(touchX: Float, touchY: Float) {
        val worm = worms[activeWormIndex]
        val wormX = worm.x * width
        val wormY = worm.y - wormRadius
        val dx = touchX - wormX
        val dy = wormY - touchY
        val facingDx = if (worm.team == 0) dx.coerceAtLeast(1f) else (-dx).coerceAtLeast(1f)
        angleDeg = Math.toDegrees(atan2(dy.toDouble(), facingDx.toDouble())).toFloat().coerceIn(8f, 84f)
        if (charging) {
            val dist = hypot(dx, wormY - touchY)
            power = (dist / (width * 0.42f)).coerceIn(0.18f, 1f)
        }
    }

    private fun jumpCurrentWorm() {
        val worm = worms[activeWormIndex]
        if (!worm.alive || worm.jumpsLeft <= 0) return
        val dir = if (worm.team == 0) 1f else -1f
        worm.x = (worm.x + 0.04f * dir).coerceIn(0.04f, 0.96f)
        worm.y -= wormRadius * 1.4f
        worm.jumpsLeft -= 1
        worm.moveLeft = (worm.moveLeft - 0.25f).coerceAtLeast(0f)
    }

    private fun fireCurrentWeapon() {
        val worm = worms[activeWormIndex]
        if (!worm.alive) return
        val weapon = weapons[selectedWeapon]
        val dir = if (worm.team == 0) 1f else -1f
        val radians = Math.toRadians(angleDeg.toDouble())
        val speed = weapon.speed * power
        projectile = Projectile(
            x = worm.x * width,
            y = worm.y - wormRadius,
            vx = (cos(radians) * speed * dir).toFloat(),
            vy = (-sin(radians) * speed).toFloat(),
            weapon = weapon,
            ownerTeam = worm.team
        )
        lastFrameNanos = System.nanoTime()
        invalidate()
    }

    private fun activeTeam(): Int = worms.getOrNull(activeWormIndex)?.team ?: 0

    private fun snapAllWormsToGround() {
        worms.filter { it.alive }.forEach { worm ->
            worm.y = groundYAtPx(worm.x * width) - wormRadius
        }
    }

    private fun groundYAtPx(xPx: Float): Float {
        if (terrainHeight.isEmpty()) return height * 0.7f
        val idx = xPx.roundToInt().coerceIn(0, terrainHeight.lastIndex)
        return terrainHeight[idx].toFloat()
    }

    private fun isTerrainAt(x: Float, y: Float): Boolean {
        val bmp = terrainBitmap ?: return false
        val ix = x.roundToInt().coerceIn(0, bmp.width - 1)
        val iy = y.roundToInt().coerceIn(0, bmp.height - 1)
        return Color.alpha(bmp.getPixel(ix, iy)) > 0
    }

    private fun movePadRect(w: Float, h: Float) = RectF(24f, h - 150f, 174f, h - 20f)
    private fun jumpRect(w: Float, h: Float) = RectF(w - 330f, h - 150f, w - 220f, h - 80f)
    private fun weaponRect(w: Float, h: Float) = RectF(w - 200f, h - 150f, w - 60f, h - 80f)
    private fun aimRect(w: Float, h: Float) = RectF(w - 290f, h - 340f, w - 24f, h - 180f)
    private fun fireRect(w: Float, h: Float) = RectF(w - 210f, h - 72f, w - 24f, h - 18f)

    private fun randomWind(): Float = Random.nextFloat() * 84f - 42f

    private fun loadBitmap(path: String): Bitmap? = runCatching {
        context.assets.open(path).use { BitmapFactory.decodeStream(it) }
    }.getOrNull()
}
