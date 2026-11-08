package com.tengine.editor.ui

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.Toast
import com.tengine.android.TEngineSurfaceView
import com.tengine.android.assets.AndroidAssetStorage
import com.tengine.core.EngineConfig
import com.tengine.core.scene.Scene
import com.tengine.editor.EditorController
import com.tengine.editor.gizmo.EditorToolMode
import com.tengine.editor.templates.GameObjectTemplates

/**
 * Complete Unity-Like 2D Game Engine Studio Workspace View for Android!
 * Combines:
 * - Top Main Toolbar: Back/Hub, Tool Modes (Hand, Move, Rotate, Scale, TileBrush), Snap, Undo/Redo,
 *   Play / Pause / Step / Stop Controls, Save/Load Scene, and Dock Panel Toggles!
 * - Left Dock: Recursive Scene Hierarchy Tree
 * - Center Workspace: Hardware-Accelerated OpenGL ES 2D Scene / Game Viewport
 * - Right Dock: Live Property & Component Inspector
 * - Bottom Dock: Live Sparkline Profiler & Engine Console
 */
@SuppressLint("ViewConstructor")
class TEngineStudioView(
    context: Context,
    initialSceneFactory: () -> Scene,
    private val presetScenes: Map<String, () -> Scene> = emptyMap(),
    private val onExitToHub: (() -> Unit)? = null
) : LinearLayout(context) {

    val surfaceView = TEngineSurfaceView(
        context,
        EngineConfig(applicationName = "T-Engine 2D Studio")
    )
    val storage = AndroidAssetStorage(context)
    val controller = EditorController(surfaceView, storage)

    private val hierarchyPanel = HierarchyPanelView(context, controller)
    private val inspectorPanel = InspectorPanelView(context, controller)
    private val bottomDock = ConsoleAndProfilerView(context, surfaceView.engine.profiler)

    private lateinit var playBtn: Button
    private lateinit var pauseBtn: Button
    private lateinit var stopBtn: Button
    private val toolButtons = LinkedHashMap<EditorToolMode, Button>()

    private var lastTelemetryRefreshMs: Long = 0L

    init {
        orientation = VERTICAL
        setBackgroundColor(EditorTheme.BG_DEEP)

        // 1. Top Studio Toolbar
        addView(buildTopToolbar(), LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        // 2. Middle 3-Column Workspace (Hierarchy | OpenGL Viewport | Inspector)
        val workspaceRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
        }

        val screenWidthDp = resources.configuration.screenWidthDp
        val sideDockWidthPx = EditorTheme.dp(context, if (screenWidthDp >= 720) 235f else 195f)

        // On compact phones, start with Hierarchy & Inspector toggleable so Viewport has generous space
        if (screenWidthDp < 640) {
            hierarchyPanel.visibility = View.GONE
            inspectorPanel.visibility = View.GONE
        }

        workspaceRow.addView(hierarchyPanel, LayoutParams(sideDockWidthPx, LayoutParams.MATCH_PARENT))

        val viewportFrame = FrameLayout(context).apply {
            setBackgroundColor(EditorTheme.BG_DEEP)
            addView(surfaceView, FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        }
        workspaceRow.addView(viewportFrame, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
        workspaceRow.addView(inspectorPanel, LayoutParams(sideDockWidthPx, LayoutParams.MATCH_PARENT))

        addView(workspaceRow, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))

        // 3. Bottom Dock (Profiler & Console)
        val bottomHeightPx = EditorTheme.dp(context, 108f)
        addView(bottomDock, LayoutParams(LayoutParams.MATCH_PARENT, bottomHeightPx))

        // Wire state updates
        controller.onUiStateChanged = {
            refreshEditorChrome()
        }

        surfaceView.onFrameCompleted = {
            val now = System.currentTimeMillis()
            if (now - lastTelemetryRefreshMs >= 250L) {
                lastTelemetryRefreshMs = now
                post {
                    if (bottomDock.visibility == View.VISIBLE) {
                        bottomDock.refreshTelemetry()
                    }
                }
            }
        }

        controller.openSceneInEditor(initialSceneFactory())
    }

    private fun buildTopToolbar(): View {
        val scroll = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            setBackgroundColor(EditorTheme.BG_HEADER)
        }

        val bar = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val pad = EditorTheme.dp(context, 5f)
            setPadding(pad, pad, pad, pad)
        }

        fun addBarBtn(btn: Button) {
            bar.addView(btn, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                marginEnd = EditorTheme.dp(context, 4f)
            })
        }

        if (onExitToHub != null) {
            addBarBtn(EditorTheme.createToolButton(context, "◀ Hub") { onExitToHub.invoke() })
        }

        addBarBtn(EditorTheme.createToolButton(context, "Scenes ▾", bgColor = EditorTheme.BG_CARD) {
            showSceneLoaderMenu()
        })

        addBarBtn(EditorTheme.createToolButton(context, "Save") {
            promptSaveScene()
        })

        // Tool Mode Buttons (Hand / Move / Rotate / Scale / Tile)
        val modes = listOf(
            EditorToolMode.HAND to "Hand",
            EditorToolMode.MOVE to "Move",
            EditorToolMode.ROTATE to "Rotate",
            EditorToolMode.SCALE to "Scale",
            EditorToolMode.TILE_BRUSH to "Tile"
        )
        for ((mode, title) in modes) {
            val b = EditorTheme.createToolButton(context, title) {
                if (mode == EditorToolMode.TILE_BRUSH && controller.toolMode == EditorToolMode.TILE_BRUSH) {
                    showTileBrushPalettePicker()
                } else {
                    controller.toolMode = mode
                }
            }
            toolButtons[mode] = b
            addBarBtn(b)
        }

        addBarBtn(EditorTheme.createToolButton(context, "Snap") {
            controller.snapToGrid = !controller.snapToGrid
            Toast.makeText(context, "Grid Snap: ${if (controller.snapToGrid) "ON" else "OFF"}", Toast.LENGTH_SHORT).show()
        })

        addBarBtn(EditorTheme.createToolButton(context, "Undo") { controller.undo() })
        addBarBtn(EditorTheme.createToolButton(context, "Redo") { controller.redo() })

        // Unity-style Play / Pause / Step / Stop cluster!
        playBtn = EditorTheme.createToolButton(context, "▶ Play", bgColor = EditorTheme.ACCENT_GREEN) {
            controller.enterPlayMode()
        }
        pauseBtn = EditorTheme.createToolButton(context, "⏸ Pause") {
            controller.togglePausePlayMode()
        }
        val stepBtn = EditorTheme.createToolButton(context, "⏭ Step") {
            controller.stepFrameInPlayMode()
        }
        stopBtn = EditorTheme.createToolButton(context, "⏹ Stop", bgColor = EditorTheme.ACCENT_RED) {
            controller.stopPlayMode()
        }
        addBarBtn(playBtn)
        addBarBtn(pauseBtn)
        addBarBtn(stepBtn)
        addBarBtn(stopBtn)

        // Dock panel toggles (especially handy on mobile phones!)
        addBarBtn(EditorTheme.createToolButton(context, "Tree") {
            hierarchyPanel.visibility = if (hierarchyPanel.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        })
        addBarBtn(EditorTheme.createToolButton(context, "Insp") {
            inspectorPanel.visibility = if (inspectorPanel.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        })
        addBarBtn(EditorTheme.createToolButton(context, "Stats") {
            bottomDock.visibility = if (bottomDock.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        })

        scroll.addView(bar)
        return scroll
    }

    private fun refreshEditorChrome() {
        for ((mode, btn) in toolButtons) {
            val active = controller.toolMode == mode && !controller.isPlayModeActive
            btn.background = EditorTheme.roundedBox(
                context,
                fillColor = if (active) EditorTheme.ACCENT_BLUE else EditorTheme.BG_CARD,
                cornerDp = 5f,
                strokeColor = EditorTheme.BORDER_SUBTLE
            )
        }

        playBtn.background = EditorTheme.roundedBox(
            context,
            fillColor = if (controller.isPlayModeActive && !controller.isPlayModePaused)
                EditorTheme.PLAY_ACTIVE_BG else EditorTheme.ACCENT_GREEN,
            cornerDp = 5f
        )
        pauseBtn.background = EditorTheme.roundedBox(
            context,
            fillColor = if (controller.isPlayModePaused) EditorTheme.ACCENT_AMBER else EditorTheme.BG_CARD,
            cornerDp = 5f
        )
        stopBtn.isEnabled = controller.isPlayModeActive

        hierarchyPanel.refreshTree()
        inspectorPanel.refreshInspector()
    }

    private fun showTileBrushPalettePicker() {
        val items = arrayOf(
            "0: Eraser (Remove Tile)",
            "1: Stone Block (Solid)",
            "2: Grass Top (Solid)",
            "3: Neon Platform (Solid)",
            "4: Hazard Lava (Non-Solid)",
            "5: Background Brick"
        )
        AlertDialog.Builder(context)
            .setTitle("Select Tile Brush")
            .setItems(items) { _, idx ->
                controller.activeTileBrushId = idx
                controller.toolMode = EditorToolMode.TILE_BRUSH
            }
            .show()
    }

    private fun promptSaveScene() {
        val currentName = surfaceView.engine.activeScene?.name ?: "MyScene"
        val input = EditText(context).apply {
            inputType = InputType.TYPE_CLASS_TEXT
            setText(currentName.replace(" ", "_"))
            selectAll()
        }
        AlertDialog.Builder(context)
            .setTitle("Save Scene (.tescene.json)")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val name = input.text.toString().ifBlank { "MyScene" }
                controller.saveCurrentScene(name)
                Toast.makeText(context, "Saved '$name.tescene.json'", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showSceneLoaderMenu() {
        val entries = ArrayList<Pair<String, () -> Scene?>>()
        entries.add("New Empty 2D Scene" to {
            Scene("New 2D Scene").also { sc ->
                GameObjectTemplates.create("Camera 2D", sc, com.tengine.core.math.Vector2.ZERO)
                    .getComponent<com.tengine.core.graphics.Camera2D>()?.isMainCamera = true
                GameObjectTemplates.create("Static Platform", sc, com.tengine.core.math.Vector2(0f, -2f))
                GameObjectTemplates.create("Physics Crate (Dynamic)", sc, com.tengine.core.math.Vector2(0f, 2f))
            }
        })
        for ((title, factory) in presetScenes) {
            entries.add("Template: $title" to factory)
        }
        for (saved in storage.listSavedScenes()) {
            entries.add("Saved: $saved" to { storage.loadUserScene(saved) })
        }

        val labels = entries.map { it.first }.toTypedArray()
        AlertDialog.Builder(context)
            .setTitle("Open Scene in Studio")
            .setItems(labels) { _, which ->
                val loaded = entries[which].second.invoke()
                if (loaded != null) {
                    controller.openSceneInEditor(loaded)
                }
            }
            .show()
    }

    fun onPauseHost() = surfaceView.onPause()
    fun onResumeHost() = surfaceView.onResume()
    fun onDestroyHost() = surfaceView.shutdownEngine()
}
