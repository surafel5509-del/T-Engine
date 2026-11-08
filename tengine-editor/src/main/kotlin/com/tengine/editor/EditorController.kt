package com.tengine.editor

import android.view.MotionEvent
import com.tengine.android.TEngineSurfaceView
import com.tengine.android.assets.AndroidAssetStorage
import com.tengine.core.EngineLogger
import com.tengine.core.EngineRunMode
import com.tengine.core.ecs.GameObject
import com.tengine.core.ecs.Prefab
import com.tengine.core.graphics.Camera2D
import com.tengine.core.graphics.components.SpriteRenderer
import com.tengine.core.graphics.components.Tilemap
import com.tengine.core.input.Input
import com.tengine.core.math.Color
import com.tengine.core.math.Mathf
import com.tengine.core.math.Vector2
import com.tengine.core.physics.Collider2D
import com.tengine.core.scene.Scene
import com.tengine.core.scene.SceneSerializer
import com.tengine.editor.gizmo.EditorToolMode
import com.tengine.editor.gizmo.TransformGizmo2D
import com.tengine.editor.templates.GameObjectTemplates
import kotlin.math.hypot

/**
 * Central state & workflow controller for T-Engine 2D Studio:
 * - Edit / Play / Pause / Step / Stop state transitions with exact JSON Scene snapshot backup & restore!
 * - Undo / Redo Scene history stack
 * - Editor Camera pan/zoom & focus
 * - Object picking, Gizmo manipulation, and Tilemap painting!
 */
class EditorController(
    val surfaceView: TEngineSurfaceView,
    val storage: AndroidAssetStorage
) {
    val editorCameraObject = GameObject("EditorViewportCamera").apply {
        transform.localPosition = Vector2(0f, 1.5f)
    }
    val editorCamera: Camera2D = editorCameraObject.addComponent(
        Camera2D(6.5f).apply {
            backgroundColor = Color(0.09f, 0.10f, 0.14f, 1f)
            isMainCamera = false
        }
    )

    val gizmo = TransformGizmo2D()

    var toolMode: EditorToolMode = EditorToolMode.MOVE
        set(value) {
            field = value
            notifyStateChanged()
        }

    var snapToGrid: Boolean = false
    var activeTileBrushId: Int = 2 // 0 = Eraser, 1..5 = Palette tile IDs

    var selectedObject: GameObject? = null
        private set

    var isPlayModeActive: Boolean = false
        private set

    var isPlayModePaused: Boolean = false
        private set

    private var playBackupJson: String? = null
    private var selectedUuidBeforePlay: String? = null

    // Undo / Redo JSON snapshot history
    private val undoStack = ArrayDeque<String>(30)
    private val redoStack = ArrayDeque<String>(30)

    var onUiStateChanged: (() -> Unit)? = null

    // Viewport touch tracking state
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var prevPinchDist = -1f
    private var prevTwoCenterX = 0f
    private var prevTwoCenterY = 0f
    private var isDraggingGizmo = false
    private var singleTouchMoved = false

    init {
        configureEditModeRendering()
        surfaceView.touchInterceptor = { ev -> handleViewportTouch(ev) }
        surfaceView.engine.customEditorOverlay = { shapes, _, _ ->
            if (!isPlayModeActive) {
                gizmo.render(shapes, selectedObject, editorCamera)
                gizmo.renderToolHandles(shapes, selectedObject, editorCamera, toolMode)
            }
        }
    }

    private fun configureEditModeRendering() {
        val eng = surfaceView.engine
        eng.runMode = EngineRunMode.EDIT_MODE
        eng.overrideCamera = editorCamera
        eng.drawEditorGrid = true
        eng.drawEditorGizmos = true
        eng.renderUiInCurrentMode = false
        eng.selectedEditorObject = selectedObject
    }

    fun openSceneInEditor(scene: Scene) {
        surfaceView.postToEngineThread {
            isPlayModeActive = false
            isPlayModePaused = false
            playBackupJson = null
            undoStack.clear()
            redoStack.clear()
            surfaceView.engine.loadScene(scene)
            selectedObject = scene.rootObjects.firstOrNull()
            configureEditModeRendering()
            notifyStateChanged()
        }
    }

    fun selectObject(go: GameObject?) {
        selectedObject = go
        surfaceView.engine.selectedEditorObject = go
        notifyStateChanged()
    }

    fun focusCameraOnSelected() {
        val target = selectedObject ?: return
        editorCameraObject.transform.worldPosition = target.transform.worldPosition
    }

    // =========================================================================
    // Play / Pause / Step / Stop Workflow (Unity-Style!)
    // =========================================================================

    fun enterPlayMode() {
        if (isPlayModeActive) {
            if (isPlayModePaused) {
                isPlayModePaused = false
                surfaceView.engine.runMode = EngineRunMode.PLAYING
                notifyStateChanged()
            }
            return
        }
        surfaceView.postToEngineThread {
            val scene = surfaceView.engine.activeScene ?: return@postToEngineThread
            playBackupJson = SceneSerializer.serializeSceneToJson(scene, pretty = false)
            selectedUuidBeforePlay = selectedObject?.uuid

            isPlayModeActive = true
            isPlayModePaused = false
            Input.clearAll()

            val eng = surfaceView.engine
            eng.overrideCamera = null // Use Scene's MainCamera!
            eng.drawEditorGrid = false
            eng.drawEditorGizmos = false
            eng.renderUiInCurrentMode = true
            eng.runMode = EngineRunMode.PLAYING

            EngineLogger.info("Studio", "Entered PLAY mode on scene '${scene.name}'")
            notifyStateChanged()
        }
    }

    fun togglePausePlayMode() {
        if (!isPlayModeActive) return
        isPlayModePaused = !isPlayModePaused
        surfaceView.engine.runMode = if (isPlayModePaused) EngineRunMode.PAUSED else EngineRunMode.PLAYING
        EngineLogger.info("Studio", if (isPlayModePaused) "Simulation PAUSED" else "Simulation RESUMED")
        notifyStateChanged()
    }

    fun stepFrameInPlayMode() {
        if (!isPlayModeActive) return
        isPlayModePaused = true
        surfaceView.engine.runMode = EngineRunMode.PAUSED
        surfaceView.postToEngineThread {
            surfaceView.engine.stepSingleFrame()
            notifyStateChanged()
        }
    }

    fun stopPlayMode() {
        if (!isPlayModeActive) return
        surfaceView.postToEngineThread {
            isPlayModeActive = false
            isPlayModePaused = false
            Input.clearAll()

            val backup = playBackupJson
            if (backup != null) {
                val restored = SceneSerializer.deserializeSceneFromJson(backup)
                surfaceView.engine.loadScene(restored)
                selectedObject = selectedUuidBeforePlay?.let { restored.findByUuid(it) }
                    ?: restored.rootObjects.firstOrNull()
            }
            playBackupJson = null
            configureEditModeRendering()
            EngineLogger.info("Studio", "Stopped PLAY mode and restored Editor Scene state")
            notifyStateChanged()
        }
    }

    // =========================================================================
    // Undo / Redo & Entity Manipulation
    // =========================================================================

    fun recordUndoSnapshot() {
        if (isPlayModeActive) return
        val scene = surfaceView.engine.activeScene ?: return
        val snap = SceneSerializer.serializeSceneToJson(scene, pretty = false)
        if (undoStack.lastOrNull() == snap) return
        if (undoStack.size >= 30) undoStack.removeFirst()
        undoStack.addLast(snap)
        redoStack.clear()
    }

    fun undo() {
        if (isPlayModeActive || undoStack.isEmpty()) return
        surfaceView.postToEngineThread {
            val currentScene = surfaceView.engine.activeScene ?: return@postToEngineThread
            val currentJson = SceneSerializer.serializeSceneToJson(currentScene, pretty = false)
            redoStack.addLast(currentJson)

            val prevJson = undoStack.removeLast()
            val restored = SceneSerializer.deserializeSceneFromJson(prevJson)
            val prevUuid = selectedObject?.uuid
            surfaceView.engine.loadScene(restored)
            selectedObject = prevUuid?.let { restored.findByUuid(it) }
            configureEditModeRendering()
            EngineLogger.info("Studio", "Undo applied")
            notifyStateChanged()
        }
    }

    fun redo() {
        if (isPlayModeActive || redoStack.isEmpty()) return
        surfaceView.postToEngineThread {
            val currentScene = surfaceView.engine.activeScene ?: return@postToEngineThread
            undoStack.addLast(SceneSerializer.serializeSceneToJson(currentScene, pretty = false))

            val nextJson = redoStack.removeLast()
            val restored = SceneSerializer.deserializeSceneFromJson(nextJson)
            val prevUuid = selectedObject?.uuid
            surfaceView.engine.loadScene(restored)
            selectedObject = prevUuid?.let { restored.findByUuid(it) }
            configureEditModeRendering()
            EngineLogger.info("Studio", "Redo applied")
            notifyStateChanged()
        }
    }

    fun spawnTemplate(templateName: String) {
        if (isPlayModeActive) return
        surfaceView.postToEngineThread {
            val scene = surfaceView.engine.activeScene ?: return@postToEngineThread
            recordUndoSnapshot()
            val spawnPos = editorCameraObject.transform.worldPosition
            val created = GameObjectTemplates.create(templateName, scene, spawnPos)
            selectObject(created)
            EngineLogger.info("Studio", "Created '$templateName' at ${created.transform.worldPosition}")
        }
    }

    fun duplicateSelected() {
        if (isPlayModeActive) return
        val target = selectedObject ?: return
        surfaceView.postToEngineThread {
            val scene = surfaceView.engine.activeScene ?: return@postToEngineThread
            recordUndoSnapshot()
            val prefab = Prefab.fromGameObject(target)
            val offsetPos = target.transform.worldPosition + Vector2(0.75f, 0.5f)
            val copy = prefab.instantiate(scene, position = offsetPos, parent = target.transform.parent)
            copy.name = "${target.name}_Copy"
            selectObject(copy)
        }
    }

    fun deleteSelected() {
        if (isPlayModeActive) return
        val target = selectedObject ?: return
        surfaceView.postToEngineThread {
            val scene = surfaceView.engine.activeScene ?: return@postToEngineThread
            recordUndoSnapshot()
            val deletedName = target.name
            scene.destroyImmediate(target)
            selectedObject = scene.rootObjects.firstOrNull()
            surfaceView.engine.selectedEditorObject = selectedObject
            EngineLogger.info("Studio", "Deleted '$deletedName'")
            notifyStateChanged()
        }
    }

    fun saveCurrentScene(slotName: String) {
        surfaceView.postToEngineThread {
            val scene = surfaceView.engine.activeScene ?: return@postToEngineThread
            storage.saveUserScene(slotName, scene)
            notifyStateChanged()
        }
    }

    // =========================================================================
    // Edit-Mode OpenGL Viewport Touch Handling (Gizmos, Picking, Tile Brush, Camera)
    // =========================================================================

    private fun handleViewportTouch(event: MotionEvent): Boolean {
        // In Play Mode, let game touches pass straight through to AndroidInputBridge & UICanvas!
        if (isPlayModeActive && !isPlayModePaused) {
            return false
        }

        val viewW = surfaceView.width.coerceAtLeast(1)
        val viewH = surfaceView.height.coerceAtLeast(1)

        // Two-finger pan & pinch-zoom works in ANY Edit Tool Mode!
        if (event.pointerCount >= 2) {
            isDraggingGizmo = false
            gizmo.endDrag()
            val x0 = event.getX(0); val y0 = event.getY(0)
            val x1 = event.getX(1); val y1 = event.getY(1)
            val dist = hypot(x1 - x0, y1 - y0)
            val cx = (x0 + x1) * 0.5f
            val cy = (y0 + y1) * 0.5f

            if (prevPinchDist > 8f && dist > 8f) {
                val scale = dist / prevPinchDist
                editorCamera.zoom = Mathf.clamp(editorCamera.zoom * scale, 0.15f, 12f)

                val worldPerPixel = (editorCamera.effectiveHalfHeight * 2f) / viewH
                val dx = (cx - prevTwoCenterX) * worldPerPixel
                val dy = (cy - prevTwoCenterY) * worldPerPixel
                editorCameraObject.transform.translate(-dx, dy, worldSpace = true)
            }
            prevPinchDist = dist
            prevTwoCenterX = cx
            prevTwoCenterY = cy
            return true
        }

        prevPinchDist = -1f
        val sx = event.x
        val sy = event.y

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX = sx
                lastTouchY = sy
                singleTouchMoved = false
                val worldPt = editorCamera.screenToWorld(sx, sy, viewW, viewH)

                if (toolMode == EditorToolMode.TILE_BRUSH) {
                    recordUndoSnapshot()
                    paintTileAtWorld(worldPt)
                    return true
                }

                if (gizmo.tryBeginDrag(worldPt, selectedObject, editorCamera, toolMode)) {
                    recordUndoSnapshot()
                    isDraggingGizmo = true
                    return true
                }
                isDraggingGizmo = false
            }

            MotionEvent.ACTION_MOVE -> {
                val dxPx = sx - lastTouchX
                val dyPx = sy - lastTouchY
                if (dxPx * dxPx + dyPx * dyPx > 36f) {
                    singleTouchMoved = true
                }

                if (toolMode == EditorToolMode.TILE_BRUSH) {
                    val worldPt = editorCamera.screenToWorld(sx, sy, viewW, viewH)
                    paintTileAtWorld(worldPt)
                } else if (isDraggingGizmo) {
                    val worldPt = editorCamera.screenToWorld(sx, sy, viewW, viewH)
                    surfaceView.postToEngineThread {
                        gizmo.updateDrag(worldPt, selectedObject, editorCamera, snapToGrid)
                    }
                    notifyStateChanged()
                } else if (toolMode == EditorToolMode.HAND || singleTouchMoved) {
                    val worldPerPixel = (editorCamera.effectiveHalfHeight * 2f) / viewH
                    editorCameraObject.transform.translate(-dxPx * worldPerPixel, dyPx * worldPerPixel, worldSpace = true)
                }

                lastTouchX = sx
                lastTouchY = sy
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isDraggingGizmo) {
                    isDraggingGizmo = false
                    gizmo.endDrag()
                    notifyStateChanged()
                } else if (!singleTouchMoved && toolMode != EditorToolMode.TILE_BRUSH) {
                    val worldPt = editorCamera.screenToWorld(sx, sy, viewW, viewH)
                    surfaceView.postToEngineThread {
                        pickObjectAtWorld(worldPt)
                    }
                }
            }
        }
        return true
    }

    private fun paintTileAtWorld(worldPt: Vector2) {
        surfaceView.postToEngineThread {
            val scene = surfaceView.engine.activeScene ?: return@postToEngineThread
            val tilemap = selectedObject?.getComponent(Tilemap::class)
                ?: scene.findComponent(Tilemap::class)
                ?: run {
                    val created = GameObjectTemplates.create("Tilemap Grid", scene, Vector2.ZERO)
                    created.getComponent(Tilemap::class)!!
                }
            val gx = tilemap.worldToCellX(worldPt.x)
            val gy = tilemap.worldToCellY(worldPt.y)
            tilemap.setTile(gx, gy, activeTileBrushId)
            tilemap.rebuildColliders()
        }
    }

    private fun pickObjectAtWorld(worldPt: Vector2) {
        val scene = surfaceView.engine.activeScene ?: return
        val candidates = scene.allActiveGameObjects()
        var bestMatch: GameObject? = null
        var smallestArea = Float.POSITIVE_INFINITY

        for (go in candidates) {
            val sr = go.getComponent(SpriteRenderer::class)
            val col = go.getComponent(Collider2D::class)
            val hit = when {
                col != null && col.containsPoint(worldPt) -> col.worldAABB
                sr != null && sr.getWorldBounds().contains(worldPt) -> sr.getWorldBounds()
                go.transform.worldPosition.distanceTo(worldPt) <= 0.55f -> null
                else -> continue
            }
            val area = if (hit != null) hit.width * hit.height else 0.25f
            if (area < smallestArea) {
                smallestArea = area
                bestMatch = go
            }
        }
        selectObject(bestMatch)
    }

    private fun notifyStateChanged() {
        surfaceView.post {
            onUiStateChanged?.invoke()
        }
    }
}
