package com.tengine.core.graphics.components

import com.tengine.core.ecs.Component
import com.tengine.core.ecs.InspectorProperty
import com.tengine.core.math.Vector2
import com.tengine.core.serialization.JsonObject

/**
 * Multi-depth 2D Parallax Scrolling component tracking the Scene's Main Camera.
 * factor = 1.0 moves locked with camera (distant sky/moon); factor = 0.0 stays fixed in world space.
 */
class ParallaxLayer(
    var parallaxFactorX: Float = 0.5f,
    var parallaxFactorY: Float = 0.25f
) : Component("ParallaxLayer") {

    override val typeName: String get() = "ParallaxLayer"

    private var lastCameraPos: Vector2? = null

    override fun onLateUpdate(dt: Float) {
        val cam = scene?.findMainCamera() ?: return
        val camPos = cam.transform.worldPosition
        val prev = lastCameraPos
        if (prev != null) {
            val dx = (camPos.x - prev.x) * parallaxFactorX
            val dy = (camPos.y - prev.y) * parallaxFactorY
            transform.translate(dx, dy, worldSpace = true)
        }
        lastCameraPos = camPos
    }

    override fun serialize(): JsonObject = super.serialize()
        .put("parallaxFactorX", parallaxFactorX)
        .put("parallaxFactorY", parallaxFactorY)

    override fun deserialize(data: JsonObject) {
        super.deserialize(data)
        parallaxFactorX = data.getFloat("parallaxFactorX", 0.5f)
        parallaxFactorY = data.getFloat("parallaxFactorY", 0.25f)
    }

    override fun inspectableProperties(): List<InspectorProperty> = listOf(
        InspectorProperty.FloatProp("Parallax X", -2f, 2f, 0.05f, { parallaxFactorX }, { parallaxFactorX = it }),
        InspectorProperty.FloatProp("Parallax Y", -2f, 2f, 0.05f, { parallaxFactorY }, { parallaxFactorY = it })
    )
}
