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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.abdownloadmanager.desktop.window.custom.WindowTitle
import com.abdownloadmanager.resources.Res
import com.abdownloadmanager.shared.ui.widget.ActionButton
import com.abdownloadmanager.shared.ui.widget.LoadingIndicator
import com.abdownloadmanager.shared.ui.widget.Text
import com.abdownloadmanager.shared.util.LocalSizeUnit
import com.abdownloadmanager.shared.util.convertPositiveSizeToHumanReadable
import com.abdownloadmanager.shared.util.convertPositiveSpeedToHumanReadable
import com.abdownloadmanager.shared.util.ui.myColors
import ir.amirab.util.binarymanager.BinaryDownloadProgress
import ir.amirab.util.binarymanager.Binaries
import ir.amirab.util.compose.StringSource
import ir.amirab.util.compose.asStringSource
import ir.amirab.util.compose.resources.myStringResource

@Composable
fun BinaryDownloadPage(
    component: DesktopBinaryDownloadComponent,
) {
    WindowTitle(myStringResource(Res.string.binary_download_title))

    val isComplete by component.isComplete.collectAsState()
    val isSuccess by component.isSuccess.collectAsState()
    val progress by component.progress.collectAsState()
    val errorMessage by component.errorMessage.collectAsState()

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
                DownloadProgressContent(progress)
            }
        }

        Spacer(Modifier.weight(1f))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        ) {
            if (isComplete) {
                ActionButton(
                    text = myStringResource(Res.string.binary_download_retry),
                    onClick = component::retry,
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
private fun DownloadProgressContent(progress: BinaryDownloadProgress?) {
    val sizeUnit = LocalSizeUnit.current
    val percent = progress
        ?.takeIf { it.totalBytes > 0 }
        ?.let { ((it.bytesDownloaded * 100.0) / it.totalBytes).coerceIn(0.0, 100.0).toInt() }

    val message: StringSource = when {
        progress == null -> Res.string.binary_download_description.asStringSource()
        progress.binaryName == Binaries.YT_DLP -> Res.string.binary_download_yt_dlp.asStringSource()
        else -> Res.string.binary_download_ffmpeg.asStringSource()
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        LoadingIndicator(
            modifier = Modifier.size(64.dp),
            progress = (percent ?: 0) / 100f,
        )
        Spacer(Modifier.height(8.dp))
        Text(text = message.rememberString(), textAlign = TextAlign.Center)
        if (progress != null && progress.totalBytes > 0) {
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
            )
        }
        if (progress != null && progress.speed > 0) {
            Text(text = convertPositiveSpeedToHumanReadable(progress.speed, sizeUnit))
        }
    }
}
