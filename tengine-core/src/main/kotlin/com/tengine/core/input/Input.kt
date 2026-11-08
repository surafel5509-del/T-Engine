package com.tengine.core.input

import com.tengine.core.math.Mathf
import com.tengine.core.math.Vector2

enum class TouchPhase {
    BEGAN,
    MOVED,
    STATIONARY,
    ENDED,
    CANCELLED
}

data class TouchPointer(
    val pointerId: Int,
    var phase: TouchPhase,
    var x: Float,
    var y: Float,
    val startX: Float,
    val startY: Float,
    var deltaX: Float = 0f,
    var deltaY: Float = 0f
)

enum class KeyCode {
    UNKNOWN,
    LEFT, RIGHT, UP, DOWN,
    W, A, S, D,
    SPACE, ENTER, ESCAPE, SHIFT,
    KEY_1, KEY_2, KEY_3, KEY_4,
    GAMEPAD_A, GAMEPAD_B, GAMEPAD_X, GAMEPAD_Y,
    GAMEPAD_LB, GAMEPAD_RB, GAMEPAD_START
}

/**
 * Thread-safe Cross-Platform Multi-Touch, Hardware Controller, Accelerometer, Virtual Stick,
 * and Unity-style Action/Axis Input System.
 */
object Input {

    private data class QueuedTouch(
        val pointerId: Int,
        val phase: TouchPhase,
        val x: Float,
        val y: Float
    )

    private data class QueuedKey(val key: KeyCode, val down: Boolean)

    private val eventLock = Any()
    private val touchQueue = ArrayDeque<QueuedTouch>(64)
    private val keyQueue = ArrayDeque<QueuedKey>(32)

    private val activeTouches = LinkedHashMap<Int, TouchPointer>()
    private val keysDown = HashSet<KeyCode>()
    private val keysJustPressed = HashSet<KeyCode>()
    private val keysJustReleased = HashSet<KeyCode>()

    // Action button states driven by UI Virtual Action Buttons + Key Bindings
    private val actionBindings = LinkedHashMap<String, MutableSet<KeyCode>>()
    private val virtualActionsDown = HashSet<String>()
    private val prevActionsDown = HashSet<String>()
    private val currentActionsDown = HashSet<String>()

    // Virtual joysticks & hardware gamepad stick
    private val primaryVirtualStick = Vector2()
    private val secondaryVirtualStick = Vector2()
    private val hardwareLeftStick = Vector2()
    val accelerometer = Vector2()

    init {
        resetDefaultBindings()
    }

    fun resetDefaultBindings() {
        actionBindings.clear()
        bindAction("Jump", KeyCode.SPACE, KeyCode.W, KeyCode.UP, KeyCode.GAMEPAD_A)
        bindAction("Dash", KeyCode.SHIFT, KeyCode.GAMEPAD_B)
        bindAction("Attack", KeyCode.ENTER, KeyCode.GAMEPAD_X)
        bindAction("Special", KeyCode.KEY_1, KeyCode.GAMEPAD_Y)
        bindAction("Pause", KeyCode.ESCAPE, KeyCode.GAMEPAD_START)
    }

    fun bindAction(actionName: String, vararg keys: KeyCode) {
        val set = actionBindings.getOrPut(actionName) { LinkedHashSet() }
        set.addAll(keys)
    }

    // Thread-safe ingestion from Android UI thread -> GL Engine thread
    fun enqueueTouch(pointerId: Int, phase: TouchPhase, x: Float, y: Float) {
        synchronized(eventLock) {
            touchQueue.addLast(QueuedTouch(pointerId, phase, x, y))
        }
    }

    fun enqueueKey(key: KeyCode, isDown: Boolean) {
        synchronized(eventLock) {
            keyQueue.addLast(QueuedKey(key, isDown))
        }
    }

    fun setHardwareLeftStick(x: Float, y: Float) {
        hardwareLeftStick.set(Mathf.clamp(x, -1f, 1f), Mathf.clamp(y, -1f, 1f))
    }

    fun setAccelerometerTilt(tiltX: Float, tiltY: Float) {
        accelerometer.set(tiltX, tiltY)
    }

    fun setVirtualJoystick(x: Float, y: Float, secondary: Boolean = false) {
        if (!secondary) {
            primaryVirtualStick.set(Mathf.clamp(x, -1f, 1f), Mathf.clamp(y, -1f, 1f))
        } else {
            secondaryVirtualStick.set(Mathf.clamp(x, -1f, 1f), Mathf.clamp(y, -1f, 1f))
        }
    }

    fun setVirtualActionState(actionName: String, pressed: Boolean) {
        if (pressed) virtualActionsDown.add(actionName) else virtualActionsDown.remove(actionName)
    }

    /**
     * Called once at the start of every Engine frame before UI & Behaviour updates.
     */
    internal fun beginFrame() {
        keysJustPressed.clear()
        keysJustReleased.clear()

        // Remove touches that finished last frame; transition stationary touches
        val iter = activeTouches.entries.iterator()
        while (iter.hasNext()) {
            val t = iter.next().value
            when (t.phase) {
                TouchPhase.ENDED, TouchPhase.CANCELLED -> iter.remove()
                TouchPhase.BEGAN, TouchPhase.MOVED -> {
                    t.phase = TouchPhase.STATIONARY
                    t.deltaX = 0f
                    t.deltaY = 0f
                }
                TouchPhase.STATIONARY -> {
                    t.deltaX = 0f
                    t.deltaY = 0f
                }
            }
        }

        synchronized(eventLock) {
            while (touchQueue.isNotEmpty()) {
                val ev = touchQueue.removeFirst()
                val existing = activeTouches[ev.pointerId]
                when (ev.phase) {
                    TouchPhase.BEGAN -> {
                        activeTouches[ev.pointerId] = TouchPointer(
                            pointerId = ev.pointerId,
                            phase = TouchPhase.BEGAN,
                            x = ev.x,
                            y = ev.y,
                            startX = ev.x,
                            startY = ev.y
                        )
                    }
                    TouchPhase.MOVED -> {
                        if (existing != null) {
                            existing.deltaX += ev.x - existing.x
                            existing.deltaY += ev.y - existing.y
                            existing.x = ev.x
                            existing.y = ev.y
                            if (existing.phase != TouchPhase.BEGAN) {
                                existing.phase = TouchPhase.MOVED
                            }
                        } else {
                            activeTouches[ev.pointerId] = TouchPointer(
                                ev.pointerId, TouchPhase.BEGAN, ev.x, ev.y, ev.x, ev.y
                            )
                        }
                    }
                    TouchPhase.ENDED, TouchPhase.CANCELLED -> {
                        if (existing != null) {
                            existing.deltaX += ev.x - existing.x
                            existing.deltaY += ev.y - existing.y
                            existing.x = ev.x
                            existing.y = ev.y
                            existing.phase = ev.phase
                        }
                    }
                    TouchPhase.STATIONARY -> {}
                }
            }

            while (keyQueue.isNotEmpty()) {
                val kv = keyQueue.removeFirst()
                if (kv.down) {
                    if (keysDown.add(kv.key)) {
                        keysJustPressed.add(kv.key)
                    }
                } else {
                    if (keysDown.remove(kv.key)) {
                        keysJustReleased.add(kv.key)
                    }
                }
            }
        }
    }

    /**
     * Called after UI widgets update virtual buttons so action edge detection reflects UI touches too!
     */
    internal fun refreshActionSnapshots() {
        prevActionsDown.clear()
        prevActionsDown.addAll(currentActionsDown)
        currentActionsDown.clear()

        currentActionsDown.addAll(virtualActionsDown)
        for ((action, boundKeys) in actionBindings) {
            if (boundKeys.any { keysDown.contains(it) }) {
                currentActionsDown.add(action)
            }
        }
    }

    val touches: List<TouchPointer>
        get() = activeTouches.values.toList()

    val touchCount: Int
        get() = activeTouches.size

    fun isKeyDown(key: KeyCode): Boolean = keysDown.contains(key)
    fun isKeyJustPressed(key: KeyCode): Boolean = keysJustPressed.contains(key)
    fun isKeyJustReleased(key: KeyCode): Boolean = keysJustReleased.contains(key)

    fun isActionPressed(actionName: String): Boolean = currentActionsDown.contains(actionName)
    fun isActionJustPressed(actionName: String): Boolean =
        currentActionsDown.contains(actionName) && !prevActionsDown.contains(actionName)
    fun isActionJustReleased(actionName: String): Boolean =
        !currentActionsDown.contains(actionName) && prevActionsDown.contains(actionName)

    fun getSecondaryStick(): Vector2 = secondaryVirtualStick.copyOf()

    /**
     * Unified axis reader ("Horizontal" / "Vertical") combining Virtual Joystick, Gamepad, and Keyboard!
     */
    fun getAxis(axisName: String): Float {
        return when (axisName) {
            "Horizontal" -> {
                var v = primaryVirtualStick.x + hardwareLeftStick.x
                if (keysDown.contains(KeyCode.LEFT) || keysDown.contains(KeyCode.A)) v -= 1f
                if (keysDown.contains(KeyCode.RIGHT) || keysDown.contains(KeyCode.D)) v += 1f
                Mathf.clamp(v, -1f, 1f)
            }
            "Vertical" -> {
                var v = primaryVirtualStick.y + hardwareLeftStick.y
                if (keysDown.contains(KeyCode.DOWN) || keysDown.contains(KeyCode.S)) v -= 1f
                if (keysDown.contains(KeyCode.UP) || keysDown.contains(KeyCode.W)) v += 1f
                Mathf.clamp(v, -1f, 1f)
            }
            else -> 0f
        }
    }

    fun clearAll() {
        synchronized(eventLock) {
            touchQueue.clear()
            keyQueue.clear()
        }
        activeTouches.clear()
        keysDown.clear()
        keysJustPressed.clear()
        keysJustReleased.clear()
        virtualActionsDown.clear()
        prevActionsDown.clear()
        currentActionsDown.clear()
        primaryVirtualStick.setZero()
        secondaryVirtualStick.setZero()
        hardwareLeftStick.setZero()
    }
}
