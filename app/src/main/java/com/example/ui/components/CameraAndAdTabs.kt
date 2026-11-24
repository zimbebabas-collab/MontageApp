package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.rounded.Face
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Videocam
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.media.SoundEffectsEngine
import com.example.media.SubtitleEngine
import com.example.model.AdMediaType
import com.example.model.EditorUiState
import com.example.model.FramingConfig
import com.example.model.SfxPack
import com.example.model.SfxSound
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CameraFramingTab(
    state: EditorUiState,
    onUpdateFraming: ((FramingConfig) -> FramingConfig) -> Unit,
    onRetrackFaces: () -> Unit,
    onSelectSfxPack: (SfxPack) -> Unit,
    onSfxVolumeChange: (Float) -> Unit,
    onPreviewSfx: (SfxSound) -> Unit
) {
    val framing = state.framing

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "Авто-трекинг лица, эффекты и SFX",
            style = MaterialTheme.typography.titleMedium,
            color = TextPrimary
        )

        // Face & Key-Point Auto-Tracking Card (ML Kit)
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = SurfaceElevated,
            border = BorderStroke(
                1.dp,
                if (framing.autoFaceTracking) AccentGreen.copy(alpha = 0.6f) else BorderSubtle
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Face,
                            contentDescription = null,
                            tint = AccentGreen,
                            modifier = Modifier.size(22.dp)
                        )
                        Column {
                            Text(
                                text = "Авто-трекинг лица и точек",
                                style = MaterialTheme.typography.titleSmall,
                                color = TextPrimary
                            )
                            Text(
                                text = "Наезды фиксируются точно на лице, а не по центру",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    }
                    Switch(
                        checked = framing.autoFaceTracking,
                        onCheckedChange = { v -> onUpdateFraming { it.copy(autoFaceTracking = v) } },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = ObsidianBg,
                            checkedTrackColor = AccentGreen
                        ),
                        modifier = Modifier.testTag("toggle_auto_face_tracking")
                    )
                }

                if (state.isTrackingFaces || !state.faceTrackingStatus.isNullOrBlank()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            if (state.isTrackingFaces) {
                                CircularProgressIndicator(
                                    color = AccentGreen,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                            Text(
                                text = state.faceTrackingStatus ?: "Поиск лиц...",
                                style = MaterialTheme.typography.labelSmall,
                                color = AccentGreen
                            )
                        }
                        if (state.videoUri != null && !state.isTrackingFaces) {
                            OutlinedButton(
                                onClick = onRetrackFaces,
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, AccentGreen.copy(alpha = 0.7f)),
                                modifier = Modifier
                                    .height(34.dp)
                                    .testTag("retrack_faces_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Refresh,
                                    contentDescription = null,
                                    tint = AccentGreen,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Найти снова", style = MaterialTheme.typography.labelSmall, color = AccentGreen)
                            }
                        }
                    }
                }
            }
        }

        // Viral Sound Effects (SFX) Section
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = SurfaceElevated,
            border = BorderStroke(
                1.dp,
                if (framing.sfxPack != SfxPack.OFF) SpeakerPink.copy(alpha = 0.6f) else BorderSubtle
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.GraphicEq,
                            contentDescription = null,
                            tint = SpeakerPink,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Звуковые эффекты (SFX)",
                            style = MaterialTheme.typography.titleSmall,
                            color = TextPrimary
                        )
                    }
                    if (state.sfxEvents.isNotEmpty()) {
                        Text(
                            text = "Точек: ${state.sfxEvents.size}",
                            style = MaterialTheme.typography.labelSmall,
                            color = SpeakerPink
                        )
                    }
                }

                Text(
                    text = framing.sfxPack.descRu,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    SfxPack.entries.forEach { pack ->
                        FilterChip(
                            selected = framing.sfxPack == pack,
                            onClick = { onSelectSfxPack(pack) },
                            label = { Text(pack.labelRu) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = SpeakerPink.copy(alpha = 0.25f),
                                selectedLabelColor = SpeakerPink,
                                labelColor = TextSecondary
                            ),
                            modifier = Modifier.testTag("sfx_pack_${pack.name.lowercase()}")
                        )
                    }
                }

                if (framing.sfxPack != SfxPack.OFF) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Громкость SFX", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                        Text(
                            text = "${(framing.sfxVolume * 100).roundToInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = SpeakerPink
                        )
                    }
                    Slider(
                        value = framing.sfxVolume,
                        onValueChange = onSfxVolumeChange,
                        valueRange = 0.1f..1.0f,
                        colors = SliderDefaults.colors(
                            thumbColor = SpeakerPink,
                            activeTrackColor = SpeakerPink,
                            inactiveTrackColor = BorderSubtle
                        )
                    )

                    Text(
                        text = "Прослушать звуки:",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        SfxSound.entries.forEach { sound ->
                            OutlinedButton(
                                onClick = { onPreviewSfx(sound) },
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, SpeakerBlue.copy(alpha = 0.6f)),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(38.dp)
                                    .testTag("preview_sfx_${sound.name.lowercase()}")
                            ) {
                                Text(
                                    text = SoundEffectsEngine.soundLabel(sound),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SpeakerBlue,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }

        // Saturated Key-Moment Effects Toggles
        ToggleOptionCard(
            title = "Насыщенные эффекты на пиках",
            subtitle = "Усиление сочности цвета + энергетический импульс в ключевых моментах",
            checked = framing.saturatedPeakEffects,
            accent = AccentYellow,
            tag = "toggle_saturated_peaks",
            onCheckedChange = { v -> onUpdateFraming { it.copy(saturatedPeakEffects = v) } }
        )

        ToggleOptionCard(
            title = "Неоновая вспышка на пиках",
            subtitle = "Яркое цианово-розовое свечение по краям кадра на громких фразах",
            checked = framing.neonFlashOnPeak,
            accent = SpeakerPink,
            tag = "toggle_neon_flash",
            onCheckedChange = { v -> onUpdateFraming { it.copy(neonFlashOnPeak = v) } }
        )

        ToggleOptionCard(
            title = "Микро-тряска камеры на пиках",
            subtitle = "Эффект динамичного удара камеры на криках и панчлайнах",
            checked = framing.microShakeOnPeak,
            accent = HotCoral,
            tag = "toggle_micro_shake",
            onCheckedChange = { v -> onUpdateFraming { it.copy(microShakeOnPeak = v) } }
        )

        ToggleOptionCard(
            title = "Авто-наезды 1.0x ↔ 1.35x",
            subtitle = "Плавная GPU-смена крупности на склейках без лагов",
            checked = framing.autoCloseUpCuts,
            accent = SpeakerBlue,
            tag = "toggle_auto_closeup",
            onCheckedChange = { v -> onUpdateFraming { it.copy(autoCloseUpCuts = v) } }
        )

        // Close-up Zoom Level + Manual Focal Override
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
                    text = "Ручная подстройка точки (можно также перетащить прямо на видео)",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Фокус по X", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    Text("${(framing.focalX * 100).roundToInt()}%", style = MaterialTheme.typography.labelSmall, color = TextPrimary)
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
                    Text("Фокус по Y", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    Text("${(framing.focalY * 100).roundToInt()}%", style = MaterialTheme.typography.labelSmall, color = TextPrimary)
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
            title = "Акцент-зум на пиках голоса",
            subtitle = "Лёгкий дополнительный наезд на самых эмоциональных словах",
            checked = framing.peakPunchEnabled,
            accent = SpeakerPink,
            tag = "toggle_peak_punch",
            onCheckedChange = { v -> onUpdateFraming { it.copy(peakPunchEnabled = v) } }
        )

        ToggleOptionCard(
            title = "Виньетка по краям",
            subtitle = "Затемнение периферии кадра для контраста",
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
    onPickAdVideo: () -> Unit,
    onClearAdMedia: () -> Unit,
    onUseVideoOwnAudioChange: (Boolean) -> Unit,
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
                    text = "Картинка или ВИДЕО-баннер по центру (25–35% экрана) со стоп-кадром",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
            Switch(
                checked = ad.enabled,
                onCheckedChange = onSetEnabled,
                enabled = ad.mediaUri != null,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = ObsidianBg,
                    checkedTrackColor = SpeakerPink
                ),
                modifier = Modifier.testTag("ad_enabled_switch")
            )
        }

        // Upload Banner Media Card (Supports BOTH Image and Video Ad Banner!)
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = SurfaceElevated,
            border = BorderStroke(
                1.dp,
                if (ad.mediaUri != null) SpeakerPink.copy(alpha = 0.6f) else BorderSubtle
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (ad.mediaUri != null && ad.mediaType == AdMediaType.IMAGE) {
                        AsyncImage(
                            model = ad.mediaUri,
                            contentDescription = stringResource(R.string.cd_ad_banner),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(54.dp)
                                .clip(RoundedCornerShape(10.dp))
                        )
                    } else {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = SurfaceCard,
                            modifier = Modifier.size(54.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                Icon(
                                    imageVector = if (ad.mediaType == AdMediaType.VIDEO && ad.mediaUri != null) {
                                        Icons.Rounded.Movie
                                    } else {
                                        Icons.Rounded.Image
                                    },
                                    contentDescription = null,
                                    tint = if (ad.mediaType == AdMediaType.VIDEO && ad.mediaUri != null) {
                                        SpeakerBlue
                                    } else {
                                        SpeakerPink
                                    },
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        val title = when {
                            ad.mediaUri == null -> "Загрузите видео или картинку"
                            ad.mediaType == AdMediaType.VIDEO -> "ВИДЕО-БАННЕР загружен"
                            else -> "Картинка баннера загружена"
                        }
                        val sub = when {
                            ad.mediaUri == null -> "Стоп-кадр • по центру • 25–35% экрана"
                            ad.mediaType == AdMediaType.VIDEO ->
                                "${ad.mediaDisplayName} (${SubtitleEngine.formatTimeMs(ad.adVideoDurationMs)})"
                            else -> ad.mediaDisplayName.ifBlank { "По центру экрана • затемнение 45%" }
                        }
                        Text(
                            text = title,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (ad.mediaType == AdMediaType.VIDEO && ad.mediaUri != null) SpeakerBlue else TextPrimary
                        )
                        Text(
                            text = sub,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (ad.mediaUri != null) {
                        IconButton(onClick = onClearAdMedia) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Удалить баннер",
                                tint = HotCoral
                            )
                        }
                    }
                }

                // Dual Upload Buttons: Video Banner & Image Banner
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onPickAdVideo,
                        shape = RoundedCornerShape(11.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SpeakerBlue,
                            contentColor = ObsidianBg
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                            .testTag("pick_ad_video_button")
                    ) {
                        Icon(Icons.Rounded.Videocam, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Видео-баннер", style = MaterialTheme.typography.labelMedium)
                    }

                    OutlinedButton(
                        onClick = onPickAdImage,
                        shape = RoundedCornerShape(11.dp),
                        border = BorderStroke(1.dp, SpeakerPink),
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                            .testTag("pick_ad_image_button")
                    ) {
                        Icon(
                            Icons.Rounded.Image,
                            contentDescription = null,
                            tint = SpeakerPink,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Картинка", color = SpeakerPink, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }

        // Timestamp & Size (25%..35%) & Duration (3..15s)
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
                    listOf(3, 5, 8, 10, 15).forEach { sec ->
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

        // Ad Sound Source: Video's Own Audio (when Video Banner) OR Custom Audio File OR Built-in Russian TTS
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
                Text("Звук рекламной паузы", style = MaterialTheme.typography.labelLarge, color = TextPrimary)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (ad.mediaType == AdMediaType.VIDEO && ad.mediaUri != null) {
                        FilterChip(
                            selected = ad.useVideoOwnAudio && !ad.useTts,
                            onClick = { onUseVideoOwnAudioChange(true) },
                            label = { Text("Из видео", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AccentGreen.copy(alpha = 0.24f),
                                selectedLabelColor = AccentGreen
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    FilterChip(
                        selected = !ad.useTts && !(ad.mediaType == AdMediaType.VIDEO && ad.useVideoOwnAudio),
                        onClick = {
                            onUseVideoOwnAudioChange(false)
                            onUseTtsChange(false)
                        },
                        leadingIcon = {
                            Icon(Icons.Rounded.AudioFile, contentDescription = null, modifier = Modifier.size(15.dp))
                        },
                        label = { Text("Аудио", maxLines = 1, overflow = TextOverflow.Ellipsis) },
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
                            Icon(Icons.Rounded.RecordVoiceOver, contentDescription = null, modifier = Modifier.size(15.dp))
                        },
                        label = { Text("TTS", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = SpeakerPink.copy(alpha = 0.22f),
                            selectedLabelColor = SpeakerPink
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }

                when {
                    ad.mediaType == AdMediaType.VIDEO && ad.useVideoOwnAudio && !ad.useTts -> {
                        Text(
                            text = "Во время рекламной паузы проиграется оригинальный звук из загруженного видео-баннера.",
                            style = MaterialTheme.typography.bodySmall,
                            color = AccentGreen
                        )
                    }
                    !ad.useTts -> {
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
                    }
                    else -> {
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
        }

        // One-tap Test Ad Freeze-Frame Pause on Live Preview
        if (ad.mediaUri != null && state.videoUri != null) {
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
