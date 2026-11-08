package com.tengine.core.scene

import com.tengine.core.EngineLogger
import com.tengine.core.audio.AudioMixer

/**
 * Manages active Scene loading, transitions, and registered Scene factories.
 */
class SceneManager(private val audioMixer: AudioMixer) {

    private val sceneBuilders = LinkedHashMap<String, () -> Scene>()

    var activeScene: Scene? = null
        private set

    var pendingScene: Scene? = null
        private set

    var onSceneLoaded: ((Scene) -> Unit)? = null

    fun registerScene(sceneName: String, builder: () -> Scene) {
        sceneBuilders[sceneName] = builder
    }

    fun registeredSceneNames(): List<String> = sceneBuilders.keys.toList()

    fun loadScene(sceneName: String): Boolean {
        val builder = sceneBuilders[sceneName]
        if (builder == null) {
            EngineLogger.warn("SceneManager", "Scene '$sceneName' not found in registry")
            return false
        }
        loadSceneImmediate(builder())
        return true
    }

    fun loadSceneDeferred(scene: Scene) {
        pendingScene = scene
    }

    fun loadSceneImmediate(scene: Scene) {
        pendingScene = null
        activeScene?.unload()
        scene.audioMixer = audioMixer
        activeScene = scene
        scene.initializeScene()
        EngineLogger.info("SceneManager", "Loaded scene '${scene.name}' (${scene.allGameObjects().size} objects)")
        onSceneLoaded?.invoke(scene)
    }

    internal fun applyPendingTransitionIfNeeded() {
        val next = pendingScene ?: return
        loadSceneImmediate(next)
    }
}
