package com.abdownloadmanager.shared.downloaderinui.yt_dlp.add

import com.abdownloadmanager.resources.Res
import com.abdownloadmanager.shared.downloaderinui.DownloadSize
import com.abdownloadmanager.shared.downloaderinui.add.NewDownloadInputs
import com.abdownloadmanager.shared.downloaderinui.http.applyToHttpDownload
import com.abdownloadmanager.shared.downloaderinui.yt_dlp.YtDlpLinkChecker
import com.abdownloadmanager.shared.downloaderinui.yt_dlp.YtDlpResponseInfo
import com.abdownloadmanager.shared.ui.configurable.item.FileChecksumConfigurable
import com.abdownloadmanager.shared.ui.configurable.item.IntConfigurable
import com.abdownloadmanager.shared.ui.configurable.item.SpeedLimitConfigurable
import com.abdownloadmanager.shared.ui.configurable.item.StringConfigurable
import com.abdownloadmanager.shared.util.FileChecksum
import com.abdownloadmanager.shared.util.SizeAndSpeedUnitProvider
import com.abdownloadmanager.shared.util.ThreadCountLimitation
import com.abdownloadmanager.shared.util.convertPositiveSpeedToHumanReadable
import com.abdownloadmanager.shared.util.perhostsettings.PerHostSettingsItem
import ir.amirab.downloader.downloaditem.DownloadJobExtraConfig
import ir.amirab.downloader.downloaditem.DownloadStatus
import ir.amirab.downloader.downloaditem.IDownloadItem
import ir.amirab.downloader.downloaditem.yt_dlp.YtDlpDownloadCredentials
import ir.amirab.downloader.downloaditem.yt_dlp.YtDlpDownloadItem
import ir.amirab.util.compose.StringSource
import ir.amirab.util.compose.asStringSource
import ir.amirab.util.compose.asStringSourceWithARgs
import ir.amirab.util.flow.combineStateFlows
import ir.amirab.util.flow.createMutableStateFlowFromStateFlow
import ir.amirab.util.flow.mapStateFlow
import ir.amirab.util.flow.mapTwoWayStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class YtDlpNewDownloadInputs(
    downloadUiChecker: YtDlpNewDownloadUIChecker,
    private val sizeAndSpeedUnitProvider: SizeAndSpeedUnitProvider,
    private val scope: CoroutineScope,
) : NewDownloadInputs<
        YtDlpDownloadItem,
        YtDlpDownloadCredentials,
        YtDlpResponseInfo,
        DownloadSize.Bytes,
        YtDlpLinkChecker,
        >(
    newDownloadUiChecker = downloadUiChecker
) {
    private val selectedFormatId = MutableStateFlow<String?>(null)
    private val threadCount = MutableStateFlow(null as Int?)
    private val speedLimit = MutableStateFlow(0L)
    private val fileChecksum = MutableStateFlow(null as FileChecksum?)

    private val extraSettings = combineStateFlows(
        speedLimit,
        threadCount,
        fileChecksum,
        selectedFormatId,
    ) { speedLimit, threadCount, fileChecksum, selectedFormatId ->
        ExtraSettings(speedLimit, threadCount, fileChecksum, selectedFormatId)
    }

    override val downloadItem: StateFlow<YtDlpDownloadItem> = combineStateFlows(
        credentials,
        folder,
        name,
        downloadSize,
        extraSettings,
    ) { credentials, folder, name, downloadSize, extra ->
        YtDlpDownloadItem(
            id = -1,
            folder = folder,
            name = name,
            link = credentials.link,
            contentLength = downloadSize?.bytes ?: IDownloadItem.LENGTH_UNKNOWN,
            dateAdded = openedTime,
            startTime = null,
            completeTime = null,
            status = DownloadStatus.Added,
            preferredConnectionCount = extra.threadCount,
            speedLimit = extra.speedLimit,
            fileChecksum = extra.fileChecksum?.toString(),
            selectedFormatId = extra.selectedFormatId,
            title = credentials.title,
            thumbnailUrl = credentials.thumbnailUrl,
        ).withCredentials(credentials)
    }

    override val downloadJobConfig: StateFlow<DownloadJobExtraConfig?> = MutableStateFlow(null)

    override fun applyHostSettingsToExtraConfig(extraConfig: PerHostSettingsItem) {
        extraConfig.applyToHttpDownload(
            setUsername = { setCredentials(credentials.value.copy(username = it)) },
            setPassword = { setCredentials(credentials.value.copy(password = it)) },
            setUserAgent = { setCredentials(credentials.value.copy(userAgent = it)) },
            setThreadCount = { threadCount.value = it },
            setSpeedLimit = { speedLimit.value = it }
        )
    }

    override val configurableList = listOf(
        StringConfigurable(
            Res.string.yt_dlp_format_id.asStringSource(),
            Res.string.yt_dlp_format_id_description.asStringSource(),
            backedBy = selectedFormatId.mapTwoWayStateFlow(
                map = { it.orEmpty() },
                unMap = { it.takeIf { value -> value.isNotBlank() } }
            ),
            describe = { it.asStringSource() }
        ),
        SpeedLimitConfigurable(
            Res.string.download_item_settings_speed_limit.asStringSource(),
            Res.string.download_item_settings_speed_limit_description.asStringSource(),
            backedBy = speedLimit,
            describe = {
                if (it == 0L) Res.string.unlimited.asStringSource()
                else convertPositiveSpeedToHumanReadable(
                    it, sizeAndSpeedUnitProvider.speedUnit.value
                ).asStringSource()
            }
        ),
        FileChecksumConfigurable(
            Res.string.download_item_settings_file_checksum.asStringSource(),
            Res.string.download_item_settings_file_checksum_description.asStringSource(),
            backedBy = fileChecksum,
            describe = { "".asStringSource() }
        ),
        IntConfigurable(
            Res.string.settings_download_thread_count.asStringSource(),
            Res.string.settings_download_thread_count_description.asStringSource(),
            backedBy = threadCount.mapTwoWayStateFlow(
                map = { it ?: 0 },
                unMap = { it.takeIf { value -> value >= 1 } }
            ),
            range = 0..ThreadCountLimitation.MAX_ALLOWED_THREAD_COUNT,
            describe = {
                if (it == 0) Res.string.use_global_settings.asStringSource()
                else Res.string.download_item_settings_thread_count_describe
                    .asStringSourceWithARgs(
                        Res.string.download_item_settings_thread_count_describe_createArgs(
                            count = it.toString()
                        )
                    )
            }
        ),
        StringConfigurable(
            Res.string.username.asStringSource(),
            Res.string.download_item_settings_username_description.asStringSource(),
            backedBy = createMutableStateFlowFromStateFlow(
                flow = credentials.mapStateFlow { it.username.orEmpty() },
                updater = {
                    setCredentials(credentials.value.copy(username = it.takeIf { value -> value.isNotBlank() }))
                }, scope
            ),
            describe = { "".asStringSource() }
        ),
        StringConfigurable(
            Res.string.password.asStringSource(),
            Res.string.download_item_settings_password_description.asStringSource(),
            backedBy = createMutableStateFlowFromStateFlow(
                flow = credentials.mapStateFlow { it.password.orEmpty() },
                updater = {
                    setCredentials(credentials.value.copy(password = it.takeIf { value -> value.isNotBlank() }))
                }, scope
            ),
            describe = { "".asStringSource() }
        ),
        StringConfigurable(
            Res.string.download_item_settings_user_agent.asStringSource(),
            Res.string.download_item_settings_user_agent_description.asStringSource(),
            backedBy = credentials.mapTwoWayStateFlow(
                map = { it.userAgent.orEmpty() },
                unMap = { copy(userAgent = it.takeIf { value -> value.isNotEmpty() }) }
            ),
            describe = { "".asStringSource() }
        ),
        StringConfigurable(
            Res.string.download_item_settings_download_page.asStringSource(),
            Res.string.download_item_settings_download_page_description.asStringSource(),
            backedBy = credentials.mapTwoWayStateFlow(
                map = { it.downloadPage.orEmpty() },
                unMap = { copy(downloadPage = it.takeIf { value -> value.isNotEmpty() }) }
            ),
            describe = { "".asStringSource() }
        ),
    )

    override fun downloadSizeToStringSource(downloadSize: DownloadSize.Bytes): StringSource? {
        return downloadSize.asStringSource(sizeAndSpeedUnitProvider.sizeUnit.value)
    }

    private data class ExtraSettings(
        val speedLimit: Long,
        val threadCount: Int?,
        val fileChecksum: FileChecksum?,
        val selectedFormatId: String?,
    )
}
