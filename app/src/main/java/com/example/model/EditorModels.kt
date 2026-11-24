package com.example.model

import android.net.Uri

enum class EditMode(
    val titleRu: String,
    val shortTitleRu: String,
    val subtitleRu: String,
    val badgeRu: String,
    val defaultSilenceThreshold: Float,
    val defaultMinSilenceMs: Long,
    val defaultFraming: FramingConfig,
    val enablesMemeBar: Boolean
) {
    CLEAN_REFERENCE(
        titleRu = "Чистый стиль (Как в референсе)",
        shortTitleRu = "Чистый стиль",
        subtitleRu = "Вырезка пауз, 1 слово в центре (Розовый / Голубой / Белый), чистые наезды 1.35x без лишнего шума",
        badgeRu = "РЕФЕРЕНС",
        defaultSilenceThreshold = 0.13f,
        defaultMinSilenceMs = 280L,
        defaultFraming = FramingConfig(
            autoCloseUpCuts = true,
            closeUpZoom = 1.35f,
            focalX = 0.50f,
            focalY = 0.36f,
            peakPunchEnabled = true,
            peakPunchBonus = 0.05f,
            vignette = false
        ),
        enablesMemeBar = false
    ),
    LITVIN_DYNAMIC(
        titleRu = "Динамика / Литвин",
        shortTitleRu = "Динамика",
        subtitleRu = "Ещё быстрее склейки, усиленные зум-удары на пиках голоса, жёлтые и зелёные акценты",
        badgeRu = "ДИНАМИКА",
        defaultSilenceThreshold = 0.16f,
        defaultMinSilenceMs = 220L,
        defaultFraming = FramingConfig(
            autoCloseUpCuts = true,
            closeUpZoom = 1.42f,
            focalX = 0.50f,
            focalY = 0.36f,
            peakPunchEnabled = true,
            peakPunchBonus = 0.10f,
            vignette = true
        ),
        enablesMemeBar = false
    ),
    MEME_INSERT(
        titleRu = "Мем-вклейка",
        shortTitleRu = "Мем-вклейка",
        subtitleRu = "Чёрная верхняя плашка с заголовком + вклейка мемного клипа или реакции в конец ролика",
        badgeRu = "МЕМ",
        defaultSilenceThreshold = 0.13f,
        defaultMinSilenceMs = 280L,
        defaultFraming = FramingConfig(
            autoCloseUpCuts = true,
            closeUpZoom = 1.35f,
            focalX = 0.50f,
            focalY = 0.38f,
            peakPunchEnabled = true,
            peakPunchBonus = 0.05f,
            vignette = false
        ),
        enablesMemeBar = true
    )
}

enum class EditorTab(val titleRu: String) {
    MODE("Режим"),
    SLICES("Нарезка"),
    CAPTIONS("Слова"),
    FRAMING("Камера"),
    AD("Реклама")
}

enum class CaptionColor(val argb: Int, val labelRu: String) {
    SPEAKER_PINK(0xFFF472B6.toInt(), "Спикер 1"),
    SPEAKER_BLUE(0xFF38BDF8.toInt(), "Спикер 2"),
    ACTION_WHITE(0xFFFFFFFF.toInt(), "Действие"),
    ACCENT_YELLOW(0xFFFACC15.toInt(), "Жёлтый"),
    ACCENT_GREEN(0xFF4ADE80.toInt(), "Зелёный")
}

data class VideoClip(
    val id: String,
    val startMs: Long,
    val endMs: Long,
    val peakIntensity: Float = 0.5f,
    val label: String = "",
    val isCloseUp: Boolean = false
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
    val color: CaptionColor,
    val isActionNote: Boolean = false
) {
    val durationMs: Long get() = (endMs - startMs).coerceAtLeast(140L)
}

data class FramingConfig(
    val autoCloseUpCuts: Boolean = true,
    val closeUpZoom: Float = 1.35f,
    val focalX: Float = 0.50f,
    val focalY: Float = 0.36f,
    val peakPunchEnabled: Boolean = true,
    val peakPunchBonus: Float = 0.05f,
    val vignette: Boolean = false
)

data class MemeConfig(
    val topBarEnabled: Boolean = false,
    val topBarText: String = "ВОСПРИНЯЛ ВСЁ БУКВАЛЬНО",
    val reactionClipUri: Uri? = null,
    val reactionClipName: String = "",
    val reactionClipDurationMs: Long = 0L
)

data class AdBannerConfig(
    val enabled: Boolean = false,
    val imageUri: Uri? = null,
    val triggerAtMs: Long = 3000L,
    val durationSec: Int = 5,
    val areaPercent: Int = 30, // 25%..35% of screen area
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
    val silenceThreshold: Float = EditMode.CLEAN_REFERENCE.defaultSilenceThreshold,
    val editMode: EditMode = EditMode.CLEAN_REFERENCE,
    val framing: FramingConfig = EditMode.CLEAN_REFERENCE.defaultFraming,
    val meme: MemeConfig = MemeConfig(),
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
    val totalMainContentMs: Long
        get() = clips.sumOf { it.durationMs }.coerceAtLeast(0L)

    val activeReactionDurationMs: Long
        get() = if (meme.reactionClipUri != null && meme.reactionClipDurationMs > 0L) {
            meme.reactionClipDurationMs
        } else {
            0L
        }

    val totalEditedContentMs: Long
        get() = totalMainContentMs + activeReactionDurationMs
}
