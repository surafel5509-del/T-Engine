package com.tengine.android.audio

import com.tengine.core.audio.SynthPreset
import com.tengine.core.math.Mathf
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/**
 * Generates crisp 16-bit 22,050 Hz PCM RIFF/WAV sound effects from scratch in Kotlin
 * so games and T-Engine Studio have responsive polyphonic SoundPool SFX out of the box!
 */
object ProceduralSfxGenerator {

    private const val SAMPLE_RATE = 22050

    fun writePresetWavFile(cacheDir: File, preset: SynthPreset): File {
        val outFile = File(cacheDir, "tengine_sfx_${preset.name.lowercase()}.wav")
        if (outFile.exists() && outFile.length() > 44L) {
            return outFile
        }
        val samples = synthesizeSamples(preset)
        val wavBytes = encodePcm16MonoWav(samples, SAMPLE_RATE)
        FileOutputStream(outFile).use { it.write(wavBytes) }
        return outFile
    }

    private fun synthesizeSamples(preset: SynthPreset): ShortArray {
        return when (preset) {
            SynthPreset.JUMP -> sweepTone(durationSec = 0.16f, startHz = 220f, endHz = 620f, squareMix = 0.35f)
            SynthPreset.LASER -> sweepTone(durationSec = 0.14f, startHz = 960f, endHz = 180f, squareMix = 0.55f)
            SynthPreset.COIN -> dualArpeggio(0.22f, firstHz = 987.77f, secondHz = 1318.5f)
            SynthPreset.POWER_UP -> sweepTone(durationSec = 0.30f, startHz = 330f, endHz = 1046.5f, squareMix = 0.25f)
            SynthPreset.DASH -> noiseBurst(durationSec = 0.12f, toneHz = 320f, noiseMix = 0.7f)
            SynthPreset.HIT -> noiseBurst(durationSec = 0.11f, toneHz = 140f, noiseMix = 0.65f)
            SynthPreset.EXPLOSION -> noiseBurst(durationSec = 0.38f, toneHz = 75f, noiseMix = 0.85f)
        }
    }

    private fun sweepTone(durationSec: Float, startHz: Float, endHz: Float, squareMix: Float): ShortArray {
        val count = (SAMPLE_RATE * durationSec).toInt().coerceAtLeast(1)
        val out = ShortArray(count)
        var phase = 0.0
        for (i in 0 until count) {
            val t = i.toFloat() / count
            val freq = Mathf.lerp(startHz, endHz, t.pow(0.75f))
            phase += 2.0 * PI * freq / SAMPLE_RATE
            val sine = sin(phase).toFloat()
            val sq = if (sine >= 0f) 0.8f else -0.8f
            val wave = Mathf.lerp(sine, sq, squareMix)
            val env = (1f - t).pow(1.2f)
            out[i] = (wave * env * 24000f).toInt().coerceIn(-32767, 32767).toShort()
        }
        return out
    }

    private fun dualArpeggio(durationSec: Float, firstHz: Float, secondHz: Float): ShortArray {
        val count = (SAMPLE_RATE * durationSec).toInt().coerceAtLeast(1)
        val out = ShortArray(count)
        var phase = 0.0
        for (i in 0 until count) {
            val t = i.toFloat() / count
            val freq = if (t < 0.35f) firstHz else secondHz
            phase += 2.0 * PI * freq / SAMPLE_RATE
            val wave = sin(phase).toFloat() + 0.25f * sin(phase * 2.0).toFloat()
            val env = (1f - t).pow(1.4f)
            out[i] = (wave * env * 19000f).toInt().coerceIn(-32767, 32767).toShort()
        }
        return out
    }

    private fun noiseBurst(durationSec: Float, toneHz: Float, noiseMix: Float): ShortArray {
        val count = (SAMPLE_RATE * durationSec).toInt().coerceAtLeast(1)
        val out = ShortArray(count)
        val rng = Random(42)
        var phase = 0.0
        var filteredNoise = 0f
        for (i in 0 until count) {
            val t = i.toFloat() / count
            val freq = toneHz * (1f - 0.5f * t)
            phase += 2.0 * PI * freq / SAMPLE_RATE
            val rawNoise = rng.nextFloat() * 2f - 1f
            filteredNoise = filteredNoise * 0.68f + rawNoise * 0.32f
            val wave = Mathf.lerp(sin(phase).toFloat(), filteredNoise, noiseMix)
            val env = (1f - t).pow(1.8f)
            out[i] = (wave * env * 26000f).toInt().coerceIn(-32767, 32767).toShort()
        }
        return out
    }

    private fun encodePcm16MonoWav(samples: ShortArray, sampleRate: Int): ByteArray {
        val dataSize = samples.size * 2
        val buffer = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put("RIFF".toByteArray(Charsets.US_ASCII))
        buffer.putInt(36 + dataSize)
        buffer.put("WAVE".toByteArray(Charsets.US_ASCII))
        buffer.put("fmt ".toByteArray(Charsets.US_ASCII))
        buffer.putInt(16) // PCM chunk size
        buffer.putShort(1) // AudioFormat = 1 (PCM)
        buffer.putShort(1) // NumChannels = 1 (Mono)
        buffer.putInt(sampleRate)
        buffer.putInt(sampleRate * 2) // ByteRate
        buffer.putShort(2) // BlockAlign
        buffer.putShort(16) // BitsPerSample
        buffer.put("data".toByteArray(Charsets.US_ASCII))
        buffer.putInt(dataSize)
        for (s in samples) {
            buffer.putShort(s)
        }
        return buffer.array()
    }
}
