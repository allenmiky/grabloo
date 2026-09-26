package com.abdownloadmanager.shared.downloaderinui.yt_dlp

import com.abdownloadmanager.resources.Res
import com.abdownloadmanager.shared.binarymanager.BinaryDownloadService
import com.abdownloadmanager.shared.downloaderinui.BasicDownloadItem
import com.abdownloadmanager.shared.downloaderinui.DownloadSize
import com.abdownloadmanager.shared.downloaderinui.DownloaderInUi
import com.abdownloadmanager.shared.downloaderinui.edit.DownloadConflictDetector
import com.abdownloadmanager.shared.downloaderinui.edit.EditDownloadChecker
import com.abdownloadmanager.shared.downloaderinui.yt_dlp.add.YtDlpNewDownloadInputs
import com.abdownloadmanager.shared.downloaderinui.yt_dlp.add.YtDlpNewDownloadUIChecker
import com.abdownloadmanager.shared.downloaderinui.yt_dlp.add.YtDlpUrlPatterns
import com.abdownloadmanager.shared.downloaderinui.yt_dlp.edit.YtDlpEditDownloadChecker
import com.abdownloadmanager.shared.downloaderinui.yt_dlp.edit.YtDlpEditDownloadInputs
import com.abdownloadmanager.shared.util.DownloadSystem
import com.abdownloadmanager.shared.util.SizeAndSpeedUnitProvider
import ir.amirab.downloader.downloaditem.IDownloadCredentials
import ir.amirab.downloader.downloaditem.yt_dlp.YtDlpDownloadCredentials
import ir.amirab.downloader.downloaditem.yt_dlp.YtDlpDownloadItem
import ir.amirab.downloader.downloaditem.yt_dlp.YtDlpDownloadJob
import ir.amirab.downloader.downloaditem.yt_dlp.YtDlpDownloader
import ir.amirab.downloader.monitor.ProcessingDownloadItemFactoryInputs
import ir.amirab.downloader.monitor.ExplicitProgressProcessingDownloadItemState
import ir.amirab.downloader.monitor.ProcessingDownloadItemState
import ir.amirab.downloader.utils.calcPercent
import ir.amirab.util.compose.StringSource
import ir.amirab.util.compose.asStringSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow

class YtDlpDownloaderInUi(
    downloader: YtDlpDownloader,
    private val sizeAndSpeedUnitProvider: SizeAndSpeedUnitProvider,
    private val binaryDownloadService: BinaryDownloadService,
) : DownloaderInUi<
        YtDlpDownloadCredentials,
        YtDlpResponseInfo,
        DownloadSize.Bytes,
        YtDlpLinkChecker,
        YtDlpDownloadItem,
        YtDlpNewDownloadInputs,
        YtDlpEditDownloadInputs,
        YtDlpCredentialsToItemMapper,
        YtDlpDownloadJob,
        YtDlpDownloader
        >(downloader) {
    override fun newDownloadUiChecker(
        initialCredentials: YtDlpDownloadCredentials,
        initialFolder: String,
        initialName: String,
        downloadSystem: DownloadSystem,
        scope: CoroutineScope,
    ): YtDlpNewDownloadUIChecker {
        return YtDlpNewDownloadUIChecker(
            initCredentials = initialCredentials,
            linkCheckerFactory = this,
            initialFolder = initialFolder,
            initialName = initialName,
            downloadSystem = downloadSystem,
            scope = scope,
        )
    }

    override fun acceptDownloadCredentials(item: IDownloadCredentials): Boolean {
        return item is YtDlpDownloadCredentials
    }

    override fun supportsThisLink(link: String): Boolean {
        return YtDlpUrlPatterns.isYtDlpSupported(link)
    }

    override fun createMinimumCredentials(link: String): YtDlpDownloadCredentials {
        return YtDlpDownloadCredentials(link = link)
    }

    override fun createBareDownloadItem(
        credentials: YtDlpDownloadCredentials,
        basicDownloadItem: BasicDownloadItem,
    ): YtDlpDownloadItem {
        return YtDlpDownloadItem.createWithCredentials(
            credentials = credentials,
            id = -1,
            folder = basicDownloadItem.folder,
            name = basicDownloadItem.name,
            contentLength = basicDownloadItem.contentLength,
            preferredConnectionCount = basicDownloadItem.preferredConnectionCount,
            speedLimit = basicDownloadItem.speedLimit,
            fileChecksum = basicDownloadItem.fileChecksum,
        )
    }

    override fun createLinkChecker(initialCredentials: YtDlpDownloadCredentials): YtDlpLinkChecker {
        return YtDlpLinkChecker(
            initialCredentials = initialCredentials,
            binDir = binaryDownloadService.binDir,
        )
    }

    override fun createEditDownloadChecker(
        currentDownloadItem: MutableStateFlow<YtDlpDownloadItem>,
        editedDownloadItem: MutableStateFlow<YtDlpDownloadItem>,
        linkChecker: YtDlpLinkChecker,
        conflictDetector: DownloadConflictDetector,
        scope: CoroutineScope,
    ): EditDownloadChecker<YtDlpDownloadItem, YtDlpDownloadCredentials, YtDlpResponseInfo, DownloadSize.Bytes, YtDlpLinkChecker> {
        return YtDlpEditDownloadChecker(
            currentDownloadItem = currentDownloadItem,
            editedDownloadItem = editedDownloadItem,
            linkChecker = linkChecker,
            conflictDetector = conflictDetector,
            scope = scope,
        )
    }

    override fun createNewDownloadInputs(
        initialCredentials: YtDlpDownloadCredentials,
        initialFolder: String,
        initialName: String,
        downloadSystem: DownloadSystem,
        scope: CoroutineScope,
    ): YtDlpNewDownloadInputs {
        return YtDlpNewDownloadInputs(
            newDownloadUiChecker(
                initialCredentials = initialCredentials,
                initialFolder = initialFolder,
                initialName = initialName,
                downloadSystem = downloadSystem,
                scope = scope,
            ),
            sizeAndSpeedUnitProvider,
            scope,
        )
    }

    override fun createEditDownloadInputs(
        currentDownloadItem: MutableStateFlow<YtDlpDownloadItem>,
        editedDownloadItem: MutableStateFlow<YtDlpDownloadItem>,
        conflictDetector: DownloadConflictDetector,
        scope: CoroutineScope,
    ): YtDlpEditDownloadInputs {
        return YtDlpEditDownloadInputs(
            currentDownloadItem = currentDownloadItem,
            editedDownloadItem = editedDownloadItem,
            mapper = YtDlpCredentialsToItemMapper(),
            conflictDetector = conflictDetector,
            linkCheckerFactory = this,
            editDownloadCheckerFactory = this,
            scope = scope,
            sizeAndSpeedUnitProvider = sizeAndSpeedUnitProvider,
        )
    }

    override fun createProcessingDownloadItemState(
        props: ProcessingDownloadItemFactoryInputs<YtDlpDownloadJob>,
    ): ProcessingDownloadItemState {
        val job = props.downloadJob
        val item = job.downloadItem
        val progress = job.progressFlow.value
        val downloadedBytes = progress?.downloadedBytes ?: 0L
        val speed = progress?.speed ?: props.speed
        val contentLength = item.contentLength
        return ExplicitProgressProcessingDownloadItemState(
            id = job.id,
            name = item.name,
            folder = item.folder,
            status = job.status.value,
            speed = speed,
            supportResume = true,
            contentLength = contentLength,
            parts = emptyList(),
            downloadLink = item.link,
            saveLocation = item.name,
            dateAdded = item.dateAdded,
            startTime = item.startTime ?: 0,
            completeTime = item.completeTime ?: 0,
            isWaiting = props.isWaiting,
            progress = downloadedBytes,
            percent = if (contentLength <= 0) null else calcPercent(downloadedBytes, contentLength),
        )
    }

    override val name: StringSource = Res.string.yt_dlp_downloader_name.asStringSource()
}
