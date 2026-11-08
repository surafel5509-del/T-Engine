package com.tengine.core.ecs

import com.tengine.core.audio.AudioListener2D
import com.tengine.core.audio.AudioSource2D
import com.tengine.core.graphics.Camera2D
import com.tengine.core.graphics.components.Animator2D
import com.tengine.core.graphics.components.Light2D
import com.tengine.core.graphics.components.ParallaxLayer
import com.tengine.core.graphics.components.ParticleSystem2D
import com.tengine.core.graphics.components.SpriteRenderer
import com.tengine.core.graphics.components.Tilemap
import com.tengine.core.graphics.components.TrailRenderer2D
import com.tengine.core.physics.BoxCollider2D
import com.tengine.core.physics.CircleCollider2D
import com.tengine.core.physics.DistanceJoint2D
import com.tengine.core.physics.PolygonCollider2D
import com.tengine.core.physics.Rigidbody2D
import com.tengine.core.physics.SpringJoint2D

/**
 * Central Component Factory Registry used by SceneSerializer, Prefabs, and T-Engine Studio's
 * "Add Component" menu. Supports registering custom game Behaviour scripts cleanly.
 */
object ComponentRegistry {

    data class Registration(
        val typeName: String,
        val category: String,
        val factory: () -> Component
    )

    private val factories = LinkedHashMap<String, Registration>()

    init {
        registerDefaults()
    }

    fun registerDefaults() {
        register("SpriteRenderer", "Rendering") { SpriteRenderer() }
        register("Animator2D", "Rendering") { Animator2D() }
        register("ParticleSystem2D", "Rendering") { ParticleSystem2D() }
        register("Tilemap", "Rendering") { Tilemap() }
        register("Light2D", "Rendering") { Light2D() }
        register("TrailRenderer2D", "Rendering") { TrailRenderer2D() }
        register("ParallaxLayer", "Rendering") { ParallaxLayer() }
        register("Camera2D", "Rendering") { Camera2D() }

        register("Rigidbody2D", "Physics 2D") { Rigidbody2D() }
        register("BoxCollider2D", "Physics 2D") { BoxCollider2D() }
        register("CircleCollider2D", "Physics 2D") { CircleCollider2D() }
        register("PolygonCollider2D", "Physics 2D") { PolygonCollider2D() }
        register("DistanceJoint2D", "Physics 2D") { DistanceJoint2D() }
        register("SpringJoint2D", "Physics 2D") { SpringJoint2D() }

        register("AudioSource2D", "Audio") { AudioSource2D() }
        register("AudioListener2D", "Audio") { AudioListener2D() }
    }

    fun register(typeName: String, category: String = "Scripts", factory: () -> Component) {
        factories[typeName] = Registration(typeName, category, factory)
    }

    fun create(typeName: String): Component? = factories[typeName]?.factory?.invoke()

    fun allRegistrations(): List<Registration> = factories.values.toList()
}
