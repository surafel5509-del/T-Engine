package com.tengine.core.ai

import com.tengine.core.math.Vector2
import java.util.PriorityQueue
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * 2D Navigation Grid + Binary-Heap A* Pathfinder with Octile Diagonal Movement
 * (preventing corner-cutting through diagonal wall gaps!) and Line-of-Sight Path Smoothing.
 */
class NavGrid2D(
    val width: Int,
    val height: Int,
    val cellSize: Float = 1f,
    val origin: Vector2 = Vector2.ZERO
) {
    private val blocked = BooleanArray(width * height)
    private val penaltyCost = FloatArray(width * height) { 1f }

    fun inBounds(gx: Int, gy: Int): Boolean = gx in 0 until width && gy in 0 until height

    fun setWalkable(gx: Int, gy: Int, walkable: Boolean, cost: Float = 1f) {
        if (!inBounds(gx, gy)) return
        val idx = gy * width + gx
        blocked[idx] = !walkable
        penaltyCost[idx] = cost.coerceAtLeast(0.1f)
    }

    fun isWalkable(gx: Int, gy: Int): Boolean =
        inBounds(gx, gy) && !blocked[gy * width + gx]

    fun getCost(gx: Int, gy: Int): Float =
        if (inBounds(gx, gy)) penaltyCost[gy * width + gx] else Float.POSITIVE_INFINITY

    fun worldToGridX(wx: Float): Int = floor((wx - origin.x) / cellSize).toInt().coerceIn(0, width - 1)
    fun worldToGridY(wy: Float): Int = floor((wy - origin.y) / cellSize).toInt().coerceIn(0, height - 1)

    fun gridToWorldCenter(gx: Int, gy: Int): Vector2 = Vector2(
        origin.x + (gx + 0.5f) * cellSize,
        origin.y + (gy + 0.5f) * cellSize
    )
}

object AStarPathfinder {

    private data class OpenNode(val index: Int, val fScore: Float) : Comparable<OpenNode> {
        override fun compareTo(other: OpenNode): Int = fScore.compareTo(other.fScore)
    }

    private val neighborDirs8 = arrayOf(
        intArrayOf(1, 0), intArrayOf(-1, 0), intArrayOf(0, 1), intArrayOf(0, -1),
        intArrayOf(1, 1), intArrayOf(-1, 1), intArrayOf(1, -1), intArrayOf(-1, -1)
    )

    fun findGridPath(
        grid: NavGrid2D,
        startX: Int,
        startY: Int,
        goalX: Int,
        goalY: Int,
        allowDiagonal: Boolean = true
    ): List<Pair<Int, Int>> {
        if (!grid.isWalkable(startX, startY) || !grid.isWalkable(goalX, goalY)) {
            return emptyList()
        }
        if (startX == goalX && startY == goalY) {
            return listOf(startX to startY)
        }

        val total = grid.width * grid.height
        val gScore = FloatArray(total) { Float.POSITIVE_INFINITY }
        val cameFrom = IntArray(total) { -1 }
        val closed = BooleanArray(total)

        val startIdx = startY * grid.width + startX
        val goalIdx = goalY * grid.width + goalX
        gScore[startIdx] = 0f

        val openSet = PriorityQueue<OpenNode>()
        openSet.add(OpenNode(startIdx, octileHeuristic(startX, startY, goalX, goalY)))

        val maxDirs = if (allowDiagonal) 8 else 4

        while (openSet.isNotEmpty()) {
            val current = openSet.poll()
            val curIdx = current.index
            if (closed[curIdx]) continue
            if (curIdx == goalIdx) {
                return reconstruct(cameFrom, goalIdx, grid.width)
            }
            closed[curIdx] = true

            val cx = curIdx % grid.width
            val cy = curIdx / grid.width

            for (d in 0 until maxDirs) {
                val dx = neighborDirs8[d][0]
                val dy = neighborDirs8[d][1]
                val nx = cx + dx
                val ny = cy + dy
                if (!grid.isWalkable(nx, ny)) continue

                // Prevent diagonal corner cutting through adjacent walls
                if (dx != 0 && dy != 0) {
                    if (!grid.isWalkable(cx + dx, cy) || !grid.isWalkable(cx, cy + dy)) {
                        continue
                    }
                }

                val nIdx = ny * grid.width + nx
                if (closed[nIdx]) continue

                val stepDist = if (dx != 0 && dy != 0) 1.4142135f else 1.0f
                val tentativeG = gScore[curIdx] + stepDist * grid.getCost(nx, ny)
                if (tentativeG < gScore[nIdx]) {
                    cameFrom[nIdx] = curIdx
                    gScore[nIdx] = tentativeG
                    val f = tentativeG + octileHeuristic(nx, ny, goalX, goalY)
                    openSet.add(OpenNode(nIdx, f))
                }
            }
        }
        return emptyList()
    }

    fun findWorldPath(
        grid: NavGrid2D,
        startWorld: Vector2,
        goalWorld: Vector2,
        allowDiagonal: Boolean = true,
        smoothPath: Boolean = true
    ): List<Vector2> {
        val cells = findGridPath(
            grid = grid,
            startX = grid.worldToGridX(startWorld.x),
            startY = grid.worldToGridY(startWorld.y),
            goalX = grid.worldToGridX(goalWorld.x),
            goalY = grid.worldToGridY(goalWorld.y),
            allowDiagonal = allowDiagonal
        )
        if (cells.isEmpty()) return emptyList()
        val smoothed = if (smoothPath && cells.size > 2) smoothGridPath(grid, cells) else cells
        return smoothed.map { (gx, gy) -> grid.gridToWorldCenter(gx, gy) }
    }

    private fun smoothGridPath(
        grid: NavGrid2D,
        path: List<Pair<Int, Int>>
    ): List<Pair<Int, Int>> {
        val result = ArrayList<Pair<Int, Int>>()
        var anchorIdx = 0
        result.add(path[0])
        while (anchorIdx < path.size - 1) {
            var furthest = anchorIdx + 1
            for (probe in path.size - 1 downTo anchorIdx + 2) {
                val (ax, ay) = path[anchorIdx]
                val (bx, by) = path[probe]
                if (hasLineOfSight(grid, ax, ay, bx, by)) {
                    furthest = probe
                    break
                }
            }
            result.add(path[furthest])
            anchorIdx = furthest
        }
        return result
    }

    private fun hasLineOfSight(grid: NavGrid2D, x0: Int, y0: Int, x1: Int, y1: Int): Boolean {
        var cx = x0
        var cy = y0
        val dx = abs(x1 - x0)
        val dy = abs(y1 - y0)
        val sx = if (x0 < x1) 1 else -1
        val sy = if (y0 < y1) 1 else -1
        var err = dx - dy

        while (true) {
            if (!grid.isWalkable(cx, cy)) return false
            if (cx == x1 && cy == y1) return true
            val e2 = 2 * err
            if (e2 > -dy) {
                err -= dy
                cx += sx
            }
            if (e2 < dx) {
                err += dx
                cy += sy
            }
        }
    }

    private fun octileHeuristic(x0: Int, y0: Int, x1: Int, y1: Int): Float {
        val dx = abs(x1 - x0).toFloat()
        val dy = abs(y1 - y0).toFloat()
        return (dx + dy) + (1.4142135f - 2f) * min(dx, dy)
    }

    private fun reconstruct(cameFrom: IntArray, goalIdx: Int, width: Int): List<Pair<Int, Int>> {
        val reversed = ArrayList<Pair<Int, Int>>()
        var cur = goalIdx
        while (cur != -1) {
            reversed.add((cur % width) to (cur / width))
            cur = cameFrom[cur]
        }
        reversed.reverse()
        return reversed
    }
}
