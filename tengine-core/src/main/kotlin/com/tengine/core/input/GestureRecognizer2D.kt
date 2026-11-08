package com.tengine.core.input

import com.tengine.core.math.Vector2

/**
 * Multi-touch Gesture Recognizer detecting Tap, Double-Tap, Two-Finger Pan, and Pinch-to-Zoom!
 */
class GestureRecognizer2D {
    var onTap: ((screenX: Float, screenY: Float) -> Unit)? = null
    var onDoubleTap: ((screenX: Float, screenY: Float) -> Unit)? = null
    var onPinchZoom: ((scaleFactor: Float, focusCenter: Vector2) -> Unit)? = null
    var onTwoFingerPan: ((deltaX: Float, deltaY: Float) -> Unit)? = null

    private var lastTapTimeSec: Float = -10f
    private val lastTapPos = Vector2()
    private var prevPinchDistance: Float = -1f
    private val prevTwoFingerCenter = Vector2()

    fun update(currentTimeSec: Float) {
        val touches = Input.touches
        if (touches.size == 1) {
            prevPinchDistance = -1f
            val t = touches[0]
            if (t.phase == TouchPhase.ENDED) {
                val movedDistSq = (t.x - t.startX) * (t.x - t.startX) + (t.y - t.startY) * (t.y - t.startY)
                if (movedDistSq <= 24f * 24f) {
                    val dt = currentTimeSec - lastTapTimeSec
                    if (dt <= 0.32f && lastTapPos.distanceTo(Vector2(t.x, t.y)) <= 48f) {
                        onDoubleTap?.invoke(t.x, t.y)
                        lastTapTimeSec = -10f
                    } else {
                        onTap?.invoke(t.x, t.y)
                        lastTapTimeSec = currentTimeSec
                        lastTapPos.set(t.x, t.y)
                    }
                }
            }
        } else if (touches.size >= 2) {
            val a = touches[0]
            val b = touches[1]
            val pA = Vector2(a.x, a.y)
            val pB = Vector2(b.x, b.y)
            val dist = pA.distanceTo(pB)
            val center = (pA + pB) * 0.5f

            if (prevPinchDistance > 1f && dist > 1f) {
                val scale = dist / prevPinchDistance
                if (kotlin.math.abs(scale - 1f) > 0.002f) {
                    onPinchZoom?.invoke(scale, center)
                }
                val dx = center.x - prevTwoFingerCenter.x
                val dy = center.y - prevTwoFingerCenter.y
                if (dx * dx + dy * dy > 0.25f) {
                    onTwoFingerPan?.invoke(dx, dy)
                }
            }
            prevPinchDistance = dist
            prevTwoFingerCenter.set(center)
        } else {
            prevPinchDistance = -1f
        }
    }
}
