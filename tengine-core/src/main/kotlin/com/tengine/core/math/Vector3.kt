package com.tengine.core.math

import kotlin.math.sqrt

data class Vector3(
    var x: Float = 0f,
    var y: Float = 0f,
    var z: Float = 0f
) {
    val lengthSquared: Float get() = x * x + y * y + z * z
    val length: Float get() = sqrt(lengthSquared)

    val normalized: Vector3
        get() {
            val len = length
            return if (len > Mathf.EPSILON) Vector3(x / len, y / len, z / len) else Vector3(0f, 0f, 0f)
        }

    fun toVector2(): Vector2 = Vector2(x, y)

    fun set(nx: Float, ny: Float, nz: Float): Vector3 {
        x = nx
        y = ny
        z = nz
        return this
    }

    infix fun dot(other: Vector3): Float = x * other.x + y * other.y + z * other.z

    infix fun cross(other: Vector3): Vector3 = Vector3(
        y * other.z - z * other.y,
        z * other.x - x * other.z,
        x * other.y - y * other.x
    )

    operator fun plus(other: Vector3): Vector3 = Vector3(x + other.x, y + other.y, z + other.z)
    operator fun minus(other: Vector3): Vector3 = Vector3(x - other.x, y - other.y, z - other.z)
    operator fun times(scalar: Float): Vector3 = Vector3(x * scalar, y * scalar, z * scalar)
    operator fun div(scalar: Float): Vector3 = Vector3(x / scalar, y / scalar, z / scalar)
    operator fun unaryMinus(): Vector3 = Vector3(-x, -y, -z)

    companion object {
        val ZERO: Vector3 get() = Vector3(0f, 0f, 0f)
        val ONE: Vector3 get() = Vector3(1f, 1f, 1f)
        val FORWARD: Vector3 get() = Vector3(0f, 0f, 1f)

        fun lerp(a: Vector3, b: Vector3, t: Float): Vector3 {
            val ct = Mathf.clamp01(t)
            return Vector3(
                a.x + (b.x - a.x) * ct,
                a.y + (b.y - a.y) * ct,
                a.z + (b.z - a.z) * ct
            )
        }
    }
}

data class Vector4(
    var x: Float = 0f,
    var y: Float = 0f,
    var z: Float = 0f,
    var w: Float = 0f
) {
    fun set(nx: Float, ny: Float, nz: Float, nw: Float): Vector4 {
        x = nx
        y = ny
        z = nz
        w = nw
        return this
    }

    companion object {
        val ZERO: Vector4 get() = Vector4(0f, 0f, 0f, 0f)
        val ONE: Vector4 get() = Vector4(1f, 1f, 1f, 1f)
    }
}
