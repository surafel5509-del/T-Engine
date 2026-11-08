package com.tengine.core.tween

import com.tengine.core.ecs.Transform2D
import com.tengine.core.graphics.components.SpriteRenderer
import com.tengine.core.math.Color
import com.tengine.core.math.Easing
import com.tengine.core.math.Mathf
import com.tengine.core.math.Vector2

/**
 * Fluent DOTween-inspired Animation & Tweening Engine built from scratch in Kotlin.
 * Supports single property tweens, ping-pong/loop repeats, delays, sequences, and completion hooks.
 */
interface TweenJob {
    val isFinished: Boolean
    fun update(dt: Float)
    fun cancel()
}

class ValueTween(
    private val duration: Float,
    private val easing: Easing = Easing.OUT_QUAD,
    private var delay: Float = 0f,
    private var repeatCount: Int = 1, // -1 = infinite
    private val yoyo: Boolean = false,
    val targetTag: Any? = null,
    private val onApply: (Float) -> Unit
) : TweenJob {

    private var elapsed: Float = 0f
    private var forward: Boolean = true
    private var cancelled: Boolean = false
    private var onCompleteCallback: (() -> Unit)? = null

    override val isFinished: Boolean
        get() = cancelled || repeatCount == 0

    fun onComplete(callback: () -> Unit): ValueTween {
        onCompleteCallback = callback
        return this
    }

    override fun cancel() {
        cancelled = true
    }

    override fun update(dt: Float) {
        if (isFinished) return
        if (delay > 0f) {
            delay -= dt
            if (delay > 0f) return
        }

        if (duration <= Mathf.EPSILON) {
            onApply(1f)
            repeatCount = 0
            onCompleteCallback?.invoke()
            return
        }

        elapsed += dt
        while (elapsed >= duration && !isFinished) {
            elapsed -= duration
            val endT = if (forward) 1f else 0f
            onApply(easing.evaluate(endT))

            if (repeatCount > 0) {
                repeatCount--
                if (repeatCount == 0) {
                    onCompleteCallback?.invoke()
                    return
                }
            }
            if (yoyo) forward = !forward
        }

        if (!isFinished) {
            val rawProgress = Mathf.clamp01(elapsed / duration)
            val directional = if (forward) rawProgress else 1f - rawProgress
            onApply(easing.evaluate(directional))
        }
    }
}

class TweenSequence(private val steps: List<TweenJob>) : TweenJob {
    private var index = 0
    private var cancelled = false

    override val isFinished: Boolean
        get() = cancelled || index >= steps.size

    override fun cancel() {
        cancelled = true
        steps.forEach { it.cancel() }
    }

    override fun update(dt: Float) {
        if (isFinished) return
        val current = steps[index]
        current.update(dt)
        if (current.isFinished) {
            index++
        }
    }
}

class TweenManager {
    private val activeTweens = ArrayList<TweenJob>(64)
    private val pendingTweens = ArrayList<TweenJob>(16)

    val count: Int get() = activeTweens.size + pendingTweens.size

    fun <T : TweenJob> add(tween: T): T {
        pendingTweens.add(tween)
        return tween
    }

    fun cancelForTarget(targetTag: Any) {
        for (t in activeTweens) {
            if (t is ValueTween && t.targetTag === targetTag) t.cancel()
        }
        for (t in pendingTweens) {
            if (t is ValueTween && t.targetTag === targetTag) t.cancel()
        }
    }

    fun update(dt: Float) {
        if (pendingTweens.isNotEmpty()) {
            activeTweens.addAll(pendingTweens)
            pendingTweens.clear()
        }
        val iter = activeTweens.iterator()
        while (iter.hasNext()) {
            val job = iter.next()
            job.update(dt)
            if (job.isFinished) {
                iter.remove()
            }
        }
    }

    fun clear() {
        activeTweens.clear()
        pendingTweens.clear()
    }

    // Ergonomic builders
    fun tweenPosition(
        transform: Transform2D,
        toLocalPos: Vector2,
        duration: Float,
        easing: Easing = Easing.OUT_CUBIC
    ): ValueTween {
        val start = transform.localPosition
        return add(ValueTween(duration, easing, targetTag = transform) { t ->
            if (!transform.isDestroyed) {
                transform.localPosition = Vector2.lerpUnclamped(start, toLocalPos, t)
            }
        })
    }

    fun tweenScale(
        transform: Transform2D,
        toScale: Vector2,
        duration: Float,
        easing: Easing = Easing.OUT_BACK,
        yoyo: Boolean = false,
        repeatCount: Int = 1
    ): ValueTween {
        val start = transform.localScale
        return add(ValueTween(duration, easing, repeatCount = repeatCount, yoyo = yoyo, targetTag = transform) { t ->
            if (!transform.isDestroyed) {
                transform.localScale = Vector2.lerpUnclamped(start, toScale, t)
            }
        })
    }

    fun tweenColor(
        renderer: SpriteRenderer,
        toColor: Color,
        duration: Float,
        easing: Easing = Easing.IN_OUT_SINE
    ): ValueTween {
        val start = renderer.color
        return add(ValueTween(duration, easing, targetTag = renderer) { t ->
            if (!renderer.isDestroyed) {
                renderer.color = Color.lerp(start, toColor, t)
            }
        })
    }
}
