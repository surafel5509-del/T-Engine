package com.tengine.core.physics

import com.tengine.core.ecs.Component
import com.tengine.core.ecs.InspectorProperty
import com.tengine.core.graphics.ShapeRenderer
import com.tengine.core.math.Color
import com.tengine.core.math.Mathf
import com.tengine.core.math.Vector2
import com.tengine.core.serialization.JsonObject
import kotlin.math.max

/**
 * Base class for 2D physical constraints linking a GameObject's [Rigidbody2D] to another body or world anchor.
 */
abstract class Joint2D(jointName: String) : Component(jointName) {
    var connectedBody: Rigidbody2D? = null
    var connectedObjectUuid: String? = null
    var localAnchorA: Vector2 = Vector2.ZERO
    var anchorB: Vector2 = Vector2.ZERO

    protected fun resolveBodyA(): Rigidbody2D? = getComponent(Rigidbody2D::class)

    protected fun resolveBodyB(): Rigidbody2D? {
        if (connectedBody != null && !connectedBody!!.isDestroyed) return connectedBody
        val targetUuid = connectedObjectUuid ?: return null
        val found = scene?.findByUuid(targetUuid)?.getComponent(Rigidbody2D::class)
        connectedBody = found
        return found
    }

    protected fun worldPointA(): Vector2 = transform.transformPoint(localAnchorA)

    protected fun worldPointB(): Vector2 {
        val b = resolveBodyB()
        return if (b != null) b.transform.transformPoint(anchorB) else anchorB.copyOf()
    }

    abstract fun applyForces(dt: Float)
    abstract fun solveConstraint(dt: Float)

    override fun serialize(): JsonObject = super.serialize()
        .put("connectedUuid", connectedBody?.gameObject?.uuid ?: connectedObjectUuid)
        .putVector2("localAnchorA", localAnchorA)
        .putVector2("anchorB", anchorB)

    override fun deserialize(data: JsonObject) {
        super.deserialize(data)
        connectedObjectUuid = data.getStringOrNull("connectedUuid")
        localAnchorA = data.getVector2("localAnchorA", Vector2.ZERO)
        anchorB = data.getVector2("anchorB", Vector2.ZERO)
    }
}

/**
 * Rigid distance constraint keeping two anchor points at a fixed (or max) target distance.
 */
class DistanceJoint2D(
    var targetDistance: Float = 2.0f,
    var maxDistanceOnly: Boolean = false
) : Joint2D("DistanceJoint2D") {

    override val typeName: String get() = "DistanceJoint2D"

    override fun applyForces(dt: Float) {}

    override fun solveConstraint(dt: Float) {
        val bA = resolveBodyA() ?: return
        val bB = resolveBodyB()

        val invMassA = bA.invMass
        val invMassB = bB?.invMass ?: 0f
        val totalInvMass = invMassA + invMassB
        if (totalInvMass <= 0f) return

        val pA = worldPointA()
        val pB = worldPointB()
        val delta = pB - pA
        val currentDist = delta.length
        if (currentDist <= Mathf.EPSILON) return
        if (maxDistanceOnly && currentDist <= targetDistance) return

        val error = currentDist - targetDistance
        val dir = delta / currentDist

        // Positional correction
        val correction = dir * (error / totalInvMass * 0.6f)
        if (invMassA > 0f) {
            bA.transform.worldPosition = bA.transform.worldPosition + correction * invMassA
        }
        if (bB != null && invMassB > 0f) {
            bB.transform.worldPosition = bB.transform.worldPosition - correction * invMassB
        }

        // Velocity impulse along rod axis
        val vB = bB?.linearVelocity ?: Vector2.ZERO
        val relVelAlongAxis = (vB - bA.linearVelocity) dot dir
        val impulseMag = relVelAlongAxis / totalInvMass
        val impulse = dir * impulseMag
        if (invMassA > 0f) bA.linearVelocity.addScaledSelf(impulse, invMassA)
        if (bB != null && invMassB > 0f) bB.linearVelocity.addScaledSelf(impulse, -invMassB)
    }

    override fun onDrawGizmos(gizmos: ShapeRenderer, selected: Boolean) {
        val pA = worldPointA()
        val pB = worldPointB()
        gizmos.drawLine(pA.x, pA.y, pB.x, pB.y, Color(1.0f, 0.78f, 0.25f, 0.9f), thickness = 0.035f)
    }

    override fun serialize(): JsonObject = super.serialize()
        .put("targetDistance", targetDistance)
        .put("maxDistanceOnly", maxDistanceOnly)

    override fun deserialize(data: JsonObject) {
        super.deserialize(data)
        targetDistance = data.getFloat("targetDistance", 2.0f)
        maxDistanceOnly = data.getBoolean("maxDistanceOnly", false)
    }

    override fun inspectableProperties(): List<InspectorProperty> = listOf(
        InspectorProperty.FloatProp("Distance", 0.05f, 50f, 0.1f, { targetDistance }, { targetDistance = max(0.05f, it) }),
        InspectorProperty.BoolProp("Max Distance Only", { maxDistanceOnly }, { maxDistanceOnly = it }),
        InspectorProperty.Vector2Prop("Anchor B", 0.1f, { anchorB.copyOf() }, { anchorB = it })
    )
}

/**
 * Damped Hookean Spring Joint (F = -stiffness * stretch - damping * relVelocity).
 */
class SpringJoint2D(
    var restLength: Float = 2.0f,
    var stiffness: Float = 45f,
    var damping: Float = 4.5f
) : Joint2D("SpringJoint2D") {

    override val typeName: String get() = "SpringJoint2D"

    override fun applyForces(dt: Float) {
        val bA = resolveBodyA() ?: return
        val bB = resolveBodyB()

        val pA = worldPointA()
        val pB = worldPointB()
        val delta = pB - pA
        val dist = delta.length
        if (dist <= Mathf.EPSILON) return

        val dir = delta / dist
        val stretch = dist - restLength
        val vB = bB?.linearVelocity ?: Vector2.ZERO
        val relVel = (vB - bA.linearVelocity) dot dir

        val forceMag = stiffness * stretch + damping * relVel
        val forceOnA = dir * forceMag
        bA.addForceAtPosition(forceOnA, pA)
        bB?.addForceAtPosition(-forceOnA, pB)
    }

    override fun solveConstraint(dt: Float) {}

    override fun onDrawGizmos(gizmos: ShapeRenderer, selected: Boolean) {
        val pA = worldPointA()
        val pB = worldPointB()
        gizmos.drawLine(pA.x, pA.y, pB.x, pB.y, Color(0.95f, 0.45f, 0.90f, 0.9f), thickness = 0.035f)
    }

    override fun serialize(): JsonObject = super.serialize()
        .put("restLength", restLength)
        .put("stiffness", stiffness)
        .put("damping", damping)

    override fun deserialize(data: JsonObject) {
        super.deserialize(data)
        restLength = data.getFloat("restLength", 2.0f)
        stiffness = data.getFloat("stiffness", 45f)
        damping = data.getFloat("damping", 4.5f)
    }

    override fun inspectableProperties(): List<InspectorProperty> = listOf(
        InspectorProperty.FloatProp("Rest Length", 0.05f, 50f, 0.1f, { restLength }, { restLength = max(0.05f, it) }),
        InspectorProperty.FloatProp("Stiffness", 1f, 500f, 5f, { stiffness }, { stiffness = max(0f, it) }),
        InspectorProperty.FloatProp("Damping", 0f, 50f, 0.5f, { damping }, { damping = max(0f, it) }),
        InspectorProperty.Vector2Prop("Anchor B", 0.1f, { anchorB.copyOf() }, { anchorB = it })
    )
}
