package com.tengine.core.graphics

import com.tengine.core.math.Color
import com.tengine.core.math.Mathf
import com.tengine.core.math.Matrix4
import kotlin.math.cos
import kotlin.math.sin

/**
 * High-speed Dynamic 2D Quad Batcher packing up to [maxSprites] textured quads per GPU draw call.
 * Vertex layout (8 floats / 32 bytes per vertex): [x, y, u, v, r, g, b, a].
 * Automatically flushes when switching textures, blend modes, or 2D lighting state!
 */
class SpriteBatch(
    var backend: RenderBackend,
    val maxSprites: Int = 4096
) {
    companion object {
        const val FLOATS_PER_VERTEX = 8
        const val VERTICES_PER_SPRITE = 4
        const val FLOATS_PER_SPRITE = FLOATS_PER_VERTEX * VERTICES_PER_SPRITE
        const val INDICES_PER_SPRITE = 6
    }

    private val vertexData = FloatArray(maxSprites * FLOATS_PER_SPRITE)
    private val indexData = ShortArray(maxSprites * INDICES_PER_SPRITE)

    private var spriteCountInBatch: Int = 0
    private var isDrawing: Boolean = false
    private var currentTexture: Texture2D? = null
    private var currentBlendMode: BlendMode = BlendMode.ALPHA
    private var currentLit: Boolean = false
    private val currentProjView = Matrix4()

    var ambientLightColor: Color = Color.WHITE
    var activeLights: List<LightUniformData> = emptyList()

    // Per-frame telemetry
    var drawCallsThisFrame: Int = 0
        private set
    var spritesSubmittedThisFrame: Int = 0
        private set
    val trianglesSubmittedThisFrame: Int
        get() = spritesSubmittedThisFrame * 2

    init {
        var v = 0
        var i = 0
        repeat(maxSprites) {
            indexData[i++] = (v + 0).toShort()
            indexData[i++] = (v + 1).toShort()
            indexData[i++] = (v + 2).toShort()
            indexData[i++] = (v + 2).toShort()
            indexData[i++] = (v + 3).toShort()
            indexData[i++] = (v + 0).toShort()
            v += 4
        }
    }

    fun resetFrameStats() {
        drawCallsThisFrame = 0
        spritesSubmittedThisFrame = 0
    }

    fun begin(
        projectionView: Matrix4,
        blendMode: BlendMode = BlendMode.ALPHA,
        isLit: Boolean = false
    ) {
        check(!isDrawing) { "SpriteBatch.begin() called while already drawing" }
        isDrawing = true
        currentProjView.setFrom(projectionView)
        currentBlendMode = blendMode
        currentLit = isLit
        currentTexture = null
        spriteCountInBatch = 0
    }

    fun setState(blendMode: BlendMode, isLit: Boolean) {
        if (currentBlendMode != blendMode || currentLit != isLit) {
            flush()
            currentBlendMode = blendMode
            currentLit = isLit
        }
    }

    fun drawSprite(
        sprite: Sprite,
        worldX: Float,
        worldY: Float,
        width: Float = sprite.worldWidth,
        height: Float = sprite.worldHeight,
        rotationDeg: Float = 0f,
        scaleX: Float = 1f,
        scaleY: Float = 1f,
        color: Color = Color.WHITE,
        flipX: Boolean = false,
        flipY: Boolean = false
    ) {
        val u0 = if (flipX) sprite.u1 else sprite.u0
        val u1 = if (flipX) sprite.u0 else sprite.u1
        // Note: In world coordinates +Y is UP while image row 0 is TOP, so v1 is bottom and v0 is top
        val vBottom = if (flipY) sprite.v0 else sprite.v1
        val vTop = if (flipY) sprite.v1 else sprite.v0

        drawTextureRegion(
            texture = sprite.texture,
            worldX = worldX,
            worldY = worldY,
            width = width * scaleX,
            height = height * scaleY,
            pivotX = sprite.pivot.x,
            pivotY = sprite.pivot.y,
            rotationDeg = rotationDeg,
            u0 = u0,
            vBottom = vBottom,
            u1 = u1,
            vTop = vTop,
            color = color
        )
    }

    fun drawTextureRegion(
        texture: Texture2D,
        worldX: Float,
        worldY: Float,
        width: Float,
        height: Float,
        pivotX: Float = 0.5f,
        pivotY: Float = 0.5f,
        rotationDeg: Float = 0f,
        u0: Float = 0f,
        vBottom: Float = 1f,
        u1: Float = 1f,
        vTop: Float = 0f,
        color: Color = Color.WHITE
    ) {
        check(isDrawing) { "SpriteBatch.begin() must be called before drawing" }

        if (currentTexture !== texture || spriteCountInBatch >= maxSprites) {
            flush()
            currentTexture = texture
        }

        val fx0 = -pivotX * width
        val fy0 = -pivotY * height
        val fx1 = fx0 + width
        val fy1 = fy0 + height

        val x0: Float; val y0: Float
        val x1: Float; val y1: Float
        val x2: Float; val y2: Float
        val x3: Float; val y3: Float

        if (kotlin.math.abs(rotationDeg) <= 0.0001f) {
            x0 = worldX + fx0; y0 = worldY + fy0
            x1 = worldX + fx1; y1 = worldY + fy0
            x2 = worldX + fx1; y2 = worldY + fy1
            x3 = worldX + fx0; y3 = worldY + fy1
        } else {
            val rad = rotationDeg * Mathf.DEG_TO_RAD
            val c = cos(rad)
            val s = sin(rad)
            x0 = worldX + (fx0 * c - fy0 * s); y0 = worldY + (fx0 * s + fy0 * c)
            x1 = worldX + (fx1 * c - fy0 * s); y1 = worldY + (fx1 * s + fy0 * c)
            x2 = worldX + (fx1 * c - fy1 * s); y2 = worldY + (fx1 * s + fy1 * c)
            x3 = worldX + (fx0 * c - fy1 * s); y3 = worldY + (fx0 * s + fy1 * c)
        }

        emitRawQuad(
            texture = texture,
            x0 = x0, y0 = y0, u0 = u0, v0 = vBottom,
            x1 = x1, y1 = y1, u1 = u1, v1 = vBottom,
            x2 = x2, y2 = y2, u2 = u1, v2 = vTop,
            x3 = x3, y3 = y3, u3 = u0, v3 = vTop,
            r = color.r, g = color.g, b = color.b, a = color.a
        )
    }

    fun emitRawQuad(
        texture: Texture2D,
        x0: Float, y0: Float, u0: Float, v0: Float,
        x1: Float, y1: Float, u1: Float, v1: Float,
        x2: Float, y2: Float, u2: Float, v2: Float,
        x3: Float, y3: Float, u3: Float, v3: Float,
        r: Float, g: Float, b: Float, a: Float
    ) {
        if (currentTexture !== texture || spriteCountInBatch >= maxSprites) {
            flush()
            currentTexture = texture
        }

        var idx = spriteCountInBatch * FLOATS_PER_SPRITE
        val v = vertexData

        v[idx++] = x0; v[idx++] = y0; v[idx++] = u0; v[idx++] = v0
        v[idx++] = r; v[idx++] = g; v[idx++] = b; v[idx++] = a

        v[idx++] = x1; v[idx++] = y1; v[idx++] = u1; v[idx++] = v1
        v[idx++] = r; v[idx++] = g; v[idx++] = b; v[idx++] = a

        v[idx++] = x2; v[idx++] = y2; v[idx++] = u2; v[idx++] = v2
        v[idx++] = r; v[idx++] = g; v[idx++] = b; v[idx++] = a

        v[idx++] = x3; v[idx++] = y3; v[idx++] = u3; v[idx++] = v3
        v[idx++] = r; v[idx++] = g; v[idx++] = b; v[idx++] = a

        spriteCountInBatch++
        spritesSubmittedThisFrame++
    }

    /**
     * Draws batched bitmap/atlas text in screen space (+Y UP or +Y UP world units).
     */
    fun drawText(
        font: FontAtlas = FontAtlas.defaultPixelFont,
        text: String,
        x: Float,
        y: Float,
        scale: Float = 2f,
        color: Color = Color.WHITE,
        align: TextAlign = TextAlign.LEFT
    ) {
        if (text.isEmpty()) return
        val lines = text.split('\n')
        var cursorY = y
        for (line in lines) {
            val lineW = font.measureTextWidth(line, scale)
            var cursorX = when (align) {
                TextAlign.LEFT -> x
                TextAlign.CENTER -> x - lineW * 0.5f
                TextAlign.RIGHT -> x - lineW
            }
            for (ch in line) {
                val g = font.getGlyph(ch) ?: continue
                val gw = g.widthPx * scale
                val gh = g.heightPx * scale
                val gx = cursorX + g.xOffsetPx * scale
                val gy = cursorY - gh - g.yOffsetPx * scale
                drawTextureRegion(
                    texture = font.texture,
                    worldX = gx,
                    worldY = gy,
                    width = gw,
                    height = gh,
                    pivotX = 0f,
                    pivotY = 0f,
                    rotationDeg = 0f,
                    u0 = g.u0,
                    vBottom = g.v1,
                    u1 = g.u1,
                    vTop = g.v0,
                    color = color
                )
                cursorX += g.xAdvancePx * scale
            }
            cursorY -= font.lineHeightPx * scale
        }
    }

    fun flush() {
        val tex = currentTexture
        if (spriteCountInBatch == 0 || tex == null) {
            spriteCountInBatch = 0
            return
        }
        backend.submitSpriteBatch(
            vertices = vertexData,
            floatCount = spriteCountInBatch * FLOATS_PER_SPRITE,
            indices = indexData,
            indexCount = spriteCountInBatch * INDICES_PER_SPRITE,
            texture = tex,
            blendMode = currentBlendMode,
            projectionView = currentProjView,
            isLit = currentLit,
            ambientColor = ambientLightColor,
            lights = if (currentLit) activeLights else emptyList()
        )
        drawCallsThisFrame++
        spriteCountInBatch = 0
    }

    fun end() {
        check(isDrawing) { "SpriteBatch.end() called without matching begin()" }
        flush()
        isDrawing = false
        currentTexture = null
    }
}
