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
        titleRu = "Чистый стиль (С лицом)",
        shortTitleRu = "Чистый",
        subtitleRu = "Вырезка пауз, наезд 1.35x точно на лицо, сочные разноцветные субтитры снизу и свуш-переходы",
        badgeRu = "ХИТ",
        defaultSilenceThreshold = 0.13f,
        defaultMinSilenceMs = 280L,
        defaultFraming = FramingConfig(
            autoCloseUpCuts = true,
            autoFaceTracking = true,
            closeUpZoom = 1.35f,
            focalX = 0.50f,
            focalY = 0.35f,
            saturatedPeakEffects = true,
            peakPunchEnabled = true,
            peakPunchBonus = 0.08f,
            neonFlashOnPeak = true,
            microShakeOnPeak = false,
            vignette = true,
            sfxPack = SfxPack.AUTO_MIX,
            sfxVolume = 0.72f
        ),
        enablesMemeBar = false
    ),
    LITVIN_DYNAMIC(
        titleRu = "Динамика / Литвин",
        shortTitleRu = "Литвин",
        subtitleRu = "Максимально резкие склейки, сочные неоновые вспышки + тряска на пиках, бас-бум SFX и яркие буквы",
        badgeRu = "ТОП",
        defaultSilenceThreshold = 0.16f,
        defaultMinSilenceMs = 220L,
        defaultFraming = FramingConfig(
            autoCloseUpCuts = true,
            autoFaceTracking = true,
            closeUpZoom = 1.42f,
            focalX = 0.50f,
            focalY = 0.35f,
            saturatedPeakEffects = true,
            peakPunchEnabled = true,
            peakPunchBonus = 0.13f,
            neonFlashOnPeak = true,
            microShakeOnPeak = true,
            vignette = true,
            sfxPack = SfxPack.AUTO_MIX,
            sfxVolume = 0.85f
        ),
        enablesMemeBar = false
    ),
    MEME_INSERT(
        titleRu = "Мем-вклейка",
        shortTitleRu = "Мем",
        subtitleRu = "Чёрная плашка сверху + вклейка мем-клипа в конец + прикольные мемные звуки на пиках",
        badgeRu = "МЕМ",
        defaultSilenceThreshold = 0.13f,
        defaultMinSilenceMs = 280L,
        defaultFraming = FramingConfig(
            autoCloseUpCuts = true,
            autoFaceTracking = true,
            closeUpZoom = 1.38f,
            focalX = 0.50f,
            focalY = 0.36f,
            saturatedPeakEffects = true,
            peakPunchEnabled = true,
            peakPunchBonus = 0.10f,
            neonFlashOnPeak = true,
            microShakeOnPeak = true,
            vignette = true,
            sfxPack = SfxPack.VINE_BOOM,
            sfxVolume = 0.85f
        ),
        enablesMemeBar = true
    )
}

enum class EditorTab(val titleRu: String) {
    MODE("Режим"),
    SLICES("Нарезка"),
    CAPTIONS("Субтитры"),
    FRAMING("Камера и FX"),
    AD("Реклама")
}

enum class SubtitlePosition(val labelRu: String) {
    BOTTOM("Снизу"),
    CENTER("По центру")
}

enum class SfxPack(val labelRu: String, val descRu: String) {
    AUTO_MIX("Авто-микс", "Свуш на наездах + Бас-бум на криках + Поп/Дзинь"),
    VINE_BOOM("Бас-бум", "Мощный мемный басовый удар в ключевых моментах"),
    WHOOSH("Свуш", "Динамичный свист перехода на смене планов"),
    POP("Поп", "Прикольный чпоньк-акцент на смешных пиках"),
    GLITCH_DING("Дзинь", "Яркий звонкий хайлайт-эффект"),
    OFF("Без SFX", "Выключить звуковые эффекты")
}

enum class SfxSound {
    VINE_BOOM,
    WHOOSH,
    POP,
    GLITCH_DING
}

data class SfxEvent(
    val id: String,
    val editedMs: Long,
    val sound: SfxSound,
    val labelRu: String
)

enum class CaptionColor(val argb: Int, val glowArgb: Int, val labelRu: String) {
    VIVID_YELLOW(0xFFFFE600.toInt(), 0xAAFFB800.toInt(), "Жёлтый"),
    NEON_LIME(0xFF39FF14.toInt(), 0xAA00E676.toInt(), "Неон"),
    HOT_PINK(0xFFFF2E93.toInt(), 0xAAFF007F.toInt(), "Розовый"),
    ELECTRIC_CYAN(0xFF00F0FF.toInt(), 0xAA00B8FF.toInt(), "Циан"),
    VIVID_ORANGE(0xFFFF7A00.toInt(), 0xAAFF4500.toInt(), "Оранж"),
    ACTION_WHITE(0xFFFFFFFF.toInt(), 0xAA38BDF8.toInt(), "Белый")
}

data class VideoClip(
    val id: String,
    val startMs: Long,
    val endMs: Long,
    val peakIntensity: Float = 0.5f,
    val label: String = "",
    val isCloseUp: Boolean = false,
    val focusX: Float = 0.50f,
    val focusY: Float = 0.35f,
    val hasDetectedFace: Boolean = false
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
    val autoFaceTracking: Boolean = true,
    val closeUpZoom: Float = 1.35f,
    val focalX: Float = 0.50f,
    val focalY: Float = 0.35f,
    val saturatedPeakEffects: Boolean = true,
    val peakPunchEnabled: Boolean = true,
    val peakPunchBonus: Float = 0.08f,
    val neonFlashOnPeak: Boolean = true,
    val microShakeOnPeak: Boolean = false,
    val vignette: Boolean = true,
    val sfxPack: SfxPack = SfxPack.AUTO_MIX,
    val sfxVolume: Float = 0.75f
)

data class MemeConfig(
    val topBarEnabled: Boolean = false,
    val topBarText: String = "ВОСПРИНЯЛ ВСЁ БУКВАЛЬНО",
    val reactionClipUri: Uri? = null,
    val reactionClipName: String = "",
    val reactionClipDurationMs: Long = 0L
)

enum class AdMediaType {
    IMAGE,
    VIDEO
}

data class AdBannerConfig(
    val enabled: Boolean = false,
    val mediaType: AdMediaType = AdMediaType.IMAGE,
    val mediaUri: Uri? = null,
    val mediaDisplayName: String = "",
    val adVideoDurationMs: Long = 0L,
    val triggerAtMs: Long = 3000L,
    val durationSec: Int = 5,
    val areaPercent: Int = 30, // 25%..35% of screen area
    val useVideoOwnAudio: Boolean = true,
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
    val isTrackingFaces: Boolean = false,
    val faceTrackingStatus: String? = null,
    val isTranscribingVideo: Boolean = false,
    val analysisError: String? = null,
    val sourceDurationMs: Long = 0L,
    val videoWidth: Int = 1080,
    val videoHeight: Int = 1920,
    val videoRotation: Int = 0,
    val rmsEnvelope: List<Float> = emptyList(),
    val rmsWindowMs: Long = 50L,
    val audioPeaks: List<AudioPeak> = emptyList(),
    val clips: List<VideoClip> = emptyList(),
    val sfxEvents: List<SfxEvent> = emptyList(),
    val selectedClipId: String? = null,
    val silenceThreshold: Float = EditMode.CLEAN_REFERENCE.defaultSilenceThreshold,
    val editMode: EditMode = EditMode.CLEAN_REFERENCE,
    val framing: FramingConfig = EditMode.CLEAN_REFERENCE.defaultFraming,
    val meme: MemeConfig = MemeConfig(),
    val captionsEnabled: Boolean = true,
    val subtitlePosition: SubtitlePosition = SubtitlePosition.BOTTOM,
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
    val seekRequestNonce: Long = 0L,
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
