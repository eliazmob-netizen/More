package com.example.simulation

import android.graphics.Bitmap
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import android.os.SystemClock
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.atomic.AtomicBoolean
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

class SeaRenderer(
    private val onTelemetry: (fpsStats: FpsStats, solarElDeg: Double, currentHours: Double) -> Unit,
    private val onError: (String?) -> Unit,
) : GLSurfaceView.Renderer {

    data class RenderConfig(
        val state: SeaState = SeaState(),
        val timeRevision: Long = 0L,
        val qualityRevision: Long = 0L,
    )

    @Volatile
    private var config: RenderConfig = RenderConfig()

    @Volatile
    var yawG: Double = 0.0
    @Volatile
    var pitG: Double = 0.0
    @Volatile
    var roll: Double = 0.0
    @Volatile
    var tiltTx: Double = 0.0
    @Volatile
    var tiltTy: Double = 0.0
    @Volatile
    var hasGyroTilt: Boolean = false
    @Volatile
    var yawD: Double = 0.0
    @Volatile
    var pitD: Double = 0.0

    private val dirtyLut = AtomicBoolean(true)
    private val pendingSpawnNearShip = AtomicBoolean(false)
    private var lastTimeRevision = -1L
    private var lastQualityRevision = -1L

    private var p1 = 0
    private var p2 = 0
    private var vbo = 0
    private var lutTex = 0
    private var fbTex = 0
    private var fbo = 0
    private var ovTex = 0
    private var nrmTex = 0

    private val u1 = HashMap<String, Int>()
    private val u2 = HashMap<String, Int>()

    private var ready = false
    private var cvWidth = 2
    private var cvHeight = 2
    private var sw = 2
    private var sh = 2
    private var qs = 0.6f
    private var cap = 30
    private var bench = 0
    private var ema = 33.0
    private var frameCount = 0
    private var ovClearedEmpty = false

    private var lastFrameStartMs = 0L
    private var lutUpdatedMs = -9000L
    private var statusUpdatedMs = -9000L
    private var lastCamEff = -999.0
    private var lastOvCam: Double? = null

    private var simHours = 12.0
    private var flash = 0.0
    private var bolt = 0.5
    private var tiltX = 0.0
    private var tiltY = 0.0

    private val lutBuf = ByteArray(AtmosphereMath.LW * AtmosphereMath.LH * 4)
    private val bkBuf = ByteArray(AtmosphereMath.LW * AtmosphereMath.LH * 4)
    private val lutDirectBuf: ByteBuffer = ByteBuffer.allocateDirect(lutBuf.size).order(ByteOrder.nativeOrder())
    private var bkRow = AtmosphereMath.LH
    private var bkParams: FrameParams? = null
    private var bkSun: DoubleArray? = null
    private var bkEx = 1.0
    private var bkAmb = doubleArrayOf(0.1, 0.1, 0.1)

    private var curParams: FrameParams? = null
    private var lightModel: LightModel? = null

    private val birds = MutableList(6) { GullState.spawn() }
    private var ship: ShipState? = null
    private var nextAutoShipMs = 0L
    private val overlayRenderer = OverlayCanvasRenderer()

    fun updateConfig(state: SeaState, timeRevision: Long, qualityRevision: Long) {
        config = RenderConfig(state, timeRevision, qualityRevision)
        dirtyLut.set(true)
    }

    fun requestSpawnShip(fast: Boolean) {
        pendingSpawnNearShip.set(true)
        val current = ship
        if (current != null) current.fast = fast
    }

    fun resetDragLook() {
        yawD = 0.0
        pitD = 0.0
    }

    fun reportExternalError(message: String) {
        ready = false
        onError(message)
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        try {
            val vs = compileShader(GLES20.GL_VERTEX_SHADER, SeaShaders.VERTEX_SHADER)
            val fs1 = compileShader(GLES20.GL_FRAGMENT_SHADER, SeaShaders.SCENE_FRAGMENT_SHADER)
            val fs2 = compileShader(GLES20.GL_FRAGMENT_SHADER, SeaShaders.GLASS_FRAGMENT_SHADER)

            p1 = linkProgram(vs, fs1)
            p2 = linkProgram(vs, fs2)
            GLES20.glDeleteShader(vs)
            GLES20.glDeleteShader(fs1)
            GLES20.glDeleteShader(fs2)

            val quad = floatArrayOf(-1f, -1f, 3f, -1f, -1f, 3f)
            val qb: FloatBuffer = ByteBuffer.allocateDirect(quad.size * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
                .put(quad)
            qb.position(0)

            val bufs = IntArray(1)
            GLES20.glGenBuffers(1, bufs, 0)
            vbo = bufs[0]
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo)
            GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, quad.size * 4, qb, GLES20.GL_STATIC_DRAW)
            GLES20.glEnableVertexAttribArray(0)
            GLES20.glVertexAttribPointer(0, 2, GLES20.GL_FLOAT, false, 0, 0)

            u1.clear()
            listOf(
                "uRes", "uTilt", "uTime", "uCloud", "uWind", "uRain", "uFog", "uFlash",
                "uMoonPh", "uMoonL", "uStar", "uWDir", "uStarRot", "uBow", "uBolt", "uNight",
                "uObj", "uLook", "uLH", "uOv", "uNrm", "uSun", "uSunL", "uSunD", "uMoon",
                "uAmb", "uPole", "uSky",
            ).forEach { name -> u1[name] = GLES20.glGetUniformLocation(p1, name) }

            u2.clear()
            listOf("uScene", "uRes", "uSceneRes", "uTime", "uRain", "uFog", "uDrops")
                .forEach { name -> u2[name] = GLES20.glGetUniformLocation(p2, name) }

            val texs = IntArray(4)
            GLES20.glGenTextures(4, texs, 0)
            lutTex = texs[0]
            fbTex = texs[1]
            ovTex = texs[2]
            nrmTex = texs[3]

            val fbos = IntArray(1)
            GLES20.glGenFramebuffers(1, fbos, 0)
            fbo = fbos[0]

            fun setupClampLinear(unit: Int, tex: Int) {
                GLES20.glActiveTexture(unit)
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex)
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
            }

            setupClampLinear(GLES20.GL_TEXTURE0, lutTex)
            setupClampLinear(GLES20.GL_TEXTURE1, fbTex)
            setupClampLinear(GLES20.GL_TEXTURE2, ovTex)

            val nrmBytes = AtmosphereMath.makeNrm()
            val nrmBuf = ByteBuffer.allocateDirect(nrmBytes.size).order(ByteOrder.nativeOrder()).put(nrmBytes)
            nrmBuf.position(0)
            GLES20.glActiveTexture(GLES20.GL_TEXTURE3)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, nrmTex)
            GLES20.glTexImage2D(
                GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, 256, 256, 0,
                GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, nrmBuf,
            )
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_REPEAT)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_REPEAT)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glGenerateMipmap(GLES20.GL_TEXTURE_2D)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR_MIPMAP_LINEAR)

            val extensions = GLES20.glGetString(GLES20.GL_EXTENSIONS) ?: ""
            if (extensions.contains("GL_EXT_texture_filter_anisotropic")) {
                val maxAniso = FloatArray(1)
                GLES20.glGetFloatv(0x84FF, maxAniso, 0)
                GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, 0x84FE, min(4f, maxAniso[0]))
            }

            GLES20.glUseProgram(p1)
            GLES20.glUniform1i(u1.getValue("uSky"), 0)
            GLES20.glUniform1i(u1.getValue("uOv"), 2)
            GLES20.glUniform1i(u1.getValue("uNrm"), 3)

            ready = true
            ovClearedEmpty = false
            lastOvCam = null
            lastQualityRevision = -1L
            lastTimeRevision = -1L
            bkRow = AtmosphereMath.LH
            dirtyLut.set(true)
            lastFrameStartMs = SystemClock.uptimeMillis()
            nextAutoShipMs = lastFrameStartMs + 6000L
            simHours = AtmosphereMath.hoursNow(this.config.state.utcOffsetSeconds)
            updateSceneParams(lastFrameStartMs, fullBake = true)
            onError(null)
        } catch (e: Throwable) {
            ready = false
            onError(e.message ?: "OpenGL ES 2.0 error")
        }
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        cvWidth = max(2, width)
        cvHeight = max(2, height)
        resizeSceneBuffers()
    }

    private fun resizeSceneBuffers() {
        sw = max(2, (cvWidth * qs).roundToInt())
        sh = max(2, (cvHeight * qs).roundToInt())
        if (!ready) return
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, fbTex)
        GLES20.glTexImage2D(
            GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, sw, sh, 0,
            GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null,
        )
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbo)
        GLES20.glFramebufferTexture2D(
            GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0,
            GLES20.GL_TEXTURE_2D, fbTex, 0,
        )
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
    }

    override fun onDrawFrame(gl: GL10?) {
        if (!ready) return

        val budgetMs = 1000.0 / cap
        val nowBeforeSleep = SystemClock.uptimeMillis()
        val elapsedSinceLast = nowBeforeSleep - lastFrameStartMs
        if (elapsedSinceLast in 0 until (budgetMs.toLong() - 2L)) {
            try {
                Thread.sleep((budgetMs.toLong() - 2L - elapsedSinceLast).coerceAtLeast(1L))
            } catch (_: InterruptedException) {
            }
        }

        val now = SystemClock.uptimeMillis()
        val dtMs = if (lastFrameStartMs == 0L) 33L else (now - lastFrameStartMs).coerceAtLeast(1L)
        lastFrameStartMs = now
        val ds = min(dtMs, 100L) / 1000.0

        val cfg = config
        if (cfg.timeRevision != lastTimeRevision) {
            lastTimeRevision = cfg.timeRevision
            simHours = cfg.state.t
        }
        if (cfg.qualityRevision != lastQualityRevision) {
            lastQualityRevision = cfg.qualityRevision
            val qm = cfg.state.qualityMode
            cap = qm.targetFps
            qs = qm.scale
            bench = if (qm == QualityMode.AUTO) 0 else 9
            ema = 1000.0 / cap
            resizeSceneBuffers()
        }

        val s = cfg.state
        if (s.fastDay) {
            simHours = (simHours + ds * 24.0 / 60.0) % 24.0
        } else if (s.realTime) {
            simHours = AtmosphereMath.hoursNow(s.utcOffsetSeconds)
        }

        val camEff = ((s.cam + yawG + yawD) % 360.0 + 720.0) % 360.0
        if (abs(AtmosphereMath.wrapDeg(camEff - lastCamEff)) > 0.6) {
            lastCamEff = camEff
            dirtyLut.set(true)
        }

        if ((dirtyLut.get() && now - lutUpdatedMs > 100L) || now - lutUpdatedMs > 1000L) {
            lutUpdatedMs = now
            dirtyLut.set(false)
            updateSceneParams(now, fullBake = false, camEffOverride = camEff)
        }
        bakeSlice(3.0)

        if (s.storm > 0.5 && flash < 0.05 && Random.nextDouble() < dtMs / 6000.0) {
            flash = 1.0
            bolt = Random.nextDouble()
        }
        flash = max(0.0, flash - dtMs / 180.0)

        tiltX += (tiltTx - tiltX) * 0.06
        tiltY += (tiltTy - tiltY) * 0.06

        val p = curParams ?: return
        val pit = AtmosphereMath.clamp(pitG + pitD, -35.0, 35.0) * AtmosphereMath.D

        if (pendingSpawnNearShip.compareAndSet(true, false)) {
            ship = ShipState.spawn(near = true, fast = s.fastShip)
        }

        val prevOvCam = lastOvCam
        if (prevOvCam == null) {
            lastOvCam = camEff
        } else {
            val dY = AtmosphereMath.wrapDeg(camEff - prevOvCam) * AtmosphereMath.D
            lastOvCam = camEff
            if (dY != 0.0) {
                birds.forEach { it.rotWorld(dY) }
                ship?.rotWorld(dY)
            }
        }

        for (i in birds.indices) {
            val b = birds[i]
            b.step(ds)
            if (b.z < 7.0 || hypot(b.x, b.z) > 170.0) {
                birds[i] = GullState.spawn()
            }
        }

        val currentShip = ship
        if (currentShip != null) {
            currentShip.fast = s.fastShip
            currentShip.step(ds)
            if (currentShip.z < 16.0 || currentShip.z > 1600.0 || abs(currentShip.x) > 1600.0) {
                ship = null
                nextAutoShipMs = now + 40_000L + Random.nextLong(80_000L)
            }
        } else if (s.shipOn && now > nextAutoShipMs) {
            ship = ShipState.spawn(near = false, fast = s.fastShip)
        }

        val timeSec = (now / 1000.0).toFloat()
        val effectiveTiltX = if (s.gyroMode) 0.0 else tiltX
        val effectiveTiltY = if (s.gyroMode) 0.0 else tiltY
        val rollWave = sin(now / 1000.0 * 0.45) * 0.04 * s.wind * s.wind * s.wind + roll * AtmosphereMath.D

        val hasOverlayObjects = (ship != null && s.shipOn && ship!!.z > 14.0) || s.birdsOn
        if (hasOverlayObjects || !ovClearedEmpty) {
            ovClearedEmpty = !hasOverlayObjects
            val light = lightModel
            if (light != null) {
                val bmp: Bitmap = overlayRenderer.render(
                    width = sw,
                    height = sh,
                    view = OverlayCanvasRenderer.ViewTransform(
                        tx = effectiveTiltX,
                        ty = effectiveTiltY,
                        pit = pit,
                        rl = rollWave,
                    ),
                    light = light,
                    ship = ship,
                    shipOn = s.shipOn,
                    windDir = s.windDir,
                    wind = s.wind,
                    camEff = camEff,
                    birds = birds,
                    birdsOn = s.birdsOn,
                )
                GLES20.glActiveTexture(GLES20.GL_TEXTURE2)
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, ovTex)
                GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bmp, 0)
            }
        }

        val workStartNs = System.nanoTime()

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo)
        GLES20.glEnableVertexAttribArray(0)
        GLES20.glVertexAttribPointer(0, 2, GLES20.GL_FLOAT, false, 0, 0)

        // Pass 1: Render scene to offscreen FBO
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbo)
        GLES20.glViewport(0, 0, sw, sh)
        GLES20.glUseProgram(p1)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, lutTex)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE2)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, ovTex)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE3)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, nrmTex)

        GLES20.glUniform2f(u1.getValue("uRes"), sw.toFloat(), sh.toFloat())
        GLES20.glUniform2f(u1.getValue("uTilt"), effectiveTiltX.toFloat(), effectiveTiltY.toFloat())
        GLES20.glUniform1f(u1.getValue("uTime"), timeSec)
        GLES20.glUniform1f(u1.getValue("uCloud"), s.cloud.toFloat())
        GLES20.glUniform1f(u1.getValue("uWind"), s.wind.toFloat())
        GLES20.glUniform1f(u1.getValue("uRain"), s.rain.toFloat())
        GLES20.glUniform1f(u1.getValue("uFog"), s.fog.toFloat())
        GLES20.glUniform1f(u1.getValue("uFlash"), flash.toFloat())
        GLES20.glUniform1f(u1.getValue("uMoonPh"), p.moonPh.toFloat())
        GLES20.glUniform1f(u1.getValue("uMoonL"), p.moonL.toFloat())
        GLES20.glUniform1f(u1.getValue("uStar"), p.star.toFloat())
        GLES20.glUniform1f(u1.getValue("uWDir"), p.wdir.toFloat())
        GLES20.glUniform1f(u1.getValue("uStarRot"), p.starRot.toFloat())
        GLES20.glUniform1f(u1.getValue("uBow"), p.bow.toFloat())
        GLES20.glUniform1f(u1.getValue("uBolt"), bolt.toFloat())
        GLES20.glUniform1f(u1.getValue("uNight"), p.night2.toFloat())
        GLES20.glUniform3fv(u1.getValue("uObj"), 1, p.obj, 0)
        GLES20.glUniform3f(u1.getValue("uLook"), pit.toFloat(), (roll * AtmosphereMath.D).toFloat(), 0f)
        GLES20.glUniform3f(
            u1.getValue("uLH"),
            p.lh.toFloat(),
            if (p.lhRotate) 1f else 0f,
            p.lhFixedB.toFloat(),
        )
        GLES20.glUniform3f(u1.getValue("uSun"), p.sun[0].toFloat(), p.sun[1].toFloat(), p.sun[2].toFloat())
        GLES20.glUniform3f(u1.getValue("uSunL"), p.sunL[0].toFloat(), p.sunL[1].toFloat(), p.sunL[2].toFloat())
        GLES20.glUniform3f(u1.getValue("uSunD"), p.sunD[0].toFloat(), p.sunD[1].toFloat(), p.sunD[2].toFloat())
        GLES20.glUniform3f(u1.getValue("uMoon"), p.moon[0].toFloat(), p.moon[1].toFloat(), p.moon[2].toFloat())
        GLES20.glUniform3f(u1.getValue("uAmb"), p.amb[0].toFloat(), p.amb[1].toFloat(), p.amb[2].toFloat())
        GLES20.glUniform3f(u1.getValue("uPole"), p.pole[0].toFloat(), p.pole[1].toFloat(), p.pole[2].toFloat())
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 3)

        // Pass 2: Bicubic upscale, sharpen, glass droplets, dither to screen
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
        GLES20.glViewport(0, 0, cvWidth, cvHeight)
        GLES20.glUseProgram(p2)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, fbTex)
        GLES20.glUniform1i(u2.getValue("uScene"), 1)
        GLES20.glUniform2f(u2.getValue("uRes"), cvWidth.toFloat(), cvHeight.toFloat())
        GLES20.glUniform2f(u2.getValue("uSceneRes"), sw.toFloat(), sh.toFloat())
        GLES20.glUniform1f(u2.getValue("uTime"), timeSec)
        GLES20.glUniform1f(u2.getValue("uRain"), s.rain.toFloat())
        GLES20.glUniform1f(u2.getValue("uFog"), s.fog.toFloat())
        GLES20.glUniform1f(u2.getValue("uDrops"), if (s.dropsOn) 1f else 0f)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 3)

        val workMs = (System.nanoTime() - workStartNs) / 1_000_000.0
        val effectiveDt = max(dtMs.toDouble(), workMs)
        ema = ema * 0.90 + effectiveDt * 0.10
        frameCount++

        if (s.qualityMode == QualityMode.AUTO &&
            frameCount > 15 &&
            ((bench < 4 && frameCount % 20 == 0) || frameCount % 300 == 0)
        ) {
            if (ema > budgetMs * 1.22 && qs > 0.34f) {
                val step = if (ema > budgetMs * 1.8) 0.10f else 0.07f
                qs = max(0.32f, qs - step)
                resizeSceneBuffers()
                bench++
                ema = budgetMs
            } else if (bench < 4) {
                bench = 9
            }
        }

        if (frameCount % 20 == 0 || now - statusUpdatedMs > 1200L) {
            statusUpdatedMs = now
            val stats = FpsStats(
                fps = (1000.0 / max(1.0, ema)).roundToInt(),
                sceneWidth = sw,
                sceneHeight = sh,
                screenWidth = cvWidth,
                screenHeight = cvHeight,
            )
            onTelemetry(stats, p.elDeg, simHours)
        }
    }

    private fun bakeStart(p: FrameParams) {
        bkParams = p
        bkRow = 0
        bkSun = p.sun.copyOf()
        bkEx = p.exp
    }

    private fun bakeSlice(budgetMs: Double) {
        if (bkRow >= AtmosphereMath.LH) return
        val bp = bkParams ?: return
        val t0 = System.nanoTime()
        while (bkRow < AtmosphereMath.LH && (System.nanoTime() - t0) / 1_000_000.0 < budgetMs) {
            AtmosphereMath.bakeRow(bp.sun, bp.exp, bkBuf, bkRow)
            bkRow++
        }
        if (bkRow >= AtmosphereMath.LH) {
            System.arraycopy(bkBuf, 0, lutBuf, 0, lutBuf.size)
            lutDirectBuf.clear()
            lutDirectBuf.put(lutBuf)
            lutDirectBuf.position(0)
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, lutTex)
            GLES20.glTexImage2D(
                GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA,
                AtmosphereMath.LW, AtmosphereMath.LH, 0,
                GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, lutDirectBuf,
            )
            val amb = AtmosphereMath.skyAmbient(lutBuf)
            bkAmb = DoubleArray(3) { amb[it] * 0.9 }
        }
    }

    private fun updateSceneParams(now: Long, fullBake: Boolean, camEffOverride: Double? = null) {
        val s = config.state.copy(
            t = simHours,
            doy = AtmosphereMath.doyNow(),
            camEff = camEffOverride ?: (((config.state.cam + yawG + yawD) % 360.0 + 720.0) % 360.0),
            flash = flash,
            bolt = bolt,
        )
        val p = AtmosphereMath.frameParams(s, System.currentTimeMillis())
        val prevSun = bkSun
        val needBake = prevSun == null ||
            hypot(hypot(p.sun[0] - prevSun[0], p.sun[1] - prevSun[1]), p.sun[2] - prevSun[2]) > 0.003 ||
            abs(p.exp / bkEx - 1.0) > 0.04

        if (fullBake) {
            bakeStart(p)
            bakeSlice(1e9)
        } else if (needBake && bkRow >= AtmosphereMath.LH) {
            bakeStart(p)
        }

        val a = bkAmb
        val l = (a[0] + a[1] + a[2]) / 3.0
        val o = p.over
        val finalAmb = DoubleArray(3) { k -> (a[k] * (1.0 - o * 0.7) + l * o * 0.7) * (1.0 - 0.35 * o) }
        val updated = p.copy(amb = finalAmb)
        curParams = updated
        lightModel = AtmosphereMath.makeLight(updated, lutBuf)
    }

    private fun compileShader(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        val compiled = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0)
        if (compiled[0] == 0) {
            val info = GLES20.glGetShaderInfoLog(shader)
            GLES20.glDeleteShader(shader)
            throw RuntimeException("Шейдер: $info")
        }
        return shader
    }

    private fun linkProgram(vs: Int, fs: Int): Int {
        val prog = GLES20.glCreateProgram()
        GLES20.glAttachShader(prog, vs)
        GLES20.glAttachShader(prog, fs)
        GLES20.glBindAttribLocation(prog, 0, "a")
        GLES20.glLinkProgram(prog)
        val linked = IntArray(1)
        GLES20.glGetProgramiv(prog, GLES20.GL_LINK_STATUS, linked, 0)
        if (linked[0] == 0) {
            val info = GLES20.glGetProgramInfoLog(prog)
            GLES20.glDeleteProgram(prog)
            throw RuntimeException("Сборка программы: $info")
        }
        return prog
    }
}
