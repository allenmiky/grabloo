package com.abdownloadmanager.shared.downloaderinui.yt_dlp

import com.abdownloadmanager.shared.downloaderinui.DownloadSize
import com.abdownloadmanager.shared.downloaderinui.LinkChecker
import com.abdownloadmanager.shared.downloaderinui.yt_dlp.add.YtDlpUrlPatterns
import com.abdownloadmanager.shared.util.FilenameFixer
import ir.amirab.downloader.downloaditem.yt_dlp.YtDlpCli
import ir.amirab.downloader.downloaditem.yt_dlp.YtDlpDownloadCredentials
import ir.amirab.downloader.downloaditem.yt_dlp.YtDlpFormat
import ir.amirab.util.HttpUrlUtils
import ir.amirab.util.binarymanager.Binaries
import ir.amirab.util.flow.mapStateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

class YtDlpLinkChecker(
    initialCredentials: YtDlpDownloadCredentials,
    private val binDir: File,
) : LinkChecker<YtDlpDownloadCredentials, YtDlpResponseInfo, DownloadSize.Bytes>(
    initialCredentials = initialCredentials
) {
    private val _suggestedName = MutableStateFlow<String?>(null)
    override val suggestedName: StateFlow<String?> = _suggestedName.asStateFlow()

    private val _contentLength = MutableStateFlow<Long?>(null)
    override val downloadSize: StateFlow<DownloadSize.Bytes?> = _contentLength.mapStateFlow { bytes ->
        bytes?.let(DownloadSize::Bytes)
    }

    override fun infoUpdated(responseInfo: YtDlpResponseInfo?) {
        val rawTitle = responseInfo?.title?.takeIf { it.isNotBlank() }
        val fixedName = if (rawTitle != null) {
            val fixed = FilenameFixer.fix(rawTitle)
            val ext = responseInfo.requestedFormats.firstOrNull { it.hasVideo }?.extension
                ?: responseInfo.formats.firstOrNull { it.hasVideo }?.extension
                ?: "mp4"
            val currentExt = fixed.substringAfterLast('.', "").lowercase()
            if (currentExt in listOf("mp4", "mkv", "webm", "avi", "mov", "m4a", "mp3")) {
                fixed
            } else {
                "$fixed.$ext"
            }
        } else {
            HttpUrlUtils.extractNameFromLink(credentials.value.link)?.let(FilenameFixer::fix)
        }
        _suggestedName.value = fixedName
        _contentLength.value = responseInfo?.fileSize?.takeIf { it > 0 }
    }

    override suspend fun actualCheck(credentials: YtDlpDownloadCredentials): YtDlpResponseInfo {
        if (!YtDlpUrlPatterns.isYtDlpSupported(credentials.link)) {
            throw IllegalArgumentException("This link is not supported by yt-dlp")
        }
        return YtDlpCli.fetchVideoInfo(
            binaryPath = Binaries.getYtDlpPath(binDir),
            link = credentials.link,
            ytCookies = credentials.ytCookies,
        ).toResponseInfo()
    }

    suspend fun fetchFormats(): List<YtDlpFormat> {
        return YtDlpCli.fetchFormats(
            binaryPath = Binaries.getYtDlpPath(binDir),
            link = credentials.value.link,
            ytCookies = credentials.value.ytCookies,
        )
    }
}
