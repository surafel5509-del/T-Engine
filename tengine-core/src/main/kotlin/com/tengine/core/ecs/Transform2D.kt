package com.tengine.core.ecs

import com.tengine.core.math.Mathf
import com.tengine.core.math.Matrix3
import com.tengine.core.math.Vector2
import com.tengine.core.serialization.JsonObject
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Hierarchical 2D Spatial Transform present on every [GameObject].
 * Caches local-to-world and world-to-local affine 3x3 matrices using dirty flags.
 */
class Transform2D : Component("Transform2D") {

    override val typeName: String get() = "Transform2D"

    private val _localPosition = Vector2(0f, 0f)
    private var _localRotationDeg: Float = 0f
    private val _localScale = Vector2(1f, 1f)
    private var _zOrderDepth: Float = 0f

    var parent: Transform2D? = null
        private set

    private val _children = mutableListOf<Transform2D>()
    val children: List<Transform2D> get() = _children

    private val localMatrix = Matrix3()
    private val _worldMatrix = Matrix3()
    private val _worldToLocalMatrix = Matrix3()
    private var isDirty: Boolean = true
    private var isInverseDirty: Boolean = true

    /** Increments whenever world transform changes so colliders/renderers can invalidate cached bounds. */
    var version: Int = 1
        private set

    var localPosition: Vector2
        get() = _localPosition.copyOf()
        set(value) {
            if (_localPosition.x != value.x || _localPosition.y != value.y) {
                _localPosition.set(value)
                markDirty()
            }
        }

    var localX: Float
        get() = _localPosition.x
        set(value) {
            if (_localPosition.x != value) {
                _localPosition.x = value
                markDirty()
            }
        }

    var localY: Float
        get() = _localPosition.y
        set(value) {
            if (_localPosition.y != value) {
                _localPosition.y = value
                markDirty()
            }
        }

    var localRotationDeg: Float
        get() = _localRotationDeg
        set(value) {
            if (_localRotationDeg != value) {
                _localRotationDeg = value
                markDirty()
            }
        }

    var localRotationRad: Float
        get() = _localRotationDeg * Mathf.DEG_TO_RAD
        set(value) {
            localRotationDeg = value * Mathf.RAD_TO_DEG
        }

    var localScale: Vector2
        get() = _localScale.copyOf()
        set(value) {
            if (_localScale.x != value.x || _localScale.y != value.y) {
                _localScale.set(value)
                markDirty()
            }
        }

    var zDepth: Float
        get() = _zOrderDepth
        set(value) {
            _zOrderDepth = value
        }

    var worldPosition: Vector2
        get() {
            val m = worldMatrix.values
            return Vector2(m[6], m[7])
        }
        set(value) {
            val p = parent
            if (p == null) {
                setLocalPosition(value.x, value.y)
            } else {
                val local = p.inverseTransformPoint(value)
                setLocalPosition(local.x, local.y)
            }
        }

    val worldX: Float get() = worldMatrix.values[6]
    val worldY: Float get() = worldMatrix.values[7]

    var worldRotationDeg: Float
        get() {
            val p = parent
            return if (p == null) _localRotationDeg else p.worldRotationDeg + _localRotationDeg
        }
        set(value) {
            val p = parent
            localRotationDeg = if (p == null) value else value - p.worldRotationDeg
        }

    val worldRotationRad: Float
        get() = worldRotationDeg * Mathf.DEG_TO_RAD

    val worldScale: Vector2
        get() {
            val p = parent ?: return _localScale.copyOf()
            val ps = p.worldScale
            return Vector2(ps.x * _localScale.x, ps.y * _localScale.y)
        }

    /** Unit vector pointing along local +X axis in world space. */
    val right: Vector2
        get() {
            val r = worldRotationRad
            return Vector2(cos(r), sin(r))
        }

    /** Unit vector pointing along local +Y axis in world space. */
    val up: Vector2
        get() {
            val r = worldRotationRad
            return Vector2(-sin(r), cos(r))
        }

    val worldMatrix: Matrix3
        get() {
            if (isDirty) {
                recalculateMatrices()
            }
            return _worldMatrix
        }

    val worldToLocalMatrix: Matrix3
        get() {
            if (isInverseDirty || isDirty) {
                worldMatrix.invert(_worldToLocalMatrix)
                isInverseDirty = false
            }
            return _worldToLocalMatrix
        }

    fun setLocalPosition(nx: Float, ny: Float) {
        if (_localPosition.x != nx || _localPosition.y != ny) {
            _localPosition.set(nx, ny)
            markDirty()
        }
    }

    fun setLocalScale(sx: Float, sy: Float) {
        if (_localScale.x != sx || _localScale.y != sy) {
            _localScale.set(sx, sy)
            markDirty()
        }
    }

    fun translate(dx: Float, dy: Float, worldSpace: Boolean = true) {
        if (worldSpace) {
            val wp = worldPosition
            worldPosition = Vector2(wp.x + dx, wp.y + dy)
        } else {
            val r = localRotationRad
            val c = cos(r)
            val s = sin(r)
            setLocalPosition(
                _localPosition.x + dx * c - dy * s,
                _localPosition.y + dx * s + dy * c
            )
        }
    }

    fun translate(delta: Vector2, worldSpace: Boolean = true) = translate(delta.x, delta.y, worldSpace)

    fun rotate(deltaDegrees: Float) {
        localRotationDeg += deltaDegrees
    }

    fun lookAt2D(targetWorldPos: Vector2, forwardOffsetDeg: Float = 0f) {
        val wp = worldPosition
        val angle = atan2(targetWorldPos.y - wp.y, targetWorldPos.x - wp.x) * Mathf.RAD_TO_DEG
        worldRotationDeg = angle + forwardOffsetDeg
    }

    fun transformPoint(localPoint: Vector2): Vector2 = worldMatrix.transformPoint(localPoint)

    fun transformPointX(lx: Float, ly: Float): Float = worldMatrix.transformPointX(lx, ly)
    fun transformPointY(lx: Float, ly: Float): Float = worldMatrix.transformPointY(lx, ly)

    fun inverseTransformPoint(worldPoint: Vector2): Vector2 = worldToLocalMatrix.transformPoint(worldPoint)

    fun setParent(newParent: Transform2D?, keepWorldPosition: Boolean = true) {
        if (newParent === this || newParent === parent) return
        if (newParent != null && newParent.isDescendantOf(this)) {
            return // Prevent hierarchy cycle
        }

        val savedWorldPos = if (keepWorldPosition) worldPosition else null
        val savedWorldRot = if (keepWorldPosition) worldRotationDeg else 0f
        val savedWorldScale = if (keepWorldPosition) worldScale else null

        val oldParent = parent
        if (oldParent != null) {
            oldParent._children.remove(this)
        } else if (isAttached) {
            gameObject.scene?.notifyRootChanged(gameObject, isNowRoot = false)
        }

        parent = newParent
        if (newParent != null) {
            newParent._children.add(this)
        } else if (isAttached) {
            gameObject.scene?.notifyRootChanged(gameObject, isNowRoot = true)
        }

        markDirty()

        if (keepWorldPosition && savedWorldPos != null && savedWorldScale != null) {
            worldPosition = savedWorldPos
            worldRotationDeg = savedWorldRot
            val ps = newParent?.worldScale ?: Vector2.ONE
            val sx = if (kotlin.math.abs(ps.x) > Mathf.EPSILON) savedWorldScale.x / ps.x else savedWorldScale.x
            val sy = if (kotlin.math.abs(ps.y) > Mathf.EPSILON) savedWorldScale.y / ps.y else savedWorldScale.y
            setLocalScale(sx, sy)
        }

        if (isAttached) {
            gameObject.refreshHierarchyActiveState()
        }
    }

    fun isDescendantOf(candidateAncestor: Transform2D): Boolean {
        var cursor = parent
        while (cursor != null) {
            if (cursor === candidateAncestor) return true
            cursor = cursor.parent
        }
        return false
    }

    private fun markDirty() {
        isDirty = true
        isInverseDirty = true
        version++
        for (i in 0 until _children.size) {
            _children[i].markDirty()
        }
    }

    private fun recalculateMatrices() {
        localMatrix.setTRS(
            _localPosition.x,
            _localPosition.y,
            _localRotationDeg * Mathf.DEG_TO_RAD,
            _localScale.x,
            _localScale.y
        )
        val p = parent
        if (p == null) {
            _worldMatrix.setFrom(localMatrix)
        } else {
            Matrix3.multiply(p.worldMatrix, localMatrix, _worldMatrix)
        }
        isDirty = false
        isInverseDirty = true
    }

    override fun serialize(): JsonObject = JsonObject()
        .putVector2("position", _localPosition)
        .put("rotationDeg", _localRotationDeg)
        .putVector2("scale", _localScale)
        .put("zDepth", _zOrderDepth)

    override fun deserialize(data: JsonObject) {
        localPosition = data.getVector2("position", Vector2.ZERO)
        localRotationDeg = data.getFloat("rotationDeg", 0f)
        localScale = data.getVector2("scale", Vector2.ONE)
        zDepth = data.getFloat("zDepth", 0f)
    }

    override fun inspectableProperties(): List<InspectorProperty> = listOf(
        InspectorProperty.Vector2Prop(
            label = "Position",
            step = 0.25f,
            getter = { localPosition },
            setter = { localPosition = it }
        ),
        InspectorProperty.FloatProp(
            label = "Rotation (deg)",
            min = -360f,
            max = 360f,
            step = 5f,
            getter = { localRotationDeg },
            setter = { localRotationDeg = it }
        ),
        InspectorProperty.Vector2Prop(
            label = "Scale",
            step = 0.1f,
            getter = { localScale },
            setter = { localScale = it }
        )
    )
}
