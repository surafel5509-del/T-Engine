package com.tengine.app

import com.tengine.android.TEngineActivity
import com.tengine.app.games.NeonKnightPlatformer
import com.tengine.app.games.PhysicsSandboxScene
import com.tengine.app.games.SpaceSurvivorArena
import com.tengine.core.EngineConfig
import com.tengine.core.math.Color
import com.tengine.core.scene.Scene
import com.tengine.core.ui.UIAnchor
import com.tengine.core.ui.UIButton
import com.tengine.core.ui.UICanvas

/**
 * Fullscreen standalone 60-120 FPS Android Game Runner powered by [TEngineActivity]!
 */
class PlayerActivity : TEngineActivity() {

    companion object {
        const val EXTRA_GAME_ID = "extra_game_id"
        const val GAME_NEON_KNIGHT = "neon_knight"
        const val GAME_PHYSICS_LAB = "physics_lab"
        const val GAME_SPACE_SURVIVOR = "space_survivor"
    }

    override fun provideEngineConfig(): EngineConfig =
        EngineConfig(applicationName = "T-Engine Standalone Player")

    override fun createInitialScene(): Scene {
        val id = intent?.getStringExtra(EXTRA_GAME_ID) ?: GAME_NEON_KNIGHT
        val scene = when (id) {
            GAME_PHYSICS_LAB -> PhysicsSandboxScene.buildScene()
            GAME_SPACE_SURVIVOR -> SpaceSurvivorArena.buildScene()
            else -> NeonKnightPlatformer.buildScene()
        }

        // Add a discreet top-right "EXIT" button on the scene's HUD canvas to return to Hub easily
        val hud = scene.uiCanvases.firstOrNull() ?: scene.addCanvas(UICanvas("OverlayHUD"))
        hud.addWidget(
            UIButton(
                id = "BackToHubBtn",
                label = "HUB",
                anchor = UIAnchor.TOP_RIGHT,
                anchoredX = -76f,
                anchoredY = -44f,
                width = 110f,
                height = 52f,
                isCircular = false,
                normalColor = Color(0.18f, 0.22f, 0.32f, 0.8f)
            ) {
                runOnUiThread { finish() }
            }
        )
        return scene
    }
}
