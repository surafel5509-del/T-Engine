package com.tengine.editor.ui

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.graphics.Typeface
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.tengine.core.ecs.Component
import com.tengine.core.ecs.ComponentRegistry
import com.tengine.core.ecs.GameObject
import com.tengine.core.ecs.InspectorProperty
import com.tengine.core.ecs.Transform2D
import com.tengine.core.math.Color
import com.tengine.core.math.Vector2
import com.tengine.editor.EditorController
import java.util.Locale

/**
 * Live Unity-style Component Inspector rendering editable controls for GameObject metadata,
 * Transform2D, and every attached Component's [InspectorProperty] descriptors + "+ Add Component"!
 */
@SuppressLint("ViewConstructor")
class InspectorPanelView(
    context: Context,
    private val controller: EditorController
) : LinearLayout(context) {

    private val contentContainer = LinearLayout(context).apply {
        orientation = VERTICAL
        val pad = EditorTheme.dp(context, 8f)
        setPadding(pad, pad, pad, pad)
    }

    private val colorSwatches = listOf(
        "Pure White" to Color.WHITE,
        "Neon Cyan" to Color.CYAN,
        "Electric Blue" to Color.BLUE,
        "Emerald Green" to Color.GREEN,
        "Solar Yellow" to Color.YELLOW,
        "Blaze Orange" to Color.ORANGE,
        "Crimson Red" to Color.RED,
        "Cyber Magenta" to Color.MAGENTA,
        "Royal Purple" to Color.PURPLE,
        "Dark Slate" to Color.DARK_GRAY
    )

    init {
        orientation = VERTICAL
        setBackgroundColor(EditorTheme.BG_PANEL)
        addView(
            EditorTheme.sectionHeader(context, "INSPECTOR"),
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        )

        val scroll = ScrollView(context).apply {
            addView(contentContainer, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        }
        addView(scroll, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
    }

    fun refreshInspector() {
        contentContainer.removeAllViews()
        val go = controller.selectedObject
        if (go == null || go.isDestroyed) {
            val emptyLabel = TextView(context).apply {
                text = "Select a GameObject in the Hierarchy or Scene Viewport to inspect & edit components."
                setTextColor(EditorTheme.TEXT_MUTED)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                val pad = EditorTheme.dp(context, 12f)
                setPadding(pad, pad, pad, pad)
            }
            contentContainer.addView(emptyLabel)
            return
        }

        // GameObject Header Card (Active toggle, Name, Tag, Layer)
        contentContainer.addView(buildGameObjectHeaderCard(go))

        // Render card for each attached Component
        for (comp in go.components.toList()) {
            if (!comp.isDestroyed) {
                contentContainer.addView(buildComponentCard(go, comp))
            }
        }

        // "+ Add Component" Button
        val addCompBtn = EditorTheme.createToolButton(
            context,
            label = "+ Add Component",
            bgColor = EditorTheme.ACCENT_BLUE
        ) {
            showAddComponentDialog(go)
        }.apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                topMargin = EditorTheme.dp(context, 8f)
            }
        }
        contentContainer.addView(addCompBtn)
    }

    private fun buildGameObjectHeaderCard(go: GameObject): View {
        val card = LinearLayout(context).apply {
            orientation = VERTICAL
            background = EditorTheme.roundedBox(context, EditorTheme.BG_CARD, cornerDp = 6f, strokeColor = EditorTheme.BORDER_SUBTLE)
            val pad = EditorTheme.dp(context, 8f)
            setPadding(pad, pad, pad, pad)
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = EditorTheme.dp(context, 8f)
            }
        }

        val topRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val activeCheck = CheckBox(context).apply {
            isChecked = go.activeSelf
            setOnCheckedChangeListener { _, checked ->
                controller.surfaceView.postToEngineThread {
                    go.activeSelf = checked
                }
            }
        }

        val nameBtn = EditorTheme.createToolButton(context, go.name, bgColor = EditorTheme.BG_INPUT) {
            promptStringEdit("Rename GameObject", go.name) { newName ->
                if (newName.isNotBlank()) {
                    go.name = newName.trim()
                    refreshInspector()
                    controller.onUiStateChanged?.invoke()
                }
            }
        }.apply {
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
        }

        topRow.addView(activeCheck)
        topRow.addView(nameBtn)
        card.addView(topRow)

        val metaRow = TextView(context).apply {
            text = "Tag: ${go.tag}   |   Layer: ${go.layer}"
            setTextColor(EditorTheme.TEXT_SECONDARY)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setPadding(EditorTheme.dp(context, 4f), EditorTheme.dp(context, 4f), 0, 0)
            setOnClickListener {
                val tags = arrayOf("Untagged", "Player", "Enemy", "Collectible", "Hazard", "Projectile")
                AlertDialog.Builder(context)
                    .setTitle("Assign Tag")
                    .setItems(tags) { _, idx ->
                        go.tag = tags[idx]
                        refreshInspector()
                    }
                    .show()
            }
        }
        card.addView(metaRow)
        return card
    }

    private fun buildComponentCard(go: GameObject, comp: Component): View {
        val card = LinearLayout(context).apply {
            orientation = VERTICAL
            background = EditorTheme.roundedBox(context, EditorTheme.BG_CARD, cornerDp = 6f, strokeColor = EditorTheme.BORDER_SUBTLE)
            val pad = EditorTheme.dp(context, 8f)
            setPadding(pad, pad, pad, pad)
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = EditorTheme.dp(context, 8f)
            }
        }

        val titleRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        if (comp !is Transform2D) {
            val enabledBox = CheckBox(context).apply {
                isChecked = comp.enabled
                setOnCheckedChangeListener { _, checked ->
                    controller.surfaceView.postToEngineThread { comp.enabled = checked }
                }
            }
            titleRow.addView(enabledBox)
        }

        val compTitle = TextView(context).apply {
            text = comp.typeName
            setTextColor(EditorTheme.ACCENT_CYAN)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
        }
        titleRow.addView(compTitle)

        if (comp !is Transform2D) {
            val removeBtn = EditorTheme.createToolButton(context, "✕", bgColor = EditorTheme.BG_INPUT, textColor = EditorTheme.ACCENT_RED) {
                controller.recordUndoSnapshot()
                controller.surfaceView.postToEngineThread {
                    go.removeComponent(comp)
                    post { refreshInspector() }
                }
            }
            titleRow.addView(removeBtn)
        }

        card.addView(titleRow)

        val props = comp.inspectableProperties()
        for (prop in props) {
            card.addView(buildPropertyRow(prop))
        }
        return card
    }

    private fun buildPropertyRow(prop: InspectorProperty): View {
        val row = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, EditorTheme.dp(context, 4f), 0, EditorTheme.dp(context, 4f))
        }

        val labelView = TextView(context).apply {
            text = prop.label
            setTextColor(EditorTheme.TEXT_SECONDARY)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
            layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 0.44f)
        }
        row.addView(labelView)

        val controlWrap = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 0.56f)
        }

        when (prop) {
            is InspectorProperty.FloatProp -> {
                val minusBtn = EditorTheme.createToolButton(context, "−") {
                    controller.recordUndoSnapshot()
                    val next = (prop.getter() - prop.step).coerceIn(prop.min, prop.max)
                    controller.surfaceView.postToEngineThread { prop.setter(next) }
                    postDelayed({ refreshInspector() }, 30)
                }
                val valBtn = EditorTheme.createToolButton(
                    context,
                    String.format(Locale.US, "%.2f", prop.getter()),
                    bgColor = EditorTheme.BG_INPUT
                ) {
                    promptFloatEdit(prop.label, prop.getter()) { entered ->
                        controller.recordUndoSnapshot()
                        controller.surfaceView.postToEngineThread {
                            prop.setter(entered.coerceIn(prop.min, prop.max))
                        }
                        postDelayed({ refreshInspector() }, 30)
                    }
                }.apply {
                    layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply {
                        marginStart = EditorTheme.dp(context, 3f)
                        marginEnd = EditorTheme.dp(context, 3f)
                    }
                }
                val plusBtn = EditorTheme.createToolButton(context, "+") {
                    controller.recordUndoSnapshot()
                    val next = (prop.getter() + prop.step).coerceIn(prop.min, prop.max)
                    controller.surfaceView.postToEngineThread { prop.setter(next) }
                    postDelayed({ refreshInspector() }, 30)
                }
                controlWrap.addView(minusBtn)
                controlWrap.addView(valBtn)
                controlWrap.addView(plusBtn)
            }

            is InspectorProperty.IntProp -> {
                val minusBtn = EditorTheme.createToolButton(context, "−") {
                    controller.recordUndoSnapshot()
                    val next = (prop.getter() - 1).coerceIn(prop.min, prop.max)
                    controller.surfaceView.postToEngineThread { prop.setter(next) }
                    postDelayed({ refreshInspector() }, 30)
                }
                val valBtn = EditorTheme.createToolButton(
                    context,
                    prop.getter().toString(),
                    bgColor = EditorTheme.BG_INPUT
                ) {
                    promptFloatEdit(prop.label, prop.getter().toFloat()) { entered ->
                        controller.surfaceView.postToEngineThread {
                            prop.setter(entered.toInt().coerceIn(prop.min, prop.max))
                        }
                        postDelayed({ refreshInspector() }, 30)
                    }
                }.apply {
                    layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply {
                        marginStart = EditorTheme.dp(context, 3f)
                        marginEnd = EditorTheme.dp(context, 3f)
                    }
                }
                val plusBtn = EditorTheme.createToolButton(context, "+") {
                    controller.recordUndoSnapshot()
                    val next = (prop.getter() + 1).coerceIn(prop.min, prop.max)
                    controller.surfaceView.postToEngineThread { prop.setter(next) }
                    postDelayed({ refreshInspector() }, 30)
                }
                controlWrap.addView(minusBtn)
                controlWrap.addView(valBtn)
                controlWrap.addView(plusBtn)
            }

            is InspectorProperty.BoolProp -> {
                val cb = CheckBox(context).apply {
                    isChecked = prop.getter()
                    setOnCheckedChangeListener { _, checked ->
                        controller.recordUndoSnapshot()
                        controller.surfaceView.postToEngineThread { prop.setter(checked) }
                    }
                }
                controlWrap.addView(cb)
            }

            is InspectorProperty.StringProp -> {
                val strBtn = EditorTheme.createToolButton(context, prop.getter().ifEmpty { "(empty)" }, bgColor = EditorTheme.BG_INPUT) {
                    promptStringEdit(prop.label, prop.getter()) { v ->
                        controller.recordUndoSnapshot()
                        controller.surfaceView.postToEngineThread { prop.setter(v) }
                        postDelayed({ refreshInspector() }, 30)
                    }
                }
                controlWrap.addView(strBtn, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
            }

            is InspectorProperty.Vector2Prop -> {
                val cur = prop.getter()
                val xBtn = EditorTheme.createToolButton(
                    context,
                    String.format(Locale.US, "X:%.1f", cur.x),
                    bgColor = EditorTheme.BG_INPUT
                ) {
                    promptFloatEdit("${prop.label} X", cur.x) { nx ->
                        controller.recordUndoSnapshot()
                        controller.surfaceView.postToEngineThread {
                            prop.setter(Vector2(nx, prop.getter().y))
                        }
                        postDelayed({ refreshInspector() }, 30)
                    }
                }.apply {
                    layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply {
                        marginEnd = EditorTheme.dp(context, 4f)
                    }
                }
                val yBtn = EditorTheme.createToolButton(
                    context,
                    String.format(Locale.US, "Y:%.1f", cur.y),
                    bgColor = EditorTheme.BG_INPUT
                ) {
                    promptFloatEdit("${prop.label} Y", cur.y) { ny ->
                        controller.recordUndoSnapshot()
                        controller.surfaceView.postToEngineThread {
                            prop.setter(Vector2(prop.getter().x, ny))
                        }
                        postDelayed({ refreshInspector() }, 30)
                    }
                }.apply {
                    layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
                }
                controlWrap.addView(xBtn)
                controlWrap.addView(yBtn)
            }

            is InspectorProperty.ColorProp -> {
                val col = prop.getter()
                val swatchBtn = EditorTheme.createToolButton(
                    context,
                    col.toHex(includeAlpha = false),
                    bgColor = col.toArgbInt(),
                    textColor = if (col.r + col.g + col.b > 1.6f) android.graphics.Color.BLACK else android.graphics.Color.WHITE
                ) {
                    val labels = colorSwatches.map { it.first }.toTypedArray()
                    AlertDialog.Builder(context)
                        .setTitle(prop.label)
                        .setItems(labels) { _, which ->
                            controller.recordUndoSnapshot()
                            val chosen = colorSwatches[which].second
                            controller.surfaceView.postToEngineThread { prop.setter(chosen) }
                            postDelayed({ refreshInspector() }, 30)
                        }
                        .show()
                }
                controlWrap.addView(swatchBtn, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
            }

            is InspectorProperty.EnumProp -> {
                val dropBtn = EditorTheme.createToolButton(context, "${prop.getter()} ▾", bgColor = EditorTheme.BG_INPUT) {
                    val arr = prop.options.toTypedArray()
                    AlertDialog.Builder(context)
                        .setTitle(prop.label)
                        .setItems(arr) { _, which ->
                            controller.recordUndoSnapshot()
                            controller.surfaceView.postToEngineThread { prop.setter(arr[which]) }
                            postDelayed({ refreshInspector() }, 30)
                        }
                        .show()
                }
                controlWrap.addView(dropBtn, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
            }
        }

        row.addView(controlWrap)
        return row
    }

    private fun showAddComponentDialog(go: GameObject) {
        val regs = ComponentRegistry.allRegistrations()
        val labels = regs.map { "[${it.category}]  ${it.typeName}" }.toTypedArray()
        AlertDialog.Builder(context)
            .setTitle("Add Component to '${go.name}'")
            .setItems(labels) { _, which ->
                val reg = regs[which]
                controller.recordUndoSnapshot()
                controller.surfaceView.postToEngineThread {
                    go.addComponent(reg.factory())
                    post { refreshInspector() }
                }
            }
            .show()
    }

    private fun promptFloatEdit(title: String, currentValue: Float, onResult: (Float) -> Unit) {
        val input = EditText(context).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED
            setText(currentValue.toString())
            selectAll()
        }
        AlertDialog.Builder(context)
            .setTitle(title)
            .setView(input)
            .setPositiveButton("Apply") { _, _ ->
                input.text.toString().toFloatOrNull()?.let(onResult)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun promptStringEdit(title: String, currentValue: String, onResult: (String) -> Unit) {
        val input = EditText(context).apply {
            setText(currentValue)
            selectAll()
        }
        AlertDialog.Builder(context)
            .setTitle(title)
            .setView(input)
            .setPositiveButton("Apply") { _, _ ->
                onResult(input.text.toString())
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
