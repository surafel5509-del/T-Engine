package com.tengine.core.physics

import kotlin.math.max
import kotlin.math.sqrt

/**
 * Surface physical properties controlling friction, bounciness (restitution), and mass density.
 */
data class PhysicsMaterial2D(
    var name: String = "Default",
    var friction: Float = 0.4f,
    var restitution: Float = 0.15f,
    var density: Float = 1.0f
) {
    companion object {
        val DEFAULT get() = PhysicsMaterial2D("Default", friction = 0.4f, restitution = 0.15f, density = 1.0f)
        val BOUNCY get() = PhysicsMaterial2D("Bouncy", friction = 0.25f, restitution = 0.85f, density = 0.9f)
        val ICE get() = PhysicsMaterial2D("Ice", friction = 0.02f, restitution = 0.05f, density = 0.92f)
        val RUBBER get() = PhysicsMaterial2D("Rubber", friction = 0.85f, restitution = 0.75f, density = 1.1f)
        val STONE get() = PhysicsMaterial2D("Stone", friction = 0.6f, restitution = 0.05f, density = 2.4f)
        val METAL get() = PhysicsMaterial2D("Metal", friction = 0.3f, restitution = 0.2f, density = 7.8f)

        fun combineFriction(a: PhysicsMaterial2D, b: PhysicsMaterial2D): Float =
            sqrt(max(0f, a.friction) * max(0f, b.friction))

        fun combineRestitution(a: PhysicsMaterial2D, b: PhysicsMaterial2D): Float =
            max(a.restitution, b.restitution).coerceIn(0f, 1f)
    }
}
