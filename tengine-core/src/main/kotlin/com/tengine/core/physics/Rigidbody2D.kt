package com.tengine.core.physics

import com.tengine.core.ecs.Component
import com.tengine.core.ecs.InspectorProperty
import com.tengine.core.math.Mathf
import com.tengine.core.math.Vector2
import com.tengine.core.serialization.JsonObject

enum class BodyType {
    DYNAMIC,
    KINEMATIC,
    STATIC
}

/**
 * 2D Rigid Body dynamics component providing velocity, angular momentum, forces, impulses,
 * gravity scaling, damping, and automatic mass/inertia calculation from attached Collider2Ds.
 */
class Rigidbody2D(
    bodyType: BodyType = BodyType.DYNAMIC
) : Component("Rigidbody2D") {

    override val typeName: String get() = "Rigidbody2D"

    var bodyType: BodyType = bodyType
        set(value) {
            field = value
            recalculateMassData()
            if (value == BodyType.STATIC) {
                linearVelocity.setZero()
                angularVelocityRad = 0f
            }
        }

    var useAutoMass: Boolean = false

    var mass: Float = 1f
        set(value) {
            field = value.coerceAtLeast(0.001f)
            recalculateMassData()
        }

    var inertia: Float = 0.5f
        private set

    var invMass: Float = 1f
        private set

    var invInertia: Float = 2f
        private set

    val linearVelocity: Vector2 = Vector2(0f, 0f)
    var angularVelocityRad: Float = 0f

    var gravityScale: Float = 1f
    var linearDamping: Float = 0.05f
    var angularDamping: Float = 0.08f

    var freezeRotation: Boolean = false
        set(value) {
            field = value
            if (value) angularVelocityRad = 0f
            recalculateMassData()
        }

    var isSleeping: Boolean = false
    internal var sleepTimer: Float = 0f

    internal val forceAccum: Vector2 = Vector2(0f, 0f)
    internal var torqueAccum: Float = 0f

    init {
        recalculateMassData()
    }

    override fun onAwake() {
        recalculateMassData()
    }

    fun recalculateMassData() {
        if (bodyType != BodyType.DYNAMIC) {
            invMass = 0f
            invInertia = 0f
            return
        }

        var computedMass = mass.coerceAtLeast(0.001f)
        var computedInertia = computedMass * 0.5f

        if (isAttached) {
            val colliders = gameObject.getComponents(Collider2D::class)
            if (colliders.isNotEmpty()) {
                var totalAreaMass = 0f
                var totalMoment = 0f
                for (c in colliders) {
                    if (!c.isTrigger) {
                        val props = c.computeMassProperties()
                        totalAreaMass += props.first
                        totalMoment += props.second
                    }
                }
                if (useAutoMass && totalAreaMass > 0.001f) {
                    computedMass = totalAreaMass
                    computedInertia = totalMoment.coerceAtLeast(0.001f)
                } else if (totalAreaMass > 0.001f) {
                    val scaleRatio = computedMass / totalAreaMass
                    computedInertia = (totalMoment * scaleRatio).coerceAtLeast(0.001f)
                }
            }
        }

        invMass = 1f / computedMass
        inertia = computedInertia
        invInertia = if (freezeRotation) 0f else 1f / computedInertia
    }

    fun addForce(force: Vector2) {
        if (bodyType != BodyType.DYNAMIC) return
        wakeUp()
        forceAccum.addSelf(force)
    }

    fun addForce(fx: Float, fy: Float) {
        if (bodyType != BodyType.DYNAMIC) return
        wakeUp()
        forceAccum.addSelf(fx, fy)
    }

    fun addForceAtPosition(force: Vector2, worldPoint: Vector2) {
        if (bodyType != BodyType.DYNAMIC) return
        wakeUp()
        forceAccum.addSelf(force)
        if (!freezeRotation) {
            val r = worldPoint - transform.worldPosition
            torqueAccum += r cross force
        }
    }

    fun addImpulse(impulse: Vector2) {
        if (bodyType != BodyType.DYNAMIC) return
        wakeUp()
        linearVelocity.addScaledSelf(impulse, invMass)
    }

    fun addImpulseAtPoint(impulse: Vector2, contactOffsetFromCenter: Vector2) {
        if (bodyType != BodyType.DYNAMIC) return
        wakeUp()
        linearVelocity.addScaledSelf(impulse, invMass)
        if (!freezeRotation) {
            angularVelocityRad += invInertia * (contactOffsetFromCenter cross impulse)
        }
    }

    fun addTorque(torque: Float) {
        if (bodyType != BodyType.DYNAMIC || freezeRotation) return
        wakeUp()
        torqueAccum += torque
    }

    fun wakeUp() {
        isSleeping = false
        sleepTimer = 0f
    }

    internal fun clearForces() {
        forceAccum.setZero()
        torqueAccum = 0f
    }

    override fun serialize(): JsonObject = super.serialize()
        .put("bodyType", bodyType.name)
        .put("useAutoMass", useAutoMass)
        .put("mass", mass)
        .putVector2("linearVelocity", linearVelocity)
        .put("angularVelocityDeg", angularVelocityRad * Mathf.RAD_TO_DEG)
        .put("gravityScale", gravityScale)
        .put("linearDamping", linearDamping)
        .put("angularDamping", angularDamping)
        .put("freezeRotation", freezeRotation)

    override fun deserialize(data: JsonObject) {
        super.deserialize(data)
        bodyType = runCatching { BodyType.valueOf(data.getString("bodyType", "DYNAMIC")) }
            .getOrDefault(BodyType.DYNAMIC)
        useAutoMass = data.getBoolean("useAutoMass", false)
        mass = data.getFloat("mass", 1f)
        linearVelocity.set(data.getVector2("linearVelocity", Vector2.ZERO))
        angularVelocityRad = data.getFloat("angularVelocityDeg", 0f) * Mathf.DEG_TO_RAD
        gravityScale = data.getFloat("gravityScale", 1f)
        linearDamping = data.getFloat("linearDamping", 0.05f)
        angularDamping = data.getFloat("angularDamping", 0.08f)
        freezeRotation = data.getBoolean("freezeRotation", false)
        recalculateMassData()
    }

    override fun inspectableProperties(): List<InspectorProperty> = listOf(
        InspectorProperty.EnumProp(
            label = "Body Type",
            options = BodyType.entries.map { it.name },
            getter = { bodyType.name },
            setter = { bodyType = BodyType.valueOf(it) }
        ),
        InspectorProperty.FloatProp(
            label = "Mass",
            min = 0.05f,
            max = 500f,
            step = 0.25f,
            getter = { mass },
            setter = { mass = it }
        ),
        InspectorProperty.FloatProp(
            label = "Gravity Scale",
            min = -5f,
            max = 10f,
            step = 0.25f,
            getter = { gravityScale },
            setter = { gravityScale = it }
        ),
        InspectorProperty.FloatProp(
            label = "Linear Damping",
            min = 0f,
            max = 10f,
            step = 0.05f,
            getter = { linearDamping },
            setter = { linearDamping = it.coerceAtLeast(0f) }
        ),
        InspectorProperty.BoolProp(
            label = "Freeze Rotation",
            getter = { freezeRotation },
            setter = { freezeRotation = it }
        ),
        InspectorProperty.Vector2Prop(
            label = "Velocity",
            step = 0.5f,
            getter = { linearVelocity.copyOf() },
            setter = { linearVelocity.set(it) }
        )
    )
}
