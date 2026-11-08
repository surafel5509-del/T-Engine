package com.tengine.core.ui

import com.tengine.core.graphics.BlendMode
import com.tengine.core.graphics.FontAtlas
import com.tengine.core.graphics.PrimitiveMode
import com.tengine.core.graphics.ShapeRenderer
import com.tengine.core.graphics.SpriteBatch
import com.tengine.core.graphics.TextAlign
import com.tengine.core.input.Input
import com.tengine.core.input.TouchPhase
import com.tengine.core.input.TouchPointer
import com.tengine.core.math.Color
import com.tengine.core.math.Mathf
import com.tengine.core.math.Matrix4
import com.tengine.core.math.Rect
import com.tengine.core.math.Vector2
import kotlin.math.min
import kotlin.math.sqrt

enum class UIAnchor {
    TOP_LEFT, TOP_CENTER, TOP_RIGHT,
    CENTER_LEFT, CENTER, CENTER_RIGHT,
    BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT
}

/**
 * Base UI Widget rendered inside a [UICanvas] using resolution-independent anchored coordinates.
 * Screen coordinate convention inside UICanvas: (0, 0) = bottom-left, (width, height) = top-right.
 */
abstract class UIWidget(
    var id: String,
    var anchor: UIAnchor = UIAnchor.TOP_LEFT,
    var anchoredX: Float = 0f,
    var anchoredY: Float = 0f,
    var width: Float = 160f,
    var height: Float = 56f,
    var pivotX: Float = 0.5f,
    var pivotY: Float = 0.5f
) {
    var visible: Boolean = true

    fun computeScreenRect(viewportW: Int, viewportH: Int, scale: Float): Rect {
        val anchorPosX = when (anchor) {
            UIAnchor.TOP_LEFT, UIAnchor.CENTER_LEFT, UIAnchor.BOTTOM_LEFT -> 0f
            UIAnchor.TOP_CENTER, UIAnchor.CENTER, UIAnchor.BOTTOM_CENTER -> viewportW * 0.5f
            UIAnchor.TOP_RIGHT, UIAnchor.CENTER_RIGHT, UIAnchor.BOTTOM_RIGHT -> viewportW.toFloat()
        }
        val anchorPosY = when (anchor) {
            UIAnchor.BOTTOM_LEFT, UIAnchor.BOTTOM_CENTER, UIAnchor.BOTTOM_RIGHT -> 0f
            UIAnchor.CENTER_LEFT, UIAnchor.CENTER, UIAnchor.CENTER_RIGHT -> viewportH * 0.5f
            UIAnchor.TOP_LEFT, UIAnchor.TOP_CENTER, UIAnchor.TOP_RIGHT -> viewportH.toFloat()
        }

        val sw = width * scale
        val sh = height * scale
        val cx = anchorPosX + anchoredX * scale
        val cy = anchorPosY + anchoredY * scale
        return Rect(cx - sw * pivotX, cy - sh * pivotY, sw, sh)
    }

    open fun updateInput(touches: List<TouchPointer>, viewportW: Int, viewportH: Int, scale: Float) {}

    abstract fun render(
        batch: SpriteBatch,
        shapes: ShapeRenderer,
        rect: Rect,
        scale: Float,
        font: FontAtlas
    )
}

class UIPanel(
    id: String,
    anchor: UIAnchor,
    anchoredX: Float,
    anchoredY: Float,
    width: Float,
    height: Float,
    var backgroundColor: Color = Color(0.06f, 0.08f, 0.14f, 0.78f),
    var borderColor: Color = Color(0.25f, 0.65f, 0.95f, 0.6f)
) : UIWidget(id, anchor, anchoredX, anchoredY, width, height) {

    override fun render(batch: SpriteBatch, shapes: ShapeRenderer, rect: Rect, scale: Float, font: FontAtlas) {
        shapes.drawRectFilled(rect.x, rect.y, rect.width, rect.height, backgroundColor)
        if (borderColor.a > 0.01f) {
            shapes.drawRectWire(rect.x, rect.y, rect.width, rect.height, borderColor, thickness = 2f * scale)
        }
    }
}

class UILabel(
    id: String,
    var text: String,
    anchor: UIAnchor = UIAnchor.TOP_LEFT,
    anchoredX: Float = 24f,
    anchoredY: Float = -24f,
    var fontScale: Float = 3.0f,
    var color: Color = Color.WHITE,
    var align: TextAlign = TextAlign.LEFT
) : UIWidget(id, anchor, anchoredX, anchoredY, width = 200f, height = 36f, pivotX = 0f, pivotY = 1f) {

    override fun render(batch: SpriteBatch, shapes: ShapeRenderer, rect: Rect, scale: Float, font: FontAtlas) {
        val effScale = fontScale * scale
        val tx = when (align) {
            TextAlign.LEFT -> rect.x
            TextAlign.CENTER -> rect.centerX
            TextAlign.RIGHT -> rect.maxX
        }
        // Subtle drop shadow for high contrast over any gameplay scene
        batch.drawText(font, text, tx + 2f * scale, rect.maxY - 2f * scale, effScale, Color(0f, 0f, 0f, color.a * 0.75f), align)
        batch.drawText(font, text, tx, rect.maxY, effScale, color, align)
    }
}

class UIProgressBar(
    id: String,
    anchor: UIAnchor,
    anchoredX: Float,
    anchoredY: Float,
    width: Float = 320f,
    height: Float = 26f,
    var progress: Float = 1f,
    var fillColor: Color = Color(0.22f, 0.92f, 0.52f, 0.95f),
    var backColor: Color = Color(0.08f, 0.10f, 0.16f, 0.85f)
) : UIWidget(id, anchor, anchoredX, anchoredY, width, height) {

    override fun render(batch: SpriteBatch, shapes: ShapeRenderer, rect: Rect, scale: Float, font: FontAtlas) {
        val clamped = Mathf.clamp01(progress)
        shapes.drawRectFilled(rect.x, rect.y, rect.width, rect.height, backColor)
        val pad = 3f * scale
        val fillW = (rect.width - pad * 2f) * clamped
        if (fillW > 0.5f) {
            shapes.drawRectFilled(rect.x + pad, rect.y + pad, fillW, rect.height - pad * 2f, fillColor)
        }
        shapes.drawRectWire(rect.x, rect.y, rect.width, rect.height, Color(1f, 1f, 1f, 0.35f), thickness = 1.8f * scale)
    }
}

/**
 * Multi-touch On-Screen Action Button (e.g., JUMP, DASH, FIRE) supporting simultaneous presses
 * with VirtualJoystick and automatic binding to T-Engine's [Input] action system!
 */
class UIButton(
    id: String,
    var label: String,
    anchor: UIAnchor,
    anchoredX: Float,
    anchoredY: Float,
    width: Float = 130f,
    height: Float = 130f,
    var boundActionName: String? = null,
    var normalColor: Color = Color(0.16f, 0.55f, 0.95f, 0.62f),
    var pressedColor: Color = Color(0.35f, 0.85f, 1.0f, 0.92f),
    var isCircular: Boolean = true,
    var onClick: (() -> Unit)? = null
) : UIWidget(id, anchor, anchoredX, anchoredY, width, height) {

    var isPressed: Boolean = false
        private set

    override fun updateInput(touches: List<TouchPointer>, viewportW: Int, viewportH: Int, scale: Float) {
        val rect = computeScreenRect(viewportW, viewportH, scale)
        var currentlyHeld = false
        var clickedThisFrame = false

        for (t in touches) {
            // Convert Android top-left touch Y to UI bottom-left Y
            val uiX = t.x
            val uiY = viewportH - t.y
            val inside = if (isCircular) {
                val r = min(rect.width, rect.height) * 0.58f
                val dx = uiX - rect.centerX
                val dy = uiY - rect.centerY
                dx * dx + dy * dy <= r * r
            } else {
                rect.contains(uiX, uiY)
            }

            if (inside) {
                if (t.phase == TouchPhase.ENDED) {
                    clickedThisFrame = true
                } else if (t.phase != TouchPhase.CANCELLED) {
                    currentlyHeld = true
                }
            }
        }

        isPressed = currentlyHeld
        boundActionName?.let { action ->
            Input.setVirtualActionState(action, currentlyHeld)
        }
        if (clickedThisFrame) {
            onClick?.invoke()
        }
    }

    override fun render(batch: SpriteBatch, shapes: ShapeRenderer, rect: Rect, scale: Float, font: FontAtlas) {
        val fill = if (isPressed) pressedColor else normalColor
        if (isCircular) {
            val radius = min(rect.width, rect.height) * 0.5f
            shapes.drawCircleFilled(rect.centerX, rect.centerY, radius, fill, segments = 28)
            shapes.drawCircleWire(rect.centerX, rect.centerY, radius, Color.WHITE.withAlpha(0.7f), segments = 28, thickness = 2.5f * scale)
        } else {
            shapes.drawRectFilled(rect.x, rect.y, rect.width, rect.height, fill)
            shapes.drawRectWire(rect.x, rect.y, rect.width, rect.height, Color.WHITE.withAlpha(0.65f), thickness = 2f * scale)
        }
        val textScale = 2.5f * scale
        val halfTextH = font.lineHeightPx * textScale * 0.4f
        batch.drawText(font, label, rect.centerX, rect.centerY + halfTextH, textScale, Color.WHITE, TextAlign.CENTER)
    }
}

/**
 * Multi-touch Analog Virtual Thumbstick feeding directly into `Input.getAxis("Horizontal"/"Vertical")`
 * (or secondary twin-stick aim vector)!
 */
class UIVirtualJoystick(
    id: String = "PrimaryJoystick",
    anchor: UIAnchor = UIAnchor.BOTTOM_LEFT,
    anchoredX: Float = 210f,
    anchoredY: Float = 195f,
    radiusPx: Float = 115f,
    val isSecondaryStick: Boolean = false,
    var deadZone: Float = 0.12f
) : UIWidget(id, anchor, anchoredX, anchoredY, radiusPx * 2f, radiusPx * 2f) {

    private var trackedPointerId: Int = -1
    val outputVector = Vector2()

    override fun updateInput(touches: List<TouchPointer>, viewportW: Int, viewportH: Int, scale: Float) {
        val rect = computeScreenRect(viewportW, viewportH, scale)
        val maxRadius = rect.width * 0.5f
        val grabRadius = maxRadius * 1.45f

        if (trackedPointerId == -1) {
            for (t in touches) {
                if (t.phase == TouchPhase.BEGAN) {
                    val uiX = t.x
                    val uiY = viewportH - t.y
                    val dx = uiX - rect.centerX
                    val dy = uiY - rect.centerY
                    if (dx * dx + dy * dy <= grabRadius * grabRadius) {
                        trackedPointerId = t.pointerId
                        break
                    }
                }
            }
        }

        if (trackedPointerId != -1) {
            val pointer = touches.firstOrNull { it.pointerId == trackedPointerId }
            if (pointer == null || pointer.phase == TouchPhase.ENDED || pointer.phase == TouchPhase.CANCELLED) {
                trackedPointerId = -1
                outputVector.setZero()
            } else {
                val uiX = pointer.x
                val uiY = viewportH - pointer.y
                val dx = uiX - rect.centerX
                val dy = uiY - rect.centerY
                val dist = sqrt(dx * dx + dy * dy)
                val normMag = if (maxRadius > 1f) Mathf.clamp01(dist / maxRadius) else 0f
                if (normMag < deadZone || dist < 0.001f) {
                    outputVector.setZero()
                } else {
                    val remapped = (normMag - deadZone) / (1f - deadZone)
                    outputVector.set(dx / dist * remapped, dy / dist * remapped)
                }
            }
        }

        Input.setVirtualJoystick(outputVector.x, outputVector.y, secondary = isSecondaryStick)
    }

    override fun render(batch: SpriteBatch, shapes: ShapeRenderer, rect: Rect, scale: Float, font: FontAtlas) {
        val r = rect.width * 0.5f
        val cx = rect.centerX
        val cy = rect.centerY
        shapes.drawCircleFilled(cx, cy, r, Color(0.08f, 0.12f, 0.20f, 0.48f), segments = 32)
        shapes.drawCircleWire(cx, cy, r, Color(0.45f, 0.82f, 1.0f, 0.58f), segments = 32, thickness = 2.5f * scale)

        val knobX = cx + outputVector.x * r * 0.68f
        val knobY = cy + outputVector.y * r * 0.68f
        val knobColor = if (trackedPointerId != -1) Color(0.35f, 0.90f, 1.0f, 0.92f) else Color(0.75f, 0.88f, 1.0f, 0.68f)
        shapes.drawCircleFilled(knobX, knobY, r * 0.38f, knobColor, segments = 24)
    }
}

/**
 * Resolution-Scaling In-Game 2D UI Canvas (modeled after Unity's Canvas + CanvasScaler).
 */
class UICanvas(
    val name: String = "HUDCanvas",
    val referenceWidth: Float = 1920f,
    val referenceHeight: Float = 1080f
) {
    var enabled: Boolean = true
    var font: FontAtlas = FontAtlas.defaultPixelFont
    private val widgets = ArrayList<UIWidget>()

    fun <T : UIWidget> addWidget(widget: T): T {
        widgets.add(widget)
        return widget
    }

    fun removeWidget(id: String) {
        widgets.removeAll { it.id == id }
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : UIWidget> findWidget(id: String): T? = widgets.firstOrNull { it.id == id } as? T

    fun clear() = widgets.clear()

    private fun computeScale(viewportW: Int, viewportH: Int): Float {
        if (viewportW <= 0 || viewportH <= 0) return 1f
        val sx = viewportW / referenceWidth
        val sy = viewportH / referenceHeight
        return min(sx, sy)
    }

    fun updateInput(viewportW: Int, viewportH: Int) {
        if (!enabled || viewportW <= 0 || viewportH <= 0) return
        val scale = computeScale(viewportW, viewportH)
        val touches = Input.touches
        for (i in 0 until widgets.size) {
            val w = widgets[i]
            if (w.visible) {
                w.updateInput(touches, viewportW, viewportH, scale)
            }
        }
    }

    fun render(
        batch: SpriteBatch,
        shapes: ShapeRenderer,
        screenOrtho: Matrix4,
        viewportW: Int,
        viewportH: Int
    ) {
        if (!enabled || widgets.isEmpty()) return
        val scale = computeScale(viewportW, viewportH)

        shapes.begin(screenOrtho, PrimitiveMode.TRIANGLES, BlendMode.ALPHA)
        batch.begin(screenOrtho, BlendMode.ALPHA, isLit = false)

        for (i in 0 until widgets.size) {
            val w = widgets[i]
            if (w.visible) {
                val rect = w.computeScreenRect(viewportW, viewportH, scale)
                w.render(batch, shapes, rect, scale, font)
            }
        }

        // Render shapes first, then crisp text labels on top!
        shapes.end()
        batch.end()
    }
}
