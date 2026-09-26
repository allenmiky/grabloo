package com.abdownloadmanager.shared.downloaderinui.yt_dlp

import com.abdownloadmanager.shared.downloaderinui.CredentialAndItemMapper
import ir.amirab.downloader.downloaditem.yt_dlp.YtDlpDownloadCredentials
import ir.amirab.downloader.downloaditem.yt_dlp.YtDlpDownloadItem

class YtDlpCredentialsToItemMapper : CredentialAndItemMapper<
        YtDlpDownloadCredentials,
        YtDlpDownloadItem,
        > {
    override fun itemToCredentials(item: YtDlpDownloadItem): YtDlpDownloadCredentials {
        return YtDlpDownloadCredentials(
            link = item.link,
            downloadPage = item.downloadPage,
            headers = item.headers,
            username = item.username,
            password = item.password,
            userAgent = item.userAgent,
            title = item.title,
            thumbnailUrl = item.thumbnailUrl,
        )
    }

    override fun appliedCredentialsToItem(
        item: YtDlpDownloadItem,
        credentials: YtDlpDownloadCredentials,
    ): YtDlpDownloadItem {
        return item.copy(
            link = credentials.link,
            downloadPage = credentials.downloadPage,
            headers = credentials.headers,
            username = credentials.username,
            password = credentials.password,
            userAgent = credentials.userAgent,
            title = credentials.title,
            thumbnailUrl = credentials.thumbnailUrl,
        )
    }

    override fun itemWithEditedName(item: YtDlpDownloadItem, name: String): YtDlpDownloadItem {
        return item.copy(name = name)
    }

    override fun credentialsWithEditedLink(
        credentials: YtDlpDownloadCredentials,
        link: String,
    ): YtDlpDownloadCredentials {
        return credentials.copy(link = link)
    }
}
