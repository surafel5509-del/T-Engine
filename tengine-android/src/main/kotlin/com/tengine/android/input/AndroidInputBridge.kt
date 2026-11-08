package com.tengine.android.input

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import com.tengine.core.input.Input
import com.tengine.core.input.KeyCode
import com.tengine.core.input.TouchPhase

/**
 * Bridges Android multi-touch [MotionEvent], hardware keyboard/controller [KeyEvent],
 * Gamepad analog sticks, and device Accelerometer tilt into T-Engine's thread-safe [Input] system!
 */
class AndroidInputBridge(context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val accelerometerSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    var enableAccelerometer: Boolean = false
        set(value) {
            field = value
            if (value) registerSensors() else unregisterSensors()
        }

    fun onTouchEvent(event: MotionEvent): Boolean {
        val action = event.actionMasked
        val actionIndex = event.actionIndex

        when (action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val id = event.getPointerId(actionIndex)
                Input.enqueueTouch(id, TouchPhase.BEGAN, event.getX(actionIndex), event.getY(actionIndex))
            }
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until event.pointerCount) {
                    val id = event.getPointerId(i)
                    Input.enqueueTouch(id, TouchPhase.MOVED, event.getX(i), event.getY(i))
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val id = event.getPointerId(actionIndex)
                Input.enqueueTouch(id, TouchPhase.ENDED, event.getX(actionIndex), event.getY(actionIndex))
            }
            MotionEvent.ACTION_CANCEL -> {
                for (i in 0 until event.pointerCount) {
                    val id = event.getPointerId(i)
                    Input.enqueueTouch(id, TouchPhase.CANCELLED, event.getX(i), event.getY(i))
                }
            }
        }
        return true
    }

    fun onGenericMotionEvent(event: MotionEvent): Boolean {
        val isJoystick = (event.source and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK
        if (isJoystick && event.action == MotionEvent.ACTION_MOVE) {
            var sx = event.getAxisValue(MotionEvent.AXIS_X)
            var sy = -event.getAxisValue(MotionEvent.AXIS_Y)
            if (kotlin.math.abs(sx) < 0.1f) sx = event.getAxisValue(MotionEvent.AXIS_HAT_X)
            if (kotlin.math.abs(sy) < 0.1f) sy = -event.getAxisValue(MotionEvent.AXIS_HAT_Y)
            Input.setHardwareLeftStick(sx, sy)
            return true
        }
        return false
    }

    fun onKeyDown(keyCode: Int): Boolean {
        val mapped = mapAndroidKeyCode(keyCode)
        if (mapped != KeyCode.UNKNOWN) {
            Input.enqueueKey(mapped, true)
            return true
        }
        return false
    }

    fun onKeyUp(keyCode: Int): Boolean {
        val mapped = mapAndroidKeyCode(keyCode)
        if (mapped != KeyCode.UNKNOWN) {
            Input.enqueueKey(mapped, false)
            return true
        }
        return false
    }

    fun registerSensors() {
        if (enableAccelerometer && accelerometerSensor != null) {
            sensorManager?.registerListener(this, accelerometerSensor, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    fun unregisterSensors() {
        sensorManager?.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_ACCELEROMETER) {
            Input.setAccelerometerTilt(-event.values[0] / 9.81f, -event.values[1] / 9.81f)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun mapAndroidKeyCode(code: Int): KeyCode = when (code) {
        KeyEvent.KEYCODE_DPAD_LEFT -> KeyCode.LEFT
        KeyEvent.KEYCODE_DPAD_RIGHT -> KeyCode.RIGHT
        KeyEvent.KEYCODE_DPAD_UP -> KeyCode.UP
        KeyEvent.KEYCODE_DPAD_DOWN -> KeyCode.DOWN
        KeyEvent.KEYCODE_W -> KeyCode.W
        KeyEvent.KEYCODE_A -> KeyCode.A
        KeyEvent.KEYCODE_S -> KeyCode.S
        KeyEvent.KEYCODE_D -> KeyCode.D
        KeyEvent.KEYCODE_SPACE -> KeyCode.SPACE
        KeyEvent.KEYCODE_ENTER -> KeyCode.ENTER
        KeyEvent.KEYCODE_ESCAPE -> KeyCode.ESCAPE
        KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.KEYCODE_SHIFT_RIGHT -> KeyCode.SHIFT
        KeyEvent.KEYCODE_1 -> KeyCode.KEY_1
        KeyEvent.KEYCODE_2 -> KeyCode.KEY_2
        KeyEvent.KEYCODE_3 -> KeyCode.KEY_3
        KeyEvent.KEYCODE_4 -> KeyCode.KEY_4
        KeyEvent.KEYCODE_BUTTON_A -> KeyCode.GAMEPAD_A
        KeyEvent.KEYCODE_BUTTON_B -> KeyCode.GAMEPAD_B
        KeyEvent.KEYCODE_BUTTON_X -> KeyCode.GAMEPAD_X
        KeyEvent.KEYCODE_BUTTON_Y -> KeyCode.GAMEPAD_Y
        KeyEvent.KEYCODE_BUTTON_L1 -> KeyCode.GAMEPAD_LB
        KeyEvent.KEYCODE_BUTTON_R1 -> KeyCode.GAMEPAD_RB
        KeyEvent.KEYCODE_BUTTON_START -> KeyCode.GAMEPAD_START
        else -> KeyCode.UNKNOWN
    }
}
