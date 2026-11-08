package com.tengine.app.games

import com.tengine.core.audio.SynthPreset
import com.tengine.core.ecs.Behaviour
import com.tengine.core.ecs.GameObject
import com.tengine.core.graphics.Camera2D
import com.tengine.core.graphics.SortingLayer
import com.tengine.core.graphics.Sprite
import com.tengine.core.graphics.SpriteLibrary
import com.tengine.core.graphics.TextAlign
import com.tengine.core.graphics.components.Light2D
import com.tengine.core.graphics.components.LightType2D
import com.tengine.core.graphics.components.ParticleSystem2D
import com.tengine.core.graphics.components.SpriteRenderer
import com.tengine.core.graphics.components.TrailRenderer2D
import com.tengine.core.input.Input
import com.tengine.core.math.Color
import com.tengine.core.math.Mathf
import com.tengine.core.math.Vector2
import com.tengine.core.physics.BodyType
import com.tengine.core.physics.CircleCollider2D
import com.tengine.core.physics.Collider2D
import com.tengine.core.physics.Rigidbody2D
import com.tengine.core.scene.Scene
import com.tengine.core.ui.UIAnchor
import com.tengine.core.ui.UICanvas
import com.tengine.core.ui.UILabel
import com.tengine.core.ui.UIProgressBar
import com.tengine.core.ui.UIVirtualJoystick
import com.tengine.core.utils.ObjectPool
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Top-Down Arena Survivor Spaceship Controller featuring Auto-Aiming Object-Pooled Plasma Bolts,
 * Wave Spawning, XP Level-Ups, and Camera Shake!
 */
class SpaceShipController : Behaviour("SpaceShipController") {

    override val typeName: String get() = "SpaceShipController"

    var moveSpeed: Float = 7.5f
    var fireInterval: Float = 0.24f
    var kills: Int = 0
    var level: Int = 1
    var hp: Int = 100

    private var fireCooldown: Float = 0f
    private var spawnTimer: Float = 0f
    private val rng = Random(99)

    private var bulletPool: ObjectPool<GameObject>? = null

    override fun onStart() {
        val sc = scene ?: return
        bulletPool = ObjectPool(
            maxCapacity = 128,
            onResetItem = { go -> go.activeSelf = false }
        ) {
            sc.createGameObject("PlasmaBolt").apply {
                tag = "Projectile"
                activeSelf = false
                addComponent(SpriteRenderer(Sprite.DIAMOND, Color(0.35f, 0.98f, 1.0f, 1f)).apply {
                    customSize = Vector2(0.45f, 0.22f)
                    sortingLayer = SortingLayer.EFFECTS
                })
                addComponent(CircleCollider2D(0.2f).apply { isTrigger = true })
                addComponent(Rigidbody2D(BodyType.KINEMATIC))
            }
        }.also { it.prewarm(24) }
    }

    override fun onUpdate(dt: Float) {
        val sc = scene ?: return
        val rb = getComponent<Rigidbody2D>() ?: return

        val moveVec = Vector2(Input.getAxis("Horizontal"), Input.getAxis("Vertical")).clampMagnitude(1f)
        rb.linearVelocity.set(moveVec * moveSpeed)

        // Clamp arena bounds
        val wp = transform.worldPosition
        transform.worldPosition = Vector2(
            Mathf.clamp(wp.x, -13f, 13f),
            Mathf.clamp(wp.y, -8f, 8f)
        )

        val nearestEnemy = findNearestEnemy(sc)
        if (nearestEnemy != null) {
            transform.lookAt2D(nearestEnemy.transform.worldPosition)
        } else if (moveVec.lengthSquared > 0.05f) {
            transform.worldRotationDeg = moveVec.angleDeg
        }

        // Auto-fire at nearest enemy
        fireCooldown -= dt
        if (fireCooldown <= 0f && nearestEnemy != null) {
            fireCooldown = fireInterval
            fireBoltAt(sc, nearestEnemy.transform.worldPosition)
        }

        // Spawn swarm waves around perimeter
        spawnTimer -= dt
        if (spawnTimer <= 0f) {
            spawnTimer = (1.1f - level * 0.08f).coerceAtLeast(0.32f)
            if (sc.findAllWithTag("Enemy").size < 36) {
                spawnSwarmBug(sc)
            }
        }

        // Update HUD
        val hud = sc.uiCanvases.firstOrNull()
        hud?.findWidget<UIProgressBar>("ShipHpBar")?.progress = hp / 100f
        hud?.findWidget<UILabel>("ArenaStats")?.text = "LEVEL $level   |   KILLS: $kills   |   HP: $hp%"
    }

    private fun findNearestEnemy(sc: Scene): GameObject? {
        val myPos = transform.worldPosition
        return sc.findAllWithTag("Enemy").minByOrNull { it.transform.worldPosition.distanceSquaredTo(myPos) }
    }

    private fun fireBoltAt(sc: Scene, targetPos: Vector2) {
        val pool = bulletPool ?: return
        val bolt = pool.obtain()
        val start = transform.worldPosition
        val dir = (targetPos - start).normalized

        bolt.activeSelf = true
        bolt.transform.worldPosition = start + dir * 0.65f
        bolt.transform.worldRotationDeg = dir.angleDeg
        bolt.getComponent<Rigidbody2D>()?.linearVelocity?.set(dir * 18f)

        sc.audioMixer.playSynth(SynthPreset.LASER, volume = 0.42f, pitch = 1.25f)
        sc.scheduler.scheduleOnce(1.3f) {
            if (!bolt.isDestroyed && bolt.activeSelf) {
                pool.release(bolt)
            }
        }
    }

    fun releaseBolt(bolt: GameObject) {
        bulletPool?.release(bolt)
    }

    fun onEnemyEliminated() {
        kills++
        if (kills % 8 == 0) {
            level++
            fireInterval = (fireInterval * 0.88f).coerceAtLeast(0.08f)
            scene?.audioMixer?.playSynth(SynthPreset.POWER_UP, volume = 0.85f)
        }
    }

    private fun spawnSwarmBug(sc: Scene) {
        val angle = rng.nextFloat() * Mathf.TWO_PI
        val spawnPos = transform.worldPosition + Vector2(cos(angle) * 9.5f, sin(angle) * 6.5f)
        sc.createGameObject("SwarmDrone", spawnPos).apply {
            tag = "Enemy"
            addComponent(SpriteRenderer(SpriteLibrary.resolve("slime_0"), Color(1f, 0.42f, 0.32f, 1f)).apply {
                customSize = Vector2(0.85f, 0.85f)
                sortingLayer = SortingLayer.ENTITIES
            })
            addComponent(Rigidbody2D(BodyType.DYNAMIC).apply {
                gravityScale = 0f
                freezeRotation = true
                linearDamping = 2.0f
            })
            addComponent(CircleCollider2D(0.4f))
            addComponent(SwarmEnemyBehaviour())
        }
    }
}

class SwarmEnemyBehaviour : Behaviour("SwarmEnemyBehaviour") {
    override val typeName: String get() = "SwarmEnemyBehaviour"
    var speed: Float = 3.2f
    var hp: Int = 2

    override fun onUpdate(dt: Float) {
        val player = scene?.findWithTag("Player") ?: return
        val rb = getComponent<Rigidbody2D>() ?: return
        val dir = (player.transform.worldPosition - transform.worldPosition).normalized
        rb.linearVelocity.set(dir * speed)
    }

    override fun onTriggerEnter2D(other: Collider2D) {
        if (other.gameObject.tag == "Projectile") {
            val sc = scene ?: return
            val player = sc.findWithTag("Player")?.getComponent<SpaceShipController>()
            player?.releaseBolt(other.gameObject)
            hp--
            if (hp <= 0) {
                player?.onEnemyEliminated()
                sc.audioMixer.playSynth(SynthPreset.EXPLOSION, volume = 0.55f, pitch = 1.4f)
                sc.findMainCamera()?.addTrauma(0.14f)
                NeonKnightPlatformer.spawnDefeatBurst(sc, transform.worldPosition)
                gameObject.destroy()
            } else {
                sc.audioMixer.playSynth(SynthPreset.HIT, volume = 0.45f)
            }
        }
    }
}

/**
 * Showcase Game 3 Builder: "Star Swarm Survivor — Top-Down Bullet Heaven Arena"
 */
object SpaceSurvivorArena {

    fun buildScene(): Scene {
        ProceduralArtPack.ensureInitialized()
        val scene = Scene("Star Swarm Survivor", gravity = Vector2.ZERO)

        // Starfield floor grid markers
        for (gx in -3..3) {
            for (gy in -2..2) {
                scene.createGameObject("StarNode_${gx}_$gy", Vector2(gx * 4f, gy * 4f)).apply {
                    addComponent(SpriteRenderer(Sprite.STAR, Color(0.25f, 0.35f, 0.58f, 0.35f)).apply {
                        customSize = Vector2(0.35f, 0.35f)
                        sortingLayer = SortingLayer.BACKGROUND
                    })
                }
            }
        }

        val ship = scene.createGameObject("StarFighter", Vector2.ZERO).apply {
            tag = "Player"
            addComponent(SpriteRenderer(SpriteLibrary.resolve("ship_fighter")).apply {
                customSize = Vector2(1.15f, 1.15f)
                sortingLayer = SortingLayer.ENTITIES
                orderInLayer = 10
            })
            addComponent(Rigidbody2D(BodyType.DYNAMIC).apply {
                gravityScale = 0f
                freezeRotation = true
            })
            addComponent(CircleCollider2D(0.45f))
            addComponent(TrailRenderer2D(durationSeconds = 0.3f, startWidth = 0.32f))
            addComponent(Light2D(LightType2D.POINT, Color.CYAN, intensity = 1.1f, radius = 5f))
            addComponent(SpaceShipController())
        }

        scene.createGameObject("MainCamera", Vector2.ZERO).apply {
            addComponent(Camera2D(6.0f).apply {
                backgroundColor = Color(0.03f, 0.04f, 0.09f, 1f)
                isMainCamera = true
                followTarget = ship
                followTargetUuid = ship.uuid
                followOffset = Vector2.ZERO
                followSmoothSpeed = 9f
            })
        }

        val hud = scene.addCanvas(UICanvas("SurvivorHUD"))
        hud.addWidget(UIProgressBar("ShipHpBar", UIAnchor.TOP_LEFT, anchoredX = 210f, anchoredY = -44f, width = 340f, height = 26f))
        hud.addWidget(UILabel("ArenaStats", "LEVEL 1   |   KILLS: 0   |   HP: 100%", UIAnchor.TOP_LEFT, anchoredX = 42f, anchoredY = -74f, fontScale = 0.95f))
        hud.addWidget(UIVirtualJoystick("ArenaMoveStick", UIAnchor.BOTTOM_LEFT, anchoredX = 220f, anchoredY = 200f, radiusPx = 125f))

        return scene
    }
}
