package com.tengine.android.graphics

import android.opengl.GLES20
import com.tengine.core.EngineLogger
import com.tengine.core.graphics.BlendMode
import com.tengine.core.graphics.LightUniformData
import com.tengine.core.graphics.PrimitiveMode
import com.tengine.core.graphics.RenderBackend
import com.tengine.core.graphics.ShaderLibrary
import com.tengine.core.graphics.SpriteBatch
import com.tengine.core.graphics.Texture2D
import com.tengine.core.graphics.TextureFilter
import com.tengine.core.graphics.TextureWrap
import com.tengine.core.math.Color
import com.tengine.core.math.Matrix4
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer

/**
 * Hardware-Accelerated OpenGL ES 2.0 / 3.0 RenderBackend for Android devices.
 * Features zero-allocation direct NIO streaming buffers, automatic EGL context loss recovery,
 * unlit & multi-light 2D sprite shaders, and vector primitive shaders!
 */
class GLESRenderBackend(
    maxSpritesPerBatch: Int = 4096,
    maxPrimitiveVertices: Int = 16384
) : RenderBackend {

    override var surfaceWidth: Int = 1
        private set
    override var surfaceHeight: Int = 1
        private set

    private var contextGeneration: Int = 0

    // Compiled GLSL program handles & cached uniform/attribute locations
    private var unlitProgram: Int = 0
    private var unlitProjLoc: Int = -1
    private var unlitTexLoc: Int = -1
    private var unlitPosAttr: Int = -1
    private var unlitUvAttr: Int = -1
    private var unlitColAttr: Int = -1

    private var litProgram: Int = 0
    private var litProjLoc: Int = -1
    private var litTexLoc: Int = -1
    private var litAmbientLoc: Int = -1
    private var litLightCountLoc: Int = -1
    private var litLightPosRadLoc: Int = -1
    private var litLightColIntLoc: Int = -1
    private var litPosAttr: Int = -1
    private var litUvAttr: Int = -1
    private var litColAttr: Int = -1

    private var primProgram: Int = 0
    private var primProjLoc: Int = -1
    private var primPosAttr: Int = -1
    private var primColAttr: Int = -1

    // Pre-allocated native direct buffers (zero per-frame heap/GC churn!)
    private val spriteVertexBuffer: FloatBuffer = ByteBuffer
        .allocateDirect(maxSpritesPerBatch * SpriteBatch.FLOATS_PER_SPRITE * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()

    private val spriteIndexBuffer: ShortBuffer = ByteBuffer
        .allocateDirect(maxSpritesPerBatch * SpriteBatch.INDICES_PER_SPRITE * 2)
        .order(ByteOrder.nativeOrder())
        .asShortBuffer()

    private val primitiveVertexBuffer: FloatBuffer = ByteBuffer
        .allocateDirect(maxPrimitiveVertices * 6 * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()

    private val lightPosRadiusScratch = FloatArray(8 * 3)
    private val lightColorIntensityScratch = FloatArray(8 * 4)

    /**
     * Must be called inside GLSurfaceView.Renderer.onSurfaceCreated().
     */
    fun onContextCreated() {
        contextGeneration++
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        GLES20.glDisable(GLES20.GL_CULL_FACE)

        unlitProgram = buildProgram(ShaderLibrary.SPRITE_VERTEX_GLSL, ShaderLibrary.SPRITE_FRAGMENT_GLSL, "UnlitSprite")
        unlitProjLoc = GLES20.glGetUniformLocation(unlitProgram, "uProjectionView")
        unlitTexLoc = GLES20.glGetUniformLocation(unlitProgram, "uTexture")
        unlitPosAttr = GLES20.glGetAttribLocation(unlitProgram, "aPosition")
        unlitUvAttr = GLES20.glGetAttribLocation(unlitProgram, "aTexCoord")
        unlitColAttr = GLES20.glGetAttribLocation(unlitProgram, "aColor")

        litProgram = buildProgram(ShaderLibrary.SPRITE_VERTEX_GLSL, ShaderLibrary.LIT_SPRITE_FRAGMENT_GLSL, "Lit2DSprite")
        litProjLoc = GLES20.glGetUniformLocation(litProgram, "uProjectionView")
        litTexLoc = GLES20.glGetUniformLocation(litProgram, "uTexture")
        litAmbientLoc = GLES20.glGetUniformLocation(litProgram, "uAmbientColor")
        litLightCountLoc = GLES20.glGetUniformLocation(litProgram, "uLightCount")
        litLightPosRadLoc = GLES20.glGetUniformLocation(litProgram, "uLightPosRadius")
        litLightColIntLoc = GLES20.glGetUniformLocation(litProgram, "uLightColorIntensity")
        litPosAttr = GLES20.glGetAttribLocation(litProgram, "aPosition")
        litUvAttr = GLES20.glGetAttribLocation(litProgram, "aTexCoord")
        litColAttr = GLES20.glGetAttribLocation(litProgram, "aColor")

        primProgram = buildProgram(ShaderLibrary.PRIMITIVE_VERTEX_GLSL, ShaderLibrary.PRIMITIVE_FRAGMENT_GLSL, "Primitive2D")
        primProjLoc = GLES20.glGetUniformLocation(primProgram, "uProjectionView")
        primPosAttr = GLES20.glGetAttribLocation(primProgram, "aPosition")
        primColAttr = GLES20.glGetAttribLocation(primProgram, "aColor")

        EngineLogger.info("GLESRenderBackend", "OpenGL ES initialized (gen=$contextGeneration, renderer=${GLES20.glGetString(GLES20.GL_RENDERER)})")
    }

    override fun setViewport(x: Int, y: Int, width: Int, height: Int) {
        surfaceWidth = width.coerceAtLeast(1)
        surfaceHeight = height.coerceAtLeast(1)
        GLES20.glViewport(x, y, surfaceWidth, surfaceHeight)
    }

    override fun clear(color: Color) {
        GLES20.glClearColor(color.r, color.g, color.b, color.a)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
    }

    override fun ensureTextureUploaded(texture: Texture2D): Int {
        val needsNewHandle = texture.gpuHandle == 0 || texture.gpuContextGeneration != contextGeneration
        if (!needsNewHandle && !texture.isDirty) {
            return texture.gpuHandle
        }

        if (needsNewHandle) {
            val handles = IntArray(1)
            GLES20.glGenTextures(1, handles, 0)
            texture.gpuHandle = handles[0]
            texture.gpuContextGeneration = contextGeneration
        }

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture.gpuHandle)

        val glFilter = when (texture.filter) {
            TextureFilter.NEAREST -> GLES20.GL_NEAREST
            TextureFilter.LINEAR -> GLES20.GL_LINEAR
        }
        val glWrap = when (texture.wrap) {
            TextureWrap.CLAMP_TO_EDGE -> GLES20.GL_CLAMP_TO_EDGE
            TextureWrap.REPEAT -> GLES20.GL_REPEAT
        }

        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, glFilter)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, glFilter)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, glWrap)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, glWrap)

        val pixels = texture.pixelBuffer
        if (pixels != null) {
            val byteBuf = ByteBuffer.allocateDirect(pixels.bytes.size).order(ByteOrder.nativeOrder())
            byteBuf.put(pixels.bytes)
            byteBuf.position(0)
            GLES20.glTexImage2D(
                GLES20.GL_TEXTURE_2D,
                0,
                GLES20.GL_RGBA,
                texture.width,
                texture.height,
                0,
                GLES20.GL_RGBA,
                GLES20.GL_UNSIGNED_BYTE,
                byteBuf
            )
        }

        texture.isDirty = false
        return texture.gpuHandle
    }

    override fun releaseTexture(texture: Texture2D) {
        if (texture.gpuHandle != 0 && texture.gpuContextGeneration == contextGeneration) {
            GLES20.glDeleteTextures(1, intArrayOf(texture.gpuHandle), 0)
        }
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
        if (floatCount <= 0 || indexCount <= 0) return

        applyBlendMode(blendMode)
        val texHandle = ensureTextureUploaded(texture)

        spriteVertexBuffer.clear()
        spriteVertexBuffer.put(vertices, 0, floatCount)
        spriteVertexBuffer.position(0)

        spriteIndexBuffer.clear()
        spriteIndexBuffer.put(indices, 0, indexCount)
        spriteIndexBuffer.position(0)

        val strideBytes = SpriteBatch.FLOATS_PER_VERTEX * 4

        if (!isLit) {
            GLES20.glUseProgram(unlitProgram)
            GLES20.glUniformMatrix4fv(unlitProjLoc, 1, false, projectionView.values, 0)
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texHandle)
            GLES20.glUniform1i(unlitTexLoc, 0)

            bindSpriteAttributes(unlitPosAttr, unlitUvAttr, unlitColAttr, strideBytes)
            GLES20.glDrawElements(GLES20.GL_TRIANGLES, indexCount, GLES20.GL_UNSIGNED_SHORT, spriteIndexBuffer)
            unbindSpriteAttributes(unlitPosAttr, unlitUvAttr, unlitColAttr)
        } else {
            GLES20.glUseProgram(litProgram)
            GLES20.glUniformMatrix4fv(litProjLoc, 1, false, projectionView.values, 0)
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texHandle)
            GLES20.glUniform1i(litTexLoc, 0)

            GLES20.glUniform4f(litAmbientLoc, ambientColor.r, ambientColor.g, ambientColor.b, ambientColor.a)
            val count = minOf(8, lights.size)
            GLES20.glUniform1i(litLightCountLoc, count)
            if (count > 0) {
                for (i in 0 until count) {
                    val l = lights[i]
                    lightPosRadiusScratch[i * 3 + 0] = l.x
                    lightPosRadiusScratch[i * 3 + 1] = l.y
                    lightPosRadiusScratch[i * 3 + 2] = l.radius

                    lightColorIntensityScratch[i * 4 + 0] = l.color.r
                    lightColorIntensityScratch[i * 4 + 1] = l.color.g
                    lightColorIntensityScratch[i * 4 + 2] = l.color.b
                    lightColorIntensityScratch[i * 4 + 3] = l.intensity
                }
                GLES20.glUniform3fv(litLightPosRadLoc, count, lightPosRadiusScratch, 0)
                GLES20.glUniform4fv(litLightColIntLoc, count, lightColorIntensityScratch, 0)
            }

            bindSpriteAttributes(litPosAttr, litUvAttr, litColAttr, strideBytes)
            GLES20.glDrawElements(GLES20.GL_TRIANGLES, indexCount, GLES20.GL_UNSIGNED_SHORT, spriteIndexBuffer)
            unbindSpriteAttributes(litPosAttr, litUvAttr, litColAttr)
        }
    }

    private fun bindSpriteAttributes(posLoc: Int, uvLoc: Int, colLoc: Int, strideBytes: Int) {
        spriteVertexBuffer.position(0)
        GLES20.glEnableVertexAttribArray(posLoc)
        GLES20.glVertexAttribPointer(posLoc, 2, GLES20.GL_FLOAT, false, strideBytes, spriteVertexBuffer)

        spriteVertexBuffer.position(2)
        GLES20.glEnableVertexAttribArray(uvLoc)
        GLES20.glVertexAttribPointer(uvLoc, 2, GLES20.GL_FLOAT, false, strideBytes, spriteVertexBuffer)

        spriteVertexBuffer.position(4)
        GLES20.glEnableVertexAttribArray(colLoc)
        GLES20.glVertexAttribPointer(colLoc, 4, GLES20.GL_FLOAT, false, strideBytes, spriteVertexBuffer)
    }

    private fun unbindSpriteAttributes(posLoc: Int, uvLoc: Int, colLoc: Int) {
        GLES20.glDisableVertexAttribArray(posLoc)
        GLES20.glDisableVertexAttribArray(uvLoc)
        GLES20.glDisableVertexAttribArray(colLoc)
    }

    override fun submitPrimitiveBatch(
        vertices: FloatArray,
        floatCount: Int,
        primitiveMode: PrimitiveMode,
        blendMode: BlendMode,
        projectionView: Matrix4
    ) {
        if (floatCount <= 0) return
        applyBlendMode(blendMode)

        val safeFloats = floatCount.coerceAtMost(primitiveVertexBuffer.capacity())
        val vertCount = safeFloats / 6

        primitiveVertexBuffer.clear()
        primitiveVertexBuffer.put(vertices, 0, safeFloats)

        GLES20.glUseProgram(primProgram)
        GLES20.glUniformMatrix4fv(primProjLoc, 1, false, projectionView.values, 0)

        val strideBytes = 6 * 4
        primitiveVertexBuffer.position(0)
        GLES20.glEnableVertexAttribArray(primPosAttr)
        GLES20.glVertexAttribPointer(primPosAttr, 2, GLES20.GL_FLOAT, false, strideBytes, primitiveVertexBuffer)

        primitiveVertexBuffer.position(2)
        GLES20.glEnableVertexAttribArray(primColAttr)
        GLES20.glVertexAttribPointer(primColAttr, 4, GLES20.GL_FLOAT, false, strideBytes, primitiveVertexBuffer)

        val glMode = when (primitiveMode) {
            PrimitiveMode.LINES -> GLES20.GL_LINES
            PrimitiveMode.TRIANGLES -> GLES20.GL_TRIANGLES
        }
        GLES20.glDrawArrays(glMode, 0, vertCount)

        GLES20.glDisableVertexAttribArray(primPosAttr)
        GLES20.glDisableVertexAttribArray(primColAttr)
    }

    private fun applyBlendMode(mode: BlendMode) {
        when (mode) {
            BlendMode.OPAQUE -> GLES20.glDisable(GLES20.GL_BLEND)
            BlendMode.ALPHA -> {
                GLES20.glEnable(GLES20.GL_BLEND)
                GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
            }
            BlendMode.ADDITIVE -> {
                GLES20.glEnable(GLES20.GL_BLEND)
                GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE)
            }
            BlendMode.MULTIPLY -> {
                GLES20.glEnable(GLES20.GL_BLEND)
                GLES20.glBlendFunc(GLES20.GL_DST_COLOR, GLES20.GL_ONE_MINUS_SRC_ALPHA)
            }
        }
    }

    private fun buildProgram(vertSrc: String, fragSrc: String, label: String): Int {
        val vs = compileShader(GLES20.GL_VERTEX_SHADER, vertSrc, "$label.vert")
        val fs = compileShader(GLES20.GL_FRAGMENT_SHADER, fragSrc, "$label.frag")
        val program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, vs)
        GLES20.glAttachShader(program, fs)
        GLES20.glLinkProgram(program)

        val linkStatus = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] == 0) {
            val log = GLES20.glGetProgramInfoLog(program)
            EngineLogger.error("GLESRenderBackend", "Shader link failed ($label): $log")
            GLES20.glDeleteProgram(program)
            return 0
        }
        GLES20.glDeleteShader(vs)
        GLES20.glDeleteShader(fs)
        return program
    }

    private fun compileShader(type: Int, source: String, label: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        val compiled = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0)
        if (compiled[0] == 0) {
            val log = GLES20.glGetShaderInfoLog(shader)
            EngineLogger.error("GLESRenderBackend", "Shader compile failed ($label): $log")
            GLES20.glDeleteShader(shader)
            return 0
        }
        return shader
    }
}
