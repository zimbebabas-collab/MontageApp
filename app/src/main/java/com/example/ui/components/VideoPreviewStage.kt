package com.example.ui.components

import android.media.MediaPlayer
import android.view.TextureView
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.rounded.Campaign
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import coil.compose.AsyncImage
import com.example.R
import com.example.media.SubtitleEngine
import com.example.model.CaptionChunk
import com.example.model.EditorUiState
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.MontserratFamily
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.SurfaceElevated
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.sqrt

@OptIn(UnstableApi::class)
@Composable
fun VideoPreviewStage(
    state: EditorUiState,
    onTogglePlay: () -> Unit,
    onSeekEditedMs: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val videoUri = state.videoUri

    val exoPlayer = remember(context) {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_OFF
        }
    }

    DisposableEffect(exoPlayer) {
        onDispose {
            exoPlayer.release()
        }
    }

    LaunchedEffect(videoUri) {
        if (videoUri != null) {
            exoPlayer.setMediaItem(MediaItem.fromUri(videoUri))
            exoPlayer.prepare()
            exoPlayer.playWhenReady = false
        } else {
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

    // Sync ExoPlayer with edited timeline, jump-cuts, and mid-roll ad freeze-frame pause
    val mapping = remember(state.currentEditedMs, state.clips) {
        SubtitleEngine.mapEditedMsToSource(state.currentEditedMs, state.clips)
    }
    val targetSourceMs = mapping?.sourceMs ?: 0L

    LaunchedEffect(state.isPlaying, state.isAdPauseActive, targetSourceMs) {
        if (videoUri == null) return@LaunchedEffect
        val shouldPlayMain = state.isPlaying && !state.isAdPauseActive
        if (exoPlayer.playWhenReady != shouldPlayMain) {
            exoPlayer.playWhenReady = shouldPlayMain
        }
        val drift = abs(exoPlayer.currentPosition - targetSourceMs)
        if (!shouldPlayMain || drift > 260L) {
            exoPlayer.seekTo(targetSourceMs)
        }
    }

    val transform = remember(
        targetSourceMs,
        state.currentEditedMs,
        state.totalEditedContentMs,
        state.audioPeaks,
        state.effects,
        state.isAdPauseActive
    ) {
        if (state.isAdPauseActive) {
            SubtitleEngine.FrameTransform(1f, 0f, 0f, 0f, 0f)
        } else {
            SubtitleEngine.computeFrameTransform(
                sourceMs = targetSourceMs,
                editedMs = state.currentEditedMs,
                totalEditedMs = state.totalEditedContentMs,
                peaks = state.audioPeaks,
                effects = state.effects
            )
        }
    }

    val activeCaption = remember(state.currentEditedMs, state.captions, state.captionsEnabled, state.isAdPauseActive) {
        if (state.captionsEnabled && !state.isAdPauseActive) {
            SubtitleEngine.activeCaptionAt(state.currentEditedMs, state.captions)
        } else {
            null
        }
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 9:16 Vertical Canvas Frame
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(9f / 16f)
                .clip(RoundedCornerShape(22.dp))
                .background(ObsidianBg)
                .border(
                    width = 1.5.dp,
                    brush = Brush.linearGradient(
                        listOf(
                            NeonPurple.copy(alpha = 0.75f),
                            ElectricCyan.copy(alpha = 0.75f)
                        )
                    ),
                    shape = RoundedCornerShape(22.dp)
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onTogglePlay
                )
                .testTag("video_preview_stage")
        ) {
            val stageWidthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
            val stageHeightPx = constraints.maxHeight.toFloat().coerceAtLeast(1f)

            // Video Surface with live zoom & shake transform
            AndroidView(
                factory = { ctx ->
                    TextureView(ctx).also { tv ->
                        exoPlayer.setVideoTextureView(tv)
                    }
                },
                update = { tv ->
                    exoPlayer.setVideoTextureView(tv)
                },
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = transform.scale
                        scaleY = transform.scale
                        translationX = transform.offsetXFraction * stageWidthPx
                        translationY = transform.offsetYFraction * stageHeightPx
                    }
            )

            // Subtle Chromatic Peak Flash overlay
            if (transform.flashAlpha > 0.01f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(ElectricCyan.copy(alpha = transform.flashAlpha))
                )
            }

            // Soft Vignette Canvas overlay
            if (state.effects.vignette) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawRect(
                        brush = Brush.radialGradient(
                            colorStops = arrayOf(
                                0.0f to Color.Transparent,
                                0.56f to Color.Transparent,
                                1.0f to Color.Black.copy(alpha = 0.58f)
                            ),
                            center = Offset(size.width / 2f, size.height / 2f),
                            radius = size.height * 0.64f
                        )
                    )
                }
            }

            // Top HUD chips: Mode badge + Clip counter
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .padding(10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = ObsidianBg.copy(alpha = 0.72f),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        NeonPurple.copy(alpha = 0.65f)
                    )
                ) {
                    Text(
                        text = state.editMode.titleRu,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }

                if (mapping != null) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = ObsidianBg.copy(alpha = 0.72f),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            ElectricCyan.copy(alpha = 0.6f)
                        )
                    ) {
                        Text(
                            text = "Часть ${mapping.clipIndex + 1}/${state.clips.size}",
                            style = MaterialTheme.typography.labelSmall,
                            color = ElectricCyan,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }
            }

            // Kinetic Colored Russian Subtitles (bottom-center safe area)
            if (activeCaption != null) {
                val popScale = SubtitleEngine.computePopScale(state.currentEditedMs, activeCaption)
                KineticSubtitleOverlay(
                    caption = activeCaption,
                    popScale = popScale,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 14.dp)
                        .padding(bottom = (maxHeight * 0.16f).coerceAtLeast(36.dp))
                )
            }

            // Mid-Roll Centered Ad Banner Overlay (25%..35% of screen area) over dimmed backdrop
            val showAdPreview = state.adBanner.enabled &&
                state.adBanner.imageUri != null &&
                state.isAdPauseActive

            androidx.compose.animation.AnimatedVisibility(
                visible = showAdPreview,
                enter = fadeIn() + scaleIn(initialScale = 0.88f),
                exit = fadeOut() + scaleOut(targetScale = 0.92f),
                modifier = Modifier.fillMaxSize()
            ) {
                val areaFrac = state.adBanner.areaPercent.coerceIn(25, 35) / 100f
                val bannerWidthFraction = sqrt(areaFrac * 1.15f).coerceIn(0.62f, 0.84f)
                val bannerHeightFraction = (areaFrac / bannerWidthFraction).coerceIn(0.36f, 0.56f)
                val remSec = ceil(state.adRemainingMs / 1000.0).toInt().coerceAtLeast(1)

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(ObsidianBg.copy(alpha = 0.74f))
                        .testTag("midroll_ad_overlay"),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = NeonPurple,
                            shadowElevation = 6.dp
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Campaign,
                                    contentDescription = stringResource(R.string.cd_ad_banner),
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "РЕКЛАМА • ${remSec}с",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth(bannerWidthFraction)
                                .height(this@BoxWithConstraints.maxHeight * bannerHeightFraction)
                                .clip(RoundedCornerShape(18.dp))
                                .border(2.dp, ElectricCyan, RoundedCornerShape(18.dp))
                                .background(SurfaceElevated)
                        ) {
                            AsyncImage(
                                model = state.adBanner.imageUri,
                                contentDescription = stringResource(R.string.cd_ad_banner),
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        Text(
                            text = "Видео на паузе • авто-продолжение",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            // Center Play/Pause Affordance when paused
            if (!state.isPlaying && !state.isAnalyzing) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(62.dp)
                        .clip(CircleShape)
                        .background(ObsidianBg.copy(alpha = 0.72f))
                        .border(1.5.dp, ElectricCyan, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = stringResource(R.string.cd_play_pause),
                        tint = Color.White,
                        modifier = Modifier.size(34.dp)
                    )
                }
            }

            // Analyzing spinner overlay
            if (state.isAnalyzing) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(ObsidianBg.copy(alpha = 0.82f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CircularProgressIndicator(color = ElectricCyan, strokeWidth = 3.dp)
                        Text(
                            text = "Анализ звука и авто-нарезка...",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Compact Playback Scrubber Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                onClick = onTogglePlay,
                shape = CircleShape,
                color = NeonPurple,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("play_pause_button")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = stringResource(R.string.cd_play_pause),
                        tint = Color.White,
                        modifier = Modifier.size(26.dp)
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
                        thumbColor = ElectricCyan,
                        activeTrackColor = NeonPurple,
                        inactiveTrackColor = BorderSubtle
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(30.dp)
                        .testTag("playback_slider")
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = SubtitleEngine.formatTimeMs(state.currentEditedMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = ElectricCyan
                    )
                    val adMarkerText = if (state.adBanner.enabled && state.adBanner.imageUri != null) {
                        "Реклама: ${SubtitleEngine.formatTimeMs(state.adBanner.triggerAtMs)} (+${state.adBanner.durationSec}с)"
                    } else {
                        "Частей: ${state.clips.size}"
                    }
                    Text(
                        text = adMarkerText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = SubtitleEngine.formatTimeMs(state.totalEditedContentMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun KineticSubtitleOverlay(
    caption: CaptionChunk,
    popScale: Float,
    modifier: Modifier = Modifier
) {
    val fillColor = Color(caption.color.argb)
    val baseStyle = TextStyle(
        fontFamily = MontserratFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 21.sp,
        lineHeight = 25.sp,
        textAlign = TextAlign.Center,
        letterSpacing = 0.3.sp
    )

    Box(
        modifier = modifier.graphicsLayer {
            scaleX = popScale
            scaleY = popScale
        },
        contentAlignment = Alignment.Center
    ) {
        // Thick black outline layer
        Text(
            text = caption.text,
            style = baseStyle.copy(
                color = Color.Black,
                drawStyle = Stroke(
                    width = 11f,
                    join = StrokeJoin.Round
                )
            ),
            textAlign = TextAlign.Center
        )
        // Bright kinetic colored fill layer
        Text(
            text = caption.text,
            style = baseStyle.copy(color = fillColor),
            textAlign = TextAlign.Center
        )
    }
}
