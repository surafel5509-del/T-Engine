package com.tengine.core.graphics.components

import com.tengine.core.ecs.Component
import com.tengine.core.ecs.InspectorProperty
import com.tengine.core.graphics.BlendMode
import com.tengine.core.graphics.SortingLayer
import com.tengine.core.graphics.Sprite
import com.tengine.core.graphics.SpriteBatch
import com.tengine.core.math.Color
import com.tengine.core.math.ColorGradient
import com.tengine.core.math.Mathf
import com.tengine.core.math.Vector2
import com.tengine.core.serialization.JsonObject
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

enum class EmissionShape {
    POINT,
    CIRCLE,
    CONE,
    BOX
}

/**
 * Zero-GC Pre-Allocated 2D Particle System Emitter (modeled after Unity's Shuriken 2D).
 * Uses flat primitive float arrays (`FloatArray`) so thousands of particles simulate and batch
 * at 60-120 FPS on Android with ZERO per-frame heap allocations!
 */
class ParticleSystem2D(
    val maxParticles: Int = 256
) : Component("ParticleSystem2D") {

    override val typeName: String get() = "ParticleSystem2D"

    var sprite: Sprite = Sprite.SOFT_GLOW
    var sortingLayer: SortingLayer = SortingLayer.EFFECTS
    var orderInLayer: Int = 10
    var blendMode: BlendMode = BlendMode.ADDITIVE

    var emissionShape: EmissionShape = EmissionShape.CONE
    var shapeRadius: Float = 0.25f
    var boxExtents: Vector2 = Vector2(0.5f, 0.2f)

    var playOnAwake: Boolean = true
    var looping: Boolean = true
    var isEmitting: Boolean = true
    var worldSpaceSimulation: Boolean = true

    var emissionRatePerSec: Float = 35f
    var lifetimeMin: Float = 0.4f
    var lifetimeMax: Float = 1.1f

    var speedMin: Float = 1.8f
    var speedMax: Float = 4.5f
    var angleMinDeg: Float = 60f
    var angleMaxDeg: Float = 120f

    var gravityY: Float = -2.5f
    var drag: Float = 0.4f

    var startSizeMin: Float = 0.18f
    var startSizeMax: Float = 0.36f
    var endSizeMultiplier: Float = 0.1f

    var angularSpeedMinDeg: Float = -90f
    var angularSpeedMaxDeg: Float = 90f

    var startColor: Color = Color(1f, 0.78f, 0.25f, 1f)
    var endColor: Color = Color(0.95f, 0.2f, 0.08f, 0f)
    var customGradient: ColorGradient? = null

    // Structure-of-Arrays (SoA) zero-GC particle storage
    private val posX = FloatArray(maxParticles)
    private val posY = FloatArray(maxParticles)
    private val velX = FloatArray(maxParticles)
    private val velY = FloatArray(maxParticles)
    private val age = FloatArray(maxParticles)
    private val maxAge = FloatArray(maxParticles)
    private val startSize = FloatArray(maxParticles)
    private val rotationDeg = FloatArray(maxParticles)
    private val angVelDeg = FloatArray(maxParticles)

    var activeCount: Int = 0
        private set

    private var spawnAccumulator: Float = 0f
    private val rng = Random(System.nanoTime())

    override fun onStart() {
        isEmitting = playOnAwake
    }

    fun play() {
        isEmitting = true
    }

    fun stop(clearExisting: Boolean = false) {
        isEmitting = false
        if (clearExisting) activeCount = 0
    }

    fun emitBurst(count: Int) {
        repeat(count.coerceAtLeast(0)) {
            if (activeCount < maxParticles) {
                spawnSingleParticle()
            }
        }
    }

    override fun onUpdate(dt: Float) {
        if (dt <= 0f) return

        // Spawn continuous particles
        if (isEmitting && emissionRatePerSec > 0f) {
            spawnAccumulator += emissionRatePerSec * dt
            val toSpawn = spawnAccumulator.toInt()
            spawnAccumulator -= toSpawn
            repeat(toSpawn) {
                if (activeCount < maxParticles) {
                    spawnSingleParticle()
                }
            }
        }

        // Update existing particles (swap-with-last removal in O(1))
        var i = 0
        val dampFactor = 1f / (1f + drag * dt)
        while (i < activeCount) {
            age[i] += dt
            if (age[i] >= maxAge[i]) {
                // Swap last active particle into slot i
                val last = activeCount - 1
                if (i != last) {
                    posX[i] = posX[last]
                    posY[i] = posY[last]
                    velX[i] = velX[last]
                    velY[i] = velY[last]
                    age[i] = age[last]
                    maxAge[i] = maxAge[last]
                    startSize[i] = startSize[last]
                    rotationDeg[i] = rotationDeg[last]
                    angVelDeg[i] = angVelDeg[last]
                }
                activeCount--
                continue
            }

            velY[i] += gravityY * dt
            velX[i] *= dampFactor
            velY[i] *= dampFactor
            posX[i] += velX[i] * dt
            posY[i] += velY[i] * dt
            rotationDeg[i] += angVelDeg[i] * dt
            i++
        }
    }

    private fun spawnSingleParticle() {
        val idx = activeCount
        if (idx >= maxParticles) return
        activeCount++

        val origin = if (isAttached && worldSpaceSimulation) transform.worldPosition else Vector2.ZERO
        val baseRot = if (isAttached) transform.worldRotationDeg else 0f

        var sx = origin.x
        var sy = origin.y
        when (emissionShape) {
            EmissionShape.POINT -> {}
            EmissionShape.CIRCLE, EmissionShape.CONE -> {
                val a = rng.nextFloat() * Mathf.TWO_PI
                val r = sqrt(rng.nextFloat()) * shapeRadius
                sx += cos(a) * r
                sy += sin(a) * r
            }
            EmissionShape.BOX -> {
                sx += (rng.nextFloat() * 2f - 1f) * boxExtents.x
                sy += (rng.nextFloat() * 2f - 1f) * boxExtents.y
            }
        }

        val emitAngleDeg = baseRot + randRange(angleMinDeg, angleMaxDeg)
        val emitRad = emitAngleDeg * Mathf.DEG_TO_RAD
        val speed = randRange(speedMin, speedMax)

        posX[idx] = sx
        posY[idx] = sy
        velX[idx] = cos(emitRad) * speed
        velY[idx] = sin(emitRad) * speed
        age[idx] = 0f
        maxAge[idx] = randRange(lifetimeMin, lifetimeMax).coerceAtLeast(0.05f)
        startSize[idx] = randRange(startSizeMin, startSizeMax)
        rotationDeg[idx] = rng.nextFloat() * 360f
        angVelDeg[idx] = randRange(angularSpeedMinDeg, angularSpeedMaxDeg)
    }

    fun render(batch: SpriteBatch) {
        if (activeCount == 0) return
        batch.setState(blendMode, isLit = false)

        val offset = if (!worldSpaceSimulation && isAttached) transform.worldPosition else Vector2.ZERO
        val grad = customGradient

        for (i in 0 until activeCount) {
            val t = Mathf.clamp01(age[i] / maxAge[i])
            val size = startSize[i] * Mathf.lerp(1f, endSizeMultiplier, t)
            val color = grad?.evaluate(t) ?: Color.lerp(startColor, endColor, t)
            batch.drawSprite(
                sprite = sprite,
                worldX = posX[i] + offset.x,
                worldY = posY[i] + offset.y,
                width = size,
                height = size,
                rotationDeg = rotationDeg[i],
                color = color
            )
        }
    }

    private fun randRange(minVal: Float, maxVal: Float): Float =
        if (maxVal <= minVal) minVal else minVal + rng.nextFloat() * (maxVal - minVal)

    override fun serialize(): JsonObject = super.serialize()
        .put("blendMode", blendMode.name)
        .put("emissionShape", emissionShape.name)
        .put("emissionRate", emissionRatePerSec)
        .put("lifetimeMin", lifetimeMin)
        .put("lifetimeMax", lifetimeMax)
        .put("speedMin", speedMin)
        .put("speedMax", speedMax)
        .put("gravityY", gravityY)
        .put("startSizeMin", startSizeMin)
        .put("startSizeMax", startSizeMax)
        .put("endSizeMultiplier", endSizeMultiplier)
        .putColor("startColor", startColor)
        .putColor("endColor", endColor)

    override fun deserialize(data: JsonObject) {
        super.deserialize(data)
        blendMode = runCatching { BlendMode.valueOf(data.getString("blendMode", "ADDITIVE")) }
            .getOrDefault(BlendMode.ADDITIVE)
        emissionShape = runCatching { EmissionShape.valueOf(data.getString("emissionShape", "CONE")) }
            .getOrDefault(EmissionShape.CONE)
        emissionRatePerSec = data.getFloat("emissionRate", 35f)
        lifetimeMin = data.getFloat("lifetimeMin", 0.4f)
        lifetimeMax = data.getFloat("lifetimeMax", 1.1f)
        speedMin = data.getFloat("speedMin", 1.8f)
        speedMax = data.getFloat("speedMax", 4.5f)
        gravityY = data.getFloat("gravityY", -2.5f)
        startSizeMin = data.getFloat("startSizeMin", 0.18f)
        startSizeMax = data.getFloat("startSizeMax", 0.36f)
        endSizeMultiplier = data.getFloat("endSizeMultiplier", 0.1f)
        startColor = data.getColor("startColor", startColor)
        endColor = data.getColor("endColor", endColor)
    }

    override fun inspectableProperties(): List<InspectorProperty> = listOf(
        InspectorProperty.BoolProp("Emitting", { isEmitting }, { isEmitting = it }),
        InspectorProperty.FloatProp("Rate / sec", 0f, 300f, 5f, { emissionRatePerSec }, { emissionRatePerSec = it }),
        InspectorProperty.FloatProp("Max Speed", 0f, 40f, 0.5f, { speedMax }, { speedMax = it }),
        InspectorProperty.FloatProp("Gravity Y", -30f, 30f, 0.5f, { gravityY }, { gravityY = it }),
        InspectorProperty.FloatProp("Start Size", 0.02f, 5f, 0.05f, { startSizeMax }, { startSizeMax = it; startSizeMin = it * 0.6f }),
        InspectorProperty.ColorProp("Start Color", { startColor }, { startColor = it }),
        InspectorProperty.ColorProp("End Color", { endColor }, { endColor = it }),
        InspectorProperty.EnumProp(
            label = "Blend Mode",
            options = BlendMode.entries.map { it.name },
            getter = { blendMode.name },
            setter = { blendMode = BlendMode.valueOf(it) }
        )
    )
}
