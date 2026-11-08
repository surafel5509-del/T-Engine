package com.tengine.editor.templates

import com.tengine.core.ecs.GameObject
import com.tengine.core.graphics.BlendMode
import com.tengine.core.graphics.Camera2D
import com.tengine.core.graphics.SortingLayer
import com.tengine.core.graphics.Sprite
import com.tengine.core.graphics.components.Light2D
import com.tengine.core.graphics.components.LightType2D
import com.tengine.core.graphics.components.ParticleSystem2D
import com.tengine.core.graphics.components.SpriteRenderer
import com.tengine.core.graphics.components.Tilemap
import com.tengine.core.math.Color
import com.tengine.core.math.Vector2
import com.tengine.core.physics.BodyType
import com.tengine.core.physics.BoxCollider2D
import com.tengine.core.physics.CircleCollider2D
import com.tengine.core.physics.PhysicsMaterial2D
import com.tengine.core.physics.Rigidbody2D
import com.tengine.core.scene.Scene

/**
 * One-tap GameObject factory presets in T-Engine Studio's "+ Add Entity" menu (like Unity's GameObject -> 2D Object menu!).
 */
object GameObjectTemplates {

    val templateNames = listOf(
        "Empty Object",
        "2D Sprite",
        "Physics Crate (Dynamic)",
        "Bouncy Sphere (Dynamic)",
        "Static Platform",
        "Particle Emitter",
        "2D Point Light",
        "Tilemap Grid",
        "Camera 2D"
    )

    fun create(templateName: String, scene: Scene, spawnPos: Vector2): GameObject {
        return when (templateName) {
            "2D Sprite" -> scene.createGameObject("Sprite", spawnPos).apply {
                addComponent(SpriteRenderer(Sprite.DIAMOND, Color(0.28f, 0.78f, 1.0f, 1f)))
            }
            "Physics Crate (Dynamic)" -> scene.createGameObject("PhysicsCrate", spawnPos).apply {
                addComponent(SpriteRenderer(Sprite.WHITE_SQUARE, Color(0.95f, 0.62f, 0.22f, 1f)))
                addComponent(Rigidbody2D(BodyType.DYNAMIC).apply { mass = 1.5f })
                addComponent(BoxCollider2D(Vector2(1f, 1f)).apply {
                    material = PhysicsMaterial2D("Crate", friction = 0.5f, restitution = 0.15f)
                })
            }
            "Bouncy Sphere (Dynamic)" -> scene.createGameObject("BouncySphere", spawnPos).apply {
                addComponent(SpriteRenderer(Sprite.CIRCLE, Color(0.96f, 0.30f, 0.65f, 1f)))
                addComponent(Rigidbody2D(BodyType.DYNAMIC).apply { mass = 1.0f })
                addComponent(CircleCollider2D(0.5f).apply {
                    material = PhysicsMaterial2D.BOUNCY
                })
            }
            "Static Platform" -> scene.createGameObject("Platform", spawnPos).apply {
                addComponent(SpriteRenderer(Sprite.WHITE_SQUARE, Color(0.26f, 0.32f, 0.44f, 1f)).apply {
                    customSize = Vector2(5f, 0.6f)
                })
                addComponent(Rigidbody2D(BodyType.STATIC))
                addComponent(BoxCollider2D(Vector2(5f, 0.6f)))
            }
            "Particle Emitter" -> scene.createGameObject("ParticleEmitter", spawnPos).apply {
                addComponent(ParticleSystem2D(180).apply {
                    emissionRatePerSec = 40f
                    blendMode = BlendMode.ADDITIVE
                    sortingLayer = SortingLayer.EFFECTS
                })
            }
            "2D Point Light" -> scene.createGameObject("PointLight2D", spawnPos).apply {
                addComponent(Light2D(LightType2D.POINT, Color(1f, 0.85f, 0.45f, 1f), intensity = 1.4f, radius = 6f))
            }
            "Tilemap Grid" -> scene.createGameObject("Tilemap", Vector2.ZERO).apply {
                val tm = addComponent(Tilemap(cellSize = 1f))
                tm.fillRect(-5, -3, 5, -3, tileId = 2)
                tm.rebuildColliders()
            }
            "Camera 2D" -> scene.createGameObject("Camera2D", spawnPos).apply {
                addComponent(Camera2D(5.4f).apply { isMainCamera = false })
            }
            else -> scene.createGameObject("EmptyObject", spawnPos)
        }
    }
}
