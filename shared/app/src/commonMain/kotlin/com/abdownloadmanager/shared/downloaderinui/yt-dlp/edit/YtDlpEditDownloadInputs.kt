package com.abdownloadmanager.shared.downloaderinui.yt_dlp.edit

import com.abdownloadmanager.resources.Res
import com.abdownloadmanager.shared.downloaderinui.DownloadSize
import com.abdownloadmanager.shared.downloaderinui.LinkCheckerFactory
import com.abdownloadmanager.shared.downloaderinui.edit.DownloadConflictDetector
import com.abdownloadmanager.shared.downloaderinui.edit.EditDownloadCheckerFactory
import com.abdownloadmanager.shared.downloaderinui.edit.EditDownloadInputs
import com.abdownloadmanager.shared.downloaderinui.yt_dlp.YtDlpCredentialsToItemMapper
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
import ir.amirab.downloader.downloaditem.DownloadJobExtraConfig
import ir.amirab.downloader.downloaditem.yt_dlp.YtDlpDownloadCredentials
import ir.amirab.downloader.downloaditem.yt_dlp.YtDlpDownloadItem
import ir.amirab.util.compose.StringSource
import ir.amirab.util.compose.asStringSource
import ir.amirab.util.compose.asStringSourceWithARgs
import ir.amirab.util.flow.mapTwoWayStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class YtDlpEditDownloadInputs(
    currentDownloadItem: MutableStateFlow<YtDlpDownloadItem>,
    editedDownloadItem: MutableStateFlow<YtDlpDownloadItem>,
    mapper: YtDlpCredentialsToItemMapper,
    conflictDetector: DownloadConflictDetector,
    linkCheckerFactory: LinkCheckerFactory<YtDlpDownloadCredentials, YtDlpResponseInfo, DownloadSize.Bytes, YtDlpLinkChecker>,
    editDownloadCheckerFactory: EditDownloadCheckerFactory<YtDlpDownloadItem, YtDlpDownloadCredentials, YtDlpResponseInfo, DownloadSize.Bytes, YtDlpLinkChecker>,
    scope: CoroutineScope,
    private val sizeAndSpeedUnitProvider: SizeAndSpeedUnitProvider,
) : EditDownloadInputs<
        YtDlpDownloadItem,
        YtDlpDownloadCredentials,
        YtDlpResponseInfo,
        DownloadSize.Bytes,
        YtDlpLinkChecker,
        YtDlpCredentialsToItemMapper
        >(
    currentDownloadItem,
    editedDownloadItem,
    mapper = mapper,
    scope = scope,
    conflictDetector = conflictDetector,
    linkCheckerFactory = linkCheckerFactory,
    editDownloadCheckerFactory = editDownloadCheckerFactory,
) {
    override val downloadJobConfig: StateFlow<DownloadJobExtraConfig?> = MutableStateFlow(null)

    override val configurableList = listOf(
        SpeedLimitConfigurable(
            Res.string.download_item_settings_speed_limit.asStringSource(),
            Res.string.download_item_settings_speed_limit_description.asStringSource(),
            backedBy = editedDownloadItem.mapTwoWayStateFlow(
                map = { it.speedLimit },
                unMap = { copy(speedLimit = it) }
            ),
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
            backedBy = editedDownloadItem.mapTwoWayStateFlow(
                map = {
                    it.fileChecksum?.let { checksum ->
                        runCatching { FileChecksum.fromString(checksum) }.getOrNull()
                    }
                },
                unMap = { copy(fileChecksum = it?.toString()) }
            ),
            describe = { "".asStringSource() }
        ),
        IntConfigurable(
            Res.string.settings_download_thread_count.asStringSource(),
            Res.string.settings_download_thread_count_description.asStringSource(),
            backedBy = editedDownloadItem.mapTwoWayStateFlow(
                map = { it.preferredConnectionCount ?: 0 },
                unMap = { copy(preferredConnectionCount = it.takeIf { count -> count >= 1 }) }
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
            Res.string.yt_dlp_format_id.asStringSource(),
            Res.string.yt_dlp_format_id_description.asStringSource(),
            backedBy = editedDownloadItem.mapTwoWayStateFlow(
                map = { it.selectedFormatId.orEmpty() },
                unMap = { copy(selectedFormatId = it.takeIf { value -> value.isNotBlank() }) }
            ),
            describe = { it.asStringSource() }
        ),
        StringConfigurable(
            Res.string.username.asStringSource(),
            Res.string.download_item_settings_username_description.asStringSource(),
            backedBy = credentials.mapTwoWayStateFlow(
                map = { it.username.orEmpty() },
                unMap = { copy(username = it.takeIf { value -> value.isNotEmpty() }) }
            ),
            describe = { "".asStringSource() }
        ),
        StringConfigurable(
            Res.string.password.asStringSource(),
            Res.string.download_item_settings_password_description.asStringSource(),
            backedBy = credentials.mapTwoWayStateFlow(
                map = { it.password.orEmpty() },
                unMap = { copy(password = it.takeIf { value -> value.isNotEmpty() }) }
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

    private fun YtDlpDownloadItem.applyOurChanges(edited: YtDlpDownloadItem) {
        link = edited.link
        headers = edited.headers
        username = edited.username
        password = edited.password
        downloadPage = edited.downloadPage
        userAgent = edited.userAgent
        title = edited.title
        thumbnailUrl = edited.thumbnailUrl

        folder = edited.folder
        name = edited.name

        contentLength = edited.contentLength

        preferredConnectionCount = edited.preferredConnectionCount
        speedLimit = edited.speedLimit
        fileChecksum = edited.fileChecksum

        selectedFormatId = edited.selectedFormatId
        duration = edited.duration
        isLive = edited.isLive
    }

    override fun applyEditedItemTo(item: YtDlpDownloadItem) {
        val edited = editedDownloadItem.value
        item.applyOurChanges(edited)
    }

    override fun downloadSizeToStringSource(downloadSize: DownloadSize.Bytes): StringSource {
        return downloadSize.asStringSource(sizeAndSpeedUnitProvider.sizeUnit.value)
    }
}
