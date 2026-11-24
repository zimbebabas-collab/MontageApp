package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Face
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material.icons.rounded.ZoomIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.media.SubtitleEngine
import com.example.model.CaptionColor
import com.example.model.EditMode
import com.example.model.EditorUiState
import com.example.model.MemeConfig
import com.example.model.SubtitlePosition
import com.example.ui.theme.AccentGreen
import com.example.ui.theme.AccentYellow
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.HotCoral
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.SpeakerBlue
import com.example.ui.theme.SpeakerPink
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceElevated
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlin.math.roundToInt

@Composable
fun ModeSelectorTab(
    state: EditorUiState,
    onSelectMode: (EditMode) -> Unit,
    onUpdateMeme: ((MemeConfig) -> MemeConfig) -> Unit,
    onPickReactionClip: () -> Unit,
    onClearReactionClip: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "Режимы монтажа",
            style = MaterialTheme.typography.titleMedium,
            color = TextPrimary
        )

        EditMode.entries.forEach { mode ->
            val isSelected = mode == state.editMode
            val accent = when (mode) {
                EditMode.CLEAN_REFERENCE -> SpeakerBlue
                EditMode.LITVIN_DYNAMIC -> AccentYellow
                EditMode.MEME_INSERT -> SpeakerPink
            }
            val icon = when (mode) {
                EditMode.CLEAN_REFERENCE -> Icons.Rounded.AutoAwesome
                EditMode.LITVIN_DYNAMIC -> Icons.Rounded.Bolt
                EditMode.MEME_INSERT -> Icons.Rounded.Movie
            }

            Surface(
                onClick = { onSelectMode(mode) },
                shape = RoundedCornerShape(15.dp),
                color = if (isSelected) SurfaceCard else SurfaceElevated,
                border = BorderStroke(
                    width = if (isSelected) 1.5.dp else 1.dp,
                    color = if (isSelected) accent else BorderSubtle
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("mode_card_${mode.name.lowercase()}")
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(11.dp))
                            .background(accent.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(21.dp)
                        )
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = mode.titleRu,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = accent.copy(alpha = 0.18f)
                            ) {
                                Text(
                                    text = mode.badgeRu,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = accent,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = mode.subtitleRu,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }
            }
        }

        // Top Black Meme Bar & End Reaction Clip Controls (Always accessible when Meme mode active or toggled)
        val meme = state.meme
        if (state.editMode == EditMode.MEME_INSERT || meme.topBarEnabled || meme.reactionClipUri != null) {
            Surface(
                shape = RoundedCornerShape(15.dp),
                color = SurfaceElevated,
                border = BorderStroke(1.dp, SpeakerPink.copy(alpha = 0.45f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Чёрная плашка сверху",
                            style = MaterialTheme.typography.labelLarge,
                            color = TextPrimary
                        )
                        Switch(
                            checked = meme.topBarEnabled,
                            onCheckedChange = { enabled -> onUpdateMeme { it.copy(topBarEnabled = enabled) } },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = ObsidianBg,
                                checkedTrackColor = SpeakerPink
                            ),
                            modifier = Modifier.testTag("meme_topbar_switch")
                        )
                    }

                    if (meme.topBarEnabled) {
                        OutlinedTextField(
                            value = meme.topBarText,
                            onValueChange = { txt -> onUpdateMeme { it.copy(topBarText = txt) } },
                            singleLine = true,
                            label = { Text("Заголовок мем-плашки") },
                            placeholder = { Text("ВОСПРИНЯЛ ВСЁ БУКВАЛЬНО") },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = SpeakerPink,
                                unfocusedBorderColor = BorderSubtle,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("meme_topbar_input")
                        )
                    }

                    // Reaction / Meme Video Clip at the End
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onPickReactionClip,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, SpeakerPink.copy(alpha = 0.7f)),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("pick_reaction_clip_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.VideoLibrary,
                                contentDescription = null,
                                tint = SpeakerPink,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (meme.reactionClipUri != null) {
                                    "${meme.reactionClipName} (${SubtitleEngine.formatTimeMs(meme.reactionClipDurationMs)})"
                                } else {
                                    "Вклеить мем-клип в конец"
                                },
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                        if (meme.reactionClipUri != null) {
                            IconButton(onClick = onClearReactionClip) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Удалить мем-клип",
                                    tint = HotCoral
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SlicesTimelineTab(
    state: EditorUiState,
    onSilenceThresholdChange: (Float) -> Unit,
    onSelectClip: (String) -> Unit,
    onToggleClipCloseUp: (String) -> Unit,
    onSplitClip: (String) -> Unit,
    onDeleteClip: (String) -> Unit,
    onResetSlices: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Авто-нарезка тишины",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary
                )
                Text(
                    text = if (state.sourceDurationMs > 0L) {
                        "Было ${SubtitleEngine.formatTimeMs(state.sourceDurationMs)} → стало ${SubtitleEngine.formatTimeMs(state.totalMainContentMs)}"
                    } else {
                        "Загрузите видео для анализа громкости"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = SpeakerBlue
                )
            }
            IconButton(
                onClick = onResetSlices,
                enabled = state.videoUri != null,
                modifier = Modifier.testTag("reset_slices_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = "Сбросить нарезку",
                    tint = TextSecondary
                )
            }
        }

        // Clean RMS Audio Waveform with Threshold Line
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = ObsidianBg,
            border = BorderStroke(1.dp, BorderSubtle),
            modifier = Modifier
                .fillMaxWidth()
                .height(66.dp)
        ) {
            if (state.rmsEnvelope.isEmpty()) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "Звуковая волна появится после выбора видео",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            } else {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp)
                ) {
                    val bars = state.rmsEnvelope
                    val barStep = size.width / bars.size.coerceAtLeast(1)
                    val barWidth = (barStep * 0.62f).coerceAtLeast(2f)
                    val threshY = size.height * (1f - state.silenceThreshold)

                    bars.forEachIndexed { idx, v ->
                        val h = (v * size.height).coerceAtLeast(3f)
                        val x = idx * barStep
                        val y = size.height - h
                        val color = when {
                            v >= 0.78f -> SpeakerPink
                            v >= state.silenceThreshold -> SpeakerBlue
                            else -> BorderSubtle
                        }
                        drawRoundRect(
                            color = color,
                            topLeft = Offset(x, y),
                            size = Size(barWidth, h),
                            cornerRadius = CornerRadius(3f, 3f)
                        )
                    }
                    drawLine(
                        color = AccentYellow.copy(alpha = 0.85f),
                        start = Offset(0f, threshY),
                        end = Offset(size.width, threshY),
                        strokeWidth = 2f
                    )
                }
            }
        }

        // Silence Threshold Slider
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Порог вырезки пауз",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary
                )
                Text(
                    text = "${(state.silenceThreshold * 100).roundToInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = AccentYellow
                )
            }
            Slider(
                value = state.silenceThreshold,
                onValueChange = onSilenceThresholdChange,
                valueRange = 0.03f..0.42f,
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = SpeakerBlue,
                    inactiveTrackColor = BorderSubtle
                ),
                modifier = Modifier.testTag("silence_threshold_slider")
            )
        }

        // Interactive Clips List with 1.0x / 1.35x Framing Toggle
        if (state.clips.isNotEmpty()) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 2.dp)
            ) {
                itemsIndexed(state.clips, key = { _, c -> c.id }) { idx, clip ->
                    val selected = clip.id == state.selectedClipId
                    val closeUpLabel = if (clip.isCloseUp) {
                        String.format("%.2fx", state.framing.closeUpZoom)
                    } else {
                        "1.0x"
                    }

                    Surface(
                        onClick = { onSelectClip(clip.id) },
                        shape = RoundedCornerShape(14.dp),
                        color = if (selected) SurfaceCard else SurfaceElevated,
                        border = BorderStroke(
                            width = if (selected) 1.5.dp else 1.dp,
                            color = if (selected) SpeakerBlue else BorderSubtle
                        ),
                        modifier = Modifier
                            .width(154.dp)
                            .testTag("clip_card_$idx")
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = clip.label.ifBlank { "Часть ${idx + 1}" },
                                    style = MaterialTheme.typography.labelLarge,
                                    color = TextPrimary
                                )
                                // Tap to toggle 1.0x <-> 1.35x close-up framing per clip!
                                Surface(
                                    onClick = { onToggleClipCloseUp(clip.id) },
                                    shape = RoundedCornerShape(50),
                                    color = if (clip.isCloseUp) SpeakerBlue.copy(alpha = 0.2f) else ObsidianBg,
                                    border = BorderStroke(
                                        1.dp,
                                        if (clip.isCloseUp) SpeakerBlue else BorderSubtle
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.ZoomIn,
                                            contentDescription = "Смена крупности",
                                            tint = if (clip.isCloseUp) SpeakerBlue else TextSecondary,
                                            modifier = Modifier.size(11.dp)
                                        )
                                        Text(
                                            text = closeUpLabel,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (clip.isCloseUp) SpeakerBlue else TextSecondary
                                        )
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${SubtitleEngine.formatTimeMs(clip.startMs)} – ${SubtitleEngine.formatTimeMs(clip.endMs)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary
                                )
                                Icon(
                                    imageVector = if (clip.hasDetectedFace) Icons.Rounded.Face else Icons.Rounded.CenterFocusStrong,
                                    contentDescription = if (clip.hasDetectedFace) "Лицо найдено" else "Ключевая точка",
                                    tint = if (clip.hasDetectedFace) AccentGreen else SpeakerBlue.copy(alpha = 0.7f),
                                    modifier = Modifier.size(14.dp)
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${(clip.durationMs / 1000f).let { String.format("%.1fс", it) }} • ${if (clip.hasDetectedFace) "Лицо" else "Точка"}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (clip.hasDetectedFace) AccentGreen else SpeakerPink
                                )
                                Row {
                                    IconButton(
                                        onClick = { onSplitClip(clip.id) },
                                        enabled = clip.durationMs >= 700L,
                                        modifier = Modifier.size(34.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.ContentCut,
                                            contentDescription = stringResource(R.string.cd_split_clip),
                                            tint = SpeakerBlue,
                                            modifier = Modifier.size(17.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = { onDeleteClip(clip.id) },
                                        enabled = state.clips.size > 1,
                                        modifier = Modifier.size(34.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.DeleteOutline,
                                            contentDescription = stringResource(R.string.cd_delete_clip),
                                            tint = if (state.clips.size > 1) HotCoral else BorderSubtle,
                                            modifier = Modifier.size(17.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CaptionsEditorTab(
    state: EditorUiState,
    onToggleCaptions: (Boolean) -> Unit,
    onSubtitlePositionChange: (SubtitlePosition) -> Unit,
    onAutoTranscribeVideo: () -> Unit,
    onTranscriptChange: (String) -> Unit,
    onStartStopVoice: () -> Unit,
    onCycleChunkColor: (String) -> Unit,
    onSeekToWord: (Long) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Яркие разноцветные субтитры",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary
                )
                Text(
                    text = "Насыщенные буквы с неоновым свечением • *слово* — белый акцент",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
            Switch(
                checked = state.captionsEnabled,
                onCheckedChange = onToggleCaptions,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = ObsidianBg,
                    checkedTrackColor = SpeakerBlue
                ),
                modifier = Modifier.testTag("captions_toggle_switch")
            )
        }

        // Subtitle Position Toggle: Bottom (default) vs Center
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Позиция:",
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondary
            )
            SubtitlePosition.entries.forEach { pos ->
                FilterChip(
                    selected = state.subtitlePosition == pos,
                    onClick = { onSubtitlePositionChange(pos) },
                    label = { Text(pos.labelRu) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = SpeakerBlue.copy(alpha = 0.24f),
                        selectedLabelColor = SpeakerBlue,
                        labelColor = TextSecondary
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("subtitle_pos_${pos.name.lowercase()}")
                )
            }
        }

        // Primary Auto-Transcribe Russian Voice directly from uploaded video
        Button(
            onClick = onAutoTranscribeVideo,
            enabled = state.videoUri != null && !state.isTranscribingVideo,
            shape = RoundedCornerShape(13.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = SpeakerBlue,
                contentColor = ObsidianBg
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("auto_transcribe_video_button")
        ) {
            if (state.isTranscribingVideo) {
                CircularProgressIndicator(
                    color = ObsidianBg,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Распознаём голос из видео...", style = MaterialTheme.typography.labelLarge)
            } else {
                Icon(
                    imageVector = Icons.Rounded.GraphicEq,
                    contentDescription = null,
                    modifier = Modifier.size(19.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Авто-распознать голос из видео", style = MaterialTheme.typography.labelLarge)
            }
        }

        // Secondary Mic Dictation Fallback (ru-RU)
        OutlinedButton(
            onClick = onStartStopVoice,
            shape = RoundedCornerShape(13.dp),
            border = BorderStroke(
                1.dp,
                if (state.isListeningSpeech) HotCoral else SpeakerPink.copy(alpha = 0.75f)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp)
                .testTag("voice_caption_button")
        ) {
            Icon(
                imageVector = if (state.isListeningSpeech) Icons.Rounded.Stop else Icons.Rounded.Mic,
                contentDescription = stringResource(R.string.cd_voice_dictate),
                tint = if (state.isListeningSpeech) HotCoral else SpeakerPink,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (state.isListeningSpeech) {
                    "Остановить диктовку (ru-RU)"
                } else {
                    "Надиктовать через микрофон (ru-RU)"
                },
                color = if (state.isListeningSpeech) HotCoral else SpeakerPink,
                style = MaterialTheme.typography.labelLarge
            )
        }

        // Vibrant 6-Color Palette Legend
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            CaptionColor.entries.forEach { c ->
                SpeakerLegendPill(c.labelRu, Color(c.argb))
            }
        }

        if (state.speechPartialText.isNotBlank()) {
            Text(
                text = "Распознаётся: «${state.speechPartialText}»",
                style = MaterialTheme.typography.bodySmall,
                color = SpeakerBlue
            )
        } else if (!state.speechStatusMessage.isNullOrBlank()) {
            Text(
                text = state.speechStatusMessage,
                style = MaterialTheme.typography.bodySmall,
                color = AccentYellow
            )
        }

        // Clean Transcript Text Editor -> instant vibrant word splitter
        OutlinedTextField(
            value = state.rawTranscriptText,
            onValueChange = onTranscriptChange,
            label = { Text("Текст субтитров (каждое слово — яркий цвет)") },
            placeholder = {
                Text("Ты серьёзно это сделал?\nДа, это просто пушка *СМЕЁТСЯ*")
            },
            minLines = 2,
            maxLines = 4,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = SpeakerBlue,
                unfocusedBorderColor = BorderSubtle,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("caption_transcript_input")
        )

        // Interactive Tappable 1-Word Color Tagging Chips
        if (state.captions.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Нажмите на слово для смены цвета (${state.captions.size}):",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                state.captions.forEach { chunk ->
                    val chipColor = Color(chunk.color.argb)
                    val isCurrent = state.currentEditedMs in chunk.startMs until chunk.endMs
                    Surface(
                        onClick = {
                            onCycleChunkColor(chunk.id)
                            onSeekToWord(chunk.startMs + 20L)
                        },
                        shape = RoundedCornerShape(10.dp),
                        color = if (isCurrent) chipColor.copy(alpha = 0.24f) else SurfaceElevated,
                        border = BorderStroke(
                            width = if (isCurrent) 1.5.dp else 1.dp,
                            color = chipColor.copy(alpha = if (isCurrent) 1f else 0.55f)
                        ),
                        modifier = Modifier.testTag("word_chip_${chunk.id}")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(chipColor)
                            )
                            Text(
                                text = chunk.text,
                                style = MaterialTheme.typography.labelSmall,
                                color = chipColor
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SpeakerLegendPill(label: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(50),
        color = SurfaceElevated,
        border = BorderStroke(1.dp, color.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = color
            )
        }
    }
}
