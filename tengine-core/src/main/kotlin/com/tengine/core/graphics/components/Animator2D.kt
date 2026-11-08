package com.tengine.core.graphics.components

import com.tengine.core.ecs.Component
import com.tengine.core.ecs.InspectorProperty
import com.tengine.core.graphics.Sprite
import com.tengine.core.graphics.SpriteLibrary
import com.tengine.core.serialization.JsonArray
import com.tengine.core.serialization.JsonObject

enum class AnimationLoopMode {
    LOOP,
    ONCE,
    PING_PONG
}

class SpriteAnimationClip(
    val name: String,
    val frames: List<Sprite>,
    var fps: Float = 10f,
    var loopMode: AnimationLoopMode = AnimationLoopMode.LOOP,
    val frameEvents: Map<Int, String> = emptyMap()
)

enum class ConditionComparison {
    IS_TRUE,
    IS_FALSE,
    GREATER_THAN,
    LESS_THAN,
    TRIGGER
}

data class TransitionCondition(
    val parameterName: String,
    val comparison: ConditionComparison,
    val threshold: Float = 0f
)

data class AnimatorTransition(
    val fromState: String, // "*" = Any State
    val toState: String,
    val conditions: List<TransitionCondition>,
    val waitClipFinish: Boolean = false
)

/**
 * Unity-style 2D Sprite Animation State Machine driving a sibling [SpriteRenderer].
 * Supports named clips, Bool/Float/Trigger parameters, conditional state transitions,
 * speed multipliers, and frame-indexed message events!
 */
class Animator2D : Component("Animator2D") {

    override val typeName: String get() = "Animator2D"

    private val clips = LinkedHashMap<String, SpriteAnimationClip>()
    private val transitions = ArrayList<AnimatorTransition>()

    private val boolParams = HashMap<String, Boolean>()
    private val floatParams = HashMap<String, Float>()
    private val activeTriggers = HashSet<String>()

    var currentStateName: String = ""
        private set

    var speedMultiplier: Float = 1f
    var isPlaying: Boolean = true

    private var frameTimer: Float = 0f
    var currentFrameIndex: Int = 0
        private set
    private var pingPongDirection: Int = 1
    var isClipFinished: Boolean = false
        private set

    fun addClip(clip: SpriteAnimationClip): Animator2D {
        clips[clip.name] = clip
        for (f in clip.frames) SpriteLibrary.registerSprite(f)
        if (currentStateName.isEmpty()) {
            play(clip.name)
        }
        return this
    }

    fun addTransition(transition: AnimatorTransition): Animator2D {
        transitions.add(transition)
        return this
    }

    fun setBool(name: String, value: Boolean) {
        boolParams[name] = value
    }

    fun getBool(name: String): Boolean = boolParams[name] ?: false

    fun setFloat(name: String, value: Float) {
        floatParams[name] = value
    }

    fun getFloat(name: String): Float = floatParams[name] ?: 0f

    fun setTrigger(name: String) {
        activeTriggers.add(name)
    }

    fun play(stateName: String, forceRestart: Boolean = false) {
        if (!forceRestart && currentStateName == stateName) return
        val clip = clips[stateName] ?: return
        currentStateName = stateName
        currentFrameIndex = 0
        frameTimer = 0f
        pingPongDirection = 1
        isClipFinished = false
        isPlaying = true
        applyCurrentFrame(clip)
    }

    override fun onUpdate(dt: Float) {
        evaluateTransitions()

        if (!isPlaying) return
        val clip = clips[currentStateName] ?: return
        if (clip.frames.isEmpty()) return

        val effectiveFps = clip.fps * speedMultiplier
        if (effectiveFps <= 0.001f) return
        val frameDuration = 1f / effectiveFps

        frameTimer += dt
        while (frameTimer >= frameDuration) {
            frameTimer -= frameDuration
            advanceFrame(clip)
        }
    }

    private fun evaluateTransitions() {
        for (t in transitions) {
            if (t.fromState != "*" && t.fromState != currentStateName) continue
            if (t.toState == currentStateName) continue
            if (t.waitClipFinish && !isClipFinished) continue

            var allMatched = true
            for (cond in t.conditions) {
                val ok = when (cond.comparison) {
                    ConditionComparison.IS_TRUE -> getBool(cond.parameterName)
                    ConditionComparison.IS_FALSE -> !getBool(cond.parameterName)
                    ConditionComparison.GREATER_THAN -> getFloat(cond.parameterName) > cond.threshold
                    ConditionComparison.LESS_THAN -> getFloat(cond.parameterName) < cond.threshold
                    ConditionComparison.TRIGGER -> activeTriggers.contains(cond.parameterName)
                }
                if (!ok) {
                    allMatched = false
                    break
                }
            }

            if (allMatched) {
                for (cond in t.conditions) {
                    if (cond.comparison == ConditionComparison.TRIGGER) {
                        activeTriggers.remove(cond.parameterName)
                    }
                }
                play(t.toState, forceRestart = true)
                return
            }
        }
    }

    private fun advanceFrame(clip: SpriteAnimationClip) {
        val count = clip.frames.size
        if (count <= 1) {
            currentFrameIndex = 0
            isClipFinished = clip.loopMode == AnimationLoopMode.ONCE
            return
        }

        when (clip.loopMode) {
            AnimationLoopMode.LOOP -> {
                currentFrameIndex = (currentFrameIndex + 1) % count
            }
            AnimationLoopMode.ONCE -> {
                if (currentFrameIndex < count - 1) {
                    currentFrameIndex++
                } else {
                    isClipFinished = true
                    isPlaying = false
                }
            }
            AnimationLoopMode.PING_PONG -> {
                currentFrameIndex += pingPongDirection
                if (currentFrameIndex >= count - 1) {
                    currentFrameIndex = count - 1
                    pingPongDirection = -1
                } else if (currentFrameIndex <= 0) {
                    currentFrameIndex = 0
                    pingPongDirection = 1
                }
            }
        }

        applyCurrentFrame(clip)
        clip.frameEvents[currentFrameIndex]?.let { eventMsg ->
            if (isAttached) gameObject.sendMessage(eventMsg, currentFrameIndex)
        }
    }

    private fun applyCurrentFrame(clip: SpriteAnimationClip) {
        if (!isAttached || clip.frames.isEmpty()) return
        val sr = getComponent(SpriteRenderer::class) ?: return
        sr.sprite = clip.frames[currentFrameIndex.coerceIn(0, clip.frames.size - 1)]
    }

    override fun serialize(): JsonObject {
        val clipsArr = JsonArray()
        for (clip in clips.values) {
            val framesArr = JsonArray()
            for (f in clip.frames) framesArr.add(f.name)
            clipsArr.add(
                JsonObject()
                    .put("name", clip.name)
                    .put("fps", clip.fps)
                    .put("loopMode", clip.loopMode.name)
                    .put("frames", framesArr)
            )
        }
        return super.serialize()
            .put("currentState", currentStateName)
            .put("speedMultiplier", speedMultiplier)
            .put("clips", clipsArr)
    }

    override fun deserialize(data: JsonObject) {
        super.deserialize(data)
        speedMultiplier = data.getFloat("speedMultiplier", 1f)
        data.getArray("clips")?.let { arr ->
            for (el in arr) {
                val o = el.asObjectOrNull() ?: continue
                val name = o.getString("name")
                val fps = o.getFloat("fps", 10f)
                val loop = runCatching { AnimationLoopMode.valueOf(o.getString("loopMode", "LOOP")) }
                    .getOrDefault(AnimationLoopMode.LOOP)
                val frames = o.getArray("frames")?.mapNotNull { f ->
                    f.asStringOrNull()?.let { SpriteLibrary.resolve(it) }
                } ?: emptyList()
                if (name.isNotEmpty() && frames.isNotEmpty()) {
                    addClip(SpriteAnimationClip(name, frames, fps, loop))
                }
            }
        }
        val state = data.getString("currentState", "")
        if (state.isNotEmpty()) play(state)
    }

    override fun inspectableProperties(): List<InspectorProperty> = listOf(
        InspectorProperty.FloatProp("Speed", 0f, 5f, 0.1f, { speedMultiplier }, { speedMultiplier = it }),
        InspectorProperty.BoolProp("Playing", { isPlaying }, { isPlaying = it }),
        InspectorProperty.EnumProp(
            label = "Active Clip",
            options = clips.keys.toList().ifEmpty { listOf("None") },
            getter = { currentStateName.ifEmpty { "None" } },
            setter = { if (it != "None") play(it, forceRestart = true) }
        )
    )
}
