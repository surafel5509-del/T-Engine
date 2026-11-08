package com.tengine.android

import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import com.tengine.core.Engine
import com.tengine.core.EngineConfig
import com.tengine.core.scene.Scene

/**
 * Base fullscreen immersive Android Activity for standalone T-Engine 2D games!
 * Subclasses simply implement [createInitialScene] and optionally override [provideEngineConfig].
 */
abstract class TEngineActivity : AppCompatActivity() {

    protected lateinit var surfaceView: TEngineSurfaceView
        private set

    protected val engine: Engine
        get() = surfaceView.engine

    open fun provideEngineConfig(): EngineConfig = EngineConfig()

    abstract fun createInitialScene(): Scene

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        surfaceView = TEngineSurfaceView(this, provideEngineConfig())
        setContentView(surfaceView)

        surfaceView.postToEngineThread {
            engine.loadScene(createInitialScene())
        }
        enableImmersiveFullscreen()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) enableImmersiveFullscreen()
    }

    private fun enableImmersiveFullscreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.let { ctrl ->
                ctrl.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                ctrl.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                )
        }
    }

    override fun onPause() {
        surfaceView.onPause()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        surfaceView.onResume()
    }

    override fun onDestroy() {
        surfaceView.shutdownEngine()
        super.onDestroy()
    }
}
