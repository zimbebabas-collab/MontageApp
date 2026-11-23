package com.example.ui.components

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.MovieFilter
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.media.SubtitleEngine
import com.example.model.ExportState
import com.example.model.ExportedVideo
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.HotCoral
import com.example.ui.theme.NeonGreen
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun ExportActionDock(
    hasVideo: Boolean,
    clipCount: Int,
    isBusy: Boolean,
    onExportFull: () -> Unit,
    onExportAllParts: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = SurfaceDark,
        border = BorderStroke(1.dp, BorderSubtle),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Button 1: «Скачать готовый ролик»
            val gradient = Brush.horizontalGradient(listOf(NeonPurple, ElectricCyan))
            val enabled = hasVideo && clipCount > 0 && !isBusy
            Button(
                onClick = onExportFull,
                enabled = enabled,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Transparent,
                    disabledContainerColor = SurfaceCard,
                    contentColor = Color.White
                ),
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier
                    .weight(1.35f)
                    .height(52.dp)
                    .testTag("export_full_button")
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            if (enabled) gradient
                            else Brush.horizontalGradient(listOf(SurfaceCard, SurfaceCard))
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(horizontal = 10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Download,
                            contentDescription = null,
                            tint = if (enabled) Color.White else TextSecondary,
                            modifier = Modifier.size(19.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.action_export_full),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (enabled) Color.White else TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Button 2: «Скачать все части»
            OutlinedButton(
                onClick = onExportAllParts,
                enabled = enabled,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(
                    1.2.dp,
                    if (enabled) ElectricCyan else BorderSubtle
                ),
                contentPadding = PaddingValues(horizontal = 10.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
                    .testTag("export_parts_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.MovieFilter,
                    contentDescription = null,
                    tint = if (enabled) ElectricCyan else TextSecondary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = if (clipCount > 1) "Все части ($clipCount)" else stringResource(R.string.action_export_clips),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (enabled) ElectricCyan else TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun ExportStatusDialog(
    exportState: ExportState,
    onCancel: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    fun shareVideo(video: ExportedVideo) {
        val uri = video.contentUri ?: return
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "video/mp4"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Поделиться видео ${video.displayName}"))
    }

    when (exportState) {
        ExportState.Idle -> Unit

        is ExportState.Exporting -> {
            AlertDialog(
                onDismissRequest = {},
                containerColor = SurfaceDark,
                title = {
                    Text(
                        text = if (exportState.totalParts > 1) {
                            "Экспорт частей (${exportState.currentPart}/${exportState.totalParts})"
                        } else {
                            "Рендер видео 9:16 (MP4)"
                        },
                        color = TextPrimary
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = exportState.statusText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                        LinearProgressIndicator(
                            progress = { exportState.progress.coerceIn(0f, 1f) },
                            color = ElectricCyan,
                            trackColor = BorderSubtle,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                        )
                        Text(
                            text = "${(exportState.progress * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = ElectricCyan
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = onCancel) {
                        Text("Отмена", color = HotCoral)
                    }
                }
            )
        }

        is ExportState.Completed -> {
            AlertDialog(
                onDismissRequest = onDismiss,
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
                        text = if (exportState.isBatch) {
                            "Сохранено частей: ${exportState.videos.size}"
                        } else {
                            "Ролик готов и сохранён!"
                        },
                        color = TextPrimary
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Файлы сохранены в Галерее (Movies/ReelsCut). Вы можете сразу отправить их в Reels, Shorts или TikTok:",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                        exportState.videos.forEach { vid ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = SurfaceCard,
                                border = BorderStroke(1.dp, BorderSubtle),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = vid.displayName,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = TextPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "Длительность: ${SubtitleEngine.formatTimeMs(vid.durationMs)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = ElectricCyan
                                        )
                                    }
                                    IconButton(onClick = { shareVideo(vid) }) {
                                        Icon(
                                            imageVector = Icons.Rounded.Share,
                                            contentDescription = stringResource(R.string.cd_share_video),
                                            tint = ElectricCyan
                                        )
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = NeonPurple, contentColor = Color.White)
                    ) {
                        Text("Отлично")
                    }
                }
            )
        }

        is ExportState.Error -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                containerColor = SurfaceDark,
                icon = {
                    Icon(
                        imageVector = Icons.Rounded.ErrorOutline,
                        contentDescription = null,
                        tint = HotCoral,
                        modifier = Modifier.size(36.dp)
                    )
                },
                title = { Text("Не удалось сохранить", color = TextPrimary) },
                text = { Text(exportState.message, color = TextSecondary) },
                confirmButton = {
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = HotCoral, contentColor = Color.White)
                    ) {
                        Text("Понятно")
                    }
                }
            )
        }
    }
}
