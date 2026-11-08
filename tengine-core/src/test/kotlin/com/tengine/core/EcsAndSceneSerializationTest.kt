package com.tengine.core

import com.tengine.core.ecs.Prefab
import com.tengine.core.graphics.Camera2D
import com.tengine.core.graphics.components.SpriteRenderer
import com.tengine.core.math.Color
import com.tengine.core.math.Vector2
import com.tengine.core.physics.BodyType
import com.tengine.core.physics.BoxCollider2D
import com.tengine.core.physics.Rigidbody2D
import com.tengine.core.scene.Scene
import com.tengine.core.scene.SceneSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class EcsAndSceneSerializationTest {

    @Test
    fun testTransformHierarchyWorldToLocal() {
        val scene = Scene("HierarchyTest")
        val parent = scene.createGameObject("Parent", Vector2(10f, 5f))
        parent.transform.localScale = Vector2(2f, 2f)

        val child = scene.createGameObject("Child", Vector2(1f, 2f))
        child.transform.setParent(parent.transform, keepWorldPosition = false)

        assertEquals(12f, child.transform.worldX, 1e-4f)
        assertEquals(9f, child.transform.worldY, 1e-4f)

        child.transform.worldPosition = Vector2(14f, 5f)
        assertEquals(2f, child.transform.localX, 1e-4f)
        assertEquals(0f, child.transform.localY, 1e-4f)
    }

    @Test
    fun testFullSceneJsonRoundTripAndPrefabInstantiation() {
        val original = Scene("Level_01", gravity = Vector2(0f, -22f))
        val camObj = original.createGameObject("MainCamera", Vector2(2f, 4f))
        camObj.addComponent(Camera2D(6.5f))

        val hero = original.createGameObject("Knight", Vector2(-3f, 1.5f))
        hero.tag = "Player"
        hero.addComponent(SpriteRenderer(color = Color.CYAN))
        hero.addComponent(Rigidbody2D(BodyType.DYNAMIC).apply { mass = 1.8f })
        hero.addComponent(BoxCollider2D(Vector2(0.8f, 1.4f)))

        val json = SceneSerializer.serializeSceneToJson(original, pretty = true)
        val restored = SceneSerializer.deserializeSceneFromJson(json)

        assertEquals("Level_01", restored.name)
        assertEquals(-22f, restored.physicsWorld.gravity.y, 1e-3f)

        val restoredHero = restored.find("Knight")
        assertNotNull(restoredHero)
        assertEquals("Player", restoredHero!!.tag)
        assertEquals(-3f, restoredHero.transform.localX, 1e-3f)
        assertEquals(1.8f, restoredHero.getComponent<Rigidbody2D>()!!.mass, 1e-3f)

        val prefab = Prefab.fromGameObject(restoredHero)
        val clone = prefab.instantiate(restored, position = Vector2(8f, 2f))
        assertNotEquals(restoredHero.uuid, clone.uuid)
        assertEquals(8f, clone.transform.worldX, 1e-3f)
    }
}
