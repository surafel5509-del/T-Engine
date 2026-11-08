package com.tengine.core.physics

import com.tengine.core.math.Mathf
import com.tengine.core.math.Vector2
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Iterative Sequential Impulse 2D Rigid-Body Contact Solver (normal impulses, Coulomb friction,
 * velocity restitution threshold, and Baumgarte positional correction).
 */
class ContactSolver2D {

    private class PreparedContact(
        val rA: Vector2,
        val rB: Vector2,
        val normalMass: Float,
        val tangentMass: Float,
        val velocityBias: Float,
        var accumulatedNormalImpulse: Float = 0f,
        var accumulatedTangentImpulse: Float = 0f
    )

    private class PreparedManifold(
        val manifold: ContactManifold2D,
        val bodyA: Rigidbody2D?,
        val bodyB: Rigidbody2D?,
        val invMassA: Float,
        val invInertiaA: Float,
        val invMassB: Float,
        val invInertiaB: Float,
        val normal: Vector2,
        val tangent: Vector2,
        val points: List<PreparedContact>
    )

    private val prepared = ArrayList<PreparedManifold>(128)

    fun prepare(manifolds: List<ContactManifold2D>) {
        prepared.clear()
        for (m in manifolds) {
            val bodyA = m.colliderA.attachedRigidbody
            val bodyB = m.colliderB.attachedRigidbody

            val invMassA = bodyA?.invMass ?: 0f
            val invInertiaA = bodyA?.invInertia ?: 0f
            val invMassB = bodyB?.invMass ?: 0f
            val invInertiaB = bodyB?.invInertia ?: 0f

            if (invMassA + invMassB <= 0f) continue

            val posA = bodyA?.transform?.worldPosition ?: m.colliderA.transform.worldPosition
            val posB = bodyB?.transform?.worldPosition ?: m.colliderB.transform.worldPosition
            val velA = bodyA?.linearVelocity ?: Vector2.ZERO
            val wA = bodyA?.angularVelocityRad ?: 0f
            val velB = bodyB?.linearVelocity ?: Vector2.ZERO
            val wB = bodyB?.angularVelocityRad ?: 0f

            val normal = m.normal
            val tangent = Vector2(-normal.y, normal.x)

            val preparedPoints = ArrayList<PreparedContact>(m.contacts.size)
            for (cp in m.contacts) {
                val rA = cp.position - posA
                val rB = cp.position - posB

                val rnA = rA cross normal
                val rnB = rB cross normal
                val kNormal = invMassA + invMassB + invInertiaA * rnA * rnA + invInertiaB * rnB * rnB
                val normalMass = if (kNormal > Mathf.EPSILON) 1f / kNormal else 0f

                val rtA = rA cross tangent
                val rtB = rB cross tangent
                val kTangent = invMassA + invMassB + invInertiaA * rtA * rtA + invInertiaB * rtB * rtB
                val tangentMass = if (kTangent > Mathf.EPSILON) 1f / kTangent else 0f

                // Relative velocity at contact point: vB + wB x rB - (vA + wA x rA)
                val rvX = (velB.x - wB * rB.y) - (velA.x - wA * rA.y)
                val rvY = (velB.y + wB * rB.x) - (velA.y + wA * rA.x)
                val relVelNormal = rvX * normal.x + rvY * normal.y

                // Restitution bounce only above threshold (1.0 m/s) to allow stable resting contacts
                val bias = if (relVelNormal < -1.0f) -m.restitution * relVelNormal else 0f

                preparedPoints.add(
                    PreparedContact(
                        rA = rA,
                        rB = rB,
                        normalMass = normalMass,
                        tangentMass = tangentMass,
                        velocityBias = bias
                    )
                )
            }

            prepared.add(
                PreparedManifold(
                    manifold = m,
                    bodyA = bodyA,
                    bodyB = bodyB,
                    invMassA = invMassA,
                    invInertiaA = invInertiaA,
                    invMassB = invMassB,
                    invInertiaB = invInertiaB,
                    normal = normal,
                    tangent = tangent,
                    points = preparedPoints
                )
            )
        }
    }

    fun solveVelocityConstraints(iterations: Int) {
        repeat(max(1, iterations)) {
            for (pm in prepared) {
                val bA = pm.bodyA
                val bB = pm.bodyB
                val vA = bA?.linearVelocity ?: Vector2.ZERO
                var wA = bA?.angularVelocityRad ?: 0f
                val vB = bB?.linearVelocity ?: Vector2.ZERO
                var wB = bB?.angularVelocityRad ?: 0f

                val n = pm.normal
                val t = pm.tangent
                val friction = pm.manifold.friction

                // Solve tangent (friction) impulses first
                for (pc in pm.points) {
                    val dvX = (vB.x - wB * pc.rB.y) - (vA.x - wA * pc.rA.y)
                    val dvY = (vB.y + wB * pc.rB.x) - (vA.y + wA * pc.rA.x)
                    val vt = dvX * t.x + dvY * t.y
                    var lambda = pc.tangentMass * (-vt)

                    val maxFriction = friction * pc.accumulatedNormalImpulse
                    val newImpulse = Mathf.clamp(pc.accumulatedTangentImpulse + lambda, -maxFriction, maxFriction)
                    lambda = newImpulse - pc.accumulatedTangentImpulse
                    pc.accumulatedTangentImpulse = newImpulse

                    val px = t.x * lambda
                    val py = t.y * lambda

                    if (bA != null && pm.invMassA > 0f) {
                        vA.x -= pm.invMassA * px
                        vA.y -= pm.invMassA * py
                        wA -= pm.invInertiaA * (pc.rA.x * py - pc.rA.y * px)
                    }
                    if (bB != null && pm.invMassB > 0f) {
                        vB.x += pm.invMassB * px
                        vB.y += pm.invMassB * py
                        wB += pm.invInertiaB * (pc.rB.x * py - pc.rB.y * px)
                    }
                }

                // Solve normal (non-penetration + restitution) impulses
                for (pc in pm.points) {
                    val dvX = (vB.x - wB * pc.rB.y) - (vA.x - wA * pc.rA.y)
                    val dvY = (vB.y + wB * pc.rB.x) - (vA.y + wA * pc.rA.x)
                    val vn = dvX * n.x + dvY * n.y

                    var lambda = pc.normalMass * (-vn + pc.velocityBias)
                    val newImpulse = max(pc.accumulatedNormalImpulse + lambda, 0f)
                    lambda = newImpulse - pc.accumulatedNormalImpulse
                    pc.accumulatedNormalImpulse = newImpulse

                    val px = n.x * lambda
                    val py = n.y * lambda

                    if (bA != null && pm.invMassA > 0f) {
                        vA.x -= pm.invMassA * px
                        vA.y -= pm.invMassA * py
                        wA -= pm.invInertiaA * (pc.rA.x * py - pc.rA.y * px)
                    }
                    if (bB != null && pm.invMassB > 0f) {
                        vB.x += pm.invMassB * px
                        vB.y += pm.invMassB * py
                        wB += pm.invInertiaB * (pc.rB.x * py - pc.rB.y * px)
                    }
                }

                if (bA != null && pm.invMassA > 0f) bA.angularVelocityRad = wA
                if (bB != null && pm.invMassB > 0f) bB.angularVelocityRad = wB
            }
        }
    }

    fun solvePositionConstraints(iterations: Int) {
        val slop = 0.005f
        val baumgarte = 0.35f
        val maxLinearCorrection = 0.25f

        repeat(max(1, iterations)) {
            for (pm in prepared) {
                val invMassSum = pm.invMassA + pm.invMassB
                if (invMassSum <= 0f) continue

                val penetration = pm.manifold.penetration
                val correctionMag = min(
                    max(penetration - slop, 0f) / invMassSum * baumgarte,
                    maxLinearCorrection
                )
                if (abs(correctionMag) <= Mathf.EPSILON) continue

                val cx = pm.normal.x * correctionMag
                val cy = pm.normal.y * correctionMag

                val bA = pm.bodyA
                if (bA != null && pm.invMassA > 0f) {
                    val pA = bA.transform.worldPosition
                    bA.transform.worldPosition = Vector2(pA.x - cx * pm.invMassA, pA.y - cy * pm.invMassA)
                    pm.manifold.colliderA.invalidateCache()
                }

                val bB = pm.bodyB
                if (bB != null && pm.invMassB > 0f) {
                    val pB = bB.transform.worldPosition
                    bB.transform.worldPosition = Vector2(pB.x + cx * pm.invMassB, pB.y + cy * pm.invMassB)
                    pm.manifold.colliderB.invalidateCache()
                }
            }
        }
    }
}
