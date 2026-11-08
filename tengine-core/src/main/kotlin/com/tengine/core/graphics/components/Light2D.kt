package com.tengine.core.graphics.components

import com.tengine.core.Time
import com.tengine.core.ecs.Component
import com.tengine.core.ecs.InspectorProperty
import com.tengine.core.graphics.BlendMode
import com.tengine.core.graphics.ShapeRenderer
import com.tengine.core.graphics.Sprite
import com.tengine.core.graphics.SpriteBatch
import com.tengine.core.math.Color
import com.tengine.core.math.Mathf
import com.tengine.core.serialization.JsonObject
import kotlin.math.max

enum class LightType2D {
    POINT,
    GLOBAL_AMBIENT
}

/**
 * Dynamic 2D Lighting component (Point Light with smooth shader attenuation + optional Volumetric Glow Halo,
 * or Global Ambient 2D Light).
 */
class Light2D(
    var lightType: LightType2D = LightType2D.POINT,
    var color: Color = Color(1f, 0.88f, 0.65f, 1f),
    var intensity: Float = 1.2f,
    var radius: Float = 5.5f
) : Component("Light2D") {

    override val typeName: String get() = "Light2D"

    var renderAdditiveGlowHalo: Boolean = true
    var haloAlpha: Float = 0.24f
    var flickerAmplitude: Float = 0.0f
    var flickerFrequency: Float = 8.0f

    val effectiveIntensity: Float
        get() {
            if (flickerAmplitude <= 0f) return intensity
            val n = Mathf.noise2D(Time.time * flickerFrequency, instanceId.toFloat() * 0.37f)
            return max(0f, intensity * (1f + n * flickerAmplitude))
        }

    fun renderHalo(batch: SpriteBatch) {
        if (lightType != LightType2D.POINT || !renderAdditiveGlowHalo || haloAlpha <= 0.001f) return
        val wp = transform.worldPosition
        val eff = effectiveIntensity
        batch.setState(BlendMode.ADDITIVE, isLit = false)
        batch.drawSprite(
            sprite = Sprite.SOFT_GLOW,
            worldX = wp.x,
            worldY = wp.y,
            width = radius * 2f,
            height = radius * 2f,
            color = color.withAlpha(Mathf.clamp01(haloAlpha * eff))
        )
    }

    override fun onDrawGizmos(gizmos: ShapeRenderer, selected: Boolean) {
        if (lightType == LightType2D.POINT) {
            val wp = transform.worldPosition
            val alpha = if (selected) 0.85f else 0.35f
            gizmos.drawCircleWire(wp.x, wp.y, radius, color.withAlpha(alpha), segments = 32)
            gizmos.drawCircleFilled(wp.x, wp.y, 0.12f, color.withAlpha(0.95f), segments = 12)
        }
    }

    override fun serialize(): JsonObject = super.serialize()
        .put("lightType", lightType.name)
        .putColor("color", color)
        .put("intensity", intensity)
        .put("radius", radius)
        .put("renderHalo", renderAdditiveGlowHalo)
        .put("haloAlpha", haloAlpha)
        .put("flickerAmplitude", flickerAmplitude)

    override fun deserialize(data: JsonObject) {
        super.deserialize(data)
        lightType = runCatching { LightType2D.valueOf(data.getString("lightType", "POINT")) }
            .getOrDefault(LightType2D.POINT)
        color = data.getColor("color", color)
        intensity = data.getFloat("intensity", 1.2f)
        radius = data.getFloat("radius", 5.5f)
        renderAdditiveGlowHalo = data.getBoolean("renderHalo", true)
        haloAlpha = data.getFloat("haloAlpha", 0.24f)
        flickerAmplitude = data.getFloat("flickerAmplitude", 0f)
    }

    override fun inspectableProperties(): List<InspectorProperty> = listOf(
        InspectorProperty.EnumProp(
            label = "Light Type",
            options = LightType2D.entries.map { it.name },
            getter = { lightType.name },
            setter = { lightType = LightType2D.valueOf(it) }
        ),
        InspectorProperty.ColorProp("Color", { color }, { color = it }),
        InspectorProperty.FloatProp("Intensity", 0f, 10f, 0.1f, { intensity }, { intensity = it }),
        InspectorProperty.FloatProp("Radius", 0.5f, 50f, 0.25f, { radius }, { radius = max(0.2f, it) }),
        InspectorProperty.BoolProp("Glow Halo", { renderAdditiveGlowHalo }, { renderAdditiveGlowHalo = it }),
        InspectorProperty.FloatProp("Flicker", 0f, 1f, 0.05f, { flickerAmplitude }, { flickerAmplitude = it })
    )
}
