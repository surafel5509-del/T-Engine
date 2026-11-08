package com.tengine.editor.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.tengine.core.EngineLogger
import com.tengine.core.FrameProfiler
import com.tengine.core.LogLevel
import java.util.Locale

/**
 * Custom Sparkline Frame-Time Chart + Real-Time Engine Telemetry & Console Dock!
 */
@SuppressLint("ViewConstructor")
class ConsoleAndProfilerView(
    context: Context,
    private val profiler: FrameProfiler
) : LinearLayout(context) {

    private var showingProfiler: Boolean = true
    private val consoleText = TextView(context).apply {
        setTextColor(EditorTheme.TEXT_PRIMARY)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
        typeface = Typeface.MONOSPACE
        val pad = EditorTheme.dp(context, 8f)
        setPadding(pad, pad, pad, pad)
    }

    private val consoleScroll = ScrollView(context).apply {
        visibility = View.GONE
        addView(consoleText, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
    }

    private val metricsLabel = TextView(context).apply {
        setTextColor(EditorTheme.TEXT_PRIMARY)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
        typeface = Typeface.MONOSPACE
        val pad = EditorTheme.dp(context, 8f)
        setPadding(pad, pad, pad, pad)
    }

    private val sparklineView = FrameSparklineGraph(context, profiler)

    private val profilerContainer = LinearLayout(context).apply {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(metricsLabel, LayoutParams(0, LayoutParams.MATCH_PARENT, 1.25f))
        addView(sparklineView, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
    }

    init {
        orientation = VERTICAL
        setBackgroundColor(EditorTheme.BG_PANEL)

        val tabBar = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(EditorTheme.BG_HEADER)
            val pad = EditorTheme.dp(context, 4f)
            setPadding(pad * 2, pad, pad * 2, pad)
        }

        val profTabBtn = EditorTheme.createToolButton(context, "Profiler", bgColor = EditorTheme.ACCENT_BLUE) {
            showingProfiler = true
            profilerContainer.visibility = View.VISIBLE
            consoleScroll.visibility = View.GONE
            refreshTelemetry()
        }
        val consoleTabBtn = EditorTheme.createToolButton(context, "Console") {
            showingProfiler = false
            profilerContainer.visibility = View.GONE
            consoleScroll.visibility = View.VISIBLE
            refreshConsoleLogs()
        }.apply {
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                marginStart = EditorTheme.dp(context, 6f)
            }
        }
        val clearBtn = EditorTheme.createToolButton(context, "Clear Logs") {
            EngineLogger.clear()
            refreshConsoleLogs()
        }.apply {
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                marginStart = EditorTheme.dp(context, 6f)
            }
        }

        tabBar.addView(profTabBtn)
        tabBar.addView(consoleTabBtn)
        tabBar.addView(clearBtn)

        addView(tabBar, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        addView(profilerContainer, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        addView(consoleScroll, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
    }

    fun refreshTelemetry() {
        if (showingProfiler) {
            val s = profiler.latest
            metricsLabel.text = String.format(
                Locale.US,
                "FPS: %5.1f  | Frame: %5.2f ms (Phys %.2f / Upd %.2f / GPU %.2f)\n" +
                    "Draw Calls: %3d | Sprites: %4d | Tris: %5d | Particles: %4d\n" +
                    "Entities: %4d  | Rigidbodies: %3d | Active Contacts: %3d",
                s.fps, s.frameTimeMs, s.physicsTimeMs, s.updateTimeMs, s.renderTimeMs,
                s.drawCalls, s.spriteCount, s.triangleCount, s.activeParticles,
                s.activeGameObjects, s.physicsBodies, s.physicsContacts
            )
            sparklineView.invalidate()
        } else {
            refreshConsoleLogs()
        }
    }

    private fun refreshConsoleLogs() {
        val entries = EngineLogger.snapshot().takeLast(40)
        if (entries.isEmpty()) {
            consoleText.text = "[Console clean — no engine log entries]"
            return
        }
        val sb = StringBuilder()
        for (e in entries) {
            val badge = when (e.level) {
                LogLevel.ERROR -> "[ERR]"
                LogLevel.WARNING -> "[WRN]"
                LogLevel.INFO -> "[INF]"
                LogLevel.DEBUG -> "[DBG]"
            }
            sb.append(String.format(Locale.US, "%6.2fs %s [%s] %s\n", e.timestampSec, badge, e.tag, e.message))
        }
        consoleText.text = sb.toString()
    }

    @SuppressLint("ViewConstructor")
    private class FrameSparklineGraph(
        context: Context,
        private val profiler: FrameProfiler
    ) : View(context) {
        private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = EditorTheme.ACCENT_GREEN
            strokeWidth = EditorTheme.dp(context, 1.8f).toFloat()
            style = Paint.Style.STROKE
        }
        private val guidePaint = Paint().apply {
            color = EditorTheme.BORDER_SUBTLE
            strokeWidth = 2f
            style = Paint.Style.STROKE
        }
        private val path = Path()

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat()
            val h = height.toFloat()
            if (w <= 4f || h <= 4f) return

            // 16.6ms (60 FPS) target reference line
            val targetY = h - (16.6f / 33.3f) * h
            canvas.drawLine(0f, targetY, w, targetY, guidePaint)

            val history = profiler.historySnapshot()
            if (history.size < 2) return

            path.reset()
            for (i in history.indices) {
                val x = (i.toFloat() / (history.size - 1)) * w
                val norm = (history[i].frameTimeMs / 33.3f).coerceIn(0.02f, 0.98f)
                val y = h - norm * h
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            canvas.drawPath(path, linePaint)
        }
    }
}
