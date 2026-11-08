package com.tengine.core.ecs

import com.tengine.core.math.Color
import com.tengine.core.math.Vector2

/**
 * Reflection-free property descriptors enabling T-Engine Studio's Inspector Panel
 * to live-edit any built-in Component or custom gameplay Behaviour at 60 FPS.
 */
sealed class InspectorProperty(open val label: String) {

    class FloatProp(
        override val label: String,
        val min: Float = -10000f,
        val max: Float = 10000f,
        val step: Float = 0.1f,
        val getter: () -> Float,
        val setter: (Float) -> Unit
    ) : InspectorProperty(label)

    class IntProp(
        override val label: String,
        val min: Int = -100000,
        val max: Int = 100000,
        val getter: () -> Int,
        val setter: (Int) -> Unit
    ) : InspectorProperty(label)

    class BoolProp(
        override val label: String,
        val getter: () -> Boolean,
        val setter: (Boolean) -> Unit
    ) : InspectorProperty(label)

    class StringProp(
        override val label: String,
        val getter: () -> String,
        val setter: (String) -> Unit
    ) : InspectorProperty(label)

    class Vector2Prop(
        override val label: String,
        val step: Float = 0.1f,
        val getter: () -> Vector2,
        val setter: (Vector2) -> Unit
    ) : InspectorProperty(label)

    class ColorProp(
        override val label: String,
        val getter: () -> Color,
        val setter: (Color) -> Unit
    ) : InspectorProperty(label)

    class EnumProp(
        override val label: String,
        val options: List<String>,
        val getter: () -> String,
        val setter: (String) -> Unit
    ) : InspectorProperty(label)
}
