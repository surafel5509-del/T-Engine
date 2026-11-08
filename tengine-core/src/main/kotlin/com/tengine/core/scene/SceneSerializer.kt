package com.tengine.core.scene

import com.tengine.core.EngineLogger
import com.tengine.core.ecs.ComponentRegistry
import com.tengine.core.ecs.GameObject
import com.tengine.core.ecs.Transform2D
import com.tengine.core.math.Vector2
import com.tengine.core.serialization.JsonArray
import com.tengine.core.serialization.JsonCodec
import com.tengine.core.serialization.JsonObject
import java.util.UUID

/**
 * Full round-trip Scene & GameObject Hierarchy JSON Serializer / Deserializer.
 * Powers T-Engine Studio's Save/Load Project files AND Unity-style Play/Stop Scene state restoration!
 */
object SceneSerializer {

    fun serializeSceneToJson(scene: Scene, pretty: Boolean = true): String =
        JsonCodec.stringify(serializeScene(scene), pretty)

    fun serializeScene(scene: Scene): JsonObject {
        val rootsArray = JsonArray()
        for (root in scene.rootObjects) {
            if (!root.isDestroyed) {
                rootsArray.add(serializeGameObjectTree(root))
            }
        }
        return JsonObject()
            .put("version", 1)
            .put("name", scene.name)
            .putVector2("gravity", scene.physicsWorld.gravity)
            .put("rootObjects", rootsArray)
    }

    fun deserializeSceneFromJson(json: String): Scene =
        deserializeScene(JsonCodec.parseObject(json))

    fun deserializeScene(data: JsonObject): Scene {
        val sceneName = data.getString("name", "Loaded Scene")
        val gravity = data.getVector2("gravity", Vector2(0f, -18f))
        val scene = Scene(name = sceneName, gravity = gravity)

        data.getArray("rootObjects")?.let { arr ->
            for (el in arr) {
                val obj = el.asObjectOrNull() ?: continue
                deserializeGameObjectTree(obj, scene, regenerateUuids = false)
            }
        }
        return scene
    }

    fun serializeGameObjectTree(go: GameObject): JsonObject {
        val compsArray = JsonArray()
        for (comp in go.components) {
            if (comp is Transform2D || comp.isDestroyed) continue
            compsArray.add(
                JsonObject()
                    .put("type", comp.typeName)
                    .put("data", comp.serialize())
            )
        }

        val childrenArray = JsonArray()
        for (childTransform in go.transform.children) {
            if (!childTransform.gameObject.isDestroyed) {
                childrenArray.add(serializeGameObjectTree(childTransform.gameObject))
            }
        }

        return JsonObject()
            .put("uuid", go.uuid)
            .put("name", go.name)
            .put("tag", go.tag)
            .put("layer", go.layer)
            .put("isStatic", go.isStatic)
            .put("activeSelf", go.activeSelf)
            .put("transform", go.transform.serialize())
            .put("components", compsArray)
            .put("children", childrenArray)
    }

    fun deserializeGameObjectTree(
        obj: JsonObject,
        scene: Scene,
        regenerateUuids: Boolean = false
    ): GameObject {
        val rawUuid = obj.getStringOrNull("uuid")
        val finalUuid = if (regenerateUuids || rawUuid.isNullOrBlank()) {
            UUID.randomUUID().toString()
        } else {
            rawUuid
        }

        val go = GameObject(
            name = obj.getString("name", "GameObject"),
            uuid = finalUuid
        )
        go.tag = obj.getString("tag", "Untagged")
        go.layer = obj.getInt("layer", 0)
        go.isStatic = obj.getBoolean("isStatic", false)
        go.activeSelf = obj.getBoolean("activeSelf", true)

        obj.getObject("transform")?.let { go.transform.deserialize(it) }

        scene.addGameObject(go)

        obj.getArray("components")?.let { comps ->
            for (el in comps) {
                val cObj = el.asObjectOrNull() ?: continue
                val typeName = cObj.getString("type")
                val cData = cObj.getObject("data") ?: JsonObject()
                val instance = ComponentRegistry.create(typeName)
                if (instance != null) {
                    instance.deserialize(cData)
                    go.addComponent(instance)
                } else {
                    EngineLogger.warn("SceneSerializer", "Unknown component type '$typeName' on '${go.name}'")
                }
            }
        }

        obj.getArray("children")?.let { children ->
            for (childEl in children) {
                val childObj = childEl.asObjectOrNull() ?: continue
                val childGo = deserializeGameObjectTree(childObj, scene, regenerateUuids)
                childGo.transform.setParent(go.transform, keepWorldPosition = false)
            }
        }

        return go
    }
}
