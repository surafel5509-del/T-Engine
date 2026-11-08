package com.tengine.android.graphics

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.tengine.core.EngineLogger
import com.tengine.core.graphics.PixelBuffer
import com.tengine.core.graphics.Texture2D
import com.tengine.core.graphics.TextureFilter
import com.tengine.core.graphics.TextureWrap

/**
 * Loads Android Bitmaps / Asset images into cross-platform T-Engine [Texture2D] instances.
 */
object AndroidTextureLoader {

    fun fromBitmap(
        name: String,
        bitmap: Bitmap,
        filter: TextureFilter = TextureFilter.NEAREST,
        wrap: TextureWrap = TextureWrap.CLAMP_TO_EDGE
    ): Texture2D {
        val w = bitmap.width
        val h = bitmap.height
        val argbPixels = IntArray(w * h)
        bitmap.getPixels(argbPixels, 0, w, 0, 0, w, h)

        val buf = PixelBuffer(w, h)
        val out = buf.bytes
        var byteIdx = 0
        for (i in argbPixels.indices) {
            val px = argbPixels[i]
            val a = (px ushr 24) and 0xFF
            val r = (px ushr 16) and 0xFF
            val g = (px ushr 8) and 0xFF
            val b = px and 0xFF
            out[byteIdx++] = r.toByte()
            out[byteIdx++] = g.toByte()
            out[byteIdx++] = b.toByte()
            out[byteIdx++] = a.toByte()
        }

        return Texture2D(
            name = name,
            width = w,
            height = h,
            filter = filter,
            wrap = wrap,
            pixelBuffer = buf
        )
    }

    fun loadFromAssets(
        context: Context,
        assetPath: String,
        filter: TextureFilter = TextureFilter.LINEAR,
        wrap: TextureWrap = TextureWrap.CLAMP_TO_EDGE
    ): Texture2D? {
        return try {
            context.assets.open(assetPath).use { stream ->
                val opts = BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                val bmp = BitmapFactory.decodeStream(stream, null, opts) ?: return null
                val tex = fromBitmap(assetPath, bmp, filter, wrap)
                tex.assetPath = assetPath
                bmp.recycle()
                tex
            }
        } catch (e: Exception) {
            EngineLogger.error("AndroidTextureLoader", "Failed to load texture '$assetPath'", e)
            null
        }
    }
}
