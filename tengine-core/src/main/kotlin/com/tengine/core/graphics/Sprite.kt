package com.tengine.core.graphics

import com.tengine.core.math.Vector2

/**
 * Sub-region of a [Texture2D] with UV coordinates, pivot, and Pixels-Per-Unit world sizing.
 */
class Sprite(
    val name: String,
    val texture: Texture2D,
    val regionX: Int = 0,
    val regionY: Int = 0,
    val regionWidth: Int = texture.width,
    val regionHeight: Int = texture.height,
    val pivot: Vector2 = Vector2(0.5f, 0.5f),
    val pixelsPerUnit: Float = 100f
) {
    val u0: Float = if (texture.width > 0) regionX.toFloat() / texture.width else 0f
    val v0: Float = if (texture.height > 0) regionY.toFloat() / texture.height else 0f
    val u1: Float = if (texture.width > 0) (regionX + regionWidth).toFloat() / texture.width else 1f
    val v1: Float = if (texture.height > 0) (regionY + regionHeight).toFloat() / texture.height else 1f

    val worldWidth: Float get() = if (pixelsPerUnit > 0f) regionWidth / pixelsPerUnit else 1f
    val worldHeight: Float get() = if (pixelsPerUnit > 0f) regionHeight / pixelsPerUnit else 1f

    companion object {
        val WHITE_SQUARE: Sprite by lazy {
            Sprite("WhiteSquare", Texture2D.whitePixel, pixelsPerUnit = 2f)
        }
        val CIRCLE: Sprite by lazy {
            Sprite("Circle", Texture2D.circleTexture, pixelsPerUnit = 64f)
        }
        val SOFT_GLOW: Sprite by lazy {
            Sprite("SoftGlow", Texture2D.softGlowTexture, pixelsPerUnit = 64f)
        }
        val DIAMOND: Sprite by lazy {
            Sprite("Diamond", Texture2D.diamondTexture, pixelsPerUnit = 48f)
        }
        val STAR: Sprite by lazy {
            Sprite("Star", Texture2D.starTexture, pixelsPerUnit = 64f)
        }
        val CAPSULE: Sprite by lazy {
            Sprite("Capsule", Texture2D.capsuleTexture, pixelsPerUnit = 48f)
        }
        val CHECKER: Sprite by lazy {
            Sprite("Checker", Texture2D.checkerTexture, pixelsPerUnit = 32f)
        }

        fun byPresetName(preset: String): Sprite = when (preset.uppercase()) {
            "CIRCLE" -> CIRCLE
            "GLOW", "SOFT_GLOW" -> SOFT_GLOW
            "DIAMOND" -> DIAMOND
            "STAR" -> STAR
            "CAPSULE" -> CAPSULE
            "CHECKER" -> CHECKER
            else -> WHITE_SQUARE
        }
    }
}

/**
 * Collection of named [Sprite]s backed by one or more texture sheets (Sprite Sheet / Texture Atlas).
 */
class SpriteAtlas(val name: String) {
    private val sprites = LinkedHashMap<String, Sprite>()

    fun addSprite(sprite: Sprite) {
        sprites[sprite.name] = sprite
    }

    fun getSprite(spriteName: String): Sprite? = sprites[spriteName]

    fun allSprites(): List<Sprite> = sprites.values.toList()

    companion object {
        /**
         * Slices a uniform grid sprite sheet into indexed sprites: "${prefix}_0", "${prefix}_1", ...
         */
        fun fromGrid(
            atlasName: String,
            texture: Texture2D,
            cellWidth: Int,
            cellHeight: Int,
            pixelsPerUnit: Float = cellWidth.toFloat(),
            prefix: String = "frame",
            margin: Int = 0,
            spacing: Int = 0
        ): SpriteAtlas {
            val atlas = SpriteAtlas(atlasName)
            var index = 0
            var y = margin
            while (y + cellHeight <= texture.height - margin) {
                var x = margin
                while (x + cellWidth <= texture.width - margin) {
                    atlas.addSprite(
                        Sprite(
                            name = "${prefix}_$index",
                            texture = texture,
                            regionX = x,
                            regionY = y,
                            regionWidth = cellWidth,
                            regionHeight = cellHeight,
                            pixelsPerUnit = pixelsPerUnit
                        )
                    )
                    index++
                    x += cellWidth + spacing
                }
                y += cellHeight + spacing
            }
            return atlas
        }
    }
}
