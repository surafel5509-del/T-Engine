package com.tengine.core.graphics

import com.tengine.core.ecs.TObject
import com.tengine.core.math.Color
import com.tengine.core.math.Mathf
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Raw RGBA8888 CPU pixel buffer (4 bytes per pixel: R, G, B, A).
 * Enables procedural sprite/atlas generation in pure Kotlin AND seamless OpenGL ES context recovery.
 */
class PixelBuffer(
    val width: Int,
    val height: Int,
    val bytes: ByteArray = ByteArray(width * height * 4)
) {
    fun setPixel(x: Int, y: Int, r: Int, g: Int, b: Int, a: Int) {
        if (x !in 0 until width || y !in 0 until height) return
        val idx = (y * width + x) * 4
        bytes[idx] = (r.coerceIn(0, 255)).toByte()
        bytes[idx + 1] = (g.coerceIn(0, 255)).toByte()
        bytes[idx + 2] = (b.coerceIn(0, 255)).toByte()
        bytes[idx + 3] = (a.coerceIn(0, 255)).toByte()
    }

    fun setPixel(x: Int, y: Int, color: Color) {
        setPixel(
            x,
            y,
            (color.r * 255f).roundToInt(),
            (color.g * 255f).roundToInt(),
            (color.b * 255f).roundToInt(),
            (color.a * 255f).roundToInt()
        )
    }

    fun fillRect(rx: Int, ry: Int, rw: Int, rh: Int, color: Color) {
        val x0 = max(0, rx)
        val y0 = max(0, ry)
        val x1 = min(width, rx + rw)
        val y1 = min(height, ry + rh)
        val r = (color.r * 255f).roundToInt()
        val g = (color.g * 255f).roundToInt()
        val b = (color.b * 255f).roundToInt()
        val a = (color.a * 255f).roundToInt()
        for (y in y0 until y1) {
            for (x in x0 until x1) {
                setPixel(x, y, r, g, b, a)
            }
        }
    }
}

/**
 * GPU 2D Texture resource. Retains [pixelBuffer] or [assetPath] so OpenGL ES contexts
 * can automatically re-upload textures if Android recreates the EGL surface.
 */
class Texture2D(
    name: String,
    val width: Int,
    val height: Int,
    var filter: TextureFilter = TextureFilter.NEAREST,
    var wrap: TextureWrap = TextureWrap.CLAMP_TO_EDGE,
    var pixelBuffer: PixelBuffer? = null,
    var assetPath: String? = null
) : TObject(name) {

    var gpuHandle: Int = 0
    var gpuContextGeneration: Int = -1
    var isDirty: Boolean = true

    fun markModified() {
        isDirty = true
    }

    companion object {
        val whitePixel: Texture2D by lazy {
            val buf = PixelBuffer(2, 2)
            buf.fillRect(0, 0, 2, 2, Color.WHITE)
            Texture2D("BuiltIn_White", 2, 2, TextureFilter.NEAREST, TextureWrap.REPEAT, buf)
        }

        val circleTexture: Texture2D by lazy {
            val size = 64
            val buf = PixelBuffer(size, size)
            val center = (size - 1) * 0.5f
            val radius = size * 0.47f
            for (y in 0 until size) {
                for (x in 0 until size) {
                    val dx = x - center
                    val dy = y - center
                    val dist = sqrt(dx * dx + dy * dy)
                    val alpha = Mathf.clamp01((radius - dist) * 1.2f)
                    buf.setPixel(x, y, Color(1f, 1f, 1f, alpha))
                }
            }
            Texture2D("BuiltIn_Circle", size, size, TextureFilter.LINEAR, TextureWrap.CLAMP_TO_EDGE, buf)
        }

        val softGlowTexture: Texture2D by lazy {
            val size = 64
            val buf = PixelBuffer(size, size)
            val center = (size - 1) * 0.5f
            val maxR = size * 0.5f
            for (y in 0 until size) {
                for (x in 0 until size) {
                    val dx = x - center
                    val dy = y - center
                    val norm = Mathf.clamp01(sqrt(dx * dx + dy * dy) / maxR)
                    val falloff = (1f - norm) * (1f - norm)
                    buf.setPixel(x, y, Color(1f, 1f, 1f, falloff))
                }
            }
            Texture2D("BuiltIn_SoftGlow", size, size, TextureFilter.LINEAR, TextureWrap.CLAMP_TO_EDGE, buf)
        }

        val diamondTexture: Texture2D by lazy {
            val size = 48
            val buf = PixelBuffer(size, size)
            val center = (size - 1) * 0.5f
            val r = size * 0.45f
            for (y in 0 until size) {
                for (x in 0 until size) {
                    val manhattan = abs(x - center) + abs(y - center)
                    val alpha = Mathf.clamp01((r - manhattan) * 1.2f)
                    val highlight = if (x < center && y < center) 1.0f else 0.88f
                    buf.setPixel(x, y, Color(highlight, highlight, 1f, alpha))
                }
            }
            Texture2D("BuiltIn_Diamond", size, size, TextureFilter.LINEAR, TextureWrap.CLAMP_TO_EDGE, buf)
        }

        val starTexture: Texture2D by lazy {
            val size = 64
            val buf = PixelBuffer(size, size)
            val center = (size - 1) * 0.5f
            for (y in 0 until size) {
                for (x in 0 until size) {
                    val dx = x - center
                    val dy = y - center
                    val dist = sqrt(dx * dx + dy * dy)
                    val angle = atan2(dy, dx)
                    val starRadius = size * (0.26f + 0.18f * cos(angle * 5f))
                    val alpha = Mathf.clamp01((starRadius - dist) * 1.1f)
                    buf.setPixel(x, y, Color(1f, 1f, 1f, alpha))
                }
            }
            Texture2D("BuiltIn_Star", size, size, TextureFilter.LINEAR, TextureWrap.CLAMP_TO_EDGE, buf)
        }

        val capsuleTexture: Texture2D by lazy {
            val w = 48
            val h = 64
            val buf = PixelBuffer(w, h)
            val cx = (w - 1) * 0.5f
            val r = w * 0.44f
            val topY = r + 2f
            val botY = h - r - 2f
            for (y in 0 until h) {
                for (x in 0 until w) {
                    val clampedY = Mathf.clamp(y.toFloat(), topY, botY)
                    val dx = x - cx
                    val dy = y - clampedY
                    val dist = sqrt(dx * dx + dy * dy)
                    val alpha = Mathf.clamp01((r - dist) * 1.25f)
                    buf.setPixel(x, y, Color(1f, 1f, 1f, alpha))
                }
            }
            Texture2D("BuiltIn_Capsule", w, h, TextureFilter.LINEAR, TextureWrap.CLAMP_TO_EDGE, buf)
        }

        val checkerTexture: Texture2D by lazy {
            val size = 32
            val buf = PixelBuffer(size, size)
            for (y in 0 until size) {
                for (x in 0 until size) {
                    val isLight = ((x / 8) + (y / 8)) % 2 == 0
                    val c = if (isLight) Color(0.88f, 0.88f, 0.92f, 1f) else Color(0.58f, 0.60f, 0.68f, 1f)
                    buf.setPixel(x, y, c)
                }
            }
            Texture2D("BuiltIn_Checker", size, size, TextureFilter.NEAREST, TextureWrap.REPEAT, buf)
        }
    }
}
