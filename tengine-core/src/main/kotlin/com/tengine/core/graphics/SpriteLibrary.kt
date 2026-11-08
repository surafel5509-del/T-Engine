package com.tengine.core.graphics

/**
 * Global runtime catalog of named [Sprite] and [SpriteAtlas] assets so scenes and prefabs
 * serialized to JSON resolve their visual sprites seamlessly upon reload.
 */
object SpriteLibrary {
    private val sprites = LinkedHashMap<String, Sprite>()
    private val atlases = LinkedHashMap<String, SpriteAtlas>()

    init {
        registerDefaults()
    }

    fun registerDefaults() {
        registerSprite(Sprite.WHITE_SQUARE)
        registerSprite(Sprite.CIRCLE)
        registerSprite(Sprite.SOFT_GLOW)
        registerSprite(Sprite.DIAMOND)
        registerSprite(Sprite.STAR)
        registerSprite(Sprite.CAPSULE)
        registerSprite(Sprite.CHECKER)
    }

    fun registerSprite(sprite: Sprite) {
        sprites[sprite.name] = sprite
    }

    fun registerAtlas(atlas: SpriteAtlas) {
        atlases[atlas.name] = atlas
        for (sprite in atlas.allSprites()) {
            sprites[sprite.name] = sprite
        }
    }

    fun resolve(nameOrPreset: String): Sprite {
        sprites[nameOrPreset]?.let { return it }
        return Sprite.byPresetName(nameOrPreset)
    }

    fun allSpriteNames(): List<String> = sprites.keys.toList()
}
