package com.tengine.core.graphics

import com.tengine.core.math.Color

/**
 * Glyph UV and typographic advance metrics for hardware-batched OpenGL ES text rendering.
 */
data class GlyphMetrics(
    val codePoint: Int,
    val u0: Float,
    val v0: Float,
    val u1: Float,
    val v1: Float,
    val widthPx: Float,
    val heightPx: Float,
    val xOffsetPx: Float,
    val yOffsetPx: Float,
    val xAdvancePx: Float
)

enum class TextAlign {
    LEFT, CENTER, RIGHT
}

/**
 * Texture-backed Font Atlas usable on any RenderBackend.
 * Includes a crisp built-in 5x7 ASCII bitmap font generated procedurally in pure Kotlin
 * so HUDs and debug overlays work immediately with zero external font files!
 */
class FontAtlas(
    val name: String,
    val texture: Texture2D,
    val lineHeightPx: Float,
    private val glyphs: Map<Int, GlyphMetrics>
) {
    fun getGlyph(ch: Char): GlyphMetrics? = glyphs[ch.code] ?: glyphs['?'.code]

    fun measureTextWidth(text: String, scale: Float = 1f): Float {
        var maxLineWidth = 0f
        var currentWidth = 0f
        for (ch in text) {
            if (ch == '\n') {
                if (currentWidth > maxLineWidth) maxLineWidth = currentWidth
                currentWidth = 0f
                continue
            }
            val g = getGlyph(ch) ?: continue
            currentWidth += g.xAdvancePx * scale
        }
        return if (currentWidth > maxLineWidth) currentWidth else maxLineWidth
    }

    companion object {
        val defaultPixelFont: FontAtlas by lazy { buildEmbeddedAsciiFont() }

        // Compact 5x7 bitmap rows (each Int encodes 7 rows x 5 bits = 35 bits packed in Long)
        private fun buildEmbeddedAsciiFont(): FontAtlas {
            val cols = 16
            val rows = 6
            val cellW = 8
            val cellH = 10
            val texW = cols * cellW // 128
            val texH = rows * cellH // 60 -> 64 POT
            val potH = 64
            val buf = PixelBuffer(texW, potH)
            val glyphMap = HashMap<Int, GlyphMetrics>(96)

            for (ascii in 32..126) {
                val idx = ascii - 32
                val gx = (idx % cols) * cellW
                val gy = (idx / cols) * cellH
                val pattern = ascii5x7(ascii.toChar())

                for (r in 0 until 7) {
                    val rowBits = pattern[r]
                    for (c in 0 until 5) {
                        if (((rowBits shr (4 - c)) and 1) != 0) {
                            // Draw crisp white pixel + subtle dark drop-outline alpha
                            buf.setPixel(gx + 1 + c, gy + 1 + r, Color.WHITE)
                        }
                    }
                }

                glyphMap[ascii] = GlyphMetrics(
                    codePoint = ascii,
                    u0 = gx.toFloat() / texW,
                    v0 = gy.toFloat() / potH,
                    u1 = (gx + 6).toFloat() / texW,
                    v1 = (gy + 8).toFloat() / potH,
                    widthPx = 6f,
                    heightPx = 8f,
                    xOffsetPx = 0f,
                    yOffsetPx = 0f,
                    xAdvancePx = 6f
                )
            }

            val texture = Texture2D(
                name = "BuiltIn_Ascii5x7",
                width = texW,
                height = potH,
                filter = TextureFilter.NEAREST,
                wrap = TextureWrap.CLAMP_TO_EDGE,
                pixelBuffer = buf
            )
            return FontAtlas("BuiltIn_Ascii5x7", texture, lineHeightPx = 10f, glyphs = glyphMap)
        }

        private fun ascii5x7(ch: Char): IntArray = when (ch.uppercaseChar()) {
            ' ' -> intArrayOf(0, 0, 0, 0, 0, 0, 0)
            '!' -> intArrayOf(4, 4, 4, 4, 4, 0, 4)
            '"' -> intArrayOf(10, 10, 0, 0, 0, 0, 0)
            '#' -> intArrayOf(10, 31, 10, 10, 31, 10, 0)
            '%' -> intArrayOf(25, 25, 2, 4, 8, 19, 19)
            '(' -> intArrayOf(2, 4, 8, 8, 8, 4, 2)
            ')' -> intArrayOf(8, 4, 2, 2, 2, 4, 8)
            '*' -> intArrayOf(0, 4, 21, 14, 21, 4, 0)
            '+' -> intArrayOf(0, 4, 4, 31, 4, 4, 0)
            ',' -> intArrayOf(0, 0, 0, 0, 4, 4, 8)
            '-' -> intArrayOf(0, 0, 0, 31, 0, 0, 0)
            '.' -> intArrayOf(0, 0, 0, 0, 0, 0, 4)
            '/' -> intArrayOf(1, 1, 2, 4, 8, 16, 16)
            '0' -> intArrayOf(14, 17, 19, 21, 25, 17, 14)
            '1' -> intArrayOf(4, 12, 4, 4, 4, 4, 14)
            '2' -> intArrayOf(14, 17, 1, 6, 8, 16, 31)
            '3' -> intArrayOf(31, 2, 4, 6, 1, 17, 14)
            '4' -> intArrayOf(2, 6, 10, 18, 31, 2, 2)
            '5' -> intArrayOf(31, 16, 30, 1, 1, 17, 14)
            '6' -> intArrayOf(6, 8, 16, 30, 17, 17, 14)
            '7' -> intArrayOf(31, 1, 2, 4, 8, 8, 8)
            '8' -> intArrayOf(14, 17, 17, 14, 17, 17, 14)
            '9' -> intArrayOf(14, 17, 17, 15, 1, 2, 12)
            ':' -> intArrayOf(0, 4, 0, 0, 0, 4, 0)
            '<' -> intArrayOf(2, 4, 8, 16, 8, 4, 2)
            '=' -> intArrayOf(0, 0, 31, 0, 31, 0, 0)
            '>' -> intArrayOf(8, 4, 2, 1, 2, 4, 8)
            '?' -> intArrayOf(14, 17, 1, 2, 4, 0, 4)
            'A' -> intArrayOf(14, 17, 17, 31, 17, 17, 17)
            'B' -> intArrayOf(30, 17, 17, 30, 17, 17, 30)
            'C' -> intArrayOf(14, 17, 16, 16, 16, 17, 14)
            'D' -> intArrayOf(28, 18, 17, 17, 17, 18, 28)
            'E' -> intArrayOf(31, 16, 16, 30, 16, 16, 31)
            'F' -> intArrayOf(31, 16, 16, 30, 16, 16, 16)
            'G' -> intArrayOf(14, 17, 16, 23, 17, 17, 15)
            'H' -> intArrayOf(17, 17, 17, 31, 17, 17, 17)
            'I' -> intArrayOf(14, 4, 4, 4, 4, 4, 14)
            'J' -> intArrayOf(7, 2, 2, 2, 2, 18, 12)
            'K' -> intArrayOf(17, 18, 20, 24, 20, 18, 17)
            'L' -> intArrayOf(16, 16, 16, 16, 16, 16, 31)
            'M' -> intArrayOf(17, 27, 21, 21, 17, 17, 17)
            'N' -> intArrayOf(17, 25, 21, 19, 17, 17, 17)
            'O' -> intArrayOf(14, 17, 17, 17, 17, 17, 14)
            'P' -> intArrayOf(30, 17, 17, 30, 16, 16, 16)
            'Q' -> intArrayOf(14, 17, 17, 17, 21, 18, 13)
            'R' -> intArrayOf(30, 17, 17, 30, 20, 18, 17)
            'S' -> intArrayOf(15, 16, 16, 14, 1, 1, 30)
            'T' -> intArrayOf(31, 4, 4, 4, 4, 4, 4)
            'U' -> intArrayOf(17, 17, 17, 17, 17, 17, 14)
            'V' -> intArrayOf(17, 17, 17, 17, 17, 10, 4)
            'W' -> intArrayOf(17, 17, 17, 21, 21, 27, 17)
            'X' -> intArrayOf(17, 17, 10, 4, 10, 17, 17)
            'Y' -> intArrayOf(17, 17, 10, 4, 4, 4, 4)
            'Z' -> intArrayOf(31, 1, 2, 4, 8, 16, 31)
            '[' -> intArrayOf(14, 8, 8, 8, 8, 8, 14)
            ']' -> intArrayOf(14, 2, 2, 2, 2, 2, 14)
            '_' -> intArrayOf(0, 0, 0, 0, 0, 0, 31)
            else -> intArrayOf(14, 17, 17, 17, 17, 17, 14)
        }
    }
}
