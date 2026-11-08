package com.tengine.core.math

import kotlin.math.PI as K_PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Core mathematical utilities, interpolation helpers, critically damped springs,
 * and procedural 2D gradient noise built from scratch for T-Engine.
 */
object Mathf {
    const val PI: Float = K_PI.toFloat()
    const val TWO_PI: Float = (K_PI * 2.0).toFloat()
    const val HALF_PI: Float = (K_PI * 0.5).toFloat()
    const val DEG_TO_RAD: Float = (K_PI / 180.0).toFloat()
    const val RAD_TO_DEG: Float = (180.0 / K_PI).toFloat()
    const val EPSILON: Float = 1e-5f

    fun clamp(value: Float, minVal: Float, maxVal: Float): Float =
        if (value < minVal) minVal else if (value > maxVal) maxVal else value

    fun clamp(value: Int, minVal: Int, maxVal: Int): Int =
        if (value < minVal) minVal else if (value > maxVal) maxVal else value

    fun clamp01(value: Float): Float = clamp(value, 0f, 1f)

    fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * clamp01(t)

    fun lerpUnclamped(a: Float, b: Float, t: Float): Float = a + (b - a) * t

    fun inverseLerp(a: Float, b: Float, value: Float): Float {
        if (abs(b - a) <= EPSILON) return 0f
        return clamp01((value - a) / (b - a))
    }

    fun remap(inMin: Float, inMax: Float, outMin: Float, outMax: Float, value: Float): Float {
        val t = inverseLerp(inMin, inMax, value)
        return lerp(outMin, outMax, t)
    }

    fun moveTowards(current: Float, target: Float, maxDelta: Float): Float {
        if (abs(target - current) <= maxDelta) return target
        return current + sign(target - current) * maxDelta
    }

    fun sign(value: Float): Float = when {
        value > 0f -> 1f
        value < 0f -> -1f
        else -> 0f
    }

    fun repeat(t: Float, length: Float): Float {
        if (length <= EPSILON) return 0f
        return clamp(t - floor(t / length) * length, 0f, length)
    }

    fun pingPong(t: Float, length: Float): Float {
        val wrapped = repeat(t, length * 2f)
        return length - abs(wrapped - length)
    }

    /**
     * Shortest signed angle difference in degrees in range [-180, 180].
     */
    fun deltaAngle(currentDeg: Float, targetDeg: Float): Float {
        var delta = repeat(targetDeg - currentDeg, 360f)
        if (delta > 180f) delta -= 360f
        return delta
    }

    fun lerpAngle(aDeg: Float, bDeg: Float, t: Float): Float {
        val delta = deltaAngle(aDeg, bDeg)
        return aDeg + delta * clamp01(t)
    }

    fun moveTowardsAngle(currentDeg: Float, targetDeg: Float, maxDeltaDeg: Float): Float {
        val delta = deltaAngle(currentDeg, targetDeg)
        if (-maxDeltaDeg < delta && delta < maxDeltaDeg) return targetDeg
        return moveTowards(currentDeg, currentDeg + delta, maxDeltaDeg)
    }

    fun smoothStep(edge0: Float, edge1: Float, x: Float): Float {
        val t = clamp01((x - edge0) / max(EPSILON, edge1 - edge0))
        return t * t * (3f - 2f * t)
    }

    fun smootherStep(edge0: Float, edge1: Float, x: Float): Float {
        val t = clamp01((x - edge0) / max(EPSILON, edge1 - edge0))
        return t * t * t * (t * (t * 6f - 15f) + 10f)
    }

    /**
     * Critically damped spring smoothing (Unity-compatible SmoothDamp).
     * Mutates [velocityRef] element 0 with the updated velocity.
     */
    fun smoothDamp(
        current: Float,
        target: Float,
        velocityRef: FloatArray,
        smoothTime: Float,
        deltaTime: Float,
        maxSpeed: Float = Float.POSITIVE_INFINITY
    ): Float {
        val safeSmoothTime = max(0.0001f, smoothTime)
        val omega = 2f / safeSmoothTime
        val x = omega * deltaTime
        val exp = 1f / (1f + x + 0.48f * x * x + 0.235f * x * x * x)
        var change = current - target
        val originalTo = target

        val maxChange = maxSpeed * safeSmoothTime
        change = clamp(change, -maxChange, maxChange)
        val adjustedTarget = current - change

        val temp = (velocityRef[0] + omega * change) * deltaTime
        velocityRef[0] = (velocityRef[0] - omega * temp) * exp
        var output = adjustedTarget + (change + temp) * exp

        if ((originalTo - current > 0f) == (output > originalTo)) {
            output = originalTo
            velocityRef[0] = if (deltaTime > EPSILON) (output - originalTo) / deltaTime else 0f
        }
        return output
    }

    fun approximately(a: Float, b: Float, tolerance: Float = EPSILON): Boolean =
        abs(a - b) <= max(tolerance, EPSILON * max(abs(a), abs(b)))

    fun isPowerOfTwo(value: Int): Boolean = value > 0 && (value and (value - 1)) == 0

    fun nextPowerOfTwo(value: Int): Int {
        if (value <= 1) return 1
        var v = value - 1
        v = v or (v ushr 1)
        v = v or (v ushr 2)
        v = v or (v ushr 4)
        v = v or (v ushr 8)
        v = v or (v ushr 16)
        return v + 1
    }

    private fun hash2D(ix: Int, iy: Int, seed: Int): Int {
        var h = ix * 374761393 + iy * 668265263 + seed * 1442695041
        h = (h xor (h ushr 13)) * 1274126177
        return h xor (h ushr 16)
    }

    private fun grad2D(hash: Int, dx: Float, dy: Float): Float {
        return when (hash and 7) {
            0 -> dx + dy
            1 -> -dx + dy
            2 -> dx - dy
            3 -> -dx - dy
            4 -> dx * 1.4142135f
            5 -> -dx * 1.4142135f
            6 -> dy * 1.4142135f
            else -> -dy * 1.4142135f
        }
    }

    /**
     * Smooth 2D Perlin-style gradient noise in approximate range [-1, 1].
     */
    fun noise2D(x: Float, y: Float, seed: Int = 1337): Float {
        val x0 = floor(x).toInt()
        val y0 = floor(y).toInt()
        val x1 = x0 + 1
        val y1 = y0 + 1

        val fx = x - x0
        val fy = y - y0
        val u = fx * fx * fx * (fx * (fx * 6f - 15f) + 10f)
        val v = fy * fy * fy * (fy * (fy * 6f - 15f) + 10f)

        val n00 = grad2D(hash2D(x0, y0, seed), fx, fy)
        val n10 = grad2D(hash2D(x1, y0, seed), fx - 1f, fy)
        val n01 = grad2D(hash2D(x0, y1, seed), fx, fy - 1f)
        val n11 = grad2D(hash2D(x1, y1, seed), fx - 1f, fy - 1f)

        val nx0 = lerpUnclamped(n00, n10, u)
        val nx1 = lerpUnclamped(n01, n11, u)
        return clamp(lerpUnclamped(nx0, nx1, v) * 0.70710677f, -1f, 1f)
    }

    /**
     * Fractal Brownian Motion (multi-octave 2D noise) normalized to [-1, 1].
     */
    fun fbm2D(
        x: Float,
        y: Float,
        octaves: Int = 4,
        lacunarity: Float = 2f,
        gain: Float = 0.5f,
        seed: Int = 1337
    ): Float {
        var total = 0f
        var frequency = 1f
        var amplitude = 1f
        var maxValue = 0f
        for (i in 0 until max(1, octaves)) {
            total += noise2D(x * frequency, y * frequency, seed + i * 31) * amplitude
            maxValue += amplitude
            amplitude *= gain
            frequency *= lacunarity
        }
        return if (maxValue > EPSILON) total / maxValue else 0f
    }

    fun fastSin(radians: Float): Float = sin(radians)
    fun fastCos(radians: Float): Float = cos(radians)
    fun hypot2D(dx: Float, dy: Float): Float = sqrt(dx * dx + dy * dy)
}
