package com.tengine.editor.ui

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.tengine.core.ecs.GameObject
import com.tengine.editor.EditorController
import com.tengine.editor.templates.GameObjectTemplates

/**
 * Unity-style Scene Hierarchy Panel displaying recursive GameObject parent-child trees,
 * active toggles, selection highlighting, and one-tap Entity creation/duplication/deletion!
 */
@SuppressLint("ViewConstructor")
class HierarchyPanelView(
    context: Context,
    private val controller: EditorController
) : LinearLayout(context) {

    private val listContainer = LinearLayout(context).apply {
        orientation = VERTICAL
    }

    init {
        orientation = VERTICAL
        setBackgroundColor(EditorTheme.BG_PANEL)

        // Header + "+ Add" button row
        val headerRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(EditorTheme.BG_HEADER)
            val pad = EditorTheme.dp(context, 6f)
            setPadding(pad * 2, pad, pad, pad)
        }

        val title = TextView(context).apply {
            text = "HIERARCHY"
            setTextColor(EditorTheme.TEXT_SECONDARY)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
        }

        val addBtn = EditorTheme.createToolButton(
            context,
            label = "+ Add",
            bgColor = EditorTheme.ACCENT_BLUE
        ) {
            showSpawnTemplateDialog()
        }

        headerRow.addView(title)
        headerRow.addView(addBtn)
        addView(headerRow, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        // Action row (Focus, Duplicate, Delete)
        val actionsRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            val pad = EditorTheme.dp(context, 4f)
            setPadding(pad, pad, pad, pad)
        }
        val btnParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply {
            marginEnd = EditorTheme.dp(context, 3f)
        }
        actionsRow.addView(EditorTheme.createToolButton(context, "Focus") {
            controller.focusCameraOnSelected()
        }, btnParams)
        actionsRow.addView(EditorTheme.createToolButton(context, "Clone") {
            controller.duplicateSelected()
        }, btnParams)
        actionsRow.addView(EditorTheme.createToolButton(context, "Del", bgColor = EditorTheme.ACCENT_RED) {
            controller.deleteSelected()
        }, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        addView(actionsRow, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        val scroll = ScrollView(context).apply {
            addView(listContainer, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        }
        addView(scroll, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
    }

    fun refreshTree() {
        listContainer.removeAllViews()
        val scene = controller.surfaceView.engine.activeScene ?: return
        val roots = scene.rootObjects
        for (root in roots) {
            appendNodeRecursive(root, depth = 0)
        }
    }

    private fun appendNodeRecursive(go: GameObject, depth: Int) {
        if (go.isDestroyed) return
        val isSelected = go === controller.selectedObject

        val row = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val leftIndent = EditorTheme.dp(context, (8 + depth * 14).toFloat())
            val vPad = EditorTheme.dp(context, 7f)
            setPadding(leftIndent, vPad, EditorTheme.dp(context, 8f), vPad)
            setBackgroundColor(if (isSelected) EditorTheme.ACCENT_BLUE else android.graphics.Color.TRANSPARENT)
            setOnClickListener {
                controller.selectObject(go)
            }
        }

        val statusIcon = if (go.activeInHierarchy) "● " else "○ "
        val label = TextView(context).apply {
            text = "$statusIcon${go.name}"
            setTextColor(
                when {
                    isSelected -> android.graphics.Color.WHITE
                    go.activeInHierarchy -> EditorTheme.TEXT_PRIMARY
                    else -> EditorTheme.TEXT_MUTED
                }
            )
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            maxLines = 1
        }

        row.addView(label, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        listContainer.addView(row, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        for (child in go.transform.children) {
            appendNodeRecursive(child.gameObject, depth + 1)
        }
    }

    private fun showSpawnTemplateDialog() {
        val options = GameObjectTemplates.templateNames.toTypedArray()
        AlertDialog.Builder(context)
            .setTitle("Create 2D GameObject")
            .setItems(options) { _, which ->
                controller.spawnTemplate(options[which])
            }
            .show()
    }
}
