package com.example.media

import com.example.model.AudioPeak
import com.example.model.CaptionChunk
import com.example.model.CaptionColor
import com.example.model.EditMode
import com.example.model.FramingConfig
import com.example.model.VideoClip
import java.util.UUID
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin

object SubtitleEngine {

    sealed interface TimelinePosition {
        data class MainClip(
            val clipIndex: Int,
            val clip: VideoClip,
            val sourceMs: Long,
            val offsetInClipMs: Long
        ) : TimelinePosition

        data class ReactionClip(
            val reactionMs: Long
        ) : TimelinePosition
    }

    data class CameraFrameState(
        val scale: Float,
        val focalX: Float,
        val focalY: Float,
        val isCloseUp: Boolean,
        val offsetXFraction: Float,
        val offsetYFraction: Float,
        val peakFlashAlpha: Float,
        val saturationBoost: Float,
        val peakPulse: Float
    )

    /**
     * Generates vivid, saturated multi-colored captions (1–2 punchy words per beat)
     * with automatic *action* white notes and alternating vibrant neon colors.
     */
    fun generateVibrantCaptions(
        text: String,
        totalMainDurationMs: Long,
        editMode: EditMode
    ): List<CaptionChunk> {
        if (text.isBlank() || totalMainDurationMs <= 0L) return emptyList()

        data class RawToken(
            val word: String,
            val color: CaptionColor,
            val isAction: Boolean
        )

        val vibrantCycle = when (editMode) {
            EditMode.CLEAN_REFERENCE -> listOf(
                CaptionColor.VIVID_YELLOW,
                CaptionColor.ELECTRIC_CYAN,
                CaptionColor.HOT_PINK,
                CaptionColor.NEON_LIME,
                CaptionColor.ACTION_WHITE
            )
            EditMode.LITVIN_DYNAMIC -> listOf(
                CaptionColor.VIVID_YELLOW,
                CaptionColor.NEON_LIME,
                CaptionColor.VIVID_ORANGE,
                CaptionColor.ELECTRIC_CYAN,
                CaptionColor.HOT_PINK
            )
            EditMode.MEME_INSERT -> listOf(
                CaptionColor.HOT_PINK,
                CaptionColor.VIVID_YELLOW,
                CaptionColor.ELECTRIC_CYAN,
                CaptionColor.NEON_LIME,
                CaptionColor.VIVID_ORANGE
            )
        }

        val tokens = mutableListOf<RawToken>()
        val lines = text.trim().split(Regex("\\r?\\n")).filter { it.isNotBlank() }
        var inAsteriskAction = false
        var colorIdx = 0

        lines.forEach { rawLine ->
            val cleanLine = rawLine.trim().removePrefix("—").removePrefix("-").trim()
            val rawWords = cleanLine.split(Regex("\\s+")).filter { it.isNotBlank() }
            for (word in rawWords) {
                val startsAction = word.startsWith("*")
                val endsAction = word.endsWith("*") && word.length > 1
                if (startsAction) inAsteriskAction = true

                val isActionToken = inAsteriskAction || (startsAction && endsAction)
                val displayWord = if (isActionToken) {
                    val stripped = word.trim('*')
                    if (stripped.isBlank()) continue
                    "*${stripped.uppercase()}*"
                } else {
                    word.uppercase()
                }

                val color = if (isActionToken) {
                    CaptionColor.ACTION_WHITE
                } else {
                    val c = vibrantCycle[colorIdx % vibrantCycle.size]
                    colorIdx++
                    c
                }

                tokens.add(RawToken(displayWord, color, isActionToken))
                if (endsAction) inAsteriskAction = false
            }
        }

        if (tokens.isEmpty()) return emptyList()

        val weights = tokens.map { t ->
            val lenWeight = max(3, t.word.length).toDouble()
            val pauseBonus = if (t.word.endsWith(".") || t.word.endsWith("!") || t.word.endsWith("?")) 2.0 else 0.0
            lenWeight + pauseBonus
        }
        val totalWeight = weights.sum().coerceAtLeast(1.0)
        val usableMs = max(500L, totalMainDurationMs)

        var cursorMs = 0L
        return tokens.mapIndexed { idx, token ->
            val durMs = if (idx == tokens.lastIndex) {
                (usableMs - cursorMs).coerceAtLeast(140L)
            } else {
                ((weights[idx] / totalWeight) * usableMs).toLong().coerceAtLeast(140L)
            }
            val start = cursorMs
            val end = (cursorMs + durMs).coerceAtMost(usableMs)
            cursorMs = end
            CaptionChunk(
                id = UUID.randomUUID().toString(),
                text = token.word,
                startMs = start,
                endMs = max(start + 120L, end),
                color = token.color,
                isActionNote = token.isAction
            )
        }
    }

    fun nextColor(current: CaptionColor): CaptionColor {
        val all = CaptionColor.entries
        return all[(all.indexOf(current) + 1) % all.size]
    }

    fun mapEditedMsToTimeline(
        editedMs: Long,
        clips: List<VideoClip>,
        reactionDurationMs: Long
    ): TimelinePosition? {
        if (clips.isEmpty()) return null
        var acc = 0L
        for (i in clips.indices) {
            val clip = clips[i]
            val clipEnd = acc + clip.durationMs
            if (editedMs < clipEnd) {
                val offset = (editedMs - acc).coerceIn(0L, clip.durationMs)
                return TimelinePosition.MainClip(
                    clipIndex = i,
                    clip = clip,
                    sourceMs = (clip.startMs + offset).coerceIn(clip.startMs, clip.endMs),
                    offsetInClipMs = offset
                )
            }
            acc = clipEnd
        }

        if (reactionDurationMs > 0L) {
            val reactionOffset = (editedMs - acc).coerceIn(0L, reactionDurationMs)
            return TimelinePosition.ReactionClip(reactionMs = reactionOffset)
        }

        val last = clips.last()
        return TimelinePosition.MainClip(
            clipIndex = clips.lastIndex,
            clip = last,
            sourceMs = last.endMs,
            offsetInClipMs = last.durationMs
        )
    }

    fun activeCaptionAt(editedMs: Long, captions: List<CaptionChunk>): CaptionChunk? {
        return captions.firstOrNull { editedMs >= it.startMs && editedMs < it.endMs }
    }

    /**
     * Snappy 120ms pop-in animation (0.82 -> 1.06 -> 1.0) for punchy vibrant words.
     */
    fun computePopScale(editedMs: Long, caption: CaptionChunk): Float {
        val elapsed = (editedMs - caption.startMs).coerceAtLeast(0L)
        val popDurationMs = 120f
        if (elapsed >= popDurationMs) return 1.0f
        val t = (elapsed / popDurationMs).coerceIn(0f, 1f)
        val eased = 1f - (1f - t).pow(3)
        return 0.82f + 0.18f * eased
    }

    fun computePeakImpulse(sourceMs: Long, peaks: List<AudioPeak>): Float {
        if (peaks.isEmpty()) return 0f
        var strongest = 0f
        val decayWindowMs = 340f
        for (peak in peaks) {
            val delta = sourceMs - peak.timestampMs
            if (delta in -35L..340L) {
                val dist = abs(delta).toFloat()
                val impulse = (1f - dist / decayWindowMs).coerceIn(0f, 1f) * peak.intensity
                if (impulse > strongest) strongest = impulse
            }
        }
        return strongest
    }

    /**
     * Computes face-locked camera zoom & saturated key-moment visual effects.
     * When autoFaceTracking is enabled, locks onto clip.focusX / clip.focusY (detected via ML Kit);
     * otherwise uses the manual focalX / focalY coordinates.
     */
    fun computeCameraFrame(
        clip: VideoClip?,
        sourceMs: Long,
        peaks: List<AudioPeak>,
        framing: FramingConfig
    ): CameraFrameState {
        if (clip == null) {
            return CameraFrameState(1f, framing.focalX, framing.focalY, false, 0f, 0f, 0f, 1f, 0f)
        }

        val fx = if (framing.autoFaceTracking) clip.focusX else framing.focalX
        val fy = if (framing.autoFaceTracking) clip.focusY else framing.focalY

        val impulse = if (framing.peakPunchEnabled || framing.saturatedPeakEffects) {
            computePeakImpulse(sourceMs, peaks)
        } else {
            0f
        }

        val closeUpActive = framing.autoCloseUpCuts && (clip.isCloseUp || impulse >= 0.72f)
        var scale = if (closeUpActive) framing.closeUpZoom else 1.0f
        if (framing.peakPunchEnabled && impulse > 0.20f) {
            scale += impulse * framing.peakPunchBonus
        }

        var offsetX = 0f
        var offsetY = 0f
        if (framing.microShakeOnPeak && impulse > 0.28f) {
            val amp = impulse * 0.022f
            val phase = sourceMs * 0.09
            offsetX = (sin(phase * 1.8) * amp).toFloat()
            offsetY = (sin(phase * 2.5 + 0.9) * amp).toFloat()
            scale = max(scale, 1.08f)
        }

        val flashAlpha = if (framing.saturatedPeakEffects && framing.neonFlashOnPeak && impulse > 0.30f) {
            ((impulse - 0.30f) / 0.70f * 0.34f).coerceIn(0f, 0.34f)
        } else {
            0f
        }

        val saturationBoost = if (framing.saturatedPeakEffects && impulse > 0.25f) {
            1f + impulse * 0.45f
        } else {
            1f
        }

        return CameraFrameState(
            scale = scale.coerceIn(1.0f, 1.85f),
            focalX = fx.coerceIn(0.15f, 0.85f),
            focalY = fy.coerceIn(0.15f, 0.85f),
            isCloseUp = closeUpActive,
            offsetXFraction = offsetX,
            offsetYFraction = offsetY,
            peakFlashAlpha = flashAlpha,
            saturationBoost = saturationBoost,
            peakPulse = impulse
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
