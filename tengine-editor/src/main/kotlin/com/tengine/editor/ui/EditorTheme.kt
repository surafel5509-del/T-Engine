package com.tengine.editor.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.widget.Button
import android.widget.TextView

/**
 * Dark-mode professional T-Engine Studio visual styling helpers (modeled after Unity 6 / JetBrains Fleet dark theme).
 */
object EditorTheme {
    val BG_DEEP = Color.parseColor("#111318")
    val BG_PANEL = Color.parseColor("#191D26")
    val BG_HEADER = Color.parseColor("#222734")
    val BG_CARD = Color.parseColor("#252B39")
    val BG_INPUT = Color.parseColor("#141720")
    val BORDER_SUBTLE = Color.parseColor("#323A4D")
    val ACCENT_BLUE = Color.parseColor("#2B8AF7")
    val ACCENT_CYAN = Color.parseColor("#1AD1E5")
    val ACCENT_GREEN = Color.parseColor("#29C46D")
    val ACCENT_AMBER = Color.parseColor("#F5A623")
    val ACCENT_RED = Color.parseColor("#EF4755")
    val PLAY_ACTIVE_BG = Color.parseColor("#1A4D33")

    val TEXT_PRIMARY = Color.parseColor("#ECEFF6")
    val TEXT_SECONDARY = Color.parseColor("#9BA5B8")
    val TEXT_MUTED = Color.parseColor("#687387")

    fun dp(context: Context, value: Float): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, context.resources.displayMetrics).toInt()

    fun roundedBox(
        context: Context,
        fillColor: Int,
        cornerDp: Float = 6f,
        strokeColor: Int = Color.TRANSPARENT,
        strokeDp: Float = 1f
    ): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(fillColor)
        cornerRadius = dp(context, cornerDp).toFloat()
        if (strokeColor != Color.TRANSPARENT) {
            setStroke(dp(context, strokeDp).coerceAtLeast(1), strokeColor)
        }
    }

    fun createToolButton(
        context: Context,
        label: String,
        bgColor: Int = BG_CARD,
        textColor: Int = TEXT_PRIMARY,
        onClick: () -> Unit
    ): Button = Button(context).apply {
        text = label
        isAllCaps = false
        setTextColor(textColor)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        minHeight = 0
        minimumHeight = 0
        minWidth = 0
        minimumWidth = 0
        val padH = dp(context, 10f)
        val padV = dp(context, 6f)
        setPadding(padH, padV, padH, padV)
        background = roundedBox(context, bgColor, cornerDp = 5f, strokeColor = BORDER_SUBTLE)
        setOnClickListener { onClick() }
    }

    fun sectionHeader(context: Context, title: String): TextView = TextView(context).apply {
        text = title
        setTextColor(TEXT_SECONDARY)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        setBackgroundColor(BG_HEADER)
        gravity = Gravity.CENTER_VERTICAL
        val padH = dp(context, 10f)
        val padV = dp(context, 6f)
        setPadding(padH, padV, padH, padV)
    }
}
