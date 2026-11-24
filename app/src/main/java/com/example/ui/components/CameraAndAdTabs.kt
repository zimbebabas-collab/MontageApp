package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.media.SubtitleEngine
import com.example.model.EditorUiState
import com.example.model.FramingConfig
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
fun CameraFramingTab(
    framing: FramingConfig,
    onUpdateFraming: ((FramingConfig) -> FramingConfig) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "Умная камера и смена планов",
            style = MaterialTheme.typography.titleMedium,
            color = TextPrimary
        )

        ToggleOptionCard(
            title = "Авто-наезды 1.0x ↔ 1.35x",
            subtitle = "Смена крупности на склейках и громких фразах (как мультикамера в Shorts)",
            checked = framing.autoCloseUpCuts,
            accent = SpeakerBlue,
            tag = "toggle_auto_closeup",
            onCheckedChange = { v -> onUpdateFraming { it.copy(autoCloseUpCuts = v) } }
        )

        // Close-up Zoom Level + X/Y Focal Point
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = SurfaceElevated,
            border = BorderStroke(1.dp, BorderSubtle),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Крупный план (зум)",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextPrimary
                    )
                    Text(
                        text = String.format("%.2fx", framing.closeUpZoom),
                        style = MaterialTheme.typography.labelSmall,
                        color = SpeakerBlue
                    )
                }
                Slider(
                    value = framing.closeUpZoom,
                    onValueChange = { v -> onUpdateFraming { it.copy(closeUpZoom = v) } },
                    valueRange = 1.15f..1.60f,
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = SpeakerBlue,
                        inactiveTrackColor = BorderSubtle
                    )
                )

                Text(
                    text = "Фокус лица (можно двигать пальцем прямо на видео)",
                    style = MaterialTheme.typography.bodySmall,
                    color = SpeakerBlue
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Фокус по X (влево / вправо)",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                    Text(
                        text = "${(framing.focalX * 100).roundToInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextPrimary
                    )
                }
                Slider(
                    value = framing.focalX,
                    onValueChange = { x -> onUpdateFraming { it.copy(focalX = x) } },
                    valueRange = 0.15f..0.85f,
                    colors = SliderDefaults.colors(
                        thumbColor = SpeakerPink,
                        activeTrackColor = SpeakerPink,
                        inactiveTrackColor = BorderSubtle
                    )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Фокус по Y (вверх / вниз)",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                    Text(
                        text = "${(framing.focalY * 100).roundToInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextPrimary
                    )
                }
                Slider(
                    value = framing.focalY,
                    onValueChange = { y -> onUpdateFraming { it.copy(focalY = y) } },
                    valueRange = 0.15f..0.85f,
                    colors = SliderDefaults.colors(
                        thumbColor = SpeakerPink,
                        activeTrackColor = SpeakerPink,
                        inactiveTrackColor = BorderSubtle
                    )
                )
            }
        }

        ToggleOptionCard(
            title = "Лёгкий акцент на пиках голоса",
            subtitle = "Небольшой дополнительный зум на самых эмоциональных словах",
            checked = framing.peakPunchEnabled,
            accent = SpeakerPink,
            tag = "toggle_peak_punch",
            onCheckedChange = { v -> onUpdateFraming { it.copy(peakPunchEnabled = v) } }
        )

        ToggleOptionCard(
            title = "Мягкая виньетка по краям",
            subtitle = "Лёгкое затемнение периферии кадра для акцента на центре",
            checked = framing.vignette,
            accent = AccentYellow,
            tag = "toggle_vignette",
            onCheckedChange = { v -> onUpdateFraming { it.copy(vignette = v) } }
        )
    }
}

@Composable
private fun ToggleOptionCard(
    title: String,
    subtitle: String,
    checked: Boolean,
    accent: Color,
    tag: String,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = SurfaceElevated,
        border = BorderStroke(1.dp, if (checked) accent.copy(alpha = 0.55f) else BorderSubtle),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = TextPrimary
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = ObsidianBg,
                    checkedTrackColor = accent
                ),
                modifier = Modifier.testTag(tag)
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
    onPreviewTts: () -> Unit,
    onTestAdPause: () -> Unit
) {
    val ad = state.adBanner

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Рекламный баннер с паузой",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary
                )
                Text(
                    text = "Стоп-кадр + баннер по центру (25–35% экрана) + авто-продолжение",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
            Switch(
                checked = ad.enabled,
                onCheckedChange = onSetEnabled,
                enabled = ad.imageUri != null,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = ObsidianBg,
                    checkedTrackColor = SpeakerPink
                ),
                modifier = Modifier.testTag("ad_enabled_switch")
            )
        }

        // Upload Banner Image Card
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = SurfaceElevated,
            border = BorderStroke(1.dp, if (ad.imageUri != null) SpeakerPink.copy(alpha = 0.5f) else BorderSubtle),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (ad.imageUri != null) {
                    AsyncImage(
                        model = ad.imageUri,
                        contentDescription = stringResource(R.string.cd_ad_banner),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(10.dp))
                    )
                } else {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = SurfaceCard,
                        modifier = Modifier.size(56.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Icon(
                                imageVector = Icons.Rounded.Image,
                                contentDescription = null,
                                tint = SpeakerPink,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (ad.imageUri != null) "Баннер загружен" else "Картинка баннера (PNG/JPG)",
                        style = MaterialTheme.typography.labelLarge,
                        color = TextPrimary
                    )
                    Text(
                        text = "По центру экрана • затемнение 45%",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }

                OutlinedButton(
                    onClick = onPickAdImage,
                    shape = RoundedCornerShape(11.dp),
                    border = BorderStroke(1.dp, SpeakerBlue),
                    modifier = Modifier.testTag("pick_ad_image_button")
                ) {
                    Text(if (ad.imageUri != null) "Сменить" else "Загрузить", color = SpeakerBlue)
                }

                if (ad.imageUri != null) {
                    IconButton(onClick = onClearAdImage) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Удалить баннер",
                            tint = HotCoral
                        )
                    }
                }
            }
        }

        // Timestamp & Size (25%..35%) & Duration (3..8s)
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = SurfaceElevated,
            border = BorderStroke(1.dp, BorderSubtle),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val maxTrigger = state.totalEditedContentMs.coerceAtLeast(1000L)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Момент паузы", style = MaterialTheme.typography.labelMedium, color = TextPrimary)
                    Text(
                        text = SubtitleEngine.formatTimeMs(ad.triggerAtMs.coerceIn(0L, maxTrigger)),
                        style = MaterialTheme.typography.labelSmall,
                        color = SpeakerBlue
                    )
                }
                Slider(
                    value = ad.triggerAtMs.toFloat().coerceIn(0f, maxTrigger.toFloat()),
                    onValueChange = { onTriggerChange(it.toLong()) },
                    valueRange = 0f..maxTrigger.toFloat(),
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = SpeakerBlue,
                        inactiveTrackColor = BorderSubtle
                    )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Площадь баннера (25–35%)", style = MaterialTheme.typography.labelMedium, color = TextPrimary)
                    Text(
                        text = "${ad.areaPercent}% экрана",
                        style = MaterialTheme.typography.labelSmall,
                        color = SpeakerPink
                    )
                }
                Slider(
                    value = ad.areaPercent.toFloat(),
                    onValueChange = { onAreaPercentChange(it.roundToInt()) },
                    valueRange = 25f..35f,
                    steps = 9,
                    colors = SliderDefaults.colors(
                        thumbColor = SpeakerPink,
                        activeTrackColor = SpeakerPink,
                        inactiveTrackColor = BorderSubtle
                    )
                )

                Text("Длительность паузы", style = MaterialTheme.typography.labelMedium, color = TextPrimary)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(3, 4, 5, 6, 8).forEach { sec ->
                        FilterChip(
                            selected = ad.durationSec == sec,
                            onClick = { onDurationChange(sec) },
                            label = { Text("${sec}с") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = SpeakerPink.copy(alpha = 0.24f),
                                selectedLabelColor = SpeakerPink,
                                labelColor = TextSecondary
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // Ad Voiceover: Custom Audio File OR Built-in Russian TTS
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = SurfaceElevated,
            border = BorderStroke(1.dp, BorderSubtle),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Озвучка рекламной паузы", style = MaterialTheme.typography.labelLarge, color = TextPrimary)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = !ad.useTts,
                        onClick = { onUseTtsChange(false) },
                        leadingIcon = {
                            Icon(Icons.Rounded.AudioFile, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        label = { Text("Аудиофайл") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = SpeakerBlue.copy(alpha = 0.22f),
                            selectedLabelColor = SpeakerBlue
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = ad.useTts,
                        onClick = { onUseTtsChange(true) },
                        leadingIcon = {
                            Icon(Icons.Rounded.RecordVoiceOver, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        label = { Text("Озвучка TTS") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = SpeakerPink.copy(alpha = 0.22f),
                            selectedLabelColor = SpeakerPink
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }

                if (!ad.useTts) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onPickAdAudio,
                            shape = RoundedCornerShape(11.dp),
                            border = BorderStroke(1.dp, SpeakerBlue),
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .testTag("pick_ad_audio_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AudioFile,
                                contentDescription = null,
                                tint = SpeakerBlue,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = ad.audioDisplayName.ifBlank { "Выбрать MP3 / WAV / M4A" },
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
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
                        label = { Text("Текст для русской TTS-озвучки") },
                        placeholder = { Text("Переходи по ссылке в профиле прямо сейчас!") },
                        minLines = 2,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SpeakerPink,
                            unfocusedBorderColor = BorderSubtle,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("ad_tts_input")
                    )
                    OutlinedButton(
                        onClick = onPreviewTts,
                        enabled = ad.ttsText.isNotBlank(),
                        shape = RoundedCornerShape(11.dp),
                        border = BorderStroke(1.dp, SpeakerPink),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.VolumeUp,
                            contentDescription = null,
                            tint = SpeakerPink,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Прослушать TTS", color = SpeakerPink)
                    }
                }
            }
        }

        // One-tap Test Ad Freeze-Frame Pause on Live Preview
        if (ad.imageUri != null && state.videoUri != null) {
            Button(
                onClick = onTestAdPause,
                shape = RoundedCornerShape(13.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = SpeakerPink,
                    contentColor = ObsidianBg
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("test_ad_pause_button")
            ) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Проверить рекламную паузу на превью", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
