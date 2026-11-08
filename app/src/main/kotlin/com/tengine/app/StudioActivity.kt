package com.tengine.app

import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import com.tengine.app.games.NeonKnightPlatformer
import com.tengine.app.games.PhysicsSandboxScene
import com.tengine.app.games.ProceduralArtPack
import com.tengine.app.games.SpaceSurvivorArena
import com.tengine.editor.ui.TEngineStudioView

/**
 * Hosts the Unity-like [TEngineStudioView] with pre-registered editable templates for all 3 showcase games!
 */
class StudioActivity : AppCompatActivity() {

    private lateinit var studioView: TEngineStudioView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        ProceduralArtPack.ensureInitialized()

        val presets = linkedMapOf(
            "Neon Knight Platformer" to { NeonKnightPlatformer.buildScene() },
            "2D Physics Sandbox Lab" to { PhysicsSandboxScene.buildScene() },
            "Star Swarm Survivor" to { SpaceSurvivorArena.buildScene() }
        )

        studioView = TEngineStudioView(
            context = this,
            initialSceneFactory = { NeonKnightPlatformer.buildScene() },
            presetScenes = presets,
            onExitToHub = { finish() }
        )
        setContentView(studioView)
    }

    override fun onPause() {
        studioView.onPauseHost()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        studioView.onResumeHost()
    }

    override fun onDestroy() {
        studioView.onDestroyHost()
        super.onDestroy()
    }
}
