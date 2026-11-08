package com.tengine.core.ecs

import com.tengine.core.graphics.ShapeRenderer
import com.tengine.core.physics.Collider2D
import com.tengine.core.physics.CollisionEvent2D
import com.tengine.core.scene.Scene
import com.tengine.core.serialization.JsonObject
import kotlin.reflect.KClass

/**
 * Base class for everything attached to a [GameObject] in T-Engine.
 * Provides full Unity-style lifecycle callbacks, physics callbacks, gizmo rendering,
 * JSON serialization, and Inspector property introspection.
 */
abstract class Component(componentName: String = "Component") : TObject(componentName) {

    lateinit var gameObject: GameObject
        internal set

    val isAttached: Boolean
        get() = ::gameObject.isInitialized

    val transform: Transform2D
        get() = gameObject.transform

    val scene: Scene?
        get() = if (isAttached) gameObject.scene else null

    open val typeName: String
        get() = this::class.simpleName ?: "Component"

    internal var awakened: Boolean = false
    internal var started: Boolean = false
    internal var currentlyEnabledInHierarchy: Boolean = false

    var enabled: Boolean = true
        set(value) {
            if (field == value) return
            field = value
            if (isAttached) {
                refreshEnabledLifecycle()
            }
        }

    val isActiveAndEnabled: Boolean
        get() = enabled && isAttached && gameObject.activeInHierarchy && !isDestroyed

    internal fun refreshEnabledLifecycle() {
        val shouldBeActive = isActiveAndEnabled && awakened
        if (shouldBeActive && !currentlyEnabledInHierarchy) {
            currentlyEnabledInHierarchy = true
            onEnable()
        } else if (!shouldBeActive && currentlyEnabledInHierarchy) {
            currentlyEnabledInHierarchy = false
            onDisable()
        }
    }

    fun <T : Component> getComponent(type: KClass<T>): T? = gameObject.getComponent(type)

    inline fun <reified T : Component> getComponent(): T? = getComponent(T::class)

    fun <T : Component> getComponents(type: KClass<T>): List<T> = gameObject.getComponents(type)

    inline fun <reified T : Component> getComponents(): List<T> = getComponents(T::class)

    // Lifecycle hooks
    open fun onAwake() {}
    open fun onEnable() {}
    open fun onStart() {}
    open fun onFixedUpdate(fixedDt: Float) {}
    open fun onUpdate(dt: Float) {}
    open fun onLateUpdate(dt: Float) {}
    open fun onDisable() {}
    open fun onDestroy() {}

    // 2D Physics Collision & Trigger callbacks
    open fun onCollisionEnter2D(collision: CollisionEvent2D) {}
    open fun onCollisionStay2D(collision: CollisionEvent2D) {}
    open fun onCollisionExit2D(collision: CollisionEvent2D) {}

    open fun onTriggerEnter2D(other: Collider2D) {}
    open fun onTriggerStay2D(other: Collider2D) {}
    open fun onTriggerExit2D(other: Collider2D) {}

    // Message Receiver
    open fun onMessage(messageName: String, payload: Any?) {}

    // Editor Scene View Gizmo hook
    open fun onDrawGizmos(gizmos: ShapeRenderer, selected: Boolean) {}

    // Serialization
    open fun serialize(): JsonObject = JsonObject().put("enabled", enabled)

    open fun deserialize(data: JsonObject) {
        enabled = data.getBoolean("enabled", true)
    }

    // Inspector UI properties
    open fun inspectableProperties(): List<InspectorProperty> = emptyList()
}
