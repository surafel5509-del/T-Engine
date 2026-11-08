package com.tengine.core.physics

import com.tengine.core.math.AABB
import com.tengine.core.math.Mathf
import com.tengine.core.math.Ray2D
import com.tengine.core.math.Vector2

/**
 * Complete 2D Physics Simulation World managing Rigidbodies, Colliders, Joints,
 * 32x32 Layer Collision Matrix, Broadphase/Narrowphase/Sequential Impulse Solver,
 * Enter/Stay/Exit Collision & Trigger dispatching, and Raycast/Overlap queries.
 */
class PhysicsWorld2D(
    gravity: Vector2 = Vector2(0f, -18f),
    cellSize: Float = 2.5f,
    var velocityIterations: Int = 8,
    var positionIterations: Int = 3
) {
    val gravity: Vector2 = gravity.copyOf()

    private val bodies = ArrayList<Rigidbody2D>(128)
    private val colliders = ArrayList<Collider2D>(256)
    private val joints = ArrayList<Joint2D>(32)

    private val spatialGrid = SpatialHashGrid(cellSize)
    private val solver = ContactSolver2D()

    private val candidatePairs = ArrayList<Pair<Collider2D, Collider2D>>(256)
    private val solidManifolds = ArrayList<ContactManifold2D>(128)

    // 32-bit masks per layer (default: every layer collides with every layer)
    private val layerMasks = IntArray(32) { -1 }

    private data class PairKey(val idA: Long, val idB: Long) {
        companion object {
            fun of(a: Collider2D, b: Collider2D): PairKey =
                if (a.instanceId <= b.instanceId) PairKey(a.instanceId, b.instanceId)
                else PairKey(b.instanceId, a.instanceId)
        }
    }

    private var previousSolidPairs = HashMap<PairKey, ContactManifold2D>()
    private var previousTriggerPairs = HashMap<PairKey, Pair<Collider2D, Collider2D>>()

    val bodyCount: Int get() = bodies.size
    val colliderCount: Int get() = colliders.size
    var activeContactCount: Int = 0
        private set

    fun setLayerCollision(layerA: Int, layerB: Int, collide: Boolean) {
        if (layerA !in 0..31 || layerB !in 0..31) return
        if (collide) {
            layerMasks[layerA] = layerMasks[layerA] or (1 shl layerB)
            layerMasks[layerB] = layerMasks[layerB] or (1 shl layerA)
        } else {
            layerMasks[layerA] = layerMasks[layerA] and (1 shl layerB).inv()
            layerMasks[layerB] = layerMasks[layerB] and (1 shl layerA).inv()
        }
    }

    fun canLayersCollide(layerA: Int, layerB: Int): Boolean {
        if (layerA !in 0..31 || layerB !in 0..31) return false
        return (layerMasks[layerA] and (1 shl layerB)) != 0
    }

    internal fun registerBody(body: Rigidbody2D) {
        if (!bodies.contains(body)) bodies.add(body)
    }

    internal fun unregisterBody(body: Rigidbody2D) {
        bodies.remove(body)
    }

    internal fun registerCollider(collider: Collider2D) {
        if (!colliders.contains(collider)) {
            colliders.add(collider)
            collider.attachedRigidbody?.recalculateMassData()
        }
    }

    internal fun unregisterCollider(collider: Collider2D) {
        colliders.remove(collider)
        collider.attachedRigidbody?.recalculateMassData()
    }

    internal fun registerJoint(joint: Joint2D) {
        if (!joints.contains(joint)) joints.add(joint)
    }

    internal fun unregisterJoint(joint: Joint2D) {
        joints.remove(joint)
    }

    fun step(dt: Float) {
        if (dt <= 0f) return

        // 1. Apply spring/joint forces
        for (i in 0 until joints.size) {
            val j = joints[i]
            if (j.isActiveAndEnabled) j.applyForces(dt)
        }

        // 2. Integrate forces & gravity into tentative velocities
        for (i in 0 until bodies.size) {
            val b = bodies[i]
            if (!b.isActiveAndEnabled || b.bodyType != BodyType.DYNAMIC || b.isSleeping) continue

            val invM = b.invMass
            b.linearVelocity.x += (gravity.x * b.gravityScale + b.forceAccum.x * invM) * dt
            b.linearVelocity.y += (gravity.y * b.gravityScale + b.forceAccum.y * invM) * dt

            if (!b.freezeRotation) {
                b.angularVelocityRad += b.torqueAccum * b.invInertia * dt
            }

            // Exponential-like frame-rate-independent damping: 1 / (1 + d * dt)
            val linDamp = 1f / (1f + b.linearDamping * dt)
            val angDamp = 1f / (1f + b.angularDamping * dt)
            b.linearVelocity.scaleSelf(linDamp)
            b.angularVelocityRad *= angDamp

            b.clearForces()
        }

        // 3. Populate Spatial Hash Broadphase
        spatialGrid.clear()
        for (i in 0 until colliders.size) {
            val col = colliders[i]
            if (col.isActiveAndEnabled) {
                col.invalidateCache()
                spatialGrid.insert(col)
            }
        }

        // 4. Broadphase -> Narrowphase
        spatialGrid.collectCandidatePairs(candidatePairs)
        solidManifolds.clear()
        val currentSolidPairs = HashMap<PairKey, ContactManifold2D>(candidatePairs.size)
        val currentTriggerPairs = HashMap<PairKey, Pair<Collider2D, Collider2D>>()

        for (i in 0 until candidatePairs.size) {
            val (colA, colB) = candidatePairs[i]
            if (!canLayersCollide(colA.gameObject.layer, colB.gameObject.layer)) continue

            val rbA = colA.attachedRigidbody
            val rbB = colB.attachedRigidbody
            // Skip if both are static / bodiless
            val aDynamicOrKin = rbA != null && rbA.bodyType != BodyType.STATIC
            val bDynamicOrKin = rbB != null && rbB.bodyType != BodyType.STATIC
            if (!aDynamicOrKin && !bDynamicOrKin) continue
            if (rbA != null && rbB != null && rbA === rbB) continue

            val manifold = Narrowphase2D.collide(colA, colB) ?: continue
            val key = PairKey.of(colA, colB)

            if (colA.isTrigger || colB.isTrigger) {
                currentTriggerPairs[key] = colA to colB
            } else {
                solidManifolds.add(manifold)
                currentSolidPairs[key] = manifold
            }
        }

        activeContactCount = solidManifolds.size

        // 5. Solve Velocity Constraints
        solver.prepare(solidManifolds)
        solver.solveVelocityConstraints(velocityIterations)

        // 6. Integrate Velocities -> World Transforms
        for (i in 0 until bodies.size) {
            val b = bodies[i]
            if (!b.isActiveAndEnabled || b.bodyType == BodyType.STATIC || b.isSleeping) continue

            val wp = b.transform.worldPosition
            b.transform.worldPosition = Vector2(
                wp.x + b.linearVelocity.x * dt,
                wp.y + b.linearVelocity.y * dt
            )
            if (!b.freezeRotation && kotlin.math.abs(b.angularVelocityRad) > 1e-6f) {
                b.transform.worldRotationDeg += b.angularVelocityRad * Mathf.RAD_TO_DEG * dt
            }
        }

        // 7. Solve Distance Joints & Positional Contact Correction
        for (i in 0 until joints.size) {
            val j = joints[i]
            if (j.isActiveAndEnabled) j.solveConstraint(dt)
        }
        solver.solvePositionConstraints(positionIterations)

        // Refresh spatial grid caches after position updates
        spatialGrid.clear()
        for (i in 0 until colliders.size) {
            val col = colliders[i]
            if (col.isActiveAndEnabled) {
                col.invalidateCache()
                spatialGrid.insert(col)
            }
        }

        // 8. Dispatch Collision & Trigger Lifecycle Callbacks (Enter / Stay / Exit)
        dispatchCallbacks(currentSolidPairs, currentTriggerPairs)
    }

    private fun dispatchCallbacks(
        currentSolid: HashMap<PairKey, ContactManifold2D>,
        currentTrigger: HashMap<PairKey, Pair<Collider2D, Collider2D>>
    ) {
        // Solid Collisions: Enter & Stay
        for ((key, m) in currentSolid) {
            val cp = m.contacts.firstOrNull()?.position ?: m.colliderA.worldAABB.center
            val vA = m.colliderA.attachedRigidbody?.linearVelocity ?: Vector2.ZERO
            val vB = m.colliderB.attachedRigidbody?.linearVelocity ?: Vector2.ZERO
            val relVel = vB - vA

            val eventForA = CollisionEvent2D(
                otherCollider = m.colliderB,
                selfCollider = m.colliderA,
                normal = m.normal,
                penetration = m.penetration,
                contactPoint = cp,
                relativeVelocity = relVel
            )
            val eventForB = CollisionEvent2D(
                otherCollider = m.colliderA,
                selfCollider = m.colliderB,
                normal = -m.normal,
                penetration = m.penetration,
                contactPoint = cp,
                relativeVelocity = -relVel
            )

            val wasColliding = previousSolidPairs.containsKey(key)
            val compsA = m.colliderA.gameObject.components.toList()
            val compsB = m.colliderB.gameObject.components.toList()

            if (!wasColliding) {
                for (c in compsA) if (c.isActiveAndEnabled) c.onCollisionEnter2D(eventForA)
                for (c in compsB) if (c.isActiveAndEnabled) c.onCollisionEnter2D(eventForB)
            } else {
                for (c in compsA) if (c.isActiveAndEnabled) c.onCollisionStay2D(eventForA)
                for (c in compsB) if (c.isActiveAndEnabled) c.onCollisionStay2D(eventForB)
            }
        }

        // Solid Collisions: Exit
        for ((key, oldM) in previousSolidPairs) {
            if (!currentSolid.containsKey(key)) {
                if (!oldM.colliderA.isDestroyed && !oldM.colliderB.isDestroyed) {
                    val cp = oldM.contacts.firstOrNull()?.position ?: Vector2.ZERO
                    val eventA = CollisionEvent2D(oldM.colliderB, oldM.colliderA, oldM.normal, 0f, cp, Vector2.ZERO)
                    val eventB = CollisionEvent2D(oldM.colliderA, oldM.colliderB, -oldM.normal, 0f, cp, Vector2.ZERO)
                    for (c in oldM.colliderA.gameObject.components.toList()) {
                        if (c.isActiveAndEnabled) c.onCollisionExit2D(eventA)
                    }
                    for (c in oldM.colliderB.gameObject.components.toList()) {
                        if (c.isActiveAndEnabled) c.onCollisionExit2D(eventB)
                    }
                }
            }
        }

        // Triggers: Enter & Stay
        for ((key, pair) in currentTrigger) {
            val (colA, colB) = pair
            val wasTriggered = previousTriggerPairs.containsKey(key)
            val compsA = colA.gameObject.components.toList()
            val compsB = colB.gameObject.components.toList()
            if (!wasTriggered) {
                for (c in compsA) if (c.isActiveAndEnabled) c.onTriggerEnter2D(colB)
                for (c in compsB) if (c.isActiveAndEnabled) c.onTriggerEnter2D(colA)
            } else {
                for (c in compsA) if (c.isActiveAndEnabled) c.onTriggerStay2D(colB)
                for (c in compsB) if (c.isActiveAndEnabled) c.onTriggerStay2D(colA)
            }
        }

        // Triggers: Exit
        for ((key, oldPair) in previousTriggerPairs) {
            if (!currentTrigger.containsKey(key)) {
                val (colA, colB) = oldPair
                if (!colA.isDestroyed && !colB.isDestroyed) {
                    for (c in colA.gameObject.components.toList()) {
                        if (c.isActiveAndEnabled) c.onTriggerExit2D(colB)
                    }
                    for (c in colB.gameObject.components.toList()) {
                        if (c.isActiveAndEnabled) c.onTriggerExit2D(colA)
                    }
                }
            }
        }

        previousSolidPairs = currentSolid
        previousTriggerPairs = currentTrigger
    }

    // =========================================================================
    // Spatial Queries (Unity Physics2D style)
    // =========================================================================

    fun raycast(
        origin: Vector2,
        direction: Vector2,
        maxDistance: Float = 1000f,
        layerMask: Int = -1,
        includeTriggers: Boolean = false
    ): RaycastHit2D? {
        if (direction.lengthSquared <= Mathf.EPSILON) return null
        val ray = Ray2D(origin, direction)
        var closestHit: RaycastHit2D? = null

        for (i in 0 until colliders.size) {
            val col = colliders[i]
            if (!col.isActiveAndEnabled) continue
            if (!includeTriggers && col.isTrigger) continue
            if ((layerMask and (1 shl col.gameObject.layer)) == 0) continue

            val hit = col.raycast(ray, closestHit?.distance ?: maxDistance)
            if (hit != null && (closestHit == null || hit.distance < closestHit.distance)) {
                closestHit = hit
            }
        }
        return closestHit
    }

    fun raycastAll(
        origin: Vector2,
        direction: Vector2,
        maxDistance: Float = 1000f,
        layerMask: Int = -1,
        includeTriggers: Boolean = true
    ): List<RaycastHit2D> {
        if (direction.lengthSquared <= Mathf.EPSILON) return emptyList()
        val ray = Ray2D(origin, direction)
        val hits = ArrayList<RaycastHit2D>()
        for (i in 0 until colliders.size) {
            val col = colliders[i]
            if (!col.isActiveAndEnabled) continue
            if (!includeTriggers && col.isTrigger) continue
            if ((layerMask and (1 shl col.gameObject.layer)) == 0) continue
            col.raycast(ray, maxDistance)?.let { hits.add(it) }
        }
        hits.sortBy { it.distance }
        return hits
    }

    fun overlapAABB(aabb: AABB, layerMask: Int = -1, includeTriggers: Boolean = true): List<Collider2D> {
        val results = ArrayList<Collider2D>()
        for (i in 0 until colliders.size) {
            val col = colliders[i]
            if (!col.isActiveAndEnabled) continue
            if (!includeTriggers && col.isTrigger) continue
            if ((layerMask and (1 shl col.gameObject.layer)) == 0) continue
            if (col.worldAABB.overlaps(aabb)) {
                results.add(col)
            }
        }
        return results
    }

    fun overlapCircle(
        center: Vector2,
        radius: Float,
        layerMask: Int = -1,
        includeTriggers: Boolean = true
    ): List<Collider2D> {
        val probe = CircleCollider2D(radius).apply {
            offset = center
        }
        val queryBox = AABB.fromCenterExtents(center.x, center.y, radius, radius)
        val candidates = overlapAABB(queryBox, layerMask, includeTriggers)
        return candidates.filter { Narrowphase2D.collide(probe, it) != null }
    }

    fun overlapPoint(point: Vector2, layerMask: Int = -1, includeTriggers: Boolean = true): List<Collider2D> {
        val results = ArrayList<Collider2D>()
        for (i in 0 until colliders.size) {
            val col = colliders[i]
            if (!col.isActiveAndEnabled) continue
            if (!includeTriggers && col.isTrigger) continue
            if ((layerMask and (1 shl col.gameObject.layer)) == 0) continue
            if (col.containsPoint(point)) results.add(col)
        }
        return results
    }

    fun clear() {
        bodies.clear()
        colliders.clear()
        joints.clear()
        spatialGrid.clear()
        previousSolidPairs.clear()
        previousTriggerPairs.clear()
        activeContactCount = 0
    }
}
