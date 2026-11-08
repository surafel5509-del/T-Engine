package com.tengine.core.ecs

import com.tengine.core.math.Vector2
import com.tengine.core.scene.Scene
import com.tengine.core.scene.SceneSerializer
import com.tengine.core.serialization.JsonCodec
import com.tengine.core.serialization.JsonObject

/**
 * Reusable serialized GameObject hierarchy template (modeled after Unity Prefabs).
 */
class Prefab(
    val name: String,
    val rootData: JsonObject
) {
    fun instantiate(
        scene: Scene,
        position: Vector2? = null,
        rotationDeg: Float? = null,
        parent: Transform2D? = null
    ): GameObject {
        val instance = SceneSerializer.deserializeGameObjectTree(
            obj = rootData,
            scene = scene,
            regenerateUuids = true
        )
        if (parent != null) {
            instance.transform.setParent(parent, keepWorldPosition = false)
        }
        if (position != null) {
            instance.transform.worldPosition = position
        }
        if (rotationDeg != null) {
            instance.transform.worldRotationDeg = rotationDeg
        }
        return instance
    }

    fun toJsonString(pretty: Boolean = true): String = JsonCodec.stringify(rootData, pretty)

    companion object {
        fun fromGameObject(source: GameObject): Prefab =
            Prefab(source.name, SceneSerializer.serializeGameObjectTree(source))

        fun fromJsonString(name: String, json: String): Prefab =
            Prefab(name, JsonCodec.parseObject(json))
    }
}
