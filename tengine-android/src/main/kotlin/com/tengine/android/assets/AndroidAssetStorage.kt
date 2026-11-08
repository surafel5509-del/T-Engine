package com.tengine.android.assets

import android.content.Context
import com.tengine.core.EngineLogger
import com.tengine.core.scene.Scene
import com.tengine.core.scene.SceneSerializer
import com.tengine.core.serialization.JsonCodec
import com.tengine.core.serialization.JsonObject
import java.io.File

/**
 * Manages reading bundled Android `assets/` Scenes AND saving/loading user-authored
 * T-Engine Studio Scenes & persistent Game Save Slots in app storage!
 */
class AndroidAssetStorage(private val context: Context) {

    private val projectsDir = File(context.filesDir, "tengine_scenes").apply { mkdirs() }
    private val prefs = context.getSharedPreferences("tengine_game_saves", Context.MODE_PRIVATE)

    fun readAssetText(assetPath: String): String? {
        return try {
            context.assets.open(assetPath).bufferedReader(Charsets.UTF_8).use { it.readText() }
        } catch (e: Exception) {
            EngineLogger.error("AndroidAssetStorage", "Unable to read asset '$assetPath'", e)
            null
        }
    }

    fun loadSceneFromAssets(assetPath: String): Scene? {
        val text = readAssetText(assetPath) ?: return null
        return try {
            SceneSerializer.deserializeSceneFromJson(text)
        } catch (e: Exception) {
            EngineLogger.error("AndroidAssetStorage", "Invalid scene JSON in asset '$assetPath'", e)
            null
        }
    }

    fun saveUserScene(fileName: String, scene: Scene): File {
        val cleanName = fileName.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").ifBlank { "scene" }
        val target = File(projectsDir, "$cleanName.tescene.json")
        val json = SceneSerializer.serializeSceneToJson(scene, pretty = true)
        target.writeText(json, Charsets.UTF_8)
        EngineLogger.info("AndroidAssetStorage", "Saved scene '${scene.name}' -> ${target.name}")
        return target
    }

    fun loadUserScene(fileName: String): Scene? {
        val cleanName = fileName.removeSuffix(".tescene.json")
        val target = File(projectsDir, "$cleanName.tescene.json")
        if (!target.exists()) return null
        return try {
            SceneSerializer.deserializeSceneFromJson(target.readText(Charsets.UTF_8))
        } catch (e: Exception) {
            EngineLogger.error("AndroidAssetStorage", "Failed loading user scene '${target.name}'", e)
            null
        }
    }

    fun listSavedScenes(): List<String> =
        projectsDir.listFiles()
            ?.filter { it.name.endsWith(".tescene.json") }
            ?.map { it.name.removeSuffix(".tescene.json") }
            ?.sorted()
            ?: emptyList()

    // Persistent Save Slots for Games (High Scores, Unlocks, Progress)
    fun saveSlot(slotKey: String, data: JsonObject) {
        prefs.edit().putString(slotKey, JsonCodec.stringify(data, pretty = false)).apply()
    }

    fun loadSlot(slotKey: String): JsonObject {
        val raw = prefs.getString(slotKey, null) ?: return JsonObject()
        return runCatching { JsonCodec.parseObject(raw) }.getOrDefault(JsonObject())
    }
}
