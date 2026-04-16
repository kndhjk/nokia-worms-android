package com.kndhjk.nokiaworms

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import android.os.VibrationEffect
import android.os.Vibrator
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.roundToInt
import kotlin.math.atan2

class GameView(context: Context) : View(context) {

    enum class GameMode { TITLE, SELECT_WORM, PVP, PVE, GAME_OVER }

    data class Weapon(
        val name: String, val speed: Float, val radius: Float,
        val damage: Int, val gravityScale: Float, val color: Int, val blastColor: Int,
        val isTimed: Boolean = false, val fuseSecs: Float = 0f,
        val isBouncy: Boolean = false, val maxBounces: Int = 0,
        val ammo: Int = 8, val pellets: Int = 1
    )

    data class Worm(
        val team: Int, val name: String, var x: Float, var y: Float = 0f,
        var hp: Int = 100, var alive: Boolean = true,
        var moveDir: Float = 1f, var jumpsLeft: Int = 1,
        var fallDistance: Float = 0f,
        var facing: Int = 1,
        var walkFrame: Int = 0, var walkTimer: Float = 0f,
        var deathAge: Float = -1f,
        var hasParachute: Boolean = false, var parachuteOpen: Boolean = false,
        var knockVx: Float = 0f, var knockVy: Float = 0f,
        var vy: Float = 0f
    )

    data class Projectile(
        var x: Float, var y: Float, var vx: Float, var vy: Float,
        val weapon: Weapon, val ownerTeam: Int,
        var trail: MutableList<Pair<Float, Float>> = mutableListOf(),
        var timer: Float = 0f, var bounces: Int = 0
    )

    data class Particle(
        var x: Float, var y: Float,
        var vx: Float, var vy: Float,
        var life: Float = 0f, val maxLife: Float,
        var size: Float = 1f, var rot: Float = 0f, var rotSpeed: Float = 0f,
        val color: Int
    )

    data class Explosion(
        var x: Float, var y: Float, var radius: Float,
        var age: Float = 0f, val particles: MutableList<Particle> = mutableListOf()
    )

    data class KillEntry(val text: String, var age: Float = 0f)

    private val weapons = listOf(
        Weapon("Bazooka",  430f, 34f, 48, 1.00f, Color.rgb(60,  80, 200), Color.rgb(120, 140, 255), ammo=6),
        Weapon("Grenade",  360f, 42f, 60, 1.18f, Color.rgb(80, 160,  60), Color.rgb(140, 220, 100), isTimed=true, fuseSecs=2.8f, isBouncy=true, maxBounces=2, ammo=5),
        Weapon("Missile",  520f, 28f, 36, 0.90f, Color.rgb(200, 60,  40), Color.rgb(255, 130,  80), ammo=4),
        Weapon("Mortar",   300f, 54f, 74, 1.32f, Color.rgb(140, 90,  40), Color.rgb(200, 160,  80), ammo=3),
        Weapon("Shotgun",  610f, 18f, 24, 0.76f, Color.rgb(180, 140, 50), Color.rgb(240, 200,  80), ammo=4, pellets=5),
        Weapon("Dynamite", 240f, 62f, 86, 1.45f, Color.rgb(50,  50,  50), Color.rgb(180,  80,  40), isTimed=true, fuseSecs=3.5f, ammo=3)
    )

    private var weaponAmmo = IntArray(weapons.size) { weapons[it].ammo }
    private var worms = mutableListOf<Worm>()
    private var activeWormIndex = 0
    private var selectedWeapon = 0
    private var angleDeg = 45f
    private var power = 0.62f
    private var wind = 0f
    private var mode = GameMode.TITLE
    private var winnerTeam: Int? = null
    private var turnStartedMs = System.currentTimeMillis()
    private var projectiles = mutableListOf<Projectile>()
    private var explosion: Explosion? = null
    private var particles = mutableListOf<Particle>()
    private var aiFireAt = 0L
    private var lastFrameNanos = System.nanoTime()
    private var moveInput = 0f
    private var lastFireMs = 0L
    private var turnFlashAge = 0f
    private var shakeAge = 0f
    private var shakeIntensity = 0f
    private val killFeed = mutableListOf<KillEntry>()
    private var selectingTeam = 0
    private var aiSelectAt = 0L
    private var joystickId = -2
    private var aimTouchId = -2
    private var aimStartX = 0f
    private var aimStartY = 0f
    private var joystickCenterX = 0f
    private var joystickCenterY = 0f
    private var joystickDeltaX = 0f
    private var joystickDeltaY = 0f
    private var wormRadius = 0f
    private var lastFireAngle = 45f
    private var lastFirePower = 0.62f
    private var terrainHeight = mutableListOf<Int>()
    private var terrainDirty = true

    private val fireCooldown: Long get() = when (selectedWeapon) {
        0 -> 700L; 1 -> 1200L; 2 -> 550L; 3 -> 1500L; 4 -> 900L; 5 -> 1800L; else -> 800L
    }
    private val cooldownRemaining: Long get() = max(0L, fireCooldown - (System.currentTimeMillis() - lastFireMs))
    private val turnDurationMs = 25_000L

    private val vibrator: Vibrator? by lazy {
        try { context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator } catch (_: Exception) { null }
    }

    // Paints
    private val solidPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }

    init { setupMatch() }

    private fun setupMatch() {
        worms.clear()
        listOf("A1","A2","A3").forEachIndexed { i, n -> worms.add(Worm(0, n, 0.12f + i * 0.04f)) }
        listOf("B1","B2","B3").forEachIndexed { i, n -> worms.add(Worm(1, n, 0.70f + i * 0.04f)) }
        activeWormIndex = 0
        selectedWeapon = 0
        angleDeg = 45f; power = 0.62f
        projectiles.clear(); explosion = null; particles.clear()
        killFeed.clear(); winnerTeam = null
        turnStartedMs = System.currentTimeMillis()
        terrainDirty = true
        mode = GameMode.SELECT_WORM
        selectingTeam = 0
        aiSelectAt = System.currentTimeMillis() + 1200
    }

    private fun groundY(team: Int, xFrac: Float): Float {
        val x = (xFrac * width).coerceIn(0f, (width - 1).toFloat())
        val base = height * 0.68f
        val b = sin((x / width) * PI * 1.4).toFloat() * height * 0.08f
        val m = sin((x / width) * PI * 4.9).toFloat() * height * 0.03f
        val s = sin((x / width) * PI * 10.4).toFloat() * height * 0.012f
        return (base + b + m + s).coerceIn(height * 0.42f, height * 0.84f)
    }

    private fun ensureTerrain() {
        if (width <= 0 || height <= 0) return
        if (!terrainDirty) return
        terrainHeight.clear()
        for (x in 0 until width) terrainHeight.add(groundY(0, x.toFloat() / width).toInt())
        terrainDirty = false
    }

    private fun isTerrainAt(px: Float, py: Float): Boolean {
        val ix = px.toInt().coerceIn(0, width - 1)
        val iy = py.toInt().coerceIn(0, height - 1)
        if (iy < 0 || iy >= terrainHeight.size) return false
        return iy >= terrainHeight[ix]
    }

    private fun snapToGround(w: Worm) {
        val gy = groundY(w.team, w.x)
        if (w.y < gy) { w.y = gy; w.fallDistance = 0f }
        w.hasParachute = false; w.parachuteOpen = false; w.vy = 0f
    }

    private fun activateWorm(idx: Int) {
        activeWormIndex = idx
        turnStartedMs = System.currentTimeMillis()
        turnFlashAge = 0.4f
        selectedWeapon = 0; angleDeg = 45f; power = 0.62f
        lastFireMs = 0L; moveInput = 0f
        joystickDeltaX = 0f; joystickDeltaY = 0f
        if (worms[idx].team == 1) maybeRunAi()
    }

    private fun nextTurn() {
        val current = worms.getOrNull(activeWormIndex) ?: return
        val currentTeam = current.team
        for (i in 1 until worms.size) {
            val idx = (activeWormIndex + i) % worms.size
            if (worms[idx].team == currentTeam && worms[idx].alive) { activateWorm(idx); return }
        }
        val otherTeam = 1 - currentTeam
        val firstOther = worms.indexOfFirst { it.team == otherTeam && it.alive }
        if (firstOther != -1) {
            mode = GameMode.SELECT_WORM; selectingTeam = otherTeam
            aiSelectAt = System.currentTimeMillis() + 1200
        } else {
            evaluateWinner()
        }
    }

    private fun selectWormForTeam(team: Int) {
        val idx = worms.indexOfFirst { it.team == team && it.alive }
        if (idx != -1) activateWorm(idx)
    }

    private fun evaluateWinner() {
        val alive = worms.filter { it.alive }.map { it.team }.distinct()
        winnerTeam = alive.singleOrNull(); mode = GameMode.GAME_OVER
    }

    private fun explode(x: Float, y: Float, weapon: Weapon, ownerTeam: Int) {
        projectiles.removeAll { true }
        explosion = Explosion(x, y, weapon.radius)
        shakeIntensity = 1.0f; shakeAge = 0f; doHaptic(80L)
        repeat(16) {
            val angle = (Math.random() * PI * 2).toFloat()
            val speed = 60f + Math.random().toFloat() * 220f
            val life = 0.3f + Math.random().toFloat() * 0.5f
            val colors = intArrayOf(Color.rgb(255,200,80), Color.rgb(255,120,40), Color.rgb(255,80,20))
            explosion!!.particles.add(Particle(x, y, cos(angle)*speed, sin(angle)*speed - 80f,
                0f, life, wormRadius*(0.5f + Math.random().toFloat()*1.2f),
                Math.random().toFloat()*360f, Math.random().toFloat()*360f-180f,
                colors[(Math.random()*3).toInt()]))
        }
        // Carve terrain
        val r = weapon.radius.toInt()
        for (dx in -r..r) {
            val px = (x.toInt() + dx).coerceIn(0, width - 1)
            for (dy in -r..r) {
                val py = (y.toInt() + dy).coerceIn(0, height - 1)
                if (dx*dx + dy*dy <= r*r && py < terrainHeight[px]) terrainHeight[px] = py
            }
        }
        terrainDirty = false
        // Damage
        worms.filter { it.alive }.forEach { worm ->
            val dist = hypot(worm.x*width - x, worm.y - y)
            if (dist <= weapon.radius * 1.35f) {
                val dmg = ((1f - dist/(weapon.radius*1.35f)) * weapon.damage).roundToInt().coerceAtLeast(6)
                worm.hp = (worm.hp - dmg).coerceAtLeast(0)
                if (dist > 1f) {
                    val dx2 = (worm.x*width - x)/dist; val dy2 = (worm.y - y)/dist
                    worm.knockVx += dx2 * weapon.radius * 3f * (1f - dist/(weapon.radius*1.35f))
                    worm.knockVy += dy2 * weapon.radius * 3f * (1f - dist/(weapon.radius*1.35f)) - weapon.radius * 1.5f
                }
                if (worm.hp <= 0) { worm.alive = false; worm.deathAge = 0f
                    val owner = worms.firstOrNull { it.team == ownerTeam }?.name ?: "??"
                    killFeed.add(0, KillEntry("$owner defeated ${worm.name}", 0f)) }
            }
        }
        if (worms.none { it.alive }) evaluateWinner()
    }

    private fun fire() {
        val worm = worms.getOrNull(activeWormIndex) ?: return
        if (!worm.alive || (mode != GameMode.PVP && mode != GameMode.PVE)) return
        if (cooldownRemaining > 0) return
        if (weaponAmmo[selectedWeapon] <= 0) return
        val wpn = weapons[selectedWeapon]
        val dir = if (worm.facing == 1) 1f else -1f
        val rad = Math.toRadians(angleDeg.toDouble())
        val baseX = worm.x * width; val baseY = worm.y - wormRadius
        if (wpn.pellets > 1) {
            for (k in 0 until wpn.pellets) {
                val spread = (k - wpn.pellets/2f) * 0.10f
                val pRad = rad + spread
                val pSpeed = wpn.speed * power
                projectiles.add(Projectile(baseX, baseY,
                    (cos(pRad)*pSpeed*dir).toFloat(), (-sin(pRad)*pSpeed).toFloat(), wpn, worm.team))
            }
        } else {
            projectiles.add(Projectile(baseX, baseY,
                (cos(rad)*wpn.speed*power*dir).toFloat(), (-sin(rad)*wpn.speed*power).toFloat(), wpn, worm.team))
        }
        weaponAmmo[selectedWeapon]--; lastFireMs = System.currentTimeMillis()
        lastFireAngle = angleDeg; lastFirePower = power
        doHaptic(40L)
    }

    private fun jump() {
        val worm = worms.getOrNull(activeWormIndex) ?: return
        if (!worm.alive || worm.jumpsLeft <= 0) return
        worm.jumpsLeft--; worm.vy = -height * 0.42f
        worm.hasParachute = true; worm.parachuteOpen = false; worm.fallDistance = 0f
    }

    private fun doHaptic(durationMs: Long) {
        try {
            vibrator?.let { v ->
                if (v.hasVibrator()) {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O)
                        v.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
                    else @Suppress("DEPRECATION") v.vibrate(durationMs)
                }
            }
        } catch (_: Exception) { }
    }

    private fun maybeRunAi() {
        if (mode != GameMode.PVE && mode != GameMode.PVP) return
        if (activeTeam() != 1) return
        if (projectiles.isNotEmpty() || explosion != null) return
        aiFireAt = System.currentTimeMillis() + 800 + (Math.random() * 1200).toLong()
    }

    private fun runAiFire() {
        val worm = worms.getOrNull(activeWormIndex) ?: return
        val target = worms.filter { it.alive && it.team != worm.team }.randomOrNull() ?: return
        val dx = (target.x - worm.x) * width; val dy = target.y - worm.y
        val dist = hypot(dx, dy)
        selectedWeapon = when {
            dist > 600 -> 0; dist > 350 -> 2; dist < 200 -> 4; else -> 1
        }
        val wpn = weapons[selectedWeapon]
        val absDx = abs(dx)
        angleDeg = Math.toDegrees(atan2((-dy + wind * absDx / wpn.speed * 0.5f).toDouble(), absDx.toDouble())).toFloat().coerceIn(5f, 85f)
        power = (dist / (wpn.speed * 0.75f)).coerceIn(0.3f, 1.0f)
        fire()
    }

    private fun activeTeam() = worms.getOrNull(activeWormIndex)?.team ?: 0

    private fun updatePhysics(dt: Float) {
        worms.forEach { worm ->
            if (!worm.alive) {
                if (worm.deathAge >= 0f) worm.deathAge = (worm.deathAge + dt).coerceAtMost(1f)
                return@forEach
            }
            worm.x += worm.knockVx * dt; worm.knockVx *= 0.88f
            worm.vy += 350f * dt
            worm.vy = worm.vy.coerceAtMost(if (worm.parachuteOpen) height * 0.055f else height * 0.25f)
            worm.y += worm.vy * dt; worm.knockVy *= 0.95f
            worm.x = worm.x.coerceIn(0.01f, 0.99f)
            val ground = groundY(worm.team, worm.x)
            if (worm.y >= ground) {
                if (worm.vy > 0) worm.fallDistance += worm.vy * dt
                worm.y = ground
                if (worm.fallDistance > height * 0.35f) {
                    worm.hp = (worm.hp - (worm.fallDistance / height * 60f).toInt()).coerceAtLeast(1)
                    worm.fallDistance = 0f
                }
                if (worm.vy > 0) { worm.vy = 0f; worm.hasParachute = false; worm.parachuteOpen = false }
            }
            if (worm.hasParachute && worm.vy > height * 0.1f) worm.parachuteOpen = true
            if (abs(moveInput) > 0.1f) {
                worm.walkTimer += dt
                if (worm.walkTimer > 0.15f) { worm.walkTimer = 0f; worm.walkFrame = (worm.walkFrame + 1) % 4 }
                worm.x += moveInput * 0.0012f; worm.facing = if (moveInput > 0) 1 else -1
            }
            worm.x = worm.x.coerceIn(0.01f, 0.99f)
        }
    }

    private fun updateFrame(dt: Float) {
        if (turnFlashAge > 0f) turnFlashAge = (turnFlashAge - dt).coerceAtLeast(0f)
        if (shakeAge >= 0f) { shakeAge += dt; if (shakeAge > 0.45f) { shakeAge = -1f; shakeIntensity = 0f } }
        else shakeIntensity *= 0.85f
        killFeed.forEach { it.age += dt }; killFeed.removeAll { it.age > 4f }

        if (mode == GameMode.SELECT_WORM && System.currentTimeMillis() > aiSelectAt) {
            selectWormForTeam(selectingTeam)
            mode = if (worms.any { it.team == 1 && it.alive }) GameMode.PVE else GameMode.PVP
        }

        if ((mode == GameMode.PVE && activeTeam() == 1 || mode == GameMode.PVP && activeTeam() == 1)
            && projectiles.isEmpty() && explosion == null && System.currentTimeMillis() > aiFireAt) {
            runAiFire()
        }

        if ((mode == GameMode.PVP || mode == GameMode.PVE) && projectiles.isEmpty() && explosion == null) {
            if (System.currentTimeMillis() - turnStartedMs > turnDurationMs) nextTurn()
        }

        projectiles.forEach { p ->
            p.trail.add(Pair(p.x, p.y))
            if (p.trail.size > 16) p.trail.removeAt(0)
            p.x += p.vx * dt; p.y += p.vy * dt
            p.vx += wind * dt; p.vy += 600f * p.weapon.gravityScale * dt
            if (p.weapon.isTimed) { p.timer += dt
                if (p.timer >= p.weapon.fuseSecs) { explode(p.x, p.y, p.weapon, p.ownerTeam); return@forEach } }
            if (p.x < 0f || p.x >= width || p.y < -40f || p.y >= height) {
                explode(p.x.coerceIn(0f, width.toFloat()), p.y.coerceIn(0f, height.toFloat()), p.weapon, p.ownerTeam); return@forEach }
            if (isTerrainAt(p.x, p.y)) {
                if (p.weapon.isBouncy && p.bounces < p.weapon.maxBounces) {
                    p.bounces++; p.vy = -p.vy * 0.42f; p.vx *= 0.78f
                    p.y = (groundY(worms[activeWormIndex].team, p.x / width) - wormRadius).coerceAtMost(p.y - 2f)
                } else { explode(p.x, p.y, p.weapon, p.ownerTeam); return@forEach }
            }
            val hit = worms.firstOrNull { it.alive && hypot(it.x*width - p.x, it.y - p.y) < wormRadius * 1.25f }
            if (hit != null) { explode(p.x, p.y, p.weapon, p.ownerTeam) }
        }
        projectiles.removeAll { it.x < 0 || it.x >= width || it.y > height }

        explosion?.let { exp ->
            exp.age += dt
            exp.particles.forEach { p -> p.x += p.vx * dt; p.y += p.vy * dt; p.vy += 180f * dt; p.life += dt; p.rot += p.rotSpeed * dt }
            exp.particles.removeAll { it.life >= it.maxLife }
            if (exp.age >= 0.55f && exp.particles.isEmpty()) explosion = null
        }
        particles.forEach { p -> p.x += p.vx * dt; p.y += p.vy * dt; p.vy += 160f * dt; p.life += dt; p.rot += p.rotSpeed * dt }
        particles.removeAll { it.life >= it.maxLife }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val act = event.actionMasked
        val px = event.x; val py = event.y
        val w = width.toFloat(); val h = height.toFloat()
        val joyR = wormRadius * 2.8f
        val joyC = RectF(30f, h - joyR * 2 - 30f, 30f + joyR * 2, h - 30f)
        val fireR = wormRadius * 2.8f
        val fireCX = w - fireR - 30f; val fireCY = h - fireR - 30f
        val jumpCX = w - fireR - 30f; val jumpCY = h - fireR * 5.8f
        val wpCX = w - fireR - 30f; val wpCY = h - fireR * 8.8f

        when (act) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val idx = event.actionIndex; val tid = event.getPointerId(idx)
                val tx = event.getX(idx); val ty = event.getY(idx)
                val dx = tx - fireCX; val dy2 = ty - fireCY; val fireDist = hypot(dx, dy2)
                val jdx = tx - jumpCX; val jdy = ty - jumpCY; val jumpDist = hypot(jdx, jdy)
                val wdx = tx - wpCX; val wdy = ty - wpCY; val wpDist = hypot(wdx, wdy)
                when {
                    fireDist <= fireR -> {
                        if (mode == GameMode.TITLE) { mode = GameMode.SELECT_WORM; selectingTeam = 0; aiSelectAt = System.currentTimeMillis() + 1200 }
                        else if (mode == GameMode.GAME_OVER) { setupMatch() }
                        else if (mode == GameMode.PVP || mode == GameMode.PVE) { angleDeg = lastFireAngle; power = lastFirePower; fire() }
                    }
                    jumpDist <= fireR -> { if (mode == GameMode.PVP || mode == GameMode.PVE) jump() }
                    wpDist <= fireR -> {
                        if (mode == GameMode.PVP || mode == GameMode.PVE) {
                            selectedWeapon = (selectedWeapon + 1) % weapons.size
                            while (weaponAmmo[selectedWeapon] <= 0) selectedWeapon = (selectedWeapon + 1) % weapons.size
                        }
                    }
                    joyC.contains(tx, ty) -> { joystickId = tid; joystickCenterX = joyC.centerX(); joystickCenterY = joyC.centerY() }
                    else -> { aimTouchId = tid; aimStartX = tx; aimStartY = ty; aimActive = true }
                }
            }
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until event.pointerCount) {
                    val tid = event.getPointerId(i); val tx2 = event.getX(i); val ty2 = event.getY(i)
                    when (tid) {
                        joystickId -> {
                            joystickDeltaX = (tx2 - joystickCenterX).coerceIn(-joyR, joyR)
                            joystickDeltaY = (ty2 - joystickCenterY).coerceIn(-joyR, joyR)
                            moveInput = (joystickDeltaX / joyR).coerceIn(-1f, 1f)
                            if ((mode == GameMode.PVP || mode == GameMode.PVE) && abs(moveInput) > 0.1f) {
                                worms.getOrNull(activeWormIndex)?.facing = if (moveInput > 0) 1 else -1
                            }
                        }
                        aimTouchId -> {
                            if (aimActive) {
                                val adx = tx2 - aimStartX; val ady = ty2 - aimStartY
                                val worm = worms.getOrNull(activeWormIndex)
                                val dir = if (worm?.facing == -1) -1f else 1f
                                angleDeg = (Math.toDegrees(atan2((-ady).toDouble(), (adx * dir).toDouble())) / 90.0 * 90.0).toFloat().coerceIn(5f, 85f)
                                power = (hypot(adx, ady) / (joyR * 2.5f)).coerceIn(0.1f, 1.0f)
                            }
                        }
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val idx = event.actionIndex; val tid = event.getPointerId(idx)
                when (tid) {
                    joystickId -> { joystickId = -2; moveInput = 0f; joystickDeltaX = 0f; joystickDeltaY = 0f }
                    aimTouchId -> { aimActive = false }
                }
            }
            MotionEvent.ACTION_CANCEL -> { joystickId = -2; aimTouchId = -2; moveInput = 0f; aimActive = false }
        }
        return true
    }

    private var aimActive = false

    override fun onDraw(canvas: Canvas) {
        wormRadius = max(18f, width * 0.026f)
        ensureTerrain()
        val nowNanos = System.nanoTime()
        val dt = ((nowNanos - lastFrameNanos) / 1_000_000_000f).coerceIn(0f, 0.05f)
        lastFrameNanos = nowNanos
        updateFrame(dt)
        updatePhysics(dt)

        val shakeX = if (shakeAge >= 0f) (Math.random() * 2 - 1).toFloat() * shakeIntensity * 14f else 0f
        val shakeY = if (shakeAge >= 0f) (Math.random() * 2 - 1).toFloat() * shakeIntensity * 10f else 0f
        canvas.save(); canvas.translate(shakeX, shakeY)

        drawBackground(canvas)
        drawTerrain(canvas)

        when (mode) {
            GameMode.TITLE -> drawTitle(canvas)
            GameMode.SELECT_WORM -> { drawWorms(canvas); drawWormSelect(canvas) }
            GameMode.GAME_OVER -> { drawWorms(canvas); drawGameOver(canvas); drawKillFeed(canvas) }
            GameMode.PVP, GameMode.PVE -> {
                drawWorms(canvas)
                if (aimActive || projectiles.isEmpty()) drawAimPreview(canvas)
                drawProjectiles(canvas)
                drawExplosion(canvas)
                drawHud(canvas)
                drawWeaponBar(canvas)
                drawControls(canvas)
                drawKillFeed(canvas)
            }
        }
        canvas.restore()
        invalidate()
    }

    private fun drawBackground(canvas: Canvas) {
        solidPaint.shader = android.graphics.LinearGradient(0f, 0f, 0f, height * 0.65f,
            Color.rgb(126, 196, 255), Color.rgb(180, 230, 255), android.graphics.Shader.TileMode.CLAMP)
        solidPaint.style = Paint.Style.FILL
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), solidPaint)
        solidPaint.shader = null

        solidPaint.color = Color.rgb(80, 140, 60)
        val backHill = Path().apply {
            moveTo(0f, height * 0.70f); cubicTo(width*.10f, height*.36f, width*.32f, height*.56f, width*.52f, height*.40f)
            cubicTo(width*.64f, height*.28f, width*.82f, height*.56f, width.toFloat(), height*.42f)
            lineTo(width.toFloat(), height.toFloat()); lineTo(0f, height.toFloat()); close()
        }
        canvas.drawPath(backHill, solidPaint)
        solidPaint.color = Color.rgb(100, 160, 70)
        val frontHill = Path().apply {
            moveTo(0f, height * 0.78f); cubicTo(width*.18f, height*.50f, width*.38f, height*.66f, width*.55f, height*.50f)
            cubicTo(width*.70f, height*.34f, width*.88f, height*.58f, width.toFloat(), height*.50f)
            lineTo(width.toFloat(), height.toFloat()); lineTo(0f, height.toFloat()); close()
        }
        canvas.drawPath(frontHill, solidPaint)
    }

    private fun drawTerrain(canvas: Canvas) {
        val path = Path().apply {
            moveTo(0f, height.toFloat())
            lineTo(0f, terrainHeight.getOrElse(0) { (height * 0.68).toInt() }.toFloat())
            for (x in 1 until width) lineTo(x.toFloat(), terrainHeight.getOrElse(x) { (height * 0.68).toInt() }.toFloat())
            lineTo(width.toFloat(), height.toFloat()); close()
        }
        solidPaint.shader = android.graphics.LinearGradient(0f, height * 0.35f, 0f, height.toFloat(),
            Color.rgb(120, 82, 48), Color.rgb(72, 48, 28), android.graphics.Shader.TileMode.CLAMP)
        solidPaint.style = Paint.Style.FILL
        canvas.drawPath(path, solidPaint)
        solidPaint.shader = null

        solidPaint.color = Color.rgb(70, 140, 45)
        solidPaint.style = Paint.Style.FILL
        val grassPath = Path().apply {
            moveTo(0f, terrainHeight.getOrElse(0) { (height * 0.68).toInt() }.toFloat())
            for (x in 1 until width) lineTo(x.toFloat(), terrainHeight.getOrElse(x) { (height * 0.68).toInt() }.toFloat())
            lineTo(width.toFloat(), height.toFloat()); lineTo(0f, height.toFloat()); close()
        }
        canvas.drawPath(grassPath, solidPaint)
    }

    private fun drawWorms(canvas: Canvas) {
        worms.forEach { worm ->
            if (!worm.alive && worm.deathAge < 0f) return@forEach
            val cx = worm.x * width; val cy = worm.y
            val r = wormRadius
            val alpha = if (worm.deathAge > 0f) ((1f - worm.deathAge) * 255).toInt().coerceIn(0, 255) else 255
            val scale = if (worm.deathAge > 0f) (1f - worm.deathAge * 0.7f).coerceAtLeast(0.1f) else 1f
            val drawR = r * scale
            val teamColor = if (worm.team == 0) Color.rgb(40, 140, 220) else Color.rgb(220, 60, 60)

            // Shadow
            solidPaint.color = Color.argb(alpha / 3, 0, 0, 0); solidPaint.style = Paint.Style.FILL
            canvas.drawCircle(cx + 3f, cy + 3f, drawR, solidPaint)
            // Body
            solidPaint.color = Color.argb(alpha, Color.red(teamColor), Color.green(teamColor), Color.blue(teamColor))
            canvas.drawCircle(cx, cy, drawR, solidPaint)
            // Highlight
            solidPaint.color = Color.argb(alpha * 4 / 5, 255, 255, 255)
            canvas.drawCircle(cx - drawR * 0.25f, cy - drawR * 0.25f, drawR * 0.3f, solidPaint)
            // Parachute
            if (worm.parachuteOpen) {
                solidPaint.color = Color.argb(180, 255, 255, 255); solidPaint.style = Paint.Style.FILL
                val capH = drawR * 3f; val capW = drawR * 2.5f
                canvas.drawArc(cx - capW, cy - drawR - capH, cx + capW, cy - drawR, PI.toFloat(), PI.toFloat(), true, solidPaint)
                solidPaint.color = Color.argb(100, 200, 200, 200); solidPaint.style = Paint.Style.STROKE; solidPaint.strokeWidth = 2f
                canvas.drawLine(cx, cy - drawR, cx - capW, cy - drawR - capH * 0.6f, solidPaint)
                canvas.drawLine(cx, cy - drawR, cx + capW, cy - drawR - capH * 0.6f, solidPaint)
                canvas.drawLine(cx, cy - drawR, cx, cy - drawR - capH * 0.8f, solidPaint)
                solidPaint.strokeWidth = 1.5f
            }
            // Name
            textPaint.color = Color.argb(alpha, 255, 255, 255); textPaint.textSize = r * 0.7f; textPaint.isFakeBoldText = true
            canvas.drawText(worm.name, cx, cy - r - 6f, textPaint)
            // HP bar
            val bw = r * 2.6f; val bh = 7f; val bx = cx - bw / 2f; val by = cy + r + 8f
            solidPaint.color = Color.argb(alpha / 2, 60, 60, 60); solidPaint.style = Paint.Style.FILL
            canvas.drawRoundRect(bx, by, bx + bw, by + bh, 3f, 3f, solidPaint)
            val hpFrac = (worm.hp / 100f).coerceIn(0f, 1f)
            solidPaint.color = when { hpFrac > 0.5f -> Color.argb(alpha, 80, 220, 80); hpFrac > 0.25f -> Color.argb(alpha, 255, 200, 0); else -> Color.argb(alpha, 255, 60, 60) }
            canvas.drawRoundRect(bx, by, bx + bw * hpFrac, by + bh, 3f, 3f, solidPaint)
            solidPaint.style = Paint.Style.STROKE; solidPaint.color = Color.argb(alpha, 255, 255, 255); solidPaint.strokeWidth = 1.5f
            canvas.drawRoundRect(bx, by, bx + bw, by + bh, 3f, 3f, solidPaint)
        }
        textPaint.isFakeBoldText = false
    }

    private fun drawAimPreview(canvas: Canvas) {
        val worm = worms.getOrNull(activeWormIndex) ?: return
        if (!worm.alive) return
        val dir = if (worm.facing == 1) 1f else -1f
        val wpn = weapons[selectedWeapon]
        var x = worm.x * width; var y = worm.y - wormRadius
        val rad = Math.toRadians(angleDeg.toDouble())
        var vx = (cos(rad) * wpn.speed * power * dir).toFloat()
        var vy = (-sin(rad) * wpn.speed * power).toFloat()

        for (i in 0 until 30) {
            x += vx * 0.08f; y += vy * 0.08f; vx += wind * 0.08f; vy += 600f * wpn.gravityScale * 0.08f
            if (x < 0f || x >= width || y >= height || isTerrainAt(x, y)) break
            val progress = i / 30f
            val alpha = (200 * (1f - progress * 0.5f)).toInt().coerceIn(40, 200)
            val sz = (9f * (1f - progress * 0.5f)).coerceAtLeast(3f)
            outlinePaint.color = Color.argb(alpha / 2, 0, 0, 0); outlinePaint.strokeWidth = sz * 0.8f
            canvas.drawCircle(x, y, sz, outlinePaint)
            solidPaint.color = Color.argb(alpha, 255, 220, 80); solidPaint.style = Paint.Style.FILL
            canvas.drawCircle(x, y, sz, solidPaint)
        }

        // Blast radius preview
        outlinePaint.color = Color.argb(200, 255, 255, 80); outlinePaint.strokeWidth = 3f
        solidPaint.color = Color.argb(60, 255, 200, 80); solidPaint.style = Paint.Style.FILL
        canvas.drawCircle(x, y, wpn.radius, solidPaint)
        canvas.drawCircle(x, y, wpn.radius, outlinePaint)
        textPaint.color = Color.argb(220, 255, 255, 255); textPaint.textSize = 18f; textPaint.isFakeBoldText = true
        canvas.drawText("R:${wpn.radius.roundToInt()}", x, y - wpn.radius - 8f, textPaint)

        // Aim arc above worm
        val arcCx = worm.x * width; val arcCy = worm.y - wormRadius * 3.2f; val arcR = wormRadius * 2.8f
        outlinePaint.color = Color.argb(150, 255, 200, 80); outlinePaint.strokeWidth = 4f
        val startAngle = if (worm.facing == 1) 180f else 0f
        val sweepAngle = (angleDeg / 90f) * 180f
        canvas.drawArc(arcCx - arcR, arcCy - arcR, arcCx + arcR, arcCy + arcR, startAngle, sweepAngle, false, outlinePaint)

        // Power bar
        val barW = wormRadius * 5f; val barH = 8f; val barX = arcCx - barW / 2f; val barY = arcCy + arcR + 14f
        solidPaint.color = Color.rgb(40, 40, 40); solidPaint.style = Paint.Style.FILL
        canvas.drawRoundRect(barX, barY, barX + barW, barY + barH, 4f, 4f, solidPaint)
        solidPaint.color = Color.argb(200, 255, 180, 60)
        canvas.drawRoundRect(barX, barY, barX + barW * power, barY + barH, 4f, 4f, solidPaint)
        textPaint.color = Color.argb(180, 255, 255, 255); textPaint.textSize = 16f
        canvas.drawText("${angleDeg.roundToInt()}°  ${(power * 100).roundToInt()}%", arcCx, barY + barH + 22f, textPaint)
        textPaint.isFakeBoldText = false
    }

    private fun drawProjectiles(canvas: Canvas) {
        projectiles.forEach { p ->
            p.trail.forEachIndexed { i, (tx, ty) ->
                val alpha = ((i.toFloat() / p.trail.size) * 200).toInt().coerceIn(20, 200)
                val sz = wormRadius * (0.25f + i.toFloat() / p.trail.size * 0.6f)
                solidPaint.color = Color.argb(alpha, 255, 220, 80); solidPaint.style = Paint.Style.FILL
                canvas.drawCircle(tx, ty, sz, solidPaint)
            }
            val projR = max(14f, wormRadius * 0.75f)
            outlinePaint.color = 0xFF000000.toInt(); outlinePaint.strokeWidth = 4f; solidPaint.style = Paint.Style.FILL
            canvas.drawCircle(p.x, p.y, projR + 2f, outlinePaint)
            solidPaint.color = p.weapon.color; canvas.drawCircle(p.x, p.y, projR, solidPaint)
            solidPaint.color = Color.argb(180, 255, 255, 255)
            canvas.drawCircle(p.x - projR * 0.25f, p.y - projR * 0.25f, projR * 0.3f, solidPaint)
            if (p.weapon.isTimed) {
                val remaining = (p.weapon.fuseSecs - p.timer).coerceAtLeast(0f)
                textPaint.color = if (remaining < 1f) Color.rgb(255, 60, 60) else Color.WHITE; textPaint.textSize = 20f
                canvas.drawText("%.1f".format(remaining), p.x, p.y - projR - 6f, textPaint)
            }
        }
    }

    private fun drawExplosion(canvas: Canvas) {
        explosion?.let { exp ->
            val progress = (exp.age / 0.55f).coerceAtMost(1f)
            val ringAlpha = ((1f - progress) * 200).toInt().coerceIn(0, 200)
            outlinePaint.color = Color.argb(ringAlpha, 255, 200, 80)
            outlinePaint.strokeWidth = (8f * (1f - progress) + 2f).coerceAtLeast(2f)
            canvas.drawCircle(exp.x, exp.y, exp.radius * (0.3f + progress * 1.4f), outlinePaint)
            exp.particles.forEach { p ->
                val alpha = ((1f - p.life / p.maxLife) * 220).toInt().coerceIn(0, 220)
                val sz = p.size * (1f - p.life / p.maxLife * 0.5f)
                solidPaint.color = Color.argb(alpha, Color.red(p.color), Color.green(p.color), Color.blue(p.color))
                solidPaint.style = Paint.Style.FILL
                canvas.drawCircle(p.x, p.y, sz, solidPaint)
            }
        }
    }

    private fun drawHud(canvas: Canvas) {
        val worm = worms.getOrNull(activeWormIndex) ?: return
        val elapsed = System.currentTimeMillis() - turnStartedMs
        val remaining = ((turnDurationMs - elapsed) / 1000).toInt().coerceAtLeast(0)
        val urgent = remaining <= 10
        textPaint.textSize = 28f; textPaint.color = if (urgent) Color.rgb(255, 60, 60) else Color.WHITE; textPaint.isFakeBoldText = true
        canvas.drawText("${remaining}s", width / 2f, 50f, textPaint)
        textPaint.textSize = 22f; textPaint.color = Color.WHITE
        canvas.drawText("${if (wind >= 0) "→" else "←"} ${abs(wind).roundToInt()}", width / 2f, 82f, textPaint)
        textPaint.textSize = 20f; canvas.drawText(worm.name, width / 2f, 112f, textPaint)
        textPaint.isFakeBoldText = false
        if (turnFlashAge > 0f) {
            solidPaint.color = Color.argb((turnFlashAge * 400).toInt().coerceIn(0, 255), 255, 255, 255); solidPaint.style = Paint.Style.FILL
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), solidPaint)
        }
    }

    private fun drawWeaponBar(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        val iconW = w / 10f; val barH2 = iconW * 1.2f; val y = h - barH2 - 10f
        weapons.forEachIndexed { i, wpn ->
            val ix = iconW * i + 8f
            val selected = i == selectedWeapon
            val hasAmmo = weaponAmmo[i] > 0
            solidPaint.style = Paint.Style.FILL
            solidPaint.color = when {
                !hasAmmo -> Color.rgb(50, 50, 50)
                selected -> Color.rgb(80, 80, 80)
                else -> Color.rgb(30, 30, 30)
            }
            canvas.drawRoundRect(ix, y, ix + iconW - 6f, y + barH2, 8f, 8f, solidPaint)
            outlinePaint.color = if (selected) Color.rgb(255, 200, 80) else Color.rgb(100, 100, 100)
            outlinePaint.strokeWidth = if (selected) 3f else 1f
            canvas.drawRoundRect(ix, y, ix + iconW - 6f, y + barH2, 8f, 8f, outlinePaint)
            textPaint.color = if (hasAmmo) wpn.color else Color.rgb(80, 80, 80); textPaint.textSize = iconW * 0.22f
            canvas.drawText(wpn.name.take(3), ix + iconW / 2 - 3f, y + barH2 * 0.45f, textPaint)
            val dotR = 4f; val dotSpacing = dotR * 2.5f; val totalDots = min(8, wpn.ammo)
            val startX = ix + iconW / 2 - (totalDots * dotSpacing) / 2
            repeat(totalDots) { d ->
                solidPaint.color = if (d < weaponAmmo[i]) Color.rgb(255, 220, 60) else Color.rgb(60, 60, 60)
                canvas.drawCircle(startX + d * dotSpacing, y + barH2 * 0.8f, dotR, solidPaint)
            }
            if (wpn.isTimed) {
                solidPaint.color = Color.rgb(255, 60, 60); textPaint.textSize = iconW * 0.18f; textPaint.color = Color.rgb(255, 60, 60)
                canvas.drawText("T", ix + iconW - 14f, y + 16f, textPaint)
            }
        }
    }

    private fun drawControls(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        val joyR = wormRadius * 2.8f
        val joyCX = 30f + joyR; val joyCY = h - joyR - 30f
        solidPaint.color = Color.argb(60, 200, 200, 200); solidPaint.style = Paint.Style.FILL
        canvas.drawCircle(joyCX, joyCY, joyR, solidPaint)
        outlinePaint.color = Color.argb(100, 200, 200, 200); outlinePaint.strokeWidth = 2f
        canvas.drawCircle(joyCX, joyCY, joyR, outlinePaint)
        solidPaint.color = Color.argb(150, 255, 255, 255)
        canvas.drawCircle(joyCX + joystickDeltaX, joyCY + joystickDeltaY, joyR * 0.5f, solidPaint)

        val fireX = w - wormRadius * 2.8f; val fireY = h - wormRadius * 2.8f - 30f; val fireR = wormRadius * 2.2f
        val cooling = cooldownRemaining > 0
        solidPaint.color = if (cooling) Color.rgb(80, 80, 80) else Color.rgb(200, 50, 50); solidPaint.style = Paint.Style.FILL
        canvas.drawCircle(fireX, fireY, fireR, solidPaint)
        outlinePaint.color = if (cooling) Color.rgb(120, 120, 120) else Color.rgb(255, 120, 120); outlinePaint.strokeWidth = 3f
        canvas.drawCircle(fireX, fireY, fireR, outlinePaint)
        textPaint.color = Color.WHITE; textPaint.textSize = wormRadius * 0.8f; textPaint.isFakeBoldText = true
        canvas.drawText("FIRE", fireX, fireY + wormRadius * 0.25f, textPaint)
        if (cooling) {
            val frac = 1f - cooldownRemaining.toFloat() / fireCooldown
            outlinePaint.color = Color.rgb(255, 200, 80); outlinePaint.strokeWidth = 5f
            canvas.drawArc(fireX - fireR, fireY - fireR, fireX + fireR, fireY + fireR, -90f, frac * 360f, false, outlinePaint)
            textPaint.textSize = wormRadius * 0.55f
            canvas.drawText("%.1f".format(cooldownRemaining / 1000f), fireX, fireY + wormRadius * 0.75f, textPaint)
        }

        val jumpX = w - wormRadius * 2.8f; val jumpY = h - wormRadius * 5.8f
        solidPaint.color = Color.rgb(50, 130, 200); solidPaint.style = Paint.Style.FILL
        canvas.drawRoundRect(jumpX - wormRadius * 2.2f, jumpY - wormRadius, jumpX + wormRadius * 2.2f, jumpY + wormRadius, 12f, 12f, solidPaint)
        outlinePaint.color = Color.rgb(100, 180, 255); outlinePaint.strokeWidth = 2f
        canvas.drawRoundRect(jumpX - wormRadius * 2.2f, jumpY - wormRadius, jumpX + wormRadius * 2.2f, jumpY + wormRadius, 12f, 12f, outlinePaint)
        textPaint.color = Color.WHITE; textPaint.textSize = wormRadius * 0.65f
        canvas.drawText("JUMP", jumpX, jumpY + wormRadius * 0.25f, textPaint)

        val wpX = w - wormRadius * 2.8f; val wpY = h - wormRadius * 8.8f
        solidPaint.color = Color.rgb(50, 150, 80); solidPaint.style = Paint.Style.FILL
        canvas.drawRoundRect(wpX - wormRadius * 2.2f, wpY - wormRadius, wpX + wormRadius * 2.2f, wpY + wormRadius, 12f, 12f, solidPaint)
        outlinePaint.color = Color.rgb(100, 220, 140); outlinePaint.strokeWidth = 2f
        canvas.drawRoundRect(wpX - wormRadius * 2.2f, wpY - wormRadius, wpX + wormRadius * 2.2f, wpY + wormRadius, 12f, 12f, outlinePaint)
        textPaint.color = Color.WHITE; textPaint.textSize = wormRadius * 0.6f
        canvas.drawText(weapons[selectedWeapon].name.uppercase().take(4), wpX, wpY + wormRadius * 0.25f, textPaint)
        textPaint.isFakeBoldText = false
    }

    private fun drawTitle(canvas: Canvas) {
        solidPaint.color = Color.argb(180, 0, 0, 0); solidPaint.style = Paint.Style.FILL
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), solidPaint)
        textPaint.textSize = width * 0.1f; textPaint.color = Color.WHITE; textPaint.isFakeBoldText = true
        canvas.drawText("NOKIA", width / 2f, height * 0.35f, textPaint)
        textPaint.color = Color.rgb(80, 200, 80)
        canvas.drawText("WORMS", width / 2f, height * 0.48f, textPaint)
        textPaint.textSize = width * 0.045f; textPaint.color = Color.rgb(200, 200, 200)
        canvas.drawText("TAP TO START", width / 2f, height * 0.65f, textPaint)
        textPaint.isFakeBoldText = false
    }

    private fun drawWormSelect(canvas: Canvas) {
        solidPaint.color = Color.argb(180, 0, 0, 0); solidPaint.style = Paint.Style.FILL
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), solidPaint)
        textPaint.textSize = width * 0.06f; textPaint.color = Color.WHITE; textPaint.isFakeBoldText = true
        val teamName = if (selectingTeam == 0) "RED TEAM" else "BLUE TEAM"
        canvas.drawText("$teamName — TAP A WORM", width / 2f, height * 0.18f, textPaint)
        val selWorms = worms.filter { it.team == selectingTeam && it.alive }
        selWorms.forEachIndexed { i, worm ->
            val cx = width / 2f + (i - (selWorms.size - 1).toFloat() / 2) * width * 0.22f
            val cy = height * 0.5f; val r = wormRadius * 2.2f
            solidPaint.color = if (worm.team == 0) Color.rgb(40, 140, 220) else Color.rgb(220, 60, 60); solidPaint.style = Paint.Style.FILL
            canvas.drawCircle(cx, cy, r, solidPaint)
            outlinePaint.color = Color.WHITE; outlinePaint.strokeWidth = 3f; solidPaint.style = Paint.Style.STROKE
            canvas.drawCircle(cx, cy, r, outlinePaint)
            textPaint.textSize = r * 0.55f; textPaint.color = Color.WHITE
            canvas.drawText(worm.name, cx, cy + r * 0.25f, textPaint)
            val bw = r * 2.6f; val bh = 8f; val bx = cx - bw / 2f; val by = cy + r + 12f
            solidPaint.color = Color.rgb(40, 40, 40); solidPaint.style = Paint.Style.FILL
            canvas.drawRoundRect(bx, by, bx + bw, by + bh, 4f, 4f, solidPaint)
            solidPaint.color = Color.rgb(80, 220, 80); solidPaint.style = Paint.Style.FILL
            canvas.drawRoundRect(bx, by, bx + bw * (worm.hp / 100f), by + bh, 4f, 4f, solidPaint)
        }
        textPaint.isFakeBoldText = false
    }

    private fun drawGameOver(canvas: Canvas) {
        solidPaint.color = Color.argb(200, 0, 0, 0); solidPaint.style = Paint.Style.FILL
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), solidPaint)
        val winner = if (winnerTeam == 0) "RED TEAM" else if (winnerTeam == 1) "BLUE TEAM" else "DRAW"
        textPaint.textSize = width * 0.09f; textPaint.color = if (winnerTeam == 0) Color.rgb(60, 160, 255) else Color.rgb(255, 80, 80); textPaint.isFakeBoldText = true
        canvas.drawText(winner, width / 2f, height * 0.30f, textPaint)
        textPaint.textSize = width * 0.05f; textPaint.color = Color.WHITE
        canvas.drawText("WINS!", width / 2f, height * 0.40f, textPaint)
        textPaint.textSize = width * 0.04f; textPaint.color = Color.rgb(200, 200, 200)
        canvas.drawText("TAP TO RESTART", width / 2f, height * 0.70f, textPaint)
        textPaint.isFakeBoldText = false
    }

    private fun drawKillFeed(canvas: Canvas) {
        killFeed.take(4).forEachIndexed { i, entry ->
            val alpha = (255 * (1f - entry.age / 4f)).toInt().coerceIn(0, 255)
            textPaint.textSize = width * 0.038f; textPaint.color = Color.argb(alpha, 255, 200, 80)
            canvas.drawText(entry.text, width / 2f, height * 0.08f + i * width * 0.05f, textPaint)
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh); terrainDirty = true
    }
}
