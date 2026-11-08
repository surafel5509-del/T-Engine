package com.tengine.core.math

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

data class Rect(
    var x: Float = 0f,
    var y: Float = 0f,
    var width: Float = 0f,
    var height: Float = 0f
) {
    val minX: Float get() = x
    val minY: Float get() = y
    val maxX: Float get() = x + width
    val maxY: Float get() = y + height
    val centerX: Float get() = x + width * 0.5f
    val centerY: Float get() = y + height * 0.5f

    fun contains(px: Float, py: Float): Boolean =
        px >= x && px <= x + width && py >= y && py <= y + height

    fun contains(p: Vector2): Boolean = contains(p.x, p.y)

    fun overlaps(other: Rect): Boolean =
        minX < other.maxX && maxX > other.minX && minY < other.maxY && maxY > other.minY

    fun toAABB(): AABB = AABB(minX, minY, maxX, maxY)
}

/**
 * Axis-Aligned Bounding Box used for frustum culling, spatial-hash broadphase, and ray queries.
 */
data class AABB(
    var minX: Float = 0f,
    var minY: Float = 0f,
    var maxX: Float = 0f,
    var maxY: Float = 0f
) {
    val width: Float get() = maxX - minX
    val height: Float get() = maxY - minY
    val centerX: Float get() = (minX + maxX) * 0.5f
    val centerY: Float get() = (minY + maxY) * 0.5f
    val center: Vector2 get() = Vector2(centerX, centerY)
    val extents: Vector2 get() = Vector2(width * 0.5f, height * 0.5f)
    val perimeter: Float get() = 2f * (max(0f, width) + max(0f, height))

    fun set(nMinX: Float, nMinY: Float, nMaxX: Float, nMaxY: Float): AABB {
        minX = nMinX
        minY = nMinY
        maxX = nMaxX
        maxY = nMaxY
        return this
    }

    fun overlaps(other: AABB): Boolean =
        minX <= other.maxX && maxX >= other.minX && minY <= other.maxY && maxY >= other.minY

    fun contains(px: Float, py: Float): Boolean =
        px in minX..maxX && py in minY..maxY

    fun contains(point: Vector2): Boolean = contains(point.x, point.y)

    fun expanded(margin: Float): AABB =
        AABB(minX - margin, minY - margin, maxX + margin, maxY + margin)

    fun merge(other: AABB): AABB = AABB(
        min(minX, other.minX),
        min(minY, other.minY),
        max(maxX, other.maxX),
        max(maxY, other.maxY)
    )

    /**
     * Slab-method Ray vs AABB intersection. Returns hit distance >= 0 or -1f if missed.
     */
    fun intersectRay(ray: Ray2D, maxDistance: Float = Float.POSITIVE_INFINITY): Float {
        var tMin = 0f
        var tMax = maxDistance

        if (abs(ray.direction.x) < Mathf.EPSILON) {
            if (ray.origin.x < minX || ray.origin.x > maxX) return -1f
        } else {
            val invD = 1f / ray.direction.x
            var t1 = (minX - ray.origin.x) * invD
            var t2 = (maxX - ray.origin.x) * invD
            if (t1 > t2) {
                val tmp = t1; t1 = t2; t2 = tmp
            }
            tMin = max(tMin, t1)
            tMax = min(tMax, t2)
            if (tMin > tMax) return -1f
        }

        if (abs(ray.direction.y) < Mathf.EPSILON) {
            if (ray.origin.y < minY || ray.origin.y > maxY) return -1f
        } else {
            val invD = 1f / ray.direction.y
            var t1 = (minY - ray.origin.y) * invD
            var t2 = (maxY - ray.origin.y) * invD
            if (t1 > t2) {
                val tmp = t1; t1 = t2; t2 = tmp
            }
            tMin = max(tMin, t1)
            tMax = min(tMax, t2)
            if (tMin > tMax) return -1f
        }

        return tMin
    }

    companion object {
        fun fromCenterExtents(cx: Float, cy: Float, halfW: Float, halfH: Float): AABB =
            AABB(cx - halfW, cy - halfH, cx + halfW, cy + halfH)
    }
}

data class Circle(
    var x: Float = 0f,
    var y: Float = 0f,
    var radius: Float = 0.5f
) {
    fun contains(px: Float, py: Float): Boolean {
        val dx = px - x
        val dy = py - y
        return dx * dx + dy * dy <= radius * radius
    }

    fun overlaps(other: Circle): Boolean {
        val dx = other.x - x
        val dy = other.y - y
        val r = radius + other.radius
        return dx * dx + dy * dy <= r * r
    }
}

class Ray2D(
    origin: Vector2,
    direction: Vector2
) {
    val origin: Vector2 = origin.copyOf()
    val direction: Vector2 = direction.normalized

    fun getPoint(distance: Float): Vector2 =
        Vector2(origin.x + direction.x * distance, origin.y + direction.y * distance)
}

/**
 * Convex 2D Polygon utilities with CCW normalization, Monotone Chain convex hull,
 * centroid calculation, and ray-segment intersection.
 */
object Geometry2D {

    fun signedArea(vertices: List<Vector2>): Float {
        if (vertices.size < 3) return 0f
        var area2 = 0f
        for (i in vertices.indices) {
            val a = vertices[i]
            val b = vertices[(i + 1) % vertices.size]
            area2 += (a.x * b.y - b.x * a.y)
        }
        return area2 * 0.5f
    }

    fun ensureCounterClockwise(vertices: List<Vector2>): List<Vector2> {
        if (vertices.size < 3) return vertices.map { it.copyOf() }
        return if (signedArea(vertices) < 0f) {
            vertices.asReversed().map { it.copyOf() }
        } else {
            vertices.map { it.copyOf() }
        }
    }

    /**
     * Andrew's Monotone Chain convex hull in O(N log N), guaranteed CCW winding.
     */
    fun convexHull(points: List<Vector2>): List<Vector2> {
        if (points.size <= 3) return ensureCounterClockwise(points)
        val sorted = points.sortedWith(compareBy<Vector2> { it.x }.thenBy { it.y })
        val lower = ArrayList<Vector2>(sorted.size)
        for (p in sorted) {
            while (lower.size >= 2) {
                val o = lower[lower.size - 2]
                val a = lower[lower.size - 1]
                val cross = (a.x - o.x) * (p.y - o.y) - (a.y - o.y) * (p.x - o.x)
                if (cross <= 0f) lower.removeAt(lower.size - 1) else break
            }
            lower.add(p.copyOf())
        }
        val upper = ArrayList<Vector2>(sorted.size)
        for (i in sorted.indices.reversed()) {
            val p = sorted[i]
            while (upper.size >= 2) {
                val o = upper[upper.size - 2]
                val a = upper[upper.size - 1]
                val cross = (a.x - o.x) * (p.y - o.y) - (a.y - o.y) * (p.x - o.x)
                if (cross <= 0f) upper.removeAt(upper.size - 1) else break
            }
            upper.add(p.copyOf())
        }
        lower.removeAt(lower.size - 1)
        upper.removeAt(upper.size - 1)
        lower.addAll(upper)
        return lower
    }

    /**
     * Intersects [ray] against line segment [a]-[b]. Returns ray distance t >= 0 or -1f on miss.
     */
    fun raySegmentIntersect(ray: Ray2D, a: Vector2, b: Vector2, outNormal: Vector2? = null): Float {
        val v1x = ray.origin.x - a.x
        val v1y = ray.origin.y - a.y
        val v2x = b.x - a.x
        val v2y = b.y - a.y
        val v3x = -ray.direction.y
        val v3y = ray.direction.x

        val dot = v2x * v3x + v2y * v3y
        if (abs(dot) < Mathf.EPSILON) return -1f

        val t1 = (v2x * v1y - v2y * v1x) / dot
        val t2 = (v1x * v3x + v1y * v3y) / dot
        if (t1 >= 0f && t2 in 0f..1f) {
            if (outNormal != null) {
                var nx = -v2y
                var ny = v2x
                val len = sqrt(nx * nx + ny * ny)
                if (len > Mathf.EPSILON) {
                    nx /= len
                    ny /= len
                }
                if (nx * ray.direction.x + ny * ray.direction.y > 0f) {
                    nx = -nx
                    ny = -ny
                }
                outNormal.set(nx, ny)
            }
            return t1
        }
        return -1f
    }

    fun rayCircleIntersect(
        ray: Ray2D,
        centerX: Float,
        centerY: Float,
        radius: Float,
        outNormal: Vector2? = null
    ): Float {
        val ox = ray.origin.x - centerX
        val oy = ray.origin.y - centerY
        val b = ox * ray.direction.x + oy * ray.direction.y
        val c = ox * ox + oy * oy - radius * radius
        if (c > 0f && b > 0f) return -1f

        val discr = b * b - c
        if (discr < 0f) return -1f

        var t = -b - sqrt(discr)
        if (t < 0f) t = 0f
        if (outNormal != null) {
            val hx = ray.origin.x + ray.direction.x * t
            val hy = ray.origin.y + ray.direction.y * t
            outNormal.set(hx - centerX, hy - centerY).normalizeSelf()
        }
        return t
    }
}
