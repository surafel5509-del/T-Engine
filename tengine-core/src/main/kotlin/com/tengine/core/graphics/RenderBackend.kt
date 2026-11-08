package com.tengine.core.graphics

import com.tengine.core.math.Color
import com.tengine.core.math.Matrix4

data class LightUniformData(
    val x: Float,
    val y: Float,
    val radius: Float,
    val color: Color,
    val intensity: Float
)

/**
 * Hardware Abstraction Layer (HAL) decoupling T-Engine's 2D Renderer from OpenGL ES,
 * Vulkan, or Headless JVM Unit Test execution.
 */
interface RenderBackend {
    val surfaceWidth: Int
    val surfaceHeight: Int

    fun setViewport(x: Int, y: Int, width: Int, height: Int)
    fun clear(color: Color)
    fun ensureTextureUploaded(texture: Texture2D): Int
    fun releaseTexture(texture: Texture2D)

    fun submitSpriteBatch(
        vertices: FloatArray,
        floatCount: Int,
        indices: ShortArray,
        indexCount: Int,
        texture: Texture2D,
        blendMode: BlendMode,
        projectionView: Matrix4,
        isLit: Boolean = false,
        ambientColor: Color = Color.WHITE,
        lights: List<LightUniformData> = emptyList()
    )

    fun submitPrimitiveBatch(
        vertices: FloatArray,
        floatCount: Int,
        primitiveMode: PrimitiveMode,
        blendMode: BlendMode,
        projectionView: Matrix4
    )
}

/**
 * Headless / Unit-Test RenderBackend tracking submitted draw calls and geometry counts.
 */
class NullRenderBackend(
    override var surfaceWidth: Int = 1920,
    override var surfaceHeight: Int = 1080
) : RenderBackend {

    private var nextFakeTexHandle = 1
    var totalBatchesSubmitted: Int = 0
        private set

    override fun setViewport(x: Int, y: Int, width: Int, height: Int) {
        surfaceWidth = width
        surfaceHeight = height
    }

    override fun clear(color: Color) {}

    override fun ensureTextureUploaded(texture: Texture2D): Int {
        if (texture.gpuHandle == 0) {
            texture.gpuHandle = nextFakeTexHandle++
        }
        texture.isDirty = false
        return texture.gpuHandle
    }

    override fun releaseTexture(texture: Texture2D) {
        texture.gpuHandle = 0
    }

    override fun submitSpriteBatch(
        vertices: FloatArray,
        floatCount: Int,
        indices: ShortArray,
        indexCount: Int,
        texture: Texture2D,
        blendMode: BlendMode,
        projectionView: Matrix4,
        isLit: Boolean,
        ambientColor: Color,
        lights: List<LightUniformData>
    ) {
        ensureTextureUploaded(texture)
        totalBatchesSubmitted++
    }

    override fun submitPrimitiveBatch(
        vertices: FloatArray,
        floatCount: Int,
        primitiveMode: PrimitiveMode,
        blendMode: BlendMode,
        projectionView: Matrix4
    ) {
        totalBatchesSubmitted++
    }
}
