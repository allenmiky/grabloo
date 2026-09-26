package ir.amirab.downloader.downloaditem.yt_dlp

import ir.amirab.downloader.DownloadManager
import ir.amirab.downloader.Downloader
import ir.amirab.downloader.downloaditem.DownloadJob
import ir.amirab.downloader.downloaditem.IDownloadCredentials
import ir.amirab.downloader.downloaditem.IDownloadItem
import kotlinx.serialization.KSerializer
import kotlin.reflect.KClass

class YtDlpDownloader : Downloader<YtDlpDownloadItem, YtDlpDownloadJob, YtDlpDownloadCredentials> {

    override fun createJob(
        item: YtDlpDownloadItem,
        downloadManager: DownloadManager,
    ): YtDlpDownloadJob {
        return YtDlpDownloadJob(item, downloadManager)
    }

    override fun accept(item: IDownloadItem): Boolean {
        return item is YtDlpDownloadItem
    }

    override val downloadItemClass: KClass<YtDlpDownloadItem> = YtDlpDownloadItem::class
    override val downloadCredentialsClass: KClass<YtDlpDownloadCredentials> = YtDlpDownloadCredentials::class
    override val downloadJobClass: KClass<YtDlpDownloadJob> = YtDlpDownloadJob::class
    override val downloadItemSerializer: KSerializer<YtDlpDownloadItem> = YtDlpDownloadItem.serializer()
    override val downloadCredentialsSerializer: KSerializer<YtDlpDownloadCredentials> = YtDlpDownloadCredentials.serializer()
}