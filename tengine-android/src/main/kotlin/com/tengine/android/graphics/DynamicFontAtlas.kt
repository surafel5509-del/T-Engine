package com.tengine.android.graphics

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.Typeface
import com.tengine.core.graphics.FontAtlas
import com.tengine.core.graphics.GlyphMetrics
import com.tengine.core.graphics.TextureFilter
import com.tengine.core.graphics.TextureWrap
import kotlin.math.ceil

/**
 * Generates high-DPI anti-aliased OpenGL [FontAtlas] textures on the fly from any Android [Typeface].
 */
object DynamicFontAtlas {

    fun build(
        typeface: Typeface = Typeface.MONOSPACE,
        fontSizePx: Float = 28f,
        bold: Boolean = true
    ): FontAtlas {
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = if (bold) Typeface.create(typeface, Typeface.BOLD) else typeface
            textSize = fontSizePx
            color = AndroidColor.WHITE
            style = Paint.Style.FILL
        }

        val fm = fillPaint.fontMetrics
        val ascent = -fm.ascent
        val descent = fm.descent
        val cellH = ceil(ascent + descent + 6f).toInt().coerceAtLeast(12)
        val atlasW = 512
        val atlasH = 512

        val bitmap = Bitmap.createBitmap(atlasW, atlasH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val glyphs = HashMap<Int, GlyphMetrics>(96)

        var penX = 4
        var penY = 4

        for (code in 32..126) {
            val chStr = code.toChar().toString()
            val advance = fillPaint.measureText(chStr).coerceAtLeast(4f)
            val glyphW = ceil(advance + 4f).toInt()

            if (penX + glyphW >= atlasW - 2) {
                penX = 4
                penY += cellH + 2
            }
            if (penY + cellH >= atlasH - 2) break

            val baselineY = penY + ascent + 2f
            canvas.drawText(chStr, penX + 1f, baselineY, fillPaint)

            glyphs[code] = GlyphMetrics(
                codePoint = code,
                u0 = penX.toFloat() / atlasW,
                v0 = penY.toFloat() / atlasH,
                u1 = (penX + glyphW).toFloat() / atlasW,
                v1 = (penY + cellH).toFloat() / atlasH,
                widthPx = glyphW.toFloat(),
                heightPx = cellH.toFloat(),
                xOffsetPx = 0f,
                yOffsetPx = 0f,
                xAdvancePx = advance
            )
            penX += glyphW + 2
        }

        val texture = AndroidTextureLoader.fromBitmap(
            name = "DynamicFont_${fontSizePx.toInt()}",
            bitmap = bitmap,
            filter = TextureFilter.LINEAR,
            wrap = TextureWrap.CLAMP_TO_EDGE
        )
        bitmap.recycle()

        return FontAtlas(
            name = texture.name,
            texture = texture,
            lineHeightPx = cellH.toFloat(),
            glyphs = glyphs
        )
    }
}
