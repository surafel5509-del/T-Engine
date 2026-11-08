package com.tengine.core.graphics

/**
 * Named Sorting Layers matching Unity's 2D Renderer sorting pipeline.
 */
enum class SortingLayer(val priority: Int) {
    BACKGROUND(-200),
    PARALLAX(-100),
    TERRAIN(0),
    DEFAULT(100),
    ENTITIES(200),
    EFFECTS(300),
    FOREGROUND(400),
    OVERLAY(500);

    companion object {
        fun fromName(name: String): SortingLayer =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: DEFAULT
    }
}
