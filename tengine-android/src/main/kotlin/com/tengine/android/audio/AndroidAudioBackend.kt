package com.tengine.android.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import com.tengine.core.EngineLogger
import com.tengine.core.audio.AudioBackend
import com.tengine.core.audio.SynthPreset
import com.tengine.core.math.Mathf

/**
 * Low-latency Android AudioBackend combining polyphonic [SoundPool] (for both asset SFX and
 * pre-synthesized [SynthPreset] waveforms) + [MediaPlayer] for streaming background music!
 */
class AndroidAudioBackend(private val context: Context) : AudioBackend {

    private val soundPool: SoundPool = SoundPool.Builder()
        .setMaxStreams(12)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    private val loadedAssetSounds = HashMap<String, Int>()
    private val synthSoundIds = HashMap<SynthPreset, Int>()
    private val readySoundIds = HashSet<Int>()

    private var mediaPlayer: MediaPlayer? = null
    private var currentMusicPath: String? = null

    init {
        soundPool.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0) {
                synchronized(readySoundIds) {
                    readySoundIds.add(sampleId)
                }
            }
        }
        preloadSynthPresets()
    }

    private fun preloadSynthPresets() {
        try {
            for (preset in SynthPreset.entries) {
                val wavFile = ProceduralSfxGenerator.writePresetWavFile(context.cacheDir, preset)
                val id = soundPool.load(wavFile.absolutePath, 1)
                synthSoundIds[preset] = id
            }
        } catch (e: Exception) {
            EngineLogger.error("AndroidAudioBackend", "Failed preloading procedural SFX", e)
        }
    }

    override fun playSynth(preset: SynthPreset, volume: Float, pitch: Float, pan: Float) {
        val soundId = synthSoundIds[preset] ?: return
        playSoundId(soundId, volume, pitch, pan)
    }

    override fun playSfx(assetPath: String, volume: Float, pitch: Float, pan: Float) {
        val soundId = loadedAssetSounds.getOrPut(assetPath) {
            try {
                context.assets.openFd(assetPath).use { afd ->
                    soundPool.load(afd, 1)
                }
            } catch (e: Exception) {
                EngineLogger.warn("AndroidAudioBackend", "Could not load SFX asset '$assetPath'")
                0
            }
        }
        if (soundId != 0) {
            playSoundId(soundId, volume, pitch, pan)
        }
    }

    private fun playSoundId(soundId: Int, volume: Float, pitch: Float, pan: Float) {
        val clampedPan = Mathf.clamp(pan, -1f, 1f)
        val leftVol = Mathf.clamp01(volume * (if (clampedPan > 0f) 1f - clampedPan else 1f))
        val rightVol = Mathf.clamp01(volume * (if (clampedPan < 0f) 1f + clampedPan else 1f))
        val rate = Mathf.clamp(pitch, 0.5f, 2.0f)
        soundPool.play(soundId, leftVol, rightVol, 1, 0, rate)
    }

    override fun playMusic(assetPath: String, loop: Boolean, volume: Float) {
        if (currentMusicPath == assetPath && mediaPlayer?.isPlaying == true) {
            mediaPlayer?.setVolume(volume, volume)
            return
        }
        stopMusic()
        try {
            context.assets.openFd(assetPath).use { afd ->
                val mp = MediaPlayer()
                mp.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                mp.isLooping = loop
                mp.setVolume(volume, volume)
                mp.prepare()
                mp.start()
                mediaPlayer = mp
                currentMusicPath = assetPath
            }
        } catch (e: Exception) {
            EngineLogger.warn("AndroidAudioBackend", "Music asset '$assetPath' not found or failed to play")
        }
    }

    override fun stopMusic() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (_: Exception) {
        }
        mediaPlayer = null
        currentMusicPath = null
    }

    override fun pauseAll() {
        soundPool.autoPause()
        try {
            if (mediaPlayer?.isPlaying == true) mediaPlayer?.pause()
        } catch (_: Exception) {
        }
    }

    override fun resumeAll() {
        soundPool.autoResume()
        try {
            mediaPlayer?.start()
        } catch (_: Exception) {
        }
    }

    override fun release() {
        stopMusic()
        soundPool.release()
    }
}
