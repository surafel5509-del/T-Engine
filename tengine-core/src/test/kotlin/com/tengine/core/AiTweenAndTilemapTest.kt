package com.tengine.core

import com.tengine.core.ai.AStarPathfinder
import com.tengine.core.ai.BehaviorTree
import com.tengine.core.ai.NavGrid2D
import com.tengine.core.ai.NodeStatus
import com.tengine.core.graphics.components.Tilemap
import com.tengine.core.math.Easing
import com.tengine.core.math.Vector2
import com.tengine.core.physics.BoxCollider2D
import com.tengine.core.scene.Scene
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiTweenAndTilemapTest {

    @Test
    fun testAStarPathfinderNavigatesAroundWall() {
        val grid = NavGrid2D(width = 10, height = 10, cellSize = 1f)
        // Vertical wall blocking x=5 from y=0..7
        for (y in 0..7) {
            grid.setWalkable(5, y, false)
        }
        val path = AStarPathfinder.findGridPath(grid, startX = 2, startY = 2, goalX = 8, goalY = 2)
        assertTrue(path.isNotEmpty())
        assertEquals(2 to 2, path.first())
        assertEquals(8 to 2, path.last())
        assertTrue(path.none { (gx, gy) -> gx == 5 && gy <= 7 })
    }

    @Test
    fun testBehaviorTreeSelectorAndSequence() {
        val tree = BehaviorTree(
            BehaviorTree.selector(
                BehaviorTree.sequence(
                    BehaviorTree.condition { bb -> (bb.get<Int>("hp") ?: 100) < 20 },
                    BehaviorTree.action { bb, _ ->
                        bb["action"] = "FLEE"
                        NodeStatus.SUCCESS
                    }
                ),
                BehaviorTree.action { bb, _ ->
                    bb["action"] = "PATROL"
                    NodeStatus.SUCCESS
                }
            )
        )
        tree.blackboard["hp"] = 80
        tree.tick(0.016f)
        assertEquals("PATROL", tree.blackboard.get<String>("action"))

        tree.blackboard["hp"] = 10
        tree.tick(0.016f)
        assertEquals("FLEE", tree.blackboard.get<String>("action"))
    }

    @Test
    fun testTilemapGreedyColliderMergingAndTweens() {
        val scene = Scene("TileTest")
        val mapObj = scene.createGameObject("WorldMap")
        val tilemap = mapObj.addComponent(Tilemap(cellSize = 1f))
        // Paint a 10x2 solid rectangular floor (20 tiles -> should merge into 1 BoxCollider2D!)
        tilemap.fillRect(0, 0, 9, 1, tileId = 1)
        tilemap.rebuildColliders()

        val colliders = mapObj.getComponents(BoxCollider2D::class)
        assertEquals(1, colliders.size)
        assertEquals(10f, colliders[0].size.x, 1e-3f)
        assertEquals(2f, colliders[0].size.y, 1e-3f)

        val mover = scene.createGameObject("TweenTarget", Vector2(0f, 0f))
        scene.tweens.tweenPosition(mover.transform, Vector2(10f, 5f), duration = 1.0f, easing = Easing.LINEAR)
        scene.tweens.update(0.5f)
        assertEquals(5f, mover.transform.localX, 1e-2f)
        scene.tweens.update(0.5f)
        assertEquals(10f, mover.transform.localX, 1e-2f)
    }
}
