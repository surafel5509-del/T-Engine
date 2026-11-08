package com.tengine.core

import com.tengine.core.math.Color
import com.tengine.core.math.Vector2

/**
 * Master configuration for initializing a T-Engine instance.
 */
data class EngineConfig(
    val applicationName: String = "T-Engine 2D Game",
    val designWidth: Int = 1920,
    val designHeight: Int = 1080,
    val pixelsPerUnit: Float = 100f,
    val fixedTimeStep: Float = 1f / 60f,
    val maxPhysicsSubSteps: Int = 6,
    val velocityIterations: Int = 8,
    val positionIterations: Int = 3,
    val defaultGravity: Vector2 = Vector2(0f, -18f),
    val spatialHashCellSize: Float = 2.5f,
    val clearColor: Color = Color(0.07f, 0.08f, 0.12f, 1f),
    val maxBatchSprites: Int = 4096,
    val enablePostProcessing: Boolean = false
)
