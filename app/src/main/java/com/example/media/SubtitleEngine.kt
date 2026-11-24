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
        val isCloseUp: Boolean
    )

    /**
     * Splits Russian transcript into strictly ONE word per subtitle chunk.
     * - Words wrapped in *...* (e.g. *СМЕЁТСЯ*) are automatically colored Crisp White (#FFFFFF).
     * - Speakers alternate cleanly between Soft Neon Pink (#F472B6) and Bright Sky Blue (#38BDF8)
     *   on line breaks, dialogue dashes, or punctuation turns.
     * - In LITVIN_DYNAMIC mode, emphatic words also receive bright Yellow / Green accents.
     */
    fun generateSingleWordCaptions(
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

        val tokens = mutableListOf<RawToken>()
        val lines = text.trim().split(Regex("\\r?\\n")).filter { it.isNotBlank() }

        var currentSpeaker = 0 // 0 = Pink, 1 = Blue
        var inAsteriskAction = false
        var wordsSinceSwitch = 0

        lines.forEachIndexed { lineIdx, rawLine ->
            val trimmed = rawLine.trim()
            if (lineIdx > 0 && !inAsteriskAction) {
                // New line signals a speaker turn in dialogue scripts
                currentSpeaker = 1 - currentSpeaker
                wordsSinceSwitch = 0
            }

            // Handle explicit speaker prefix tags like "Ж:", "М:", "1:", "2:", "—"
            val cleanLine = when {
                trimmed.startsWith("—") || trimmed.startsWith("-") -> {
                    if (lineIdx == 0 && tokens.isNotEmpty()) {
                        currentSpeaker = 1 - currentSpeaker
                        wordsSinceSwitch = 0
                    }
                    trimmed.drop(1).trim()
                }
                else -> trimmed
            }

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

                val color = when {
                    isActionToken -> CaptionColor.ACTION_WHITE
                    editMode == EditMode.LITVIN_DYNAMIC && (
                        word.endsWith("!") ||
                            (tokens.size % 5 == 2)
                        ) -> if (tokens.size % 2 == 0) CaptionColor.ACCENT_YELLOW else CaptionColor.ACCENT_GREEN
                    currentSpeaker == 0 -> CaptionColor.SPEAKER_PINK
                    else -> CaptionColor.SPEAKER_BLUE
                }

                tokens.add(RawToken(displayWord, color, isActionToken))
                wordsSinceSwitch++

                if (endsAction) inAsteriskAction = false

                // If single-paragraph input without newlines, smoothly alternate speakers on '?' / '!' or every ~6 words at sentence end
                if (lines.size == 1 && !inAsteriskAction) {
                    val endsSentence = word.endsWith("?") || word.endsWith("!") || word.endsWith(".")
                    if (endsSentence && wordsSinceSwitch >= 2) {
                        currentSpeaker = 1 - currentSpeaker
                        wordsSinceSwitch = 0
                    }
                }
            }
        }

        if (tokens.isEmpty()) return emptyList()

        // Weight duration by character count so each word reads naturally
        val weights = tokens.map { t ->
            val lenWeight = max(3, t.word.length).toDouble()
            val pauseBonus = if (t.word.endsWith(".") || t.word.endsWith("!") || t.word.endsWith("?")) 2.2 else 0.0
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

    fun nextColorForMode(current: CaptionColor, mode: EditMode): CaptionColor {
        val cycle = if (mode == EditMode.LITVIN_DYNAMIC) {
            listOf(
                CaptionColor.SPEAKER_PINK,
                CaptionColor.SPEAKER_BLUE,
                CaptionColor.ACTION_WHITE,
                CaptionColor.ACCENT_YELLOW,
                CaptionColor.ACCENT_GREEN
            )
        } else {
            listOf(
                CaptionColor.SPEAKER_PINK,
                CaptionColor.SPEAKER_BLUE,
                CaptionColor.ACTION_WHITE
            )
        }
        val idx = cycle.indexOf(current)
        return cycle[(idx + 1) % cycle.size]
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
     * Snappy 120ms scale pop-in animation (0.85 -> 1.0) with cubic ease-out.
     */
    fun computePopScale(editedMs: Long, caption: CaptionChunk): Float {
        val elapsed = (editedMs - caption.startMs).coerceAtLeast(0L)
        val popDurationMs = 120f
        if (elapsed >= popDurationMs) return 1.0f
        val t = (elapsed / popDurationMs).coerceIn(0f, 1f)
        val eased = 1f - (1f - t).pow(3)
        return 0.85f + 0.15f * eased
    }

    fun computePeakImpulse(sourceMs: Long, peaks: List<AudioPeak>): Float {
        if (peaks.isEmpty()) return 0f
        var strongest = 0f
        val decayWindowMs = 280f
        for (peak in peaks) {
            val delta = sourceMs - peak.timestampMs
            if (delta in -30L..280L) {
                val dist = abs(delta).toFloat()
                val impulse = (1f - dist / decayWindowMs).coerceIn(0f, 1f) * peak.intensity
                if (impulse > strongest) strongest = impulse
            }
        }
        return strongest
    }

    /**
     * Clean multi-camera Shorts framing:
     * - Toggles cleanly between normal framing (1.0x) and close-up (1.35x) with adjustable focal X/Y.
     * - Optionally punches slightly tighter on vocal peaks.
     */
    fun computeCameraFrame(
        clip: VideoClip?,
        sourceMs: Long,
        peaks: List<AudioPeak>,
        framing: FramingConfig
    ): CameraFrameState {
        if (clip == null) {
            return CameraFrameState(1f, framing.focalX, framing.focalY, false)
        }
        val impulse = if (framing.peakPunchEnabled) computePeakImpulse(sourceMs, peaks) else 0f
        val closeUpActive = framing.autoCloseUpCuts && (clip.isCloseUp || impulse >= 0.72f)

        var scale = if (closeUpActive) framing.closeUpZoom else 1.0f
        if (framing.peakPunchEnabled && impulse > 0.25f) {
            scale += impulse * framing.peakPunchBonus
        }

        return CameraFrameState(
            scale = scale.coerceIn(1.0f, 1.85f),
            focalX = framing.focalX.coerceIn(0.15f, 0.85f),
            focalY = framing.focalY.coerceIn(0.15f, 0.85f),
            isCloseUp = closeUpActive
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
