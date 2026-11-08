package com.tengine.core

import com.tengine.core.audio.AudioBackend
import com.tengine.core.audio.AudioMixer
import com.tengine.core.audio.NullAudioBackend
import com.tengine.core.ecs.GameObject
import com.tengine.core.graphics.Camera2D
import com.tengine.core.graphics.NullRenderBackend
import com.tengine.core.graphics.RenderBackend
import com.tengine.core.graphics.RenderPipeline2D
import com.tengine.core.graphics.ShapeRenderer
import com.tengine.core.graphics.SpriteBatch
import com.tengine.core.input.Input
import com.tengine.core.math.AABB
import com.tengine.core.scene.Scene
import com.tengine.core.scene.SceneManager

enum class EngineRunMode {
    PLAYING,
    PAUSED,
    EDIT_MODE
}

/**
 * Master T-Engine Runtime Kernel orchestrating Fixed-Timestep Physics, Behaviour Lifecycles,
 * Input Polling, Multi-Pass 2D Rendering, Audio Mixing, Scene Transitions, and Frame Telemetry!
 */
class Engine(
    val config: EngineConfig = EngineConfig(),
    renderBackend: RenderBackend = NullRenderBackend(config.designWidth, config.designHeight),
    audioBackend: AudioBackend = NullAudioBackend()
) {
    val audioMixer = AudioMixer(audioBackend)
    val sceneManager = SceneManager(audioMixer)
    val renderPipeline = RenderPipeline2D(renderBackend, config.maxBatchSprites)
    val profiler = FrameProfiler()

    var runMode: EngineRunMode = EngineRunMode.PLAYING
    private var physicsAccumulator: Float = 0f

    // Editor hooks used by T-Engine Studio
    var overrideCamera: Camera2D? = null
    var drawEditorGrid: Boolean = false
    var drawEditorGizmos: Boolean = false
    var selectedEditorObject: GameObject? = null
    var renderUiInCurrentMode: Boolean = true
    var customEditorOverlay: ((ShapeRenderer, SpriteBatch, AABB) -> Unit)? = null

    val activeScene: Scene?
        get() = sceneManager.activeScene

    init {
        Time.reset(config.fixedTimeStep)
        EngineLogger.info("Engine", "Initialized ${config.applicationName} (FixedStep=${config.fixedTimeStep}s)")
    }

    fun attachBackends(renderBackend: RenderBackend, audioBackend: AudioBackend? = null) {
        renderPipeline.backend = renderBackend
        if (audioBackend != null) {
            audioMixer.backend = audioBackend
        }
    }

    fun loadScene(scene: Scene) {
        physicsAccumulator = 0f
        sceneManager.loadSceneImmediate(scene)
    }

    /**
     * Advances a single paused simulation step (Unity "Step" toolbar button!).
     */
    fun stepSingleFrame() {
        val scene = sceneManager.activeScene ?: return
        val dt = config.fixedTimeStep
        Time.stepFrame(dt)
        Input.beginFrame()
        Input.refreshActionSnapshots()
        scene.stepFixedUpdate(dt)
        scene.stepUpdate(dt, renderPipeline.backend.surfaceWidth, renderPipeline.backend.surfaceHeight)
    }

    /**
     * Executes one complete T-Engine frame:
     * Input -> Scene Transition -> FixedUpdate (Physics2D) -> Update/LateUpdate -> RenderPipeline2D -> Profiler!
     */
    fun tickFrame(rawDeltaSeconds: Float) {
        val frameStartNs = System.nanoTime()

        Time.stepFrame(rawDeltaSeconds)
        Input.beginFrame()
        sceneManager.applyPendingTransitionIfNeeded()

        val scene = sceneManager.activeScene
        var physicsMs = 0f
        var updateMs = 0f

        if (scene != null && runMode == EngineRunMode.PLAYING) {
            val viewW = renderPipeline.backend.surfaceWidth
            val viewH = renderPipeline.backend.surfaceHeight

            // Update UI touch inputs before refreshing action snapshots so Virtual Buttons trigger in the same frame!
            for (canvas in scene.uiCanvases) {
                canvas.updateInput(viewW, viewH)
            }
            Input.refreshActionSnapshots()

            // Fixed-timestep Physics2D accumulator
            val physStartNs = System.nanoTime()
            physicsAccumulator += Time.deltaTime
            val fixedDt = Time.fixedDeltaTime
            var subSteps = 0
            while (physicsAccumulator >= fixedDt && subSteps < config.maxPhysicsSubSteps) {
                scene.stepFixedUpdate(fixedDt)
                physicsAccumulator -= fixedDt
                subSteps++
            }
            if (subSteps == config.maxPhysicsSubSteps && physicsAccumulator > fixedDt) {
                physicsAccumulator = 0f // Prevent spiral of death on frame spikes
            }
            Time.alpha = if (fixedDt > 0f) physicsAccumulator / fixedDt else 0f
            physicsMs = (System.nanoTime() - physStartNs) / 1_000_000f

            // Variable-timestep Gameplay Update, Scheduler, Tweens, LateUpdate
            val updStartNs = System.nanoTime()
            scene.stepUpdate(Time.deltaTime, viewW, viewH)
            updateMs = (System.nanoTime() - updStartNs) / 1_000_000f
        } else {
            Input.refreshActionSnapshots()
            scene?.flushPendingDestroys()
        }

        // Render Pass
        val renderStartNs = System.nanoTime()
        if (scene != null) {
            renderPipeline.renderScene(
                scene = scene,
                overrideCamera = overrideCamera,
                drawEditorGrid = drawEditorGrid,
                drawGizmos = drawEditorGizmos,
                selectedObject = selectedEditorObject,
                extraWorldOverlay = customEditorOverlay,
                renderUiCanvases = renderUiInCurrentMode
            )
        } else {
            renderPipeline.backend.clear(config.clearColor)
        }
        val renderMs = (System.nanoTime() - renderStartNs) / 1_000_000f
        val totalFrameMs = (System.nanoTime() - frameStartNs) / 1_000_000f

        profiler.record(
            ProfilerSample(
                frameNumber = Time.frameCount,
                fps = Time.fps,
                frameTimeMs = totalFrameMs,
                physicsTimeMs = physicsMs,
                updateTimeMs = updateMs,
                renderTimeMs = renderMs,
                drawCalls = renderPipeline.lastFrameDrawCalls,
                spriteCount = renderPipeline.lastFrameSprites,
                triangleCount = renderPipeline.lastFrameTriangles,
                activeGameObjects = scene?.allActiveGameObjects()?.size ?: 0,
                physicsBodies = scene?.physicsWorld?.bodyCount ?: 0,
                physicsContacts = scene?.physicsWorld?.activeContactCount ?: 0,
                activeParticles = renderPipeline.lastFrameParticlesCount
            )
        )
    }

    fun shutdown() {
        sceneManager.activeScene?.unload()
        audioMixer.backend.release()
        Input.clearAll()
    }
}
