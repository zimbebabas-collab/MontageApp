package com.example.model

import android.net.Uri

enum class EditMode(
    val titleRu: String,
    val subtitleRu: String,
    val badgeRu: String,
    val defaultSilenceThreshold: Float,
    val defaultMinSilenceMs: Long,
    val defaultEffects: EffectsConfig
) {
    LITVIN(
        titleRu = "Литвин / Динамика",
        subtitleRu = "Быстрые склейки без пауз, зум на пиках голоса, яркие цветные субтитры",
        badgeRu = "ХИТ",
        defaultSilenceThreshold = 0.14f,
        defaultMinSilenceMs = 280L,
        defaultEffects = EffectsConfig(
            microZoomOnPeak = true,
            cameraShakeOnPeak = false,
            vignette = true,
            chromaticFlash = false,
            slowZoom = false,
            zoomStrength = 0.13f,
            shakeStrength = 0.35f
        )
    ),
    MEME(
        titleRu = "Мем / Прикол",
        subtitleRu = "Тряска камеры на криках, хроматическая вспышка и контрастные субтитры",
        badgeRu = "ВИРАЛ",
        defaultSilenceThreshold = 0.13f,
        defaultMinSilenceMs = 300L,
        defaultEffects = EffectsConfig(
            microZoomOnPeak = true,
            cameraShakeOnPeak = true,
            vignette = true,
            chromaticFlash = true,
            slowZoom = false,
            zoomStrength = 0.15f,
            shakeStrength = 0.70f
        )
    ),
    LIGHT(
        titleRu = "Лайт / Разговорный",
        subtitleRu = "Мягкая нарезка пауз, плавный кино-зум и чистые выделенные субтитры",
        badgeRu = "КЛИН",
        defaultSilenceThreshold = 0.08f,
        defaultMinSilenceMs = 460L,
        defaultEffects = EffectsConfig(
            microZoomOnPeak = false,
            cameraShakeOnPeak = false,
            vignette = true,
            chromaticFlash = false,
            slowZoom = true,
            zoomStrength = 0.07f,
            shakeStrength = 0.20f
        )
    )
}

enum class EditorTab(val titleRu: String) {
    MODE("Режим"),
    SLICES("Нарезка"),
    CAPTIONS("Субтитры"),
    EFFECTS("Эффекты"),
    AD("Реклама")
}

enum class CaptionColor(val argb: Int, val labelRu: String) {
    WHITE(0xFFFFFFFF.toInt(), "Белый"),
    YELLOW(0xFFFFE600.toInt(), "Жёлтый"),
    NEON_GREEN(0xFF39FF14.toInt(), "Неон"),
    CYAN(0xFF00F0FF.toInt(), "Циан")
}

data class VideoClip(
    val id: String,
    val startMs: Long,
    val endMs: Long,
    val peakIntensity: Float = 0.5f,
    val label: String = ""
) {
    val durationMs: Long get() = (endMs - startMs).coerceAtLeast(100L)
}

data class AudioPeak(
    val timestampMs: Long,
    val intensity: Float
)

data class AudioAnalysisResult(
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val rotation: Int,
    val rmsEnvelope: FloatArray,
    val windowMs: Long,
    val peaks: List<AudioPeak>,
    val clips: List<VideoClip>
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AudioAnalysisResult) return false
        return durationMs == other.durationMs &&
            width == other.width &&
            height == other.height &&
            rotation == other.rotation &&
            windowMs == other.windowMs &&
            rmsEnvelope.contentEquals(other.rmsEnvelope) &&
            peaks == other.peaks &&
            clips == other.clips
    }

    override fun hashCode(): Int {
        var result = durationMs.hashCode()
        result = 31 * result + width
        result = 31 * result + height
        result = 31 * result + rotation
        result = 31 * result + rmsEnvelope.contentHashCode()
        result = 31 * result + windowMs.hashCode()
        result = 31 * result + peaks.hashCode()
        result = 31 * result + clips.hashCode()
        return result
    }
}

data class CaptionChunk(
    val id: String,
    val text: String,
    val startMs: Long,
    val endMs: Long,
    val color: CaptionColor
) {
    val durationMs: Long get() = (endMs - startMs).coerceAtLeast(200L)
}

data class EffectsConfig(
    val microZoomOnPeak: Boolean = true,
    val cameraShakeOnPeak: Boolean = false,
    val vignette: Boolean = true,
    val chromaticFlash: Boolean = false,
    val slowZoom: Boolean = false,
    val zoomStrength: Float = 0.13f,
    val shakeStrength: Float = 0.45f
)

data class AdBannerConfig(
    val enabled: Boolean = false,
    val imageUri: Uri? = null,
    val triggerAtMs: Long = 3000L,
    val durationSec: Int = 5,
    val areaPercent: Int = 30, // 25%..35% of 9:16 screen area
    val audioUri: Uri? = null,
    val audioDisplayName: String = "",
    val ttsText: String = "",
    val useTts: Boolean = false,
    val synthesizedTtsPath: String? = null
)

data class ExportedVideo(
    val displayName: String,
    val contentUri: Uri?,
    val filePath: String?,
    val durationMs: Long
)

sealed interface ExportState {
    data object Idle : ExportState
    data class Exporting(
        val progress: Float,
        val statusText: String,
        val currentPart: Int = 1,
        val totalParts: Int = 1
    ) : ExportState

    data class Completed(
        val videos: List<ExportedVideo>,
        val isBatch: Boolean
    ) : ExportState

    data class Error(val message: String) : ExportState
}

data class EditorUiState(
    val videoUri: Uri? = null,
    val videoFileName: String = "",
    val isAnalyzing: Boolean = false,
    val analysisError: String? = null,
    val sourceDurationMs: Long = 0L,
    val videoWidth: Int = 1080,
    val videoHeight: Int = 1920,
    val videoRotation: Int = 0,
    val rmsEnvelope: List<Float> = emptyList(),
    val rmsWindowMs: Long = 50L,
    val audioPeaks: List<AudioPeak> = emptyList(),
    val clips: List<VideoClip> = emptyList(),
    val selectedClipId: String? = null,
    val silenceThreshold: Float = EditMode.LITVIN.defaultSilenceThreshold,
    val editMode: EditMode = EditMode.LITVIN,
    val effects: EffectsConfig = EditMode.LITVIN.defaultEffects,
    val captionsEnabled: Boolean = true,
    val rawTranscriptText: String = "",
    val captions: List<CaptionChunk> = emptyList(),
    val isListeningSpeech: Boolean = false,
    val speechPartialText: String = "",
    val speechStatusMessage: String? = null,
    val adBanner: AdBannerConfig = AdBannerConfig(),
    val isTtsSynthesizing: Boolean = false,
    val activeTab: EditorTab = EditorTab.MODE,
    val isPlaying: Boolean = false,
    val currentEditedMs: Long = 0L,
    val isAdPauseActive: Boolean = false,
    val adRemainingMs: Long = 0L,
    val adElapsedMs: Long = 0L,
    val exportState: ExportState = ExportState.Idle
) {
    val totalEditedContentMs: Long
        get() = clips.sumOf { it.durationMs }.coerceAtLeast(0L)

    val totalWithAdMs: Long
        get() = totalEditedContentMs + if (adBanner.enabled && adBanner.imageUri != null && clips.isNotEmpty()) {
            adBanner.durationSec * 1000L
        } else {
            0L
        }
}
