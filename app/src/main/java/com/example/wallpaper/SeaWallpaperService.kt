package com.example.wallpaper

import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES20
import android.os.SystemClock
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import com.example.data.PreferencesRepository
import com.example.data.SavedPreferences
import com.example.data.WeatherRepository
import com.example.sensors.OrientationController
import com.example.simulation.AtmosphereMath
import com.example.simulation.SeaRenderer
import com.example.simulation.SeaState
import com.example.simulation.WallpaperScrollMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class SeaWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = SeaWallpaperEngine()

    private inner class SeaWallpaperEngine : Engine() {

        private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        private val prefsRepo = PreferencesRepository(applicationContext)
        private val weatherRepo = WeatherRepository()

        private var timeRevision = 0L
        private var qualityRevision = 0L
        private var currentState = SeaState(
            t = AtmosphereMath.hoursNow(null),
            doy = AtmosphereMath.doyNow(),
        )
        private var lastAppliedPrefs: SavedPreferences? = null
        private var detectedScreenCount = 0
        private var lastXOffset = 0.0f
        private var lastWeatherFetchMs = 0L
        private var weatherJob: Job? = null
        private var prefsJob: Job? = null
        private var glThread: WallpaperGlThread? = null
        private var isEngineVisible = false

        private val renderer = SeaRenderer(
            onTelemetry = { _, _, _ -> },
            onError = { _ -> },
        )

        private val orientationController = OrientationController(applicationContext) { yawG, pitG, roll, tiltTx, tiltTy ->
            if (currentState.gyroMode) {
                renderer.yawG = yawG
                renderer.pitG = pitG
                renderer.roll = roll
                renderer.tiltTx = tiltTx
                renderer.tiltTy = tiltTy
            }
        }

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            // Disable direct touch manipulation on the live wallpaper screen;
            // all framing and settings are configured strictly inside Wallpaper Settings.
            setTouchEventsEnabled(false)

            prefsJob = engineScope.launch {
                prefsRepo.preferencesFlow.collect { saved ->
                    applyPreferences(saved)
                }
            }
        }

        private fun applyPreferences(saved: SavedPreferences) {
            val prev = lastAppliedPrefs
            lastAppliedPrefs = saved
            if (saved.detectedScreenCount > 0) {
                detectedScreenCount = saved.detectedScreenCount
            }

            if (prev == null || prev.qualityMode != saved.qualityMode) {
                qualityRevision++
            }
            if (prev == null ||
                prev.realTime != saved.realTime ||
                prev.fastDay != saved.fastDay ||
                (!saved.realTime && !saved.fastDay && prev.manualTimeHours != saved.manualTimeHours)
            ) {
                timeRevision++
            }

            val gyroEnabled = saved.gyroMode && orientationController.isAvailable
            if (prev == null || prev.gyroMode != saved.gyroMode) {
                orientationController.setGyroEnabled(gyroEnabled)
                if (gyroEnabled && isEngineVisible) {
                    orientationController.start()
                } else {
                    orientationController.stop()
                    renderer.yawG = 0.0
                    renderer.pitG = 0.0
                    renderer.roll = 0.0
                    renderer.tiltTx = 0.0
                    renderer.tiltTy = 0.0
                }
            }

            val targetTime = if (saved.realTime) {
                AtmosphereMath.hoursNow(currentState.utcOffsetSeconds)
            } else {
                saved.manualTimeHours
            }

            val coordsChanged = prev == null || prev.lat != saved.lat || prev.lon != saved.lon
            val autoTurnedOn = (prev?.autoMode != true) && saved.autoMode

            currentState = if (saved.autoMode && prev != null && prev.autoMode) {
                currentState.copy(
                    autoMode = true,
                    lat = saved.lat,
                    lon = saved.lon,
                    qualityMode = saved.qualityMode,
                    gyroMode = gyroEnabled,
                    cam = saved.cam,
                    camPitch = saved.camPitch,
                    lhOn = saved.lhOn,
                    lhMode = saved.lhMode,
                    shipOn = saved.shipOn,
                    birdsOn = saved.birdsOn,
                    dropsOn = saved.dropsOn,
                    fastShip = saved.fastShip,
                    scrollMode = saved.scrollMode,
                    homeScreenIndex = saved.homeScreenIndex,
                    scrollSpanDeg = saved.scrollSpanDeg,
                    scrollReverse = saved.scrollReverse,
                    realTime = true,
                    fastDay = false,
                )
            } else {
                currentState.copy(
                    autoMode = saved.autoMode,
                    lat = saved.lat,
                    lon = saved.lon,
                    qualityMode = saved.qualityMode,
                    gyroMode = gyroEnabled,
                    wind = saved.wind,
                    cloud = saved.cloud,
                    rain = saved.rain,
                    fog = saved.fog,
                    storm = saved.storm,
                    cam = saved.cam,
                    camPitch = saved.camPitch,
                    windDir = saved.windDir,
                    realTime = if (saved.autoMode) true else saved.realTime,
                    fastDay = if (saved.autoMode) false else saved.fastDay,
                    t = targetTime,
                    doy = AtmosphereMath.doyNow(),
                    lhOn = saved.lhOn,
                    lhMode = saved.lhMode,
                    shipOn = saved.shipOn,
                    birdsOn = saved.birdsOn,
                    dropsOn = saved.dropsOn,
                    fastShip = saved.fastShip,
                    scrollMode = saved.scrollMode,
                    homeScreenIndex = saved.homeScreenIndex,
                    scrollSpanDeg = saved.scrollSpanDeg,
                    scrollReverse = saved.scrollReverse,
                )
            }

            renderer.pitD = currentState.camPitch
            updatePagePanning()
            renderer.updateConfig(currentState, timeRevision, qualityRevision)

            if (saved.autoMode) {
                if (weatherJob == null || autoTurnedOn || coordsChanged) {
                    startWeatherPolling()
                }
            } else {
                weatherJob?.cancel()
                weatherJob = null
            }
        }

        private fun updatePagePanning() {
            if (currentState.scrollMode != WallpaperScrollMode.PANORAMA) {
                renderer.yawD = 0.0
                return
            }
            val effectivePages = if (detectedScreenCount >= 2) detectedScreenCount else 3
            val clampedHome = currentState.homeScreenIndex.coerceIn(1, effectivePages)
            val homeFraction = (clampedHome - 1).toDouble() / (effectivePages - 1).toDouble()
            val signedSpan = if (currentState.scrollReverse) {
                -currentState.scrollSpanDeg
            } else {
                currentState.scrollSpanDeg
            }
            renderer.yawD = (lastXOffset.toDouble() - homeFraction) * signedSpan
        }

        private fun startWeatherPolling() {
            weatherJob?.cancel()
            weatherJob = engineScope.launch {
                while (isActive) {
                    if (isEngineVisible || lastWeatherFetchMs == 0L) {
                        fetchAndApplyWeather()
                    }
                    delay(600_000L)
                }
            }
        }

        private suspend fun fetchAndApplyWeather() {
            val lat = currentState.lat
            val lon = currentState.lon
            try {
                val snap = weatherRepo.fetchWeather(lat, lon)
                lastWeatherFetchMs = SystemClock.uptimeMillis()
                timeRevision++
                val updatedHours = AtmosphereMath.hoursNow(snap.utcOffsetSeconds)
                currentState = currentState.copy(
                    utcOffsetSeconds = snap.utcOffsetSeconds,
                    t = updatedHours,
                    wind = snap.wind,
                    cloud = snap.cloud,
                    rain = snap.rain,
                    fog = snap.fog,
                    storm = snap.storm,
                    windDir = snap.windDir,
                    realTime = true,
                    fastDay = false,
                )
                renderer.updateConfig(currentState, timeRevision, qualityRevision)
            } catch (_: Exception) {
                // Keep showing cached/last-known state gracefully on transient network failure
            }
        }

        override fun onSurfaceCreated(holder: SurfaceHolder) {
            super.onSurfaceCreated(holder)
            glThread?.requestExitAndWait()
            val frame = holder.surfaceFrame
            val width = frame.width().coerceAtLeast(2)
            val height = frame.height().coerceAtLeast(2)
            val thread = WallpaperGlThread(
                surfaceHolder = holder,
                initialWidth = width,
                initialHeight = height,
                renderer = renderer,
                initialPaused = !isEngineVisible,
            )
            glThread = thread
            thread.start()
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            glThread?.onResize(width, height)
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            glThread?.requestExitAndWait()
            glThread = null
            super.onSurfaceDestroyed(holder)
        }

        override fun onVisibilityChanged(visible: Boolean) {
            super.onVisibilityChanged(visible)
            isEngineVisible = visible
            glThread?.setPaused(!visible)
            if (visible) {
                if (currentState.gyroMode) {
                    orientationController.start()
                }
                val now = SystemClock.uptimeMillis()
                if (currentState.autoMode && now - lastWeatherFetchMs > 600_000L) {
                    startWeatherPolling()
                }
            } else {
                orientationController.stop()
            }
        }

        override fun onOffsetsChanged(
            xOffset: Float,
            yOffset: Float,
            xOffsetStep: Float,
            yOffsetStep: Float,
            xPixelOffset: Int,
            yPixelOffset: Int,
        ) {
            super.onOffsetsChanged(xOffset, yOffset, xOffsetStep, yOffsetStep, xPixelOffset, yPixelOffset)
            lastXOffset = xOffset.coerceIn(0f, 1f)

            if (!isPreview) {
                val pages = when {
                    xOffsetStep > 0.001f && xOffsetStep < 0.99f ->
                        ((1.0f / xOffsetStep).roundToInt() + 1).coerceIn(2, 12)
                    xOffsetStep >= 0.99f -> 2
                    else -> null
                }
                if (pages != null && pages != detectedScreenCount) {
                    detectedScreenCount = pages
                    engineScope.launch {
                        prefsRepo.setDetectedScreenCount(pages)
                    }
                }
            }

            updatePagePanning()
        }

        override fun onDestroy() {
            weatherJob?.cancel()
            prefsJob?.cancel()
            orientationController.stop()
            glThread?.requestExitAndWait()
            glThread = null
            engineScope.cancel()
            super.onDestroy()
        }
    }
}

private class WallpaperGlThread(
    private val surfaceHolder: SurfaceHolder,
    initialWidth: Int,
    initialHeight: Int,
    private val renderer: SeaRenderer,
    initialPaused: Boolean,
) : Thread("SeaWallpaperGlThread") {

    private val lock = Object()
    private var shouldExit = false
    private var exited = false
    private var paused = initialPaused
    private var width = initialWidth
    private var height = initialHeight
    private var sizeChanged = true

    fun setPaused(value: Boolean) {
        synchronized(lock) {
            paused = value
            lock.notifyAll()
        }
    }

    fun onResize(newWidth: Int, newHeight: Int) {
        synchronized(lock) {
            if (width != newWidth || height != newHeight) {
                width = newWidth
                height = newHeight
                sizeChanged = true
                lock.notifyAll()
            }
        }
    }

    fun requestExitAndWait() {
        synchronized(lock) {
            shouldExit = true
            lock.notifyAll()
            while (!exited) {
                try {
                    lock.wait(200)
                    if (!isAlive) break
                } catch (_: InterruptedException) {
                    currentThread().interrupt()
                    break
                }
            }
        }
    }

    override fun run() {
        var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
        var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
        var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE

        try {
            val surface = surfaceHolder.surface
            if (surface == null || !surface.isValid) {
                return
            }

            eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            if (eglDisplay == EGL14.EGL_NO_DISPLAY) {
                throw IllegalStateException("eglGetDisplay failed")
            }
            val version = IntArray(2)
            if (!EGL14.eglInitialize(eglDisplay, version, 0, version, 1)) {
                throw IllegalStateException("eglInitialize failed")
            }

            val configAttribs = intArrayOf(
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_NONE,
            )
            val configs = arrayOfNulls<EGLConfig>(1)
            val numConfigs = IntArray(1)
            if (!EGL14.eglChooseConfig(eglDisplay, configAttribs, 0, configs, 0, 1, numConfigs, 0) ||
                numConfigs[0] <= 0 ||
                configs[0] == null
            ) {
                throw IllegalStateException("eglChooseConfig failed")
            }
            val eglConfig = configs[0]!!

            val ctxAttribs = intArrayOf(
                EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
                EGL14.EGL_NONE,
            )
            eglContext = EGL14.eglCreateContext(eglDisplay, eglConfig, EGL14.EGL_NO_CONTEXT, ctxAttribs, 0)
            if (eglContext == EGL14.EGL_NO_CONTEXT) {
                throw IllegalStateException("eglCreateContext failed")
            }

            val surfAttribs = intArrayOf(EGL14.EGL_NONE)
            eglSurface = EGL14.eglCreateWindowSurface(eglDisplay, eglConfig, surface, surfAttribs, 0)
            if (eglSurface == EGL14.EGL_NO_SURFACE) {
                throw IllegalStateException("eglCreateWindowSurface failed")
            }

            if (!EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
                throw IllegalStateException("eglMakeCurrent failed")
            }

            GLES20.glClearColor(0.024f, 0.063f, 0.110f, 1.0f)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
            EGL14.eglSwapBuffers(eglDisplay, eglSurface)

            renderer.onSurfaceCreated(null, null)

            while (true) {
                val curWidth: Int
                val curHeight: Int
                val doResize: Boolean
                synchronized(lock) {
                    while (!shouldExit && paused) {
                        try {
                            lock.wait()
                        } catch (_: InterruptedException) {
                            shouldExit = true
                        }
                    }
                    if (shouldExit) return
                    curWidth = width
                    curHeight = height
                    doResize = sizeChanged
                    sizeChanged = false
                }

                if (doResize && curWidth > 0 && curHeight > 0) {
                    renderer.onSurfaceChanged(null, curWidth, curHeight)
                }

                renderer.onDrawFrame(null)
                if (!EGL14.eglSwapBuffers(eglDisplay, eglSurface)) {
                    val err = EGL14.eglGetError()
                    if (err == EGL14.EGL_CONTEXT_LOST) {
                        break
                    }
                }
            }
        } catch (t: Throwable) {
            renderer.reportExternalError(t.message ?: "EGL wallpaper initialization failed")
        } finally {
            if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
                EGL14.eglMakeCurrent(
                    eglDisplay,
                    EGL14.EGL_NO_SURFACE,
                    EGL14.EGL_NO_SURFACE,
                    EGL14.EGL_NO_CONTEXT,
                )
                if (eglSurface != EGL14.EGL_NO_SURFACE) {
                    EGL14.eglDestroySurface(eglDisplay, eglSurface)
                }
                if (eglContext != EGL14.EGL_NO_CONTEXT) {
                    EGL14.eglDestroyContext(eglDisplay, eglContext)
                }
                EGL14.eglTerminate(eglDisplay)
            }
            synchronized(lock) {
                exited = true
                lock.notifyAll()
            }
        }
    }
}
