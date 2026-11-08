package com.tengine.core.graphics.components

import com.tengine.core.Time
import com.tengine.core.ecs.Component
import com.tengine.core.ecs.InspectorProperty
import com.tengine.core.graphics.SortingLayer
import com.tengine.core.graphics.Sprite
import com.tengine.core.graphics.SpriteBatch
import com.tengine.core.graphics.SpriteLibrary
import com.tengine.core.math.AABB
import com.tengine.core.math.Color
import com.tengine.core.math.Vector2
import com.tengine.core.physics.BoxCollider2D
import com.tengine.core.physics.PhysicsMaterial2D
import com.tengine.core.serialization.JsonArray
import com.tengine.core.serialization.JsonObject
import kotlin.math.floor

/**
 * Definition for a single tile type in a [Tilemap] palette.
 */
data class TileDefinition(
    val id: Int,
    val name: String,
    val sprite: Sprite = Sprite.WHITE_SQUARE,
    val tint: Color = Color.WHITE,
    val solidCollision: Boolean = true,
    val animatedFrames: List<Sprite> = emptyList(),
    val animationFps: Float = 6f,
    val friction: Float = 0.5f
) {
    fun currentSprite(elapsedTime: Float): Sprite {
        if (animatedFrames.isEmpty()) return sprite
        val idx = (elapsedTime * animationFps).toInt().mod(animatedFrames.size)
        return animatedFrames[idx]
    }
}

/**
 * High-performance 2D Tilemap Renderer & Merged Collision Generator.
 * - Sparse cell map supporting large open worlds without allocating empty air arrays
 * - Frustum-culled rendering (only draws visible tiles inside Camera2D bounds!)
 * - Greedy strip/rectangle collider merging so 500 contiguous floor tiles merge into a handful of BoxCollider2Ds!
 */
class Tilemap(
    var cellSize: Float = 1.0f
) : Component("Tilemap") {

    override val typeName: String get() = "Tilemap"

    var sortingLayer: SortingLayer = SortingLayer.TERRAIN
    var orderInLayer: Int = 0
    var isLit: Boolean = false

    private val palette = LinkedHashMap<Int, TileDefinition>()
    private val cells = HashMap<Long, Int>(512)
    private val generatedColliders = ArrayList<BoxCollider2D>()
    private var collidersDirty: Boolean = false

    val tileCount: Int get() = cells.size

    init {
        // Default starter palette so users painting in T-Engine Studio immediately have rich tiles!
        registerTile(TileDefinition(1, "StoneBlock", Sprite.WHITE_SQUARE, Color(0.32f, 0.36f, 0.45f, 1f), solidCollision = true))
        registerTile(TileDefinition(2, "GrassTop", Sprite.WHITE_SQUARE, Color(0.24f, 0.78f, 0.42f, 1f), solidCollision = true))
        registerTile(TileDefinition(3, "NeonPlatform", Sprite.WHITE_SQUARE, Color(0.14f, 0.82f, 0.96f, 1f), solidCollision = true))
        registerTile(TileDefinition(4, "HazardLava", Sprite.WHITE_SQUARE, Color(0.98f, 0.32f, 0.16f, 1f), solidCollision = false))
        registerTile(TileDefinition(5, "BackBrick", Sprite.CHECKER, Color(0.20f, 0.22f, 0.30f, 0.75f), solidCollision = false))
    }

    fun registerTile(tile: TileDefinition) {
        palette[tile.id] = tile
        SpriteLibrary.registerSprite(tile.sprite)
    }

    fun getPaletteTiles(): List<TileDefinition> = palette.values.toList()

    fun setTile(gridX: Int, gridY: Int, tileId: Int) {
        val key = packCoord(gridX, gridY)
        if (tileId <= 0) {
            if (cells.remove(key) != null) {
                collidersDirty = true
            }
        } else {
            if (cells.put(key, tileId) != tileId) {
                collidersDirty = true
            }
        }
    }

    fun fillRect(minGX: Int, minGY: Int, maxGX: Int, maxGY: Int, tileId: Int) {
        for (gy in minGY..maxGY) {
            for (gx in minGX..maxGX) {
                setTile(gx, gy, tileId)
            }
        }
    }

    fun getTile(gridX: Int, gridY: Int): Int = cells[packCoord(gridX, gridY)] ?: 0

    fun worldToCellX(worldX: Float): Int {
        val originX = if (isAttached) transform.worldX else 0f
        return floor((worldX - originX) / cellSize).toInt()
    }

    fun worldToCellY(worldY: Float): Int {
        val originY = if (isAttached) transform.worldY else 0f
        return floor((worldY - originY) / cellSize).toInt()
    }

    fun cellCenterWorld(gridX: Int, gridY: Int): Vector2 {
        val ox = if (isAttached) transform.worldX else 0f
        val oy = if (isAttached) transform.worldY else 0f
        return Vector2(ox + (gridX + 0.5f) * cellSize, oy + (gridY + 0.5f) * cellSize)
    }

    override fun onStart() {
        if (collidersDirty) rebuildColliders()
    }

    override fun onUpdate(dt: Float) {
        if (collidersDirty) rebuildColliders()
    }

    /**
     * Greedy 2D rectangle merging algorithm combining contiguous solid tiles into minimal BoxCollider2Ds!
     */
    fun rebuildColliders() {
        collidersDirty = false
        if (!isAttached) return

        for (col in generatedColliders) {
            gameObject.removeComponent(col)
        }
        generatedColliders.clear()

        val solidCoords = HashSet<Long>()
        var minX = Int.MAX_VALUE; var maxX = Int.MIN_VALUE
        var minY = Int.MAX_VALUE; var maxY = Int.MIN_VALUE

        for ((key, tileId) in cells) {
            val def = palette[tileId] ?: continue
            if (!def.solidCollision) continue
            solidCoords.add(key)
            val gx = unpackX(key)
            val gy = unpackY(key)
            if (gx < minX) minX = gx
            if (gx > maxX) maxX = gx
            if (gy < minY) minY = gy
            if (gy > maxY) maxY = gy
        }

        if (solidCoords.isEmpty()) return

        val visited = HashSet<Long>(solidCoords.size)
        for (gy in minY..maxY) {
            var gx = minX
            while (gx <= maxX) {
                val startKey = packCoord(gx, gy)
                if (!solidCoords.contains(startKey) || visited.contains(startKey)) {
                    gx++
                    continue
                }

                // Expand width along +X
                var runWidth = 1
                while (gx + runWidth <= maxX) {
                    val nextKey = packCoord(gx + runWidth, gy)
                    if (solidCoords.contains(nextKey) && !visited.contains(nextKey)) {
                        runWidth++
                    } else {
                        break
                    }
                }

                // Expand height along +Y if entire row matches
                var runHeight = 1
                while (gy + runHeight <= maxY) {
                    var fullRowSolid = true
                    for (dx in 0 until runWidth) {
                        val checkKey = packCoord(gx + dx, gy + runHeight)
                        if (!solidCoords.contains(checkKey) || visited.contains(checkKey)) {
                            fullRowSolid = false
                            break
                        }
                    }
                    if (fullRowSolid) runHeight++ else break
                }

                for (dy in 0 until runHeight) {
                    for (dx in 0 until runWidth) {
                        visited.add(packCoord(gx + dx, gy + dy))
                    }
                }

                val boxW = runWidth * cellSize
                val boxH = runHeight * cellSize
                val centerX = (gx + runWidth * 0.5f) * cellSize
                val centerY = (gy + runHeight * 0.5f) * cellSize

                val box = BoxCollider2D(Vector2(boxW, boxH)).apply {
                    offset = Vector2(centerX, centerY)
                    material = PhysicsMaterial2D("TileSurface", friction = 0.55f, restitution = 0.0f)
                }
                gameObject.addComponent(box)
                generatedColliders.add(box)

                gx += runWidth
            }
        }
    }

    fun render(batch: SpriteBatch, visibleBounds: AABB) {
        if (cells.isEmpty()) return
        val originX = if (isAttached) transform.worldX else 0f
        val originY = if (isAttached) transform.worldY else 0f

        val minGX = floor((visibleBounds.minX - originX) / cellSize).toInt() - 1
        val maxGX = floor((visibleBounds.maxX - originX) / cellSize).toInt() + 1
        val minGY = floor((visibleBounds.minY - originY) / cellSize).toInt() - 1
        val maxGY = floor((visibleBounds.maxY - originY) / cellSize).toInt() + 1

        batch.setState(com.tengine.core.graphics.BlendMode.ALPHA, isLit)
        val t = Time.time

        val totalQueryArea = (maxGX - minGX + 1).toLong() * (maxGY - minGY + 1).toLong()
        if (totalQueryArea < cells.size) {
            for (gy in minGY..maxGY) {
                val wy = originY + (gy + 0.5f) * cellSize
                for (gx in minGX..maxGX) {
                    val tileId = cells[packCoord(gx, gy)] ?: continue
                    val def = palette[tileId] ?: continue
                    val wx = originX + (gx + 0.5f) * cellSize
                    batch.drawSprite(
                        sprite = def.currentSprite(t),
                        worldX = wx,
                        worldY = wy,
                        width = cellSize,
                        height = cellSize,
                        color = def.tint
                    )
                }
            }
        } else {
            for ((key, tileId) in cells) {
                val gx = unpackX(key)
                val gy = unpackY(key)
                if (gx !in minGX..maxGX || gy !in minGY..maxGY) continue
                val def = palette[tileId] ?: continue
                val wx = originX + (gx + 0.5f) * cellSize
                val wy = originY + (gy + 0.5f) * cellSize
                batch.drawSprite(
                    sprite = def.currentSprite(t),
                    worldX = wx,
                    worldY = wy,
                    width = cellSize,
                    height = cellSize,
                    color = def.tint
                )
            }
        }
    }

    override fun serialize(): JsonObject {
        val tileArr = JsonArray()
        for ((key, id) in cells) {
            tileArr.add(
                JsonObject()
                    .put("x", unpackX(key))
                    .put("y", unpackY(key))
                    .put("id", id)
            )
        }
        return super.serialize()
            .put("cellSize", cellSize)
            .put("sortingLayer", sortingLayer.name)
            .put("orderInLayer", orderInLayer)
            .put("isLit", isLit)
            .put("tiles", tileArr)
    }

    override fun deserialize(data: JsonObject) {
        super.deserialize(data)
        cellSize = data.getFloat("cellSize", 1.0f).coerceAtLeast(0.1f)
        sortingLayer = SortingLayer.fromName(data.getString("sortingLayer", "TERRAIN"))
        orderInLayer = data.getInt("orderInLayer", 0)
        isLit = data.getBoolean("isLit", false)
        cells.clear()
        data.getArray("tiles")?.let { arr ->
            for (el in arr) {
                val o = el.asObjectOrNull() ?: continue
                val gx = o.getInt("x")
                val gy = o.getInt("y")
                val id = o.getInt("id")
                if (id > 0) cells[packCoord(gx, gy)] = id
            }
        }
        collidersDirty = true
    }

    override fun inspectableProperties(): List<InspectorProperty> = listOf(
        InspectorProperty.FloatProp("Cell Size", 0.25f, 10f, 0.25f, { cellSize }, { cellSize = it; collidersDirty = true }),
        InspectorProperty.BoolProp("2D Lit", { isLit }, { isLit = it }),
        InspectorProperty.IntProp("Order in Layer", -500, 500, { orderInLayer }, { orderInLayer = it })
    )

    private fun packCoord(gx: Int, gy: Int): Long =
        (gx.toLong() shl 32) or (gy.toLong() and 0xFFFFFFFFL)

    private fun unpackX(key: Long): Int = (key shr 32).toInt()
    private fun unpackY(key: Long): Int = key.toInt()
}
