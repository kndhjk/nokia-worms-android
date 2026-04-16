package com.kndhjk.nokiaworms

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Bitmap.Config
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.os.VibrationEffect
import android.os.Vibrator
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

class GameView(context: Context) : View(context) {
    // ── Asset bitmaps ─────────────────────────────────────────────────────────
    private val wormSprite  = loadBitmap("legacy/icon.png")
    private val logoImg     = loadBitmap("legacy/mgilog.png")
    private val titleImg    = loadBitmap("legacy/mgi24171.png")
    private val tileGrass   = loadBitmap("legacy/mgc27804.png")
    private val tileDirtA    = loadBitmap("legacy/mgc30403.png")
    private val bulletImg   = loadBitmap("legacy/bullet.png")
    private val logoSmall    = loadBitmap("legacy/mgi27282.png")

    // ── Particle bitmaps ─────────────────────────────────────────────────────
    private val circles = loadParticleSet("legacy/particles/circle_0%d.png", 1, 5)
    private val dirts   = loadParticleSet("legacy/particles/dirt_0%d.png", 1, 3)
    private val sparks  = loadParticleSet("legacy/particles/spark_0%d.png", 1, 7)
    private val flames  = loadParticleSet("legacy/particles/flame_0%d.png", 1, 6)
    private val fires   = loadParticleSet("legacy/particles/fire_0%d.png", 1, 2)
    private val muzzles = loadParticleSet("legacy/particles/muzzle_0%d.png", 1, 5)
    private val traces  = loadParticleSet("legacy/particles/trace_0%d.png", 1, 7)

    // ── Tiling paints ────────────────────────────────────────────────────────
    private val tileGrassPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        tileGrass?.let { shader = BitmapShader(it, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT) }
    }
    private val tileDirtPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        tileDirtA?.let { shader = BitmapShader(it, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT) }
    }

    // ── Color paints ────────────────────────────────────────────────────────
    private val skyPaint      = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(126, 196, 255) }
    private val skyGradPaint  = Paint(Paint.ANTI_ALIAS_FLAG)
    private val hillBackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(130, 167, 101) }
    private val hillFrontPaint= Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(95, 140, 72) }
    private val terrainPaint  = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(92, 64, 38) }
    private val grassPaint    = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(99, 173, 71) }
    private val panelPaint    = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(225, 255, 255, 255) }
    private val panelBorder   = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK; style = Paint.Style.STROKE; strokeWidth = 3f
    }
    private val activePaint   = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(140, 255, 220, 100) }
    private val aimPaint      = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(120, 0, 0, 0) }
    private val projectilePaint=Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK }
    private val hpBgPaint     = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(65, 65, 65) }
    private val hpGoodPaint   = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(101, 220, 111) }
    private val hpBadPaint    = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(255, 110, 110) }
    private val windPaint     = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(80, 125, 230); strokeWidth = 5f
    }
    private val cratePaint    = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(181, 129, 60) }
    private val crateBandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(83, 55, 28) }
    private val teamAPaint    = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(90, 255, 150, 70) }
    private val teamBPaint    = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(90, 80, 225, 110) }
    private val titleText     = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK; textSize = 66f; textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD
    }
    private val bodyText      = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 30f }
    private val hudText       = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 22f }
    private val hudSubText    = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(200, 220, 255); textSize = 18f
    }
    private val smallText     = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 22f }
    private val titleSmallText= Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.DKGRAY; textSize = 26f; textAlign = Paint.Align.CENTER
    }

    // ── Game state ───────────────────────────────────────────────────────────
    private enum class GameMode { TITLE, PVP, PVE, SELECT_WORM, GAME_OVER }
    private enum class CrateType { HEALTH, WEAPON }
    private enum class Dir { LEFT, RIGHT }

    private data class Weapon(
        val name: String, val speed: Float, val radius: Float,
        val damage: Int, val gravityScale: Float, val color: Int, val blastColor: Int,
        val trailParticle: List<Bitmap>?, val blastParticle: List<Bitmap>?,
        val isTimed: Boolean = false, val fuseSecs: Float = 0f,
        val isBouncy: Boolean = false, val maxBounces: Int = 0,
        val ammo: Int = 8,      // -1 = unlimited
        val pellets: Int = 1    // shotgun: 5, mortar: 1, etc.
    )
    private data class Worm(
        val team: Int, val name: String, var x: Float, var y: Float = 0f,
        var hp: Int = 100, var alive: Boolean = true,
        var moveLeft: Float = 1f, var jumpsLeft: Int = 1,
        var fallDistance: Float = 0f,
        var facing: Dir = Dir.RIGHT,
        var walkFrame: Int = 0,
        var walkTimer: Float = 0f,
        var deathAge: Float = -1f,  // -1 = alive, 0..1 = dying animation
        var hasParachute: Boolean = false, var parachuteOpen: Boolean = false
    )
    private data class Projectile(
        var x: Float, var y: Float, var vx: Float, var vy: Float,
        val weapon: Weapon, val ownerTeam: Int,
        var trail: MutableList<Pair<Float,Float>> = mutableListOf(),
        var timer: Float = 0f, var bounces: Int = 0
    )
    private data class Particle(
        var x: Float, var y: Float,
        var vx: Float, var vy: Float,
        val bmp: Bitmap, var life: Float = 0f, var maxLife: Float,
        var size: Float = 1f, var rot: Float = 0f, var rotSpeed: Float = 0f
    )
    private data class Explosion(
        var x: Float, var y: Float, var radius: Float,
        var age: Float = 0f, val particles: MutableList<Particle> = mutableListOf()
    )
    private data class SupplyCrate(
        var x: Float, var y: Float, val type: CrateType, var active: Boolean = true
    )
    private data class MuzzleFlash(
        var x: Float, var y: Float, var angle: Float,
        val frames: List<Bitmap>, var frame: Int = 0, var age: Float = 0f
    )

    private val weapons = listOf(
        Weapon("Bazooka",  430f, 34f, 48, 1.00f, Color.rgb(60,  80, 200), Color.rgb(120, 140, 255), traces, sparks,        ammo=6),
        Weapon("Grenade",  360f, 42f, 60, 1.18f, Color.rgb(80, 160,  60), Color.rgb(140, 220, 100), flames, dirts,  isTimed=true, fuseSecs=2.8f, isBouncy=true, maxBounces=2, ammo=5),
        Weapon("Missile",  520f, 28f, 36, 0.90f, Color.rgb(200, 60,  40), Color.rgb(255, 130,  80), traces, flames,          ammo=4),
        Weapon("Mortar",   300f, 54f, 74, 1.32f, Color.rgb(140, 90,  40), Color.rgb(200, 160,  80), fires,  circles,         ammo=3),
        Weapon("Shotgun",  610f, 18f, 24, 0.76f, Color.rgb(180, 140, 50), Color.rgb(240, 200,  80), sparks, sparks,          ammo=4, pellets=5),
        Weapon("Dynamite", 240f, 62f, 86, 1.45f, Color.rgb(50,  50,  50), Color.rgb(180,  80,  40), flames, circles, isTimed=true, fuseSecs=3.5f, ammo=3)
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
    private var lastFrameNanos = System.nanoTime()
    private var turnStartedMs = System.currentTimeMillis()
    private var projectiles = mutableListOf<Projectile>()
    private var explosion: Explosion? = null
    private var muzzleFlash: MuzzleFlash? = null
    private var particles = mutableListOf<Particle>()
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
    private var bulletOffset = 0f
    private var frameCount = 0
    // Screen shake
    private var shakeAge = 0f
    private var shakeIntensity = 0f
    // Turn transition flash
    private var turnFlashAge = 0f
    // Explosions drawn this frame for ring effect
    private val blastRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    // Weapon cooldown
    private var lastFireMs = 0L
    private val fireCooldown get() = when(selectedWeapon) {
        0->700L; 1->1200L; 2->550L; 3->1500L; 4->900L; 5->1800L; else->800L
    }
    private val cooldownRemaining get() = max(0L, fireCooldown - (System.currentTimeMillis()-lastFireMs))
    // Kill feed
    private data class KillEntry(val text: String, var age: Float = 0f)
    private val killFeed = mutableListOf<KillEntry>()
    // Background decorations
    private val cloudPaints = listOf(
        Paint(Paint.ANTI_ALIAS_FLAG).apply{color=Color.argb(70,255,255,255)},
        Paint(Paint.ANTI_ALIAS_FLAG).apply{color=Color.argb(50,240,240,255)}
    )
    private var cloudOffset = 0f
    // Haptic feedback
    private val vibrator: Vibrator? by lazy { context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator }
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

    private val turnDurationMs get() = 25_000L
    // Worm selection
    private var selectingTeam = 0
    private var aiSelectAt = 0L
    private val wormRadius get() = max(18f, width * 0.026f)
    private val crateSize get() = wormRadius * 1.45f

    init { setupMatch() }

    private fun loadParticleSet(pattern: String, start: Int, end: Int): List<Bitmap> {
        return (start..end).mapNotNull { i ->
            loadBitmap(String.format(pattern, i))
        }
    }

    private fun setupMatch() {
        worms = mutableListOf(
            Worm(0,"A-1",0.15f), Worm(0,"A-2",0.28f), Worm(0,"A-3",0.38f),
            Worm(1,"B-1",0.63f), Worm(1,"B-2",0.75f), Worm(1,"B-3",0.86f)
        )
        activeWormIndex = 0; selectedWeapon = 0; angleDeg = 45f; power = 0.62f
        wind = randomWind(); winnerTeam = null; projectiles.clear(); explosion = null
        muzzleFlash = null; particles.clear(); crates.clear(); killFeed.clear()
        lastFrameNanos = System.nanoTime(); turnStartedMs = System.currentTimeMillis()
        aiFireAt = 0L; terrainDirty = true; terrainBitmap = null; terrainCanvas = null
        terrainHeight = IntArray(0); bulletOffset = 0f; frameCount = 0
        weaponAmmo = IntArray(weapons.size) { weapons[it].ammo }
        mode = GameMode.TITLE
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat().coerceAtLeast(1f)
        val h = height.toFloat().coerceAtLeast(1f)
        ensureTerrain(); updateFrame()
        // Apply screen shake
        val shakeX = if(shakeAge>=0f) (Random.nextFloat()*2f-1f)*shakeIntensity*14f else 0f
        val shakeY = if(shakeAge>=0f) (Random.nextFloat()*2f-1f)*shakeIntensity*10f else 0f
        canvas.save(); canvas.translate(shakeX,shakeY)
        drawBackground(canvas, w, h); drawTerrain(canvas)
        if (mode == GameMode.TITLE) drawTitle(canvas, w, h)
        else if (mode == GameMode.SELECT_WORM) {
            drawParticles(canvas); drawCrates(canvas); drawWorms(canvas)
            drawWormSelect(canvas, w, h)
        }
        else if (mode == GameMode.GAME_OVER) {
            drawParticles(canvas); drawCrates(canvas); drawWorms(canvas)
            drawGameOver(canvas, w, h)
        }
        else {
            drawParticles(canvas)
            drawCrates(canvas); drawWorms(canvas)
            drawAimPreview(canvas); drawProjectile(canvas)
            drawMuzzleFlash(canvas); drawExplosion(canvas)
            drawHud(canvas, w, h); drawWeaponBar(canvas, w, h); drawControls(canvas, w, h)
            drawKillFeed(canvas, w)
        }
        // Restore screen shake translate
        canvas.restore()
        // Turn flash overlay
        if (turnFlashAge in 0f..0.95f) {
            val flashAlpha = ((1f-turnFlashAge/0.95f)*140).toInt().coerceIn(0,140)
            val flashPaint = Paint().apply { color = Color.argb(flashAlpha, 255, 255, 200) }
            canvas.drawRect(0f,0f,width.toFloat(),height.toFloat(),flashPaint)
        }
        if (mode==GameMode.TITLE || projectiles.isNotEmpty() || explosion!=null || particles.isNotEmpty() ||
            winnerTeam!=null || (mode==GameMode.PVE && activeTeam()==1)) invalidate()
    }

    private fun ensureTerrain() {
        if (width<=0||height<=0) return
        if (!terrainDirty && terrainBitmap?.width==width && terrainBitmap?.height==height) return
        terrainBitmap = Bitmap.createBitmap(width, height, Config.ARGB_8888)
        terrainCanvas = Canvas(terrainBitmap!!)
        terrainBitmap?.eraseColor(Color.TRANSPARENT)
        val canvas = terrainCanvas ?: return
        val path = Path().apply {
            moveTo(0f,height.toFloat()); lineTo(0f,sampleTerrainY(0f))
            for(x in 1 until width) lineTo(x.toFloat(),sampleTerrainY(x.toFloat()))
            lineTo(width.toFloat(),height.toFloat()); close()
        }
        // Draw dirt with vertical gradient (reliable, no tile artifacts)
        val dirtGrad = android.graphics.LinearGradient(0f, height*0.35f, 0f, height.toFloat(),
            Color.rgb(120, 82, 48), Color.rgb(72, 48, 28), android.graphics.Shader.TileMode.CLAMP)
        val dirtPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = dirtGrad }
        canvas.save(); canvas.clipPath(path)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), dirtPaint)
        canvas.restore()
        // Grass strip on top with gradient
        val grassGrad = android.graphics.LinearGradient(0f, height*0.30f, 0f, height*0.38f,
            Color.rgb(110, 180, 75), Color.rgb(80, 140, 50), android.graphics.Shader.TileMode.CLAMP)
        val grassBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = grassGrad }
        val gPath = Path().apply {
            moveTo(0f,sampleTerrainY(0f)-6f)
            for(x in 1 until width) lineTo(x.toFloat(),sampleTerrainY(x.toFloat())-6f)
            lineTo(width.toFloat(),height.toFloat()); lineTo(0f,height.toFloat()); close()
        }
        canvas.save(); canvas.clipPath(gPath)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), grassBg)
        canvas.restore()
        rebuildTerrainHeight(); snapAllWormsToGround(); maybeSpawnCrates()
        terrainDirty = false
    }

    private fun sampleTerrainY(x: Float): Float {
        val xf = x/width.toFloat().coerceAtLeast(1f)
        val base = height*0.68f
        val big   = sin(xf*PI*1.4).toFloat()*height*0.080f
        val med   = sin(xf*PI*4.9).toFloat()*height*0.030f
        val small = sin(xf*PI*10.4).toFloat()*height*0.012f
        return (base+big+med+small).coerceIn(height*0.42f, height*0.84f)
    }

    private fun rebuildTerrainHeight() {
        val bmp = terrainBitmap ?: return
        if (terrainHeight.size!=bmp.width) terrainHeight = IntArray(bmp.width)
        val top = (height*0.25f).toInt()
        for (x in 0 until bmp.width) {
            var found = bmp.height-1
            for (y in top until bmp.height) {
                if (Color.alpha(bmp.getPixel(x,y))>0) { found=y; break }
            }
            terrainHeight[x]=found
        }
    }

    private fun drawBackground(canvas: Canvas, w: Float, h: Float) {
        canvas.drawRect(0f,0f,w,h, skyPaint)
        bulletImg?.let {
            val tileW = it.width.toFloat(); val off = (bulletOffset%tileW).toInt()
            for (col in -1..((w/tileW).toInt()+2)) canvas.drawBitmap(it,col*tileW-off,0f,null)
        } ?: run {
            val grad = android.graphics.LinearGradient(0f,0f,0f,h*0.65f,
                Color.rgb(126,196,255),Color.rgb(180,230,255), android.graphics.Shader.TileMode.CLAMP)
            skyGradPaint.shader = grad; canvas.drawRect(0f,0f,w,h*0.65f,skyGradPaint)
        }
        val back = Path().apply {
            moveTo(0f,h*0.70f); cubicTo(w*.10f,h*.36f,w*.32f,h*.56f,w*.52f,h*.40f)
            cubicTo(w*.64f,h*.28f,w*.82f,h*.56f,w,h*.42f); lineTo(w,h); lineTo(0f,h); close()
        }
        val front = Path().apply {
            moveTo(0f,h*0.78f); cubicTo(w*.18f,h*.50f,w*.38f,h*.66f,w*.55f,h*.50f)
            cubicTo(w*.70f,h*.34f,w*.88f,h*.58f,w,h*.50f); lineTo(w,h); lineTo(0f,h); close()
        }
        canvas.drawPath(back,hillBackPaint); canvas.drawPath(front,hillFrontPaint)
    }

    private fun drawTerrain(canvas: Canvas) { terrainBitmap?.let { canvas.drawBitmap(it,0f,0f,null) } }

    private fun drawTitle(canvas: Canvas, w: Float, h: Float) {
        val panel = RectF(w*0.10f,h*0.14f,w*0.90f,h*0.84f)
        canvas.drawRoundRect(panel,30f,30f,panelPaint); canvas.drawRoundRect(panel,30f,30f,panelBorder)
        logoImg?.let { canvas.drawBitmap(it,null,RectF(w*0.18f,h*0.18f,w*0.82f,h*0.46f),null) }
            ?: canvas.drawText("NOKIA WORMS",w/2f,h*0.32f,titleText)
        titleImg?.let { canvas.drawBitmap(it,null,RectF(w*0.70f,h*0.06f,w*0.96f,h*0.32f),null) }
        wormSprite?.let { spr ->
            val a=RectF(w*0.18f,h*0.49f,w*0.28f,h*0.59f); val b=RectF(w*0.72f,h*0.49f,w*0.82f,h*0.59f)
            canvas.drawBitmap(spr,null,a,null); canvas.drawBitmap(spr,null,b,null)
            canvas.drawOval(a,teamAPaint); canvas.drawOval(b,teamBPaint)
        }
        val pve=RectF(w*0.20f,h*0.53f,w*0.80f,h*0.61f)
        val pvp=RectF(w*0.20f,h*0.64f,w*0.80f,h*0.72f)
        canvas.drawRoundRect(pve,16f,16f,activePaint); canvas.drawRoundRect(pvp,16f,16f,panelPaint)
        canvas.drawRoundRect(pve,16f,16f,panelBorder); canvas.drawRoundRect(pvp,16f,16f,panelBorder)
        canvas.drawText("1 Player vs AI",w/2f,h*0.595f,bodyText)
        canvas.drawText("2 Players",w/2f,h*0.705f,bodyText)
        canvas.drawText("Classic Worms · Multi-worm · 6 Weapons · Particle FX",w/2f,h*0.80f,titleSmallText)
    }

    private fun drawWorms(canvas: Canvas) {
        worms.forEachIndexed { index, worm ->
            // Draw dying worms (death animation: shrink + fade)
            if (worm.deathAge in 0f..0.99f) {
                val t = worm.deathAge
                val scale = (1f - t * 1.1f).coerceAtLeast(0f)
                val alpha = ((1f - t) * 200).toInt().coerceIn(0, 200)
                val cx = worm.x*width; val cy = worm.y; val r = wormRadius * scale
                val deathPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = if(worm.team==0) Color.argb(alpha,255,150,70) else Color.argb(alpha,80,225,110)
                }
                canvas.drawCircle(cx, cy, r, deathPaint)
                return@forEachIndexed
            }
            if (!worm.alive) return@forEachIndexed
            val cx = worm.x*width; val cy = worm.y; val r = wormRadius
            // Walk bobbing
            val bob = if (worm.moveLeft < 1f && abs(moveInput) > 0.01f)
                sin((worm.walkFrame / 6f) * PI.toFloat()) * r * 0.12f else 0f
            wormSprite?.let { spr ->
                canvas.save()
                canvas.translate(cx, cy + bob)
                if (worm.facing == Dir.LEFT) {
                    canvas.scale(-1f, 1f)
                    canvas.drawBitmap(spr, -r*1.3f, -r*1.3f, null)
                } else {
                    canvas.drawBitmap(spr, -r*1.3f, -r*1.3f, null)
                }
                canvas.restore()
                val tint = if(worm.team==0) teamAPaint else teamBPaint
                val oval = RectF(cx-r*1.3f,cy-r*1.3f+bob,cx+r*1.3f,cy+r*1.3f+bob)
                canvas.drawOval(oval, tint)
            } ?: run {
                canvas.drawCircle(cx,cy+bob,r, if(worm.team==0) teamAPaint else teamBPaint)
                canvas.drawCircle(cx,cy+bob,r,panelBorder)
            }
            if (index==activeWormIndex && winnerTeam==null) {
                val mark=RectF(cx-r*1.8f,cy-r*2.6f+bob,cx+r*1.8f,cy-r*1.4f+bob)
                canvas.drawRoundRect(mark,10f,10f,activePaint); canvas.drawRoundRect(mark,10f,10f,panelBorder)
                canvas.drawText("▶",cx-8f,cy-r*2.0f+bob,bodyText)
            }
            // Draw parachute when open
            if (worm.parachuteOpen) {
                val px=cx; val py=cy+bob-r*2.8f
                val pCanopy=Path().apply {
                    moveTo(px,py); cubicTo(px-r*1.4f,py+r*0.3f,px-r*1.8f,py+r*1.0f,px-r*1.6f,py+r*1.4f)
                    lineTo(px+r*1.6f,py+r*1.4f)
                    cubicTo(px+r*1.8f,py+r*1.0f,px+r*1.4f,py+r*0.3f,px,py)
                    close()
                }
                val paraPaint=Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color=Color.argb(160,255,240,255); style=Paint.Style.FILL
                }
                canvas.drawPath(pCanopy,paraPaint)
                val paraBorder=Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color=Color.rgb(200,200,200); style=Paint.Style.STROKE; strokeWidth=1.5f
                }
                canvas.drawPath(pCanopy,paraBorder)
                // Rope lines
                canvas.drawLine(px-r*0.8f,py+r*1.4f,cx-r*0.4f,cy+bob-r*0.5f,paraBorder)
                canvas.drawLine(px+r*0.8f,py+r*1.4f,cx+r*0.4f,cy+bob-r*0.5f,paraBorder)
                canvas.drawLine(px,py+r*1.4f,cx,cy+bob-r*0.5f,paraBorder)
            }
            drawHpBar(canvas, worm, cx, cy+bob)
        }
    }

    private fun drawHpBar(canvas: Canvas, worm: Worm, cx: Float, cy: Float) {
        val bw=wormRadius*3.4f; val bh=12f; val top=cy-wormRadius-20f; val left=cx-bw/2f
        val outer=RectF(left,top,left+bw,top+bh); val fill=(bw-4f)*(worm.hp.coerceIn(0,100)/100f)
        canvas.drawRoundRect(outer,7f,7f,hpBgPaint); canvas.drawRoundRect(outer,7f,7f,panelBorder)
        canvas.drawRoundRect(RectF(left+2f,top+2f,left+2f+fill,top+bh-2f),6f,6f,
            if(worm.hp>40) hpGoodPaint else hpBadPaint)
        canvas.drawText("${worm.name}",left,top-5f,smallText)
    }

    private fun drawCrates(canvas: Canvas) {
        crates.filter{it.active}.forEach { crate ->
            val sz=crateSize; val rect=RectF(crate.x-sz/2f,crate.y-sz/2f,crate.x+sz/2f,crate.y+sz/2f)
            canvas.drawRoundRect(rect,10f,10f,cratePaint); canvas.drawRoundRect(rect,10f,10f,panelBorder)
            canvas.drawLine(rect.left+5f,rect.centerY(),rect.right-5f,rect.centerY(),crateBandPaint)
            canvas.drawLine(rect.centerX(),rect.top+5f,rect.centerX(),rect.bottom-5f,crateBandPaint)
            val lbl=if(crate.type==CrateType.HEALTH) "+" else "?"
            val lblPaint=Paint(Paint.ANTI_ALIAS_FLAG).apply{
                color=if(crate.type==CrateType.HEALTH) Color.rgb(80,200,80) else Color.rgb(255,200,80)
                textSize=sz*0.55f; textAlign=Paint.Align.CENTER
            }
            canvas.drawText(lbl,rect.centerX(),rect.centerY()+sz*0.2f,lblPaint)
        }
    }

    private fun drawAimPreview(canvas: Canvas) {
        if (winnerTeam!=null||explosion!=null||mode==GameMode.TITLE) return
        if (mode==GameMode.PVE && activeTeam()==1) return
        val worm=worms[activeWormIndex]; if(!worm.alive) return
        val dir=if(worm.team==0) 1f else -1f
        val weapon=weapons[selectedWeapon]
        var x=worm.x*width; var y=worm.y-wormRadius
        val rad=Math.toRadians(angleDeg.toDouble())
        var vx=(cos(rad)*weapon.speed*power*dir).toFloat()
        var vy=(-sin(rad)*weapon.speed*power).toFloat()
        // Draw parabolic arc: dots that get smaller/fainter as they travel further
        for (i in 0 until 30) {
            x+=vx*0.08f; y+=vy*0.08f; vx+=wind*0.08f; vy+=600f*weapon.gravityScale*0.08f
            if (x!in 0f..width.toFloat()||y!in 0f..height.toFloat()||isTerrainAt(x,y)) break
            val progress=i.toFloat()/30f
            val alpha=(220*(1f-progress*0.5f)).toInt().coerceIn(40,220)
            val sz=(9f*(1f-progress*0.5f)).coerceAtLeast(3f)
            val dotPaint=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=Color.argb(alpha,255,220,80)}
            canvas.drawCircle(x,y,sz+2f,Paint(Paint.ANTI_ALIAS_FLAG).apply{color=Color.argb(alpha/2,0,0,0)})
            canvas.drawCircle(x,y,sz,dotPaint)
        }
        // Blast radius preview at predicted impact point (bright and visible)
        val blastPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(80, 255, 200, 80); style = Paint.Style.FILL
        }
        val blastBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(220, 255, 255, 80); style = Paint.Style.STROKE; strokeWidth = 3f
        }
        canvas.drawCircle(x, y, weapon.radius, blastPaint)
        canvas.drawCircle(x, y, weapon.radius, blastBorder)
        canvas.drawText("R:${weapon.radius.roundToInt()}", x, y - weapon.radius - 8f,
            Paint().apply{color=Color.argb(230,255,255,255);textSize=18f;textAlign=Paint.Align.CENTER;isFakeBoldText=true})
        // Label
        canvas.drawText("%.0f".format(weapon.radius), x, y - weapon.radius - 6f,
            Paint().apply{color=Color.argb(180,255,255,255);textSize=16f;textAlign=Paint.Align.CENTER})
    }

    private fun drawProjectile(canvas: Canvas) {
        projectiles.forEach { p ->
            val wpnColor = p.weapon.color
            // Draw trail with bright dots
            p.trail.forEachIndexed { i, (tx,ty) ->
                val alpha = ((i.toFloat()/p.trail.size)*220).toInt().coerceIn(20,220)
                val sz = wormRadius*(0.25f+i.toFloat()/p.trail.size*0.55f)
                canvas.drawCircle(tx,ty,sz,Paint(Paint.ANTI_ALIAS_FLAG).apply{color=Color.argb(alpha,255,220,80)})
            }
            // Main projectile: bright circle with dark outline for visibility
            val projSize = max(14f, wormRadius * 0.65f)
            canvas.drawCircle(p.x,p.y,projSize+3f,Paint(Paint.ANTI_ALIAS_FLAG).apply{color=0xFF000000.toInt()})
            canvas.drawCircle(p.x,p.y,projSize,Paint(Paint.ANTI_ALIAS_FLAG).apply{color=wpnColor})
            canvas.drawCircle(p.x-projSize*0.25f,p.y-projSize*0.25f,projSize*0.3f,
                Paint(Paint.ANTI_ALIAS_FLAG).apply{color=Color.argb(180,255,255,255)})
            // Fuse countdown for timed weapons
            if (p.weapon.isTimed && p.timer >= 0f) {
                val remaining = (p.weapon.fuseSecs - p.timer).coerceAtLeast(0f)
                val countdownPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = if(remaining < 1.0f) Color.rgb(255,80,80) else Color.WHITE
                    textSize = 20f; textAlign = Paint.Align.CENTER
                }
                canvas.drawText("%.1f".format(remaining), p.x, p.y - wormRadius*0.8f, countdownPaint)
            }
        }
    }

    private fun drawMuzzleFlash(canvas: Canvas) {
        muzzleFlash?.let { mf ->
            val bmp = mf.frames.getOrNull(mf.frame) ?: return
            val sz = wormRadius*2.2f
            canvas.save()
            canvas.translate(mf.x,mf.y)
            canvas.rotate(mf.angle)
            canvas.drawBitmap(bmp, -sz/2f, -sz/2f, null)
            canvas.restore()
        }
    }

    private fun drawExplosion(canvas: Canvas) {
        explosion?.let { exp ->
            // Expanding shockwave ring
            val ringProgress = (exp.age / 0.35f).coerceAtMost(1f)
            val ringAlpha = ((1f - ringProgress) * 180).toInt().coerceIn(0,180)
            val ringRadius = exp.radius * (0.3f + ringProgress * 1.4f)
            blastRingPaint.color = Color.argb(ringAlpha, 255, 200, 100)
            blastRingPaint.strokeWidth = (8f * (1f - ringProgress) + 2f).coerceAtLeast(2f)
            canvas.drawCircle(exp.x, exp.y, ringRadius, blastRingPaint)
            // Particles
            exp.particles.forEach { part ->
                val alpha = ((1f-part.life/part.maxLife)*220).toInt().coerceIn(0,220)
                val sz = part.size*(1f+part.life/part.maxLife*0.5f)
                canvas.save()
                canvas.translate(part.x,part.y); canvas.rotate(part.rot)
                canvas.drawBitmap(bmpWithAlpha(part.bmp,alpha),-sz/2f,-sz/2f,null)
                canvas.restore()
            }
        }
    }

    private fun drawParticles(canvas: Canvas) {
        particles.forEach { p ->
            val alpha = ((1f-p.life/p.maxLife)*200).toInt().coerceIn(0,200)
            val sz = p.size
            canvas.save()
            canvas.translate(p.x,p.y); canvas.rotate(p.rot)
            canvas.drawBitmap(bmpWithAlpha(p.bmp,alpha),-sz/2f,-sz/2f,null)
            canvas.restore()
        }
    }

    private fun bmpWithAlpha(bmp: Bitmap, alpha: Int): Bitmap {
        val out = Bitmap.createBitmap(bmp.width,bmp.height,Config.ARGB_8888)
        val c = Canvas(out)
        val p = Paint().apply { this.alpha = alpha }
        c.drawBitmap(bmp,0f,0f,p)
        return out
    }

    private fun drawHud(canvas: Canvas, w: Float, h: Float) {
        val panel=RectF(10f,10f,w-10f,124f)
        canvas.drawRoundRect(panel,16f,16f,panelPaint); canvas.drawRoundRect(panel,16f,16f,panelBorder)
        logoSmall?.let { canvas.drawBitmap(it,null,RectF(16f,14f,56f,54f),null) }
        val active=worms.getOrNull(activeWormIndex) ?: return
        val turnLeft=(turnDurationMs-(System.currentTimeMillis()-turnStartedMs)).coerceAtLeast(0L)
        val secs=turnLeft/1000+1
        canvas.drawText("TURN",68f,36f,hudSubText)
        canvas.drawText(if(active.team==0) "TEAM α" else if(mode==GameMode.PVE) "AI β" else "TEAM β",68f,60f,hudText)
        canvas.drawText("Worm: ${active.name}",68f,82f,hudText)
        canvas.drawText("⏱ ${secs}s",68f,104f,
            Paint(hudText).apply{color=if(secs<=10) Color.rgb(255,80,80) else Color.WHITE})
        val wpn=weapons[selectedWeapon]
        canvas.drawText("WEAPON",w*0.28f,36f,hudSubText)
        val iconBg=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=wpn.color}
        canvas.drawRoundRect(RectF(w*0.28f,40f,w*0.28f+32f,72f),8f,8f,iconBg)
        canvas.drawRoundRect(RectF(w*0.28f,40f,w*0.28f+32f,72f),8f,8f,panelBorder)
        canvas.drawText("${wpn.name}",w*0.28f+40f,56f,hudText)
        canvas.drawText("A:${angleDeg.roundToInt()}° P:${(power*100).roundToInt()}%",w*0.28f,80f,hudText)
        canvas.drawText("Move:${(active.moveLeft*100).roundToInt()}% Jump:${active.jumpsLeft}",w*0.28f,104f,hudText)
        val aliveA=worms.count{it.team==0&&it.alive}; val aliveB=worms.count{it.team==1&&it.alive}
        canvas.drawText("TEAM α",w*0.56f,36f,hudSubText)
        canvas.drawText("Alive: $aliveA/3",w*0.56f,80f,hudText)
        canvas.drawText("TEAM β",w*0.56f,104f,hudSubText)
        canvas.drawText("Alive: $aliveB/3",w*0.56f,122f,hudText)
        drawWind(canvas,w*0.86f,58f)
        winnerTeam?.let {
            val msg=if(mode==GameMode.PVE&&it==1) "AI wins — tap to restart"
                    else "Team ${if(it==0) "α" else "β"} wins — tap to restart"
            canvas.drawText(msg,20f,h-22f,bodyText)
        }
    }

    private fun drawWind(canvas: Canvas, x: Float, y: Float) {
        val len=abs(wind)*0.55f+22f; val dir=if(wind>=0f) 1f else -1f
        canvas.drawLine(x,y,x+len*dir,y,windPaint)
        canvas.drawLine(x+len*dir,y,x+(len-10f)*dir,y-6f,windPaint)
        canvas.drawLine(x+len*dir,y,x+(len-10f)*dir,y+6f,windPaint)
        canvas.drawText("Wind ${"%+.1f".format(wind/18f)}",x-8f,y-12f,hudSubText)
    }

    // ── Worm selection screen ─────────────────────────────────────────────────
    private fun drawWormSelect(canvas: Canvas, w: Float, h: Float) {
        // Dim overlay
        canvas.drawRect(0f,0f,w,h, Paint().apply{color=Color.argb(160,0,0,0)})
        // Panel
        val panel=RectF(w*0.10f,h*0.20f,w*0.90f,h*0.75f)
        canvas.drawRoundRect(panel,20f,20f, Paint().apply{color=Color.rgb(30,30,30)})
        canvas.drawRoundRect(panel,20f,20f, Paint().apply{color=Color.rgb(100,100,100);style=Paint.Style.STROKE;strokeWidth=3f})
        val teamLabel=if(selectingTeam==0) "Team α — Choose Your Worm" else if(mode==GameMode.PVE) "AI β — Selecting..." else "Team β — Choose Your Worm"
        canvas.drawText(teamLabel,w/2f,h*0.28f,
            Paint().apply{color=Color.WHITE;textSize=28f;textAlign=Paint.Align.CENTER;isFakeBoldText=true})
        // Draw each alive worm of the selecting team
        worms.filter{it.alive && it.team==selectingTeam}.forEach{ worm ->
            val cx=worm.x*width; val cy=h*0.50f
            val r=wormRadius*1.8f
            // Worm circle
            val bgPaint=Paint(Paint.ANTI_ALIAS_FLAG).apply{
                color=Color.argb(200, if(worm.team==0) 255 else 80, if(worm.team==0) 150 else 225, if(worm.team==0) 70 else 110)
            }
            canvas.drawCircle(cx,cy,r,bgPaint)
            canvas.drawCircle(cx,cy,r, Paint().apply{color=Color.WHITE;style=Paint.Style.STROKE;strokeWidth=2.5f})
            wormSprite?.let{ canvas.drawBitmap(it,null, RectF(cx-r,cy-r,cx+r,cy+r),null) }
            canvas.drawText(worm.name,cx,cy+r*1.6f,
                Paint().apply{color=Color.WHITE;textSize=22f;textAlign=Paint.Align.CENTER})
            canvas.drawText("HP: ${worm.hp}",cx,cy+r*1.6f+24f,
                Paint().apply{color=Color.rgb(180,220,180);textSize=18f;textAlign=Paint.Align.CENTER})
        }
        canvas.drawText("Tap a worm to select",w/2f,h*0.70f,
            Paint().apply{color=Color.rgb(180,180,180);textSize=20f;textAlign=Paint.Align.CENTER})
    }

    // ── Game over screen ──────────────────────────────────────────────────────
    private fun drawGameOver(canvas: Canvas, w: Float, h: Float) {
        canvas.drawRect(0f,0f,w,h, Paint().apply{color=Color.argb(180,0,0,0)})
        val panel=RectF(w*0.08f,h*0.12f,w*0.92f,h*0.85f)
        canvas.drawRoundRect(panel,24f,24f, Paint().apply{color=Color.rgb(25,25,35)})
        canvas.drawRoundRect(panel,24f,24f, Paint().apply{color=Color.rgb(120,120,180);style=Paint.Style.STROKE;strokeWidth=3f})
        val winTeam=winnerTeam ?: return
        val winLabel=when{mode==GameMode.PVE && winTeam==1->"AI β WINS!"; winTeam==0->"Team α Wins!"; else->"Team β Wins!"}
        val winColor=if(winTeam==0) Color.rgb(255,180,80) else Color.rgb(80,255,130)
        canvas.drawText(winLabel,w/2f,h*0.23f,
            Paint().apply{color=winColor;textSize=40f;textAlign=Paint.Align.CENTER;isFakeBoldText=true})
        // Stats
        val aliveA=worms.filter{it.team==0}.map{it.name+" HP:"+it.hp}.joinToString("  ")
        val aliveB=worms.filter{it.team==1}.map{it.name+" HP:"+it.hp}.joinToString("  ")
        canvas.drawText("Team α: $aliveA",w/2f,h*0.36f,
            Paint().apply{color=Color.rgb(255,200,120);textSize=20f;textAlign=Paint.Align.CENTER})
        canvas.drawText("Team β: $aliveB",w/2f,h*0.43f,
            Paint().apply{color=Color.rgb(130,220,150);textSize=20f;textAlign=Paint.Align.CENTER})
        // Kill feed summary
        if (killFeed.isNotEmpty()) {
            canvas.drawText("Recent events:",w/2f,h*0.52f,
                Paint().apply{color=Color.rgb(160,160,160);textSize=18f;textAlign=Paint.Align.CENTER})
            killFeed.takeLast(4).forEachIndexed { i, entry ->
                canvas.drawText(entry.text,w/2f,h*0.57f+i*26f,
                    Paint().apply{color=Color.rgb(220,200,160);textSize=17f;textAlign=Paint.Align.CENTER})
            }
        }
        // Restart button
        val btn=RectF(w*0.25f,h*0.72f,w*0.75f,h*0.80f)
        canvas.drawRoundRect(btn,16f,16f, Paint().apply{color=Color.rgb(60,100,200)})
        canvas.drawRoundRect(btn,16f,16f, Paint().apply{color=Color.WHITE;style=Paint.Style.STROKE;strokeWidth=2f})
        canvas.drawText("TAP TO RESTART",btn.centerX(),btn.centerY()+8f,
            Paint().apply{color=Color.WHITE;textSize=22f;textAlign=Paint.Align.CENTER;isFakeBoldText=true})
    }

    private fun drawKillFeed(canvas: Canvas, w: Float) {
        if (killFeed.isEmpty()) return
        val feedTop = 196f; val lineH = 28f
        killFeed.forEachIndexed { i, entry ->
            val alpha = ((1f - entry.age / 4.0f) * 220).toInt().coerceIn(0, 220)
            val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(alpha / 2, 20, 20, 20); style = Paint.Style.FILL
            }
            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(alpha, 255, 220, 160); textSize = 20f
                textAlign = Paint.Align.CENTER
            }
            val y = feedTop + i * lineH
            canvas.drawRoundRect(RectF(w / 2f - 140f, y - 14f, w / 2f + 140f, y + 10f), 6f, 6f, bgPaint)
            canvas.drawText(entry.text, w / 2f, y + 4f, textPaint)
        }
    }

    // ── Weapon bar (horizontal strip of all 6 weapons) ────────────────────────
    private fun drawWeaponBar(canvas: Canvas, w: Float, h: Float) {
        val barTop = 128f; val barBot = 182f
        val slotW = w / 7f
        weapons.forEachIndexed { i, wpn ->
            val left = slotW * (i + 0.5f); val right = slotW * (i + 1.5f)
            val selected = (i == selectedWeapon)
            val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if(selected) Color.argb(220,255,230,120) else Color.argb(180,80,80,80)
            }
            val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if(selected) Color.rgb(255,180,0) else Color.rgb(120,120,120)
                style = Paint.Style.STROKE; strokeWidth = if(selected) 3f else 1.5f
            }
            canvas.drawRoundRect(RectF(left+4f,barTop+4f,right-4f,barBot-4f),10f,10f,bg)
            canvas.drawRoundRect(RectF(left+4f,barTop+4f,right-4f,barBot-4f),10f,10f,border)
            // Weapon icon: colored circle
            val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = wpn.color }
            canvas.drawCircle((left+right)/2f, barTop+26f, 13f, iconPaint)
            // Timer indicator for timed weapons
            if (wpn.isTimed) {
                val fusePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(255,80,80); strokeWidth=2.5f }
                canvas.drawCircle((left+right)/2f, barTop+26f, 16f, fusePaint)
                canvas.drawText("T",(left+right)/2f-5f,barTop+30f,
                    Paint(Paint.ANTI_ALIAS_FLAG).apply{color=Color.WHITE;textSize=16f})
            }
            // Name label
            canvas.drawText(wpn.name.take(4),(left+right)/2f,barBot-8f,
                Paint(Paint.ANTI_ALIAS_FLAG).apply{
                    color=if(selected) Color.BLACK else Color.rgb(200,200,200); textSize=17f; textAlign=Paint.Align.CENTER
                })
            // Ammo dots
            val maxAmmo=wpn.ammo.coerceAtMost(8)
            val ammoCount=weaponAmmo[i].coerceIn(0,maxAmmo)
            val dotR=3.5f; val dotGap=9f; val totalW=(maxAmmo-1)*dotGap
            val dotX=(left+right)/2f-totalW/2f
            for(d in 0 until maxAmmo) {
                val dotPaint=Paint(Paint.ANTI_ALIAS_FLAG).apply{
                    color=if(d<ammoCount) Color.rgb(255,200,60) else Color.rgb(60,60,60)
                }
                canvas.drawCircle(dotX+d*dotGap, barTop+52f, dotR, dotPaint)
            }
        }
        // Weapon bar underline
        canvas.drawLine(0f,barBot+2f,w,barBot+2f,
            Paint().apply{color=Color.rgb(100,100,100);strokeWidth=1f})
    }

    private fun drawControls(canvas: Canvas, w: Float, h: Float) {
        if (winnerTeam!=null) return
        val mp=movePadRect(w,h); val jp=jumpRect(w,h); val wp=weaponRect(w,h)
        val ar=aimRect(w,h); val fr=fireRect(w,h)

        // ── Virtual joystick (MOVE) ─────────────────────────────────────────────
        // Outer ring
        canvas.drawCircle(mp.centerX(),mp.centerY(),joystickRadius, Paint().apply{
            color=Color.argb(60,80,180,255); style=Paint.Style.FILL
        })
        canvas.drawCircle(mp.centerX(),mp.centerY(),joystickRadius,
            Paint().apply{color=Color.argb(120,100,200,255);style=Paint.Style.STROKE;strokeWidth=2.5f})
        // Inner knob — follows thumb when active
        val knobX=if(joystickActive) joystickTouchX.coerceIn(mp.centerX()-joystickRadius, mp.centerX()+joystickRadius) else mp.centerX()
        val knobY=if(joystickActive) joystickTouchY.coerceIn(mp.centerY()-joystickRadius, mp.centerY()+joystickRadius) else mp.centerY()
        canvas.drawCircle(knobX,knobY,joystickKnobRadius,joystickKnobPaint)
        canvas.drawCircle(knobX,knobY,joystickKnobRadius,
            Paint().apply{color=Color.argb(200,255,255,255);style=Paint.Style.STROKE;strokeWidth=2f})
        canvas.drawText("◀ MOVE ▶",mp.centerX(),mp.centerY()+joystickRadius+18f,
            Paint().apply{color=Color.argb(180,255,255,255);textSize=17f;textAlign=Paint.Align.CENTER})

        // ── Right-side control buttons ────────────────────────────────────────
        // FIRE button — shows cooldown when cooling down
        val cooling = cooldownRemaining > 0L
        val coolFrac = if(cooling) cooldownRemaining.toFloat()/fireCooldown else 0f
        val fireBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if(cooling) Color.rgb(90,90,90) else Color.rgb(230,80,30); style=Paint.Style.FILL
        }
        canvas.drawRoundRect(fr,18f,18f, fireBgPaint)
        canvas.drawRoundRect(fr,18f,18f, fireBtnBorder)
        if (cooling) {
            // Cooldown overlay arc
            val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(255,160,60); style=Paint.Style.STROKE; strokeWidth=5f
            }
            canvas.drawArc(fr.left+8f,fr.top+8f,fr.right-8f,fr.bottom-8f,
                -90f, 360f*(1f-coolFrac), false, arcPaint)
            canvas.drawText("%.1fs".format(cooldownRemaining/1000.0),
                fr.centerX(), fr.centerY()+8f,
                Paint().apply{color=Color.LTGRAY;textSize=24f;textAlign=Paint.Align.CENTER})
        } else {
            canvas.drawText("FIRE",fr.centerX(),fr.centerY()+8f,
                Paint().apply{color=Color.WHITE;textSize=30f;textAlign=Paint.Align.CENTER;isFakeBoldText=true})
        }

        // WEAPON button
        canvas.drawRoundRect(wp,14f,14f,panelPaint); canvas.drawRoundRect(wp,14f,14f,panelBorder)
        val wpn=weapons[selectedWeapon]
        canvas.drawText("WPN",wp.centerX(),wp.centerY()-5f,
            Paint().apply{color=Color.DKGRAY;textSize=18f;textAlign=Paint.Align.CENTER})
        canvas.drawCircle(wp.centerX(),wp.centerY()+22f,10f,
            Paint().apply{color=wpn.color})

        // JUMP button
        canvas.drawRoundRect(jp,14f,14f,activePaint); canvas.drawRoundRect(jp,14f,14f,panelBorder)
        val jumpLabel=if(worms.getOrNull(activeWormIndex)?.hasParachute==true) "PARA" else "JUMP"
        canvas.drawText(jumpLabel,jp.centerX(),jp.centerY()+8f,
            Paint().apply{color=Color.DKGRAY;textSize=22f;textAlign=Paint.Align.CENTER;isFakeBoldText=true})

        // ── Slingshot aim indicator (centered on active worm) ────────────────
        val worm=worms.getOrNull(activeWormIndex) ?: return
        val wx=worm.x*width; val wy=worm.y
        // Angle arc above the worm
        val arcCx=wx; val arcCy=wy-wormRadius*2.8f
        val arcR=wormRadius*2.5f
        val aimArcColor=if(aimActive) 0xFFC8C850.toInt() else 0xFF5078FF.toInt()
        val indicatorPaint=Paint(Paint.ANTI_ALIAS_FLAG).apply{
            color=aimArcColor; style=Paint.Style.STROKE; strokeWidth=4f
        }
        val startAngle=if(worm.team==0) 180f else 0f
        val sweepAngle=(angleDeg/90f)*180f
        canvas.drawArc(arcCx-arcR,arcCy-arcR,arcCx+arcR,arcCy+arcR,
            startAngle, sweepAngle, false, indicatorPaint)
        // Power bar below the arc
        val barW=wormRadius*5f; val barH=8f; val barX=wx-barW/2f; val barY=arcCy+arcR+12f
        canvas.drawRoundRect(RectF(barX,barY,barX+barW,barY+barH),4f,4f,
            Paint().apply{color=Color.rgb(40,40,40)})
        canvas.drawRoundRect(RectF(barX,barY,barX+barW*power,barY+barH),4f,4f,
            Paint().apply{color=Color.argb(200,255,180,60)})
        // Angle + power text
        canvas.drawText("${angleDeg.roundToInt()}°  ${(power*100).roundToInt()}%", wx, barY+barH+20f,
            Paint().apply{color=Color.argb(180,255,255,255);textSize=16f;textAlign=Paint.Align.CENTER})
    }

    private fun updateFrame() {
        val now=System.nanoTime()
        val dt=((now-lastFrameNanos)/1_000_000_000f).coerceAtMost(0.033f)
        lastFrameNanos=now; frameCount++
        if (mode==GameMode.TITLE) return
        bulletOffset+=dt*8f
        // Update shake
        if (shakeAge>=0f) { shakeAge+=dt; if (shakeAge>0.45f) { shakeAge=-1f; shakeIntensity=0f } }
        // Update turn flash
        if (turnFlashAge>=0f) { turnFlashAge+=dt*2.5f; if (turnFlashAge>=1f) turnFlashAge=-1f }
        // Tick kill feed
        killFeed.forEach { it.age += dt }
        killFeed.removeAll { it.age > 4.0f }
        // Update clouds
        cloudOffset += dt * 6f
        // Auto AI worm selection
        if (mode==GameMode.SELECT_WORM && aiSelectAt>0L && System.currentTimeMillis()>=aiSelectAt) {
            val candidates=worms.filter{it.alive && it.team==1}
            if (candidates.isNotEmpty()) {
                val chosen=candidates.random()
                activeWormIndex=worms.indexOf(chosen)
                aiSelectAt=0L; mode=GameMode.PVE; activateCurrentWorm()
            }
        }
        // Animate dying worms
        worms.filter{it.deathAge>=0f}.forEach{ it.deathAge=(it.deathAge+dt/0.9f).coerceAtMost(1f) }
        updateMovement(dt); updatePhysics(dt)
        // Update projectiles and cull exploded/dead ones
        val aliveProj = projectiles.toList()
        projectiles.clear()
        aliveProj.forEach { updateProjectile(it, dt) }
        explosion?.let { updateExplosion(it, dt) }
        updateMuzzleFlash(dt)
        updateParticles(dt)
        if (winnerTeam==null && projectiles.isEmpty() && explosion==null) {
            if (System.currentTimeMillis()-turnStartedMs>=turnDurationMs) nextTurn()
            maybeRunAi(); collectCrates()
        }
    }

    private fun updateMovement(dt: Float) {
        if (moveInput==0f||projectiles.isNotEmpty()||explosion!=null||winnerTeam!=null) return
        val worm=worms[activeWormIndex]
        if (!worm.alive||worm.moveLeft<=0f) return
        val delta=moveInput*dt*0.12f
        val nextX=(worm.x+delta).coerceIn(0.04f,0.96f)
        val currG=groundYAtPx(worm.x*width); val nextG=groundYAtPx(nextX*width)
        if (abs(nextG-currG)>wormRadius*1.15f) return
        worm.x=nextX; worm.moveLeft=(worm.moveLeft-abs(delta)*6f).coerceAtLeast(0f)
        worm.y=groundYAtPx(worm.x*width)-wormRadius
        if (moveInput>0.01f) { worm.facing=Dir.RIGHT } else if(moveInput<-0.01f) { worm.facing=Dir.LEFT }
        worm.walkTimer+=dt; if (worm.walkTimer>0.12f) { worm.walkTimer=0f; worm.walkFrame=(worm.walkFrame+1)%12 }
    }

    private fun updatePhysics(dt: Float) {
        worms.filter{it.alive}.forEach { worm ->
            val targetY=groundYAtPx(worm.x*width)-wormRadius
            if (worm.y<targetY-1f) {
                val maxFall = if (worm.parachuteOpen) 55f else 250f
                val fall=min(targetY-worm.y,dt*maxFall)
                worm.y+=fall; worm.fallDistance+=fall
                // Parachute: open when falling fast and not yet at ground
                if (worm.hasParachute && worm.fallDistance > wormRadius*2f && !worm.parachuteOpen) {
                    worm.parachuteOpen = true
                }
                if (worm.y>=targetY-1f) {
                    worm.y=targetY
                    worm.parachuteOpen = false; worm.hasParachute = false
                    if (worm.fallDistance>36f) {
                        worm.hp=(worm.hp-((worm.fallDistance-36f)/5f).roundToInt()).coerceAtLeast(0)
                        if(worm.hp<=0 && worm.alive) { worm.alive=false; worm.deathAge=0f }
                    }
                    worm.fallDistance=0f
                }
            } else { worm.y=targetY; worm.fallDistance=0f; worm.parachuteOpen=false }
        }
        evaluateWinner()
    }

    private fun updateProjectile(p: Projectile, dt: Float) {
        p.trail.add(Pair(p.x,p.y))
        if (p.trail.size>14) p.trail.removeAt(0)
        p.x+=p.vx*dt; p.y+=p.vy*dt; p.vx+=wind*dt; p.vy+=600f*p.weapon.gravityScale*dt
        // Timed weapon — count down, explode on fuse regardless of position
        if (p.weapon.isTimed) {
            p.timer+=dt
            if (p.timer>=p.weapon.fuseSecs) { explode(p.x,p.y,p.weapon,p.ownerTeam); return }
        }
        // Out of bounds — explode at edge
        if (p.x<0f||p.x>=width||p.y<-40f||p.y>=height) {
            explode(p.x.coerceIn(0f,width.toFloat()),p.y.coerceIn(0f,height.toFloat()),p.weapon,p.ownerTeam); return
        }
        // Terrain collision
        if (isTerrainAt(p.x,p.y)) {
            if (p.weapon.isBouncy && p.bounces<p.weapon.maxBounces) {
                // Bounce: reflect vy, keep some horizontal, increment bounce counter
                p.bounces++
                p.vy=-p.vy*0.42f
                p.vx*=0.78f
                // Push out of terrain
                p.y=(groundYAtPx(p.x)-wormRadius).coerceAtMost(p.y-2f)
            } else { explode(p.x,p.y,p.weapon,p.ownerTeam); return }
        }
        // Hit worm
        val hit=worms.firstOrNull{it.alive && hypot(it.x*width-p.x,it.y-p.y)<wormRadius*1.25f}
        if (hit!=null) explode(p.x,p.y,p.weapon,p.ownerTeam)
    }

    private fun updateExplosion(exp: Explosion, dt: Float) {
        exp.age+=dt
        exp.particles.forEach { p ->
            p.x+=p.vx*dt; p.y+=p.vy*dt
            p.vy+=180f*dt; p.life+=dt; p.rot+=p.rotSpeed*dt
        }
        exp.particles.removeAll { it.life>=it.maxLife }
        if (exp.age>=0.55f && exp.particles.isEmpty()) explosion=null
    }

    private fun updateMuzzleFlash(dt: Float) {
        muzzleFlash?.let { mf ->
            mf.age+=dt
            mf.frame=(mf.age/0.04f).toInt()
            if (mf.frame>=mf.frames.size) muzzleFlash=null
        }
    }

    private fun updateParticles(dt: Float) {
        particles.forEach { p ->
            p.x+=p.vx*dt; p.y+=p.vy*dt; p.vy+=160f*dt; p.life+=dt; p.rot+=p.rotSpeed*dt
        }
        particles.removeAll { it.life>=it.maxLife }
    }

    private fun explode(x: Float, y: Float, weapon: Weapon, ownerTeam: Int = -1) {
        projectiles.removeAll { true }
        explosion=Explosion(x,y,weapon.radius)
        shakeIntensity=1.0f; shakeAge=0f; doHaptic(80L)
        // Spawn particles
        val pList=weapon.blastParticle ?: circles
        repeat(14) {
            val bmp=pList.random()
            val angle=Random.nextFloat()*PI.toFloat()*2
            val speed=60f+Random.nextFloat()*200f
            explosion!!.particles.add(Particle(
                x=x,y=y,
                vx=cos(angle)*speed, vy=sin(angle)*speed-80f,
                bmp=bmp,
                maxLife=0.35f+Random.nextFloat()*0.45f,
                size=wormRadius*(0.5f+Random.nextFloat()*1.2f),
                rot=Random.nextFloat()*360f,
                rotSpeed=Random.nextFloat()*360f-180f
            ))
        }
        carveTerrain(x,y,weapon.radius); damageWorms(x,y,weapon.radius,weapon.damage,ownerTeam)
        terrainDirty=false; rebuildTerrainHeight(); snapAllWormsToGround()
    }

    private fun carveTerrain(cx: Float, cy: Float, radius: Float) {
        val bmp=terrainBitmap ?: return
        val left=max(0,(cx-radius).toInt()); val right=min(bmp.width-1,(cx+radius).toInt())
        val top=max(0,(cy-radius).toInt()); val bottom=min(bmp.height-1,(cy+radius).toInt())
        for (x in left..right) for (y in top..bottom) {
            val dx=x-cx; val dy=y-cy
            if (dx*dx+dy*dy<=radius*radius) bmp.setPixel(x,y,Color.TRANSPARENT)
        }
    }

    private fun damageWorms(cx: Float, cy: Float, radius: Float, maxDmg: Int, ownerTeam: Int = -1) {
        worms.filter{it.alive}.forEach { worm ->
            val dist=hypot(worm.x*width-cx, worm.y-cy)
            if (dist<=radius*1.35f) {
                val dmg=((1f-dist/(radius*1.35f))*maxDmg).roundToInt().coerceAtLeast(6)
                worm.hp=(worm.hp-dmg).coerceAtLeast(0)
                // Knockback: push worm away from explosion center
                if (dist>1f) {
                    val dx=(worm.x*width-cx)/dist; val dy=(worm.y-cy)/dist
                    val knockback=((radius*0.6f)*(1f-dist/radius/1.35f)).coerceAtLeast(0f)
                    worm.x=(worm.x+dx*knockback/width).coerceIn(0.04f,0.96f)
                    worm.y=(worm.y+dy*knockback).coerceAtLeast(0f)
                }
                if (worm.hp<=0 && worm.alive) {
                    worm.alive=false; worm.deathAge=0f
                    // Kill feed
                    val killerTeam=if(ownerTeam>=0) ownerTeam else -1
                    val msg=when {
                        killerTeam<0 -> "${worm.name} fell off!"
                        killerTeam!=worm.team -> "${if(killerTeam==0)"α" else "β"} team eliminated ${worm.name}"
                        else -> "${worm.name} self-destructed"
                    }
                    killFeed.add(KillEntry(msg)); if(killFeed.size>5) killFeed.removeAt(0)
                    // Death burst particles
                    repeat(10) {
                        val bmp=(circles+dirts).random()
                        val angle=Random.nextFloat()*PI.toFloat()*2
                        val speed=80f+Random.nextFloat()*160f
                        particles.add(Particle(
                            x=worm.x*width, y=worm.y,
                            vx=cos(angle)*speed, vy=sin(angle)*speed-120f,
                            bmp=bmp, maxLife=0.6f+Random.nextFloat()*0.4f,
                            size=wormRadius*(0.4f+Random.nextFloat()*0.8f),
                            rot=Random.nextFloat()*360f, rotSpeed=Random.nextFloat()*360f-180f
                        ))
                    }
                }
            }
        }
    }

    private fun nextTurn() {
        moveInput=0f; charging=false; aimActive=false
        val curTeam=activeTeam(); val nextTeam=if(winnerTeam==null) 1-curTeam else curTeam
        val aliveOnTeam=worms.filter{it.alive && it.team==nextTeam}
        if (aliveOnTeam.size>1 && mode!=GameMode.GAME_OVER) {
            // Show worm selection screen
            selectingTeam=nextTeam; mode=GameMode.SELECT_WORM
            aiSelectAt = if(selectingTeam==1 && mode==GameMode.PVE) System.currentTimeMillis()+1200L else 0L
            turnFlashAge=0f; return
        }
        activeWormIndex=nextAliveWormIndex(nextTeam)
        activateCurrentWorm()
    }

    private fun activateCurrentWorm() {
        val worm=worms[activeWormIndex]
        worm.moveLeft=1f; worm.jumpsLeft=1
        angleDeg=if(worm.team==0) 45f else 50f; power=0.62f
        wind=randomWind(); turnStartedMs=System.currentTimeMillis(); aiFireAt=0L
        lastFireMs=0L; turnFlashAge=0f
    }

    private fun nextAliveWormIndex(team: Int): Int {
        val start=(activeWormIndex+1)%worms.size
        for (offset in worms.indices) {
            val idx=(start+offset)%worms.size
            if (worms[idx].alive && worms[idx].team==team) return idx
        }
        return worms.indexOfFirst{it.alive}.coerceAtLeast(0)
    }

    private fun maybeRunAi() {
        if (mode!=GameMode.PVE||activeTeam()!=1||projectiles.isNotEmpty()||
            explosion!=null||winnerTeam!=null) return
        val now=System.currentTimeMillis()
        if (aiFireAt==0L) { aiFireAt=now+900L; chooseAiTurn(); return }
        if (now>=aiFireAt) { fireCurrentWeapon(); aiFireAt=0L }
    }

    private fun chooseAiTurn() {
        val shooter=worms[activeWormIndex]
        val targets=worms.filter{it.alive && it.team!=shooter.team}
        if (targets.isEmpty()) return
        // Pick a random target for unpredictability
        val target=targets.random()
        val dx=(target.x-shooter.x)*width
        val dy=target.y-shooter.y
        val dist=hypot(dx,dy)
        // Pick weapon with ammo, prefer appropriate weapon for distance
        val preferredForDist=when {
            dist>width*0.40f-> listOf(2,0,5)  // Missile, Bazooka, Dynamite
            dist>width*0.22f-> listOf(0,1,3)  // Bazooka, Grenade, Mortar
            dist<width*0.14f-> listOf(4,1,3)  // Shotgun, Grenade, Mortar (close)
            else-> listOf(3,1,5)               // Mortar, Grenade, Dynamite
        }
        selectedWeapon=preferredForDist.firstOrNull{ weaponAmmo[it]>0 }
            ?: (0 until weapons.size).firstOrNull{ weaponAmmo[it]>0 } ?: 0
        val wpn=weapons[selectedWeapon]
        // Lead the target: aim slightly ahead based on wind and distance
        val windBonus=(wind/42f)*(dist/width)*12f
        angleDeg=Math.toDegrees(atan2(dy.toDouble(),dx.toDouble().coerceAtLeast(1.0))).toFloat()
            .coerceIn(8f,84f)+(if(dx<0) 180f else 0f)
        angleDeg=angleDeg.coerceIn(8f,84f)
        // Power based on distance and weapon gravity
        val basePower=(dist/(wpn.speed*0.68f)).coerceIn(0.30f,1.0f)
        power=(basePower+(Random.nextFloat()-0.5f)*0.10f).coerceIn(0.30f,1.0f)
        // Small chance to jump/toggle parachute
        if (Random.nextFloat()<0.18f && shooter.jumpsLeft>0) jumpCurrentWorm()
        else if (Random.nextFloat()<0.10f && shooter.hasParachute) { shooter.parachuteOpen=!shooter.parachuteOpen }
    }

    private fun collectCrates() {
        val active=worms[activeWormIndex]
        crates.filter{it.active}.forEach { crate ->
            if (hypot(active.x*width-crate.x,active.y-crate.y)<=wormRadius+crateSize*0.55f) {
                crate.active=false
                when(crate.type) {
                    CrateType.HEALTH->active.hp=(active.hp+25).coerceAtMost(100)
                    CrateType.WEAPON->selectedWeapon=(selectedWeapon+1)%weapons.size
                }
            }
        }
    }

    private fun maybeSpawnCrates() {
        if (crates.isNotEmpty()) return
        listOf(Pair(width*0.45f,CrateType.HEALTH),Pair(width*0.57f,CrateType.WEAPON))
            .forEach { (x,type)->crates.add(SupplyCrate(x,groundYAtPx(x)-crateSize/2f,type)) }
    }

    private fun evaluateWinner() {
        val aliveA=worms.any{it.alive && it.team==0}
        val aliveB=worms.any{it.alive && it.team==1}
        winnerTeam=when {
            aliveA&&aliveB->null; aliveA->0; aliveB->1
            else->1-activeTeam()
        }
        if (winnerTeam!=null) mode=GameMode.GAME_OVER
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val w=width.toFloat().coerceAtLeast(1f); val h=height.toFloat().coerceAtLeast(1f)
        if (mode==GameMode.TITLE) {
            if (event.action==MotionEvent.ACTION_DOWN)
                mode=if(event.y<h*0.62f) GameMode.PVE else GameMode.PVP
            return true
        }
        if (winnerTeam!=null || mode==GameMode.GAME_OVER) {
            if(event.action==MotionEvent.ACTION_DOWN) { mode=GameMode.TITLE; killFeed.clear() }
            return true
        }
        if (mode==GameMode.SELECT_WORM) {
            if (event.action==MotionEvent.ACTION_DOWN) {
                val tx=event.x; val ty=event.y
                for (i in worms.indices) {
                    val w=worms[i]
                    if (!w.alive || w.team!=selectingTeam) continue
                    val cx=w.x*width; val cy=w.y
                    if (hypot(tx-cx,ty-cy)<wormRadius*2f) {
                        activeWormIndex=i; mode=GameMode.PVE; activateCurrentWorm(); break
                    }
                }
            }
            return true
        }
        if (projectiles.isNotEmpty()||explosion!=null) return true
        if (mode==GameMode.PVE && activeTeam()==1) return true

        val jp=jumpRect(w,h); val wp=weaponRect(w,h); val fr=fireRect(w,h); val ar=aimRect(w,h)
        val action=event.actionMasked
        val pid=event.getPointerId(event.actionIndex)

        // ── Handle each pointer ────────────────────────────────────────────
        // Slingshot aiming: any touch outside buttons starts aiming
        // Drag = set angle (direction from worm to touch) + power (distance from worm)
        // Release = fire
        when(action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val x=event.getX(event.findPointerIndex(pid))
                val y=event.getY(event.findPointerIndex(pid))
                val inJoystick = movePadRect(w,h).contains(x,y)
                val inJump = jp.contains(x,y)
                val inWeapon = wp.contains(x,y)
                val inFire = fr.contains(x,y)

                if (!joystickActive && inJoystick) {
                    // Joystick touch
                    joystickActive=true; joystickPointerId=pid
                    val mp=movePadRect(w,h); joystickCenterX=mp.centerX(); joystickCenterY=mp.centerY()
                    joystickTouchX=x; joystickTouchY=y; updateJoystick()
                } else if (!inJoystick && !inJump && !inWeapon && !inFire) {
                    // Slingshot aim start: touch outside all buttons
                    aimPointerId=pid; aimStartX=x; aimStartY=y; isSlingshotAiming=true
                    updateSlingshotAim(x, y)
                } else if (inJump) jumpCurrentWorm()
                else if (inWeapon) selectedWeapon=(selectedWeapon+1)%weapons.size
                else if (inFire) fireCurrentWeapon()
            }

            MotionEvent.ACTION_MOVE -> {
                if (joystickActive) {
                    val idx=event.findPointerIndex(joystickPointerId)
                    if (idx>=0) { joystickTouchX=event.getX(idx); joystickTouchY=event.getY(idx); updateJoystick() }
                }
                if (isSlingshotAiming && aimPointerId>=0) {
                    val idx=event.findPointerIndex(aimPointerId)
                    if (idx>=0) updateSlingshotAim(event.getX(idx), event.getY(idx))
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                if (pid==joystickPointerId) {
                    joystickActive=false; joystickPointerId=-1; moveInput=0f
                }
                if (pid==aimPointerId) {
                    if (isSlingshotAiming) fireCurrentWeapon()
                    isSlingshotAiming=false; aimPointerId=-1
                }
            }
        }
        invalidate(); return true
    }

    private fun updateSlingshotAim(tx: Float, ty: Float) {
        val worm=worms.getOrNull(activeWormIndex) ?: return
        if (!worm.alive) return
        val wx=worm.x*width; val wy=worm.y
        val dx=tx-wx; val dy=ty-wy
        val dist=hypot(dx,dy).coerceAtLeast(1f)
        // Power from drag distance (max 250px = full power)
        power=(dist/250f).coerceIn(0.18f, 1.0f)
        // Angle: drag direction determines angle. If worm faces right:
        // drag RIGHT-UP = aim up-right, drag LEFT-DOWN = aim down-left (backward)
        val rawAngle=Math.toDegrees(atan2((-dy).toDouble(), dx.toDouble().coerceAtLeast(1.0))).toFloat().coerceIn(8f, 84f)
        // For left-facing team, flip: drag RIGHT-UP = aim up-left (backward)
        angleDeg=if(worm.facing==Dir.LEFT) (180f-rawAngle).coerceIn(8f,172f) else rawAngle
        aimActive=true
    }

    private fun updateJoystick() {
        if (!joystickActive) return
        val dx=joystickTouchX-joystickCenterX; val dy=joystickTouchY-joystickCenterY
        val dist=hypot(dx,dy).coerceAtLeast(1f)
        val clampedDist=min(dist,joystickRadius)
        moveInput=(clampedDist/joystickRadius)*(dx/dist).coerceIn(-1f,1f)
        // Update worm facing direction from joystick X
        val worm=worms.getOrNull(activeWormIndex) ?: return
        if (moveInput>0.05f) worm.facing=Dir.RIGHT else if(moveInput<-0.05f) worm.facing=Dir.LEFT
    }

    private fun updateAimFromJoystick(tx: Float, ty: Float) {
        val worm=worms[activeWormIndex]; if(!worm.alive) return
        // Angle: vertical delta from touch start to worm
        val dy=(worm.y-wormRadius)-ty
        // Horizontal delta from touch start determines power
        val powerDx=(tx-aimStartX)
        val dir=if(worm.team==0) 1f else -1f
        // Angle: 0°=flat, 90°=straight up
        angleDeg=Math.toDegrees(atan2(dy.toDouble().coerceAtLeast(1.0), abs(powerDx).toDouble().coerceAtLeast(1.0))).toFloat().coerceIn(8f,84f)
        // Power from horizontal displacement
        power=(abs(powerDx)/(width*0.32f)).coerceIn(0.18f,1f)
    }

    private fun updateAim(tx: Float, ty: Float) {
        val worm=worms[activeWormIndex]
        val dx=tx-worm.x*width; val dy=(worm.y-wormRadius)-ty
        val facingDx=if(worm.team==0) dx.coerceAtLeast(1f) else (-dx).coerceAtLeast(1f)
        angleDeg=Math.toDegrees(atan2(dy.toDouble(),facingDx.toDouble())).toFloat().coerceIn(8f,84f)
        if (charging) power=(hypot(dx,(worm.y-wormRadius)-ty)/(width*0.42f)).coerceIn(0.18f,1f)
    }

    private fun jumpCurrentWorm() {
        val worm=worms[activeWormIndex]
        if (!worm.alive) return
        val inAir = worm.y < groundYAtPx(worm.x*width)-wormRadius-2f
        if (inAir) {
            // Open parachute if not yet open
            if (!worm.parachuteOpen && worm.hasParachute) { worm.parachuteOpen = true; return }
            return
        }
        if (worm.jumpsLeft<=0) return
        worm.x=(worm.x+0.04f*(if(worm.team==0) 1f else -1f)).coerceIn(0.04f,0.96f)
        worm.y-=wormRadius*1.4f; worm.jumpsLeft--; worm.moveLeft=(worm.moveLeft-0.25f).coerceAtLeast(0f)
        worm.hasParachute=true; worm.parachuteOpen=false
    }

    private fun fireCurrentWeapon() {
        val worm=worms[activeWormIndex]; if (!worm.alive) return
        // Auto-skip weapons with no ammo
        if (weaponAmmo[selectedWeapon] <= 0) {
            val next = (selectedWeapon+1 until weapons.size).firstOrNull { weaponAmmo[it] > 0 }
                ?: (0 until selectedWeapon).firstOrNull { weaponAmmo[it] > 0 }
            if (next != null) selectedWeapon = next else return
        }
        val wpn=weapons[selectedWeapon]; val dir=if(worm.team==0) 1f else -1f
        // Check cooldown
        if (cooldownRemaining > 0L) return
        val rad=Math.toRadians(angleDeg.toDouble())
        val baseX=worm.x*width; val baseY=worm.y-wormRadius
        // Shotgun: fire pellets in a spread
        if (wpn.pellets > 1) {
            for (p in 0 until wpn.pellets) {
                val spread = (p - wpn.pellets/2f) * 0.10f
                val pRad = rad + spread.toDouble()
                val pSpeed = wpn.speed * power
                projectiles.add(Projectile(baseX, baseY,
                    (cos(pRad)*pSpeed*dir).toFloat(),
                    (-sin(pRad)*pSpeed).toFloat(), wpn, worm.team,
                    timer=if(wpn.isTimed) 0f else -1f, bounces=0))
            }
            if (projectiles.isEmpty()) projectiles.add(Projectile(baseX,baseY,
                (cos(rad)*wpn.speed*power*dir).toFloat(),
                (-sin(rad)*wpn.speed*power).toFloat(),wpn,worm.team,
                timer=if(wpn.isTimed) 0f else -1f, bounces=0))
        } else {
            projectiles.add(Projectile(baseX,baseY,
                (cos(rad)*wpn.speed*power*dir).toFloat(),
                (-sin(rad)*wpn.speed*power).toFloat(),wpn,worm.team,
                timer=if(wpn.isTimed) 0f else -1f, bounces=0))
        }
        weaponAmmo[selectedWeapon]--
        lastFireMs = System.currentTimeMillis()
        // Muzzle flash
        muzzles?.let { mfs ->
            if (mfs.isNotEmpty()) {
                muzzleFlash=MuzzleFlash(worm.x*width,worm.y-wormRadius,
                    angleDeg+if(worm.team==1) 180f else 0f, mfs)
            }
        }
        lastFrameNanos=System.nanoTime(); doHaptic(40L); invalidate()
    }

    private fun activeTeam()=worms.getOrNull(activeWormIndex)?.team ?: 0

    private fun snapAllWormsToGround() {
        worms.filter{it.alive}.forEach{ worm->worm.y=groundYAtPx(worm.x*width)-wormRadius }
    }

    private fun groundYAtPx(px: Float): Float {
        if (terrainHeight.isEmpty()) return height*0.70f
        val idx=px.roundToInt().coerceIn(0,terrainHeight.lastIndex)
        return terrainHeight[idx].toFloat()
    }

    private fun isTerrainAt(x: Float, y: Float): Boolean {
        val bmp=terrainBitmap ?: return false
        val ix=x.roundToInt().coerceIn(0,bmp.width-1); val iy=y.roundToInt().coerceIn(0,bmp.height-1)
        return Color.alpha(bmp.getPixel(ix,iy))>0
    }

    // New optimized control layout:
    // - Bottom-left: virtual joystick (MOVE)
    // - Right side: FIRE (large), WEAPON, JUMP stacked
    // - Upper-right area: AIM zone
    private val joystickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(100, 100, 100, 200); style = Paint.Style.FILL
    }
    private val joystickKnobPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(180, 200, 200, 255); style = Paint.Style.FILL
    }
    private val joystickBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(160, 255, 255, 255); style = Paint.Style.STROKE; strokeWidth = 3f
    }
    private val fireBtnPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(230, 80, 30); style = Paint.Style.FILL
    }
    private val fireBtnBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(255, 140, 60); style = Paint.Style.STROKE; strokeWidth = 3f
    }
    // Track joystick state
    private var joystickActive = false
    private var joystickPointerId = -1
    private var joystickCenterX = 0f
    private var joystickCenterY = 0f
    private var joystickTouchX = 0f
    private var joystickTouchY = 0f
    private val joystickRadius = 70f   // outer radius
    private val joystickKnobRadius = 28f // inner knob radius
    // Track aim state
    private var aimPointerId = -1
    private var aimStartX = 0f
    private var aimStartY = 0f
    // Slingshot aiming state
    private var isSlingshotAiming = false

    private fun movePadRect(w: Float, h: Float) = RectF(20f, h-200f, 20f+joystickRadius*2, h-200f+joystickRadius*2)
    private fun jumpRect(w: Float, h: Float)   = RectF(w-135f, h-350f, w-20f, h-270f)
    private fun weaponRect(w: Float, h: Float) = RectF(w-135f, h-260f, w-20f, h-180f)
    private fun fireRect(w: Float, h: Float)   = RectF(w-135f, h-170f, w-20f, h-80f)
    private fun aimRect(w: Float, h: Float)    = RectF(w*0.38f, 190f, w-20f, h-365f)

    private fun randomWind()=Random.nextFloat()*84f-42f

    private fun loadBitmap(path: String): Bitmap? = runCatching {
        context.assets.open(path).use { BitmapFactory.decodeStream(it) }
    }.getOrNull()
}
