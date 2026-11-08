package com.tengine.core.math

import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Standard 25-curve Easing library for Tweens, UI animations, Camera transitions, and Particles.
 */
enum class Easing {
    LINEAR,
    IN_QUAD, OUT_QUAD, IN_OUT_QUAD,
    IN_CUBIC, OUT_CUBIC, IN_OUT_CUBIC,
    IN_QUART, OUT_QUART, IN_OUT_QUART,
    IN_SINE, OUT_SINE, IN_OUT_SINE,
    IN_EXPO, OUT_EXPO, IN_OUT_EXPO,
    IN_CIRC, OUT_CIRC, IN_OUT_CIRC,
    IN_BACK, OUT_BACK, IN_OUT_BACK,
    IN_ELASTIC, OUT_ELASTIC,
    IN_BOUNCE, OUT_BOUNCE;

    fun evaluate(rawT: Float): Float {
        val t = Mathf.clamp01(rawT)
        return when (this) {
            LINEAR -> t
            IN_QUAD -> t * t
            OUT_QUAD -> 1f - (1f - t) * (1f - t)
            IN_OUT_QUAD -> if (t < 0.5f) 2f * t * t else 1f - (-2f * t + 2f).pow(2) * 0.5f
            IN_CUBIC -> t * t * t
            OUT_CUBIC -> 1f - (1f - t).pow(3)
            IN_OUT_CUBIC -> if (t < 0.5f) 4f * t * t * t else 1f - (-2f * t + 2f).pow(3) * 0.5f
            IN_QUART -> t * t * t * t
            OUT_QUART -> 1f - (1f - t).pow(4)
            IN_OUT_QUART -> if (t < 0.5f) 8f * t.pow(4) else 1f - (-2f * t + 2f).pow(4) * 0.5f
            IN_SINE -> 1f - cos(t * Mathf.HALF_PI)
            OUT_SINE -> sin(t * Mathf.HALF_PI)
            IN_OUT_SINE -> -(cos(Mathf.PI * t) - 1f) * 0.5f
            IN_EXPO -> if (t == 0f) 0f else 2f.pow(10f * t - 10f)
            OUT_EXPO -> if (t == 1f) 1f else 1f - 2f.pow(-10f * t)
            IN_OUT_EXPO -> when {
                t == 0f -> 0f
                t == 1f -> 1f
                t < 0.5f -> 2f.pow(20f * t - 10f) * 0.5f
                else -> (2f - 2f.pow(-20f * t + 10f)) * 0.5f
            }
            IN_CIRC -> 1f - sqrt(1f - t * t)
            OUT_CIRC -> sqrt(1f - (t - 1f) * (t - 1f))
            IN_OUT_CIRC -> if (t < 0.5f) {
                (1f - sqrt(1f - (2f * t).pow(2))) * 0.5f
            } else {
                (sqrt(1f - (-2f * t + 2f).pow(2)) + 1f) * 0.5f
            }
            IN_BACK -> {
                val c1 = 1.70158f
                val c3 = c1 + 1f
                c3 * t * t * t - c1 * t * t
            }
            OUT_BACK -> {
                val c1 = 1.70158f
                val c3 = c1 + 1f
                1f + c3 * (t - 1f).pow(3) + c1 * (t - 1f).pow(2)
            }
            IN_OUT_BACK -> {
                val c1 = 1.70158f
                val c2 = c1 * 1.525f
                if (t < 0.5f) {
                    ((2f * t).pow(2) * ((c2 + 1f) * 2f * t - c2)) * 0.5f
                } else {
                    ((2f * t - 2f).pow(2) * ((c2 + 1f) * (t * 2f - 2f) + c2) + 2f) * 0.5f
                }
            }
            IN_ELASTIC -> when (t) {
                0f -> 0f
                1f -> 1f
                else -> -2f.pow(10f * t - 10f) * sin((t * 10f - 10.75f) * (Mathf.TWO_PI / 3f))
            }
            OUT_ELASTIC -> when (t) {
                0f -> 0f
                1f -> 1f
                else -> 2f.pow(-10f * t) * sin((t * 10f - 0.75f) * (Mathf.TWO_PI / 3f)) + 1f
            }
            IN_BOUNCE -> 1f - bounceOut(1f - t)
            OUT_BOUNCE -> bounceOut(t)
        }
    }

    companion object {
        private fun bounceOut(x: Float): Float {
            val n1 = 7.5625f
            val d1 = 2.75f
            return when {
                x < 1f / d1 -> n1 * x * x
                x < 2f / d1 -> {
                    val t = x - 1.5f / d1
                    n1 * t * t + 0.75f
                }
                x < 2.5f / d1 -> {
                    val t = x - 2.25f / d1
                    n1 * t * t + 0.9375f
                }
                else -> {
                    val t = x - 2.625f / d1
                    n1 * t * t + 0.984375f
                }
            }
        }
    }
}

data class CurveKeyframe(
    val time: Float,
    val value: Float,
    val inTangent: Float = 0f,
    val outTangent: Float = 0f
)

/**
 * Unity-style cubic Hermite AnimationCurve for particle size over lifetime, camera shakes, and script curves.
 */
class AnimationCurve(keyframes: List<CurveKeyframe>) {
    val keys: List<CurveKeyframe> = if (keyframes.isEmpty()) {
        listOf(CurveKeyframe(0f, 1f), CurveKeyframe(1f, 1f))
    } else {
        keyframes.sortedBy { it.time }
    }

    fun evaluate(time: Float): Float {
        if (keys.size == 1) return keys[0].value
        if (time <= keys.first().time) return keys.first().value
        if (time >= keys.last().time) return keys.last().value

        for (i in 0 until keys.size - 1) {
            val k0 = keys[i]
            val k1 = keys[i + 1]
            if (time >= k0.time && time <= k1.time) {
                val dt = k1.time - k0.time
                if (dt <= Mathf.EPSILON) return k0.value
                val t = (time - k0.time) / dt
                val t2 = t * t
                val t3 = t2 * t

                val m0 = k0.outTangent * dt
                val m1 = k1.inTangent * dt

                val h00 = 2f * t3 - 3f * t2 + 1f
                val h10 = t3 - 2f * t2 + t
                val h01 = -2f * t3 + 3f * t2
                val h11 = t3 - t2

                return h00 * k0.value + h10 * m0 + h01 * k1.value + h11 * m1
            }
        }
        return keys.last().value
    }

    companion object {
        fun constant(value: Float): AnimationCurve =
            AnimationCurve(listOf(CurveKeyframe(0f, value), CurveKeyframe(1f, value)))

        fun linear(startValue: Float, endValue: Float): AnimationCurve {
            val slope = endValue - startValue
            return AnimationCurve(
                listOf(
                    CurveKeyframe(0f, startValue, slope, slope),
                    CurveKeyframe(1f, endValue, slope, slope)
                )
            )
        }

        fun bellCurve(peak: Float = 1f): AnimationCurve = AnimationCurve(
            listOf(
                CurveKeyframe(0f, 0f, 0f, 4f * peak),
                CurveKeyframe(0.35f, peak, 0f, 0f),
                CurveKeyframe(1f, 0f, -2f * peak, 0f)
            )
        )
    }
}
