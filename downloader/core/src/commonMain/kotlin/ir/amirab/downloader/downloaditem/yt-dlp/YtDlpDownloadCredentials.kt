package ir.amirab.downloader.downloaditem.yt_dlp

import arrow.core.Option
import arrow.core.getOrElse
import ir.amirab.downloader.downloaditem.IDownloadCredentials
import ir.amirab.downloader.downloaditem.http.IHttpBasedDownloadCredentials
import ir.amirab.util.HttpUrlUtils
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("yt-dlp")
data class YtDlpDownloadCredentials(
    override val headers: Map<String, String>? = null,
    override val username: String? = null,
    override val password: String? = null,
    override val userAgent: String? = null,
    override val link: String,
    override val downloadPage: String? = null,
    override val title: String? = null,
    override val thumbnailUrl: String? = null,
    val ytCookies: String? = null,
) : IYtDlpCredentials {
    override fun copy(
        link: Option<String>,
        downloadPage: Option<String?>
    ): IDownloadCredentials {
        return copy(
            link = link.getOrElse { this.link },
            downloadPage = downloadPage.getOrElse { this.downloadPage },
        )
    }

    override fun validateCredentials() {
        HttpUrlUtils.isValidUrl(link)
    }

    companion object {
        fun empty() = YtDlpDownloadCredentials(
            link = ""
        )
    }
}

interface IYtDlpCredentials : IHttpBasedDownloadCredentials {
    val title: String?
    val thumbnailUrl: String?
}