package com.example.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.R
import com.example.model.EditorTab
import com.example.model.EditorUiState
import com.example.model.ExportState
import com.example.ui.components.AdBannerEditorTab
import com.example.ui.components.CaptionsEditorTab
import com.example.ui.components.EffectsEditorTab
import com.example.ui.components.ExportActionDock
import com.example.ui.components.ExportStatusDialog
import com.example.ui.components.ModeSelectorTab
import com.example.ui.components.SlicesTimelineTab
import com.example.ui.components.VideoPreviewStage
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.HotCoral
import com.example.ui.theme.NeonGreen
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.viewmodel.EditorViewModel

@Composable
fun EditorScreen(
    viewModel: EditorViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Zero-permission Android Photo Picker for Video
    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.onVideoSelected(uri)
        }
    }

    // Zero-permission Android Photo Picker for Custom Ad Banner Image (PNG/JPG)
    val adImagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.onAdImageSelected(uri)
        }
    }

    // Zero-permission Storage Access Framework picker for optional Ad Voiceover Audio
    val adAudioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.onAdAudioSelected(uri)
        }
    }

    val recordAudioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.startRussianSpeechRecognition()
        } else {
            viewModel.onSpeechPermissionDenied()
        }
    }

    fun launchPickVideo() {
        viewModel.stopPlayback()
        videoPickerLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
        )
    }

    fun launchPickAdImage() {
        adImagePickerLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    fun toggleSpeechDictation() {
        if (state.isListeningSpeech) {
            viewModel.stopRussianSpeechRecognition()
            return
        }
        val hasPerm = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (hasPerm) {
            viewModel.startRussianSpeechRecognition()
        } else {
            recordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    Scaffold(
        containerColor = ObsidianBg,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            StudioTopBar(
                videoName = state.videoFileName,
                hasVideo = state.videoUri != null,
                onPickVideo = ::launchPickVideo
            )
        },
        bottomBar = {
            ExportActionDock(
                hasVideo = state.videoUri != null,
                clipCount = state.clips.size,
                isBusy = state.isAnalyzing || state.exportState is ExportState.Exporting,
                onExportFull = viewModel::exportFullVideo,
                onExportAllParts = viewModel::exportAllClips
            )
        }
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val isWideScreen = maxWidth >= 680.dp

            if (isWideScreen) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (state.videoUri == null) {
                        EmptyStudioHeroCard(
                            onPickVideo = ::launchPickVideo,
                            modifier = Modifier
                                .weight(0.9f)
                                .align(Alignment.CenterVertically)
                        )
                    } else {
                        VideoPreviewStage(
                            state = state,
                            onTogglePlay = viewModel::togglePlayPause,
                            onSeekEditedMs = viewModel::seekEditedMs,
                            modifier = Modifier
                                .weight(0.9f)
                                .fillMaxHeight()
                        )
                    }

                    EditorToolsColumn(
                        state = state,
                        viewModel = viewModel,
                        onToggleSpeech = ::toggleSpeechDictation,
                        onPickAdImage = ::launchPickAdImage,
                        onPickAdAudio = { adAudioPickerLauncher.launch(arrayOf("audio/*")) },
                        modifier = Modifier
                            .weight(1.1f)
                            .fillMaxHeight()
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (!state.analysisError.isNullOrBlank()) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = HotCoral.copy(alpha = 0.16f),
                            border = BorderStroke(1.dp, HotCoral.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = state.analysisError!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }

                    if (state.videoUri == null) {
                        EmptyStudioHeroCard(
                            onPickVideo = ::launchPickVideo,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        VideoPreviewStage(
                            state = state,
                            onTogglePlay = viewModel::togglePlayPause,
                            onSeekEditedMs = viewModel::seekEditedMs,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height((this@BoxWithConstraints.maxHeight * 0.44f).coerceIn(220.dp, 360.dp))
                        )
                    }

                    EditorToolsColumn(
                        state = state,
                        viewModel = viewModel,
                        onToggleSpeech = ::toggleSpeechDictation,
                        onPickAdImage = ::launchPickAdImage,
                        onPickAdAudio = { adAudioPickerLauncher.launch(arrayOf("audio/*")) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    )
                }
            }
        }
    }

    ExportStatusDialog(
        exportState = state.exportState,
        onCancel = viewModel::cancelExport,
        onDismiss = viewModel::dismissExportDialog
    )
}

@Composable
private fun EmptyStudioHeroCard(
    onPickVideo: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        color = SurfaceDark,
        border = BorderStroke(1.dp, BorderSubtle)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(148.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_studio_hero_1795466466494),
                    contentDescription = stringResource(R.string.cd_studio_hero),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, SurfaceDark.copy(alpha = 0.88f), SurfaceDark)
                            )
                        )
                )
                Surface(
                    shape = RoundedCornerShape(50),
                    color = ObsidianBg.copy(alpha = 0.8f),
                    border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.7f)),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            tint = ElectricCyan,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = "9:16 REELS / SHORTS / TIKTOK",
                            style = MaterialTheme.typography.labelSmall,
                            color = ElectricCyan
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Авто-монтаж как у Литвина",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "Загрузите видео — вырежем паузы по громкости, добавим зум и тряску на пиках, цветные русские субтитры и рекламную паузу.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    textAlign = TextAlign.Center
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)
                ) {
                    HeroBadge("Без тишины", NeonPurple)
                    HeroBadge("Субтитры RU", NeonGreen)
                    HeroBadge("Реклама-пауза", ElectricCyan)
                }

                Button(
                    onClick = onPickVideo,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NeonPurple,
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("pick_video_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.VideoLibrary,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.action_pick_video),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroBadge(label: String, accent: Color) {
    Surface(
        shape = RoundedCornerShape(50),
        color = accent.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.5f))
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = accent,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun StudioTopBar(
    videoName: String,
    hasVideo: Boolean,
    onPickVideo: () -> Unit
) {
    Surface(
        color = ObsidianBg,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.app_icon_fg_1795466465872),
                    contentDescription = stringResource(R.string.cd_app_logo),
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                )
                Column {
                    Text(
                        text = stringResource(R.string.studio_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary
                    )
                    Text(
                        text = if (hasVideo) videoName else "Авто-монтаж 9:16 • Reels / Shorts",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (hasVideo) ElectricCyan else TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (hasVideo) {
                TextButton(
                    onClick = onPickVideo,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier.testTag("replace_video_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.VideoLibrary,
                        contentDescription = null,
                        tint = NeonPurple,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = stringResource(R.string.action_replace_video),
                        style = MaterialTheme.typography.labelMedium,
                        color = ElectricCyan
                    )
                }
            }
        }
    }
}

@Composable
private fun EditorToolsColumn(
    state: EditorUiState,
    viewModel: EditorViewModel,
    onToggleSpeech: () -> Unit,
    onPickAdImage: () -> Unit,
    onPickAdAudio: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(EditorTab.entries) { tab ->
                val selected = tab == state.activeTab
                val icon = when (tab) {
                    EditorTab.MODE -> Icons.Rounded.Bolt
                    EditorTab.SLICES -> Icons.Rounded.ContentCut
                    EditorTab.CAPTIONS -> Icons.Rounded.Subtitles
                    EditorTab.EFFECTS -> Icons.Rounded.AutoFixHigh
                    EditorTab.AD -> Icons.Rounded.Campaign
                }
                Surface(
                    onClick = { viewModel.selectTab(tab) },
                    shape = RoundedCornerShape(12.dp),
                    color = if (selected) NeonPurple.copy(alpha = 0.24f) else SurfaceCard,
                    border = BorderStroke(
                        width = if (selected) 1.5.dp else 1.dp,
                        color = if (selected) ElectricCyan else BorderSubtle
                    ),
                    modifier = Modifier
                        .height(44.dp)
                        .testTag("tab_${tab.name.lowercase()}")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = if (selected) ElectricCyan else TextSecondary,
                            modifier = Modifier.size(17.dp)
                        )
                        Text(
                            text = tab.titleRu,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (selected) TextPrimary else TextSecondary
                        )
                    }
                }
            }
        }

        Surface(
            shape = RoundedCornerShape(18.dp),
            color = SurfaceDark,
            border = BorderStroke(1.dp, BorderSubtle),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(14.dp)
            ) {
                when (state.activeTab) {
                    EditorTab.MODE -> ModeSelectorTab(
                        selectedMode = state.editMode,
                        onSelectMode = viewModel::selectEditMode
                    )

                    EditorTab.SLICES -> SlicesTimelineTab(
                        state = state,
                        onSilenceThresholdChange = viewModel::updateSilenceThreshold,
                        onSelectClip = viewModel::selectClip,
                        onSplitClip = viewModel::splitClip,
                        onDeleteClip = viewModel::deleteClip,
                        onResetSlices = viewModel::resetSlices
                    )

                    EditorTab.CAPTIONS -> CaptionsEditorTab(
                        state = state,
                        onToggleCaptions = viewModel::toggleCaptionsEnabled,
                        onTranscriptChange = viewModel::updateTranscriptText,
                        onStartStopVoice = onToggleSpeech,
                        onCycleChunkColor = viewModel::cycleCaptionColor
                    )

                    EditorTab.EFFECTS -> EffectsEditorTab(
                        effects = state.effects,
                        onUpdateEffects = viewModel::updateEffects
                    )

                    EditorTab.AD -> AdBannerEditorTab(
                        state = state,
                        onSetEnabled = viewModel::setAdBannerEnabled,
                        onPickAdImage = onPickAdImage,
                        onClearAdImage = viewModel::clearAdImage,
                        onPickAdAudio = onPickAdAudio,
                        onClearAdAudio = viewModel::clearAdAudio,
                        onTriggerChange = viewModel::setAdTriggerMs,
                        onDurationChange = viewModel::setAdDurationSec,
                        onAreaPercentChange = viewModel::setAdAreaPercent,
                        onUseTtsChange = viewModel::setAdUseTts,
                        onTtsTextChange = viewModel::setAdTtsText,
                        onPreviewTts = viewModel::previewAdTts
                    )
                }
            }
        }
    }
}
