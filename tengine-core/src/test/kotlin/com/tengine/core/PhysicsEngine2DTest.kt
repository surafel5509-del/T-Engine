package com.tengine.core

import com.tengine.core.ecs.Behaviour
import com.tengine.core.math.Vector2
import com.tengine.core.physics.BodyType
import com.tengine.core.physics.BoxCollider2D
import com.tengine.core.physics.CircleCollider2D
import com.tengine.core.physics.Collider2D
import com.tengine.core.physics.PhysicsMaterial2D
import com.tengine.core.physics.Rigidbody2D
import com.tengine.core.scene.Scene
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhysicsEngine2DTest {

    @Test
    fun testDynamicBoxSettlesOnStaticFloor() {
        val scene = Scene("PhysicsStackTest", gravity = Vector2(0f, -10f))

        val floor = scene.createGameObject("Floor", Vector2(0f, -1f))
        floor.addComponent(Rigidbody2D(BodyType.STATIC))
        floor.addComponent(BoxCollider2D(Vector2(20f, 2f))) // Top surface at y = 0

        val crate = scene.createGameObject("Crate", Vector2(0f, 3f))
        crate.addComponent(Rigidbody2D(BodyType.DYNAMIC).apply { mass = 2f })
        crate.addComponent(BoxCollider2D(Vector2(1f, 1f)).apply {
            material = PhysicsMaterial2D("Wood", friction = 0.6f, restitution = 0.0f)
        })

        scene.initializeScene()
        repeat(180) {
            scene.stepFixedUpdate(1f / 60f)
        }

        // Crate center should settle near y = 0.5 (half-height above floor top y = 0)
        assertEquals(0.5f, crate.transform.worldY, 0.08f)
        assertTrue(kotlin.math.abs(crate.getComponent<Rigidbody2D>()!!.linearVelocity.y) < 0.1f)
    }

    @Test
    fun testTriggerEnterAndExitCallbacks() {
        val scene = Scene("TriggerTest", gravity = Vector2.ZERO)
        var entered = 0
        var exited = 0

        val sensor = scene.createGameObject("Zone", Vector2(0f, 0f))
        sensor.addComponent(BoxCollider2D(Vector2(2f, 2f)).apply { isTrigger = true })
        sensor.addComponent(object : Behaviour("ZoneWatcher") {
            override fun onTriggerEnter2D(other: Collider2D) {
                entered++
            }
            override fun onTriggerExit2D(other: Collider2D) {
                exited++
            }
        })

        val mover = scene.createGameObject("Mover", Vector2(-3f, 0f))
        val rb = mover.addComponent(Rigidbody2D(BodyType.KINEMATIC))
        mover.addComponent(CircleCollider2D(0.5f))
        rb.linearVelocity.set(6f, 0f)

        scene.initializeScene()
        repeat(90) {
            scene.stepFixedUpdate(1f / 60f)
        }

        assertEquals(1, entered)
        assertEquals(1, exited)
    }

    @Test
    fun testPhysicsRaycastHitsClosestCollider() {
        val scene = Scene("RayTest")
        val nearWall = scene.createGameObject("NearWall", Vector2(4f, 0f))
        nearWall.addComponent(BoxCollider2D(Vector2(1f, 4f)))

        val farWall = scene.createGameObject("FarWall", Vector2(10f, 0f))
        farWall.addComponent(BoxCollider2D(Vector2(1f, 4f)))

        scene.initializeScene()
        val hit = scene.physicsWorld.raycast(Vector2(0f, 0f), Vector2(1f, 0f), maxDistance = 25f)
        assertNotNull(hit)
        assertEquals("NearWall", hit!!.collider.gameObject.name)
        assertEquals(3.5f, hit.distance, 0.02f)
    }
}
