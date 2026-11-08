package com.tengine.app.games

import com.tengine.core.audio.SynthPreset
import com.tengine.core.ecs.Behaviour
import com.tengine.core.graphics.Camera2D
import com.tengine.core.graphics.SortingLayer
import com.tengine.core.graphics.Sprite
import com.tengine.core.graphics.SpriteLibrary
import com.tengine.core.graphics.TextAlign
import com.tengine.core.graphics.components.ParticleSystem2D
import com.tengine.core.graphics.components.SpriteRenderer
import com.tengine.core.input.Input
import com.tengine.core.input.TouchPhase
import com.tengine.core.math.Color
import com.tengine.core.math.Vector2
import com.tengine.core.physics.BodyType
import com.tengine.core.physics.BoxCollider2D
import com.tengine.core.physics.CircleCollider2D
import com.tengine.core.physics.DistanceJoint2D
import com.tengine.core.physics.PhysicsMaterial2D
import com.tengine.core.physics.PolygonCollider2D
import com.tengine.core.physics.Rigidbody2D
import com.tengine.core.physics.SpringJoint2D
import com.tengine.core.scene.Scene
import com.tengine.core.ui.UIAnchor
import com.tengine.core.ui.UIButton
import com.tengine.core.ui.UICanvas
import com.tengine.core.ui.UILabel

/**
 * Interactive Touch Impulse Cannon for the 2D Physics & Joint Sandbox Lab!
 */
class SandboxCannonController : Behaviour("SandboxCannonController") {

    override val typeName: String get() = "SandboxCannonController"

    override fun onUpdate(dt: Float) {
        val sc = scene ?: return
        val cam = sc.findMainCamera() ?: return
        val hud = sc.uiCanvases.firstOrNull()

        for (t in Input.touches) {
            if (t.phase == TouchPhase.BEGAN && t.y > 140f) {
                val targetWorld = cam.screenToWorld(
                    t.x,
                    t.y,
                    sc.viewportWidth,
                    sc.viewportHeight
                )
                spawnCannonball(sc, hud, Vector2(-8.5f, 1.5f), targetWorld)
            }
        }
    }

    private fun spawnCannonball(sc: Scene, hud: UICanvas?, origin: Vector2, target: Vector2) {
        val dir = (target - origin).normalized
        val ball = sc.createGameObject("CannonOrb", origin).apply {
            addComponent(SpriteRenderer(Sprite.CIRCLE, Color(0.22f, 0.92f, 1.0f, 1f)).apply {
                customSize = Vector2(0.8f, 0.8f)
                sortingLayer = SortingLayer.ENTITIES
            })
            addComponent(Rigidbody2D(BodyType.DYNAMIC).apply {
                mass = 2.6f
                linearVelocity.set(dir * 19f)
            })
            addComponent(CircleCollider2D(0.4f).apply {
                material = PhysicsMaterial2D.BOUNCY
            })
        }
        sc.audioMixer.playSynth(SynthPreset.LASER, volume = 0.7f)
        camShake(sc, 0.16f)
        sc.scheduler.scheduleOnce(8f) {
            if (!ball.isDestroyed) ball.destroy()
        }
        if (hud != null) {
            hud.findWidget<UILabel>("BodyCounter")?.text =
                "BODIES: ${sc.physicsWorld.bodyCount}  |  TAP ANYWHERE TO LAUNCH ORB!"
        }
    }

    private fun camShake(sc: Scene, amount: Float) {
        sc.findMainCamera()?.addTrauma(amount)
    }
}

/**
 * Showcase Game 2 Builder: "2D Rigid-Body Physics, SAT Polygons & Joint Lab"
 */
object PhysicsSandboxScene {

    fun buildScene(): Scene {
        ProceduralArtPack.ensureInitialized()
        val scene = Scene("2D Physics Sandbox Lab", gravity = Vector2(0f, -18f))

        // Main Camera
        scene.createGameObject("MainCamera", Vector2(0f, 1.5f)).apply {
            addComponent(Camera2D(6.2f).apply {
                backgroundColor = Color(0.06f, 0.08f, 0.13f, 1f)
                isMainCamera = true
            })
            addComponent(SandboxCannonController())
        }

        // Arena Floor & Walls
        createWall(scene, "ArenaFloor", Vector2(0f, -4.2f), Vector2(24f, 1.0f))
        createWall(scene, "LeftWall", Vector2(-11.2f, 1f), Vector2(1.0f, 11f))
        createWall(scene, "RightWall", Vector2(11.2f, 1f), Vector2(1.0f, 11f))
        createWall(scene, "AngledRamp", Vector2(-6.8f, -1.2f), Vector2(4.5f, 0.45f), rotDeg = -18f)

        // 5-Row Pyramid of Reinforced Crates on the right (showcasing 2-point SAT clipping stability!)
        val crateSprite = SpriteLibrary.resolve("crate_wood")
        val baseStartX = 2.5f
        val baseStartY = -3.15f
        for (row in 0 until 5) {
            val count = 5 - row
            for (col in 0 until count) {
                val cx = baseStartX + row * 0.52f + col * 1.04f
                val cy = baseStartY + row * 1.02f
                scene.createGameObject("Crate_${row}_$col", Vector2(cx, cy)).apply {
                    addComponent(SpriteRenderer(crateSprite).apply {
                        customSize = Vector2(1f, 1f)
                        sortingLayer = SortingLayer.ENTITIES
                    })
                    addComponent(Rigidbody2D(BodyType.DYNAMIC).apply { mass = 1.4f })
                    addComponent(BoxCollider2D(Vector2(1f, 1f)).apply {
                        material = PhysicsMaterial2D("Wood", friction = 0.55f, restitution = 0.1f)
                    })
                }
            }
        }

        // Wrecking Pendulum on DistanceJoint2D
        scene.createGameObject("PendulumWreckingBall", Vector2(-1.5f, 2.5f)).apply {
            addComponent(SpriteRenderer(Sprite.CIRCLE, Color(0.98f, 0.52f, 0.18f, 1f)).apply {
                customSize = Vector2(1.3f, 1.3f)
                sortingLayer = SortingLayer.ENTITIES
            })
            addComponent(Rigidbody2D(BodyType.DYNAMIC).apply {
                mass = 4.5f
                linearVelocity.set(6f, 0f)
            })
            addComponent(CircleCollider2D(0.65f).apply { material = PhysicsMaterial2D.METAL })
            addComponent(DistanceJoint2D(targetDistance = 3.8f).apply {
                anchorB = Vector2(-1.5f, 6.3f)
            })
        }

        // Bouncy Spring Orb on SpringJoint2D
        scene.createGameObject("SpringDancer", Vector2(-5.5f, 3.2f)).apply {
            addComponent(SpriteRenderer(Sprite.STAR, Color(0.95f, 0.30f, 0.82f, 1f)).apply {
                customSize = Vector2(1.1f, 1.1f)
                sortingLayer = SortingLayer.ENTITIES
            })
            addComponent(Rigidbody2D(BodyType.DYNAMIC).apply { mass = 1.2f })
            addComponent(PolygonCollider2D(
                listOf(
                    Vector2(-0.45f, -0.4f), Vector2(0.45f, -0.4f),
                    Vector2(0.55f, 0.15f), Vector2(0f, 0.55f), Vector2(-0.55f, 0.15f)
                )
            ).apply { material = PhysicsMaterial2D.BOUNCY })
            addComponent(SpringJoint2D(restLength = 2.2f, stiffness = 38f, damping = 2.8f).apply {
                anchorB = Vector2(-5.5f, 5.8f)
            })
        }

        // Ambient Sparks Emitter at Launcher
        scene.createGameObject("CannonBeacon", Vector2(-8.5f, 1.5f)).apply {
            addComponent(ParticleSystem2D(90).apply {
                emissionRatePerSec = 22f
                speedMin = 0.8f
                speedMax = 2.4f
                startColor = Color.CYAN
                endColor = Color(0.2f, 0.3f, 1f, 0f)
            })
        }

        // Sandbox HUD
        val hud = scene.addCanvas(UICanvas("PhysicsLabHUD"))
        hud.addWidget(
            UILabel(
                "BodyCounter",
                "TAP SCREEN TO FIRE PHYSICS ORBS AT THE PYRAMID!",
                UIAnchor.TOP_CENTER,
                anchoredX = 0f,
                anchoredY = -32f,
                fontScale = 0.95f,
                color = Color.YELLOW,
                align = TextAlign.CENTER
            )
        )
        hud.addWidget(
            UIButton(
                id = "ZeroGravToggle",
                label = "ZERO-G",
                anchor = UIAnchor.TOP_RIGHT,
                anchoredX = -110f,
                anchoredY = -60f,
                width = 160f,
                height = 64f,
                isCircular = false,
                normalColor = Color(0.22f, 0.55f, 0.95f, 0.8f)
            ) {
                val g = scene.physicsWorld.gravity
                if (kotlin.math.abs(g.y) > 0.1f) g.set(0f, 0f) else g.set(0f, -18f)
            }
        )

        return scene
    }

    private fun createWall(
        scene: Scene,
        name: String,
        pos: Vector2,
        size: Vector2,
        rotDeg: Float = 0f
    ) {
        scene.createGameObject(name, pos).apply {
            transform.localRotationDeg = rotDeg
            addComponent(SpriteRenderer(Sprite.WHITE_SQUARE, Color(0.22f, 0.28f, 0.38f, 1f)).apply {
                customSize = size
                sortingLayer = SortingLayer.TERRAIN
            })
            addComponent(Rigidbody2D(BodyType.STATIC))
            addComponent(BoxCollider2D(size))
        }
    }
}
