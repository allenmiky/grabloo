package com.abdownloadmanager.shared.downloaderinui.yt_dlp.add

import com.abdownloadmanager.shared.downloaderinui.DownloadSize
import com.abdownloadmanager.shared.downloaderinui.LinkCheckerFactory
import com.abdownloadmanager.shared.downloaderinui.add.NewDownloadUiChecker
import com.abdownloadmanager.shared.downloaderinui.yt_dlp.YtDlpLinkChecker
import com.abdownloadmanager.shared.downloaderinui.yt_dlp.YtDlpResponseInfo
import com.abdownloadmanager.shared.util.DownloadSystem
import ir.amirab.downloader.downloaditem.yt_dlp.YtDlpDownloadCredentials
import kotlinx.coroutines.CoroutineScope

class YtDlpNewDownloadUIChecker(
    initCredentials: YtDlpDownloadCredentials,
    linkCheckerFactory: LinkCheckerFactory<YtDlpDownloadCredentials, YtDlpResponseInfo, DownloadSize.Bytes, YtDlpLinkChecker>,
    initialFolder: String,
    initialName: String,
    downloadSystem: DownloadSystem,
    scope: CoroutineScope,
) : NewDownloadUiChecker<YtDlpDownloadCredentials, YtDlpResponseInfo, DownloadSize.Bytes, YtDlpLinkChecker>(
    initialCredentials = initCredentials,
    linkCheckerFactory = linkCheckerFactory,
    initialFolder = initialFolder,
    initialName = initialName,
    downloadSystem = downloadSystem,
    scope = scope,
)
