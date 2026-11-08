package com.tengine.core.physics

import com.tengine.core.math.AABB
import com.tengine.core.math.Vector2
import kotlin.math.floor

/**
 * Uniform Spatial Hash Grid Broadphase providing O(N) average collision pair generation
 * and fast regional AABB / Point queries.
 */
class SpatialHashGrid(cellSize: Float = 2.5f) {

    private val invCellSize: Float = 1f / cellSize.coerceAtLeast(0.25f)
    private val buckets = HashMap<Long, MutableList<Collider2D>>(256)
    private val bucketPool = ArrayDeque<MutableList<Collider2D>>(256)

    fun clear() {
        for (list in buckets.values) {
            list.clear()
            if (bucketPool.size < 512) {
                bucketPool.addLast(list)
            }
        }
        buckets.clear()
    }

    fun insert(collider: Collider2D) {
        val aabb = collider.worldAABB
        val minCX = cellCoord(aabb.minX)
        val maxCX = cellCoord(aabb.maxX)
        val minCY = cellCoord(aabb.minY)
        val maxCY = cellCoord(aabb.maxY)

        for (cy in minCY..maxCY) {
            for (cx in minCX..maxCX) {
                val key = packKey(cx, cy)
                val list = buckets.getOrPut(key) {
                    if (bucketPool.isNotEmpty()) bucketPool.removeLast() else ArrayList(8)
                }
                list.add(collider)
            }
        }
    }

    fun collectCandidatePairs(outPairs: MutableList<Pair<Collider2D, Collider2D>>) {
        outPairs.clear()
        val seenPairs = HashSet<Long>(buckets.size * 2)

        for (bucket in buckets.values) {
            val count = bucket.size
            if (count < 2) continue
            for (i in 0 until count - 1) {
                val a = bucket[i]
                for (j in i + 1 until count) {
                    val b = bucket[j]
                    if (a.gameObject === b.gameObject) continue
                    val idA = a.instanceId
                    val idB = b.instanceId
                    val pairKey = if (idA < idB) {
                        (idA shl 32) xor (idB and 0xFFFFFFFFL)
                    } else {
                        (idB shl 32) xor (idA and 0xFFFFFFFFL)
                    }
                    if (seenPairs.add(pairKey) && a.worldAABB.overlaps(b.worldAABB)) {
                        if (idA < idB) outPairs.add(a to b) else outPairs.add(b to a)
                    }
                }
            }
        }
    }

    fun queryAABB(aabb: AABB, outResults: MutableSet<Collider2D>) {
        val minCX = cellCoord(aabb.minX)
        val maxCX = cellCoord(aabb.maxX)
        val minCY = cellCoord(aabb.minY)
        val maxCY = cellCoord(aabb.maxY)

        for (cy in minCY..maxCY) {
            for (cx in minCX..maxCX) {
                val list = buckets[packKey(cx, cy)] ?: continue
                for (i in 0 until list.size) {
                    val col = list[i]
                    if (col.worldAABB.overlaps(aabb)) {
                        outResults.add(col)
                    }
                }
            }
        }
    }

    fun queryPoint(point: Vector2, outResults: MutableSet<Collider2D>) {
        val cx = cellCoord(point.x)
        val cy = cellCoord(point.y)
        val list = buckets[packKey(cx, cy)] ?: return
        for (i in 0 until list.size) {
            val col = list[i]
            if (col.containsPoint(point)) {
                outResults.add(col)
            }
        }
    }

    private fun cellCoord(v: Float): Int = floor(v * invCellSize).toInt()

    private fun packKey(cx: Int, cy: Int): Long =
        (cx.toLong() shl 32) or (cy.toLong() and 0xFFFFFFFFL)
}
