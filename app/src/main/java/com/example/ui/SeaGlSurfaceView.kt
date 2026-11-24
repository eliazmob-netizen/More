package com.example.ui

import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES20
import android.view.TextureView
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.simulation.SeaRenderer

@Composable
fun SeaGlSurfaceView(
    renderer: SeaRenderer,
    onDragLook: (dx: Float, dy: Float) -> Unit,
    onDoubleTapReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var glController by remember { mutableStateOf<TextureGlController?>(null) }

    DisposableEffect(lifecycleOwner, glController) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> glController?.setPaused(false)
                Lifecycle.Event.ON_PAUSE -> glController?.setPaused(true)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { onDoubleTapReset() },
                )
            }
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onDragLook(dragAmount.x, dragAmount.y)
                }
            },
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                val controller = TextureGlController(renderer)
                glController = controller
                TextureView(context).apply {
                    isOpaque = true
                    surfaceTextureListener = controller
                }
            },
        )
    }
}

private class TextureGlController(
    private val renderer: SeaRenderer,
) : TextureView.SurfaceTextureListener {

    private var thread: GlRenderThread? = null
    private var paused = false

    fun setPaused(isPaused: Boolean) {
        paused = isPaused
        thread?.setPaused(isPaused)
    }

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        thread?.requestExitAndWait()
        val renderThread = GlRenderThread(surface, width, height, renderer, paused)
        thread = renderThread
        renderThread.start()
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
        thread?.onResize(width, height)
    }

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        thread?.requestExitAndWait()
        thread = null
        return true
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit
}

private class GlRenderThread(
    private val surfaceTexture: SurfaceTexture,
    initialWidth: Int,
    initialHeight: Int,
    private val renderer: SeaRenderer,
    initialPaused: Boolean,
) : Thread("SeaGlThread") {

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
            eglSurface = EGL14.eglCreateWindowSurface(eglDisplay, eglConfig, surfaceTexture, surfAttribs, 0)
            if (eglSurface == EGL14.EGL_NO_SURFACE) {
                throw IllegalStateException("eglCreateWindowSurface failed")
            }

            if (!EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
                throw IllegalStateException("eglMakeCurrent failed")
            }

            // Immediately present deep-ocean frame before shader compilation
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
            renderer.reportExternalError(t.message ?: "EGL initialization failed")
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
