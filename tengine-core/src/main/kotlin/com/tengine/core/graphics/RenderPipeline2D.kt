package com.tengine.core.graphics

import com.tengine.core.ecs.GameObject
import com.tengine.core.graphics.components.Light2D
import com.tengine.core.graphics.components.LightType2D
import com.tengine.core.graphics.components.ParticleSystem2D
import com.tengine.core.graphics.components.SpriteRenderer
import com.tengine.core.graphics.components.Tilemap
import com.tengine.core.graphics.components.TrailRenderer2D
import com.tengine.core.math.AABB
import com.tengine.core.math.Color
import com.tengine.core.math.Matrix4
import com.tengine.core.scene.Scene
import com.tengine.core.ui.UICanvas

/**
 * Multi-Pass 2D Scene Render Pipeline:
 * 1. Lighting Gathering Pass (Global Ambient + Point Lights)
 * 2. Frustum Culling & Sorting Pass (SortingLayer -> orderInLayer -> Y-sort -> Texture coherence)
 * 3. World Batched Render Pass (Tilemaps, Sprites, Trails, Particles, Light Halos)
 * 4. Optional Editor Scene Gizmo Pass (Infinite Grid, Colliders, Light Radii, Camera Frustums, Custom Handles)
 * 5. Screen-Space UI Canvas Overlay Pass
 */
class RenderPipeline2D(
    backend: RenderBackend,
    maxBatchSprites: Int = 4096
) {
    var backend: RenderBackend = backend
        set(value) {
            field = value
            spriteBatch.backend = value
            shapeRenderer.backend = value
        }

    val spriteBatch = SpriteBatch(backend, maxBatchSprites)
    val shapeRenderer = ShapeRenderer(backend)

    private data class SortableItem(
        val layerPriority: Int,
        val orderInLayer: Int,
        val sortY: Float,
        val kind: Int, // 0=Tilemap, 1=Sprite, 2=Trail, 3=Particle
        val component: Any
    ) : Comparable<SortableItem> {
        override fun compareTo(other: SortableItem): Int {
            if (layerPriority != other.layerPriority) return layerPriority.compareTo(other.layerPriority)
            if (orderInLayer != other.orderInLayer) return orderInLayer.compareTo(other.orderInLayer)
            return other.sortY.compareTo(sortY) // Higher Y drawn behind lower Y when Y-sorted
        }
    }

    private val renderQueue = ArrayList<SortableItem>(512)
    private val pointLights = ArrayList<Light2D>(16)
    private val lightUniforms = ArrayList<LightUniformData>(8)
    private val screenOrtho = Matrix4()

    var lastFrameParticlesCount: Int = 0
        private set

    val lastFrameDrawCalls: Int
        get() = spriteBatch.drawCallsThisFrame + shapeRenderer.drawCallsThisFrame

    val lastFrameSprites: Int
        get() = spriteBatch.spritesSubmittedThisFrame

    val lastFrameTriangles: Int
        get() = spriteBatch.trianglesSubmittedThisFrame

    fun renderScene(
        scene: Scene,
        overrideCamera: Camera2D? = null,
        viewportWidth: Int = backend.surfaceWidth,
        viewportHeight: Int = backend.surfaceHeight,
        drawEditorGrid: Boolean = false,
        drawGizmos: Boolean = false,
        selectedObject: GameObject? = null,
        extraWorldOverlay: ((ShapeRenderer, SpriteBatch, AABB) -> Unit)? = null,
        renderUiCanvases: Boolean = true
    ) {
        if (viewportWidth <= 0 || viewportHeight <= 0) return

        spriteBatch.resetFrameStats()
        shapeRenderer.resetFrameStats()
        lastFrameParticlesCount = 0

        backend.setViewport(0, 0, viewportWidth, viewportHeight)

        val camera = overrideCamera ?: scene.findMainCamera()
        val clearColor = camera?.backgroundColor ?: Color(0.07f, 0.08f, 0.13f, 1f)
        backend.clear(clearColor)

        val projView: Matrix4
        val visibleBounds: AABB
        val cullingMask: Int

        if (camera != null) {
            projView = camera.computeProjectionViewMatrix(viewportWidth, viewportHeight)
            visibleBounds = camera.getVisibleWorldBounds(viewportWidth, viewportHeight)
            cullingMask = camera.cullingMask
        } else {
            val aspect = viewportWidth.toFloat() / viewportHeight.toFloat()
            val halfH = 5.4f
            val halfW = halfH * aspect
            projView = Matrix4().setOrtho(-halfW, halfW, -halfH, halfH, -100f, 100f)
            visibleBounds = AABB(-halfW, -halfH, halfW, halfH)
            cullingMask = -1
        }

        // Optional Editor Infinite Grid behind world objects
        if (drawEditorGrid) {
            val pixelWorldSize = visibleBounds.height / viewportHeight.toFloat() * 1.5f
            shapeRenderer.begin(projView, PrimitiveMode.TRIANGLES, BlendMode.ALPHA)
            shapeRenderer.drawEditorGrid(visibleBounds, cellSize = 1f, pixelWorldSize = pixelWorldSize)
            shapeRenderer.end()
        }

        // Gather Lights & Renderables
        renderQueue.clear()
        pointLights.clear()
        lightUniforms.clear()
        var ambientColor = Color(1f, 1f, 1f, 1f)
        var foundGlobalLight = false

        val allObjects = scene.allActiveGameObjects()
        for (i in 0 until allObjects.size) {
            val go = allObjects[i]
            if ((cullingMask and (1 shl go.layer)) == 0) continue

            val comps = go.components
            for (j in 0 until comps.size) {
                val c = comps[j]
                if (!c.enabled) continue
                when (c) {
                    is Light2D -> {
                        if (c.lightType == LightType2D.GLOBAL_AMBIENT) {
                            ambientColor = c.color.withAlpha(c.effectiveIntensity)
                            foundGlobalLight = true
                        } else {
                            pointLights.add(c)
                        }
                    }
                    is Tilemap -> {
                        renderQueue.add(SortableItem(c.sortingLayer.priority, c.orderInLayer, 0f, 0, c))
                    }
                    is SpriteRenderer -> {
                        if (c.getWorldBounds().overlaps(visibleBounds)) {
                            val sy = if (c.ySort) go.transform.worldY else 0f
                            renderQueue.add(SortableItem(c.sortingLayer.priority, c.orderInLayer, sy, 1, c))
                        }
                    }
                    is TrailRenderer2D -> {
                        renderQueue.add(SortableItem(c.sortingLayer.priority, c.orderInLayer, 0f, 2, c))
                    }
                    is ParticleSystem2D -> {
                        lastFrameParticlesCount += c.activeCount
                        if (c.activeCount > 0) {
                            renderQueue.add(SortableItem(c.sortingLayer.priority, c.orderInLayer, 0f, 3, c))
                        }
                    }
                }
            }
        }

        if (!foundGlobalLight && pointLights.isNotEmpty()) {
            // Subtle moody default ambient when point lights exist without an explicit Global light
            ambientColor = Color(0.42f, 0.45f, 0.56f, 1f)
        }

        for (i in 0 until minOf(8, pointLights.size)) {
            val pl = pointLights[i]
            val wp = pl.transform.worldPosition
            lightUniforms.add(
                LightUniformData(
                    x = wp.x,
                    y = wp.y,
                    radius = pl.radius,
                    color = pl.color,
                    intensity = pl.effectiveIntensity
                )
            )
        }

        renderQueue.sort()

        // World Batched Pass
        spriteBatch.ambientLightColor = ambientColor
        spriteBatch.activeLights = lightUniforms
        spriteBatch.begin(projView, BlendMode.ALPHA, isLit = false)

        for (i in 0 until renderQueue.size) {
            val item = renderQueue[i]
            when (val comp = item.component) {
                is Tilemap -> comp.render(spriteBatch, visibleBounds)
                is SpriteRenderer -> comp.render(spriteBatch)
                is TrailRenderer2D -> comp.render(spriteBatch)
                is ParticleSystem2D -> comp.render(spriteBatch)
            }
        }

        // Additive 2D Light Glow Halos
        for (i in 0 until pointLights.size) {
            pointLights[i].renderHalo(spriteBatch)
        }

        spriteBatch.end()

        // Editor Gizmos & Custom Overlay Handles
        if (drawGizmos || extraWorldOverlay != null) {
            shapeRenderer.begin(projView, PrimitiveMode.TRIANGLES, BlendMode.ALPHA)
            if (drawGizmos) {
                for (i in 0 until allObjects.size) {
                    val go = allObjects[i]
                    val isSelected = go === selectedObject
                    for (comp in go.components) {
                        if (comp.enabled) {
                            comp.onDrawGizmos(shapeRenderer, isSelected)
                        }
                    }
                }
            }
            shapeRenderer.end()

            if (extraWorldOverlay != null) {
                shapeRenderer.begin(projView, PrimitiveMode.TRIANGLES, BlendMode.ALPHA)
                spriteBatch.begin(projView, BlendMode.ALPHA, isLit = false)
                extraWorldOverlay(shapeRenderer, spriteBatch, visibleBounds)
                spriteBatch.end()
                shapeRenderer.end()
            }
        }

        // Screen-Space UI Canvases (+Y UP, 0..viewportWidth, 0..viewportHeight)
        if (renderUiCanvases) {
            val canvases = scene.uiCanvases
            if (canvases.isNotEmpty()) {
                screenOrtho.setOrtho(0f, viewportWidth.toFloat(), 0f, viewportHeight.toFloat(), -10f, 10f)
                for (canvas in canvases) {
                    if (canvas.enabled) {
                        canvas.render(spriteBatch, shapeRenderer, screenOrtho, viewportWidth, viewportHeight)
                    }
                }
            }
        }
    }
}
