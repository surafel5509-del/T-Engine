package com.tengine.core.math

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * High-performance 2D vector supporting both idiomatic immutable operators
 * and allocation-free in-place mutation methods for real-time Android physics & rendering.
 */
data class Vector2(
    var x: Float = 0f,
    var y: Float = 0f
) {
    val lengthSquared: Float
        get() = x * x + y * y

    val length: Float
        get() = sqrt(x * x + y * y)

    val normalized: Vector2
        get() {
            val len = length
            return if (len > Mathf.EPSILON) Vector2(x / len, y / len) else Vector2(0f, 0f)
        }

    /** Perpendicular vector rotated +90 degrees CCW: (-y, x). */
    val perpLeft: Vector2
        get() = Vector2(-y, x)

    /** Perpendicular vector rotated -90 degrees CW: (y, -x). */
    val perpRight: Vector2
        get() = Vector2(y, -x)

    val angleRad: Float
        get() = atan2(y, x)

    val angleDeg: Float
        get() = atan2(y, x) * Mathf.RAD_TO_DEG

    fun copyOf(): Vector2 = Vector2(x, y)

    fun set(nx: Float, ny: Float): Vector2 {
        x = nx
        y = ny
        return this
    }

    fun set(other: Vector2): Vector2 {
        x = other.x
        y = other.y
        return this
    }

    fun setZero(): Vector2 {
        x = 0f
        y = 0f
        return this
    }

    fun addSelf(dx: Float, dy: Float): Vector2 {
        x += dx
        y += dy
        return this
    }

    fun addSelf(other: Vector2): Vector2 {
        x += other.x
        y += other.y
        return this
    }

    fun addScaledSelf(other: Vector2, scale: Float): Vector2 {
        x += other.x * scale
        y += other.y * scale
        return this
    }

    fun subSelf(other: Vector2): Vector2 {
        x -= other.x
        y -= other.y
        return this
    }

    fun scaleSelf(scalar: Float): Vector2 {
        x *= scalar
        y *= scalar
        return this
    }

    fun normalizeSelf(): Vector2 {
        val len = length
        if (len > Mathf.EPSILON) {
            x /= len
            y /= len
        } else {
            x = 0f
            y = 0f
        }
        return this
    }

    fun clampMagnitude(maxLength: Float): Vector2 {
        val sq = lengthSquared
        if (sq > maxLength * maxLength && sq > Mathf.EPSILON) {
            val len = sqrt(sq)
            return Vector2(x / len * maxLength, y / len * maxLength)
        }
        return Vector2(x, y)
    }

    fun rotatedRad(radians: Float): Vector2 {
        val c = cos(radians)
        val s = sin(radians)
        return Vector2(x * c - y * s, x * s + y * c)
    }

    fun rotatedDeg(degrees: Float): Vector2 = rotatedRad(degrees * Mathf.DEG_TO_RAD)

    fun rotateSelfRad(radians: Float): Vector2 {
        val c = cos(radians)
        val s = sin(radians)
        val nx = x * c - y * s
        val ny = x * s + y * c
        x = nx
        y = ny
        return this
    }

    infix fun dot(other: Vector2): Float = x * other.x + y * other.y

    /** 2D scalar cross product (z-component of 3D cross product): x * other.y - y * other.x */
    infix fun cross(other: Vector2): Float = x * other.y - y * other.x

    fun distanceTo(other: Vector2): Float {
        val dx = x - other.x
        val dy = y - other.y
        return sqrt(dx * dx + dy * dy)
    }

    fun distanceSquaredTo(other: Vector2): Float {
        val dx = x - other.x
        val dy = y - other.y
        return dx * dx + dy * dy
    }

    fun reflect(normal: Vector2): Vector2 {
        val d = 2f * this.dot(normal)
        return Vector2(x - d * normal.x, y - d * normal.y)
    }

    fun projectOnto(axis: Vector2): Vector2 {
        val denom = axis.lengthSquared
        if (denom <= Mathf.EPSILON) return Vector2(0f, 0f)
        val scale = this.dot(axis) / denom
        return Vector2(axis.x * scale, axis.y * scale)
    }

    operator fun plus(other: Vector2): Vector2 = Vector2(x + other.x, y + other.y)
    operator fun minus(other: Vector2): Vector2 = Vector2(x - other.x, y - other.y)
    operator fun times(scalar: Float): Vector2 = Vector2(x * scalar, y * scalar)
    operator fun times(other: Vector2): Vector2 = Vector2(x * other.x, y * other.y)
    operator fun div(scalar: Float): Vector2 = Vector2(x / scalar, y / scalar)
    operator fun unaryMinus(): Vector2 = Vector2(-x, -y)

    fun approximatelyEquals(other: Vector2, tolerance: Float = Mathf.EPSILON): Boolean =
        Mathf.approximately(x, other.x, tolerance) && Mathf.approximately(y, other.y, tolerance)

    override fun toString(): String = "Vector2(${formatFloat(x)}, ${formatFloat(y)})"

    companion object {
        val ZERO: Vector2 get() = Vector2(0f, 0f)
        val ONE: Vector2 get() = Vector2(1f, 1f)
        val UP: Vector2 get() = Vector2(0f, 1f)
        val DOWN: Vector2 get() = Vector2(0f, -1f)
        val LEFT: Vector2 get() = Vector2(-1f, 0f)
        val RIGHT: Vector2 get() = Vector2(1f, 0f)

        fun fromAngleRad(radians: Float, length: Float = 1f): Vector2 =
            Vector2(cos(radians) * length, sin(radians) * length)

        fun fromAngleDeg(degrees: Float, length: Float = 1f): Vector2 =
            fromAngleRad(degrees * Mathf.DEG_TO_RAD, length)

        fun lerp(a: Vector2, b: Vector2, t: Float): Vector2 {
            val ct = Mathf.clamp01(t)
            return Vector2(a.x + (b.x - a.x) * ct, a.y + (b.y - a.y) * ct)
        }

        fun lerpUnclamped(a: Vector2, b: Vector2, t: Float): Vector2 =
            Vector2(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)

        fun moveTowards(current: Vector2, target: Vector2, maxDistanceDelta: Float): Vector2 {
            val dx = target.x - current.x
            val dy = target.y - current.y
            val sqDist = dx * dx + dy * dy
            if (sqDist == 0f || (maxDistanceDelta >= 0f && sqDist <= maxDistanceDelta * maxDistanceDelta)) {
                return target.copyOf()
            }
            val dist = sqrt(sqDist)
            return Vector2(
                current.x + dx / dist * maxDistanceDelta,
                current.y + dy / dist * maxDistanceDelta
            )
        }

        /** 2D cross product of scalar s and vector v: s x v = (-s * v.y, s * v.x) */
        fun cross(s: Float, v: Vector2): Vector2 = Vector2(-s * v.y, s * v.x)

        /** 2D cross product of vector v and scalar s: v x s = (s * v.y, -s * v.x) */
        fun cross(v: Vector2, s: Float): Vector2 = Vector2(s * v.y, -s * v.x)

        fun signedAngleDeg(from: Vector2, to: Vector2): Float {
            val rad = atan2(from cross to, from dot to)
            return rad * Mathf.RAD_TO_DEG
        }

        fun quadraticBezier(p0: Vector2, p1: Vector2, p2: Vector2, t: Float): Vector2 {
            val u = 1f - t
            return Vector2(
                u * u * p0.x + 2f * u * t * p1.x + t * t * p2.x,
                u * u * p0.y + 2f * u * t * p1.y + t * t * p2.y
            )
        }

        fun cubicBezier(p0: Vector2, p1: Vector2, p2: Vector2, p3: Vector2, t: Float): Vector2 {
            val u = 1f - t
            val uu = u * u
            val uuu = uu * u
            val tt = t * t
            val ttt = tt * t
            return Vector2(
                uuu * p0.x + 3f * uu * t * p1.x + 3f * u * tt * p2.x + ttt * p3.x,
                uuu * p0.y + 3f * uu * t * p1.y + 3f * u * tt * p2.y + ttt * p3.y
            )
        }

        private fun formatFloat(v: Float): String = ((v * 1000f).toInt() / 1000f).toString()
    }
}

operator fun Float.times(v: Vector2): Vector2 = Vector2(this * v.x, this * v.y)
