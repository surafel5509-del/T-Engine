package com.tengine.core.graphics

import com.tengine.core.ecs.Component
import com.tengine.core.ecs.GameObject
import com.tengine.core.ecs.InspectorProperty
import com.tengine.core.math.AABB
import com.tengine.core.math.Color
import com.tengine.core.math.Mathf
import com.tengine.core.math.Matrix4
import com.tengine.core.math.Vector2
import com.tengine.core.serialization.JsonObject
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * Orthographic 2D Camera component supporting zoom, aspect-adaptive viewport sizing,
 * screen<->world coordinate projection, smooth target following, deadzones, and
 * trauma-based Perlin noise screen shake!
 */
class Camera2D(
    orthographicSize: Float = 5.4f
) : Component("Camera2D") {

    override val typeName: String get() = "Camera2D"

    /** Half-height of the camera view in world units when zoom == 1. */
    var orthographicSize: Float = max(0.25f, orthographicSize)
        set(value) {
            field = max(0.25f, value)
        }

    var zoom: Float = 1f
        set(value) {
            field = value.coerceIn(0.05f, 50f)
        }

    var backgroundColor: Color = Color(0.07f, 0.08f, 0.13f, 1f)
    var isMainCamera: Boolean = true
    var cullingMask: Int = -1

    // Smooth target follow
    var followTarget: GameObject? = null
    var followTargetUuid: String? = null
    var followSmoothSpeed: Float = 7.5f
    var followOffset: Vector2 = Vector2(0f, 0.8f)
    var clampBounds: AABB? = null

    // Trauma-based Perlin screen shake
    private var trauma: Float = 0f
    private var shakeSeedTime: Float = 0f
    var maxShakeOffset: Float = 0.45f
    var maxShakeRollDeg: Float = 3.5f
    var traumaDecayPerSec: Float = 1.6f

    private val shakeOffset = Vector2()
    private var shakeRollDeg: Float = 0f

    private val projMatrix = Matrix4()
    private val viewMatrix = Matrix4()
    private val vpMatrix = Matrix4()
    private val invVpMatrix = Matrix4()

    val effectiveHalfHeight: Float
        get() = orthographicSize / zoom.coerceAtLeast(0.05f)

    fun addTrauma(amount: Float) {
        trauma = Mathf.clamp01(trauma + amount)
    }

    override fun onLateUpdate(dt: Float) {
        // Target tracking
        val target = resolveFollowTarget()
        if (target != null && target.activeInHierarchy) {
            val desired = target.transform.worldPosition + followOffset
            val current = transform.worldPosition
            val t = Mathf.clamp01(followSmoothSpeed * dt)
            var nextPos = Vector2.lerp(current, desired, t)

            val bounds = clampBounds
            if (bounds != null) {
                nextPos.x = Mathf.clamp(nextPos.x, bounds.minX, bounds.maxX)
                nextPos.y = Mathf.clamp(nextPos.y, bounds.minY, bounds.maxY)
            }
            transform.worldPosition = nextPos
        }

        // Trauma screen shake (shake = trauma^2 for juicy non-linear falloff)
        if (trauma > 0f) {
            shakeSeedTime += dt * 28f
            val shake = trauma * trauma
            shakeOffset.x = maxShakeOffset * shake * Mathf.noise2D(shakeSeedTime, 11.3f)
            shakeOffset.y = maxShakeOffset * shake * Mathf.noise2D(37.7f, shakeSeedTime)
            shakeRollDeg = maxShakeRollDeg * shake * Mathf.noise2D(shakeSeedTime, 93.1f)
            trauma = max(0f, trauma - traumaDecayPerSec * dt)
        } else {
            shakeOffset.setZero()
            shakeRollDeg = 0f
        }
    }

    private fun resolveFollowTarget(): GameObject? {
        if (followTarget != null && !followTarget!!.isDestroyed) return followTarget
        val uuid = followTargetUuid ?: return null
        followTarget = scene?.findByUuid(uuid)
        return followTarget
    }

    fun computeProjectionViewMatrix(viewportWidth: Int, viewportHeight: Int): Matrix4 {
        val aspect = if (viewportHeight > 0) viewportWidth.toFloat() / viewportHeight.toFloat() else 16f / 9f
        val halfH = effectiveHalfHeight
        val halfW = halfH * aspect

        projMatrix.setOrtho(-halfW, halfW, -halfH, halfH, -100f, 100f)

        val camPos = if (isAttached) transform.worldPosition else Vector2.ZERO
        val camRotDeg = (if (isAttached) transform.worldRotationDeg else 0f) + shakeRollDeg
        val cx = camPos.x + shakeOffset.x
        val cy = camPos.y + shakeOffset.y

        // View matrix is inverse of camera world transform: R(-theta) * T(-pos)
        val rad = -camRotDeg * Mathf.DEG_TO_RAD
        val c = cos(rad)
        val s = sin(rad)
        viewMatrix.setIdentity()
        viewMatrix.values[0] = c
        viewMatrix.values[1] = s
        viewMatrix.values[4] = -s
        viewMatrix.values[5] = c
        viewMatrix.values[12] = -(cx * c - cy * s)
        viewMatrix.values[13] = -(cx * s + cy * c)

        return Matrix4.multiply(projMatrix, viewMatrix, vpMatrix)
    }

    fun getVisibleWorldBounds(viewportWidth: Int, viewportHeight: Int): AABB {
        val aspect = if (viewportHeight > 0) viewportWidth.toFloat() / viewportHeight.toFloat() else 16f / 9f
        val halfH = effectiveHalfHeight * 1.15f // Slight margin for rotated objects
        val halfW = halfH * aspect
        val pos = if (isAttached) transform.worldPosition else Vector2.ZERO
        return AABB(pos.x - halfW, pos.y - halfH, pos.x + halfW, pos.y + halfH)
    }

    /**
     * Converts Android touch coordinates (origin top-left: 0..viewportW, 0..viewportH) to 2D World Space.
     */
    fun screenToWorld(
        screenX: Float,
        screenY: Float,
        viewportWidth: Int,
        viewportHeight: Int
    ): Vector2 {
        if (viewportWidth <= 0 || viewportHeight <= 0) return Vector2.ZERO
        val vp = computeProjectionViewMatrix(viewportWidth, viewportHeight)
        if (!vp.invert(invVpMatrix)) return Vector2.ZERO

        val ndcX = (screenX / viewportWidth.toFloat()) * 2f - 1f
        val ndcY = 1f - (screenY / viewportHeight.toFloat()) * 2f
        return invVpMatrix.transformPoint2D(Vector2(ndcX, ndcY))
    }

    /**
     * Converts 2D World Space point to screen pixels (origin top-left).
     */
    fun worldToScreen(
        worldPoint: Vector2,
        viewportWidth: Int,
        viewportHeight: Int
    ): Vector2 {
        val vp = computeProjectionViewMatrix(viewportWidth, viewportHeight)
        val ndc = vp.transformPoint2D(worldPoint)
        val sx = (ndc.x + 1f) * 0.5f * viewportWidth
        val sy = (1f - ndc.y) * 0.5f * viewportHeight
        return Vector2(sx, sy)
    }

    override fun onDrawGizmos(gizmos: ShapeRenderer, selected: Boolean) {
        val halfH = effectiveHalfHeight
        val halfW = halfH * (16f / 9f)
        val pos = transform.worldPosition
        val color = if (selected) Color(1f, 0.92f, 0.35f, 0.9f) else Color(0.85f, 0.85f, 0.9f, 0.45f)
        gizmos.drawRectWire(pos.x - halfW, pos.y - halfH, halfW * 2f, halfH * 2f, color, thickness = 0.04f)
    }

    override fun serialize(): JsonObject = super.serialize()
        .put("orthographicSize", orthographicSize)
        .put("zoom", zoom)
        .putColor("backgroundColor", backgroundColor)
        .put("isMainCamera", isMainCamera)
        .put("followTargetUuid", followTarget?.uuid ?: followTargetUuid)
        .put("followSmoothSpeed", followSmoothSpeed)
        .putVector2("followOffset", followOffset)

    override fun deserialize(data: JsonObject) {
        super.deserialize(data)
        orthographicSize = data.getFloat("orthographicSize", 5.4f)
        zoom = data.getFloat("zoom", 1f)
        backgroundColor = data.getColor("backgroundColor", Color(0.07f, 0.08f, 0.13f, 1f))
        isMainCamera = data.getBoolean("isMainCamera", true)
        followTargetUuid = data.getStringOrNull("followTargetUuid")
        followSmoothSpeed = data.getFloat("followSmoothSpeed", 7.5f)
        followOffset = data.getVector2("followOffset", Vector2(0f, 0.8f))
    }

    override fun inspectableProperties(): List<InspectorProperty> = listOf(
        InspectorProperty.FloatProp("Ortho Size", 0.5f, 50f, 0.25f, { orthographicSize }, { orthographicSize = it }),
        InspectorProperty.FloatProp("Zoom", 0.1f, 10f, 0.1f, { zoom }, { zoom = it }),
        InspectorProperty.ColorProp("Background", { backgroundColor }, { backgroundColor = it }),
        InspectorProperty.BoolProp("Main Camera", { isMainCamera }, { isMainCamera = it }),
        InspectorProperty.FloatProp("Follow Speed", 0f, 30f, 0.5f, { followSmoothSpeed }, { followSmoothSpeed = it })
    )
}
