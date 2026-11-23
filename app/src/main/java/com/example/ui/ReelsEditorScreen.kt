package com.example.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ClosedCaption
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FolderCopy
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.media.SubtitleEngine
import com.example.model.EditMode
import com.example.model.EditorTab
import com.example.model.EditorUiState
import com.example.model.ExportState
import com.example.model.ExportedVideo
import com.example.ui.components.AdBannerEditorTab
import com.example.ui.components.CaptionsEditorTab
import com.example.ui.components.EffectsEditorTab
import com.example.ui.components.ModeSelectorTab
import com.example.ui.components.SlicesTimelineTab
import com.example.ui.components.VideoPreviewStage
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.HotCoral
import com.example.ui.theme.NeonGreen
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.NeonPurpleContainer
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.SurfaceElevated
import com.example.viewmodel.EditorViewModel
import kotlin.math.roundToInt

@Composable
fun ReelsEditorScreen(viewModel: EditorViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.onVideoSelected(uri)
        }
    }

    val adImagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.onAdImageSelected(uri)
        }
    }

    val adAudioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.onAdAudioSelected(uri)
        }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.startRussianSpeechRecognition()
        }
    }

    val onVoiceClick: () -> Unit = {
        if (state.isListeningSpeech) {
            viewModel.stopRussianSpeechRecognition()
        } else {
            val hasPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
            if (hasPermission) {
                viewModel.startRussianSpeechRecognition()
            } else {
                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    val openVideoPicker = {
        videoPickerLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
        )
    }

    val openAdImagePicker = {
        adImagePickerLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    val openAdAudioPicker = {
        adAudioPickerLauncher.launch(arrayOf("audio/*"))
    }

    Scaffold(
        containerColor = ObsidianBg,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            StudioTopBar(
                hasVideo = state.videoUri != null,
                videoName = state.videoFileName,
                onPickVideo = openVideoPicker
            )
        },
        bottomBar = {
            if (state.videoUri != null) {
                StudioBottomNavBar(
                    activeTab = state.activeTab,
                    onSelectTab = viewModel::selectTab
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (state.videoUri == null) {
                EmptyStudioOnboarding(
                    selectedMode = state.editMode,
                    onSelectMode = viewModel::selectEditMode,
                    onPickVideo = openVideoPicker
                )
            } else {
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val isWideScreen = maxWidth >= 700.dp
                    if (isWideScreen) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(18.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .widthIn(max = 320.dp)
                                    .fillMaxHeight()
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                VideoPreviewStage(
                                    state = state,
                                    onTogglePlay = viewModel::togglePlayPause,
                                    onSeekEditedMs = viewModel::seekEditedMs
                                )
                                ExportActionButtons(
                                    clipCount = state.clips.size,
                                    enabled = !state.isAnalyzing && state.clips.isNotEmpty(),
                                    onExportFull = viewModel::exportFullVideo,
                                    onExportAllParts = viewModel::exportAllClips
                                )
                            }

                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .verticalScroll(rememberScrollState())
                                    .padding(bottom = 16.dp)
                            ) {
                                ActiveTabContent(
                                    state = state,
                                    viewModel = viewModel,
                                    onVoiceClick = onVoiceClick,
                                    onPickAdImage = openAdImagePicker,
                                    onPickAdAudio = openAdAudioPicker
                                )
                            }
                        }
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                VideoPreviewStage(
                                    state = state,
                                    onTogglePlay = viewModel::togglePlayPause,
                                    onSeekEditedMs = viewModel::seekEditedMs,
                                    modifier = Modifier.widthIn(max = 260.dp)
                                )
                            }

                            state.analysisError?.let { errorMsg ->
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = HotCoral.copy(alpha = 0.16f),
                                    border = BorderStroke(1.dp, HotCoral)
                                ) {
                                    Text(
                                        text = errorMsg,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.White,
                                        modifier = Modifier.padding(12.dp)
                                    )
                                }
                            }

                            ExportActionButtons(
                                clipCount = state.clips.size,
                                enabled = !state.isAnalyzing && state.clips.isNotEmpty(),
                                onExportFull = viewModel::exportFullVideo,
                                onExportAllParts = viewModel::exportAllClips
                            )

                            HorizontalDivider(color = BorderSubtle.copy(alpha = 0.7f))

                            ActiveTabContent(
                                state = state,
                                viewModel = viewModel,
                                onVoiceClick = onVoiceClick,
                                onPickAdImage = openAdImagePicker,
                                onPickAdAudio = openAdAudioPicker
                            )

                            Spacer(modifier = Modifier.height(12.dp))
                        }
                    }
                }
            }
        }
    }

    when (val exp = state.exportState) {
        is ExportState.Idle -> Unit
        is ExportState.Exporting -> {
            AlertDialog(
                onDismissRequest = {},
                containerColor = SurfaceDark,
                title = {
                    Text(
                        text = if (exp.totalParts > 1) {
                            "Экспорт частей (${exp.currentPart}/${exp.totalParts})"
                        } else {
                            "Экспорт видео 9:16"
                        },
                        color = Color.White
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = exp.statusText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = ElectricCyan
                        )
                        LinearProgressIndicator(
                            progress = { exp.progress.coerceIn(0f, 1f) },
                            color = NeonPurple,
                            trackColor = BorderSubtle,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                        )
                        Text(
                            text = "${(exp.progress * 100).roundToInt()}% — рендер с эффектами, субтитрами и аудио",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(
                        onClick = viewModel::cancelExport,
                        modifier = Modifier.testTag("cancel_export_button")
                    ) {
                        Text("Отмена", color = HotCoral)
                    }
                }
            )
        }

        is ExportState.Completed -> {
            AlertDialog(
                onDismissRequest = viewModel::dismissExportDialog,
                containerColor = SurfaceDark,
                icon = {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        tint = NeonGreen,
                        modifier = Modifier.size(36.dp)
                    )
                },
                title = {
                    Text(
                        text = if (exp.isBatch) {
                            "Сохранено частей: ${exp.videos.size}"
                        } else {
                            "Ролик готов и сохранён!"
                        },
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Файлы сохранены в Галерею (папка Movies/ReelsCut):",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        exp.videos.take(5).forEach { vid ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = SurfaceElevated,
                                border = BorderStroke(1.dp, BorderSubtle),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = vid.displayName,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = Color.White,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = SubtitleEngine.formatTimeMs(vid.durationMs),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = ElectricCyan
                                        )
                                    }
                                    if (vid.contentUri != null) {
                                        TextButton(
                                            onClick = { shareExportedVideo(context, vid) },
                                            modifier = Modifier.testTag("share_exported_video")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Share,
                                                contentDescription = stringResource(R.string.cd_share_video),
                                                tint = ElectricCyan,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = viewModel::dismissExportDialog,
                        colors = ButtonDefaults.buttonColors(containerColor = NeonPurple)
                    ) {
                        Text("Отлично", color = Color.White)
                    }
                }
            )
        }

        is ExportState.Error -> {
            AlertDialog(
                onDismissRequest = viewModel::dismissExportDialog,
                containerColor = SurfaceDark,
                icon = {
                    Icon(
                        imageVector = Icons.Rounded.ErrorOutline,
                        contentDescription = null,
                        tint = HotCoral,
                        modifier = Modifier.size(34.dp)
                    )
                },
                title = { Text("Не удалось экспортировать", color = Color.White) },
                text = { Text(exp.message, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                confirmButton = {
                    Button(
                        onClick = viewModel::dismissExportDialog,
                        colors = ButtonDefaults.buttonColors(containerColor = NeonPurple)
                    ) {
                        Text("Понятно", color = Color.White)
                    }
                }
            )
        }
    }
}

@Composable
private fun StudioTopBar(
    hasVideo: Boolean,
    videoName: String,
    onPickVideo: () -> Unit
) {
    Surface(
        color = ObsidianBg,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(
                            Brush.linearGradient(listOf(NeonPurple, ElectricCyan))
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Bolt,
                        contentDescription = stringResource(R.string.cd_app_logo),
                        tint = ObsidianBg,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Column {
                    Text(
                        text = stringResource(R.string.studio_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White
                    )
                    Text(
                        text = if (hasVideo) videoName else "9:16 Авто-монтаж Reels / Shorts",
                        style = MaterialTheme.typography.bodySmall,
                        color = ElectricCyan,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (hasVideo) {
                OutlinedButton(
                    onClick = onPickVideo,
                    border = BorderStroke(1.dp, NeonPurple.copy(alpha = 0.75f)),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("replace_video_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.VideoLibrary,
                        contentDescription = null,
                        tint = ElectricCyan,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.action_replace_video),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyStudioOnboarding(
    selectedMode: EditMode,
    onSelectMode: (EditMode) -> Unit,
    onPickVideo: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 560.dp)
                .height(205.dp)
                .clip(RoundedCornerShape(22.dp))
                .border(
                    1.5.dp,
                    Brush.linearGradient(listOf(NeonPurple, ElectricCyan)),
                    RoundedCornerShape(22.dp)
                )
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
                            listOf(
                                Color.Transparent,
                                ObsidianBg.copy(alpha = 0.88f)
                            )
                        )
                    )
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp)
            ) {
                Text(
                    text = "Монтаж в стиле Литвина за 1 тап",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Вырежем тишину, добавим зум на пиках, цветные субтитры и рекламную паузу",
                    style = MaterialTheme.typography.bodySmall,
                    color = ElectricCyan
                )
            }
        }

        Button(
            onClick = onPickVideo,
            colors = ButtonDefaults.buttonColors(
                containerColor = NeonPurple,
                contentColor = Color.White
            ),
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 560.dp)
                .height(58.dp)
                .testTag("pick_video_button")
        ) {
            Icon(
                imageVector = Icons.Rounded.VideoLibrary,
                contentDescription = null,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "Загрузить видео из галереи",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 560.dp)
        ) {
            ModeSelectorTab(
                selectedMode = selectedMode,
                onSelectMode = onSelectMode
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
    }
}

@Composable
private fun ExportActionButtons(
    clipCount: Int,
    enabled: Boolean,
    onExportFull: () -> Unit,
    onExportAllParts: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = onExportFull,
            enabled = enabled,
            colors = ButtonDefaults.buttonColors(
                containerColor = NeonPurple,
                contentColor = Color.White,
                disabledContainerColor = SurfaceCard
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .testTag("export_full_button")
        ) {
            Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.action_export_full),
                style = MaterialTheme.typography.labelLarge
            )
        }

        OutlinedButton(
            onClick = onExportAllParts,
            enabled = enabled && clipCount > 0,
            border = BorderStroke(1.4.dp, if (enabled) ElectricCyan else BorderSubtle),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("export_all_parts_button")
        ) {
            Icon(
                Icons.Rounded.FolderCopy,
                contentDescription = null,
                tint = if (enabled) ElectricCyan else BorderSubtle,
                modifier = Modifier.size(19.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "${stringResource(R.string.action_export_clips)} ($clipCount)",
                style = MaterialTheme.typography.labelLarge,
                color = if (enabled) ElectricCyan else BorderSubtle
            )
        }
    }
}

@Composable
private fun ActiveTabContent(
    state: EditorUiState,
    viewModel: EditorViewModel,
    onVoiceClick: () -> Unit,
    onPickAdImage: () -> Unit,
    onPickAdAudio: () -> Unit
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
            onStartStopVoice = onVoiceClick,
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

@Composable
private fun StudioBottomNavBar(
    activeTab: EditorTab,
    onSelectTab: (EditorTab) -> Unit
) {
    NavigationBar(
        containerColor = SurfaceDark,
        tonalElevation = 0.dp
    ) {
        EditorTab.entries.forEach { tab ->
            val icon = when (tab) {
                EditorTab.MODE -> Icons.Rounded.Bolt
                EditorTab.SLICES -> Icons.Rounded.ContentCut
                EditorTab.CAPTIONS -> Icons.Rounded.ClosedCaption
                EditorTab.EFFECTS -> Icons.Rounded.AutoAwesome
                EditorTab.AD -> Icons.Rounded.Campaign
            }
            NavigationBarItem(
                selected = tab == activeTab,
                onClick = { onSelectTab(tab) },
                icon = {
                    Icon(
                        imageVector = icon,
                        contentDescription = tab.titleRu,
                        modifier = Modifier.size(22.dp)
                    )
                },
                label = {
                    Text(
                        text = tab.titleRu,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = ElectricCyan,
                    selectedTextColor = ElectricCyan,
                    indicatorColor = NeonPurpleContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                modifier = Modifier.testTag("nav_tab_${tab.name.lowercase()}")
            )
        }
    }
}

private fun shareExportedVideo(context: Context, video: ExportedVideo) {
    val uri = video.contentUri ?: return
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "video/mp4"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching {
        context.startActivity(Intent.createChooser(intent, "Поделиться видео"))
    }
}
