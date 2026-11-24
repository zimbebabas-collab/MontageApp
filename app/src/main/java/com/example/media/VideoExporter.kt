package com.example.media

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import com.example.R
import com.example.model.AdBannerConfig
import com.example.model.AdMediaType
import com.example.model.AudioPeak
import com.example.model.CaptionChunk
import com.example.model.ExportedVideo
import com.example.model.FramingConfig
import com.example.model.MemeConfig
import com.example.model.SfxEvent
import com.example.model.SubtitlePosition
import com.example.model.VideoClip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.coroutineContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

object VideoExporter {

    private const val OUT_WIDTH = 720
    private const val OUT_HEIGHT = 1280
    private const val OUT_FPS = 24
    private const val VIDEO_BITRATE = 4_500_000
    private const val AUDIO_SAMPLE_RATE = 44100
    private const val AUDIO_CHANNELS = 2
    private const val AUDIO_BITRATE = 128_000

    suspend fun exportFullVideo(
        context: Context,
        sourceUri: Uri,
        clips: List<VideoClip>,
        peaks: List<AudioPeak>,
        sfxEvents: List<SfxEvent>,
        captions: List<CaptionChunk>,
        captionsEnabled: Boolean,
        subtitlePosition: SubtitlePosition,
        framing: FramingConfig,
        meme: MemeConfig,
        adBanner: AdBannerConfig,
        onProgress: (Float, String) -> Unit
    ): ExportedVideo = withContext(Dispatchers.IO) {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val fileName = "ReelsCut_Full_$timestamp.mp4"
        renderSequenceToMp4(
            context = context,
            sourceUri = sourceUri,
            clips = clips,
            peaks = peaks,
            sfxEvents = sfxEvents,
            captions = if (captionsEnabled) captions else emptyList(),
            subtitlePosition = subtitlePosition,
            framing = framing,
            meme = meme,
            includeReactionClip = meme.reactionClipUri != null && meme.reactionClipDurationMs > 0L,
            adBanner = adBanner,
            includeAdBanner = adBanner.enabled && adBanner.mediaUri != null,
            outputDisplayName = fileName,
            onProgress = onProgress
        )
    }

    suspend fun exportAllClips(
        context: Context,
        sourceUri: Uri,
        clips: List<VideoClip>,
        peaks: List<AudioPeak>,
        sfxEvents: List<SfxEvent>,
        captions: List<CaptionChunk>,
        captionsEnabled: Boolean,
        subtitlePosition: SubtitlePosition,
        framing: FramingConfig,
        meme: MemeConfig,
        onProgress: (Float, String, Int, Int) -> Unit
    ): List<ExportedVideo> = withContext(Dispatchers.IO) {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val results = mutableListOf<ExportedVideo>()
        var timelineOffsetMs = 0L

        clips.forEachIndexed { idx, clip ->
            coroutineContext.ensureActive()
            val partNum = idx + 1
            val clipStartEdited = timelineOffsetMs
            val clipEndEdited = timelineOffsetMs + clip.durationMs
            timelineOffsetMs = clipEndEdited

            val shiftedCaptions = if (captionsEnabled) {
                captions
                    .filter { it.endMs > clipStartEdited && it.startMs < clipEndEdited }
                    .map {
                        it.copy(
                            startMs = (it.startMs - clipStartEdited).coerceAtLeast(0L),
                            endMs = (it.endMs - clipStartEdited).coerceAtMost(clip.durationMs)
                        )
                    }
            } else {
                emptyList()
            }

            val shiftedSfx = sfxEvents
                .filter { it.editedMs in clipStartEdited until clipEndEdited }
                .map { it.copy(editedMs = (it.editedMs - clipStartEdited).coerceAtLeast(0L)) }

            val exported = renderSequenceToMp4(
                context = context,
                sourceUri = sourceUri,
                clips = listOf(clip),
                peaks = peaks,
                sfxEvents = shiftedSfx,
                captions = shiftedCaptions,
                subtitlePosition = subtitlePosition,
                framing = framing,
                meme = meme,
                includeReactionClip = false,
                adBanner = AdBannerConfig(enabled = false),
                includeAdBanner = false,
                outputDisplayName = "ReelsCut_Part${partNum}_$timestamp.mp4",
                onProgress = { subProgress, _ ->
                    val totalProgress = (idx + subProgress) / clips.size.coerceAtLeast(1)
                    onProgress(
                        totalProgress.coerceIn(0f, 1f),
                        "Рендер части $partNum из ${clips.size}...",
                        partNum,
                        clips.size
                    )
                }
            )
            results.add(exported)
        }
        results
    }

    private suspend fun renderSequenceToMp4(
        context: Context,
        sourceUri: Uri,
        clips: List<VideoClip>,
        peaks: List<AudioPeak>,
        sfxEvents: List<SfxEvent>,
        captions: List<CaptionChunk>,
        subtitlePosition: SubtitlePosition,
        framing: FramingConfig,
        meme: MemeConfig,
        includeReactionClip: Boolean,
        adBanner: AdBannerConfig,
        includeAdBanner: Boolean,
        outputDisplayName: String,
        onProgress: (Float, String) -> Unit
    ): ExportedVideo {
        require(clips.isNotEmpty()) { "Нет клипов для экспорта" }

        val mainContentMs = clips.sumOf { it.durationMs }.coerceAtLeast(300L)
        val reactionDurationMs = if (includeReactionClip && meme.reactionClipUri != null) {
            meme.reactionClipDurationMs.coerceAtLeast(0L)
        } else {
            0L
        }
        val totalContentMs = mainContentMs + reactionDurationMs
        val adDurationMs = if (includeAdBanner) (adBanner.durationSec.coerceIn(3, 15) * 1000L) else 0L
        val adTriggerMs = if (includeAdBanner) {
            adBanner.triggerAtMs.coerceIn(200L, max(200L, totalContentMs - 200L))
        } else {
            -1L
        }
        val totalOutputMs = totalContentMs + adDurationMs

        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val tempFile = File(exportDir, outputDisplayName)
        if (tempFile.exists()) tempFile.delete()

        onProgress(0.04f, "Сведение аудио и звуковых эффектов...")
        val baseMixedPcm = buildMixedAudioPcm(
            context = context,
            sourceUri = sourceUri,
            clips = clips,
            reactionUri = if (reactionDurationMs > 0L) meme.reactionClipUri else null,
            reactionDurationMs = reactionDurationMs,
            includeAd = includeAdBanner,
            adTriggerMs = adTriggerMs,
            adDurationMs = adDurationMs,
            adBanner = adBanner
        )
        val mixedPcm = SoundEffectsEngine.mixSfxIntoPcm(
            pcmBytes = baseMixedPcm,
            events = sfxEvents,
            volume = framing.sfxVolume,
            includeAd = includeAdBanner,
            adTriggerMs = adTriggerMs,
            adDurationMs = adDurationMs
        )

        val adImageBitmap = if (includeAdBanner &&
            adBanner.mediaType == AdMediaType.IMAGE &&
            adBanner.mediaUri != null
        ) {
            loadBitmapFromUri(context, adBanner.mediaUri, OUT_WIDTH, OUT_HEIGHT)
        } else {
            null
        }

        val typeface = runCatching {
            ResourcesCompat.getFont(context, R.font.montserrat)
        }.getOrNull() ?: Typeface.DEFAULT_BOLD

        val mainRetriever = MediaMetadataRetriever()
        var reactionRetriever: MediaMetadataRetriever? = null
        var adVideoRetriever: MediaMetadataRetriever? = null
        var videoEncoder: MediaCodec? = null
        var audioEncoder: MediaCodec? = null
        var muxer: MediaMuxer? = null
        var muxerStarted = false

        try {
            mainRetriever.setDataSource(context, sourceUri)
            if (reactionDurationMs > 0L && meme.reactionClipUri != null) {
                reactionRetriever = MediaMetadataRetriever().apply {
                    setDataSource(context, meme.reactionClipUri)
                }
            }
            if (includeAdBanner && adBanner.mediaType == AdMediaType.VIDEO && adBanner.mediaUri != null) {
                adVideoRetriever = runCatching {
                    MediaMetadataRetriever().apply {
                        setDataSource(context, adBanner.mediaUri)
                    }
                }.getOrNull()
            }

            val videoFormat = MediaFormat.createVideoFormat(
                MediaFormat.MIMETYPE_VIDEO_AVC,
                OUT_WIDTH,
                OUT_HEIGHT
            ).apply {
                setInteger(
                    MediaFormat.KEY_COLOR_FORMAT,
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
                )
                setInteger(MediaFormat.KEY_BIT_RATE, VIDEO_BITRATE)
                setInteger(MediaFormat.KEY_FRAME_RATE, OUT_FPS)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            }

            videoEncoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            videoEncoder.configure(videoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            val inputSurface = videoEncoder.createInputSurface()
            videoEncoder.start()

            val hasAudio = mixedPcm.isNotEmpty()
            if (hasAudio) {
                val audioFormat = MediaFormat.createAudioFormat(
                    MediaFormat.MIMETYPE_AUDIO_AAC,
                    AUDIO_SAMPLE_RATE,
                    AUDIO_CHANNELS
                ).apply {
                    setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                    setInteger(MediaFormat.KEY_BIT_RATE, AUDIO_BITRATE)
                    setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
                }
                audioEncoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
                audioEncoder.configure(audioFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                audioEncoder.start()
            }

            muxer = MediaMuxer(tempFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            var videoTrackIndex = -1
            var audioTrackIndex = -1
            val pendingSamples = mutableListOf<Triple<Boolean, ByteArray, MediaCodec.BufferInfo>>()

            fun tryStartMuxer() {
                if (muxerStarted) return
                val videoReady = videoTrackIndex >= 0
                val audioReady = !hasAudio || audioTrackIndex >= 0
                if (videoReady && audioReady) {
                    muxer?.start()
                    muxerStarted = true
                    for ((isVideo, bytes, info) in pendingSamples) {
                        val track = if (isVideo) videoTrackIndex else audioTrackIndex
                        if (track >= 0) {
                            muxer?.writeSampleData(track, ByteBuffer.wrap(bytes), info)
                        }
                    }
                    pendingSamples.clear()
                }
            }

            val videoBufInfo = MediaCodec.BufferInfo()
            val audioBufInfo = MediaCodec.BufferInfo()

            fun drainVideoEncoder(endOfStream: Boolean) {
                val enc = videoEncoder ?: return
                if (endOfStream) enc.signalEndOfInputStream()
                while (true) {
                    val outIdx = enc.dequeueOutputBuffer(videoBufInfo, if (endOfStream) 10_000L else 0L)
                    when {
                        outIdx == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                            if (!endOfStream) break
                        }
                        outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            if (videoTrackIndex < 0) {
                                videoTrackIndex = muxer?.addTrack(enc.outputFormat) ?: -1
                                tryStartMuxer()
                            }
                        }
                        outIdx >= 0 -> {
                            val encoded = enc.getOutputBuffer(outIdx)
                            if (encoded != null && videoBufInfo.size > 0 &&
                                (videoBufInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0
                            ) {
                                encoded.position(videoBufInfo.offset)
                                encoded.limit(videoBufInfo.offset + videoBufInfo.size)
                                if (muxerStarted && videoTrackIndex >= 0) {
                                    muxer?.writeSampleData(videoTrackIndex, encoded, videoBufInfo)
                                } else {
                                    val copy = ByteArray(videoBufInfo.size)
                                    encoded.get(copy)
                                    val cloneInfo = MediaCodec.BufferInfo().apply {
                                        set(0, copy.size, videoBufInfo.presentationTimeUs, videoBufInfo.flags)
                                    }
                                    pendingSamples.add(Triple(true, copy, cloneInfo))
                                }
                            }
                            enc.releaseOutputBuffer(outIdx, false)
                            if ((videoBufInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) break
                        }
                    }
                }
            }

            var audioReadPos = 0
            var audioEosQueued = false
            var audioPtsSamples = 0L

            fun pumpAudioEncoder(drainToEnd: Boolean) {
                val enc = audioEncoder ?: return
                var guard = 0
                while (guard < (if (drainToEnd) 5000 else 6)) {
                    guard++
                    if (!audioEosQueued) {
                        val inIdx = enc.dequeueInputBuffer(0L)
                        if (inIdx >= 0) {
                            val inBuf = enc.getInputBuffer(inIdx)
                            if (inBuf != null) {
                                inBuf.clear()
                                val remaining = mixedPcm.size - audioReadPos
                                if (remaining <= 0) {
                                    enc.queueInputBuffer(
                                        inIdx, 0, 0,
                                        (audioPtsSamples * 1_000_000L) / AUDIO_SAMPLE_RATE,
                                        MediaCodec.BUFFER_FLAG_END_OF_STREAM
                                    )
                                    audioEosQueued = true
                                } else {
                                    val toWrite = min(remaining, inBuf.capacity())
                                    val aligned = (toWrite / 4) * 4
                                    if (aligned > 0) {
                                        inBuf.put(mixedPcm, audioReadPos, aligned)
                                        val ptsUs = (audioPtsSamples * 1_000_000L) / AUDIO_SAMPLE_RATE
                                        enc.queueInputBuffer(inIdx, 0, aligned, ptsUs, 0)
                                        audioReadPos += aligned
                                        audioPtsSamples += aligned / (AUDIO_CHANNELS * 2)
                                    }
                                }
                            }
                        }
                    }

                    val outIdx = enc.dequeueOutputBuffer(audioBufInfo, if (drainToEnd) 5_000L else 0L)
                    when {
                        outIdx == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                            if (!drainToEnd || (audioEosQueued && guard > 250)) break
                        }
                        outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            if (audioTrackIndex < 0) {
                                audioTrackIndex = muxer?.addTrack(enc.outputFormat) ?: -1
                                tryStartMuxer()
                            }
                        }
                        outIdx >= 0 -> {
                            val encoded = enc.getOutputBuffer(outIdx)
                            if (encoded != null && audioBufInfo.size > 0 &&
                                (audioBufInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0
                            ) {
                                encoded.position(audioBufInfo.offset)
                                encoded.limit(audioBufInfo.offset + audioBufInfo.size)
                                if (muxerStarted && audioTrackIndex >= 0) {
                                    muxer?.writeSampleData(audioTrackIndex, encoded, audioBufInfo)
                                } else {
                                    val copy = ByteArray(audioBufInfo.size)
                                    encoded.get(copy)
                                    val cloneInfo = MediaCodec.BufferInfo().apply {
                                        set(0, copy.size, audioBufInfo.presentationTimeUs, audioBufInfo.flags)
                                    }
                                    pendingSamples.add(Triple(false, copy, cloneInfo))
                                }
                            }
                            enc.releaseOutputBuffer(outIdx, false)
                            if ((audioBufInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) break
                        }
                    }
                }
            }

            // Paints for vivid saturated rendering
            val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            val adBitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

            // Saturated multi-colored subtitle fill & crisp dark outline paints
            val subtitleStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.typeface = Typeface.create(typeface, Typeface.BOLD)
                textSize = 80f
                textAlign = Paint.Align.CENTER
                style = Paint.Style.STROKE
                strokeWidth = 14f
                strokeJoin = Paint.Join.ROUND
                color = Color.argb(240, 10, 10, 16)
            }
            val subtitleFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.typeface = Typeface.create(typeface, Typeface.BOLD)
                textSize = 80f
                textAlign = Paint.Align.CENTER
                style = Paint.Style.FILL
            }

            // Neon peak flash paint
            val peakFlashPaint = Paint(Paint.ANTI_ALIAS_FLAG)

            // Top Black Meme Bar paints
            val memeBarBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                style = Paint.Style.FILL
            }
            val memeBarTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.typeface = Typeface.create(typeface, Typeface.BOLD)
                color = Color.WHITE
                textSize = 42f
                textAlign = Paint.Align.CENTER
            }

            // Subtle Vignette paint
            val vignettePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(
                    OUT_WIDTH / 2f,
                    OUT_HEIGHT / 2f,
                    OUT_HEIGHT * 0.65f,
                    intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT, Color.argb(128, 0, 0, 0)),
                    floatArrayOf(0f, 0.58f, 1f),
                    Shader.TileMode.CLAMP
                )
            }

            // Mid-Roll Ad Banner paints (exact rgba(0,0,0,0.45) dim backdrop)
            val dimPaint = Paint().apply {
                color = Color.argb(115, 0, 0, 0) // rgba(0, 0, 0, 0.45)
            }
            val adBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(200, 255, 255, 255)
                style = Paint.Style.STROKE
                strokeWidth = 4f
            }
            val badgeBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(220, 11, 11, 15)
                style = Paint.Style.FILL
            }
            val badgeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.typeface = Typeface.create(typeface, Typeface.BOLD)
                color = Color.WHITE
                textSize = 24f
                textAlign = Paint.Align.CENTER
            }

            val totalFrames = max(1, ((totalOutputMs * OUT_FPS) / 1000L).toInt())
            var cachedFrame: Bitmap? = null
            var lastFetchedKey = ""
            var cachedAdVideoFrame: Bitmap? = null
            var lastAdFrameKey = -1L

            onProgress(0.08f, "Рендер 9:16 (трекинг лица, эффекты, субтитры)...")

            for (frameIndex in 0 until totalFrames) {
                coroutineContext.ensureActive()
                val outMs = (frameIndex * 1000L) / OUT_FPS
                val inAdWindow = includeAdBanner &&
                    adTriggerMs >= 0L &&
                    outMs >= adTriggerMs &&
                    outMs < (adTriggerMs + adDurationMs)

                val editedContentMs = when {
                    !includeAdBanner || adTriggerMs < 0L -> outMs
                    outMs < adTriggerMs -> outMs
                    inAdWindow -> adTriggerMs // Freeze-frame on exact trigger timestamp!
                    else -> (outMs - adDurationMs).coerceAtLeast(0L)
                }

                val pos = SubtitleEngine.mapEditedMsToTimeline(
                    editedMs = editedContentMs,
                    clips = clips,
                    reactionDurationMs = reactionDurationMs
                )

                val frameKey: String
                val fetchedFrame: Bitmap?
                when (pos) {
                    is SubtitleEngine.TimelinePosition.MainClip -> {
                        val quantizedMs = (pos.sourceMs / 42L) * 42L
                        frameKey = "main_$quantizedMs"
                        fetchedFrame = if (frameKey != lastFetchedKey) {
                            fetchFrameSafe(mainRetriever, pos.sourceMs)
                        } else null
                    }
                    is SubtitleEngine.TimelinePosition.ReactionClip -> {
                        val quantizedMs = (pos.reactionMs / 42L) * 42L
                        frameKey = "react_$quantizedMs"
                        fetchedFrame = if (frameKey != lastFetchedKey && reactionRetriever != null) {
                            fetchFrameSafe(reactionRetriever, pos.reactionMs)
                        } else null
                    }
                    null -> {
                        frameKey = lastFetchedKey
                        fetchedFrame = null
                    }
                }

                if (fetchedFrame != null) {
                    cachedFrame?.recycle()
                    cachedFrame = fetchedFrame
                    lastFetchedKey = frameKey
                }

                // Fetch live frame of Video Ad Banner if playing in the mid-roll pause window
                if (inAdWindow && adVideoRetriever != null) {
                    val adElapsed = (outMs - adTriggerMs).coerceAtLeast(0L)
                    val adVideoLen = adBanner.adVideoDurationMs.coerceAtLeast(1000L)
                    val adPosMs = adElapsed % adVideoLen
                    val quantizedAdMs = (adPosMs / 42L) * 42L
                    if (quantizedAdMs != lastAdFrameKey) {
                        val fetchedAd = fetchFrameSafe(adVideoRetriever, adPosMs)
                        if (fetchedAd != null) {
                            cachedAdVideoFrame?.recycle()
                            cachedAdVideoFrame = fetchedAd
                            lastAdFrameKey = quantizedAdMs
                        }
                    }
                }

                val canvas = inputSurface.lockCanvas(null)
                try {
                    canvas.drawColor(Color.parseColor("#0B0B0F"))

                    val cameraFrame = when {
                        inAdWindow -> SubtitleEngine.CameraFrameState(
                            scale = 1f,
                            focalX = framing.focalX,
                            focalY = framing.focalY,
                            isCloseUp = false,
                            offsetXFraction = 0f,
                            offsetYFraction = 0f,
                            peakFlashAlpha = 0f,
                            saturationBoost = 1f,
                            peakPulse = 0f
                        )
                        pos is SubtitleEngine.TimelinePosition.MainClip -> SubtitleEngine.computeCameraFrame(
                            clip = pos.clip,
                            sourceMs = pos.sourceMs,
                            peaks = peaks,
                            framing = framing
                        )
                        else -> SubtitleEngine.CameraFrameState(
                            scale = 1f,
                            focalX = 0.5f,
                            focalY = 0.5f,
                            isCloseUp = false,
                            offsetXFraction = 0f,
                            offsetYFraction = 0f,
                            peakFlashAlpha = 0f,
                            saturationBoost = 1f,
                            peakPulse = 0f
                        )
                    }

                    // 1. Draw vertical 9:16 video frame with face-locked focal zoom + saturation boost on peaks
                    cachedFrame?.let { bmp ->
                        bitmapPaint.colorFilter = if (cameraFrame.saturationBoost > 1.03f) {
                            ColorMatrixColorFilter(
                                ColorMatrix().apply { setSaturation(cameraFrame.saturationBoost) }
                            )
                        } else {
                            null
                        }
                        drawCenterCropWithFocalZoom(canvas, bmp, cameraFrame, bitmapPaint)
                    }

                    // 2. Saturated vivid neon flash & edge energy glow on vocal peaks
                    if (!inAdWindow && cameraFrame.peakFlashAlpha > 0.01f &&
                        pos is SubtitleEngine.TimelinePosition.MainClip
                    ) {
                        val flashA = (cameraFrame.peakFlashAlpha * 255f).roundToInt().coerceIn(0, 120)
                        peakFlashPaint.shader = RadialGradient(
                            OUT_WIDTH / 2f,
                            OUT_HEIGHT / 2f,
                            OUT_HEIGHT * 0.62f,
                            intArrayOf(
                                Color.argb((flashA * 0.45f).roundToInt(), 255, 255, 255),
                                Color.argb((flashA * 0.7f).roundToInt(), 0, 240, 255),
                                Color.argb(flashA, 255, 46, 147)
                            ),
                            floatArrayOf(0f, 0.55f, 1f),
                            Shader.TileMode.CLAMP
                        )
                        canvas.drawRect(0f, 0f, OUT_WIDTH.toFloat(), OUT_HEIGHT.toFloat(), peakFlashPaint)
                    }

                    // 3. Optional subtle vignette
                    if (framing.vignette && pos is SubtitleEngine.TimelinePosition.MainClip) {
                        canvas.drawRect(0f, 0f, OUT_WIDTH.toFloat(), OUT_HEIGHT.toFloat(), vignettePaint)
                    }

                    // 4. Top black meme caption bar (when enabled)
                    if (meme.topBarEnabled && meme.topBarText.isNotBlank() &&
                        pos is SubtitleEngine.TimelinePosition.MainClip
                    ) {
                        drawTopMemeBar(canvas, meme.topBarText.uppercase(), memeBarBgPaint, memeBarTextPaint)
                    }

                    // 5. Saturated multi-colored subtitle (BOTTOM or CENTER) with neon glow + crisp outline
                    if (!inAdWindow && pos is SubtitleEngine.TimelinePosition.MainClip) {
                        val activeCaption = SubtitleEngine.activeCaptionAt(editedContentMs, captions)
                        if (activeCaption != null) {
                            drawVibrantSubtitle(
                                canvas = canvas,
                                caption = activeCaption,
                                editedMs = editedContentMs,
                                position = subtitlePosition,
                                strokePaint = subtitleStrokePaint,
                                fillPaint = subtitleFillPaint
                            )
                        }
                    }

                    // 6. Dead-center mid-roll Ad Banner (IMAGE or VIDEO, 25%..35% area) over rgba(0,0,0,0.45) dim
                    val activeAdBitmap = if (adBanner.mediaType == AdMediaType.VIDEO) {
                        cachedAdVideoFrame
                    } else {
                        adImageBitmap
                    }
                    if (inAdWindow && activeAdBitmap != null) {
                        val remainingSec = ceilSeconds(adTriggerMs + adDurationMs - outMs)
                        drawMidRollAdBanner(
                            canvas = canvas,
                            adBitmap = activeAdBitmap,
                            areaPercent = adBanner.areaPercent.coerceIn(25, 35),
                            remainingSec = remainingSec,
                            dimPaint = dimPaint,
                            bitmapPaint = adBitmapPaint,
                            borderPaint = adBorderPaint,
                            badgeBgPaint = badgeBgPaint,
                            badgeTextPaint = badgeTextPaint
                        )
                    }
                } finally {
                    inputSurface.unlockCanvasAndPost(canvas)
                }

                drainVideoEncoder(endOfStream = false)
                if (hasAudio) {
                    pumpAudioEncoder(drainToEnd = false)
                }

                if (frameIndex % 4 == 0 || frameIndex == totalFrames - 1) {
                    val p = 0.08f + 0.84f * (frameIndex.toFloat() / totalFrames.toFloat())
                    onProgress(p.coerceIn(0f, 0.94f), "Рендер видео: ${(p * 100).roundToInt()}%")
                }
            }

            cachedFrame?.recycle()
            cachedAdVideoFrame?.recycle()
            drainVideoEncoder(endOfStream = true)
            if (hasAudio) {
                pumpAudioEncoder(drainToEnd = true)
            }
            tryStartMuxer()

            onProgress(0.96f, "Сохранение в галерею...")
        } finally {
            runCatching { mainRetriever.release() }
            runCatching { reactionRetriever?.release() }
            runCatching { adVideoRetriever?.release() }
            runCatching { adImageBitmap?.recycle() }
            runCatching {
                videoEncoder?.stop()
                videoEncoder?.release()
            }
            runCatching {
                audioEncoder?.stop()
                audioEncoder?.release()
            }
            runCatching {
                if (muxerStarted) muxer?.stop()
                muxer?.release()
            }
        }

        val savedUri = saveMp4ToGallery(context, tempFile, outputDisplayName)
        onProgress(1f, "Готово!")

        return ExportedVideo(
            displayName = outputDisplayName,
            contentUri = savedUri ?: runCatching {
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    tempFile
                )
            }.getOrNull(),
            filePath = tempFile.absolutePath,
            durationMs = totalOutputMs
        )
    }

    private fun fetchFrameSafe(retriever: MediaMetadataRetriever, sourceMs: Long): Bitmap? {
        val us = sourceMs.coerceAtLeast(0L) * 1000L
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                retriever.getScaledFrameAtTime(
                    us,
                    MediaMetadataRetriever.OPTION_CLOSEST,
                    OUT_WIDTH,
                    OUT_HEIGHT
                ) ?: retriever.getScaledFrameAtTime(
                    us,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                    OUT_WIDTH,
                    OUT_HEIGHT
                )
            } else {
                retriever.getFrameAtTime(us, MediaMetadataRetriever.OPTION_CLOSEST)
                    ?: retriever.getFrameAtTime(us, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            }
        }.getOrNull()
    }

    /**
     * Draws center-cropped 9:16 video frame and zooms smoothly around (focalX, focalY),
     * clamping translation so no black bars ever appear around the edges.
     */
    private fun drawCenterCropWithFocalZoom(
        canvas: Canvas,
        bitmap: Bitmap,
        cam: SubtitleEngine.CameraFrameState,
        paint: Paint
    ) {
        val bw = bitmap.width.toFloat().coerceAtLeast(1f)
        val bh = bitmap.height.toFloat().coerceAtLeast(1f)
        val baseScale = max(OUT_WIDTH / bw, OUT_HEIGHT / bh)
        val finalScale = baseScale * cam.scale

        val scaledW = bw * finalScale
        val scaledH = bh * finalScale

        // Center-crop base position, then lock zoom accurately onto detected face (focalX, focalY) + micro-shake
        val pivotCanvasX = OUT_WIDTH * cam.focalX
        val pivotCanvasY = OUT_HEIGHT * cam.focalY
        val baseLeft = (OUT_WIDTH - bw * baseScale) / 2f
        val baseTop = (OUT_HEIGHT - bh * baseScale) / 2f

        val shakeX = cam.offsetXFraction * OUT_WIDTH
        val shakeY = cam.offsetYFraction * OUT_HEIGHT
        val rawLeft = pivotCanvasX - (pivotCanvasX - baseLeft) * cam.scale + shakeX
        val rawTop = pivotCanvasY - (pivotCanvasY - baseTop) * cam.scale + shakeY

        val minLeft = OUT_WIDTH - scaledW
        val minTop = OUT_HEIGHT - scaledH
        val clampedLeft = rawLeft.coerceIn(min(0f, minLeft), max(0f, minLeft))
        val clampedTop = rawTop.coerceIn(min(0f, minTop), max(0f, minTop))

        val matrix = Matrix().apply {
            postScale(finalScale, finalScale)
            postTranslate(clampedLeft, clampedTop)
        }
        canvas.drawBitmap(bitmap, matrix, paint)
    }

    private fun drawTopMemeBar(
        canvas: Canvas,
        text: String,
        bgPaint: Paint,
        textPaint: Paint
    ) {
        val barHeight = OUT_HEIGHT * 0.145f
        canvas.drawRect(0f, 0f, OUT_WIDTH.toFloat(), barHeight, bgPaint)

        val maxW = OUT_WIDTH * 0.88f
        val lines = wrapLines(text, textPaint, maxW, maxLines = 2)
        val lineHeight = 50f
        val centerY = barHeight * 0.56f
        val startY = centerY - ((lines.size - 1) * lineHeight) / 2f + 12f
        lines.forEachIndexed { idx, line ->
            canvas.drawText(line, OUT_WIDTH / 2f, startY + idx * lineHeight, textPaint)
        }
    }

    /**
     * Saturated multi-colored word subtitle positioned cleanly at BOTTOM (78% Y) or CENTER (52% Y)
     * with dark outline, vivid colored neon glow, and snappy 120ms pop-in animation.
     */
    private fun drawVibrantSubtitle(
        canvas: Canvas,
        caption: CaptionChunk,
        editedMs: Long,
        position: SubtitlePosition,
        strokePaint: Paint,
        fillPaint: Paint
    ) {
        val popScale = SubtitleEngine.computePopScale(editedMs, caption)
        val centerX = OUT_WIDTH * 0.50f
        val centerY = when (position) {
            SubtitlePosition.BOTTOM -> OUT_HEIGHT * 0.78f
            SubtitlePosition.CENTER -> OUT_HEIGHT * 0.52f
        }

        fillPaint.color = caption.color.argb
        fillPaint.setShadowLayer(18f, 0f, 5f, caption.color.glowArgb)

        var textSize = if (caption.isActionNote) 64f else 84f
        fillPaint.textSize = textSize
        val maxW = OUT_WIDTH * 0.88f
        while (fillPaint.measureText(caption.text) > maxW && textSize > 38f) {
            textSize -= 4f
            fillPaint.textSize = textSize
        }
        strokePaint.textSize = textSize
        strokePaint.strokeWidth = (textSize * 0.17f).coerceIn(8f, 15f)

        val fm = fillPaint.fontMetrics
        val baselineOffset = -(fm.ascent + fm.descent) / 2f

        canvas.save()
        canvas.translate(centerX, centerY)
        canvas.scale(popScale, popScale)
        canvas.drawText(caption.text, 0f, baselineOffset, strokePaint)
        canvas.drawText(caption.text, 0f, baselineOffset, fillPaint)
        canvas.restore()
    }

    private fun wrapLines(text: String, paint: Paint, maxWidth: Float, maxLines: Int): List<String> {
        if (paint.measureText(text) <= maxWidth) return listOf(text)
        val words = text.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.size <= 1) return listOf(text)
        val lines = mutableListOf<String>()
        var current = words[0]
        for (i in 1 until words.size) {
            val candidate = "$current ${words[i]}"
            if (paint.measureText(candidate) <= maxWidth || lines.size == maxLines - 1) {
                current = candidate
            } else {
                lines.add(current)
                current = words[i]
            }
        }
        lines.add(current)
        return lines.take(maxLines)
    }

    private fun drawMidRollAdBanner(
        canvas: Canvas,
        adBitmap: Bitmap,
        areaPercent: Int,
        remainingSec: Int,
        dimPaint: Paint,
        bitmapPaint: Paint,
        borderPaint: Paint,
        badgeBgPaint: Paint,
        badgeTextPaint: Paint
    ) {
        // Subtle rgba(0, 0, 0, 0.45) dim backdrop
        canvas.drawRect(0f, 0f, OUT_WIDTH.toFloat(), OUT_HEIGHT.toFloat(), dimPaint)

        // Centered box occupying strictly 25%..35% of screen area
        val targetArea = OUT_WIDTH.toFloat() * OUT_HEIGHT.toFloat() * (areaPercent / 100f)
        val imgAspect = (adBitmap.width.toFloat() / adBitmap.height.toFloat().coerceAtLeast(1f))
            .coerceIn(0.65f, 1.85f)
        var boxWidth = sqrt(targetArea * imgAspect)
        var boxHeight = boxWidth / imgAspect

        val maxW = OUT_WIDTH * 0.86f
        val maxH = OUT_HEIGHT * 0.58f
        if (boxWidth > maxW) {
            boxWidth = maxW
            boxHeight = (targetArea / boxWidth).coerceAtMost(maxH)
        }
        if (boxHeight > maxH) {
            boxHeight = maxH
            boxWidth = (targetArea / boxHeight).coerceAtMost(maxW)
        }

        val left = (OUT_WIDTH - boxWidth) / 2f
        val top = (OUT_HEIGHT - boxHeight) / 2f
        val rect = RectF(left, top, left + boxWidth, top + boxHeight)

        val saveCount = canvas.save()
        val path = Path().apply {
            addRoundRect(rect, 26f, 26f, Path.Direction.CW)
        }
        canvas.clipPath(path)

        val scale = max(rect.width() / adBitmap.width, rect.height() / adBitmap.height)
        val dx = rect.centerX() - (adBitmap.width * scale) / 2f
        val dy = rect.centerY() - (adBitmap.height * scale) / 2f
        val m = Matrix().apply {
            postScale(scale, scale)
            postTranslate(dx, dy)
        }
        canvas.drawBitmap(adBitmap, m, bitmapPaint)
        canvas.restoreToCount(saveCount)

        canvas.drawRoundRect(rect, 26f, 26f, borderPaint)

        // Clean pill countdown badge above banner
        val badgeW = 220f
        val badgeH = 44f
        val badgeRect = RectF(
            OUT_WIDTH / 2f - badgeW / 2f,
            top - badgeH - 16f,
            OUT_WIDTH / 2f + badgeW / 2f,
            top - 16f
        )
        canvas.drawRoundRect(badgeRect, 22f, 22f, badgeBgPaint)
        canvas.drawText(
            "РЕКЛАМА • ${remainingSec}с",
            badgeRect.centerX(),
            badgeRect.centerY() + 8f,
            badgeTextPaint
        )
    }

    private fun ceilSeconds(ms: Long): Int = ((ms.coerceAtLeast(0L) + 999L) / 1000L).toInt().coerceAtLeast(1)

    private fun loadBitmapFromUri(context: Context, uri: Uri, reqW: Int, reqH: Int): Bitmap? {
        return runCatching {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, opts)
            }
            var inSample = 1
            while (opts.outWidth / inSample > reqW * 2 || opts.outHeight / inSample > reqH * 2) {
                inSample *= 2
            }
            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = inSample }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, decodeOpts)
            }
        }.getOrNull()
    }

    private fun buildMixedAudioPcm(
        context: Context,
        sourceUri: Uri,
        clips: List<VideoClip>,
        reactionUri: Uri?,
        reactionDurationMs: Long,
        includeAd: Boolean,
        adTriggerMs: Long,
        adDurationMs: Long,
        adBanner: AdBannerConfig
    ): ByteArray {
        val output = ByteArrayOutputStream()
        var editedCursorMs = 0L
        var adInserted = false

        val adPcm: ByteArray? = if (includeAd && adDurationMs > 0L) {
            val targetBytes = msToPcmBytes(adDurationMs)
            val decodedAd = when {
                adBanner.mediaType == AdMediaType.VIDEO &&
                    adBanner.useVideoOwnAudio &&
                    !adBanner.useTts &&
                    adBanner.mediaUri != null ->
                    decodeAudioToStereo44100(context, adBanner.mediaUri, 0L, adDurationMs)
                !adBanner.useTts && adBanner.audioUri != null ->
                    decodeAudioToStereo44100(context, adBanner.audioUri, 0L, adDurationMs)
                adBanner.useTts && !adBanner.synthesizedTtsPath.isNullOrBlank() ->
                    decodeAudioFileToStereo44100(adBanner.synthesizedTtsPath, adDurationMs)
                else -> null
            }
            fitPcmLength(decodedAd, targetBytes)
        } else {
            null
        }

        fun appendSegmentWithOptionalAd(
            segUri: Uri,
            segStartMs: Long,
            segEndMs: Long
        ) {
            val segDuration = (segEndMs - segStartMs).coerceAtLeast(0L)
            val segEditedStart = editedCursorMs
            val segEditedEnd = editedCursorMs + segDuration

            if (includeAd && !adInserted && adTriggerMs in segEditedStart..segEditedEnd) {
                val firstPartMs = (adTriggerMs - segEditedStart).coerceAtLeast(0L)
                val secondPartMs = (segDuration - firstPartMs).coerceAtLeast(0L)

                if (firstPartMs > 0L) {
                    val p1 = decodeAudioToStereo44100(context, segUri, segStartMs, segStartMs + firstPartMs)
                    output.write(fitPcmLength(p1, msToPcmBytes(firstPartMs)))
                }
                if (adPcm != null) {
                    output.write(adPcm)
                }
                adInserted = true
                if (secondPartMs > 0L) {
                    val p2 = decodeAudioToStereo44100(context, segUri, segStartMs + firstPartMs, segEndMs)
                    output.write(fitPcmLength(p2, msToPcmBytes(secondPartMs)))
                }
            } else {
                val pcm = decodeAudioToStereo44100(context, segUri, segStartMs, segEndMs)
                output.write(fitPcmLength(pcm, msToPcmBytes(segDuration)))
            }
            editedCursorMs = segEditedEnd
        }

        for (clip in clips) {
            appendSegmentWithOptionalAd(sourceUri, clip.startMs, clip.endMs)
        }

        if (reactionUri != null && reactionDurationMs > 0L) {
            appendSegmentWithOptionalAd(reactionUri, 0L, reactionDurationMs)
        }

        if (includeAd && !adInserted && adPcm != null) {
            output.write(adPcm)
        }

        return output.toByteArray()
    }

    private fun msToPcmBytes(durationMs: Long): Int {
        val frames = ((durationMs.coerceAtLeast(0L) * AUDIO_SAMPLE_RATE) / 1000L).toInt()
        return frames * AUDIO_CHANNELS * 2
    }

    private fun fitPcmLength(pcm: ByteArray?, targetBytes: Int): ByteArray {
        val safeTarget = (targetBytes / 4) * 4
        val result = ByteArray(safeTarget)
        if (pcm != null && pcm.isNotEmpty()) {
            val copyLen = min(pcm.size, safeTarget)
            System.arraycopy(pcm, 0, result, 0, copyLen)
        }
        return result
    }

    private fun decodeAudioFileToStereo44100(path: String, maxDurationMs: Long): ByteArray? {
        val file = File(path)
        if (!file.exists()) return null
        return decodeAudioWithExtractor(
            setupSource = { it.setDataSource(path) },
            startMs = 0L,
            endMs = maxDurationMs
        )
    }

    private fun decodeAudioToStereo44100(
        context: Context,
        uri: Uri,
        startMs: Long,
        endMs: Long
    ): ByteArray? {
        return decodeAudioWithExtractor(
            setupSource = { it.setDataSource(context, uri, null) },
            startMs = startMs,
            endMs = endMs
        )
    }

    private fun decodeAudioWithExtractor(
        setupSource: (MediaExtractor) -> Unit,
        startMs: Long,
        endMs: Long
    ): ByteArray? {
        if (endMs <= startMs) return ByteArray(0)
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        val out = ByteArrayOutputStream()

        try {
            setupSource(extractor)
            var trackIndex = -1
            var trackFormat: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val fmt = extractor.getTrackFormat(i)
                val mime = fmt.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    trackIndex = i
                    trackFormat = fmt
                    break
                }
            }
            if (trackIndex < 0 || trackFormat == null) return null

            extractor.selectTrack(trackIndex)
            val seekUs = max(0L, (startMs - 100L) * 1000L)
            extractor.seekTo(seekUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)

            val mime = trackFormat.getString(MediaFormat.KEY_MIME) ?: return null
            var srcRate = trackFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var srcCh = trackFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)

            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(trackFormat, null, null, 0)
            codec.start()

            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            val startUs = startMs * 1000L
            val endUs = endMs * 1000L

            while (!outputDone) {
                if (!inputDone) {
                    val inIdx = codec.dequeueInputBuffer(5_000L)
                    if (inIdx >= 0) {
                        val inBuf = codec.getInputBuffer(inIdx)
                        if (inBuf != null) {
                            val size = extractor.readSampleData(inBuf, 0)
                            val pts = extractor.sampleTime
                            if (size < 0 || pts > endUs + 150_000L) {
                                codec.queueInputBuffer(
                                    inIdx, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM
                                )
                                inputDone = true
                            } else {
                                codec.queueInputBuffer(inIdx, 0, size, pts, 0)
                                extractor.advance()
                            }
                        }
                    }
                }

                val outIdx = codec.dequeueOutputBuffer(info, 5_000L)
                when {
                    outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val f = codec.outputFormat
                        if (f.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                            srcRate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        }
                        if (f.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                            srcCh = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        }
                    }

                    outIdx >= 0 -> {
                        val buf = codec.getOutputBuffer(outIdx)
                        if (buf != null && info.size > 0) {
                            val chunkPtsUs = info.presentationTimeUs
                            val sampleFrames = info.size / (2 * max(1, srcCh))
                            val chunkEndUs = chunkPtsUs + (sampleFrames * 1_000_000L / max(1, srcRate))

                            if (chunkEndUs >= startUs && chunkPtsUs <= endUs) {
                                buf.position(info.offset)
                                buf.limit(info.offset + info.size)
                                val raw = ByteArray(info.size)
                                buf.get(raw)
                                val resampled = resamplePcmToStereo44100(raw, srcRate, srcCh)
                                out.write(resampled)
                            }
                        }
                        codec.releaseOutputBuffer(outIdx, false)
                        if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            outputDone = true
                        }
                    }
                }
            }
            return out.toByteArray()
        } catch (_: Throwable) {
            return null
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { extractor.release() }
        }
    }

    private fun resamplePcmToStereo44100(
        rawBytes: ByteArray,
        srcRate: Int,
        srcChannels: Int
    ): ByteArray {
        val safeCh = max(1, srcChannels)
        val safeRate = max(8000, srcRate)
        val shortCount = rawBytes.size / 2
        val srcFrames = shortCount / safeCh
        if (srcFrames <= 0) return ByteArray(0)

        val srcShorts = ShortArray(shortCount)
        ByteBuffer.wrap(rawBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(srcShorts)

        if (safeRate == AUDIO_SAMPLE_RATE && safeCh == AUDIO_CHANNELS) {
            return rawBytes
        }

        val ratio = safeRate.toDouble() / AUDIO_SAMPLE_RATE.toDouble()
        val dstFrames = max(1, (srcFrames / ratio).roundToInt())
        val dstBytes = ByteArray(dstFrames * AUDIO_CHANNELS * 2)
        val dstBuf = ByteBuffer.wrap(dstBytes).order(ByteOrder.LITTLE_ENDIAN)

        for (i in 0 until dstFrames) {
            val srcPos = min((i * ratio).toInt(), srcFrames - 1)
            val base = srcPos * safeCh
            val left = srcShorts[base]
            val right = if (safeCh >= 2 && base + 1 < srcShorts.size) srcShorts[base + 1] else left
            dstBuf.putShort(left)
            dstBuf.putShort(right)
        }
        return dstBytes
    }

    private fun saveMp4ToGallery(context: Context, sourceFile: File, displayName: String): Uri? {
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
                    put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                    put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/ReelsCut")
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                    ?: return@runCatching null
                resolver.openOutputStream(uri)?.use { outStream ->
                    FileInputStream(sourceFile).use { inStream ->
                        inStream.copyTo(outStream)
                    }
                }
                values.clear()
                values.put(MediaStore.Video.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                uri
            } else {
                val dir = File(
                    context.getExternalFilesDir(Environment.DIRECTORY_MOVIES),
                    "ReelsCut"
                ).apply { mkdirs() }
                val dest = File(dir, displayName)
                sourceFile.copyTo(dest, overwrite = true)
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", dest)
            }
        }.getOrNull()
    }
}
