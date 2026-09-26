package com.abdownloadmanager.shared.downloaderinui.yt_dlp

import ir.amirab.downloader.connection.IResponseInfo
import ir.amirab.downloader.downloaditem.yt_dlp.YtDlpVideoInfo

data class YtDlpResponseInfo(
    val title: String?,
    val fileSize: Long?,
    val isLive: Boolean,
    val unsuccessFullThrowable: Throwable? = null,
) : IResponseInfo {
    override val isSuccessFul: Boolean = unsuccessFullThrowable == null
    override val requiresAuth: Boolean = false
    override val requireBasicAuth: Boolean = false
    override val resumeSupport: Boolean = true
    override val isWebPage: Boolean = true
    override val unsuccessFullException: Throwable? = unsuccessFullThrowable
}

fun YtDlpVideoInfo.toResponseInfo(): YtDlpResponseInfo {
    return YtDlpResponseInfo(
        title = title,
        fileSize = fileSize,
        isLive = isLive,
    )
}
