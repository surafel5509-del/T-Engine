package com.tengine.core.graphics.components

import com.tengine.core.ecs.Component
import com.tengine.core.ecs.InspectorProperty
import com.tengine.core.graphics.BlendMode
import com.tengine.core.graphics.SortingLayer
import com.tengine.core.graphics.Sprite
import com.tengine.core.graphics.SpriteBatch
import com.tengine.core.graphics.SpriteLibrary
import com.tengine.core.math.AABB
import com.tengine.core.math.Color
import com.tengine.core.math.Vector2
import com.tengine.core.serialization.JsonObject
import kotlin.math.abs
import kotlin.math.max

/**
 * Primary 2D visual renderer component (modeled after Unity's SpriteRenderer).
 * Supports custom Sprites, procedural shapes, tint Color, X/Y flipping, SortingLayer,
 * orderInLayer, Y-sorting, Additive/Alpha blending, and dynamic 2D lighting!
 */
class SpriteRenderer(
    sprite: Sprite = Sprite.WHITE_SQUARE,
    var color: Color = Color.WHITE
) : Component("SpriteRenderer") {

    override val typeName: String get() = "SpriteRenderer"

    var sprite: Sprite = sprite
        set(value) {
            field = value
            SpriteLibrary.registerSprite(value)
        }

    /** Optional explicit draw size override; if null, uses sprite.worldWidth x sprite.worldHeight. */
    var customSize: Vector2 = Vector2(1f, 1f)
    var useCustomSize: Boolean = true

    var flipX: Boolean = false
    var flipY: Boolean = false

    var sortingLayer: SortingLayer = SortingLayer.DEFAULT
    var orderInLayer: Int = 0
    var ySort: Boolean = false

    var blendMode: BlendMode = BlendMode.ALPHA
    var isLit: Boolean = false

    fun getWorldBounds(): AABB {
        val wp = transform.worldPosition
        val ws = transform.worldScale
        val w = (if (useCustomSize) customSize.x else sprite.worldWidth) * abs(ws.x)
        val h = (if (useCustomSize) customSize.y else sprite.worldHeight) * abs(ws.y)
        val radius = max(w, h) * 0.72f
        return AABB(wp.x - radius, wp.y - radius, wp.x + radius, wp.y + radius)
    }

    fun render(batch: SpriteBatch) {
        val wp = transform.worldPosition
        val ws = transform.worldScale
        val rot = transform.worldRotationDeg
        val drawW = if (useCustomSize) customSize.x else sprite.worldWidth
        val drawH = if (useCustomSize) customSize.y else sprite.worldHeight

        batch.setState(blendMode, isLit)
        batch.drawSprite(
            sprite = sprite,
            worldX = wp.x,
            worldY = wp.y,
            width = drawW,
            height = drawH,
            rotationDeg = rot,
            scaleX = ws.x,
            scaleY = ws.y,
            color = color,
            flipX = flipX,
            flipY = flipY
        )
    }

    override fun serialize(): JsonObject = super.serialize()
        .put("spriteName", sprite.name)
        .putColor("color", color)
        .put("useCustomSize", useCustomSize)
        .putVector2("customSize", customSize)
        .put("flipX", flipX)
        .put("flipY", flipY)
        .put("sortingLayer", sortingLayer.name)
        .put("orderInLayer", orderInLayer)
        .put("ySort", ySort)
        .put("blendMode", blendMode.name)
        .put("isLit", isLit)

    override fun deserialize(data: JsonObject) {
        super.deserialize(data)
        sprite = SpriteLibrary.resolve(data.getString("spriteName", "WhiteSquare"))
        color = data.getColor("color", Color.WHITE)
        useCustomSize = data.getBoolean("useCustomSize", true)
        customSize = data.getVector2("customSize", Vector2(1f, 1f))
        flipX = data.getBoolean("flipX", false)
        flipY = data.getBoolean("flipY", false)
        sortingLayer = SortingLayer.fromName(data.getString("sortingLayer", "DEFAULT"))
        orderInLayer = data.getInt("orderInLayer", 0)
        ySort = data.getBoolean("ySort", false)
        blendMode = runCatching { BlendMode.valueOf(data.getString("blendMode", "ALPHA")) }
            .getOrDefault(BlendMode.ALPHA)
        isLit = data.getBoolean("isLit", false)
    }

    override fun inspectableProperties(): List<InspectorProperty> = listOf(
        InspectorProperty.EnumProp(
            label = "Sprite",
            options = SpriteLibrary.allSpriteNames().ifEmpty { listOf("WhiteSquare", "Circle", "Diamond", "Star", "Capsule") },
            getter = { sprite.name },
            setter = { sprite = SpriteLibrary.resolve(it) }
        ),
        InspectorProperty.ColorProp("Tint Color", { color }, { color = it }),
        InspectorProperty.Vector2Prop("Draw Size", 0.1f, { customSize.copyOf() }, { customSize = it; useCustomSize = true }),
        InspectorProperty.BoolProp("Flip X", { flipX }, { flipX = it }),
        InspectorProperty.BoolProp("Flip Y", { flipY }, { flipY = it }),
        InspectorProperty.EnumProp(
            label = "Sorting Layer",
            options = SortingLayer.entries.map { it.name },
            getter = { sortingLayer.name },
            setter = { sortingLayer = SortingLayer.fromName(it) }
        ),
        InspectorProperty.IntProp("Order in Layer", -1000, 1000, { orderInLayer }, { orderInLayer = it }),
        InspectorProperty.BoolProp("2D Lit", { isLit }, { isLit = it })
    )
}
