package com.tengine.app.games

import com.tengine.core.Time
import com.tengine.core.ai.BehaviorTree
import com.tengine.core.ai.NodeStatus
import com.tengine.core.audio.AudioListener2D
import com.tengine.core.audio.SynthPreset
import com.tengine.core.ecs.Behaviour
import com.tengine.core.ecs.InspectorProperty
import com.tengine.core.graphics.BlendMode
import com.tengine.core.graphics.Camera2D
import com.tengine.core.graphics.SortingLayer
import com.tengine.core.graphics.Sprite
import com.tengine.core.graphics.SpriteLibrary
import com.tengine.core.graphics.TextAlign
import com.tengine.core.graphics.components.AnimationLoopMode
import com.tengine.core.graphics.components.Animator2D
import com.tengine.core.graphics.components.Light2D
import com.tengine.core.graphics.components.LightType2D
import com.tengine.core.graphics.components.ParallaxLayer
import com.tengine.core.graphics.components.ParticleSystem2D
import com.tengine.core.graphics.components.SpriteAnimationClip
import com.tengine.core.graphics.components.SpriteRenderer
import com.tengine.core.graphics.components.Tilemap
import com.tengine.core.graphics.components.TrailRenderer2D
import com.tengine.core.input.Input
import com.tengine.core.math.AABB
import com.tengine.core.math.Color
import com.tengine.core.math.Easing
import com.tengine.core.math.Mathf
import com.tengine.core.math.Vector2
import com.tengine.core.physics.BodyType
import com.tengine.core.physics.BoxCollider2D
import com.tengine.core.physics.CircleCollider2D
import com.tengine.core.physics.Collider2D
import com.tengine.core.physics.CollisionEvent2D
import com.tengine.core.physics.PhysicsMaterial2D
import com.tengine.core.physics.Rigidbody2D
import com.tengine.core.scene.Scene
import com.tengine.core.serialization.JsonObject
import com.tengine.core.ui.UIAnchor
import com.tengine.core.ui.UIButton
import com.tengine.core.ui.UICanvas
import com.tengine.core.ui.UILabel
import com.tengine.core.ui.UIProgressBar
import com.tengine.core.ui.UIVirtualJoystick
import kotlin.math.abs
import kotlin.math.sin

/**
 * Responsive 2D Platformer Hero Controller with Coyote Time, Jump Buffering,
 * Variable Jump Cut, Air Dash, Trail VFX, Camera Shake, and HUD integration!
 */
class KnightController : Behaviour("KnightController") {

    override val typeName: String get() = "KnightController"

    var moveSpeed: Float = 7.2f
    var jumpForce: Float = 13.8f
    var dashSpeed: Float = 17.5f
    var maxHp: Int = 100
    var currentHp: Int = 100
    var collectedGems: Int = 0

    private var coyoteTimer: Float = 0f
    private var jumpBufferTimer: Float = 0f
    private var dashTimer: Float = 0f
    private var dashCooldown: Float = 0f
    private var invulnTimer: Float = 0f
    private var facingSign: Float = 1f
    private val spawnPoint = Vector2(-7f, 1.5f)

    override fun onStart() {
        spawnPoint.set(transform.worldPosition)
    }

    override fun onUpdate(dt: Float) {
        val rb = getComponent<Rigidbody2D>() ?: return
        val sr = getComponent<SpriteRenderer>()
        val anim = getComponent<Animator2D>()
        val trail = getComponent<TrailRenderer2D>()
        val world = scene?.physicsWorld ?: return

        // Ground check raycast downward
        val footOrigin = transform.worldPosition + Vector2(0f, -0.52f)
        val groundHit = world.raycast(footOrigin, Vector2.DOWN, maxDistance = 0.22f)
        val isGrounded = groundHit != null && groundHit.collider.gameObject !== gameObject

        if (isGrounded) coyoteTimer = 0.12f else coyoteTimer -= dt

        if (Input.isActionJustPressed("Jump")) {
            jumpBufferTimer = 0.12f
        } else {
            jumpBufferTimer -= dt
        }

        if (dashCooldown > 0f) dashCooldown -= dt
        if (invulnTimer > 0f) invulnTimer -= dt

        val horiz = Input.getAxis("Horizontal")
        if (abs(horiz) > 0.1f) {
            facingSign = if (horiz > 0f) 1f else -1f
            sr?.flipX = facingSign < 0f
        }

        // Trigger Dash
        if (Input.isActionJustPressed("Dash") && dashCooldown <= 0f) {
            dashTimer = 0.16f
            dashCooldown = 0.75f
            rb.linearVelocity.set(facingSign * dashSpeed, 0f)
            scene?.audioMixer?.playSynth(SynthPreset.DASH, volume = 0.75f)
            scene?.findMainCamera()?.addTrauma(0.24f)
        }

        if (dashTimer > 0f) {
            dashTimer -= dt
            rb.linearVelocity.x = facingSign * dashSpeed
            rb.linearVelocity.y = 0f
            trail?.emitting = true
        } else {
            trail?.emitting = false
            // Smooth horizontal control
            val targetVx = horiz * moveSpeed
            rb.linearVelocity.x = Mathf.moveTowards(rb.linearVelocity.x, targetVx, 55f * dt)

            // Execute buffered / coyote jump
            if (jumpBufferTimer > 0f && coyoteTimer > 0f) {
                rb.linearVelocity.y = jumpForce
                jumpBufferTimer = 0f
                coyoteTimer = 0f
                scene?.audioMixer?.playSynth(SynthPreset.JUMP, volume = 0.8f)
            }

            // Variable jump height cut on early release
            if (Input.isActionJustReleased("Jump") && rb.linearVelocity.y > 4f) {
                rb.linearVelocity.y *= 0.52f
            }
        }

        // Update Animator2D state
        if (anim != null) {
            val targetClip = if (abs(rb.linearVelocity.x) > 0.6f) "Run" else "Idle"
            anim.play(targetClip)
        }

        // Respawn if fallen into pit
        if (transform.worldY < -8f) {
            takeDamage(25)
            transform.worldPosition = spawnPoint.copyOf()
            rb.linearVelocity.setZero()
        }

        // Update HUD widgets
        val hud = scene?.uiCanvases?.firstOrNull()
        hud?.findWidget<UIProgressBar>("HpBar")?.progress = currentHp.toFloat() / maxHp
        hud?.findWidget<UILabel>("ScoreLabel")?.text = "GEMS: $collectedGems   HP: $currentHp/$maxHp"
    }

    fun takeDamage(amount: Int) {
        if (invulnTimer > 0f) return
        invulnTimer = 0.8f
        currentHp = (currentHp - amount).coerceAtLeast(0)
        scene?.audioMixer?.playSynth(SynthPreset.HIT, volume = 0.9f)
        scene?.findMainCamera()?.addTrauma(0.42f)
        if (currentHp <= 0) {
            currentHp = maxHp
            transform.worldPosition = spawnPoint.copyOf()
            getComponent<Rigidbody2D>()?.linearVelocity?.setZero()
        }
    }

    override fun onCollisionEnter2D(collision: CollisionEvent2D) {
        val other = collision.otherGameObject
        if (other.tag == "Enemy") {
            val rb = getComponent<Rigidbody2D>() ?: return
            // Stomp check: player center comfortably above enemy center
            if (transform.worldY > other.transform.worldY + 0.35f && rb.linearVelocity.y <= 1f) {
                rb.linearVelocity.y = jumpForce * 0.82f
                collectedGems += 2
                scene?.audioMixer?.playSynth(SynthPreset.EXPLOSION, volume = 0.8f, pitch = 1.25f)
                scene?.findMainCamera()?.addTrauma(0.32f)
                NeonKnightPlatformer.spawnDefeatBurst(scene ?: return, other.transform.worldPosition)
                other.destroy()
            } else {
                takeDamage(20)
                val knockDir = if (transform.worldX < other.transform.worldX) -1f else 1f
                rb.linearVelocity.set(knockDir * 8f, 7f)
            }
        }
    }

    override fun serialize(): JsonObject = super.serialize()
        .put("moveSpeed", moveSpeed)
        .put("jumpForce", jumpForce)
        .put("maxHp", maxHp)

    override fun deserialize(data: JsonObject) {
        super.deserialize(data)
        moveSpeed = data.getFloat("moveSpeed", 7.2f)
        jumpForce = data.getFloat("jumpForce", 13.8f)
        maxHp = data.getInt("maxHp", 100)
        currentHp = maxHp
    }

    override fun inspectableProperties(): List<InspectorProperty> = listOf(
        InspectorProperty.FloatProp("Move Speed", 1f, 25f, 0.5f, { moveSpeed }, { moveSpeed = it }),
        InspectorProperty.FloatProp("Jump Force", 2f, 30f, 0.5f, { jumpForce }, { jumpForce = it }),
        InspectorProperty.FloatProp("Dash Speed", 5f, 35f, 1f, { dashSpeed }, { dashSpeed = it }),
        InspectorProperty.IntProp("Max HP", 10, 500, { maxHp }, { maxHp = it; currentHp = it })
    )
}

/**
 * Behavior-Tree Driven Patrol & Chase Enemy AI!
 * Uses Physics2D cliff raycasts so enemies turn cleanly at platform ledges!
 */
class PatrolEnemyAI : Behaviour("PatrolEnemyAI") {

    override val typeName: String get() = "PatrolEnemyAI"

    var patrolSpeed: Float = 2.4f
    var chaseSpeed: Float = 4.2f
    var aggroRange: Float = 5.5f
    private var moveDir: Float = 1f

    private val behaviorTree = BehaviorTree(
        BehaviorTree.selector(
            // Branch 1: Chase Player if within aggro distance on similar height
            BehaviorTree.sequence(
                BehaviorTree.condition { bb ->
                    val dist = bb.get<Float>("playerDist") ?: 999f
                    val dy = bb.get<Float>("playerDy") ?: 999f
                    dist < aggroRange && dy < 2.2f
                },
                BehaviorTree.action { bb, _ ->
                    val playerX = bb.get<Float>("playerX") ?: transform.worldX
                    moveDir = if (playerX >= transform.worldX) 1f else -1f
                    driveHorizontal(chaseSpeed)
                    NodeStatus.SUCCESS
                }
            ),
            // Branch 2: Default Ledge-Aware Patrol
            BehaviorTree.action { _, _ ->
                driveHorizontal(patrolSpeed)
                NodeStatus.SUCCESS
            }
        )
    )

    private fun driveHorizontal(speed: Float) {
        val rb = getComponent<Rigidbody2D>() ?: return
        val world = scene?.physicsWorld ?: return

        // Ledge check ahead + wall check ahead
        val probeOrigin = transform.worldPosition + Vector2(moveDir * 0.52f, 0f)
        val groundAhead = world.raycast(probeOrigin, Vector2.DOWN, maxDistance = 1.0f)
        val wallAhead = world.raycast(transform.worldPosition, Vector2(moveDir, 0f), maxDistance = 0.55f)

        if (groundAhead == null || (wallAhead != null && wallAhead.collider.gameObject.tag != "Player")) {
            moveDir = -moveDir
        }

        rb.linearVelocity.x = moveDir * speed
        getComponent<SpriteRenderer>()?.flipX = moveDir < 0f
    }

    override fun onUpdate(dt: Float) {
        val player = scene?.findWithTag("Player")
        if (player != null) {
            behaviorTree.blackboard["playerDist"] = player.transform.worldPosition.distanceTo(transform.worldPosition)
            behaviorTree.blackboard["playerDy"] = abs(player.transform.worldY - transform.worldY)
            behaviorTree.blackboard["playerX"] = player.transform.worldX
        } else {
            behaviorTree.blackboard["playerDist"] = 999f
        }
        behaviorTree.tick(dt)
    }

    override fun inspectableProperties(): List<InspectorProperty> = listOf(
        InspectorProperty.FloatProp("Patrol Speed", 0.5f, 12f, 0.25f, { patrolSpeed }, { patrolSpeed = it }),
        InspectorProperty.FloatProp("Chase Speed", 1f, 15f, 0.25f, { chaseSpeed }, { chaseSpeed = it }),
        InspectorProperty.FloatProp("Aggro Range", 1f, 20f, 0.5f, { aggroRange }, { aggroRange = it })
    )
}

class SpinningGemBehaviour : Behaviour("SpinningGemBehaviour") {
    override val typeName: String get() = "SpinningGemBehaviour"
    private var baseY: Float = 0f

    override fun onStart() {
        baseY = transform.localY
        scene?.tweens?.tweenScale(
            transform,
            Vector2(1.25f, 1.25f),
            duration = 0.65f,
            easing = Easing.IN_OUT_SINE,
            yoyo = true,
            repeatCount = -1
        )
    }

    override fun onUpdate(dt: Float) {
        transform.rotate(110f * dt)
        transform.localY = baseY + sin(Time.time * 3.5f + instanceId) * 0.16f
    }

    override fun onTriggerEnter2D(other: Collider2D) {
        if (other.gameObject.tag == "Player") {
            other.gameObject.getComponent<KnightController>()?.let { it.collectedGems++ }
            scene?.audioMixer?.playSynth(SynthPreset.COIN, volume = 0.85f)
            gameObject.destroy()
        }
    }
}

class MovingPlatformBehaviour : Behaviour("MovingPlatformBehaviour") {
    override val typeName: String get() = "MovingPlatformBehaviour"
    var amplitudeX: Float = 3.2f
    var speed: Float = 1.4f
    private var originX: Float = 0f

    override fun onStart() {
        originX = transform.worldX
    }

    override fun onFixedUpdate(fixedDt: Float) {
        val rb = getComponent<Rigidbody2D>() ?: return
        val targetX = originX + sin(Time.time * speed) * amplitudeX
        rb.linearVelocity.x = (targetX - transform.worldX) / fixedDt.coerceAtLeast(0.001f)
    }

    override fun inspectableProperties(): List<InspectorProperty> = listOf(
        InspectorProperty.FloatProp("Travel X", 0f, 15f, 0.5f, { amplitudeX }, { amplitudeX = it }),
        InspectorProperty.FloatProp("Cycle Speed", 0.2f, 6f, 0.2f, { speed }, { speed = it })
    )
}

/**
 * Showcase Game 1 Builder: "Neon Knight — Cyber Citadel"
 */
object NeonKnightPlatformer {

    fun buildScene(): Scene {
        ProceduralArtPack.ensureInitialized()
        val scene = Scene("Neon Knight Platformer", gravity = Vector2(0f, -26f))

        // 1. Global moody 2D Ambient Light
        scene.createGameObject("GlobalLight2D").apply {
            addComponent(Light2D(LightType2D.GLOBAL_AMBIENT, Color(0.55f, 0.60f, 0.75f, 1f), intensity = 0.72f))
        }

        // 2. Parallax Cyber Towers Background
        for (i in -3..5) {
            scene.createGameObject("ParallaxTower_$i", Vector2(i * 6.5f, 1.5f)).apply {
                val h = 6f + (abs(i * 37) % 5)
                addComponent(SpriteRenderer(Sprite.WHITE_SQUARE, Color(0.11f, 0.14f, 0.24f, 0.85f)).apply {
                    customSize = Vector2(3.2f, h)
                    sortingLayer = SortingLayer.PARALLAX
                    orderInLayer = -10
                })
                addComponent(ParallaxLayer(parallaxFactorX = 0.65f, parallaxFactorY = 0.3f))
            }
        }

        // 3. Citadel Tilemap World (Greedy-merged solid colliders!)
        val worldObj = scene.createGameObject("CitadelTilemap", Vector2.ZERO)
        val tilemap = worldObj.addComponent(Tilemap(cellSize = 1f).apply { isLit = true })
        worldObj.addComponent(Rigidbody2D(BodyType.STATIC))

        // Main floors & elevated ledges
        tilemap.fillRect(-12, -3, 4, -2, tileId = 1)
        tilemap.fillRect(-12, -1, 4, -1, tileId = 2)

        tilemap.fillRect(8, -3, 26, -2, tileId = 1)
        tilemap.fillRect(8, -1, 26, -1, tileId = 2)

        // Lava hazard gap (x = 5..7)
        tilemap.fillRect(5, -3, 7, -3, tileId = 4)

        // Floating neon platforms
        tilemap.fillRect(-4, 2, 0, 2, tileId = 3)
        tilemap.fillRect(11, 2, 16, 2, tileId = 3)
        tilemap.fillRect(18, 5, 23, 5, tileId = 3)

        // Left & Right boundary walls
        tilemap.fillRect(-13, -3, -13, 8, tileId = 1)
        tilemap.fillRect(27, -3, 27, 8, tileId = 1)
        tilemap.rebuildColliders()

        // 4. Kinematic Moving Bridge over Lava Gap
        scene.createGameObject("MovingBridge", Vector2(6f, 0.2f)).apply {
            addComponent(SpriteRenderer(Sprite.WHITE_SQUARE, Color(0.20f, 0.90f, 0.95f, 1f)).apply {
                customSize = Vector2(2.8f, 0.45f)
                sortingLayer = SortingLayer.TERRAIN
            })
            addComponent(Rigidbody2D(BodyType.KINEMATIC))
            addComponent(BoxCollider2D(Vector2(2.8f, 0.45f)))
            addComponent(MovingPlatformBehaviour().apply { amplitudeX = 1.8f; speed = 1.6f })
        }

        // 5. Dynamic Point Torches along the level
        val torchCoords = listOf(Vector2(-7f, 1.5f), Vector2(-2f, 4f), Vector2(6f, -1.5f), Vector2(14f, 4f), Vector2(21f, 7f))
        for ((idx, pos) in torchCoords.withIndex()) {
            scene.createGameObject("CyberTorch_$idx", pos).apply {
                val c = if (idx == 2) Color(1f, 0.38f, 0.14f, 1f) else Color(0.22f, 0.88f, 1f, 1f)
                addComponent(Light2D(LightType2D.POINT, c, intensity = 1.35f, radius = 6.5f).apply {
                    flickerAmplitude = 0.12f
                })
            }
        }

        // 6. Hero ("Neon Knight")
        val hero = scene.createGameObject("NeonKnight", Vector2(-8f, 1.2f)).apply {
            tag = "Player"
            addComponent(SpriteRenderer(SpriteLibrary.resolve("knight_idle_0")).apply {
                customSize = Vector2(1.1f, 1.1f)
                sortingLayer = SortingLayer.ENTITIES
                orderInLayer = 10
                isLit = true
            })
            addComponent(Animator2D().apply {
                addClip(
                    SpriteAnimationClip(
                        "Idle",
                        listOf(SpriteLibrary.resolve("knight_idle_0"), SpriteLibrary.resolve("knight_idle_1")),
                        fps = 3.5f,
                        loopMode = AnimationLoopMode.LOOP
                    )
                )
                addClip(
                    SpriteAnimationClip(
                        "Run",
                        listOf(SpriteLibrary.resolve("knight_run_0"), SpriteLibrary.resolve("knight_run_1")),
                        fps = 10f,
                        loopMode = AnimationLoopMode.LOOP
                    )
                )
            })
            addComponent(Rigidbody2D(BodyType.DYNAMIC).apply {
                mass = 1.4f
                freezeRotation = true
                gravityScale = 1.0f
            })
            addComponent(BoxCollider2D(Vector2(0.75f, 1.0f)).apply {
                material = PhysicsMaterial2D("PlayerFeet", friction = 0.0f, restitution = 0.0f)
            })
            addComponent(TrailRenderer2D(durationSeconds = 0.22f, startWidth = 0.55f).apply {
                emitting = false
            })
            addComponent(AudioListener2D())
            addComponent(KnightController())
        }

        // 7. Patrol & Chase Slime Enemies
        val enemySpawns = listOf(Vector2(0f, 0.8f), Vector2(13f, 0.8f), Vector2(13.5f, 3.8f), Vector2(20.5f, 6.8f))
        for ((idx, pos) in enemySpawns.withIndex()) {
            scene.createGameObject("CyberSlime_$idx", pos).apply {
                tag = "Enemy"
                addComponent(SpriteRenderer(SpriteLibrary.resolve("slime_0")).apply {
                    customSize = Vector2(1.0f, 0.9f)
                    sortingLayer = SortingLayer.ENTITIES
                    orderInLayer = 5
                    isLit = true
                })
                addComponent(Animator2D().apply {
                    addClip(
                        SpriteAnimationClip(
                            "Squash",
                            listOf(SpriteLibrary.resolve("slime_0"), SpriteLibrary.resolve("slime_1")),
                            fps = 5f
                        )
                    )
                })
                addComponent(Rigidbody2D(BodyType.DYNAMIC).apply {
                    mass = 1.2f
                    freezeRotation = true
                })
                addComponent(BoxCollider2D(Vector2(0.85f, 0.78f)).apply {
                    material = PhysicsMaterial2D.ICE
                })
                addComponent(PatrolEnemyAI())
            }
        }

        // 8. Collectible Neon Gems
        val gemPositions = listOf(
            Vector2(-3.5f, 3.3f), Vector2(-1.5f, 3.3f), Vector2(0.2f, 3.3f),
            Vector2(6f, 1.8f), Vector2(12f, 3.5f), Vector2(15f, 3.5f),
            Vector2(19.5f, 6.5f), Vector2(22f, 6.5f)
        )
        for ((idx, pos) in gemPositions.withIndex()) {
            scene.createGameObject("NeonGem_$idx", pos).apply {
                tag = "Collectible"
                addComponent(SpriteRenderer(Sprite.DIAMOND, Color(0.25f, 0.98f, 0.88f, 1f)).apply {
                    customSize = Vector2(0.58f, 0.58f)
                    sortingLayer = SortingLayer.ENTITIES
                })
                addComponent(CircleCollider2D(0.35f).apply { isTrigger = true })
                addComponent(SpinningGemBehaviour())
            }
        }

        // 9. Main Smooth-Tracking Camera2D
        scene.createGameObject("MainCamera", hero.transform.worldPosition).apply {
            addComponent(Camera2D(5.5f).apply {
                backgroundColor = Color(0.05f, 0.06f, 0.12f, 1f)
                isMainCamera = true
                followTarget = hero
                followTargetUuid = hero.uuid
                followSmoothSpeed = 8.0f
                followOffset = Vector2(0f, 1.1f)
                clampBounds = AABB(-6f, 0.5f, 20f, 9f)
            })
        }

        // 10. Touch HUD & Controls Canvas
        val hud = scene.addCanvas(UICanvas("PlatformerHUD"))
        hud.addWidget(UIProgressBar("HpBar", UIAnchor.TOP_LEFT, anchoredX = 210f, anchoredY = -44f, width = 340f, height = 28f))
        hud.addWidget(UILabel("ScoreLabel", "GEMS: 0   HP: 100/100", UIAnchor.TOP_LEFT, anchoredX = 42f, anchoredY = -74f, fontScale = 1.0f))
        hud.addWidget(UILabel("HintLabel", "STOMP SLIMES  |  COLLECT GEMS", UIAnchor.TOP_CENTER, anchoredX = 0f, anchoredY = -28f, fontScale = 0.85f, color = Color.CYAN, align = TextAlign.CENTER))

        hud.addWidget(UIVirtualJoystick("MoveStick", UIAnchor.BOTTOM_LEFT, anchoredX = 220f, anchoredY = 200f, radiusPx = 120f))
        hud.addWidget(
            UIButton(
                id = "JumpButton",
                label = "JUMP",
                anchor = UIAnchor.BOTTOM_RIGHT,
                anchoredX = -190f,
                anchoredY = 185f,
                width = 150f,
                height = 150f,
                boundActionName = "Jump",
                normalColor = Color(0.18f, 0.72f, 0.98f, 0.65f)
            )
        )
        hud.addWidget(
            UIButton(
                id = "DashButton",
                label = "DASH",
                anchor = UIAnchor.BOTTOM_RIGHT,
                anchoredX = -370f,
                anchoredY = 155f,
                width = 125f,
                height = 125f,
                boundActionName = "Dash",
                normalColor = Color(0.95f, 0.35f, 0.65f, 0.65f)
            )
        )

        return scene
    }

    fun spawnDefeatBurst(scene: Scene, position: Vector2) {
        val fx = scene.createGameObject("SlimeBurstVFX", position)
        val ps = fx.addComponent(ParticleSystem2D(64).apply {
            emissionRatePerSec = 0f
            angleMinDeg = 0f
            angleMaxDeg = 360f
            speedMin = 2.5f
            speedMax = 7.5f
            lifetimeMin = 0.3f
            lifetimeMax = 0.65f
            startColor = Color(1f, 0.45f, 0.85f, 1f)
            endColor = Color(0.35f, 0.85f, 1f, 0f)
        })
        ps.emitBurst(28)
        scene.scheduler.scheduleOnce(0.8f) { fx.destroy() }
    }
}
