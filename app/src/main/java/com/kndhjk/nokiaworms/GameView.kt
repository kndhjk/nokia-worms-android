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
    private enum class GameMode { TITLE, PVP, PVE }
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

    private val turnDurationMs get() = 25_000L
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
        muzzleFlash = null; particles.clear(); crates.clear()
        lastFrameNanos = System.nanoTime(); turnStartedMs = System.currentTimeMillis()
        aiFireAt = 0L; terrainDirty = true; terrainBitmap = null; terrainCanvas = null
        terrainHeight = IntArray(0); bulletOffset = 0f; frameCount = 0
        weaponAmmo = IntArray(weapons.size) { weapons[it].ammo }
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
        else {
            drawParticles(canvas)
            drawCrates(canvas); drawWorms(canvas)
            drawAimPreview(canvas); drawProjectile(canvas)
            drawMuzzleFlash(canvas); drawExplosion(canvas)
            drawHud(canvas, w, h); drawWeaponBar(canvas, w, h); drawControls(canvas, w, h)
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
        canvas.drawPath(path, terrainPaint)
        tileDirtPaint.shader?.let {
            canvas.save(); canvas.clipPath(path)
            canvas.drawRect(0f,0f,width.toFloat(),height.toFloat(),tileDirtPaint); canvas.restore()
        }
        val gPath = Path().apply {
            moveTo(0f,sampleTerrainY(0f)-5f)
            for(x in 2 until width step 2) lineTo(x.toFloat(),sampleTerrainY(x.toFloat())-5f)
        }
        canvas.drawPath(gPath, grassPaint)
        tileGrassPaint.shader?.let {
            canvas.save(); canvas.clipPath(gPath)
            canvas.drawRect(0f,0f,width.toFloat(),height.toFloat(),tileGrassPaint); canvas.restore()
        }
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
        if (winnerTeam!=null||projectiles.isNotEmpty()||explosion!=null||mode==GameMode.TITLE) return
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
            val alpha=(120*(1f-progress*0.7f)).toInt().coerceIn(15,120)
            val sz=(4f*(1f-progress*0.6f)).coerceAtLeast(1.5f)
            val dotPaint=Paint(Paint.ANTI_ALIAS_FLAG).apply{
                color=Color.argb(alpha,
                    if(weapon.isTimed) 255 else (weapon.color shr 16 and 0xff),
                    if(weapon.isTimed) 180 else (weapon.color shr 8 and 0xff),
                    if(weapon.isTimed) 80 else (weapon.color and 0xff))
            }
            canvas.drawCircle(x,y,sz,dotPaint)
        }
    }

    private fun drawProjectile(canvas: Canvas) {
        projectiles.forEach { p ->
            projectilePaint.color = p.weapon.color
            // Draw trail
            p.trail.forEachIndexed { i, (tx,ty) ->
                val alpha = ((i.toFloat()/p.trail.size)*160).toInt().coerceIn(0,160)
                projectilePaint.alpha = alpha
                val sz = wormRadius*(0.15f+i.toFloat()/p.trail.size*0.5f)
                canvas.drawCircle(tx,ty,sz,projectilePaint)
            }
            projectilePaint.alpha = 255
            canvas.drawCircle(p.x,p.y,wormRadius*0.40f,projectilePaint)
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
        canvas.drawText("⏱ ${secs}s",68f,104f,hudText)
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
        canvas.drawOval(mp,panelPaint); canvas.drawOval(mp,panelBorder)
        canvas.drawText("◀ MOVE ▶",mp.centerX()-28f,mp.centerY()+8f,smallText)
        canvas.drawRoundRect(jp,14f,14f,activePaint); canvas.drawRoundRect(jp,14f,14f,panelBorder)
        canvas.drawText("JUMP",jp.left+18f,jp.centerY()+8f,smallText)
        canvas.drawRoundRect(wp,14f,14f,panelPaint); canvas.drawRoundRect(wp,14f,14f,panelBorder)
        canvas.drawText("WEAPON",wp.left+8f,wp.centerY()+8f,smallText)
        canvas.drawRoundRect(ar,18f,18f,panelPaint); canvas.drawRoundRect(ar,18f,18f,panelBorder)
        canvas.drawText("DRAG AIM",ar.left+20f,ar.centerY()+8f,smallText)
        canvas.drawRoundRect(fr,14f,14f,activePaint); canvas.drawRoundRect(fr,14f,14f,panelBorder)
        canvas.drawText("🔥 FIRE",fr.left+22f,fr.centerY()+8f,bodyText)
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
            if (p.timer>=p.weapon.fuseSecs) { explode(p.x,p.y,p.weapon); return }
        }
        // Out of bounds — explode at edge
        if (p.x<0f||p.x>=width||p.y<-40f||p.y>=height) {
            explode(p.x.coerceIn(0f,width.toFloat()),p.y.coerceIn(0f,height.toFloat()),p.weapon); return
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
            } else { explode(p.x,p.y,p.weapon); return }
        }
        // Hit worm
        val hit=worms.firstOrNull{it.alive && hypot(it.x*width-p.x,it.y-p.y)<wormRadius*1.25f}
        if (hit!=null) explode(p.x,p.y,p.weapon)
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

    private fun explode(x: Float, y: Float, weapon: Weapon) {
        projectiles.removeAll { true }
        explosion=Explosion(x,y,weapon.radius)
        shakeIntensity=1.0f; shakeAge=0f
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
        carveTerrain(x,y,weapon.radius); damageWorms(x,y,weapon.radius,weapon.damage)
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

    private fun damageWorms(cx: Float, cy: Float, radius: Float, maxDmg: Int) {
        worms.filter{it.alive}.forEach { worm ->
            val dist=hypot(worm.x*width-cx, worm.y-cy)
            if (dist<=radius*1.35f) {
                val dmg=((1f-dist/(radius*1.35f))*maxDmg).roundToInt().coerceAtLeast(6)
                worm.hp=(worm.hp-dmg).coerceAtLeast(0)
                if (worm.hp<=0 && worm.alive) {
                    worm.alive=false; worm.deathAge=0f
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
        activeWormIndex=nextAliveWormIndex(nextTeam)
        val worm=worms[activeWormIndex]
        worm.moveLeft=1f; worm.jumpsLeft=1
        angleDeg=if(worm.team==0) 45f else 50f; power=0.62f
        wind=randomWind(); turnStartedMs=System.currentTimeMillis(); aiFireAt=0L
        turnFlashAge=0f  // trigger turn flash
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
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val w=width.toFloat().coerceAtLeast(1f); val h=height.toFloat().coerceAtLeast(1f)
        if (mode==GameMode.TITLE) {
            if (event.action==MotionEvent.ACTION_DOWN)
                mode=if(event.y<h*0.62f) GameMode.PVE else GameMode.PVP
            return true
        }
        if (winnerTeam!=null) { if(event.action==MotionEvent.ACTION_DOWN) mode=GameMode.TITLE; return true }
        if (projectiles.isNotEmpty()||explosion!=null) return true
        if (mode==GameMode.PVE && activeTeam()==1) return true
        val mp=movePadRect(w,h); val jp=jumpRect(w,h)
        val wp=weaponRect(w,h); val ar=aimRect(w,h); val fr=fireRect(w,h)
        when(event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                when {
                    mp.contains(event.x,event.y)->
                        moveInput=((event.x-mp.centerX())/(mp.width()/2f)).coerceIn(-1f,1f)
                    jp.contains(event.x,event.y)&&event.actionMasked==MotionEvent.ACTION_DOWN->jumpCurrentWorm()
                    wp.contains(event.x,event.y)&&event.actionMasked==MotionEvent.ACTION_DOWN->
                        selectedWeapon=(selectedWeapon+1)%weapons.size
                    fr.contains(event.x,event.y)&&event.actionMasked==MotionEvent.ACTION_DOWN->fireCurrentWeapon()
                    ar.contains(event.x,event.y)->{
                        aimActive=true; updateAim(event.x,event.y)
                        if (!charging&&event.actionMasked==MotionEvent.ACTION_DOWN) {
                            charging=true; chargeStartedMs=System.currentTimeMillis()
                        }
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL->{
                if (charging&&aimActive) {
                    power=(0.20f+(System.currentTimeMillis()-chargeStartedMs).coerceAtMost(1600L)/1600f*0.80f).coerceIn(0.20f,1f)
                }
                moveInput=0f; charging=false; aimActive=false
            }
        }
        invalidate(); return true
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
        // Muzzle flash
        muzzles?.let { mfs ->
            if (mfs.isNotEmpty()) {
                muzzleFlash=MuzzleFlash(worm.x*width,worm.y-wormRadius,
                    angleDeg+if(worm.team==1) 180f else 0f, mfs)
            }
        }
        lastFrameNanos=System.nanoTime(); invalidate()
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

    private fun movePadRect(w: Float,h: Float)=RectF(24f,h-150f,174f,h-20f)
    private fun jumpRect(w: Float,h: Float)=RectF(w-330f,h-150f,w-220f,h-80f)
    private fun weaponRect(w: Float,h: Float)=RectF(w-200f,h-150f,w-60f,h-80f)
    private fun aimRect(w: Float,h: Float)=RectF(w-290f,h-340f,w-24f,h-180f)
    private fun fireRect(w: Float,h: Float)=RectF(w-210f,h-72f,w-24f,h-18f)

    private fun randomWind()=Random.nextFloat()*84f-42f

    private fun loadBitmap(path: String): Bitmap? = runCatching {
        context.assets.open(path).use { BitmapFactory.decodeStream(it) }
    }.getOrNull()
}
