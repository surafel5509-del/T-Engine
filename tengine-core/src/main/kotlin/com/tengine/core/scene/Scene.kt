package com.tengine.core.scene

import com.tengine.core.audio.AudioMixer
import com.tengine.core.ecs.Component
import com.tengine.core.ecs.GameObject
import com.tengine.core.graphics.Camera2D
import com.tengine.core.math.Vector2
import com.tengine.core.physics.Collider2D
import com.tengine.core.physics.Joint2D
import com.tengine.core.physics.PhysicsWorld2D
import com.tengine.core.physics.Rigidbody2D
import com.tengine.core.tween.TweenManager
import com.tengine.core.ui.UICanvas
import com.tengine.core.utils.CoroutineScheduler
import com.tengine.core.utils.EventBus
import kotlin.reflect.KClass

/**
 * Self-contained 2D Game World (modeled after UnityEngine.SceneManagement.Scene).
 * Owns root GameObjects, PhysicsWorld2D, TweenManager, CoroutineScheduler, EventBus, and UICanvases.
 */
class Scene(
    var name: String = "Untitled Scene",
    gravity: Vector2 = Vector2(0f, -18f)
) {
    val physicsWorld = PhysicsWorld2D(gravity = gravity)
    val scheduler = CoroutineScheduler()
    val tweens = TweenManager()
    val events = EventBus()
    var audioMixer: AudioMixer = AudioMixer()

    private val _rootObjects = ArrayList<GameObject>()
    val rootObjects: List<GameObject> get() = _rootObjects

    private val pendingDestroy = LinkedHashSet<GameObject>()
    val uiCanvases = ArrayList<UICanvas>()

    var isInitialized: Boolean = false
        private set

    var viewportWidth: Int = 1920
        private set
    var viewportHeight: Int = 1080
        private set

    fun createGameObject(name: String = "GameObject", position: Vector2 = Vector2.ZERO): GameObject {
        val go = GameObject(name)
        go.transform.localPosition = position
        addGameObject(go)
        return go
    }

    fun addGameObject(go: GameObject) {
        if (go.scene === this) return
        go.scene = this
        if (go.transform.parent == null && !_rootObjects.contains(go)) {
            _rootObjects.add(go)
        }
        registerHierarchyRecursive(go)
    }

    private fun registerHierarchyRecursive(go: GameObject) {
        go.scene = this
        for (comp in go.components) {
            onComponentAdded(go, comp)
            if (isInitialized && !comp.awakened) {
                comp.awakened = true
                comp.onAwake()
            }
            if (isInitialized) {
                comp.refreshEnabledLifecycle()
            }
        }
        for (child in go.transform.children) {
            registerHierarchyRecursive(child.gameObject)
        }
    }

    internal fun notifyRootChanged(go: GameObject, isNowRoot: Boolean) {
        if (isNowRoot) {
            if (!_rootObjects.contains(go)) _rootObjects.add(go)
        } else {
            _rootObjects.remove(go)
        }
    }

    internal fun onComponentAdded(go: GameObject, comp: Component) {
        when (comp) {
            is Rigidbody2D -> physicsWorld.registerBody(comp)
            is Collider2D -> physicsWorld.registerCollider(comp)
            is Joint2D -> physicsWorld.registerJoint(comp)
        }
    }

    internal fun onComponentRemoved(go: GameObject, comp: Component) {
        when (comp) {
            is Rigidbody2D -> physicsWorld.unregisterBody(comp)
            is Collider2D -> physicsWorld.unregisterCollider(comp)
            is Joint2D -> physicsWorld.unregisterJoint(comp)
        }
    }

    fun destroy(go: GameObject) {
        pendingDestroy.add(go)
    }

    fun destroyImmediate(go: GameObject) {
        pendingDestroy.remove(go)
        _rootObjects.remove(go)
        go.performImmediateDestroy()
    }

    fun addCanvas(canvas: UICanvas): UICanvas {
        uiCanvases.add(canvas)
        return canvas
    }

    // =========================================================================
    // Lifecycle Execution
    // =========================================================================

    fun initializeScene() {
        if (isInitialized) return
        isInitialized = true
        val snapshot = allGameObjects(includeInactive = true)
        for (go in snapshot) {
            for (comp in go.components.toList()) {
                if (!comp.awakened) {
                    comp.awakened = true
                    comp.onAwake()
                }
                comp.refreshEnabledLifecycle()
            }
        }
    }

    private fun ensurePendingStarts(activeList: List<GameObject>) {
        for (i in 0 until activeList.size) {
            val comps = activeList[i].components
            for (j in 0 until comps.size) {
                val c = comps[j]
                if (c.isActiveAndEnabled && !c.started) {
                    c.started = true
                    c.onStart()
                }
            }
        }
    }

    fun stepFixedUpdate(fixedDt: Float) {
        val active = allActiveGameObjects()
        ensurePendingStarts(active)
        for (i in 0 until active.size) {
            val comps = active[i].components
            for (j in 0 until comps.size) {
                val c = comps[j]
                if (c.isActiveAndEnabled) {
                    c.onFixedUpdate(fixedDt)
                }
            }
        }
        physicsWorld.step(fixedDt)
        flushPendingDestroys()
    }

    fun stepUpdate(dt: Float, viewportW: Int = 1920, viewportH: Int = 1080, updateUiInput: Boolean = false) {
        viewportWidth = viewportW.coerceAtLeast(1)
        viewportHeight = viewportH.coerceAtLeast(1)
        if (updateUiInput) {
            for (i in 0 until uiCanvases.size) {
                uiCanvases[i].updateInput(viewportW, viewportH)
            }
        }

        val active = allActiveGameObjects()
        ensurePendingStarts(active)

        for (i in 0 until active.size) {
            val comps = active[i].components
            for (j in 0 until comps.size) {
                val c = comps[j]
                if (c.isActiveAndEnabled) {
                    c.onUpdate(dt)
                }
            }
        }

        scheduler.update(dt)
        tweens.update(dt)

        val activeLate = allActiveGameObjects()
        for (i in 0 until activeLate.size) {
            val comps = activeLate[i].components
            for (j in 0 until comps.size) {
                val c = comps[j]
                if (c.isActiveAndEnabled) {
                    c.onLateUpdate(dt)
                }
            }
        }

        flushPendingDestroys()
    }

    fun flushPendingDestroys() {
        if (pendingDestroy.isEmpty()) return
        val toRemove = pendingDestroy.toList()
        pendingDestroy.clear()
        for (go in toRemove) {
            _rootObjects.remove(go)
            go.performImmediateDestroy()
        }
    }

    // =========================================================================
    // Queries & Hierarchy Traversal
    // =========================================================================

    fun allActiveGameObjects(): List<GameObject> {
        val out = ArrayList<GameObject>(_rootObjects.size * 2)
        for (i in 0 until _rootObjects.size) {
            collectActiveRecursive(_rootObjects[i], out)
        }
        return out
    }

    private fun collectActiveRecursive(go: GameObject, out: MutableList<GameObject>) {
        if (!go.activeInHierarchy || go.isDestroyed) return
        out.add(go)
        val children = go.transform.children
        for (i in 0 until children.size) {
            collectActiveRecursive(children[i].gameObject, out)
        }
    }

    fun allGameObjects(includeInactive: Boolean = true): List<GameObject> {
        val out = ArrayList<GameObject>(_rootObjects.size * 2)
        for (i in 0 until _rootObjects.size) {
            collectAllRecursive(_rootObjects[i], includeInactive, out)
        }
        return out
    }

    private fun collectAllRecursive(go: GameObject, includeInactive: Boolean, out: MutableList<GameObject>) {
        if (go.isDestroyed) return
        if (!includeInactive && !go.activeInHierarchy) return
        out.add(go)
        for (child in go.transform.children) {
            collectAllRecursive(child.gameObject, includeInactive, out)
        }
    }

    fun find(name: String): GameObject? =
        allGameObjects(includeInactive = true).firstOrNull { it.name == name }

    fun findByUuid(uuid: String): GameObject? =
        allGameObjects(includeInactive = true).firstOrNull { it.uuid == uuid }

    fun findWithTag(tag: String): GameObject? =
        allActiveGameObjects().firstOrNull { it.tag == tag }

    fun findAllWithTag(tag: String): List<GameObject> =
        allActiveGameObjects().filter { it.tag == tag }

    fun <T : Component> findComponent(type: KClass<T>): T? {
        for (go in allActiveGameObjects()) {
            go.getComponent(type)?.let { return it }
        }
        return null
    }

    inline fun <reified T : Component> findComponent(): T? = findComponent(T::class)

    fun findMainCamera(): Camera2D? {
        var fallback: Camera2D? = null
        for (go in allActiveGameObjects()) {
            val cam = go.getComponent(Camera2D::class) ?: continue
            if (!cam.enabled) continue
            if (cam.isMainCamera) return cam
            if (fallback == null) fallback = cam
        }
        return fallback
    }

    fun unload() {
        for (go in _rootObjects.toList()) {
            go.performImmediateDestroy()
        }
        _rootObjects.clear()
        pendingDestroy.clear()
        physicsWorld.clear()
        scheduler.clear()
        tweens.clear()
        events.clear()
        uiCanvases.clear()
        isInitialized = false
    }
}
