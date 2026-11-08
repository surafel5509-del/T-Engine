package com.tengine.core

import com.tengine.core.math.Mathf

/**
 * Global engine frame timing state modeled after Unity's Time API.
 */
object Time {
    /** Scaled frame delta time in seconds. */
    var deltaTime: Float = 0f
        internal set

    /** Unscaled real-world frame delta time in seconds. */
    var unscaledDeltaTime: Float = 0f
        internal set

    /** Fixed physics simulation step in seconds (default 1/60s). */
    var fixedDeltaTime: Float = 1f / 60f

    /** Time multiplier (0 = paused, 0.25 = slow-motion, 1 = normal). */
    var timeScale: Float = 1f
        set(value) {
            field = Mathf.clamp(value, 0f, 20f)
        }

    /** Total scaled elapsed game time in seconds since Engine start. */
    var time: Float = 0f
        internal set

    /** Total unscaled elapsed real time in seconds since Engine start. */
    var unscaledTime: Float = 0f
        internal set

    /** Total rendered frames since Engine start. */
    var frameCount: Long = 0L
        internal set

    /** Smoothed frames per second. */
    var fps: Float = 60f
        internal set

    /** Interpolation factor [0..1] remaining in physics accumulator for smooth rendering. */
    var alpha: Float = 0f
        internal set

    private var fpsAccumulator: Float = 0f
    private var fpsFrames: Int = 0

    internal fun reset(fixedStep: Float = 1f / 60f) {
        deltaTime = 0f
        unscaledDeltaTime = 0f
        fixedDeltaTime = fixedStep
        timeScale = 1f
        time = 0f
        unscaledTime = 0f
        frameCount = 0L
        fps = 60f
        alpha = 0f
        fpsAccumulator = 0f
        fpsFrames = 0
    }

    internal fun stepFrame(rawDeltaSeconds: Float, maxDeltaSeconds: Float = 0.25f) {
        val clamped = Mathf.clamp(rawDeltaSeconds, 0f, maxDeltaSeconds)
        unscaledDeltaTime = clamped
        deltaTime = clamped * timeScale
        unscaledTime += unscaledDeltaTime
        time += deltaTime
        frameCount++

        fpsAccumulator += clamped
        fpsFrames++
        if (fpsAccumulator >= 0.25f) {
            fps = fpsFrames / fpsAccumulator
            fpsAccumulator = 0f
            fpsFrames = 0
        }
    }
}
