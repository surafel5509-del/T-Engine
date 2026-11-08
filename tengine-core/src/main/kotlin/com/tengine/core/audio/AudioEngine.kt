package com.tengine.core.audio

import com.tengine.core.ecs.Component
import com.tengine.core.ecs.InspectorProperty
import com.tengine.core.math.Mathf
import com.tengine.core.serialization.JsonObject

enum class AudioChannel {
    MASTER,
    MUSIC,
    SFX,
    UI
}

enum class SynthPreset {
    JUMP,
    LASER,
    COIN,
    EXPLOSION,
    HIT,
    POWER_UP,
    DASH
}

/**
 * Platform Audio Backend interface supporting both asset sound files AND built-in procedural SFX presets!
 */
interface AudioBackend {
    fun playSfx(assetPath: String, volume: Float = 1f, pitch: Float = 1f, pan: Float = 0f)
    fun playSynth(preset: SynthPreset, volume: Float = 0.8f, pitch: Float = 1f, pan: Float = 0f)
    fun playMusic(assetPath: String, loop: Boolean = true, volume: Float = 0.7f)
    fun stopMusic()
    fun pauseAll()
    fun resumeAll()
    fun release()
}

class NullAudioBackend : AudioBackend {
    var playedSfxCount: Int = 0
        private set

    override fun playSfx(assetPath: String, volume: Float, pitch: Float, pan: Float) {
        playedSfxCount++
    }

    override fun playSynth(preset: SynthPreset, volume: Float, pitch: Float, pan: Float) {
        playedSfxCount++
    }

    override fun playMusic(assetPath: String, loop: Boolean, volume: Float) {}
    override fun stopMusic() {}
    override fun pauseAll() {}
    override fun resumeAll() {}
    override fun release() {}
}

/**
 * Multi-channel Audio Mixer routing SFX, Music, and Procedural Synth playback.
 */
class AudioMixer(var backend: AudioBackend = NullAudioBackend()) {
    private val channelVolumes = EnumMapLike()
    var muted: Boolean = false

    private class EnumMapLike {
        private val values = FloatArray(AudioChannel.entries.size) { 1f }
        operator fun get(ch: AudioChannel): Float = values[ch.ordinal]
        operator fun set(ch: AudioChannel, v: Float) {
            values[ch.ordinal] = Mathf.clamp01(v)
        }
    }

    fun setVolume(channel: AudioChannel, volume: Float) {
        channelVolumes[channel] = volume
    }

    fun getVolume(channel: AudioChannel): Float = channelVolumes[channel]

    private fun effectiveVolume(channel: AudioChannel, clipVolume: Float): Float {
        if (muted) return 0f
        return Mathf.clamp01(channelVolumes[AudioChannel.MASTER] * channelVolumes[channel] * clipVolume)
    }

    fun playSfx(
        assetPath: String,
        volume: Float = 1f,
        pitch: Float = 1f,
        pan: Float = 0f,
        channel: AudioChannel = AudioChannel.SFX
    ) {
        val v = effectiveVolume(channel, volume)
        if (v > 0.001f) backend.playSfx(assetPath, v, pitch, Mathf.clamp(pan, -1f, 1f))
    }

    fun playSynth(
        preset: SynthPreset,
        volume: Float = 0.8f,
        pitch: Float = 1f,
        pan: Float = 0f
    ) {
        val v = effectiveVolume(AudioChannel.SFX, volume)
        if (v > 0.001f) backend.playSynth(preset, v, pitch, Mathf.clamp(pan, -1f, 1f))
    }

    fun playMusic(assetPath: String, loop: Boolean = true, volume: Float = 0.7f) {
        val v = effectiveVolume(AudioChannel.MUSIC, volume)
        backend.playMusic(assetPath, loop, v)
    }

    fun stopMusic() = backend.stopMusic()
}

/**
 * Marks the GameObject (usually Main Camera or Player) as the 2D spatial ear in the Scene.
 */
class AudioListener2D : Component("AudioListener2D") {
    override val typeName: String get() = "AudioListener2D"
}

/**
 * Spatial 2D AudioSource component computing distance attenuation and stereo panning
 * relative to the active [AudioListener2D] in the Scene!
 */
class AudioSource2D(
    var clipPath: String = "",
    var synthPreset: SynthPreset = SynthPreset.COIN
) : Component("AudioSource2D") {

    override val typeName: String get() = "AudioSource2D"

    var useProceduralSynth: Boolean = true
    var volume: Float = 0.85f
    var pitch: Float = 1.0f
    var spatialBlend: Float = 1.0f
    var maxDistance: Float = 18.0f
    var playOnStart: Boolean = false

    override fun onStart() {
        if (playOnStart) play()
    }

    fun play() {
        val mixer = scene?.audioMixer ?: return
        var finalVol = volume
        var pan = 0f

        if (spatialBlend > 0f && isAttached) {
            val listener = scene?.findComponent(AudioListener2D::class)
            if (listener != null) {
                val myPos = transform.worldPosition
                val earPos = listener.transform.worldPosition
                val dist = myPos.distanceTo(earPos)
                val atten = 1f - Mathf.clamp01(dist / maxDistance.coerceAtLeast(0.5f))
                finalVol *= Mathf.lerp(1f, atten * atten, spatialBlend)
                pan = Mathf.clamp((myPos.x - earPos.x) / (maxDistance * 0.6f), -1f, 1f) * spatialBlend
            }
        }

        if (useProceduralSynth || clipPath.isBlank()) {
            mixer.playSynth(synthPreset, finalVol, pitch, pan)
        } else {
            mixer.playSfx(clipPath, finalVol, pitch, pan)
        }
    }

    override fun serialize(): JsonObject = super.serialize()
        .put("clipPath", clipPath)
        .put("synthPreset", synthPreset.name)
        .put("useProceduralSynth", useProceduralSynth)
        .put("volume", volume)
        .put("pitch", pitch)
        .put("spatialBlend", spatialBlend)
        .put("maxDistance", maxDistance)
        .put("playOnStart", playOnStart)

    override fun deserialize(data: JsonObject) {
        super.deserialize(data)
        clipPath = data.getString("clipPath", "")
        synthPreset = runCatching { SynthPreset.valueOf(data.getString("synthPreset", "COIN")) }
            .getOrDefault(SynthPreset.COIN)
        useProceduralSynth = data.getBoolean("useProceduralSynth", true)
        volume = data.getFloat("volume", 0.85f)
        pitch = data.getFloat("pitch", 1f)
        spatialBlend = data.getFloat("spatialBlend", 1f)
        maxDistance = data.getFloat("maxDistance", 18f)
        playOnStart = data.getBoolean("playOnStart", false)
    }

    override fun inspectableProperties(): List<InspectorProperty> = listOf(
        InspectorProperty.EnumProp(
            label = "Synth SFX",
            options = SynthPreset.entries.map { it.name },
            getter = { synthPreset.name },
            setter = { synthPreset = SynthPreset.valueOf(it) }
        ),
        InspectorProperty.FloatProp("Volume", 0f, 1f, 0.05f, { volume }, { volume = it }),
        InspectorProperty.FloatProp("Pitch", 0.25f, 3f, 0.05f, { pitch }, { pitch = it }),
        InspectorProperty.FloatProp("Max Distance", 1f, 100f, 1f, { maxDistance }, { maxDistance = it }),
        InspectorProperty.BoolProp("Play On Start", { playOnStart }, { playOnStart = it })
    )
}
