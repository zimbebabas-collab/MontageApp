package com.example.viewmodel

import android.app.Application
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.media.AdVoiceoverSynthesizer
import com.example.media.AudioAnalyzer
import com.example.media.FaceAndFocusTracker
import com.example.media.SoundEffectsEngine
import com.example.media.SpeechCaptioner
import com.example.media.SubtitleEngine
import com.example.media.VideoExporter
import com.example.media.VideoSpeechTranscriber
import com.example.model.AdMediaType
import com.example.model.EditMode
import com.example.model.EditorTab
import com.example.model.EditorUiState
import com.example.model.ExportState
import com.example.model.FramingConfig
import com.example.model.MemeConfig
import com.example.model.SfxPack
import com.example.model.SfxSound
import com.example.model.SubtitlePosition
import com.example.model.VideoClip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import kotlin.math.max
import kotlin.math.min

class EditorViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(EditorUiState())
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    private val appContext get() = getApplication<Application>()
    private var rmsEnvelopeArray: FloatArray = FloatArray(0)
    private var analyzeJob: Job? = null
    private var faceTrackJob: Job? = null
    private var transcribeJob: Job? = null
    private var adCountdownJob: Job? = null
    private var exportJob: Job? = null

    private var adTriggerConsumedForRun: Boolean = false
    private val firedSfxIds = HashSet<String>()

    private val ttsSynthesizer = AdVoiceoverSynthesizer(application)
    private val speechCaptioner = SpeechCaptioner(
        context = application,
        onPartialResult = { partial ->
            _uiState.update { it.copy(speechPartialText = partial, speechStatusMessage = null) }
        },
        onFinalResult = { text ->
            val current = _uiState.value.rawTranscriptText.trim()
            val combined = if (current.isEmpty()) text else "$current\n$text"
            updateTranscriptText(combined)
            _uiState.update {
                it.copy(
                    speechPartialText = "",
                    speechStatusMessage = "Речь распознана (ru-RU) — яркие субтитры готовы"
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
                SubtitleEngine.generateVibrantCaptions(
                    state.rawTranscriptText,
                    state.totalMainContentMs.coerceAtLeast(1000L),
                    mode
                )
            } else {
                state.captions
            }
            val nextFraming = mode.defaultFraming.copy(
                focalX = state.framing.focalX,
                focalY = state.framing.focalY
            )
            val nextSfx = SoundEffectsEngine.generateEventsForTimeline(
                clips = state.clips,
                peaks = state.audioPeaks,
                sfxPack = nextFraming.sfxPack
            )
            state.copy(
                editMode = mode,
                framing = nextFraming,
                meme = state.meme.copy(topBarEnabled = mode.enablesMemeBar),
                silenceThreshold = mode.defaultSilenceThreshold,
                captions = updatedCaptions,
                sfxEvents = nextSfx
            )
        }
        if (rmsEnvelopeArray.isNotEmpty()) {
            resliceClips(mode.defaultSilenceThreshold, mode.defaultMinSilenceMs)
        }
    }

    fun onVideoSelected(uri: Uri) {
        stopPlayback()
        faceTrackJob?.cancel()
        transcribeJob?.cancel()

        val displayName = resolveDisplayName(uri) ?: "Видео_9x16.mp4"
        _uiState.update {
            it.copy(
                videoUri = uri,
                videoFileName = displayName,
                isAnalyzing = true,
                isTrackingFaces = false,
                faceTrackingStatus = null,
                isTranscribingVideo = false,
                analysisError = null,
                speechStatusMessage = null,
                currentEditedMs = 0L,
                seekRequestNonce = it.seekRequestNonce + 1L,
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
                val totalMain = result.clips.sumOf { it.durationMs }.coerceAtLeast(500L)
                val defaultAdTrigger = (totalMain / 2L).coerceAtLeast(500L)
                val currentState = _uiState.value
                val regeneratedCaptions = if (currentState.rawTranscriptText.isNotBlank()) {
                    SubtitleEngine.generateVibrantCaptions(
                        currentState.rawTranscriptText,
                        totalMain,
                        currentState.editMode
                    )
                } else {
                    emptyList()
                }
                val sfxEvents = SoundEffectsEngine.generateEventsForTimeline(
                    clips = result.clips,
                    peaks = result.peaks,
                    sfxPack = currentState.framing.sfxPack
                )

                _uiState.update { state ->
                    state.copy(
                        isAnalyzing = false,
                        sourceDurationMs = result.durationMs,
                        videoWidth = result.width,
                        videoHeight = result.height,
                        videoRotation = result.rotation,
                        rmsEnvelope = downsampleForUi(result.rmsEnvelope, 88),
                        rmsWindowMs = result.windowMs,
                        audioPeaks = result.peaks,
                        clips = result.clips,
                        sfxEvents = sfxEvents,
                        selectedClipId = result.clips.firstOrNull()?.id,
                        captions = regeneratedCaptions,
                        adBanner = state.adBanner.copy(triggerAtMs = defaultAdTrigger)
                    )
                }

                // Automatically track speaker faces & key points per clip (ML Kit + saliency)
                launchFaceTracking(uri, result.clips)

                // Automatically attempt on-device Russian voice recognition from the video track
                autoTranscribeVideoAudio()
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isAnalyzing = false,
                        analysisError = "Не удалось проанализировать видео: ${err.localizedMessage ?: "ошибка файла"}"
                    )
                }
            }
        }
    }

    // ---- Automatic Face & Key-Point Tracking (ML Kit) ----

    fun retrackFaces() {
        val state = _uiState.value
        val uri = state.videoUri ?: return
        if (state.clips.isEmpty()) return
        launchFaceTracking(uri, state.clips)
    }

    private fun launchFaceTracking(uri: Uri, targetClips: List<VideoClip>) {
        faceTrackJob?.cancel()
        faceTrackJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isTrackingFaces = true,
                    faceTrackingStatus = "Поиск лиц и ключевых точек..."
                )
            }
            val tracked = FaceAndFocusTracker.trackClipsFocus(appContext, uri, targetClips)
            val faceCount = tracked.count { it.hasDetectedFace }
            val statusText = if (faceCount > 0) {
                "Лицо зафиксировано в $faceCount из ${tracked.size} клипов"
            } else {
                "Зафиксированы ключевые точки сцены (${tracked.size} клипов)"
            }
            val firstFocus = tracked.firstOrNull()
            _uiState.update { state ->
                // Merge tracked coordinates by clip ID so user edits aren't overwritten
                val byId = tracked.associateBy { it.id }
                val mergedClips = state.clips.map { c ->
                    val t = byId[c.id]
                    if (t != null) {
                        c.copy(
                            focusX = t.focusX,
                            focusY = t.focusY,
                            hasDetectedFace = t.hasDetectedFace
                        )
                    } else {
                        c
                    }
                }
                state.copy(
                    isTrackingFaces = false,
                    faceTrackingStatus = statusText,
                    clips = mergedClips,
                    framing = if (firstFocus != null) {
                        state.framing.copy(
                            focalX = firstFocus.focusX,
                            focalY = firstFocus.focusY
                        )
                    } else {
                        state.framing
                    }
                )
            }
        }
    }

    // ---- Automatic Voice Recognition From Video Audio ----

    fun autoTranscribeVideoAudio() {
        val uri = _uiState.value.videoUri ?: return
        transcribeJob?.cancel()
        transcribeJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isTranscribingVideo = true,
                    speechStatusMessage = "Распознаём русский голос прямо из видео..."
                )
            }
            when (val res = VideoSpeechTranscriber.transcribeVideoAudioRussian(appContext, uri)) {
                is VideoSpeechTranscriber.Result.Success -> {
                    val totalMs = _uiState.value.totalMainContentMs.coerceAtLeast(3000L)
                    val chunks = SubtitleEngine.generateVibrantCaptions(
                        res.text,
                        totalMs,
                        _uiState.value.editMode
                    )
                    _uiState.update {
                        it.copy(
                            isTranscribingVideo = false,
                            rawTranscriptText = res.text,
                            captions = chunks,
                            captionsEnabled = true,
                            speechStatusMessage = "Голос из видео распознан автоматически (${chunks.size} слов)"
                        )
                    }
                }
                is VideoSpeechTranscriber.Result.UnsupportedOrEmpty -> {
                    _uiState.update {
                        it.copy(
                            isTranscribingVideo = false,
                            speechStatusMessage = res.reasonRu
                        )
                    }
                }
            }
        }
    }

    // ---- Auto-Slicing & Clips ----

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
        state.videoUri?.let { launchFaceTracking(it, newClips) }
    }

    fun selectClip(clipId: String) {
        val clips = _uiState.value.clips
        var offset = 0L
        for (clip in clips) {
            if (clip.id == clipId) {
                adTriggerConsumedForRun = offset > _uiState.value.adBanner.triggerAtMs
                resetFiredSfxAfter(offset)
                _uiState.update {
                    it.copy(
                        selectedClipId = clipId,
                        currentEditedMs = offset,
                        seekRequestNonce = it.seekRequestNonce + 1L,
                        isAdPauseActive = false
                    )
                }
                return
            }
            offset += clip.durationMs
        }
    }

    fun toggleClipCloseUp(clipId: String) {
        _uiState.update { state ->
            val updatedClips = state.clips.map { clip ->
                if (clip.id == clipId) clip.copy(isCloseUp = !clip.isCloseUp) else clip
            }
            val updatedSfx = SoundEffectsEngine.generateEventsForTimeline(
                clips = updatedClips,
                peaks = state.audioPeaks,
                sfxPack = state.framing.sfxPack
            )
            state.copy(clips = updatedClips, sfxEvents = updatedSfx)
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
            startMs = midMs,
            isCloseUp = !target.isCloseUp
        )
        val updated = clips.toMutableList().apply {
            removeAt(idx)
            add(idx, second)
            add(idx, first)
        }.mapIndexed { i, c -> c.copy(label = "Часть ${i + 1}") }

        applyUpdatedClips(updated, selectedId = second.id)
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

    private fun applyUpdatedClips(newClips: List<VideoClip>, selectedId: String? = null) {
        val totalMain = newClips.sumOf { it.durationMs }.coerceAtLeast(300L)
        _uiState.update { state ->
            val nextCaptions = if (state.rawTranscriptText.isNotBlank()) {
                SubtitleEngine.generateVibrantCaptions(
                    state.rawTranscriptText,
                    totalMain,
                    state.editMode
                )
            } else {
                state.captions
            }
            val nextSfx = SoundEffectsEngine.generateEventsForTimeline(
                clips = newClips,
                peaks = state.audioPeaks,
                sfxPack = state.framing.sfxPack
            )
            val totalAll = totalMain + state.activeReactionDurationMs
            state.copy(
                clips = newClips,
                sfxEvents = nextSfx,
                selectedClipId = selectedId ?: newClips.firstOrNull()?.id,
                currentEditedMs = state.currentEditedMs.coerceIn(0L, totalAll),
                seekRequestNonce = state.seekRequestNonce + 1L,
                captions = nextCaptions,
                adBanner = state.adBanner.copy(
                    triggerAtMs = state.adBanner.triggerAtMs.coerceIn(200L, max(200L, totalAll - 200L))
                )
            )
        }
    }

    // ---- Vibrant Multi-Colored Captions ----

    fun toggleCaptionsEnabled(enabled: Boolean) {
        _uiState.update { it.copy(captionsEnabled = enabled) }
    }

    fun setSubtitlePosition(position: SubtitlePosition) {
        _uiState.update { it.copy(subtitlePosition = position) }
    }

    fun updateTranscriptText(text: String) {
        val state = _uiState.value
        val totalMs = state.totalMainContentMs.coerceAtLeast(3000L)
        val chunks = SubtitleEngine.generateVibrantCaptions(text, totalMs, state.editMode)
        _uiState.update {
            it.copy(
                rawTranscriptText = text,
                captions = chunks,
                captionsEnabled = true,
                speechStatusMessage = null
            )
        }
    }

    fun cycleCaptionColor(chunkId: String) {
        _uiState.update { state ->
            state.copy(
                captions = state.captions.map { chunk ->
                    if (chunk.id == chunkId) {
                        chunk.copy(color = SubtitleEngine.nextColor(chunk.color))
                    } else {
                        chunk
                    }
                }
            )
        }
    }

    fun startRussianSpeechRecognition() {
        _uiState.update { it.copy(speechStatusMessage = "Слушаю русскую речь (ru-RU)...") }
        speechCaptioner.startListeningRussian()
    }

    fun stopRussianSpeechRecognition() {
        speechCaptioner.stopListening()
    }

    fun onSpeechPermissionDenied() {
        _uiState.update {
            it.copy(speechStatusMessage = "Нет доступа к микрофону. Вставьте текст ниже — яркие слова появятся автоматически.")
        }
    }

    // ---- Smart Camera Framing, Saturated FX & SFX ----

    fun updateFraming(transform: (FramingConfig) -> FramingConfig) {
        _uiState.update { state ->
            val next = transform(state.framing)
            val nextSfx = if (next.sfxPack != state.framing.sfxPack) {
                SoundEffectsEngine.generateEventsForTimeline(state.clips, state.audioPeaks, next.sfxPack)
            } else {
                state.sfxEvents
            }
            state.copy(framing = next, sfxEvents = nextSfx)
        }
    }

    fun setSfxPack(pack: SfxPack) {
        updateFraming { it.copy(sfxPack = pack) }
    }

    fun setSfxVolume(volume: Float) {
        updateFraming { it.copy(sfxVolume = volume.coerceIn(0f, 1f)) }
    }

    fun previewSfx(sound: SfxSound) {
        SoundEffectsEngine.playPreviewSfx(sound, _uiState.value.framing.sfxVolume.coerceAtLeast(0.5f))
    }

    fun updateFocalPoint(x: Float, y: Float) {
        val fx = x.coerceIn(0.15f, 0.85f)
        val fy = y.coerceIn(0.15f, 0.85f)
        _uiState.update { state ->
            val activeId = state.selectedClipId
            // Update both manual focal point and currently active clip's focus so user fine-tuning works instantly
            val updatedClips = if (activeId != null) {
                state.clips.map { c ->
                    if (c.id == activeId) c.copy(focusX = fx, focusY = fy) else c
                }
            } else {
                state.clips
            }
            state.copy(
                clips = updatedClips,
                framing = state.framing.copy(
                    focalX = fx,
                    focalY = fy
                )
            )
        }
    }

    // ---- Meme Top Bar & End Reaction Clip ----

    fun updateMemeConfig(transform: (MemeConfig) -> MemeConfig) {
        _uiState.update { it.copy(meme = transform(it.meme)) }
    }

    fun onReactionClipSelected(uri: Uri) {
        val name = resolveDisplayName(uri) ?: "Мем_реакция.mp4"
        viewModelScope.launch {
            val durMs = probeVideoDurationMs(uri).coerceIn(500L, 20_000L)
            _uiState.update { state ->
                state.copy(
                    meme = state.meme.copy(
                        reactionClipUri = uri,
                        reactionClipName = name,
                        reactionClipDurationMs = durMs
                    )
                )
            }
        }
    }

    fun clearReactionClip() {
        _uiState.update { state ->
            val nextTotal = state.totalMainContentMs
            state.copy(
                meme = state.meme.copy(
                    reactionClipUri = null,
                    reactionClipName = "",
                    reactionClipDurationMs = 0L
                ),
                currentEditedMs = state.currentEditedMs.coerceAtMost(nextTotal),
                seekRequestNonce = state.seekRequestNonce + 1L
            )
        }
    }

    // ---- Mid-Roll Ad Banner (IMAGE or VIDEO) ----

    fun setAdBannerEnabled(enabled: Boolean) {
        _uiState.update { it.copy(adBanner = it.adBanner.copy(enabled = enabled)) }
    }

    fun onAdImageSelected(uri: Uri) {
        val name = resolveDisplayName(uri) ?: "Баннер.png"
        _uiState.update {
            it.copy(
                adBanner = it.adBanner.copy(
                    mediaType = AdMediaType.IMAGE,
                    mediaUri = uri,
                    mediaDisplayName = name,
                    adVideoDurationMs = 0L,
                    useVideoOwnAudio = false,
                    enabled = true
                )
            )
        }
    }

    fun onAdVideoSelected(uri: Uri) {
        val name = resolveDisplayName(uri) ?: "Видео_баннер.mp4"
        viewModelScope.launch {
            val durMs = probeVideoDurationMs(uri).coerceIn(1000L, 60_000L)
            val suggestedSec = ((durMs + 500L) / 1000L).toInt().coerceIn(3, 15)
            _uiState.update {
                it.copy(
                    adBanner = it.adBanner.copy(
                        mediaType = AdMediaType.VIDEO,
                        mediaUri = uri,
                        mediaDisplayName = name,
                        adVideoDurationMs = durMs,
                        durationSec = suggestedSec,
                        useVideoOwnAudio = true,
                        useTts = false,
                        enabled = true
                    )
                )
            }
        }
    }

    fun clearAdMedia() {
        _uiState.update {
            it.copy(
                adBanner = it.adBanner.copy(
                    mediaUri = null,
                    mediaDisplayName = "",
                    adVideoDurationMs = 0L,
                    enabled = false
                )
            )
        }
    }

    fun setAdUseVideoOwnAudio(useOwn: Boolean) {
        _uiState.update {
            it.copy(
                adBanner = it.adBanner.copy(
                    useVideoOwnAudio = useOwn,
                    useTts = if (useOwn) false else it.adBanner.useTts
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
                    useTts = false,
                    useVideoOwnAudio = false
                )
            )
        }
    }

    fun clearAdAudio() {
        _uiState.update {
            it.copy(adBanner = it.adBanner.copy(audioUri = null, audioDisplayName = ""))
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
            it.copy(adBanner = it.adBanner.copy(durationSec = sec.coerceIn(3, 15)))
        }
    }

    fun setAdAreaPercent(percent: Int) {
        _uiState.update {
            it.copy(adBanner = it.adBanner.copy(areaPercent = percent.coerceIn(25, 35)))
        }
    }

    fun setAdUseTts(useTts: Boolean) {
        _uiState.update {
            it.copy(
                adBanner = it.adBanner.copy(
                    useTts = useTts,
                    useVideoOwnAudio = if (useTts) false else it.adBanner.useVideoOwnAudio
                )
            )
        }
    }

    fun setAdTtsText(text: String) {
        _uiState.update {
            it.copy(adBanner = it.adBanner.copy(ttsText = text, synthesizedTtsPath = null))
        }
    }

    fun previewAdTts() {
        val text = _uiState.value.adBanner.ttsText
        if (text.isNotBlank()) ttsSynthesizer.speakPreview(text)
    }

    fun testAdPausePreview() {
        val state = _uiState.value
        if (state.videoUri == null || state.adBanner.mediaUri == null) return
        val trigger = state.adBanner.triggerAtMs.coerceIn(0L, state.totalEditedContentMs)
        adTriggerConsumedForRun = true
        if (state.adBanner.useTts && state.adBanner.ttsText.isNotBlank()) {
            ttsSynthesizer.speakPreview(state.adBanner.ttsText)
        }
        _uiState.update {
            it.copy(
                currentEditedMs = trigger,
                seekRequestNonce = it.seekRequestNonce + 1L,
                isPlaying = true,
                isAdPauseActive = true,
                adRemainingMs = state.adBanner.durationSec * 1000L,
                adElapsedMs = 0L
            )
        }
        launchAdCountdownLoop()
    }

    // ---- Stutter-Free ExoPlayer-Driven Playback Controls ----

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
        resetFiredSfxAfter(clamped)
        adCountdownJob?.cancel()
        ttsSynthesizer.stop()
        _uiState.update {
            it.copy(
                currentEditedMs = clamped,
                seekRequestNonce = it.seekRequestNonce + 1L,
                isAdPauseActive = false,
                adRemainingMs = 0L,
                adElapsedMs = 0L
            )
        }
    }

    /**
     * Called directly from VideoPreviewStage while ExoPlayer smoothly plays at native framerate.
     * Eliminates fighting between a separate coroutine clock and ExoPlayer's media clock!
     */
    fun onPlayerProgress(newEditedMs: Long) {
        val cur = _uiState.value
        if (!cur.isPlaying || cur.isAdPauseActive) return

        val prevMs = cur.currentEditedMs

        // 1. Trigger viral SFX cleanly when crossing an event timestamp
        if (cur.framing.sfxPack != SfxPack.OFF && cur.framing.sfxVolume > 0.02f) {
            for (ev in cur.sfxEvents) {
                if (ev.id !in firedSfxIds && ev.editedMs in (prevMs - 20L)..(newEditedMs + 30L)) {
                    firedSfxIds.add(ev.id)
                    SoundEffectsEngine.playPreviewSfx(ev.sound, cur.framing.sfxVolume)
                }
            }
        }

        // 2. Trigger Mid-Roll Ad Banner Pause (Freeze-frame main video, play Image or Video Banner!)
        val adCfg = cur.adBanner
        val shouldTriggerAd = adCfg.enabled &&
            adCfg.mediaUri != null &&
            !adTriggerConsumedForRun &&
            prevMs <= adCfg.triggerAtMs &&
            newEditedMs >= adCfg.triggerAtMs

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
            launchAdCountdownLoop()
            return
        }

        // 3. Check end of edited timeline
        val totalMs = cur.totalEditedContentMs
        if (newEditedMs >= totalMs - 35L) {
            stopPlayback()
            adTriggerConsumedForRun = false
            firedSfxIds.clear()
            _uiState.update {
                it.copy(
                    currentEditedMs = 0L,
                    seekRequestNonce = it.seekRequestNonce + 1L
                )
            }
            return
        }

        // 4. Sync UI scrubber, active clip & subtitles smoothly
        val pos = SubtitleEngine.mapEditedMsToTimeline(
            newEditedMs,
            cur.clips,
            cur.activeReactionDurationMs
        )
        val activeClipId = (pos as? SubtitleEngine.TimelinePosition.MainClip)?.clip?.id
        _uiState.update {
            it.copy(
                currentEditedMs = newEditedMs,
                selectedClipId = activeClipId ?: it.selectedClipId
            )
        }
    }

    private fun startPlayback() {
        val state = _uiState.value
        if (state.videoUri == null || state.clips.isEmpty()) return

        val total = state.totalEditedContentMs
        val fromStart = state.currentEditedMs >= total - 100L
        val startMs = if (fromStart) 0L else state.currentEditedMs
        adTriggerConsumedForRun = startMs > state.adBanner.triggerAtMs
        resetFiredSfxAfter(startMs)

        _uiState.update {
            it.copy(
                isPlaying = true,
                currentEditedMs = startMs,
                seekRequestNonce = if (fromStart) it.seekRequestNonce + 1L else it.seekRequestNonce,
                isAdPauseActive = false
            )
        }
    }

    /**
     * Only runs while the Mid-Roll Ad Banner freeze-frame pause is active!
     * Main ExoPlayer is paused during this window, and automatically resumes when countdown hits 0.
     */
    private fun launchAdCountdownLoop() {
        adCountdownJob?.cancel()
        adCountdownJob = viewModelScope.launch {
            var lastWallMs = System.currentTimeMillis()
            while (isActive) {
                delay(50L)
                val now = System.currentTimeMillis()
                val dt = (now - lastWallMs).coerceIn(1L, 120L)
                lastWallMs = now

                val cur = _uiState.value
                if (!cur.isPlaying || !cur.isAdPauseActive) break

                val nextRemaining = (cur.adRemainingMs - dt).coerceAtLeast(0L)
                val nextElapsed = cur.adElapsedMs + dt
                if (nextRemaining <= 0L) {
                    ttsSynthesizer.stop()
                    _uiState.update {
                        it.copy(
                            isAdPauseActive = false,
                            adRemainingMs = 0L,
                            adElapsedMs = 0L
                        )
                    }
                    break
                } else {
                    _uiState.update {
                        it.copy(
                            adRemainingMs = nextRemaining,
                            adElapsedMs = nextElapsed
                        )
                    }
                }
            }
        }
    }

    fun stopPlayback() {
        adCountdownJob?.cancel()
        ttsSynthesizer.stop()
        _uiState.update {
            it.copy(
                isPlaying = false,
                isAdPauseActive = false,
                adRemainingMs = 0L,
                adElapsedMs = 0L
            )
        }
    }

    private fun resetFiredSfxAfter(ms: Long) {
        val curEvents = _uiState.value.sfxEvents
        firedSfxIds.clear()
        curEvents.filter { it.editedMs < ms - 30L }.forEach { firedSfxIds.add(it.id) }
    }

    // ---- Export Full Video & Export All Clips ----

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
                        statusText = "Подготовка экспорта 9:16..."
                    )
                )
            }

            var adConfig = state.adBanner
            if (adConfig.enabled && adConfig.mediaUri != null && adConfig.useTts && adConfig.ttsText.isNotBlank()) {
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
                    sfxEvents = state.sfxEvents,
                    captions = state.captions,
                    captionsEnabled = state.captionsEnabled,
                    subtitlePosition = state.subtitlePosition,
                    framing = state.framing,
                    meme = state.meme,
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
                    sfxEvents = state.sfxEvents,
                    captions = state.captions,
                    captionsEnabled = state.captionsEnabled,
                    subtitlePosition = state.subtitlePosition,
                    framing = state.framing,
                    meme = state.meme,
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
        _uiState.update { it.copy(exportState = ExportState.Idle, isTtsSynthesizing = false) }
    }

    fun dismissExportDialog() {
        _uiState.update { it.copy(exportState = ExportState.Idle) }
    }

    private suspend fun probeVideoDurationMs(uri: Uri): Long = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(appContext, uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 4000L
        } catch (_: Throwable) {
            4000L
        } finally {
            runCatching { retriever.release() }
        }
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
        analyzeJob?.cancel()
        faceTrackJob?.cancel()
        transcribeJob?.cancel()
        exportJob?.cancel()
        speechCaptioner.release()
        ttsSynthesizer.release()
    }
}
