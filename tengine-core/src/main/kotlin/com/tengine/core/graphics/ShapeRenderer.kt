package com.tengine.core.graphics

import com.tengine.core.math.AABB
import com.tengine.core.math.Color
import com.tengine.core.math.Mathf
import com.tengine.core.math.Matrix4
import com.tengine.core.math.Vector2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Batched 2D Vector Shape & Editor Gizmo Renderer.
 * Renders anti-aliased/thick world-space lines, wireframes, circles, filled rects, arrows,
 * infinite 2D coordinate grids, and interactive Move/Rotate/Scale transform gizmos!
 */
class ShapeRenderer(
    var backend: RenderBackend,
    private val maxVertices: Int = 16384
) {
    companion object {
        const val FLOATS_PER_VERTEX = 6 // [x, y, r, g, b, a]
    }

    private val vertices = FloatArray(maxVertices * FLOATS_PER_VERTEX)
    private var vertexCount: Int = 0
    private var isDrawing: Boolean = false
    private val projView = Matrix4()
    private var currentMode: PrimitiveMode = PrimitiveMode.TRIANGLES
    private var currentBlend: BlendMode = BlendMode.ALPHA

    var drawCallsThisFrame: Int = 0
        private set

    fun resetFrameStats() {
        drawCallsThisFrame = 0
    }

    fun begin(
        projectionView: Matrix4,
        mode: PrimitiveMode = PrimitiveMode.TRIANGLES,
        blendMode: BlendMode = BlendMode.ALPHA
    ) {
        check(!isDrawing) { "ShapeRenderer.begin() called while already active" }
        isDrawing = true
        projView.setFrom(projectionView)
        currentMode = mode
        currentBlend = blendMode
        vertexCount = 0
    }

    private fun ensureSpace(neededVertices: Int, mode: PrimitiveMode) {
        if (currentMode != mode || vertexCount + neededVertices > maxVertices) {
            flush()
            currentMode = mode
        }
    }

    private fun putVertex(x: Float, y: Float, c: Color) {
        var idx = vertexCount * FLOATS_PER_VERTEX
        vertices[idx++] = x
        vertices[idx++] = y
        vertices[idx++] = c.r
        vertices[idx++] = c.g
        vertices[idx++] = c.b
        vertices[idx] = c.a
        vertexCount++
    }

    fun drawTriangle(
        x0: Float, y0: Float,
        x1: Float, y1: Float,
        x2: Float, y2: Float,
        color: Color
    ) {
        ensureSpace(3, PrimitiveMode.TRIANGLES)
        putVertex(x0, y0, color)
        putVertex(x1, y1, color)
        putVertex(x2, y2, color)
    }

    /**
     * Draws a crisp world-space thick line as a 2-triangle quad (consistent across all OpenGL ES GPUs,
     * even drivers where glLineWidth > 1 is unsupported!).
     */
    fun drawLine(
        x0: Float,
        y0: Float,
        x1: Float,
        y1: Float,
        color: Color,
        thickness: Float = 0.03f
    ) {
        val dx = x1 - x0
        val dy = y1 - y0
        val len = sqrt(dx * dx + dy * dy)
        if (len <= 1e-6f) return
        val nx = -dy / len * (thickness * 0.5f)
        val ny = dx / len * (thickness * 0.5f)

        ensureSpace(6, PrimitiveMode.TRIANGLES)
        putVertex(x0 + nx, y0 + ny, color)
        putVertex(x1 + nx, y1 + ny, color)
        putVertex(x1 - nx, y1 - ny, color)

        putVertex(x1 - nx, y1 - ny, color)
        putVertex(x0 - nx, y0 - ny, color)
        putVertex(x0 + nx, y0 + ny, color)
    }

    fun drawRectFilled(x: Float, y: Float, width: Float, height: Float, color: Color) {
        ensureSpace(6, PrimitiveMode.TRIANGLES)
        val x1 = x + width
        val y1 = y + height
        putVertex(x, y, color)
        putVertex(x1, y, color)
        putVertex(x1, y1, color)

        putVertex(x1, y1, color)
        putVertex(x, y1, color)
        putVertex(x, y, color)
    }

    fun drawRectWire(
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        color: Color,
        thickness: Float = 0.03f
    ) {
        val x1 = x + width
        val y1 = y + height
        drawLine(x, y, x1, y, color, thickness)
        drawLine(x1, y, x1, y1, color, thickness)
        drawLine(x1, y1, x, y1, color, thickness)
        drawLine(x, y1, x, y, color, thickness)
    }

    fun drawPolygonWire(points: List<Vector2>, color: Color, thickness: Float = 0.03f) {
        if (points.size < 2) return
        for (i in points.indices) {
            val a = points[i]
            val b = points[(i + 1) % points.size]
            drawLine(a.x, a.y, b.x, b.y, color, thickness)
        }
    }

    fun drawCircleWire(
        cx: Float,
        cy: Float,
        radius: Float,
        color: Color,
        segments: Int = 28,
        thickness: Float = 0.03f
    ) {
        val segs = segments.coerceAtLeast(8)
        val step = Mathf.TWO_PI / segs
        var prevX = cx + radius
        var prevY = cy
        for (i in 1..segs) {
            val a = i * step
            val nx = cx + cos(a) * radius
            val ny = cy + sin(a) * radius
            drawLine(prevX, prevY, nx, ny, color, thickness)
            prevX = nx
            prevY = ny
        }
    }

    fun drawCircleFilled(
        cx: Float,
        cy: Float,
        radius: Float,
        color: Color,
        segments: Int = 24
    ) {
        val segs = segments.coerceAtLeast(8)
        val step = Mathf.TWO_PI / segs
        var prevX = cx + radius
        var prevY = cy
        for (i in 1..segs) {
            val a = i * step
            val nx = cx + cos(a) * radius
            val ny = cy + sin(a) * radius
            drawTriangle(cx, cy, prevX, prevY, nx, ny, color)
            prevX = nx
            prevY = ny
        }
    }

    fun drawArrow(
        x0: Float,
        y0: Float,
        x1: Float,
        y1: Float,
        color: Color,
        thickness: Float = 0.045f,
        headSize: Float = 0.22f
    ) {
        drawLine(x0, y0, x1, y1, color, thickness)
        val dx = x1 - x0
        val dy = y1 - y0
        val len = sqrt(dx * dx + dy * dy)
        if (len <= 1e-5f) return
        val ux = dx / len
        val uy = dy / len
        val px = -uy
        val py = ux
        val bx = x1 - ux * headSize
        val by = y1 - uy * headSize
        val hw = headSize * 0.55f
        drawTriangle(
            x1, y1,
            bx + px * hw, by + py * hw,
            bx - px * hw, by - py * hw,
            color
        )
    }

    /**
     * Draws a Unity-style infinite world-space 2D reference grid with major/minor lines and X/Y origin axes.
     */
    fun drawEditorGrid(visibleBounds: AABB, cellSize: Float = 1f, pixelWorldSize: Float = 0.02f) {
        val step = if (visibleBounds.width > 40f) cellSize * 5f else cellSize
        val startX = floor(visibleBounds.minX / step) * step
        val endX = visibleBounds.maxX
        val startY = floor(visibleBounds.minY / step) * step
        val endY = visibleBounds.maxY

        val minorColor = Color(1f, 1f, 1f, 0.07f)
        val majorColor = Color(1f, 1f, 1f, 0.16f)
        val thin = pixelWorldSize.coerceAtLeast(0.008f)

        var x = startX
        var safety = 0
        while (x <= endX && safety++ < 250) {
            val idx = kotlin.math.round(x / step).toInt()
            if (idx != 0) {
                val c = if (idx % 5 == 0) majorColor else minorColor
                drawLine(x, visibleBounds.minY, x, visibleBounds.maxY, c, thin)
            }
            x += step
        }

        var y = startY
        safety = 0
        while (y <= endY && safety++ < 250) {
            val idx = kotlin.math.round(y / step).toInt()
            if (idx != 0) {
                val c = if (idx % 5 == 0) majorColor else minorColor
                drawLine(visibleBounds.minX, y, visibleBounds.maxX, y, c, thin)
            }
            y += step
        }

        // World Origin X (Red) and Y (Green) axes
        if (0f in visibleBounds.minY..visibleBounds.maxY) {
            drawLine(visibleBounds.minX, 0f, visibleBounds.maxX, 0f, Color(0.92f, 0.28f, 0.32f, 0.55f), thin * 1.8f)
        }
        if (0f in visibleBounds.minX..visibleBounds.maxX) {
            drawLine(0f, visibleBounds.minY, 0f, visibleBounds.maxY, Color(0.28f, 0.88f, 0.42f, 0.55f), thin * 1.8f)
        }
    }

    fun flush() {
        if (vertexCount == 0) return
        backend.submitPrimitiveBatch(
            vertices = vertices,
            floatCount = vertexCount * FLOATS_PER_VERTEX,
            primitiveMode = currentMode,
            blendMode = currentBlend,
            projectionView = projView
        )
        drawCallsThisFrame++
        vertexCount = 0
    }

    fun end() {
        check(isDrawing) { "ShapeRenderer.end() called without begin()" }
        flush()
        isDrawing = false
    }
}
