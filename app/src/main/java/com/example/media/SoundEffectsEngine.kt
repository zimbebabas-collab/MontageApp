package com.example.media

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.example.model.AudioPeak
import com.example.model.SfxEvent
import com.example.model.SfxPack
import com.example.model.SfxSound
import com.example.model.VideoClip
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Random
import java.util.UUID
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.tanh

/**
 * Offline procedural 44.1kHz stereo SFX synthesizer & mixer for viral Reels/Shorts moments:
 * - VINE_BOOM: deep comedic sub-bass punch drop
 * - WHOOSH: crisp dynamic transition sweep on close-up jump-cuts
 * - POP: snappy bubble-pop accent on punchlines
 * - GLITCH_DING: bright hyper-pop resonant ding on energetic highlights
 */
object SoundEffectsEngine {

    private const val SAMPLE_RATE = 44100
    private const val CHANNELS = 2

    private val cache = HashMap<SfxSound, ShortArray>()

    fun generateEventsForTimeline(
        clips: List<VideoClip>,
        peaks: List<AudioPeak>,
        sfxPack: SfxPack
    ): List<SfxEvent> {
        if (sfxPack == SfxPack.OFF || clips.isEmpty()) return emptyList()

        val events = mutableListOf<SfxEvent>()
        var editedCursor = 0L
        var lastEventMs = -1000L

        clips.forEachIndexed { idx, clip ->
            val clipStartEdited = editedCursor
            val clipEndEdited = editedCursor + clip.durationMs

            // 1. Add dynamic swoosh/accent on jump-cut close-ups
            if (idx > 0 && clip.isCloseUp && (clipStartEdited - lastEventMs) >= 1200L) {
                val sound = when (sfxPack) {
                    SfxPack.AUTO_MIX -> SfxSound.WHOOSH
                    SfxPack.VINE_BOOM -> SfxSound.VINE_BOOM
                    SfxPack.WHOOSH -> SfxSound.WHOOSH
                    SfxPack.POP -> SfxSound.POP
                    SfxPack.GLITCH_DING -> SfxSound.GLITCH_DING
                    SfxPack.OFF -> return@forEachIndexed
                }
                events.add(
                    SfxEvent(
                        id = UUID.randomUUID().toString(),
                        editedMs = clipStartEdited,
                        sound = sound,
                        labelRu = soundLabel(sound)
                    )
                )
                lastEventMs = clipStartEdited
            }

            // 2. Add punchy comedic SFX on strongest vocal peaks inside the clip
            val strongPeaks = peaks.filter { it.timestampMs in clip.startMs..clip.endMs && it.intensity >= 0.76f }
            for (peak in strongPeaks) {
                val peakEditedMs = clipStartEdited + (peak.timestampMs - clip.startMs)
                if (peakEditedMs - lastEventMs >= 1800L && peakEditedMs < clipEndEdited - 180L) {
                    val sound = when (sfxPack) {
                        SfxPack.AUTO_MIX -> when {
                            peak.intensity >= 0.88f -> SfxSound.VINE_BOOM
                            events.size % 2 == 0 -> SfxSound.POP
                            else -> SfxSound.GLITCH_DING
                        }
                        SfxPack.VINE_BOOM -> SfxSound.VINE_BOOM
                        SfxPack.WHOOSH -> SfxSound.WHOOSH
                        SfxPack.POP -> SfxSound.POP
                        SfxPack.GLITCH_DING -> SfxSound.GLITCH_DING
                        SfxPack.OFF -> break
                    }
                    events.add(
                        SfxEvent(
                            id = UUID.randomUUID().toString(),
                            editedMs = peakEditedMs,
                            sound = sound,
                            labelRu = soundLabel(sound)
                        )
                    )
                    lastEventMs = peakEditedMs
                }
            }

            editedCursor = clipEndEdited
        }
        return events
    }

    fun soundLabel(sound: SfxSound): String = when (sound) {
        SfxSound.VINE_BOOM -> "Бас-бум"
        SfxSound.WHOOSH -> "Свуш"
        SfxSound.POP -> "Поп"
        SfxSound.GLITCH_DING -> "Дзинь"
    }

    fun playPreviewSfx(sound: SfxSound, volume: Float = 0.8f) {
        val samples = getSamples(sound)
        val clampedVol = volume.coerceIn(0.05f, 1.0f)
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                val scaled = ShortArray(samples.size) { i ->
                    (samples[i] * clampedVol).toInt().coerceIn(-32767, 32767).toShort()
                }
                val byteSize = scaled.size * 2
                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(SAMPLE_RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                            .build()
                    )
                    .setBufferSizeInBytes(byteSize)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()

                track.write(scaled, 0, scaled.size)
                track.play()
                val durMs = (samples.size.toLong() / CHANNELS) * 1000L / SAMPLE_RATE
                Thread.sleep(durMs + 80L)
                track.stop()
                track.release()
            }
        }
    }

    /**
     * Mixes synthesized SFX cleanly into the 44.1kHz stereo 16-bit PCM byte buffer for MP4 export,
     * properly shifting SFX timestamps past the mid-roll ad banner pause when applicable.
     */
    fun mixSfxIntoPcm(
        pcmBytes: ByteArray,
        events: List<SfxEvent>,
        volume: Float,
        includeAd: Boolean,
        adTriggerMs: Long,
        adDurationMs: Long
    ): ByteArray {
        if (pcmBytes.isEmpty() || events.isEmpty() || volume <= 0.01f) return pcmBytes

        val totalShorts = pcmBytes.size / 2
        val mixBuf = ShortArray(totalShorts)
        ByteBuffer.wrap(pcmBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(mixBuf)

        val gain = volume.coerceIn(0f, 1f) * 0.78f

        for (event in events) {
            val outMs = if (includeAd && adTriggerMs >= 0L && event.editedMs >= adTriggerMs) {
                event.editedMs + adDurationMs
            } else {
                event.editedMs
            }
            val startFrame = ((outMs * SAMPLE_RATE) / 1000L).toInt()
            val startIdx = startFrame * CHANNELS
            if (startIdx < 0 || startIdx >= totalShorts) continue

            val sfx = getSamples(event.sound)
            val count = min(sfx.size, totalShorts - startIdx)
            for (i in 0 until count) {
                val sum = mixBuf[startIdx + i].toInt() + (sfx[i] * gain).toInt()
                mixBuf[startIdx + i] = sum.coerceIn(-32767, 32767).toShort()
            }
        }

        val outBytes = ByteArray(pcmBytes.size)
        ByteBuffer.wrap(outBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(mixBuf)
        return outBytes
    }

    private fun getSamples(sound: SfxSound): ShortArray {
        return synchronized(cache) {
            cache.getOrPut(sound) {
                when (sound) {
                    SfxSound.VINE_BOOM -> synthVineBoom()
                    SfxSound.WHOOSH -> synthWhoosh()
                    SfxSound.POP -> synthBubblePop()
                    SfxSound.GLITCH_DING -> synthGlitchDing()
                }
            }
        }
    }

    private fun synthVineBoom(): ShortArray {
        val durSec = 0.65
        val frames = (SAMPLE_RATE * durSec).toInt()
        val out = ShortArray(frames * CHANNELS)
        var phase = 0.0
        val rng = Random(42L)
        for (i in 0 until frames) {
            val t = i.toDouble() / SAMPLE_RATE
            val freq = 38.0 + 95.0 * exp(-t * 14.0)
            phase += 2.0 * PI * freq / SAMPLE_RATE
            val env = exp(-t * 4.6) * (1.0 - exp(-t * 220.0))
            val click = if (t < 0.018) (rng.nextDouble() * 2.0 - 1.0) * (1.0 - t / 0.018) * 0.45 else 0.0
            val raw = sin(phase) * 0.95 + sin(phase * 2.0) * 0.35 + click
            val saturated = tanh(raw * 1.9) * env
            val s = (saturated * 30000.0).toInt().coerceIn(-32767, 32767).toShort()
            out[i * 2] = s
            out[i * 2 + 1] = s
        }
        return out
    }

    private fun synthWhoosh(): ShortArray {
        val durSec = 0.28
        val frames = (SAMPLE_RATE * durSec).toInt()
        val out = ShortArray(frames * CHANNELS)
        val rng = Random(77L)
        var lp = 0.0
        var hpPrev = 0.0
        for (i in 0 until frames) {
            val u = i.toDouble() / frames
            // Bell-shaped air envelope
            val env = sin(PI * u) * sin(PI * u)
            val noise = rng.nextDouble() * 2.0 - 1.0
            // Sweeping low-pass cutoff
            val alpha = 0.04 + 0.34 * sin(PI * u)
            lp += alpha * (noise - lp)
            val band = lp - hpPrev * 0.72
            hpPrev = lp
            // Stereo pan sweep left -> right
            val panL = (1.0 - 0.35 * u)
            val panR = (0.65 + 0.35 * u)
            val v = tanh(band * 2.4) * env * 25000.0
            out[i * 2] = (v * panL).toInt().coerceIn(-32767, 32767).toShort()
            out[i * 2 + 1] = (v * panR).toInt().coerceIn(-32767, 32767).toShort()
        }
        return out
    }

    private fun synthBubblePop(): ShortArray {
        val durSec = 0.14
        val frames = (SAMPLE_RATE * durSec).toInt()
        val out = ShortArray(frames * CHANNELS)
        var phase = 0.0
        for (i in 0 until frames) {
            val t = i.toDouble() / SAMPLE_RATE
            val u = i.toDouble() / frames
            val freq = 260.0 + 780.0 * (u * u)
            phase += 2.0 * PI * freq / SAMPLE_RATE
            val env = sin(PI * u) * exp(-t * 14.0)
            val s = (sin(phase) * env * 28000.0).toInt().coerceIn(-32767, 32767).toShort()
            out[i * 2] = s
            out[i * 2 + 1] = s
        }
        return out
    }

    private fun synthGlitchDing(): ShortArray {
        val durSec = 0.36
        val frames = (SAMPLE_RATE * durSec).toInt()
        val out = ShortArray(frames * CHANNELS)
        for (i in 0 until frames) {
            val t = i.toDouble() / SAMPLE_RATE
            val env = exp(-t * 9.5) * (1.0 - exp(-t * 350.0))
            val f1 = sin(2.0 * PI * 1046.5 * t)
            val f2 = sin(2.0 * PI * 1567.98 * t) * 0.55
            val f3 = sin(2.0 * PI * 2093.0 * t) * 0.28
            val s = ((f1 + f2 + f3) * 0.55 * env * 26000.0).toInt().coerceIn(-32767, 32767).toShort()
            out[i * 2] = s
            out[i * 2 + 1] = s
        }
        return out
    }
}
