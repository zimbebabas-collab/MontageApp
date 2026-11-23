package com.example.media

import com.example.model.AudioPeak
import com.example.model.CaptionChunk
import com.example.model.CaptionColor
import com.example.model.EditMode
import com.example.model.EffectsConfig
import com.example.model.VideoClip
import java.util.UUID
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sin

object SubtitleEngine {

    data class SourceMapping(
        val clipIndex: Int,
        val clip: VideoClip,
        val sourceMs: Long,
        val offsetInClipMs: Long
    )

    data class FrameTransform(
        val scale: Float,
        val offsetXFraction: Float,
        val offsetYFraction: Float,
        val flashAlpha: Float,
        val chromaticShiftFraction: Float
    )

    fun generateChunksFromRussianText(
        text: String,
        totalEditedDurationMs: Long,
        editMode: EditMode
    ): List<CaptionChunk> {
        val cleanWords = text
            .replace("\n", " ")
            .trim()
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }

        if (cleanWords.isEmpty() || totalEditedDurationMs <= 0L) return emptyList()

        // Group strictly 2 to 3 words at a time for punchy Reels/TikTok kinetic pacing
        val groups = mutableListOf<String>()
        var i = 0
        while (i < cleanWords.size) {
            val remaining = cleanWords.size - i
            val take = when {
                remaining <= 3 -> remaining
                remaining == 4 -> 2
                cleanWords[i].length <= 3 && remaining >= 3 -> 3
                cleanWords[i].length >= 9 -> 2
                else -> if (groups.size % 2 == 0) 2 else 3
            }
            groups.add(cleanWords.subList(i, i + take).joinToString(" "))
            i += take
        }

        val palette = when (editMode) {
            EditMode.LITVIN -> listOf(
                CaptionColor.WHITE,
                CaptionColor.YELLOW,
                CaptionColor.NEON_GREEN,
                CaptionColor.CYAN
            )
            EditMode.MEME -> listOf(
                CaptionColor.YELLOW,
                CaptionColor.NEON_GREEN,
                CaptionColor.CYAN,
                CaptionColor.WHITE
            )
            EditMode.LIGHT -> listOf(
                CaptionColor.WHITE,
                CaptionColor.CYAN,
                CaptionColor.WHITE,
                CaptionColor.YELLOW
            )
        }

        val weights = groups.map { max(4, it.length).toDouble() }
        val totalWeight = weights.sum().coerceAtLeast(1.0)
        val usableDuration = max(600L, totalEditedDurationMs)

        var cursorMs = 0L
        return groups.mapIndexed { idx, chunkText ->
            val chunkDur = if (idx == groups.lastIndex) {
                (usableDuration - cursorMs).coerceAtLeast(250L)
            } else {
                ((weights[idx] / totalWeight) * usableDuration).toLong().coerceAtLeast(280L)
            }
            val start = cursorMs
            val end = (cursorMs + chunkDur).coerceAtMost(usableDuration)
            cursorMs = end
            CaptionChunk(
                id = UUID.randomUUID().toString(),
                text = chunkText.uppercase(),
                startMs = start,
                endMs = max(start + 150L, end),
                color = palette[idx % palette.size]
            )
        }
    }

    fun mapEditedMsToSource(editedMs: Long, clips: List<VideoClip>): SourceMapping? {
        if (clips.isEmpty()) return null
        var acc = 0L
        for (i in clips.indices) {
            val clip = clips[i]
            val clipEnd = acc + clip.durationMs
            if (editedMs < clipEnd || i == clips.lastIndex) {
                val offset = (editedMs - acc).coerceIn(0L, clip.durationMs)
                return SourceMapping(
                    clipIndex = i,
                    clip = clip,
                    sourceMs = (clip.startMs + offset).coerceIn(clip.startMs, clip.endMs),
                    offsetInClipMs = offset
                )
            }
            acc = clipEnd
        }
        return null
    }

    fun activeCaptionAt(editedMs: Long, captions: List<CaptionChunk>): CaptionChunk? {
        return captions.firstOrNull { editedMs >= it.startMs && editedMs < it.endMs }
    }

    fun computePopScale(editedMs: Long, caption: CaptionChunk): Float {
        val elapsed = (editedMs - caption.startMs).coerceAtLeast(0L)
        val popDurationMs = 150f
        if (elapsed >= popDurationMs) return 1.0f
        val t = (elapsed / popDurationMs).coerceIn(0f, 1f)
        // Overshoot spring feel: 0.72 -> 1.14 -> 1.0
        return if (t < 0.65f) {
            val p = t / 0.65f
            0.72f + (1.14f - 0.72f) * p
        } else {
            val p = (t - 0.65f) / 0.35f
            1.14f - (1.14f - 1.0f) * p
        }
    }

    fun computePeakImpulse(sourceMs: Long, peaks: List<AudioPeak>): Float {
        if (peaks.isEmpty()) return 0f
        var strongest = 0f
        val decayWindowMs = 340f
        for (peak in peaks) {
            val delta = sourceMs - peak.timestampMs
            if (delta in -40L..340L) {
                val dist = abs(delta).toFloat()
                val impulse = (1f - dist / decayWindowMs).coerceIn(0f, 1f) * peak.intensity
                if (impulse > strongest) strongest = impulse
            }
        }
        return strongest
    }

    fun computeFrameTransform(
        sourceMs: Long,
        editedMs: Long,
        totalEditedMs: Long,
        peaks: List<AudioPeak>,
        effects: EffectsConfig
    ): FrameTransform {
        val impulse = computePeakImpulse(sourceMs, peaks)
        var scale = 1f

        if (effects.slowZoom && totalEditedMs > 0L) {
            val progress = (editedMs.toFloat() / totalEditedMs.toFloat()).coerceIn(0f, 1f)
            scale += progress * effects.zoomStrength
        }

        if (effects.microZoomOnPeak && impulse > 0.05f) {
            scale += impulse * effects.zoomStrength
        }

        var offsetX = 0f
        var offsetY = 0f
        if (effects.cameraShakeOnPeak && impulse > 0.18f) {
            val amp = impulse * effects.shakeStrength * 0.032f
            val phase = sourceMs * 0.095
            offsetX = (sin(phase * 1.7) * amp).toFloat()
            offsetY = (sin(phase * 2.3 + 1.1) * amp).toFloat()
            // Slight zoom safety margin so shaking edges never reveal black borders
            scale = max(scale, 1.07f)
        }

        val flashAlpha = if (effects.chromaticFlash && impulse > 0.35f) {
            ((impulse - 0.35f) / 0.65f * 0.28f).coerceIn(0f, 0.3f)
        } else {
            0f
        }

        val chromaticShift = if (effects.chromaticFlash && impulse > 0.3f) {
            impulse * 0.012f
        } else {
            0f
        }

        return FrameTransform(
            scale = scale,
            offsetXFraction = offsetX,
            offsetYFraction = offsetY,
            flashAlpha = flashAlpha,
            chromaticShiftFraction = chromaticShift
        )
    }

    fun formatTimeMs(ms: Long): String {
        val safe = ms.coerceAtLeast(0L)
        val totalSec = safe / 1000L
        val mins = totalSec / 60L
        val secs = totalSec % 60L
        val tenths = (safe % 1000L) / 100L
        return String.format("%02d:%02d.%d", mins, secs, tenths)
    }
}
