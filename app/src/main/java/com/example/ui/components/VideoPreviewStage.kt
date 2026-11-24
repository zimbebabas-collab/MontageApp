package com.example.ui.components

import android.media.MediaPlayer
import android.view.Gravity
import android.view.TextureView
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TransformOrigin
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
import com.example.model.CaptionChunk
import com.example.model.EditorTab
import com.example.model.EditorUiState
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.MontserratFamily
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.SpeakerBlue
import com.example.ui.theme.SpeakerPink
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.SurfaceElevated
import com.example.ui.theme.TextSecondary
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
    onUpdateFocalPoint: (Float, Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
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

    val exoPlayer = remember(context) {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_OFF
        }
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    val ratio = (videoSize.width * videoSize.pixelWidthHeightRatio) / videoSize.height
                    if (ratio in 0.2f..5f) {
                        videoAspectRatio = ratio
                    }
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    var loadedUri by remember { mutableStateOf<android.net.Uri?>(null) }

    LaunchedEffect(activeVideoUri) {
        if (activeVideoUri != null && activeVideoUri != loadedUri) {
            loadedUri = activeVideoUri
            exoPlayer.setMediaItem(MediaItem.fromUri(activeVideoUri))
            exoPlayer.prepare()
            exoPlayer.playWhenReady = state.isPlaying && !state.isAdPauseActive
        } else if (activeVideoUri == null) {
            loadedUri = null
            exoPlayer.clearMediaItems()
        }
    }

    // Play uploaded custom Ad audio file during mid-roll Ad Pause
    val adAudioUri = state.adBanner.audioUri
    val shouldPlayAdFileAudio = state.isPlaying &&
        state.isAdPauseActive &&
        !state.adBanner.useTts &&
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

    val targetSourceMs = when (position) {
        is SubtitleEngine.TimelinePosition.MainClip -> position.sourceMs
        is SubtitleEngine.TimelinePosition.ReactionClip -> position.reactionMs
        null -> 0L
    }

    LaunchedEffect(state.isPlaying, state.isAdPauseActive, targetSourceMs, activeVideoUri) {
        if (activeVideoUri == null) return@LaunchedEffect
        val shouldPlay = state.isPlaying && !state.isAdPauseActive
        if (exoPlayer.playWhenReady != shouldPlay) {
            exoPlayer.playWhenReady = shouldPlay
        }
        val drift = abs(exoPlayer.currentPosition - targetSourceMs)
        if (!shouldPlay || drift > 260L) {
            exoPlayer.seekTo(targetSourceMs)
        }
    }

    val cameraFrame = remember(
        position,
        state.audioPeaks,
        state.framing,
        state.isAdPauseActive
    ) {
        when {
            state.isAdPauseActive -> SubtitleEngine.CameraFrameState(1f, state.framing.focalX, state.framing.focalY, false)
            position is SubtitleEngine.TimelinePosition.MainClip -> SubtitleEngine.computeCameraFrame(
                clip = position.clip,
                sourceMs = position.sourceMs,
                peaks = state.audioPeaks,
                framing = state.framing
            )
            else -> SubtitleEngine.CameraFrameState(1f, 0.5f, 0.5f, false)
        }
    }

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
        // Strictly proportional 9:16 vertical stage that fits cleanly without clipping
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxHeight()
                    .aspectRatio(9f / 16f, matchHeightConstraintsFirst = true)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.Black)
                    .border(
                        width = 1.dp,
                        color = if (cameraFrame.isCloseUp) SpeakerBlue.copy(alpha = 0.7f) else BorderSubtle,
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

                // Center-cropped TextureView inside AspectRatioFrameLayout(RESIZE_MODE_ZOOM)
                // with exact focal-point (focalX, focalY) zoom transform
                AndroidView(
                    factory = { ctx ->
                        AspectRatioFrameLayout(ctx).apply {
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                            setAspectRatio(videoAspectRatio)
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
                        container.setAspectRatio(videoAspectRatio)
                        val tv = container.getChildAt(0) as? TextureView
                        if (tv != null) {
                            exoPlayer.setVideoTextureView(tv)
                        }
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            transformOrigin = TransformOrigin(cameraFrame.focalX, cameraFrame.focalY)
                            scaleX = cameraFrame.scale
                            scaleY = cameraFrame.scale
                        }
                )

                // Optional subtle vignette
                if (state.framing.vignette && !isReactionActive) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawRect(
                            brush = Brush.radialGradient(
                                colorStops = arrayOf(
                                    0.0f to Color.Transparent,
                                    0.60f to Color.Transparent,
                                    1.0f to Color.Black.copy(alpha = 0.48f)
                                ),
                                center = Offset(size.width / 2f, size.height / 2f),
                                radius = size.height * 0.65f
                            )
                        )
                    }
                }

                // Top Black Meme Caption Bar (Mode: «Мем-вклейка» or toggled on)
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

                // Subtle framing badge (1.0x / 1.35x or MEME)
                val badgeText = when {
                    isReactionActive -> "МЕМ-ВКЛЕЙКА"
                    cameraFrame.isCloseUp -> String.format("%.2fx КРУПНО", cameraFrame.scale)
                    else -> "1.0x ОБЩИЙ"
                }
                val badgeColor = when {
                    isReactionActive -> SpeakerPink
                    cameraFrame.isCloseUp -> SpeakerBlue
                    else -> TextSecondary
                }
                Surface(
                    shape = RoundedCornerShape(50),
                    color = ObsidianBg.copy(alpha = 0.68f),
                    border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.55f)),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(
                            start = 8.dp,
                            top = if (state.meme.topBarEnabled && !isReactionActive) 44.dp else 8.dp
                        )
                ) {
                    Text(
                        text = badgeText,
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 9.sp,
                        color = badgeColor,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                    )
                }

                // SINGLE-WORD DEAD-CENTER SUBTITLE (50% X, 52% Y)
                // Clean Montserrat Black uppercase + crisp dark drop-shadow, 120ms pop-in (0.85 -> 1.0)
                if (activeCaption != null) {
                    val popScale = SubtitleEngine.computePopScale(state.currentEditedMs, activeCaption)
                    val offsetYPx = (stageH * 0.02f).roundToInt() // 50% + 2% = 52% Y
                    SingleWordCenterSubtitle(
                        caption = activeCaption,
                        popScale = popScale,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .offset { IntOffset(0, offsetYPx) }
                            .padding(horizontal = 12.dp)
                    )
                }

                // Interactive Touch Focal Point Target (visible on «Камера» tab)
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
                        val targetXPx = (state.framing.focalX * stageW).roundToInt()
                        val targetYPx = (state.framing.focalY * stageH).roundToInt()
                        val halfPx = with(density) { 18.dp.roundToPx() }
                        Box(
                            modifier = Modifier
                                .offset { IntOffset(targetXPx - halfPx, targetYPx - halfPx) }
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(SpeakerBlue.copy(alpha = 0.22f))
                                .border(1.5.dp, SpeakerBlue, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CenterFocusStrong,
                                contentDescription = "Точка фокуса камеры",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                // MID-ROLL AD BANNER OVERLAY (Strictly dead-center, 25%..35% area, rgba(0,0,0,0.45) dim)
                val showAdOverlay = state.adBanner.enabled &&
                    state.adBanner.imageUri != null &&
                    state.isAdPauseActive

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
                        val bannerAspect = 1.05f
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
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.28f))
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
                                        text = "РЕКЛАМА • ${secLeft}с",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White
                                    )
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .size(width = bannerWDp, height = bannerHDp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .border(1.5.dp, Color.White.copy(alpha = 0.7f), RoundedCornerShape(16.dp))
                                    .background(SurfaceElevated)
                            ) {
                                AsyncImage(
                                    model = state.adBanner.imageUri,
                                    contentDescription = stringResource(R.string.cd_ad_banner),
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }
                }

                // Subtle Play affordance when paused (hidden when dragging focal target)
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

                // Audio analysis spinner
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
                                text = "Режем паузы и настраиваем планы...",
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
                        Text(
                            text = if (state.adBanner.enabled && state.adBanner.imageUri != null) {
                                "Пауза: ${SubtitleEngine.formatTimeMs(state.adBanner.triggerAtMs)}"
                            } else {
                                "Клипов: ${state.clips.size}"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
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
private fun SingleWordCenterSubtitle(
    caption: CaptionChunk,
    popScale: Float,
    modifier: Modifier = Modifier
) {
    val fillColor = Color(caption.color.argb)
    val fontSizeSp = when {
        caption.text.length >= 13 -> 17.sp
        caption.text.length >= 10 -> 20.sp
        caption.isActionNote -> 21.sp
        else -> 26.sp
    }

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
            letterSpacing = 0.4.sp,
            shadow = Shadow(
                color = Color.Black.copy(alpha = 0.90f),
                offset = Offset(0f, 5f),
                blurRadius = 12f
            )
        ),
        modifier = modifier.graphicsLayer {
            scaleX = popScale
            scaleY = popScale
        }
    )
}
