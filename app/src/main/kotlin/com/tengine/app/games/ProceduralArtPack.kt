package com.tengine.app.games

import com.tengine.core.ecs.ComponentRegistry
import com.tengine.core.graphics.PixelBuffer
import com.tengine.core.graphics.Sprite
import com.tengine.core.graphics.SpriteLibrary
import com.tengine.core.graphics.Texture2D
import com.tengine.core.graphics.TextureFilter
import com.tengine.core.math.Color

/**
 * Generates crisp procedural 16x16 pixel-art sprites (Knight frames, Slime enemy frames,
 * Reinforced Crate, Space Fighter, Swarm Bug) and registers all custom game Behaviours
 * into [ComponentRegistry] so Studio Edit/Play/Save/Load works out of the box!
 */
object ProceduralArtPack {

    private var initialized = false

    fun ensureInitialized() {
        if (initialized) return
        initialized = true

        SpriteLibrary.registerSprite(buildKnightSprite("knight_idle_0", stepOffset = 0, visorGlow = Color.CYAN))
        SpriteLibrary.registerSprite(buildKnightSprite("knight_idle_1", stepOffset = 1, visorGlow = Color(0.5f, 0.98f, 1f)))
        SpriteLibrary.registerSprite(buildKnightSprite("knight_run_0", stepOffset = 2, visorGlow = Color.CYAN))
        SpriteLibrary.registerSprite(buildKnightSprite("knight_run_1", stepOffset = -1, visorGlow = Color.CYAN))

        SpriteLibrary.registerSprite(buildSlimeSprite("slime_0", squash = false))
        SpriteLibrary.registerSprite(buildSlimeSprite("slime_1", squash = true))

        SpriteLibrary.registerSprite(buildCrateSprite("crate_wood"))
        SpriteLibrary.registerSprite(buildShipSprite("ship_fighter"))

        // Register all showcase gameplay scripts for Studio Inspector & JSON Scene Serialization
        ComponentRegistry.register("KnightController", "Gameplay") { KnightController() }
        ComponentRegistry.register("PatrolEnemyAI", "Gameplay") { PatrolEnemyAI() }
        ComponentRegistry.register("SpinningGemBehaviour", "Gameplay") { SpinningGemBehaviour() }
        ComponentRegistry.register("MovingPlatformBehaviour", "Gameplay") { MovingPlatformBehaviour() }
        ComponentRegistry.register("SandboxCannonController", "Gameplay") { SandboxCannonController() }
        ComponentRegistry.register("SpaceShipController", "Gameplay") { SpaceShipController() }
        ComponentRegistry.register("SwarmEnemyBehaviour", "Gameplay") { SwarmEnemyBehaviour() }
    }

    private fun buildKnightSprite(name: String, stepOffset: Int, visorGlow: Color): Sprite {
        val buf = PixelBuffer(16, 16)
        val armor = Color(0.22f, 0.34f, 0.56f, 1f)
        val armorLight = Color(0.36f, 0.56f, 0.88f, 1f)
        val cape = Color(0.88f, 0.22f, 0.38f, 1f)
        val boot = Color(0.14f, 0.18f, 0.28f, 1f)

        val bobY = if (stepOffset == 1) 1 else 0
        // Helmet
        buf.fillRect(5, 1 + bobY, 7, 5, armorLight)
        buf.fillRect(8, 3 + bobY, 4, 2, visorGlow)
        // Cape & Torso
        buf.fillRect(3, 6 + bobY, 3, 6, cape)
        buf.fillRect(5, 6 + bobY, 6, 6, armor)
        buf.fillRect(6, 7 + bobY, 4, 3, armorLight)
        // Boots
        val leftX = if (stepOffset == 2) 4 else 5
        val rightX = if (stepOffset == 2) 10 else 9
        buf.fillRect(leftX, 12, 2, 4, boot)
        buf.fillRect(rightX, 12, 2, 4, boot)

        val tex = Texture2D(name, 16, 16, TextureFilter.NEAREST, pixelBuffer = buf)
        return Sprite(name, tex, pixelsPerUnit = 16f)
    }

    private fun buildSlimeSprite(name: String, squash: Boolean): Sprite {
        val buf = PixelBuffer(16, 16)
        val body = Color(0.88f, 0.25f, 0.65f, 0.95f)
        val highlight = Color(1f, 0.65f, 0.90f, 1f)
        val eye = Color.WHITE

        val topY = if (squash) 7 else 5
        val w = if (squash) 14 else 12
        val startX = (16 - w) / 2
        buf.fillRect(startX, topY, w, 15 - topY, body)
        buf.fillRect(startX + 2, topY + 1, 3, 2, highlight)
        buf.fillRect(startX + 3, topY + 3, 2, 2, eye)
        buf.fillRect(startX + w - 5, topY + 3, 2, 2, eye)

        val tex = Texture2D(name, 16, 16, TextureFilter.NEAREST, pixelBuffer = buf)
        return Sprite(name, tex, pixelsPerUnit = 16f)
    }

    private fun buildCrateSprite(name: String): Sprite {
        val buf = PixelBuffer(16, 16)
        val border = Color(0.42f, 0.26f, 0.12f, 1f)
        val plank = Color(0.76f, 0.52f, 0.24f, 1f)
        buf.fillRect(0, 0, 16, 16, border)
        buf.fillRect(2, 2, 12, 12, plank)
        for (i in 2..13) {
            buf.setPixel(i, i, border)
            buf.setPixel(15 - i, i, border)
        }
        val tex = Texture2D(name, 16, 16, TextureFilter.NEAREST, pixelBuffer = buf)
        return Sprite(name, tex, pixelsPerUnit = 16f)
    }

    private fun buildShipSprite(name: String): Sprite {
        val buf = PixelBuffer(16, 16)
        val hull = Color(0.20f, 0.85f, 0.98f, 1f)
        val wing = Color(0.16f, 0.45f, 0.85f, 1f)
        val cockpit = Color(1f, 0.92f, 0.35f, 1f)
        // Pointing +X (Right) so lookAt2D aligns nose directly toward movement/target
        buf.fillRect(2, 3, 6, 10, wing)
        buf.fillRect(4, 5, 8, 6, hull)
        buf.fillRect(12, 7, 3, 2, hull)
        buf.fillRect(7, 7, 3, 2, cockpit)
        val tex = Texture2D(name, 16, 16, TextureFilter.NEAREST, pixelBuffer = buf)
        return Sprite(name, tex, pixelsPerUnit = 16f)
    }
}
