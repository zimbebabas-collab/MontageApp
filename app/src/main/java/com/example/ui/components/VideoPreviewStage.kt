package com.example.ui.components

import android.media.MediaPlayer
import android.net.Uri
import android.view.Gravity
import android.view.TextureView
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.Face
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import coil.compose.AsyncImage
import com.example.R
import com.example.media.SubtitleEngine
import com.example.model.AdMediaType
import com.example.model.CaptionChunk
import com.example.model.EditorTab
import com.example.model.EditorUiState
import com.example.model.SubtitlePosition
import com.example.ui.theme.AccentGreen
import com.example.ui.theme.AccentYellow
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.MontserratFamily
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.SpeakerBlue
import com.example.ui.theme.SpeakerPink
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.SurfaceElevated
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlin.math.sqrt

@OptIn(UnstableApi::class)
@Composable
fun VideoPreviewStage(
    state: EditorUiState,
    onTogglePlay: () -> Unit,
    onSeekEditedMs: (Long) -> Unit,
    onPlayerProgress: (Long) -> Unit,
    onUpdateFocalPoint: (Float, Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val latestState by rememberUpdatedState(state)
    val latestOnProgress by rememberUpdatedState(onPlayerProgress)

    val mainUri = state.videoUri
    val reactionUri = state.meme.reactionClipUri

    val position = remember(state.currentEditedMs, state.clips, state.activeReactionDurationMs) {
        SubtitleEngine.mapEditedMsToTimeline(
            editedMs = state.currentEditedMs,
            clips = state.clips,
            reactionDurationMs = state.activeReactionDurationMs
        )
    }

    val isReactionActive = position is SubtitleEngine.TimelinePosition.ReactionClip && reactionUri != null
    val activeVideoUri = if (isReactionActive) reactionUri else mainUri

    var videoAspectRatio by remember { mutableFloatStateOf(9f / 16f) }
    var adVideoAspectRatio by remember { mutableFloatStateOf(16f / 9f) }

    // Main video ExoPlayer (single persistent instance, TextureView attached ONLY ONCE in factory)
    val exoPlayer = remember(context) {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_OFF
        }
    }

    // Dedicated ExoPlayer for Video Ad Banner during mid-roll pause
    val adExoPlayer = remember(context) {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_ALL
        }
    }

    DisposableEffect(exoPlayer, adExoPlayer) {
        val mainListener = object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    val ratio = (videoSize.width * videoSize.pixelWidthHeightRatio) / videoSize.height
                    if (ratio in 0.2f..5f) {
                        videoAspectRatio = ratio
                    }
                }
            }
        }
        val adListener = object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    val ratio = (videoSize.width * videoSize.pixelWidthHeightRatio) / videoSize.height
                    if (ratio in 0.3f..3.5f) {
                        adVideoAspectRatio = ratio
                    }
                }
            }
        }
        exoPlayer.addListener(mainListener)
        adExoPlayer.addListener(adListener)
        onDispose {
            exoPlayer.removeListener(mainListener)
            adExoPlayer.removeListener(adListener)
            exoPlayer.release()
            adExoPlayer.release()
        }
    }

    // Load active main or reaction media item ONLY when URI changes
    var loadedMainUri by remember { mutableStateOf<Uri?>(null) }
    LaunchedEffect(activeVideoUri) {
        if (activeVideoUri != null && activeVideoUri != loadedMainUri) {
            loadedMainUri = activeVideoUri
            exoPlayer.setMediaItem(MediaItem.fromUri(activeVideoUri))
            exoPlayer.prepare()
            val pos = SubtitleEngine.mapEditedMsToTimeline(
                latestState.currentEditedMs,
                latestState.clips,
                latestState.activeReactionDurationMs
            )
            val initMs = when (pos) {
                is SubtitleEngine.TimelinePosition.MainClip -> pos.sourceMs
                is SubtitleEngine.TimelinePosition.ReactionClip -> pos.reactionMs
                null -> 0L
            }
            exoPlayer.seekTo(initMs)
            exoPlayer.playWhenReady = latestState.isPlaying && !latestState.isAdPauseActive
        } else if (activeVideoUri == null) {
            loadedMainUri = null
            exoPlayer.clearMediaItems()
        }
    }

    // Load Video Ad Banner media item ONLY when ad video URI changes
    val adVideoUri = if (state.adBanner.mediaType == AdMediaType.VIDEO) state.adBanner.mediaUri else null
    var loadedAdVideoUri by remember { mutableStateOf<Uri?>(null) }
    LaunchedEffect(adVideoUri) {
        if (adVideoUri != null && adVideoUri != loadedAdVideoUri) {
            loadedAdVideoUri = adVideoUri
            adExoPlayer.setMediaItem(MediaItem.fromUri(adVideoUri))
            adExoPlayer.prepare()
            adExoPlayer.playWhenReady = false
        } else if (adVideoUri == null) {
            loadedAdVideoUri = null
            adExoPlayer.clearMediaItems()
        }
    }

    // Sync Video Ad Banner play/pause + mute state cleanly during mid-roll pause
    val showAdOverlay = state.adBanner.enabled &&
        state.adBanner.mediaUri != null &&
        state.isAdPauseActive

    LaunchedEffect(showAdOverlay, adVideoUri, state.adBanner.useVideoOwnAudio, state.adBanner.useTts) {
        if (adVideoUri != null) {
            if (showAdOverlay && state.isPlaying) {
                adExoPlayer.volume = if (state.adBanner.useVideoOwnAudio && !state.adBanner.useTts) 1f else 0f
                adExoPlayer.seekTo(0L)
                adExoPlayer.playWhenReady = true
            } else {
                adExoPlayer.playWhenReady = false
            }
        }
    }

    // Play uploaded custom Ad audio file during mid-roll Ad Pause (when not using TTS or video's own audio)
    val adAudioUri = state.adBanner.audioUri
    val shouldPlayAdFileAudio = state.isPlaying &&
        state.isAdPauseActive &&
        !state.adBanner.useTts &&
        !(state.adBanner.mediaType == AdMediaType.VIDEO && state.adBanner.useVideoOwnAudio) &&
        adAudioUri != null

    DisposableEffect(shouldPlayAdFileAudio, adAudioUri) {
        var mp: MediaPlayer? = null
        if (shouldPlayAdFileAudio && adAudioUri != null) {
            mp = runCatching {
                MediaPlayer().apply {
                    setDataSource(context, adAudioUri)
                    prepare()
                    start()
                }
            }.getOrNull()
        }
        onDispose {
            runCatching {
                mp?.stop()
                mp?.release()
            }
        }
    }

    // Immediate play/pause sync for main ExoPlayer (freezes frame instantly on Ad Pause!)
    LaunchedEffect(state.isPlaying, state.isAdPauseActive, activeVideoUri) {
        if (activeVideoUri == null) return@LaunchedEffect
        val shouldPlay = state.isPlaying && !state.isAdPauseActive
        if (exoPlayer.playWhenReady != shouldPlay) {
            exoPlayer.playWhenReady = shouldPlay
        }
    }

    // Handle EXPLICIT user seeks ONLY (slider drag, tap clip card, tap word, restart)
    LaunchedEffect(state.seekRequestNonce) {
        if (activeVideoUri == null) return@LaunchedEffect
        val pos = SubtitleEngine.mapEditedMsToTimeline(
            latestState.currentEditedMs,
            latestState.clips,
            latestState.activeReactionDurationMs
        )
        val seekTarget = when (pos) {
            is SubtitleEngine.TimelinePosition.MainClip -> pos.sourceMs
            is SubtitleEngine.TimelinePosition.ReactionClip -> pos.reactionMs
            null -> 0L
        }
        if (abs(exoPlayer.currentPosition - seekTarget) > 35L) {
            exoPlayer.seekTo(seekTarget)
        }
    }

    // CRITICAL ANTI-STUTTER ENGINE:
    // ExoPlayer owns the continuous media clock! This lightweight loop ONLY reads currentPosition
    // and jumps cleanly at clip cut boundaries — NEVER fighting or re-seeking mid-clip!
    LaunchedEffect(state.isPlaying, state.isAdPauseActive, activeVideoUri) {
        if (!state.isPlaying || state.isAdPauseActive || activeVideoUri == null) return@LaunchedEffect
        var currentClipIdx = (SubtitleEngine.mapEditedMsToTimeline(
            latestState.currentEditedMs,
            latestState.clips,
            latestState.activeReactionDurationMs
        ) as? SubtitleEngine.TimelinePosition.MainClip)?.clipIndex ?: 0

        while (isActive) {
            delay(33L) // ~30fps UI/subtitle/peak sync while TextureView decodes natively at 60fps
            val st = latestState
            if (!st.isPlaying || st.isAdPauseActive) break

            val clips = st.clips
            if (clips.isEmpty()) break

            val mapped = SubtitleEngine.mapEditedMsToTimeline(
                st.currentEditedMs,
                clips,
                st.activeReactionDurationMs
            )

            if (mapped is SubtitleEngine.TimelinePosition.ReactionClip && st.meme.reactionClipUri != null) {
                val rPos = exoPlayer.currentPosition.coerceAtLeast(0L)
                val edited = st.totalMainContentMs + rPos.coerceAtMost(st.activeReactionDurationMs)
                latestOnProgress(edited)
                if (exoPlayer.playbackState == Player.STATE_ENDED || rPos >= st.activeReactionDurationMs - 30L) {
                    latestOnProgress(st.totalEditedContentMs)
                    break
                }
            } else {
                // Keep clip index synced if user tapped another clip while playing
                val mappedIdx = (mapped as? SubtitleEngine.TimelinePosition.MainClip)?.clipIndex ?: 0
                currentClipIdx = mappedIdx.coerceIn(0, clips.lastIndex)
                val clip = clips[currentClipIdx]

                // Compute edited offset of this clip
                var clipStartEdited = 0L
                for (i in 0 until currentClipIdx) {
                    clipStartEdited += clips[i].durationMs
                }

                val playerPosMs = exoPlayer.currentPosition.coerceAtLeast(0L)

                if (playerPosMs < clip.startMs - 80L) {
                    exoPlayer.seekTo(clip.startMs)
                    continue
                }

                if (playerPosMs >= clip.endMs - 25L || exoPlayer.playbackState == Player.STATE_ENDED) {
                    val nextIdx = currentClipIdx + 1
                    if (nextIdx < clips.size) {
                        val nextClip = clips[nextIdx]
                        val nextEditedMs = clipStartEdited + clip.durationMs + 5L
                        currentClipIdx = nextIdx
                        exoPlayer.seekTo(nextClip.startMs)
                        latestOnProgress(nextEditedMs)
                    } else if (st.activeReactionDurationMs > 0L) {
                        latestOnProgress(st.totalMainContentMs + 10L)
                    } else {
                        latestOnProgress(st.totalEditedContentMs)
                        break
                    }
                } else {
                    val offsetInClip = (playerPosMs - clip.startMs).coerceIn(0L, clip.durationMs)
                    latestOnProgress(clipStartEdited + offsetInClip)
                }
            }
        }
    }

    val activeMainClip = (position as? SubtitleEngine.TimelinePosition.MainClip)?.clip

    val cameraFrame = remember(
        position,
        state.audioPeaks,
        state.framing,
        state.isAdPauseActive
    ) {
        when {
            state.isAdPauseActive -> SubtitleEngine.CameraFrameState(
                scale = 1f,
                focalX = state.framing.focalX,
                focalY = state.framing.focalY,
                isCloseUp = false,
                offsetXFraction = 0f,
                offsetYFraction = 0f,
                peakFlashAlpha = 0f,
                saturationBoost = 1f,
                peakPulse = 0f
            )
            position is SubtitleEngine.TimelinePosition.MainClip -> SubtitleEngine.computeCameraFrame(
                clip = position.clip,
                sourceMs = position.sourceMs,
                peaks = state.audioPeaks,
                framing = state.framing
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
    }

    // Silky-smooth GPU-accelerated zoom & face-lock focal transitions (ZERO frame stutter!)
    val animatedScale by animateFloatAsState(
        targetValue = cameraFrame.scale,
        animationSpec = tween(durationMillis = 140),
        label = "cameraScale"
    )
    val animatedFocalX by animateFloatAsState(
        targetValue = cameraFrame.focalX,
        animationSpec = tween(durationMillis = 160),
        label = "cameraFocalX"
    )
    val animatedFocalY by animateFloatAsState(
        targetValue = cameraFrame.focalY,
        animationSpec = tween(durationMillis = 160),
        label = "cameraFocalY"
    )

    val activeCaption = remember(
        state.currentEditedMs,
        state.captions,
        state.captionsEnabled,
        state.isAdPauseActive,
        isReactionActive
    ) {
        if (state.captionsEnabled && !state.isAdPauseActive && !isReactionActive) {
            SubtitleEngine.activeCaptionAt(state.currentEditedMs, state.captions)
        } else {
            null
        }
    }

    val showFocalOverlay = state.activeTab == EditorTab.FRAMING && !state.isAdPauseActive && !isReactionActive

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Strictly proportional 9:16 vertical stage
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            val stageBorderColor = when {
                cameraFrame.peakFlashAlpha > 0.08f -> SpeakerPink
                cameraFrame.isCloseUp -> SpeakerBlue.copy(alpha = 0.85f)
                else -> BorderSubtle
            }

            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxHeight()
                    .aspectRatio(9f / 16f, matchHeightConstraintsFirst = true)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.Black)
                    .border(
                        width = if (cameraFrame.peakFlashAlpha > 0.08f) 2.dp else 1.dp,
                        color = stageBorderColor,
                        shape = RoundedCornerShape(20.dp)
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onTogglePlay
                    )
                    .testTag("video_preview_stage")
            ) {
                val stageW = constraints.maxWidth.toFloat().coerceAtLeast(1f)
                val stageH = constraints.maxHeight.toFloat().coerceAtLeast(1f)
                val density = LocalDensity.current

                // Main Video TextureView — bound to ExoPlayer ONCE in factory (NEVER in update!)
                var lastAppliedRatio by remember { mutableFloatStateOf(-1f) }
                AndroidView(
                    factory = { ctx ->
                        AspectRatioFrameLayout(ctx).apply {
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                            setAspectRatio(videoAspectRatio)
                            lastAppliedRatio = videoAspectRatio
                            val tv = TextureView(ctx)
                            val lp = FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                Gravity.CENTER
                            )
                            addView(tv, lp)
                            exoPlayer.setVideoTextureView(tv)
                        }
                    },
                    update = { container ->
                        if (abs(lastAppliedRatio - videoAspectRatio) > 0.005f) {
                            container.setAspectRatio(videoAspectRatio)
                            lastAppliedRatio = videoAspectRatio
                        }
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            transformOrigin = TransformOrigin(animatedFocalX, animatedFocalY)
                            scaleX = animatedScale
                            scaleY = animatedScale
                            translationX = cameraFrame.offsetXFraction * stageW
                            translationY = cameraFrame.offsetYFraction * stageH
                        }
                )

                // Saturated Key-Moment Visual Effects: Vivid Neon Glow + Peak Flash Pulse
                if (!state.isAdPauseActive && !isReactionActive && cameraFrame.peakFlashAlpha > 0.01f) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val a = cameraFrame.peakFlashAlpha
                        drawRect(
                            brush = Brush.radialGradient(
                                colorStops = arrayOf(
                                    0.0f to Color.White.copy(alpha = a * 0.45f),
                                    0.55f to Color(0xFF00F0FF).copy(alpha = a * 0.65f),
                                    1.0f to Color(0xFFFF2E93).copy(alpha = a)
                                ),
                                center = Offset(size.width / 2f, size.height / 2f),
                                radius = size.height * 0.62f
                            )
                        )
                    }
                }

                // Optional subtle vignette
                if (state.framing.vignette && !isReactionActive) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawRect(
                            brush = Brush.radialGradient(
                                colorStops = arrayOf(
                                    0.0f to Color.Transparent,
                                    0.58f to Color.Transparent,
                                    1.0f to Color.Black.copy(alpha = 0.50f)
                                ),
                                center = Offset(size.width / 2f, size.height / 2f),
                                radius = size.height * 0.65f
                            )
                        )
                    }
                }

                // Top Black Meme Caption Bar
                if (state.meme.topBarEnabled && state.meme.topBarText.isNotBlank() && !isReactionActive) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(0.145f)
                            .align(Alignment.TopCenter)
                            .background(Color.Black)
                            .padding(horizontal = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = state.meme.topBarText.uppercase(),
                            style = TextStyle(
                                fontFamily = MontserratFamily,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 13.sp,
                                lineHeight = 16.sp,
                                color = Color.White,
                                textAlign = TextAlign.Center
                            ),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Face-Lock & Framing Status Pill
                val hasFace = activeMainClip?.hasDetectedFace == true && state.framing.autoFaceTracking
                val badgeText = when {
                    isReactionActive -> "МЕМ-ВКЛЕЙКА"
                    cameraFrame.isCloseUp && hasFace -> String.format("ЛИЦО %.2fx", cameraFrame.scale)
                    cameraFrame.isCloseUp -> String.format("ТОЧКА %.2fx", cameraFrame.scale)
                    hasFace -> "ЛИЦО • 1.0x"
                    else -> "1.0x ОБЩИЙ"
                }
                val badgeColor = when {
                    isReactionActive -> SpeakerPink
                    hasFace -> AccentGreen
                    cameraFrame.isCloseUp -> SpeakerBlue
                    else -> TextSecondary
                }
                Surface(
                    shape = RoundedCornerShape(50),
                    color = ObsidianBg.copy(alpha = 0.72f),
                    border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.65f)),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(
                            start = 8.dp,
                            top = if (state.meme.topBarEnabled && !isReactionActive) 44.dp else 8.dp
                        )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (hasFace) {
                            Icon(
                                imageVector = Icons.Rounded.Face,
                                contentDescription = null,
                                tint = AccentGreen,
                                modifier = Modifier.size(11.dp)
                            )
                        }
                        Text(
                            text = badgeText,
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 9.sp,
                            color = badgeColor
                        )
                    }
                }

                // SATURATED MULTI-COLORED SUBTITLES (BOTTOM by default or CENTER)
                if (activeCaption != null) {
                    val popScale = SubtitleEngine.computePopScale(state.currentEditedMs, activeCaption)
                    val subtitleModifier = when (state.subtitlePosition) {
                        SubtitlePosition.BOTTOM -> Modifier
                            .align(Alignment.BottomCenter)
                            .padding(
                                start = 12.dp,
                                end = 12.dp,
                                bottom = with(density) { (stageH * 0.16f).toDp() }
                            )
                        SubtitlePosition.CENTER -> Modifier
                            .align(Alignment.Center)
                            .offset { IntOffset(0, (stageH * 0.02f).roundToInt()) }
                            .padding(horizontal = 12.dp)
                    }
                    VibrantKineticSubtitle(
                        caption = activeCaption,
                        popScale = popScale,
                        modifier = subtitleModifier
                    )
                }

                // Interactive Face / Key-Point Target Overlay (visible on «Камера и FX» tab)
                if (showFocalOverlay) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectTapGestures { offset ->
                                    onUpdateFocalPoint(offset.x / stageW, offset.y / stageH)
                                }
                            }
                            .pointerInput(Unit) {
                                detectDragGestures { change, _ ->
                                    change.consume()
                                    val pos = change.position
                                    onUpdateFocalPoint(pos.x / stageW, pos.y / stageH)
                                }
                            }
                    ) {
                        val targetXPx = (animatedFocalX * stageW).roundToInt()
                        val targetYPx = (animatedFocalY * stageH).roundToInt()
                        val halfPx = with(density) { 19.dp.roundToPx() }
                        val targetTint = if (hasFace) AccentGreen else SpeakerBlue
                        Box(
                            modifier = Modifier
                                .offset { IntOffset(targetXPx - halfPx, targetYPx - halfPx) }
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(targetTint.copy(alpha = 0.22f))
                                .border(1.8.dp, targetTint, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (hasFace) Icons.Rounded.Face else Icons.Rounded.CenterFocusStrong,
                                contentDescription = "Точка фокуса камеры",
                                tint = Color.White,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }
                }

                // MID-ROLL AD BANNER OVERLAY (IMAGE or VIDEO! Strictly dead-center, 25%..35% area, rgba(0,0,0,0.45) dim)
                androidx.compose.animation.AnimatedVisibility(
                    visible = showAdOverlay,
                    enter = fadeIn() + scaleIn(initialScale = 0.90f),
                    exit = fadeOut() + scaleOut(targetScale = 0.94f),
                    modifier = Modifier.fillMaxSize()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.45f)), // Exact rgba(0,0,0,0.45)
                        contentAlignment = Alignment.Center
                    ) {
                        val areaFraction = state.adBanner.areaPercent.coerceIn(25, 35) / 100f
                        val targetAreaPx = stageW * stageH * areaFraction
                        val bannerAspect = if (state.adBanner.mediaType == AdMediaType.VIDEO) {
                            adVideoAspectRatio.coerceIn(0.65f, 1.85f)
                        } else {
                            1.08f
                        }
                        val bannerW = sqrt(targetAreaPx * bannerAspect).coerceAtMost(stageW * 0.86f)
                        val bannerH = (targetAreaPx / bannerW).coerceAtMost(stageH * 0.58f)
                        val bannerWDp = with(density) { bannerW.toDp() }
                        val bannerHDp = with(density) { bannerH.toDp() }
                        val secLeft = ceil(state.adRemainingMs / 1000.0).toInt().coerceAtLeast(1)

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = ObsidianBg.copy(alpha = 0.88f),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.32f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Campaign,
                                        contentDescription = null,
                                        tint = SpeakerPink,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Text(
                                        text = if (state.adBanner.mediaType == AdMediaType.VIDEO) {
                                            "ВИДЕО-РЕКЛАМА • ${secLeft}с"
                                        } else {
                                            "РЕКЛАМА • ${secLeft}с"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White
                                    )
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .size(width = bannerWDp, height = bannerHDp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .border(1.8.dp, Color.White.copy(alpha = 0.78f), RoundedCornerShape(16.dp))
                                    .background(SurfaceElevated)
                            ) {
                                if (state.adBanner.mediaType == AdMediaType.VIDEO) {
                                    var lastAdRatio by remember { mutableFloatStateOf(-1f) }
                                    AndroidView(
                                        factory = { ctx ->
                                            AspectRatioFrameLayout(ctx).apply {
                                                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                                setAspectRatio(adVideoAspectRatio)
                                                lastAdRatio = adVideoAspectRatio
                                                val tv = TextureView(ctx)
                                                addView(
                                                    tv,
                                                    FrameLayout.LayoutParams(
                                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                                        Gravity.CENTER
                                                    )
                                                )
                                                adExoPlayer.setVideoTextureView(tv)
                                            }
                                        },
                                        update = { container ->
                                            if (abs(lastAdRatio - adVideoAspectRatio) > 0.01f) {
                                                container.setAspectRatio(adVideoAspectRatio)
                                                lastAdRatio = adVideoAspectRatio
                                            }
                                        },
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    AsyncImage(
                                        model = state.adBanner.mediaUri,
                                        contentDescription = stringResource(R.string.cd_ad_banner),
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                        }
                    }
                }

                // Subtle Play affordance when paused
                if (!state.isPlaying && !state.isAnalyzing && !showFocalOverlay) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(ObsidianBg.copy(alpha = 0.65f))
                            .border(1.dp, Color.White.copy(alpha = 0.28f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = stringResource(R.string.cd_play_pause),
                            tint = Color.White,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }

                // Analysis spinner overlay
                if (state.isAnalyzing) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(ObsidianBg.copy(alpha = 0.85f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CircularProgressIndicator(color = SpeakerBlue, strokeWidth = 2.5.dp)
                            Text(
                                text = "Режем паузы, ищем лица и голос...",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 14.dp)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Clean Compact Scrubber Bar
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = SurfaceDark,
            border = BorderStroke(1.dp, BorderSubtle),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    onClick = onTogglePlay,
                    shape = CircleShape,
                    color = if (state.isPlaying) SpeakerPink else SpeakerBlue,
                    modifier = Modifier
                        .size(40.dp)
                        .testTag("play_pause_button")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = stringResource(R.string.cd_play_pause),
                            tint = ObsidianBg,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    val totalMs = state.totalEditedContentMs.coerceAtLeast(100L)
                    Slider(
                        value = state.currentEditedMs.toFloat().coerceIn(0f, totalMs.toFloat()),
                        onValueChange = { onSeekEditedMs(it.toLong()) },
                        valueRange = 0f..totalMs.toFloat(),
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = SpeakerBlue,
                            inactiveTrackColor = BorderSubtle
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(26.dp)
                            .testTag("playback_slider")
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = SubtitleEngine.formatTimeMs(state.currentEditedMs),
                            style = MaterialTheme.typography.labelSmall,
                            color = SpeakerBlue
                        )
                        val midInfo = when {
                            state.isTrackingFaces -> "Поиск лиц..."
                            state.isTranscribingVideo -> "Распознаём речь..."
                            state.adBanner.enabled && state.adBanner.mediaUri != null ->
                                "Пауза: ${SubtitleEngine.formatTimeMs(state.adBanner.triggerAtMs)}"
                            state.sfxEvents.isNotEmpty() ->
                                "Клипов: ${state.clips.size} • SFX: ${state.sfxEvents.size}"
                            else -> "Клипов: ${state.clips.size}"
                        }
                        Text(
                            text = midInfo,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (state.isTrackingFaces || state.isTranscribingVideo) AccentYellow else TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = SubtitleEngine.formatTimeMs(state.totalEditedContentMs),
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VibrantKineticSubtitle(
    caption: CaptionChunk,
    popScale: Float,
    modifier: Modifier = Modifier
) {
    val fillColor = Color(caption.color.argb)
    val glowColor = Color(caption.color.glowArgb)
    val fontSizeSp = when {
        caption.text.length >= 13 -> 18.sp
        caption.text.length >= 10 -> 21.sp
        caption.isActionNote -> 21.sp
        else -> 27.sp
    }

    Box(
        modifier = modifier.graphicsLayer {
            scaleX = popScale
            scaleY = popScale
        },
        contentAlignment = Alignment.Center
    ) {
        // Crisp dark outline layer for razor-sharp contrast over bright footage
        Text(
            text = caption.text,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
            style = TextStyle(
                fontFamily = MontserratFamily,
                fontWeight = FontWeight.Black,
                fontSize = fontSizeSp,
                color = Color(0xF0080810),
                textAlign = TextAlign.Center,
                letterSpacing = 0.5.sp,
                drawStyle = Stroke(width = 9f, join = StrokeJoin.Round)
            )
        )
        // Saturated vivid fill + neon colored glow shadow
        Text(
            text = caption.text,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
            style = TextStyle(
                fontFamily = MontserratFamily,
                fontWeight = FontWeight.Black,
                fontSize = fontSizeSp,
                color = fillColor,
                textAlign = TextAlign.Center,
                letterSpacing = 0.5.sp,
                shadow = Shadow(
                    color = glowColor,
                    offset = Offset(0f, 3f),
                    blurRadius = 16f
                )
            )
        )
    }
}
