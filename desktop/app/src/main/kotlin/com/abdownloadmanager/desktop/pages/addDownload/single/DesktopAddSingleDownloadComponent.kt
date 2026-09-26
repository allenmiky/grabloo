package com.abdownloadmanager.desktop.pages.addDownload.single

import com.abdownloadmanager.shared.binarymanager.BinaryDownloadService
import com.abdownloadmanager.shared.binarymanager.openBinaryDownloadDialogAndAwait
import com.abdownloadmanager.shared.downloaderinui.DownloaderInUi
import com.abdownloadmanager.shared.downloaderinui.yt_dlp.YtDlpDownloaderInUi
import com.abdownloadmanager.shared.pagemanager.BinaryDownloadDialogManager
import com.abdownloadmanager.shared.pagemanager.CategoryDialogManager
import com.abdownloadmanager.shared.pagemanager.DownloadErrorDialogManager
import com.abdownloadmanager.shared.pages.adddownload.AddDownloadCredentialsInUiProps
import com.abdownloadmanager.shared.pages.adddownload.ImportOptions
import com.abdownloadmanager.shared.pages.adddownload.single.BaseAddSingleDownloadComponent
import com.abdownloadmanager.shared.pages.adddownload.single.OnRequestAddSingleItem
import com.abdownloadmanager.shared.pages.adddownload.single.OnRequestDownloadSingleItem
import com.abdownloadmanager.shared.repository.BaseAppRepository
import com.abdownloadmanager.shared.storage.appsettings.BaseAppSettingsStorage
import com.abdownloadmanager.shared.storage.ILastSavedLocationsStorage
import com.abdownloadmanager.shared.storage.ISelectQueueStorage
import com.abdownloadmanager.shared.util.DownloadItemOpener
import com.abdownloadmanager.shared.util.DownloadSystem
import com.abdownloadmanager.shared.util.FileIconProvider
import com.abdownloadmanager.shared.util.category.CategoryManager
import com.abdownloadmanager.shared.util.perhostsettings.PerHostSettingsManager
import com.arkivanov.decompose.ComponentContext
import ir.amirab.downloader.downloaditem.DownloadJobExtraConfig
import ir.amirab.downloader.downloaditem.IDownloadCredentials
import ir.amirab.downloader.queue.QueueManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async

class DesktopAddSingleDownloadComponent(
    ctx: ComponentContext,
    onRequestClose: () -> Unit,
    onRequestDownload: OnRequestDownloadSingleItem,
    onRequestAddToQueue: OnRequestAddSingleItem,
    openExistingDownload: (Long) -> Unit,
    updateExistingDownloadCredentials: (Long, IDownloadCredentials, DownloadJobExtraConfig?) -> Unit,
    downloadItemOpener: DownloadItemOpener,
    lastSavedLocationsStorage: ILastSavedLocationsStorage,
    selectQueueStorage: ISelectQueueStorage,
    queueManager: QueueManager,
    categoryManager: CategoryManager,
    downloadSystem: DownloadSystem,
    appSettings: BaseAppSettingsStorage,
    iconProvider: FileIconProvider,
    appScope: CoroutineScope,
    appRepository: BaseAppRepository,
    perHostSettingsManager: PerHostSettingsManager,
    importOptions: ImportOptions,
    id: String,
    downloaderInUi: DownloaderInUi<IDownloadCredentials, *, *, *, *, *, *, *, *, *>,
    initialCredentials: AddDownloadCredentialsInUiProps,
    downloadErrorDialogManager: DownloadErrorDialogManager,
    private val categoryDialogManager: CategoryDialogManager,
    private val binaryDownloadDialogManager: BinaryDownloadDialogManager,
    private val binaryDownloadService: BinaryDownloadService,
) : BaseAddSingleDownloadComponent(
    ctx = ctx,
    onRequestClose = onRequestClose,
    onRequestDownload = { item, categoryId ->
        appScope.async {
            if (awaitRequiredBinaries(
                    isYtDlp = downloaderInUi is YtDlpDownloaderInUi,
                    binaryDownloadService = binaryDownloadService,
                    binaryDownloadDialogManager = binaryDownloadDialogManager,
                )
            ) {
                onRequestDownload(item, categoryId)
            }
            -1L
        }
    },
    onRequestAddToQueue = { item, queueId, categoryId ->
        appScope.async {
            if (awaitRequiredBinaries(
                    isYtDlp = downloaderInUi is YtDlpDownloaderInUi,
                    binaryDownloadService = binaryDownloadService,
                    binaryDownloadDialogManager = binaryDownloadDialogManager,
                )
            ) {
                onRequestAddToQueue(item, queueId, categoryId).await()
            } else {
                -1L
            }
        }
    },
    openExistingDownload = openExistingDownload,
    updateExistingDownloadCredentials = updateExistingDownloadCredentials,
    downloadItemOpener = downloadItemOpener,
    lastSavedLocationsStorage = lastSavedLocationsStorage,
    selectQueueStorage = selectQueueStorage,
    queueManager = queueManager,
    categoryManager = categoryManager,
    downloadSystem = downloadSystem,
    appSettings = appSettings,
    iconProvider = iconProvider,
    appRepository = appRepository,
    appScope = appScope,
    perHostSettingsManager = perHostSettingsManager,
    importOptions = importOptions,
    id = id,
    downloaderInUi = downloaderInUi,
    initialCredentials = initialCredentials,
    downloadErrorDialogManager = downloadErrorDialogManager,
) {
    override fun getCategoryPageManager(): CategoryDialogManager {
        return categoryDialogManager
    }
}

/**
 * yt-dlp needs its binaries, so a download is only started once they are ready.
 */
private suspend fun awaitRequiredBinaries(
    isYtDlp: Boolean,
    binaryDownloadService: BinaryDownloadService,
    binaryDownloadDialogManager: BinaryDownloadDialogManager,
): Boolean {
    if (!isYtDlp) return true
    if (binaryDownloadService.areBinariesAvailable()) return true
    return binaryDownloadDialogManager.openBinaryDownloadDialogAndAwait()
}
