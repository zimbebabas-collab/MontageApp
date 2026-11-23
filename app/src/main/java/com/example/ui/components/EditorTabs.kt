package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.media.SubtitleEngine
import com.example.model.EditMode
import com.example.model.EditorUiState
import com.example.model.EffectsConfig
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.ElectricCyanContainer
import com.example.ui.theme.HotCoral
import com.example.ui.theme.NeonGreen
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.NeonPurpleContainer
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceElevated
import kotlin.math.roundToInt

@Composable
fun ModeSelectorTab(
    selectedMode: EditMode,
    onSelectMode: (EditMode) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "Режимы авто-монтажа",
            style = MaterialTheme.typography.titleMedium,
            color = Color.White
        )
        EditMode.entries.forEach { mode ->
            val isSelected = mode == selectedMode
            val accent = when (mode) {
                EditMode.LITVIN -> NeonPurple
                EditMode.MEME -> ElectricCyan
                EditMode.LIGHT -> NeonGreen
            }
            Surface(
                onClick = { onSelectMode(mode) },
                shape = RoundedCornerShape(16.dp),
                color = if (isSelected) SurfaceCard else SurfaceElevated,
                border = BorderStroke(
                    width = if (isSelected) 1.8.dp else 1.dp,
                    color = if (isSelected) accent else BorderSubtle
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("mode_card_${mode.name.lowercase()}")
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(accent.copy(alpha = 0.16f))
                            .border(1.dp, accent.copy(alpha = 0.55f), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when (mode) {
                                EditMode.LITVIN -> Icons.Rounded.Bolt
                                EditMode.MEME -> Icons.Rounded.AutoAwesome
                                EditMode.LIGHT -> Icons.Rounded.GraphicEq
                            },
                            contentDescription = mode.titleRu,
                            tint = accent,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = mode.titleRu,
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White
                            )
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = accent.copy(alpha = 0.2f)
                            ) {
                                Text(
                                    text = mode.badgeRu,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = accent,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = mode.subtitleRu,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
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
    onSplitClip: (String) -> Unit,
    onDeleteClip: (String) -> Unit,
    onResetSlices: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Авто-нарезка и таймлайн",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White
                )
                val savedMs = (state.sourceDurationMs - state.totalEditedContentMs).coerceAtLeast(0L)
                Text(
                    text = "Вырезано тишины: ${SubtitleEngine.formatTimeMs(savedMs)} • Пиков: ${state.audioPeaks.size}",
                    style = MaterialTheme.typography.bodySmall,
                    color = ElectricCyan
                )
            }
            OutlinedButton(
                onClick = onResetSlices,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                border = BorderStroke(1.dp, BorderSubtle),
                modifier = Modifier.testTag("reset_slices_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = "Сбросить нарезку",
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Авто", style = MaterialTheme.typography.labelMedium)
            }
        }

        // RMS Waveform visualization + Silence Threshold line
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = ObsidianBg,
            border = BorderStroke(1.dp, BorderSubtle),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(58.dp)
                ) {
                    val bins = state.rmsEnvelope
                    if (bins.isNotEmpty()) {
                        val barW = (size.width / bins.size.toFloat()).coerceAtLeast(2f)
                        val gap = (barW * 0.28f).coerceAtMost(2.5f)
                        bins.forEachIndexed { idx, value ->
                            val barH = (value * size.height).coerceAtLeast(3f)
                            val isActive = value >= state.silenceThreshold
                            drawRoundRect(
                                color = if (isActive) ElectricCyan else BorderSubtle,
                                topLeft = Offset(idx * barW, size.height - barH),
                                size = Size((barW - gap).coerceAtLeast(1.5f), barH),
                                cornerRadius = CornerRadius(4f, 4f)
                            )
                        }
                        // Silence threshold line
                        val threshY = size.height * (1f - state.silenceThreshold.coerceIn(0f, 1f))
                        drawLine(
                            color = NeonPurple,
                            start = Offset(0f, threshY),
                            end = Offset(size.width, threshY),
                            strokeWidth = 3f
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Порог тишины (RMS)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${(state.silenceThreshold * 100).roundToInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = NeonPurple
                    )
                }
                Slider(
                    value = state.silenceThreshold,
                    onValueChange = onSilenceThresholdChange,
                    valueRange = 0.04f..0.42f,
                    colors = SliderDefaults.colors(
                        thumbColor = NeonPurple,
                        activeTrackColor = NeonPurple,
                        inactiveTrackColor = BorderSubtle
                    ),
                    modifier = Modifier.testTag("silence_threshold_slider")
                )
            }
        }

        // Interactive Horizontal Clip Cards (Preview, Split, Delete)
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("clips_timeline_row")
        ) {
            itemsIndexed(state.clips, key = { _, c -> c.id }) { idx, clip ->
                val isSelected = clip.id == state.selectedClipId
                Surface(
                    onClick = { onSelectClip(clip.id) },
                    shape = RoundedCornerShape(14.dp),
                    color = if (isSelected) NeonPurpleContainer else SurfaceElevated,
                    border = BorderStroke(
                        width = if (isSelected) 1.8.dp else 1.dp,
                        color = if (isSelected) ElectricCyan else BorderSubtle
                    ),
                    modifier = Modifier
                        .width(172.dp)
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
                                text = clip.label.ifEmpty { "Часть ${idx + 1}" },
                                style = MaterialTheme.typography.labelLarge,
                                color = Color.White
                            )
                            Text(
                                text = "${String.format("%.1f", clip.durationMs / 1000f)}с",
                                style = MaterialTheme.typography.labelSmall,
                                color = ElectricCyan
                            )
                        }
                        Text(
                            text = "${SubtitleEngine.formatTimeMs(clip.startMs)} – ${SubtitleEngine.formatTimeMs(clip.endMs)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            FilledTonalButton(
                                onClick = { onSplitClip(clip.id) },
                                enabled = clip.durationMs >= 700L,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = SurfaceCard,
                                    contentColor = ElectricCyan
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(40.dp)
                                    .testTag("split_clip_$idx")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.ContentCut,
                                    contentDescription = stringResource(R.string.cd_split_clip),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Резать", style = MaterialTheme.typography.labelSmall)
                            }
                            IconButton(
                                onClick = { onDeleteClip(clip.id) },
                                enabled = state.clips.size > 1,
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(HotCoral.copy(alpha = if (state.clips.size > 1) 0.16f else 0.05f))
                                    .testTag("delete_clip_$idx")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.DeleteOutline,
                                    contentDescription = stringResource(R.string.cd_delete_clip),
                                    tint = if (state.clips.size > 1) HotCoral else BorderSubtle,
                                    modifier = Modifier.size(18.dp)
                                )
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
    onTranscriptChange: (String) -> Unit,
    onStartStopVoice: () -> Unit,
    onCycleChunkColor: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Цветные субтитры снизу",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White
                )
                Text(
                    text = "2–3 слова с pop-in анимацией, чёрной обводкой и чередованием цветов",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = state.captionsEnabled,
                onCheckedChange = onToggleCaptions,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = ObsidianBg,
                    checkedTrackColor = ElectricCyan
                ),
                modifier = Modifier.testTag("captions_enable_switch")
            )
        }

        // Russian Speech Recognition Button (ru-RU)
        Button(
            onClick = onStartStopVoice,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (state.isListeningSpeech) HotCoral else NeonPurple,
                contentColor = Color.White
            ),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("voice_caption_button")
        ) {
            Icon(
                imageVector = if (state.isListeningSpeech) Icons.Rounded.Stop else Icons.Rounded.Mic,
                contentDescription = stringResource(R.string.cd_voice_dictate),
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (state.isListeningSpeech) {
                    "Остановить запись речи (ru-RU)"
                } else {
                    "Надиктовать субтитры голосом (ru-RU)"
                },
                style = MaterialTheme.typography.labelLarge
            )
        }

        if (state.speechPartialText.isNotBlank()) {
            Text(
                text = "Слышу: ${state.speechPartialText}",
                style = MaterialTheme.typography.bodySmall,
                color = ElectricCyan
            )
        }
        state.speechStatusMessage?.let { msg ->
            Text(
                text = msg,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Manual Russian Text Editor Fallback
        OutlinedTextField(
            value = state.rawTranscriptText,
            onValueChange = onTranscriptChange,
            label = { Text("Текст субтитров (авто-разбивка по 2–3 слова)") },
            placeholder = {
                Text("Например: Короче пацаны смотрите что сегодня произошло это просто жесть...")
            },
            minLines = 3,
            maxLines = 5,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = ElectricCyan,
                unfocusedBorderColor = BorderSubtle,
                focusedContainerColor = ObsidianBg,
                unfocusedContainerColor = ObsidianBg,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("caption_text_input")
        )

        if (state.captions.isNotEmpty()) {
            Text(
                text = "Нажмите на фразу, чтобы сменить её цвет:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                state.captions.forEachIndexed { idx, chunk ->
                    val chunkColor = Color(chunk.color.argb)
                    Surface(
                        onClick = { onCycleChunkColor(chunk.id) },
                        shape = RoundedCornerShape(10.dp),
                        color = ObsidianBg,
                        border = BorderStroke(1.2.dp, chunkColor.copy(alpha = 0.8f)),
                        modifier = Modifier.testTag("caption_chip_$idx")
                    ) {
                        Text(
                            text = chunk.text,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold),
                            color = chunkColor,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun EffectsEditorTab(
    effects: EffectsConfig,
    onUpdateEffects: ((EffectsConfig) -> EffectsConfig) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "Лёгкие визуальные эффекты",
            style = MaterialTheme.typography.titleMedium,
            color = Color.White
        )

        EffectToggleRow(
            title = "Микро-зум на пиках голоса",
            subtitle = "Резкий punch-in зум в стиле Литвина на громких фразах",
            checked = effects.microZoomOnPeak,
            testTag = "effect_micro_zoom",
            onCheckedChange = { v -> onUpdateEffects { it.copy(microZoomOnPeak = v) } }
        )

        EffectToggleRow(
            title = "Тряска камеры на пиках",
            subtitle = "Динамичный мемный шейк при громких выкриках",
            checked = effects.cameraShakeOnPeak,
            testTag = "effect_camera_shake",
            onCheckedChange = { v -> onUpdateEffects { it.copy(cameraShakeOnPeak = v) } }
        )

        EffectToggleRow(
            title = "Мягкая виньетка",
            subtitle = "Кинематографичное затемнение по краям кадра",
            checked = effects.vignette,
            testTag = "effect_vignette",
            onCheckedChange = { v -> onUpdateEffects { it.copy(vignette = v) } }
        )

        EffectToggleRow(
            title = "Неоновая вспышка / хроматика",
            subtitle = "Лёгкая циановая вспышка на самых громких пиках",
            checked = effects.chromaticFlash,
            testTag = "effect_chromatic_flash",
            onCheckedChange = { v -> onUpdateEffects { it.copy(chromaticFlash = v) } }
        )

        EffectToggleRow(
            title = "Плавный разговорный зум",
            subtitle = "Постепенное приближение кадра (для Лайт-режима)",
            checked = effects.slowZoom,
            testTag = "effect_slow_zoom",
            onCheckedChange = { v -> onUpdateEffects { it.copy(slowZoom = v) } }
        )

        Surface(
            shape = RoundedCornerShape(14.dp),
            color = SurfaceElevated,
            border = BorderStroke(1.dp, BorderSubtle),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Сила приближения (Zoom)",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White
                    )
                    Text(
                        text = "+${(effects.zoomStrength * 100).roundToInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = ElectricCyan
                    )
                }
                Slider(
                    value = effects.zoomStrength,
                    onValueChange = { v -> onUpdateEffects { it.copy(zoomStrength = v) } },
                    valueRange = 0.04f..0.24f,
                    colors = SliderDefaults.colors(
                        thumbColor = ElectricCyan,
                        activeTrackColor = NeonPurple,
                        inactiveTrackColor = BorderSubtle
                    ),
                    modifier = Modifier.testTag("effect_zoom_slider")
                )
            }
        }
    }
}

@Composable
private fun EffectToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    testTag: String,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = SurfaceElevated,
        border = BorderStroke(1.dp, if (checked) NeonPurple.copy(alpha = 0.6f) else BorderSubtle),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.titleSmall, color = Color.White)
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = ObsidianBg,
                    checkedTrackColor = ElectricCyan
                )
            )
        }
    }
}

@Composable
fun AdBannerEditorTab(
    state: EditorUiState,
    onSetEnabled: (Boolean) -> Unit,
    onPickAdImage: () -> Unit,
    onClearAdImage: () -> Unit,
    onPickAdAudio: () -> Unit,
    onClearAdAudio: () -> Unit,
    onTriggerChange: (Long) -> Unit,
    onDurationChange: (Int) -> Unit,
    onAreaPercentChange: (Int) -> Unit,
    onUseTtsChange: (Boolean) -> Unit,
    onTtsTextChange: (String) -> Unit,
    onPreviewTts: () -> Unit
) {
    val ad = state.adBanner

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Рекламный баннер с паузой",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White
                )
                Text(
                    text = "Стоп-кадр видео, баннер по центру (25–35% экрана) и авто-возврат",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = ad.enabled,
                onCheckedChange = onSetEnabled,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = ObsidianBg,
                    checkedTrackColor = ElectricCyan
                ),
                modifier = Modifier.testTag("ad_enable_switch")
            )
        }

        // Upload Banner Image Card
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = SurfaceElevated,
            border = BorderStroke(1.dp, if (ad.imageUri != null) ElectricCyan else BorderSubtle),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(ObsidianBg)
                        .border(1.dp, BorderSubtle, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (ad.imageUri != null) {
                        AsyncImage(
                            model = ad.imageUri,
                            contentDescription = stringResource(R.string.cd_ad_banner),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.Image,
                            contentDescription = "Выбрать баннер",
                            tint = NeonPurple,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (ad.imageUri != null) "Баннер загружен (PNG/JPG)" else "Загрузите картинку баннера",
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White
                    )
                    Text(
                        text = "Будет показан строго по центру с затемнением фона",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (ad.imageUri != null) {
                    IconButton(
                        onClick = onClearAdImage,
                        modifier = Modifier.testTag("clear_ad_image_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Удалить баннер",
                            tint = HotCoral
                        )
                    }
                }
            }
        }

        Button(
            onClick = onPickAdImage,
            colors = ButtonDefaults.buttonColors(
                containerColor = NeonPurple,
                contentColor = Color.White
            ),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("pick_ad_image_button")
        ) {
            Icon(Icons.Rounded.Campaign, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (ad.imageUri != null) "Заменить картинку баннера" else "Выбрать баннер (PNG / JPG)",
                style = MaterialTheme.typography.labelLarge
            )
        }

        // Timestamp, Pause Duration (3-8s), Banner Area Size (25-35%)
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = SurfaceElevated,
            border = BorderStroke(1.dp, BorderSubtle),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val maxTrigger = state.totalEditedContentMs.coerceAtLeast(1000L)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Таймкод вставки", style = MaterialTheme.typography.bodySmall, color = Color.White)
                    Text(
                        SubtitleEngine.formatTimeMs(ad.triggerAtMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = ElectricCyan
                    )
                }
                Slider(
                    value = ad.triggerAtMs.toFloat().coerceIn(200f, maxTrigger.toFloat()),
                    onValueChange = { onTriggerChange(it.toLong()) },
                    valueRange = 200f..maxTrigger.toFloat(),
                    colors = SliderDefaults.colors(
                        thumbColor = ElectricCyan,
                        activeTrackColor = NeonPurple
                    ),
                    modifier = Modifier.testTag("ad_trigger_slider")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Длительность паузы", style = MaterialTheme.typography.bodySmall, color = Color.White)
                    Text("${ad.durationSec} сек", style = MaterialTheme.typography.labelSmall, color = NeonPurple)
                }
                Slider(
                    value = ad.durationSec.toFloat(),
                    onValueChange = { onDurationChange(it.roundToInt()) },
                    valueRange = 3f..8f,
                    steps = 4,
                    colors = SliderDefaults.colors(
                        thumbColor = NeonPurple,
                        activeTrackColor = NeonPurple
                    ),
                    modifier = Modifier.testTag("ad_duration_slider")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Площадь баннера на экране", style = MaterialTheme.typography.bodySmall, color = Color.White)
                    Text("${ad.areaPercent}% экрана", style = MaterialTheme.typography.labelSmall, color = NeonGreen)
                }
                Slider(
                    value = ad.areaPercent.toFloat(),
                    onValueChange = { onAreaPercentChange(it.roundToInt()) },
                    valueRange = 25f..35f,
                    steps = 9,
                    colors = SliderDefaults.colors(
                        thumbColor = NeonGreen,
                        activeTrackColor = ElectricCyan
                    ),
                    modifier = Modifier.testTag("ad_area_slider")
                )
            }
        }

        // Ad Voiceover: Audio File OR Russian TTS
        Text(
            text = "Озвучка рекламы (необязательно)",
            style = MaterialTheme.typography.titleSmall,
            color = Color.White
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = !ad.useTts,
                onClick = { onUseTtsChange(false) },
                label = { Text("Аудиофайл") },
                leadingIcon = { Icon(Icons.Rounded.AudioFile, null, modifier = Modifier.size(16.dp)) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = NeonPurpleContainer,
                    selectedLabelColor = Color.White
                ),
                modifier = Modifier.testTag("ad_audio_mode_file")
            )
            FilterChip(
                selected = ad.useTts,
                onClick = { onUseTtsChange(true) },
                label = { Text("Озвучка TTS (RU)") },
                leadingIcon = { Icon(Icons.Rounded.RecordVoiceOver, null, modifier = Modifier.size(16.dp)) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = ElectricCyanContainer,
                    selectedLabelColor = ElectricCyan
                ),
                modifier = Modifier.testTag("ad_audio_mode_tts")
            )
        }

        if (!ad.useTts) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onPickAdAudio,
                    border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.7f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("pick_ad_audio_button")
                ) {
                    Icon(Icons.Rounded.AudioFile, null, tint = ElectricCyan, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = ad.audioDisplayName.ifBlank { "Загрузить аудио (MP3/WAV)" },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = Color.White
                    )
                }
                if (ad.audioUri != null) {
                    IconButton(onClick = onClearAdAudio) {
                        Icon(Icons.Rounded.Close, contentDescription = "Убрать аудио", tint = HotCoral)
                    }
                }
            }
        } else {
            OutlinedTextField(
                value = ad.ttsText,
                onValueChange = onTtsTextChange,
                label = { Text("Текст голосовой рекламы (TTS)") },
                placeholder = { Text("Переходи по ссылке в профиле и забирай бонус!") },
                maxLines = 3,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ElectricCyan,
                    unfocusedBorderColor = BorderSubtle,
                    focusedContainerColor = ObsidianBg,
                    unfocusedContainerColor = ObsidianBg,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("ad_tts_input")
            )
            OutlinedButton(
                onClick = onPreviewTts,
                enabled = ad.ttsText.isNotBlank(),
                border = BorderStroke(1.dp, ElectricCyan),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .testTag("preview_tts_button")
            ) {
                Icon(Icons.Rounded.VolumeUp, null, tint = ElectricCyan, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Прослушать озвучку TTS", color = ElectricCyan)
            }
        }
    }
}
