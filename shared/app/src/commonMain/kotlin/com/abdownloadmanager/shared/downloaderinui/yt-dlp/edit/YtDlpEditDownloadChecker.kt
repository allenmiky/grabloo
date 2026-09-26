package com.abdownloadmanager.shared.downloaderinui.yt_dlp.edit

import com.abdownloadmanager.shared.downloaderinui.DownloadSize
import com.abdownloadmanager.shared.downloaderinui.edit.CanEditDownloadResult
import com.abdownloadmanager.shared.downloaderinui.edit.CanEditWarnings
import com.abdownloadmanager.shared.downloaderinui.edit.DownloadConflictDetector
import com.abdownloadmanager.shared.downloaderinui.edit.EditDownloadChecker
import com.abdownloadmanager.shared.downloaderinui.yt_dlp.YtDlpLinkChecker
import com.abdownloadmanager.shared.downloaderinui.yt_dlp.YtDlpResponseInfo
import ir.amirab.downloader.downloaditem.yt_dlp.YtDlpDownloadCredentials
import ir.amirab.downloader.downloaditem.yt_dlp.YtDlpDownloadItem
import ir.amirab.util.FileNameValidator
import ir.amirab.util.HttpUrlUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class YtDlpEditDownloadChecker(
    currentDownloadItem: MutableStateFlow<YtDlpDownloadItem>,
    editedDownloadItem: MutableStateFlow<YtDlpDownloadItem>,
    linkChecker: YtDlpLinkChecker,
    conflictDetector: DownloadConflictDetector,
    scope: CoroutineScope,
) : EditDownloadChecker<
        YtDlpDownloadItem,
        YtDlpDownloadCredentials,
        YtDlpResponseInfo,
        DownloadSize.Bytes,
        YtDlpLinkChecker
        >(
    currentDownloadItem = currentDownloadItem,
    editedDownloadItem = editedDownloadItem,
    linkChecker = linkChecker,
    conflictDetector = conflictDetector,
    scope = scope,
) {
    init {
        editedDownloadItem
            .onEach {
                _canEditResult.value = CanEditDownloadResult.Waiting
            }.launchIn(scope)
    }

    override fun check() {
        _canEditResult.value = CanEditDownloadResult.Waiting
        _canEditResult.value = check(
            current = currentDownloadItem.value,
            edited = editedDownloadItem.value,
            newSize = linkChecker.downloadSize.value?.bytes,
        )
    }

    private fun check(
        current: YtDlpDownloadItem,
        edited: YtDlpDownloadItem,
        newSize: Long?,
    ): CanEditDownloadResult {
        if (current == edited) {
            return CanEditDownloadResult.NothingChanged
        }
        if (!HttpUrlUtils.isValidUrl(edited.link)) {
            return CanEditDownloadResult.InvalidURL
        }
        if (edited.name != current.name) {
            if (!FileNameValidator.isValidFileName(edited.name)) {
                return CanEditDownloadResult.InvalidFileName
            }
            if (conflictDetector.checkAlreadyExists(current, edited)) {
                return CanEditDownloadResult.FileNameAlreadyExists
            }
        }
        val warnings = mutableListOf<CanEditWarnings>()
        if (newSize != null && current.contentLength > 0 && current.contentLength != newSize) {
            warnings.add(
                CanEditWarnings.FileSizeNotMatch(
                    current.contentLength,
                    newSize,
                )
            )
        }
        return CanEditDownloadResult.CanEdit(warnings)
    }
}
