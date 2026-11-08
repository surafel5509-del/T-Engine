package com.tengine.core.ecs

import com.tengine.core.scene.Scene
import kotlin.reflect.KClass

/**
 * Fundamental entity container in T-Engine Scenes (modeled after Unity's GameObject).
 * Owns a [Transform2D], tag, 32-bit layer index, active hierarchy state, and attached [Component] list.
 */
class GameObject(
    name: String = "GameObject",
    uuid: String? = null
) : TObject(name, uuid) {

    var tag: String = "Untagged"

    /** Layer index in [0..31] used for camera culling and 32x32 physics collision matrix. */
    var layer: Int = 0
        set(value) {
            field = value.coerceIn(0, 31)
        }

    var isStatic: Boolean = false

    var scene: Scene? = null
        internal set

    val transform: Transform2D = Transform2D()

    private val _components = mutableListOf<Component>()
    val components: List<Component> get() = _components

    var activeSelf: Boolean = true
        set(value) {
            if (field == value) return
            field = value
            refreshHierarchyActiveState()
        }

    var activeInHierarchy: Boolean = true
        private set

    init {
        transform.gameObject = this
        transform.awakened = true
        transform.currentlyEnabledInHierarchy = true
        _components.add(transform)
    }

    internal fun refreshHierarchyActiveState() {
        val parentActive = transform.parent?.gameObject?.activeInHierarchy ?: true
        val newActive = activeSelf && parentActive && !isDestroyed
        activeInHierarchy = newActive
        for (i in 0 until _components.size) {
            _components[i].refreshEnabledLifecycle()
        }
        val children = transform.children
        for (i in 0 until children.size) {
            children[i].gameObject.refreshHierarchyActiveState()
        }
    }

    fun <T : Component> addComponent(component: T): T {
        require(component !is Transform2D) { "GameObject already owns a primary Transform2D" }
        component.gameObject = this
        _components.add(component)
        scene?.onComponentAdded(this, component)
        if (scene != null && scene!!.isInitialized) {
            if (!component.awakened) {
                component.awakened = true
                component.onAwake()
            }
            component.refreshEnabledLifecycle()
        }
        return component
    }

    inline fun <reified T : Component> addComponent(factory: () -> T): T = addComponent(factory())

    fun removeComponent(component: Component): Boolean {
        if (component === transform) return false
        if (!_components.remove(component)) return false
        if (component.currentlyEnabledInHierarchy) {
            component.currentlyEnabledInHierarchy = false
            component.onDisable()
        }
        component.isDestroyed = true
        component.onDestroy()
        scene?.onComponentRemoved(this, component)
        return true
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Component> getComponent(type: KClass<T>): T? {
        for (i in 0 until _components.size) {
            val c = _components[i]
            if (!c.isDestroyed && type.isInstance(c)) {
                return c as T
            }
        }
        return null
    }

    inline fun <reified T : Component> getComponent(): T? = getComponent(T::class)

    @Suppress("UNCHECKED_CAST")
    fun <T : Component> getComponents(type: KClass<T>): List<T> {
        val result = mutableListOf<T>()
        for (i in 0 until _components.size) {
            val c = _components[i]
            if (!c.isDestroyed && type.isInstance(c)) {
                result.add(c as T)
            }
        }
        return result
    }

    inline fun <reified T : Component> getComponents(): List<T> = getComponents(T::class)

    fun <T : Component> getComponentInChildren(type: KClass<T>, includeInactive: Boolean = false): T? {
        if (includeInactive || activeInHierarchy) {
            getComponent(type)?.let { return it }
        }
        for (childTransform in transform.children) {
            childTransform.gameObject.getComponentInChildren(type, includeInactive)?.let { return it }
        }
        return null
    }

    inline fun <reified T : Component> getComponentInChildren(includeInactive: Boolean = false): T? =
        getComponentInChildren(T::class, includeInactive)

    fun <T : Component> getComponentInParent(type: KClass<T>): T? {
        var cursor: GameObject? = this
        while (cursor != null) {
            cursor.getComponent(type)?.let { return it }
            cursor = cursor.transform.parent?.gameObject
        }
        return null
    }

    inline fun <reified T : Component> getComponentInParent(): T? = getComponentInParent(T::class)

    /**
     * Broadcasts a named message to all enabled components on this GameObject (and optionally children).
     */
    fun sendMessage(messageName: String, payload: Any? = null, broadcastToChildren: Boolean = false) {
        val snapshot = _components.toList()
        for (comp in snapshot) {
            if (comp.isActiveAndEnabled) {
                comp.onMessage(messageName, payload)
            }
        }
        if (broadcastToChildren) {
            for (child in transform.children.toList()) {
                child.gameObject.sendMessage(messageName, payload, broadcastToChildren = true)
            }
        }
    }

    fun destroy() {
        scene?.destroy(this)
    }

    internal fun performImmediateDestroy() {
        if (isDestroyed) return
        isDestroyed = true
        // Destroy children first depth-first
        for (child in transform.children.toList()) {
            child.gameObject.performImmediateDestroy()
        }
        transform.setParent(null, keepWorldPosition = false)
        for (i in _components.size - 1 downTo 0) {
            val comp = _components[i]
            if (comp.currentlyEnabledInHierarchy) {
                comp.currentlyEnabledInHierarchy = false
                comp.onDisable()
            }
            comp.isDestroyed = true
            comp.onDestroy()
            scene?.onComponentRemoved(this, comp)
        }
        _components.clear()
    }
}
