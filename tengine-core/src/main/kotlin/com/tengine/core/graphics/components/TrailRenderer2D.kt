package com.tengine.core.graphics.components

import com.tengine.core.ecs.Component
import com.tengine.core.ecs.InspectorProperty
import com.tengine.core.graphics.BlendMode
import com.tengine.core.graphics.SortingLayer
import com.tengine.core.graphics.SpriteBatch
import com.tengine.core.graphics.Texture2D
import com.tengine.core.math.Color
import com.tengine.core.math.Mathf
import com.tengine.core.math.Vector2
import com.tengine.core.serialization.JsonObject

/**
 * Smooth ribbon trail behind fast-moving characters, dashes, and projectiles.
 */
class TrailRenderer2D(
    var durationSeconds: Float = 0.28f,
    var startWidth: Float = 0.35f,
    var endWidth: Float = 0.02f,
    var startColor: Color = Color(0.2f, 0.9f, 1f, 0.85f),
    var endColor: Color = Color(0.2f, 0.4f, 1f, 0.0f)
) : Component("TrailRenderer2D") {

    override val typeName: String get() = "TrailRenderer2D"

    var minVertexDistance: Float = 0.08f
    var sortingLayer: SortingLayer = SortingLayer.EFFECTS
    var orderInLayer: Int = 5
    var blendMode: BlendMode = BlendMode.ADDITIVE
    var emitting: Boolean = true

    private class Sample(val x: Float, val y: Float, var age: Float = 0f)
    private val samples = ArrayDeque<Sample>(64)

    override fun onUpdate(dt: Float) {
        val iter = samples.iterator()
        while (iter.hasNext()) {
            val s = iter.next()
            s.age += dt
            if (s.age >= durationSeconds) {
                iter.remove()
            }
        }

        if (emitting && isAttached) {
            val wp = transform.worldPosition
            val last = samples.lastOrNull()
            if (last == null) {
                samples.addLast(Sample(wp.x, wp.y))
            } else {
                val dx = wp.x - last.x
                val dy = wp.y - last.y
                if (dx * dx + dy * dy >= minVertexDistance * minVertexDistance) {
                    samples.addLast(Sample(wp.x, wp.y))
                }
            }
        }
    }

    fun clearTrail() {
        samples.clear()
    }

    fun render(batch: SpriteBatch) {
        if (samples.size < 2) return
        batch.setState(blendMode, isLit = false)
        val pts = samples.toList()
        val tex = Texture2D.whitePixel

        for (i in 0 until pts.size - 1) {
            val a = pts[i]
            val b = pts[i + 1]
            val tA = 1f - Mathf.clamp01(a.age / durationSeconds)
            val tB = 1f - Mathf.clamp01(b.age / durationSeconds)

            val halfWA = Mathf.lerp(endWidth, startWidth, tA) * 0.5f
            val halfWB = Mathf.lerp(endWidth, startWidth, tB) * 0.5f
            val col = Color.lerp(endColor, startColor, (tA + tB) * 0.5f)

            val seg = Vector2(b.x - a.x, b.y - a.y)
            val perp = seg.normalized.perpLeft

            batch.emitRawQuad(
                texture = tex,
                x0 = a.x - perp.x * halfWA, y0 = a.y - perp.y * halfWA, u0 = 0f, v0 = 0f,
                x1 = b.x - perp.x * halfWB, y1 = b.y - perp.y * halfWB, u1 = 1f, v1 = 0f,
                x2 = b.x + perp.x * halfWB, y2 = b.y + perp.y * halfWB, u2 = 1f, v2 = 1f,
                x3 = a.x + perp.x * halfWA, y3 = a.y + perp.y * halfWA, u3 = 0f, v3 = 1f,
                r = col.r, g = col.g, b = col.b, a = col.a
            )
        }
    }

    override fun serialize(): JsonObject = super.serialize()
        .put("durationSeconds", durationSeconds)
        .put("startWidth", startWidth)
        .put("endWidth", endWidth)
        .putColor("startColor", startColor)
        .putColor("endColor", endColor)

    override fun deserialize(data: JsonObject) {
        super.deserialize(data)
        durationSeconds = data.getFloat("durationSeconds", 0.28f)
        startWidth = data.getFloat("startWidth", 0.35f)
        endWidth = data.getFloat("endWidth", 0.02f)
        startColor = data.getColor("startColor", startColor)
        endColor = data.getColor("endColor", endColor)
    }

    override fun inspectableProperties(): List<InspectorProperty> = listOf(
        InspectorProperty.BoolProp("Emitting", { emitting }, { emitting = it }),
        InspectorProperty.FloatProp("Duration", 0.05f, 5f, 0.05f, { durationSeconds }, { durationSeconds = it }),
        InspectorProperty.FloatProp("Start Width", 0.01f, 5f, 0.05f, { startWidth }, { startWidth = it }),
        InspectorProperty.ColorProp("Start Color", { startColor }, { startColor = it })
    )
}
