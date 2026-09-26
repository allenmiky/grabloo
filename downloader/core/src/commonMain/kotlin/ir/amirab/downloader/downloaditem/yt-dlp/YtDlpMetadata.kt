package ir.amirab.downloader.downloaditem.yt_dlp

import ir.amirab.downloader.downloaditem.IDownloadItem

data class YtDlpVideoInfo(
    val title: String,
    val duration: Double?,
    val fileSize: Long?,
    val thumbnailUrl: String?,
    val isLive: Boolean,
) {
    fun asFileSize(): Long = fileSize?.takeIf { it > 0 } ?: IDownloadItem.LENGTH_UNKNOWN
}

data class YtDlpFormat(
    val formatId: String,
    val extension: String,
    val resolution: String?,
    val frameRate: Double?,
    val fileSize: Long?,
    val videoCodec: String?,
    val audioCodec: String?,
    val note: String?,
) {
    val hasVideo: Boolean
        get() = !videoCodec.isNullOrBlank() && videoCodec != "none"

    val hasAudio: Boolean
        get() = !audioCodec.isNullOrBlank() && audioCodec != "none"

    val height: Int
        get() = resolution
            ?.split("x", "×")
            ?.lastOrNull()
            ?.toIntOrNull()
            ?: 0

    fun asDisplayName(): String {
        val parts = mutableListOf<String>()
        resolution?.let { parts.add(it.replace("x", " ").replace("×", " ")) }
        frameRate?.let { if (it >= 50.0) parts.add("${it.toInt()}fps") }
        when {
            !hasVideo -> parts.add("audio only")
            !hasAudio -> parts.add("video only")
        }
        note?.takeIf { it.isNotBlank() && !it.contains("DASH", ignoreCase = true) }?.let {
            parts.add(it)
        }
        parts.add(extension.uppercase())
        return if (parts.isEmpty()) formatId else parts.joinToString(" · ")
    }
}
