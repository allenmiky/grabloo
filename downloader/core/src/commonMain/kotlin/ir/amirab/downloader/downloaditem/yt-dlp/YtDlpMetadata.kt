package ir.amirab.downloader.downloaditem.yt_dlp

import ir.amirab.downloader.downloaditem.IDownloadItem

data class YtDlpVideoInfo(
    val title: String,
    val duration: Double?,
    val fileSize: Long?,
    val thumbnailUrl: String?,
    val isLive: Boolean,
    val formats: List<YtDlpFormat> = emptyList(),
    val requestedFormats: List<YtDlpFormat> = emptyList(),
    val directUrl: String? = null,
    val httpHeaders: Map<String, String> = emptyMap(),
) {
    fun asFileSize(): Long = fileSize?.takeIf { it > 0 } ?: IDownloadItem.LENGTH_UNKNOWN

    val hasDirectMediaStreams: Boolean
        get() = (requestedFormats.isNotEmpty() && requestedFormats.all { it.url != null && it.isProgressive })
            || (directUrl != null)

    val videoStream: YtDlpFormat?
        get() = requestedFormats.firstOrNull { it.hasVideo }

    val audioStream: YtDlpFormat?
        get() = requestedFormats.firstOrNull { it.hasAudio && !it.hasVideo }
            ?: requestedFormats.firstOrNull { it.hasAudio && it != videoStream }
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
    val explicitHeight: Int? = null,
    val explicitWidth: Int? = null,
    val protocol: String? = null,
    val bitrate: Long? = null,
    val dynamicRange: String? = null,
    val isDrm: Boolean = false,
    val url: String? = null,
    val httpHeaders: Map<String, String> = emptyMap(),
) {
    val hasVideo: Boolean
        get() = !videoCodec.isNullOrBlank() && videoCodec != "none"

    val hasAudio: Boolean
        get() = !audioCodec.isNullOrBlank() && audioCodec != "none"

    val height: Int
        get() = explicitHeight?.takeIf { it > 0 }
            ?: resolution
                ?.split("x", "×")
                ?.lastOrNull()
                ?.toIntOrNull()
            ?: 0

    /**
     * progressive http(s) streams only. the m3u8 (HLS) manifests yt-dlp lists are duplicates of the
     * same quality in the same video, and picking one of those makes the download much slower
     * because the segments come over a stream instead of a single file with a content length.
     */
    val isProgressive: Boolean
        get() = protocol == null || protocol == "https" || protocol == "http"

    val isHdr: Boolean
        get() = dynamicRange != null && dynamicRange != "SDR"

    /**
     * yt-dlp reports codecs with a profile and level suffix (avc1.64002a, vp09.00.50.08), so the
     * friendly name has to be matched on a prefix.
     */
    fun videoCodecName(): String? = when {
        videoCodec == null || videoCodec == "none" -> null
        videoCodec.startsWith("avc1") || videoCodec.startsWith("h264") -> "H.264"
        videoCodec.startsWith("vp9") || videoCodec.startsWith("vp09") -> "VP9"
        videoCodec.startsWith("av01") -> "AV1"
        videoCodec.startsWith("hev1") || videoCodec.startsWith("hvc1") -> "H.265"
        videoCodec.startsWith("avs") -> "AVS"
        else -> videoCodec.substringBefore('.').uppercase()
    }

    fun audioCodecName(): String? = when {
        audioCodec == null || audioCodec == "none" -> null
        audioCodec.startsWith("mp4a") -> "AAC"
        audioCodec.startsWith("opus") -> "Opus"
        audioCodec.startsWith("vorbis") -> "Vorbis"
        audioCodec.startsWith("ec-3") || audioCodec.startsWith("eac3") -> "E-AC3"
        audioCodec.startsWith("ac-3") || audioCodec.startsWith("ac3") -> "AC3"
        else -> audioCodec.substringBefore('.').uppercase()
    }

    /**
     * how playable the stream is on a device that only knows the common codecs. the app always
     * merges into mp4, and an av1 or vp9 track inside an mp4 is a file that a lot of phones and
     * desktop players refuse, so h264 wins wherever YouTube still offers it (it only goes up to
     * 1080p, above that there is no h264 and the higher bitrate stream is the best left).
     */
    fun codecCompatibilityScore(): Int {
        val codec = videoCodec.orEmpty()
        return when {
            codec.startsWith("avc1") || codec.startsWith("h264") -> 300
            codec.startsWith("vp9") || codec.startsWith("vp09") -> 200
            codec.startsWith("hev1") || codec.startsWith("hvc1") -> 150
            codec.startsWith("av01") -> 100
            codec.isBlank() -> 0
            else -> 50
        }
    }

    /**
     * used to pick one representative format out of all the streams that share a resolution, so the
     * quality list shows what will actually be downloaded instead of every single stream.
     * [isBetterThan] is the one to use for that: the codec comes first on purpose, see
     * [codecCompatibilityScore].
     */
    fun isBetterThan(other: YtDlpFormat): Boolean {
        val compatibility = codecCompatibilityScore().compareTo(other.codecCompatibilityScore())
        if (compatibility != 0) return compatibility > 0
        if (isDrm != other.isDrm) return !isDrm
        if (isProgressive != other.isProgressive) return isProgressive
        val mine = bitrate ?: 0L
        val theirs = other.bitrate ?: 0L
        if (mine != theirs) return mine > theirs
        val mySize = fileSize ?: 0L
        val theirSize = other.fileSize ?: 0L
        if (mySize != theirSize) return mySize > theirSize
        return (frameRate ?: 0.0) > (other.frameRate ?: 0.0)
    }

    /**
     * the "best" entry has to mean the biggest stream, not the most compatible one. [isBetterThan]
     * deliberately puts the codec first so that a resolution group prefers h264, but applying that
     * across resolutions would make "best" stop at 1080p because that is the last height YouTube
     * still offers in h264.
     */
    fun isBiggerStreamThan(other: YtDlpFormat): Boolean {
        if (height != other.height) return height > other.height
        if (isDrm != other.isDrm) return !isDrm
        if (isProgressive != other.isProgressive) return isProgressive
        val mine = bitrate ?: 0L
        val theirs = other.bitrate ?: 0L
        if (mine != theirs) return mine > theirs
        val mySize = fileSize ?: 0L
        val theirSize = other.fileSize ?: 0L
        if (mySize != theirSize) return mySize > theirSize
        return (frameRate ?: 0.0) > (other.frameRate ?: 0.0)
    }

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
