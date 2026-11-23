package com.example.viewmodel

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.media.AdVoiceoverSynthesizer
import com.example.media.AudioAnalyzer
import com.example.media.SpeechCaptioner
import com.example.media.SubtitleEngine
import com.example.media.VideoExporter
import com.example.model.CaptionColor
import com.example.model.EditMode
import com.example.model.EditorTab
import com.example.model.EditorUiState
import com.example.model.EffectsConfig
import com.example.model.ExportState
import com.example.model.VideoClip
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.math.max
import kotlin.math.min

class EditorViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(EditorUiState())
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    private val appContext get() = getApplication<Application>()
    private var rmsEnvelopeArray: FloatArray = FloatArray(0)
    private var analyzeJob: Job? = null
    private var playbackJob: Job? = null
    private var exportJob: Job? = null
    private var adTriggerConsumedForRun: Boolean = false

    private val ttsSynthesizer = AdVoiceoverSynthesizer(application)
    private val speechCaptioner = SpeechCaptioner(
        context = application,
        onPartialResult = { partial ->
            _uiState.update { it.copy(speechPartialText = partial, speechStatusMessage = null) }
        },
        onFinalResult = { text ->
            val current = _uiState.value.rawTranscriptText.trim()
            val combined = if (current.isEmpty()) text else "$current $text"
            updateTranscriptText(combined)
            _uiState.update {
                it.copy(
                    speechPartialText = "",
                    speechStatusMessage = "Речь распознана и разбита на субтитры"
                )
            }
        },
        onListeningChanged = { listening ->
            _uiState.update { it.copy(isListeningSpeech = listening) }
        },
        onError = { msg ->
            _uiState.update { it.copy(speechStatusMessage = msg, speechPartialText = "") }
        }
    )

    fun selectTab(tab: EditorTab) {
        _uiState.update { it.copy(activeTab = tab) }
    }

    fun selectEditMode(mode: EditMode) {
        _uiState.update { state ->
            val updatedCaptions = if (state.rawTranscriptText.isNotBlank()) {
                SubtitleEngine.generateChunksFromRussianText(
                    state.rawTranscriptText,
                    state.totalEditedContentMs.coerceAtLeast(1000L),
                    mode
                )
            } else {
                state.captions
            }
            state.copy(
                editMode = mode,
                effects = mode.defaultEffects,
                silenceThreshold = mode.defaultSilenceThreshold,
                captions = updatedCaptions
            )
        }
        if (rmsEnvelopeArray.isNotEmpty()) {
            resliceClips(mode.defaultSilenceThreshold, mode.defaultMinSilenceMs)
        }
    }

    fun onVideoSelected(uri: Uri) {
        stopPlayback()
        val displayName = resolveDisplayName(uri) ?: "Видео.mp4"
        _uiState.update {
            it.copy(
                videoUri = uri,
                videoFileName = displayName,
                isAnalyzing = true,
                analysisError = null,
                currentEditedMs = 0L,
                isPlaying = false,
                isAdPauseActive = false,
                exportState = ExportState.Idle
            )
        }

        analyzeJob?.cancel()
        analyzeJob = viewModelScope.launch {
            runCatching {
                val mode = _uiState.value.editMode
                AudioAnalyzer.analyzeVideo(
                    context = appContext,
                    uri = uri,
                    silenceThreshold = _uiState.value.silenceThreshold,
                    minSilenceMs = mode.defaultMinSilenceMs
                )
            }.onSuccess { result ->
                rmsEnvelopeArray = result.rmsEnvelope
                val totalEdited = result.clips.sumOf { it.durationMs }.coerceAtLeast(500L)
                val defaultAdTrigger = (totalEdited / 2L).coerceAtLeast(500L)
                val regeneratedCaptions = if (_uiState.value.rawTranscriptText.isNotBlank()) {
                    SubtitleEngine.generateChunksFromRussianText(
                        _uiState.value.rawTranscriptText,
                        totalEdited,
                        _uiState.value.editMode
                    )
                } else {
                    emptyList()
                }
                _uiState.update { state ->
                    state.copy(
                        isAnalyzing = false,
                        sourceDurationMs = result.durationMs,
                        videoWidth = result.width,
                        videoHeight = result.height,
                        videoRotation = result.rotation,
                        rmsEnvelope = downsampleForUi(result.rmsEnvelope, 96),
                        rmsWindowMs = result.windowMs,
                        audioPeaks = result.peaks,
                        clips = result.clips,
                        selectedClipId = result.clips.firstOrNull()?.id,
                        captions = regeneratedCaptions,
                        adBanner = state.adBanner.copy(
                            triggerAtMs = defaultAdTrigger
                        )
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isAnalyzing = false,
                        analysisError = "Не удалось проанализировать видео: ${err.localizedMessage ?: "неизвестная ошибка"}"
                    )
                }
            }
        }
    }

    fun updateSilenceThreshold(threshold: Float) {
        val clamped = threshold.coerceIn(0.03f, 0.45f)
        _uiState.update { it.copy(silenceThreshold = clamped) }
        if (rmsEnvelopeArray.isNotEmpty()) {
            resliceClips(clamped, _uiState.value.editMode.defaultMinSilenceMs)
        }
    }

    private fun resliceClips(threshold: Float, minSilenceMs: Long) {
        val state = _uiState.value
        if (state.sourceDurationMs <= 0L) return
        val newClips = AudioAnalyzer.sliceClipsFromEnvelope(
            envelope = rmsEnvelopeArray,
            durationMs = state.sourceDurationMs,
            windowMs = state.rmsWindowMs,
            silenceThreshold = threshold,
            minSilenceMs = minSilenceMs
        )
        applyUpdatedClips(newClips)
    }

    fun selectClip(clipId: String) {
        val clips = _uiState.value.clips
        var offset = 0L
        for (clip in clips) {
            if (clip.id == clipId) {
                _uiState.update {
                    it.copy(
                        selectedClipId = clipId,
                        currentEditedMs = offset,
                        isAdPauseActive = false
                    )
                }
                return
            }
            offset += clip.durationMs
        }
    }

    fun splitClip(clipId: String) {
        val clips = _uiState.value.clips
        val idx = clips.indexOfFirst { it.id == clipId }
        if (idx < 0) return
        val target = clips[idx]
        if (target.durationMs < 700L) return

        val midMs = (target.startMs + target.endMs) / 2L
        val first = target.copy(
            id = UUID.randomUUID().toString(),
            endMs = midMs
        )
        val second = target.copy(
            id = UUID.randomUUID().toString(),
            startMs = midMs
        )
        val updated = clips.toMutableList().apply {
            removeAt(idx)
            add(idx, second)
            add(idx, first)
        }.mapIndexed { i, c -> c.copy(label = "Часть ${i + 1}") }

        applyUpdatedClips(updated, selectedId = first.id)
    }

    fun splitSelectedClip() {
        val targetId = _uiState.value.selectedClipId ?: _uiState.value.clips.firstOrNull()?.id ?: return
        splitClip(targetId)
    }

    fun deleteClip(clipId: String) {
        val clips = _uiState.value.clips
        if (clips.size <= 1) return
        val updated = clips
            .filterNot { it.id == clipId }
            .mapIndexed { i, c -> c.copy(label = "Часть ${i + 1}") }
        applyUpdatedClips(updated)
    }

    fun resetSlices() {
        resliceClips(_uiState.value.silenceThreshold, _uiState.value.editMode.defaultMinSilenceMs)
    }

    fun restoreFullVideoClip() {
        val dur = _uiState.value.sourceDurationMs
        if (dur <= 0L) return
        val full = VideoClip(
            id = UUID.randomUUID().toString(),
            startMs = 0L,
            endMs = dur,
            peakIntensity = 0.8f,
            label = "Часть 1"
        )
        applyUpdatedClips(listOf(full), selectedId = full.id)
    }

    private fun applyUpdatedClips(newClips: List<VideoClip>, selectedId: String? = null) {
        val totalEdited = newClips.sumOf { it.durationMs }.coerceAtLeast(300L)
        _uiState.update { state ->
            val nextCaptions = if (state.rawTranscriptText.isNotBlank()) {
                SubtitleEngine.generateChunksFromRussianText(
                    state.rawTranscriptText,
                    totalEdited,
                    state.editMode
                )
            } else {
                state.captions
            }
            state.copy(
                clips = newClips,
                selectedClipId = selectedId ?: newClips.firstOrNull()?.id,
                currentEditedMs = state.currentEditedMs.coerceIn(0L, totalEdited),
                captions = nextCaptions,
                adBanner = state.adBanner.copy(
                    triggerAtMs = state.adBanner.triggerAtMs.coerceIn(200L, max(200L, totalEdited - 200L))
                )
            )
        }
    }

    fun toggleCaptionsEnabled(enabled: Boolean) {
        _uiState.update { it.copy(captionsEnabled = enabled) }
    }

    fun updateTranscriptText(text: String) {
        val state = _uiState.value
        val totalMs = state.totalEditedContentMs.coerceAtLeast(3000L)
        val chunks = SubtitleEngine.generateChunksFromRussianText(text, totalMs, state.editMode)
        _uiState.update {
            it.copy(
                rawTranscriptText = text,
                captions = chunks,
                captionsEnabled = true
            )
        }
    }

    fun cycleCaptionColor(chunkId: String) {
        val colors = CaptionColor.entries
        _uiState.update { state ->
            state.copy(
                captions = state.captions.map { chunk ->
                    if (chunk.id == chunkId) {
                        val nextIdx = (colors.indexOf(chunk.color) + 1) % colors.size
                        chunk.copy(color = colors[nextIdx])
                    } else {
                        chunk
                    }
                }
            )
        }
    }

    fun updateCaptionText(chunkId: String, newText: String) {
        _uiState.update { state ->
            state.copy(
                captions = state.captions.map { chunk ->
                    if (chunk.id == chunkId) chunk.copy(text = newText.uppercase()) else chunk
                }
            )
        }
    }

    fun deleteCaption(chunkId: String) {
        _uiState.update { state ->
            state.copy(captions = state.captions.filterNot { it.id == chunkId })
        }
    }

    fun startRussianSpeechRecognition() {
        _uiState.update { it.copy(speechStatusMessage = "Говорите по-русски...") }
        speechCaptioner.startListeningRussian()
    }

    fun stopRussianSpeechRecognition() {
        speechCaptioner.stopListening()
    }

    fun onSpeechPermissionDenied() {
        _uiState.update {
            it.copy(speechStatusMessage = "Разрешение на микрофон отклонено. Введите текст субтитров вручную.")
        }
    }

    fun updateEffects(transform: (EffectsConfig) -> EffectsConfig) {
        _uiState.update { it.copy(effects = transform(it.effects)) }
    }

    fun setAdBannerEnabled(enabled: Boolean) {
        _uiState.update { it.copy(adBanner = it.adBanner.copy(enabled = enabled)) }
    }

    fun onAdImageSelected(uri: Uri) {
        _uiState.update {
            it.copy(
                adBanner = it.adBanner.copy(
                    imageUri = uri,
                    enabled = true
                )
            )
        }
    }

    fun clearAdImage() {
        _uiState.update {
            it.copy(
                adBanner = it.adBanner.copy(
                    imageUri = null,
                    enabled = false
                )
            )
        }
    }

    fun onAdAudioSelected(uri: Uri) {
        val name = resolveDisplayName(uri) ?: "Озвучка.mp3"
        _uiState.update {
            it.copy(
                adBanner = it.adBanner.copy(
                    audioUri = uri,
                    audioDisplayName = name,
                    useTts = false
                )
            )
        }
    }

    fun clearAdAudio() {
        _uiState.update {
            it.copy(
                adBanner = it.adBanner.copy(
                    audioUri = null,
                    audioDisplayName = ""
                )
            )
        }
    }

    fun setAdTriggerMs(ms: Long) {
        val maxMs = max(200L, _uiState.value.totalEditedContentMs - 200L)
        _uiState.update {
            it.copy(adBanner = it.adBanner.copy(triggerAtMs = ms.coerceIn(200L, maxMs)))
        }
    }

    fun setAdDurationSec(sec: Int) {
        _uiState.update {
            it.copy(adBanner = it.adBanner.copy(durationSec = sec.coerceIn(3, 8)))
        }
    }

    fun setAdAreaPercent(percent: Int) {
        _uiState.update {
            it.copy(adBanner = it.adBanner.copy(areaPercent = percent.coerceIn(25, 35)))
        }
    }

    fun setAdUseTts(useTts: Boolean) {
        _uiState.update {
            it.copy(adBanner = it.adBanner.copy(useTts = useTts))
        }
    }

    fun setAdTtsText(text: String) {
        _uiState.update {
            it.copy(
                adBanner = it.adBanner.copy(
                    ttsText = text,
                    synthesizedTtsPath = null
                )
            )
        }
    }

    fun updateAdBanner(transform: (com.example.model.AdBannerConfig) -> com.example.model.AdBannerConfig) {
        _uiState.update { it.copy(adBanner = transform(it.adBanner)) }
    }

    fun previewAdTts() {
        val text = _uiState.value.adBanner.ttsText
        if (text.isNotBlank()) {
            ttsSynthesizer.speakPreview(text)
        }
    }

    fun triggerManualAdPreview() {
        val state = _uiState.value
        val adCfg = state.adBanner
        if (adCfg.imageUri == null || state.videoUri == null) return
        val targetMs = adCfg.triggerAtMs.coerceIn(0L, state.totalEditedContentMs)
        adTriggerConsumedForRun = true
        if (adCfg.useTts && adCfg.ttsText.isNotBlank()) {
            ttsSynthesizer.speakPreview(adCfg.ttsText)
        }
        _uiState.update {
            it.copy(
                currentEditedMs = targetMs,
                isPlaying = true,
                isAdPauseActive = true,
                adRemainingMs = adCfg.durationSec * 1000L,
                adElapsedMs = 0L
            )
        }
        startPlayback(preserveAdPause = true)
    }

    fun togglePlayPause() {
        if (_uiState.value.isPlaying) {
            stopPlayback()
        } else {
            startPlayback()
        }
    }

    fun seekEditedMs(targetMs: Long) {
        val total = _uiState.value.totalEditedContentMs
        val clamped = targetMs.coerceIn(0L, total)
        adTriggerConsumedForRun = clamped > _uiState.value.adBanner.triggerAtMs
        _uiState.update {
            it.copy(
                currentEditedMs = clamped,
                isAdPauseActive = false,
                adRemainingMs = 0L
            )
        }
    }

    private fun startPlayback(preserveAdPause: Boolean = false) {
        val state = _uiState.value
        if (state.videoUri == null || state.clips.isEmpty()) return

        if (!preserveAdPause) {
            val total = state.totalEditedContentMs
            val startMs = if (state.currentEditedMs >= total - 100L) 0L else state.currentEditedMs
            adTriggerConsumedForRun = startMs > state.adBanner.triggerAtMs

            _uiState.update {
                it.copy(
                    isPlaying = true,
                    currentEditedMs = startMs,
                    isAdPauseActive = false
                )
            }
        }

        playbackJob?.cancel()
        playbackJob = viewModelScope.launch {
            val tickMs = 33L
            while (isActive) {
                delay(tickMs)
                val cur = _uiState.value
                if (!cur.isPlaying) break

                if (cur.isAdPauseActive) {
                    val nextRemaining = (cur.adRemainingMs - tickMs).coerceAtLeast(0L)
                    val nextElapsed = cur.adElapsedMs + tickMs
                    if (nextRemaining <= 0L) {
                        ttsSynthesizer.stop()
                        _uiState.update {
                            it.copy(
                                isAdPauseActive = false,
                                adRemainingMs = 0L,
                                adElapsedMs = 0L
                            )
                        }
                    } else {
                        _uiState.update {
                            it.copy(
                                adRemainingMs = nextRemaining,
                                adElapsedMs = nextElapsed
                            )
                        }
                    }
                    continue
                }

                val nextEditedMs = cur.currentEditedMs + tickMs
                val adCfg = cur.adBanner
                val shouldTriggerAd = adCfg.enabled &&
                    adCfg.imageUri != null &&
                    !adTriggerConsumedForRun &&
                    cur.currentEditedMs <= adCfg.triggerAtMs &&
                    nextEditedMs >= adCfg.triggerAtMs

                if (shouldTriggerAd) {
                    adTriggerConsumedForRun = true
                    if (adCfg.useTts && adCfg.ttsText.isNotBlank()) {
                        ttsSynthesizer.speakPreview(adCfg.ttsText)
                    }
                    _uiState.update {
                        it.copy(
                            currentEditedMs = adCfg.triggerAtMs,
                            isAdPauseActive = true,
                            adRemainingMs = adCfg.durationSec * 1000L,
                            adElapsedMs = 0L
                        )
                    }
                    continue
                }

                val totalMs = cur.totalEditedContentMs
                if (nextEditedMs >= totalMs) {
                    _uiState.update {
                        it.copy(
                            isPlaying = false,
                            currentEditedMs = 0L,
                            isAdPauseActive = false
                        )
                    }
                    adTriggerConsumedForRun = false
                    break
                } else {
                    val activeClipId = SubtitleEngine.mapEditedMsToSource(nextEditedMs, cur.clips)?.clip?.id
                    _uiState.update {
                        it.copy(
                            currentEditedMs = nextEditedMs,
                            selectedClipId = activeClipId ?: it.selectedClipId
                        )
                    }
                }
            }
        }
    }

    fun stopPlayback() {
        playbackJob?.cancel()
        ttsSynthesizer.stop()
        _uiState.update {
            it.copy(
                isPlaying = false,
                isAdPauseActive = false,
                adRemainingMs = 0L
            )
        }
    }

    fun exportFullVideo() {
        val state = _uiState.value
        val uri = state.videoUri ?: return
        if (state.clips.isEmpty()) return
        stopPlayback()

        exportJob?.cancel()
        exportJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    exportState = ExportState.Exporting(
                        progress = 0.02f,
                        statusText = "Подготовка экспорта..."
                    )
                )
            }

            var adConfig = state.adBanner
            if (adConfig.enabled && adConfig.imageUri != null && adConfig.useTts && adConfig.ttsText.isNotBlank()) {
                _uiState.update {
                    it.copy(
                        isTtsSynthesizing = true,
                        exportState = ExportState.Exporting(0.03f, "Синтез русской озвучки TTS...")
                    )
                }
                val wavPath = ttsSynthesizer.synthesizeToWav(adConfig.ttsText)
                adConfig = adConfig.copy(synthesizedTtsPath = wavPath)
                _uiState.update { it.copy(isTtsSynthesizing = false, adBanner = adConfig) }
            }

            runCatching {
                VideoExporter.exportFullVideo(
                    context = appContext,
                    sourceUri = uri,
                    clips = state.clips,
                    peaks = state.audioPeaks,
                    captions = state.captions,
                    captionsEnabled = state.captionsEnabled,
                    effects = state.effects,
                    adBanner = adConfig,
                    onProgress = { progress, text ->
                        _uiState.update {
                            it.copy(exportState = ExportState.Exporting(progress, text, 1, 1))
                        }
                    }
                )
            }.onSuccess { exported ->
                _uiState.update {
                    it.copy(exportState = ExportState.Completed(listOf(exported), isBatch = false))
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        exportState = ExportState.Error(
                            "Ошибка экспорта: ${err.localizedMessage ?: "попробуйте другой ролик"}"
                        )
                    )
                }
            }
        }
    }

    fun exportAllClips() {
        val state = _uiState.value
        val uri = state.videoUri ?: return
        if (state.clips.isEmpty()) return
        stopPlayback()

        exportJob?.cancel()
        exportJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    exportState = ExportState.Exporting(
                        progress = 0.02f,
                        statusText = "Подготовка экспорта ${state.clips.size} частей...",
                        currentPart = 1,
                        totalParts = state.clips.size
                    )
                )
            }

            runCatching {
                VideoExporter.exportAllClips(
                    context = appContext,
                    sourceUri = uri,
                    clips = state.clips,
                    peaks = state.audioPeaks,
                    captions = state.captions,
                    captionsEnabled = state.captionsEnabled,
                    effects = state.effects,
                    onProgress = { progress, text, curPart, totalParts ->
                        _uiState.update {
                            it.copy(
                                exportState = ExportState.Exporting(
                                    progress = progress,
                                    statusText = text,
                                    currentPart = curPart,
                                    totalParts = totalParts
                                )
                            )
                        }
                    }
                )
            }.onSuccess { list ->
                _uiState.update {
                    it.copy(exportState = ExportState.Completed(list, isBatch = true))
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        exportState = ExportState.Error(
                            "Ошибка пакетного экспорта: ${err.localizedMessage ?: "попробуйте снова"}"
                        )
                    )
                }
            }
        }
    }

    fun cancelExport() {
        exportJob?.cancel()
        _uiState.update { it.copy(exportState = ExportState.Idle) }
    }

    fun dismissExportDialog() {
        _uiState.update { it.copy(exportState = ExportState.Idle) }
    }

    private fun downsampleForUi(source: FloatArray, targetBins: Int): List<Float> {
        if (source.isEmpty()) return emptyList()
        if (source.size <= targetBins) return source.toList()
        val result = ArrayList<Float>(targetBins)
        val step = source.size.toFloat() / targetBins.toFloat()
        for (i in 0 until targetBins) {
            val s = (i * step).toInt().coerceIn(0, source.lastIndex)
            val e = min(source.size, ((i + 1) * step).toInt().coerceAtLeast(s + 1))
            var maxV = 0f
            for (j in s until e) {
                if (source[j] > maxV) maxV = source[j]
            }
            result.add(maxV)
        }
        return result
    }

    private fun resolveDisplayName(uri: Uri): String? {
        return runCatching {
            appContext.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                }
        }.getOrNull()
    }

    override fun onCleared() {
        super.onCleared()
        stopPlayback()
        exportJob?.cancel()
        speechCaptioner.release()
        ttsSynthesizer.release()
    }
}
