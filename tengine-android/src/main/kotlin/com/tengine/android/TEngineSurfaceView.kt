package com.tengine.android

import android.annotation.SuppressLint
import android.content.Context
import android.opengl.GLSurfaceView
import android.view.KeyEvent
import android.view.MotionEvent
import com.tengine.android.audio.AndroidAudioBackend
import com.tengine.android.graphics.DynamicFontAtlas
import com.tengine.android.graphics.GLESRenderBackend
import com.tengine.android.input.AndroidInputBridge
import com.tengine.core.Engine
import com.tengine.core.EngineConfig
import com.tengine.core.graphics.FontAtlas
import java.util.concurrent.ConcurrentLinkedQueue
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * Hardware-Accelerated 60-120 FPS OpenGL ES [GLSurfaceView] hosting a T-Engine [Engine] instance.
 * Can be used standalone in fullscreen Android games OR embedded inside T-Engine Studio's Editor!
 */
@SuppressLint("ViewConstructor")
class TEngineSurfaceView(
    context: Context,
    config: EngineConfig = EngineConfig()
) : GLSurfaceView(context), GLSurfaceView.Renderer {

    val glesBackend = GLESRenderBackend(config.maxBatchSprites)
    val audioBackend = AndroidAudioBackend(context)
    val inputBridge = AndroidInputBridge(context)
    val engine = Engine(config, glesBackend, audioBackend)

    var highResFont: FontAtlas? = null
        private set

    /**
     * Optional interceptor allowing T-Engine Studio's Scene Viewport to handle gizmo/camera
     * touches when in Edit Mode, returning true if consumed before game Input!
     */
    var touchInterceptor: ((MotionEvent) -> Boolean)? = null

    /** Optional UI-thread frame listener (throttled by caller) for live Profiler / Inspector updates. */
    var onFrameCompleted: (() -> Unit)? = null

    private val engineTasks = ConcurrentLinkedQueue<() -> Unit>()
    private var lastFrameNano: Long = 0L

    init {
        setEGLContextClientVersion(2)
        setEGLConfigChooser(8, 8, 8, 8, 16, 0)
        preserveEGLContextOnPause = true
        isFocusable = true
        isFocusableInTouchMode = true
        setRenderer(this)
        renderMode = RENDERMODE_CONTINUOUSLY
    }

    /**
     * Safely posts a lambda to execute on the Engine / OpenGL simulation thread before the next tick.
     */
    fun postToEngineThread(task: () -> Unit) {
        engineTasks.add(task)
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        glesBackend.onContextCreated()
        if (highResFont == null) {
            highResFont = DynamicFontAtlas.build(fontSizePx = 26f, bold = true)
        }
        highResFont?.let { font ->
            engine.activeScene?.uiCanvases?.forEach { it.font = font }
        }
        lastFrameNano = System.nanoTime()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        glesBackend.setViewport(0, 0, width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        while (true) {
            val task = engineTasks.poll() ?: break
            task.invoke()
        }

        val now = System.nanoTime()
        val rawDt = if (lastFrameNano == 0L) {
            1f / 60f
        } else {
            ((now - lastFrameNano) / 1_000_000_000.0).toFloat()
        }
        lastFrameNano = now

        highResFont?.let { font ->
            val canvases = engine.activeScene?.uiCanvases
            if (canvases != null) {
                for (i in 0 until canvases.size) {
                    if (canvases[i].font === FontAtlas.defaultPixelFont) {
                        canvases[i].font = font
                    }
                }
            }
        }

        engine.tickFrame(rawDt)
        onFrameCompleted?.invoke()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val interceptor = touchInterceptor
        if (interceptor != null && interceptor(event)) {
            return true
        }
        return inputBridge.onTouchEvent(event)
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (inputBridge.onGenericMotionEvent(event)) return true
        return super.onGenericMotionEvent(event)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (inputBridge.onKeyDown(keyCode)) return true
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (inputBridge.onKeyUp(keyCode)) return true
        return super.onKeyUp(keyCode, event)
    }

    override fun onPause() {
        super.onPause()
        inputBridge.unregisterSensors()
        audioBackend.pauseAll()
    }

    override fun onResume() {
        super.onResume()
        lastFrameNano = System.nanoTime()
        inputBridge.registerSensors()
        audioBackend.resumeAll()
    }

    fun shutdownEngine() {
        inputBridge.unregisterSensors()
        engine.shutdown()
    }
}
