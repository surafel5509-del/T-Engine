package com.tengine.editor.gizmo

import com.tengine.core.ecs.GameObject
import com.tengine.core.graphics.Camera2D
import com.tengine.core.graphics.ShapeRenderer
import com.tengine.core.graphics.components.SpriteRenderer
import com.tengine.core.math.Color
import com.tengine.core.math.Mathf
import com.tengine.core.math.Vector2
import com.tengine.core.physics.Collider2D
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.round

enum class EditorToolMode {
    HAND,
    MOVE,
    ROTATE,
    SCALE,
    TILE_BRUSH
}

enum class GizmoHandlePart {
    NONE,
    MOVE_X,
    MOVE_Y,
    MOVE_FREE,
    ROTATE_RING,
    SCALE_X,
    SCALE_Y,
    SCALE_UNIFORM
}

/**
 * Renders and hit-tests Unity-style 2D Scene View Transform Gizmos (Move, Rotate, Scale)
 * with zoom-independent handle sizing and optional grid/angle snapping!
 */
class TransformGizmo2D {

    var activeHandle: GizmoHandlePart = GizmoHandlePart.NONE
        private set

    private val dragStartWorld = Vector2()
    private val initialObjWorldPos = Vector2()
    private var initialObjRotDeg: Float = 0f
    private val initialObjScale = Vector2(1f, 1f)
    private var dragStartAngleDeg: Float = 0f

    private fun gizmoScale(camera: Camera2D): Float = camera.effectiveHalfHeight * 0.24f

    fun render(shapes: ShapeRenderer, selected: GameObject?, camera: Camera2D) {
        val go = selected ?: return
        if (go.isDestroyed) return

        val wp = go.transform.worldPosition
        val s = gizmoScale(camera)
        val thick = (s * 0.045f).coerceAtLeast(0.015f)

        // Selection bounding outline
        val sr = go.getComponent(SpriteRenderer::class)
        val col = go.getComponent(Collider2D::class)
        val bounds = sr?.getWorldBounds() ?: col?.worldAABB
        if (bounds != null) {
            shapes.drawRectWire(
                bounds.minX - thick,
                bounds.minY - thick,
                bounds.width + thick * 2f,
                bounds.height + thick * 2f,
                Color(1f, 0.68f, 0.18f, 0.85f),
                thickness = thick * 0.75f
            )
        }
    }

    fun renderToolHandles(
        shapes: ShapeRenderer,
        selected: GameObject?,
        camera: Camera2D,
        toolMode: EditorToolMode
    ) {
        val go = selected ?: return
        if (go.isDestroyed) return

        val wp = go.transform.worldPosition
        val s = gizmoScale(camera)
        val thick = (s * 0.055f).coerceAtLeast(0.018f)

        val xCol = if (activeHandle == GizmoHandlePart.MOVE_X || activeHandle == GizmoHandlePart.SCALE_X)
            Color.YELLOW else Color(0.96f, 0.28f, 0.32f, 0.96f)
        val yCol = if (activeHandle == GizmoHandlePart.MOVE_Y || activeHandle == GizmoHandlePart.SCALE_Y)
            Color.YELLOW else Color(0.28f, 0.92f, 0.45f, 0.96f)
        val centerCol = if (activeHandle == GizmoHandlePart.MOVE_FREE || activeHandle == GizmoHandlePart.SCALE_UNIFORM)
            Color.YELLOW else Color(0.25f, 0.78f, 1.0f, 0.88f)

        when (toolMode) {
            EditorToolMode.MOVE -> {
                val freeBox = s * 0.22f
                shapes.drawRectFilled(wp.x, wp.y, freeBox, freeBox, centerCol.withAlpha(0.38f))
                shapes.drawRectWire(wp.x, wp.y, freeBox, freeBox, centerCol, thick * 0.7f)
                shapes.drawArrow(wp.x, wp.y, wp.x + s, wp.y, xCol, thick, s * 0.22f)
                shapes.drawArrow(wp.x, wp.y, wp.x, wp.y + s, yCol, thick, s * 0.22f)
            }
            EditorToolMode.ROTATE -> {
                val ringCol = if (activeHandle == GizmoHandlePart.ROTATE_RING)
                    Color.YELLOW else Color(0.22f, 0.88f, 0.98f, 0.95f)
                shapes.drawCircleWire(wp.x, wp.y, s * 0.95f, ringCol, segments = 40, thickness = thick)
                val right = go.transform.right
                shapes.drawLine(wp.x, wp.y, wp.x + right.x * s * 0.95f, wp.y + right.y * s * 0.95f, ringCol, thick * 0.8f)
            }
            EditorToolMode.SCALE -> {
                val tip = s * 0.14f
                shapes.drawLine(wp.x, wp.y, wp.x + s, wp.y, xCol, thick)
                shapes.drawRectFilled(wp.x + s - tip * 0.5f, wp.y - tip * 0.5f, tip, tip, xCol)

                shapes.drawLine(wp.x, wp.y, wp.x, wp.y + s, yCol, thick)
                shapes.drawRectFilled(wp.x - tip * 0.5f, wp.y + s - tip * 0.5f, tip, tip, yCol)

                shapes.drawRectFilled(wp.x - tip * 0.6f, wp.y - tip * 0.6f, tip * 1.2f, tip * 1.2f, centerCol)
            }
            EditorToolMode.HAND, EditorToolMode.TILE_BRUSH -> {}
        }
    }

    fun tryBeginDrag(
        worldTouch: Vector2,
        selected: GameObject?,
        camera: Camera2D,
        toolMode: EditorToolMode
    ): Boolean {
        val go = selected ?: return false
        val wp = go.transform.worldPosition
        val s = gizmoScale(camera)
        val hitTol = s * 0.24f

        activeHandle = when (toolMode) {
            EditorToolMode.MOVE -> {
                val freeBox = s * 0.28f
                when {
                    worldTouch.x in (wp.x - hitTol * 0.4f)..(wp.x + freeBox) &&
                        worldTouch.y in (wp.y - hitTol * 0.4f)..(wp.y + freeBox) -> GizmoHandlePart.MOVE_FREE
                    worldTouch.x in wp.x..(wp.x + s + hitTol) && abs(worldTouch.y - wp.y) <= hitTol -> GizmoHandlePart.MOVE_X
                    worldTouch.y in wp.y..(wp.y + s + hitTol) && abs(worldTouch.x - wp.x) <= hitTol -> GizmoHandlePart.MOVE_Y
                    else -> GizmoHandlePart.NONE
                }
            }
            EditorToolMode.ROTATE -> {
                val dist = worldTouch.distanceTo(wp)
                if (abs(dist - s * 0.95f) <= hitTol * 1.3f) GizmoHandlePart.ROTATE_RING else GizmoHandlePart.NONE
            }
            EditorToolMode.SCALE -> {
                when {
                    worldTouch.distanceTo(wp) <= hitTol -> GizmoHandlePart.SCALE_UNIFORM
                    worldTouch.x in wp.x..(wp.x + s + hitTol) && abs(worldTouch.y - wp.y) <= hitTol -> GizmoHandlePart.SCALE_X
                    worldTouch.y in wp.y..(wp.y + s + hitTol) && abs(worldTouch.x - wp.x) <= hitTol -> GizmoHandlePart.SCALE_Y
                    else -> GizmoHandlePart.NONE
                }
            }
            else -> GizmoHandlePart.NONE
        }

        if (activeHandle != GizmoHandlePart.NONE) {
            dragStartWorld.set(worldTouch)
            initialObjWorldPos.set(wp)
            initialObjRotDeg = go.transform.localRotationDeg
            initialObjScale.set(go.transform.localScale)
            dragStartAngleDeg = atan2(worldTouch.y - wp.y, worldTouch.x - wp.x) * Mathf.RAD_TO_DEG
            return true
        }
        return false
    }

    fun updateDrag(
        worldTouch: Vector2,
        selected: GameObject?,
        camera: Camera2D,
        snapEnabled: Boolean,
        gridStep: Float = 0.5f
    ) {
        val go = selected ?: return
        if (activeHandle == GizmoHandlePart.NONE) return

        val delta = worldTouch - dragStartWorld
        val s = max(0.2f, gizmoScale(camera))

        when (activeHandle) {
            GizmoHandlePart.MOVE_X -> {
                var nx = initialObjWorldPos.x + delta.x
                if (snapEnabled) nx = round(nx / gridStep) * gridStep
                go.transform.worldPosition = Vector2(nx, initialObjWorldPos.y)
            }
            GizmoHandlePart.MOVE_Y -> {
                var ny = initialObjWorldPos.y + delta.y
                if (snapEnabled) ny = round(ny / gridStep) * gridStep
                go.transform.worldPosition = Vector2(initialObjWorldPos.x, ny)
            }
            GizmoHandlePart.MOVE_FREE -> {
                var nx = initialObjWorldPos.x + delta.x
                var ny = initialObjWorldPos.y + delta.y
                if (snapEnabled) {
                    nx = round(nx / gridStep) * gridStep
                    ny = round(ny / gridStep) * gridStep
                }
                go.transform.worldPosition = Vector2(nx, ny)
            }
            GizmoHandlePart.ROTATE_RING -> {
                val wp = go.transform.worldPosition
                val curAngle = atan2(worldTouch.y - wp.y, worldTouch.x - wp.x) * Mathf.RAD_TO_DEG
                var nextRot = initialObjRotDeg + (curAngle - dragStartAngleDeg)
                if (snapEnabled) nextRot = round(nextRot / 15f) * 15f
                go.transform.localRotationDeg = nextRot
            }
            GizmoHandlePart.SCALE_X -> {
                val factor = max(0.05f, 1f + delta.x / s)
                go.transform.localScale = Vector2(initialObjScale.x * factor, initialObjScale.y)
            }
            GizmoHandlePart.SCALE_Y -> {
                val factor = max(0.05f, 1f + delta.y / s)
                go.transform.localScale = Vector2(initialObjScale.x, initialObjScale.y * factor)
            }
            GizmoHandlePart.SCALE_UNIFORM -> {
                val factor = max(0.05f, 1f + (delta.x + delta.y) / s)
                go.transform.localScale = Vector2(initialObjScale.x * factor, initialObjScale.y * factor)
            }
            GizmoHandlePart.NONE -> {}
        }
    }

    fun endDrag() {
        activeHandle = GizmoHandlePart.NONE
    }
}
