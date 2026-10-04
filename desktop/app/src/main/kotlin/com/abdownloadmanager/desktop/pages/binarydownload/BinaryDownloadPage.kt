package com.abdownloadmanager.desktop.pages.binarydownload

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.abdownloadmanager.desktop.window.custom.WindowTitle
import com.abdownloadmanager.resources.Res
import com.abdownloadmanager.shared.ui.widget.ActionButton
import com.abdownloadmanager.shared.ui.widget.LoadingIndicator
import com.abdownloadmanager.shared.ui.widget.Text
import ir.amirab.util.datasize.ConvertSizeConfig
import com.abdownloadmanager.shared.util.LocalSizeUnit
import com.abdownloadmanager.shared.util.convertPositiveSizeToHumanReadable
import com.abdownloadmanager.shared.util.convertPositiveSpeedToHumanReadable
import com.abdownloadmanager.shared.util.ui.myColors
import ir.amirab.util.binarymanager.BinaryDownloadProgress
import ir.amirab.util.binarymanager.BinaryPhase
import ir.amirab.util.binarymanager.Binaries
import ir.amirab.util.compose.StringSource
import ir.amirab.util.compose.asStringSource
import ir.amirab.util.compose.resources.myStringResource
import kotlinx.coroutines.flow.combine
import java.text.NumberFormat
import java.util.Locale

@Composable
fun BinaryDownloadPage(
    component: DesktopBinaryDownloadComponent,
) {
    WindowTitle(myStringResource(Res.string.binary_download_title))

    val isComplete by component.isComplete.collectAsState()
    val isSuccess by component.isSuccess.collectAsState()
    val isDownloading by component.isDownloading.collectAsState()
    val ytDlpProgress by component.ytDlpProgress.collectAsState()
    val ffmpegProgress by component.ffmpegProgress.collectAsState()
    val errorMessage by component.errorMessage.collectAsState()
    val failedBinaries by component.failedBinaries.collectAsState()

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = myStringResource(Res.string.binary_download_description))

        when {
            isComplete && isSuccess -> {
                Text(
                    text = myStringResource(Res.string.binary_download_complete),
                    color = myColors.success,
                )
            }

            isComplete -> {
                Text(
                    text = myStringResource(Res.string.binary_download_failed),
                    color = myColors.error,
                )
                errorMessage?.let { Text(text = it, maxLines = 4) }
            }

            else -> {
                BinaryProgressContent(
                    ytDlpProgress = ytDlpProgress,
                    ffmpegProgress = ffmpegProgress,
                    isDownloading = isDownloading,
                )
            }
        }

        Spacer(Modifier.weight(1f))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        ) {
            if (isComplete) {
                if (failedBinaries.isNotEmpty()) {
                    ActionButton(
                        text = myStringResource(Res.string.binary_download_retry_failed),
                        onClick = component::retryFailed,
                    )
                } else {
                    ActionButton(
                        text = myStringResource(Res.string.binary_download_retry),
                        onClick = component::retry,
                    )
                }
            } else if (isDownloading) {
                ActionButton(
                    text = myStringResource(Res.string.cancel),
                    onClick = component::cancel,
                )
            }
            ActionButton(
                text = myStringResource(Res.string.close),
                onClick = component::onRequestClose,
                enabled = isComplete,
            )
        }
    }
}

@Composable
private fun BinaryProgressContent(
    ytDlpProgress: BinaryDownloadProgress?,
    ffmpegProgress: BinaryDownloadProgress?,
    isDownloading: Boolean,
) {
    val sizeUnit = LocalSizeUnit.current

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // yt-dlp row
        ytDlpProgress?.let {
            BinaryRow(
                binaryName = Binaries.YT_DLP,
                displayName = myStringResource(Res.string.binary_download_yt_dlp),
                progress = it,
                isDownloading = isDownloading,
                sizeUnit = sizeUnit,
            )
        }

        // FFmpeg row
        ffmpegProgress?.let {
            BinaryRow(
                binaryName = Binaries.FFMPEG,
                displayName = myStringResource(Res.string.binary_download_ffmpeg),
                progress = it,
                isDownloading = isDownloading,
                sizeUnit = sizeUnit,
            )
        }

        // Combined progress if only one is shown
        if (ytDlpProgress == null && ffmpegProgress == null) {
            BinaryRow(
                binaryName = "",
                displayName = "Preparing download\u2026",
                progress = BinaryDownloadProgress(
                    binaryName = "",
                    phase = BinaryPhase.Resolve,
                    isComplete = false,
                ),
                isDownloading = isDownloading,
                sizeUnit = sizeUnit,
            )
        }
    }
}

@Composable
private fun BinaryRow(
    binaryName: String,
    displayName: String,
    progress: BinaryDownloadProgress,
    isDownloading: Boolean,
    sizeUnit: ConvertSizeConfig,
) {
    val percent = progress.takeIf { it.totalBytes > 0 }
        ?.let { ((it.bytesDownloaded * 100.0) / it.totalBytes).coerceIn(0.0, 100.0).toInt() }

    val phaseText = when (progress.phase) {
        BinaryPhase.Resolve -> myStringResource(Res.string.binary_phase_resolve)
        BinaryPhase.Download -> myStringResource(Res.string.binary_phase_download)
        BinaryPhase.Verify -> myStringResource(Res.string.binary_phase_verify)
        BinaryPhase.Extract -> myStringResource(Res.string.binary_phase_extract)
        BinaryPhase.Complete -> myStringResource(Res.string.binary_phase_complete)
        BinaryPhase.Error -> myStringResource(Res.string.binary_phase_error)
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Header with name and phase
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        ) {
            Text(
                text = displayName,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
            )
            if (progress.phase != BinaryPhase.Resolve && progress.phase != BinaryPhase.Complete) {
                Text(
                    text = phaseText,
                    fontSize = 12.sp,
                    color = myColors.dimText,
                )
            }
        }

        // Progress bar
        LoadingIndicator(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp),
            progress = (percent ?: 0) / 100f,
        )

        // Details row
        if (progress.totalBytes > 0) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
            ) {
                // Downloaded / Total
                Text(
                    text = StringSource.CombinedStringSource(
                        listOf(
                            convertPositiveSizeToHumanReadable(progress.bytesDownloaded, sizeUnit),
                            " / ".asStringSource(),
                            convertPositiveSizeToHumanReadable(progress.totalBytes, sizeUnit),
                            " (${percent ?: 0}%)".asStringSource(),
                        ),
                        " ",
                    ).rememberString(),
                    fontSize = 12.sp,
                )

                // Speed
                if (progress.speed > 0) {
                    Text(
                        text = convertPositiveSpeedToHumanReadable(progress.speed, sizeUnit),
                        fontSize = 12.sp,
                    )
                }

                // ETA
                if (progress.speed > 0 && progress.totalBytes > progress.bytesDownloaded) {
                    val etaSeconds = (progress.totalBytes - progress.bytesDownloaded) / progress.speed.coerceAtLeast(1)
                    Text(
                        text = myStringResource(Res.string.binary_download_eta, mapOf("value" to formatEta(etaSeconds))),
                        fontSize = 12.sp,
                        color = myColors.dimText,
                    )
                }
            }
        }
    }
}

private fun formatEta(seconds: Long): String {
    return when {
        seconds < 60 -> "${seconds}s"
        seconds < 3600 -> "${seconds / 60}m ${seconds % 60}s"
        else -> "${seconds / 3600}h ${(seconds % 3600) / 60}m"
    }
}