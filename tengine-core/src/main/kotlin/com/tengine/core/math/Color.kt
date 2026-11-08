package com.tengine.core.math

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * RGBA Floating-Point Color with HSV conversion, Hex parsing, packed ARGB/ABGR integer conversion,
 * and multi-stop ColorGradient interpolation for particles, lights, and UI.
 */
data class Color(
    val r: Float,
    val g: Float,
    val b: Float,
    val a: Float = 1f
) {
    fun withAlpha(newAlpha: Float): Color = Color(r, g, b, Mathf.clamp01(newAlpha))

    fun brighten(amount: Float): Color = Color(
        Mathf.clamp01(r + amount),
        Mathf.clamp01(g + amount),
        Mathf.clamp01(b + amount),
        a
    )

    fun darken(amount: Float): Color = brighten(-amount)

    operator fun times(other: Color): Color = Color(
        r * other.r,
        g * other.g,
        b * other.b,
        a * other.a
    )

    operator fun times(scalar: Float): Color = Color(
        Mathf.clamp01(r * scalar),
        Mathf.clamp01(g * scalar),
        Mathf.clamp01(b * scalar),
        a
    )

    /** Packed Android ARGB_8888 integer (0xAARRGGBB). */
    fun toArgbInt(): Int {
        val ai = (Mathf.clamp01(a) * 255f).roundToInt() and 0xFF
        val ri = (Mathf.clamp01(r) * 255f).roundToInt() and 0xFF
        val gi = (Mathf.clamp01(g) * 255f).roundToInt() and 0xFF
        val bi = (Mathf.clamp01(b) * 255f).roundToInt() and 0xFF
        return (ai shl 24) or (ri shl 16) or (gi shl 8) or bi
    }

    /** Packed OpenGL little-endian ABGR_8888 integer (0xAABBGGRR). */
    fun toAbgrInt(): Int {
        val ai = (Mathf.clamp01(a) * 255f).roundToInt() and 0xFF
        val ri = (Mathf.clamp01(r) * 255f).roundToInt() and 0xFF
        val gi = (Mathf.clamp01(g) * 255f).roundToInt() and 0xFF
        val bi = (Mathf.clamp01(b) * 255f).roundToInt() and 0xFF
        return (ai shl 24) or (bi shl 16) or (gi shl 8) or ri
    }

    fun toHex(includeAlpha: Boolean = true): String {
        val ri = (Mathf.clamp01(r) * 255f).roundToInt()
        val gi = (Mathf.clamp01(g) * 255f).roundToInt()
        val bi = (Mathf.clamp01(b) * 255f).roundToInt()
        val ai = (Mathf.clamp01(a) * 255f).roundToInt()
        return if (includeAlpha) {
            "#%02X%02X%02X%02X".format(ri, gi, bi, ai)
        } else {
            "#%02X%02X%02X".format(ri, gi, bi)
        }
    }

    /** Returns [hue (0..360), saturation (0..1), value (0..1)]. */
    fun toHSV(): FloatArray {
        val cMax = max(r, max(g, b))
        val cMin = min(r, min(g, b))
        val delta = cMax - cMin

        var h = 0f
        if (delta > Mathf.EPSILON) {
            h = when (cMax) {
                r -> 60f * (((g - b) / delta) % 6f)
                g -> 60f * (((b - r) / delta) + 2f)
                else -> 60f * (((r - g) / delta) + 4f)
            }
            if (h < 0f) h += 360f
        }
        val s = if (cMax <= Mathf.EPSILON) 0f else delta / cMax
        return floatArrayOf(h, s, cMax)
    }

    companion object {
        val WHITE = Color(1f, 1f, 1f, 1f)
        val BLACK = Color(0f, 0f, 0f, 1f)
        val TRANSPARENT = Color(0f, 0f, 0f, 0f)
        val RED = Color(0.95f, 0.24f, 0.27f, 1f)
        val GREEN = Color(0.24f, 0.88f, 0.45f, 1f)
        val BLUE = Color(0.22f, 0.56f, 0.98f, 1f)
        val CYAN = Color(0.16f, 0.88f, 0.96f, 1f)
        val MAGENTA = Color(0.93f, 0.26f, 0.84f, 1f)
        val YELLOW = Color(1.0f, 0.85f, 0.20f, 1f)
        val ORANGE = Color(1.0f, 0.55f, 0.14f, 1f)
        val PURPLE = Color(0.62f, 0.34f, 0.96f, 1f)
        val GRAY = Color(0.5f, 0.5f, 0.5f, 1f)
        val DARK_GRAY = Color(0.18f, 0.20f, 0.24f, 1f)
        val LIGHT_GRAY = Color(0.78f, 0.80f, 0.84f, 1f)

        fun fromArgbInt(argb: Int): Color {
            val a = ((argb ushr 24) and 0xFF) / 255f
            val r = ((argb ushr 16) and 0xFF) / 255f
            val g = ((argb ushr 8) and 0xFF) / 255f
            val b = (argb and 0xFF) / 255f
            return Color(r, g, b, a)
        }

        fun fromBytes(r: Int, g: Int, b: Int, a: Int = 255): Color =
            Color(r / 255f, g / 255f, b / 255f, a / 255f)

        fun fromHex(hexString: String): Color {
            val clean = hexString.trim().removePrefix("#")
            return when (clean.length) {
                6 -> {
                    val v = clean.toLongOrNull(16) ?: return WHITE
                    val r = ((v shr 16) and 0xFF) / 255f
                    val g = ((v shr 8) and 0xFF) / 255f
                    val b = (v and 0xFF) / 255f
                    Color(r, g, b, 1f)
                }
                8 -> {
                    val v = clean.toLongOrNull(16) ?: return WHITE
                    val r = ((v shr 24) and 0xFF) / 255f
                    val g = ((v shr 16) and 0xFF) / 255f
                    val b = ((v shr 8) and 0xFF) / 255f
                    val a = (v and 0xFF) / 255f
                    Color(r, g, b, a)
                }
                else -> WHITE
            }
        }

        fun fromHSV(hueDeg: Float, saturation: Float, value: Float, alpha: Float = 1f): Color {
            val h = Mathf.repeat(hueDeg, 360f)
            val s = Mathf.clamp01(saturation)
            val v = Mathf.clamp01(value)
            val c = v * s
            val x = c * (1f - kotlin.math.abs((h / 60f) % 2f - 1f))
            val m = v - c
            val (rp, gp, bp) = when {
                h < 60f -> Triple(c, x, 0f)
                h < 120f -> Triple(x, c, 0f)
                h < 180f -> Triple(0f, c, x)
                h < 240f -> Triple(0f, x, c)
                h < 300f -> Triple(x, 0f, c)
                else -> Triple(c, 0f, x)
            }
            return Color(rp + m, gp + m, bp + m, Mathf.clamp01(alpha))
        }

        fun lerp(a: Color, b: Color, t: Float): Color {
            val ct = Mathf.clamp01(t)
            return Color(
                a.r + (b.r - a.r) * ct,
                a.g + (b.g - a.g) * ct,
                a.b + (b.b - a.b) * ct,
                a.a + (b.a - a.a) * ct
            )
        }
    }
}

data class GradientStop(val time: Float, val color: Color)

class ColorGradient(stops: List<GradientStop>) {
    val stops: List<GradientStop> = if (stops.isEmpty()) {
        listOf(GradientStop(0f, Color.WHITE), GradientStop(1f, Color.WHITE))
    } else {
        stops.sortedBy { it.time }
    }

    fun evaluate(t: Float): Color {
        val ct = Mathf.clamp01(t)
        if (ct <= stops.first().time) return stops.first().color
        if (ct >= stops.last().time) return stops.last().color
        for (i in 0 until stops.size - 1) {
            val s0 = stops[i]
            val s1 = stops[i + 1]
            if (ct >= s0.time && ct <= s1.time) {
                val localT = Mathf.inverseLerp(s0.time, s1.time, ct)
                return Color.lerp(s0.color, s1.color, localT)
            }
        }
        return stops.last().color
    }

    companion object {
        fun twoColor(start: Color, end: Color): ColorGradient = ColorGradient(
            listOf(GradientStop(0f, start), GradientStop(1f, end))
        )

        fun fire(): ColorGradient = ColorGradient(
            listOf(
                GradientStop(0.0f, Color(1f, 0.98f, 0.75f, 1f)),
                GradientStop(0.25f, Color(1f, 0.65f, 0.12f, 0.95f)),
                GradientStop(0.65f, Color(0.88f, 0.18f, 0.06f, 0.65f)),
                GradientStop(1.0f, Color(0.18f, 0.16f, 0.16f, 0f))
            )
        )

        fun neonCyanFade(): ColorGradient = ColorGradient(
            listOf(
                GradientStop(0.0f, Color(0.75f, 1f, 1f, 1f)),
                GradientStop(0.4f, Color(0.12f, 0.88f, 1f, 0.85f)),
                GradientStop(1.0f, Color(0.25f, 0.20f, 0.95f, 0f))
            )
        )
    }
}
