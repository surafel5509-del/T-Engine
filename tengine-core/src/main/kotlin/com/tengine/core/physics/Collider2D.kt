package com.tengine.core.physics

import com.tengine.core.ecs.Component
import com.tengine.core.ecs.InspectorProperty
import com.tengine.core.graphics.ShapeRenderer
import com.tengine.core.math.AABB
import com.tengine.core.math.Color
import com.tengine.core.math.Geometry2D
import com.tengine.core.math.Mathf
import com.tengine.core.math.Ray2D
import com.tengine.core.math.Vector2
import com.tengine.core.serialization.JsonArray
import com.tengine.core.serialization.JsonObject
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Result of a Physics2D raycast against a [Collider2D].
 */
data class RaycastHit2D(
    val collider: Collider2D,
    val point: Vector2,
    val normal: Vector2,
    val distance: Float
)

/**
 * Collision callback payload delivered to [Component.onCollisionEnter2D] / Stay / Exit.
 */
data class CollisionEvent2D(
    val otherCollider: Collider2D,
    val selfCollider: Collider2D,
    val normal: Vector2,
    val penetration: Float,
    val contactPoint: Vector2,
    val relativeVelocity: Vector2
) {
    val otherGameObject get() = otherCollider.gameObject
    val rigidbody get() = otherCollider.attachedRigidbody
}

/**
 * Abstract base 2D collision shape attached to a [com.tengine.core.ecs.GameObject].
 */
abstract class Collider2D(colliderName: String = "Collider2D") : Component(colliderName) {

    var offset: Vector2 = Vector2(0f, 0f)
        set(value) {
            field = value.copyOf()
            invalidateCache()
        }

    var isTrigger: Boolean = false
    var material: PhysicsMaterial2D = PhysicsMaterial2D.DEFAULT

    private val cachedAABB = AABB()
    private var cachedTransformVersion: Int = -1

    val attachedRigidbody: Rigidbody2D?
        get() = if (isAttached) gameObject.getComponentInParent(Rigidbody2D::class) else null

    val worldAABB: AABB
        get() {
            val tv = if (isAttached) transform.version else 0
            if (cachedTransformVersion != tv) {
                recalculateWorldGeometry(cachedAABB)
                cachedTransformVersion = tv
            }
            return cachedAABB
        }

    fun invalidateCache() {
        cachedTransformVersion = -1
    }

    protected abstract fun recalculateWorldGeometry(outAABB: AABB)

    /** Returns Pair(mass, rotationalInertia) given material density. */
    abstract fun computeMassProperties(): Pair<Float, Float>

    abstract fun containsPoint(worldPoint: Vector2): Boolean

    abstract fun raycast(ray: Ray2D, maxDistance: Float): RaycastHit2D?

    override fun serialize(): JsonObject = super.serialize()
        .putVector2("offset", offset)
        .put("isTrigger", isTrigger)
        .put("friction", material.friction)
        .put("restitution", material.restitution)
        .put("density", material.density)

    override fun deserialize(data: JsonObject) {
        super.deserialize(data)
        offset = data.getVector2("offset", Vector2.ZERO)
        isTrigger = data.getBoolean("isTrigger", false)
        material = PhysicsMaterial2D(
            friction = data.getFloat("friction", 0.4f),
            restitution = data.getFloat("restitution", 0.15f),
            density = data.getFloat("density", 1.0f)
        )
        invalidateCache()
    }
}

/**
 * Oriented 2D Box Collider supporting full rotation, non-uniform scale, SAT vertices, and gizmos.
 */
class BoxCollider2D(
    size: Vector2 = Vector2(1f, 1f)
) : Collider2D("BoxCollider2D") {

    override val typeName: String get() = "BoxCollider2D"

    var size: Vector2 = size.copyOf()
        set(value) {
            field = Vector2(max(0.01f, value.x), max(0.01f, value.y))
            invalidateCache()
        }

    private val worldVerts = Array(4) { Vector2() }

    fun getWorldVertices(): Array<Vector2> {
        // Ensure cache is fresh
        val _aabb = worldAABB
        return worldVerts
    }

    override fun recalculateWorldGeometry(outAABB: AABB) {
        val hx = size.x * 0.5f
        val hy = size.y * 0.5f
        val ox = offset.x
        val oy = offset.y

        // Counter-clockwise local corners
        val lx0 = ox - hx; val ly0 = oy - hy
        val lx1 = ox + hx; val ly1 = oy - hy
        val lx2 = ox + hx; val ly2 = oy + hy
        val lx3 = ox - hx; val ly3 = oy + hy

        if (isAttached) {
            val t = transform
            worldVerts[0].set(t.transformPointX(lx0, ly0), t.transformPointY(lx0, ly0))
            worldVerts[1].set(t.transformPointX(lx1, ly1), t.transformPointY(lx1, ly1))
            worldVerts[2].set(t.transformPointX(lx2, ly2), t.transformPointY(lx2, ly2))
            worldVerts[3].set(t.transformPointX(lx3, ly3), t.transformPointY(lx3, ly3))
        } else {
            worldVerts[0].set(lx0, ly0)
            worldVerts[1].set(lx1, ly1)
            worldVerts[2].set(lx2, ly2)
            worldVerts[3].set(lx3, ly3)
        }

        var minX = worldVerts[0].x
        var maxX = worldVerts[0].x
        var minY = worldVerts[0].y
        var maxY = worldVerts[0].y
        for (i in 1..3) {
            val v = worldVerts[i]
            if (v.x < minX) minX = v.x
            if (v.x > maxX) maxX = v.x
            if (v.y < minY) minY = v.y
            if (v.y > maxY) maxY = v.y
        }
        outAABB.set(minX, minY, maxX, maxY)
    }

    override fun computeMassProperties(): Pair<Float, Float> {
        val ws = if (isAttached) transform.worldScale else Vector2.ONE
        val w = abs(size.x * ws.x)
        val h = abs(size.y * ws.y)
        val area = w * h
        val m = max(0.001f, area * material.density)
        val inertia = (m * (w * w + h * h)) / 12f
        return m to inertia
    }

    override fun containsPoint(worldPoint: Vector2): Boolean {
        if (!worldAABB.contains(worldPoint)) return false
        val verts = getWorldVertices()
        for (i in 0..3) {
            val a = verts[i]
            val b = verts[(i + 1) and 3]
            val cross = (b.x - a.x) * (worldPoint.y - a.y) - (b.y - a.y) * (worldPoint.x - a.x)
            if (cross < 0f) return false
        }
        return true
    }

    override fun raycast(ray: Ray2D, maxDistance: Float): RaycastHit2D? {
        if (worldAABB.intersectRay(ray, maxDistance) < 0f) return null
        val verts = getWorldVertices()
        var bestDist = Float.POSITIVE_INFINITY
        val bestNormal = Vector2()
        val tempNormal = Vector2()
        for (i in 0..3) {
            val t = Geometry2D.raySegmentIntersect(ray, verts[i], verts[(i + 1) and 3], tempNormal)
            if (t in 0f..maxDistance && t < bestDist) {
                bestDist = t
                bestNormal.set(tempNormal)
            }
        }
        if (!bestDist.isFinite()) return null
        return RaycastHit2D(this, ray.getPoint(bestDist), bestNormal, bestDist)
    }

    override fun onDrawGizmos(gizmos: ShapeRenderer, selected: Boolean) {
        val verts = getWorldVertices()
        val color = when {
            isTrigger -> Color(0.2f, 0.95f, 0.95f, if (selected) 0.95f else 0.55f)
            selected -> Color(0.35f, 1.0f, 0.45f, 0.95f)
            else -> Color(0.25f, 0.85f, 0.35f, 0.55f)
        }
        gizmos.drawPolygonWire(verts.toList(), color, thickness = if (selected) 0.045f else 0.025f)
    }

    override fun serialize(): JsonObject = super.serialize().putVector2("size", size)

    override fun deserialize(data: JsonObject) {
        super.deserialize(data)
        size = data.getVector2("size", Vector2.ONE)
    }

    override fun inspectableProperties(): List<InspectorProperty> = listOf(
        InspectorProperty.Vector2Prop("Size", 0.1f, { size.copyOf() }, { size = it }),
        InspectorProperty.Vector2Prop("Offset", 0.05f, { offset.copyOf() }, { offset = it }),
        InspectorProperty.BoolProp("Is Trigger", { isTrigger }, { isTrigger = it }),
        InspectorProperty.FloatProp("Friction", 0f, 2f, 0.05f, { material.friction }, { material.friction = it }),
        InspectorProperty.FloatProp("Bounciness", 0f, 1f, 0.05f, { material.restitution }, { material.restitution = it })
    )
}

/**
 * 2D Circle Collider with exact analytical narrowphase and gizmos.
 */
class CircleCollider2D(
    radius: Float = 0.5f
) : Collider2D("CircleCollider2D") {

    override val typeName: String get() = "CircleCollider2D"

    var radius: Float = max(0.01f, radius)
        set(value) {
            field = max(0.01f, value)
            invalidateCache()
        }

    val worldCenter: Vector2 = Vector2()
    var worldRadius: Float = radius
        private set

    fun syncWorldCircle() {
        val _aabb = worldAABB
    }

    override fun recalculateWorldGeometry(outAABB: AABB) {
        if (isAttached) {
            val t = transform
            worldCenter.set(t.transformPointX(offset.x, offset.y), t.transformPointY(offset.x, offset.y))
            val ws = t.worldScale
            worldRadius = radius * max(abs(ws.x), abs(ws.y))
        } else {
            worldCenter.set(offset)
            worldRadius = radius
        }
        outAABB.set(
            worldCenter.x - worldRadius,
            worldCenter.y - worldRadius,
            worldCenter.x + worldRadius,
            worldCenter.y + worldRadius
        )
    }

    override fun computeMassProperties(): Pair<Float, Float> {
        syncWorldCircle()
        val r = worldRadius
        val area = Mathf.PI * r * r
        val m = max(0.001f, area * material.density)
        val inertia = 0.5f * m * r * r
        return m to inertia
    }

    override fun containsPoint(worldPoint: Vector2): Boolean {
        syncWorldCircle()
        return worldCenter.distanceSquaredTo(worldPoint) <= worldRadius * worldRadius
    }

    override fun raycast(ray: Ray2D, maxDistance: Float): RaycastHit2D? {
        syncWorldCircle()
        val normal = Vector2()
        val t = Geometry2D.rayCircleIntersect(ray, worldCenter.x, worldCenter.y, worldRadius, normal)
        if (t < 0f || t > maxDistance) return null
        return RaycastHit2D(this, ray.getPoint(t), normal, t)
    }

    override fun onDrawGizmos(gizmos: ShapeRenderer, selected: Boolean) {
        syncWorldCircle()
        val color = when {
            isTrigger -> Color(0.2f, 0.95f, 0.95f, if (selected) 0.95f else 0.55f)
            selected -> Color(0.35f, 1.0f, 0.45f, 0.95f)
            else -> Color(0.25f, 0.85f, 0.35f, 0.55f)
        }
        gizmos.drawCircleWire(worldCenter.x, worldCenter.y, worldRadius, color, segments = 28)
    }

    override fun serialize(): JsonObject = super.serialize().put("radius", radius)

    override fun deserialize(data: JsonObject) {
        super.deserialize(data)
        radius = data.getFloat("radius", 0.5f)
    }

    override fun inspectableProperties(): List<InspectorProperty> = listOf(
        InspectorProperty.FloatProp("Radius", 0.05f, 100f, 0.05f, { radius }, { radius = it }),
        InspectorProperty.Vector2Prop("Offset", 0.05f, { offset.copyOf() }, { offset = it }),
        InspectorProperty.BoolProp("Is Trigger", { isTrigger }, { isTrigger = it }),
        InspectorProperty.FloatProp("Friction", 0f, 2f, 0.05f, { material.friction }, { material.friction = it }),
        InspectorProperty.FloatProp("Bounciness", 0f, 1f, 0.05f, { material.restitution }, { material.restitution = it })
    )
}

/**
 * Convex 2D Polygon Collider supporting arbitrary convex shapes (triangles, pentagons, slopes, hulls).
 */
class PolygonCollider2D(
    points: List<Vector2> = listOf(
        Vector2(-0.5f, -0.4f),
        Vector2(0.5f, -0.4f),
        Vector2(0.0f, 0.6f)
    )
) : Collider2D("PolygonCollider2D") {

    override val typeName: String get() = "PolygonCollider2D"

    var localPoints: List<Vector2> = Geometry2D.convexHull(points)
        set(value) {
            field = if (value.size >= 3) Geometry2D.convexHull(value) else field
            worldVertices = Array(field.size) { Vector2() }
            invalidateCache()
        }

    private var worldVertices: Array<Vector2> = Array(localPoints.size) { Vector2() }

    fun getWorldVertices(): Array<Vector2> {
        val _aabb = worldAABB
        return worldVertices
    }

    override fun recalculateWorldGeometry(outAABB: AABB) {
        if (worldVertices.size != localPoints.size) {
            worldVertices = Array(localPoints.size) { Vector2() }
        }
        var minX = Float.POSITIVE_INFINITY
        var minY = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY
        var maxY = Float.NEGATIVE_INFINITY

        for (i in localPoints.indices) {
            val lp = localPoints[i]
            val lx = lp.x + offset.x
            val ly = lp.y + offset.y
            val wx: Float
            val wy: Float
            if (isAttached) {
                wx = transform.transformPointX(lx, ly)
                wy = transform.transformPointY(lx, ly)
            } else {
                wx = lx
                wy = ly
            }
            worldVertices[i].set(wx, wy)
            minX = min(minX, wx)
            minY = min(minY, wy)
            maxX = max(maxX, wx)
            maxY = max(maxY, wy)
        }
        // If mirrored scale flipped winding, ensure CCW order
        if (Geometry2D.signedArea(worldVertices.asList()) < 0f) {
            worldVertices.reverse()
        }
        outAABB.set(minX, minY, maxX, maxY)
    }

    override fun computeMassProperties(): Pair<Float, Float> {
        val verts = getWorldVertices()
        val area = abs(Geometry2D.signedArea(verts.asList()))
        val m = max(0.001f, area * material.density)
        val w = worldAABB.width
        val h = worldAABB.height
        val inertia = m * (w * w + h * h) / 14f
        return m to inertia
    }

    override fun containsPoint(worldPoint: Vector2): Boolean {
        if (!worldAABB.contains(worldPoint)) return false
        val verts = getWorldVertices()
        for (i in verts.indices) {
            val a = verts[i]
            val b = verts[(i + 1) % verts.size]
            val cross = (b.x - a.x) * (worldPoint.y - a.y) - (b.y - a.y) * (worldPoint.x - a.x)
            if (cross < 0f) return false
        }
        return true
    }

    override fun raycast(ray: Ray2D, maxDistance: Float): RaycastHit2D? {
        if (worldAABB.intersectRay(ray, maxDistance) < 0f) return null
        val verts = getWorldVertices()
        var bestDist = Float.POSITIVE_INFINITY
        val bestNormal = Vector2()
        val tempNormal = Vector2()
        for (i in verts.indices) {
            val t = Geometry2D.raySegmentIntersect(ray, verts[i], verts[(i + 1) % verts.size], tempNormal)
            if (t in 0f..maxDistance && t < bestDist) {
                bestDist = t
                bestNormal.set(tempNormal)
            }
        }
        if (!bestDist.isFinite()) return null
        return RaycastHit2D(this, ray.getPoint(bestDist), bestNormal, bestDist)
    }

    override fun onDrawGizmos(gizmos: ShapeRenderer, selected: Boolean) {
        val verts = getWorldVertices()
        val color = if (selected) Color(0.35f, 1.0f, 0.45f, 0.95f) else Color(0.25f, 0.85f, 0.35f, 0.55f)
        gizmos.drawPolygonWire(verts.toList(), color)
    }

    override fun serialize(): JsonObject {
        val arr = JsonArray()
        for (p in localPoints) {
            arr.add(JsonObject().put("x", p.x).put("y", p.y))
        }
        return super.serialize().put("points", arr)
    }

    override fun deserialize(data: JsonObject) {
        super.deserialize(data)
        val arr = data.getArray("points") ?: return
        val pts = mutableListOf<Vector2>()
        for (el in arr) {
            val o = el.asObjectOrNull() ?: continue
            pts.add(Vector2(o.getFloat("x"), o.getFloat("y")))
        }
        if (pts.size >= 3) {
            localPoints = pts
        }
    }
}
