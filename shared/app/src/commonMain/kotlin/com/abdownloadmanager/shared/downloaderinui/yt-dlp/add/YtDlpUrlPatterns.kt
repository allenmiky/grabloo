package com.abdownloadmanager.shared.downloaderinui.yt_dlp.add

object YtDlpUrlPatterns {

    // Domains where yt-dlp is the correct downloader (video/audio platforms).
    private val knownSupportedPatterns = listOf(
        // YouTube
        "youtube.com".toRegex(),
        "youtu.be".toRegex(),
        "youtube-nocookie.com".toRegex(),
        "m.youtube.com".toRegex(),

        // Vimeo
        "vimeo.com".toRegex(),

        // Twitch
        "twitch.tv".toRegex(),
        "clips.twitch.tv".toRegex(),

        // Twitter/X
        "twitter.com".toRegex(),
        "x.com".toRegex(),
        "t.co".toRegex(),

        // Instagram
        "instagram.com".toRegex(),

        // TikTok
        "tiktok.com".toRegex(),
        "vm.tiktok.com".toRegex(),

        // Facebook
        "facebook.com".toRegex(),
        "fb.watch".toRegex(),

        // Reddit (video posts only — but we guard by extension check below)
        "v.redd.it".toRegex(),

        // SoundCloud
        "soundcloud.com".toRegex(),

        // Bandcamp
        "bandcamp.com".toRegex(),

        // Dailymotion
        "dailymotion.com".toRegex(),

        // Bilibili
        "bilibili.com".toRegex(),
        "b23.tv".toRegex(),

        // Niconico
        "nicovideo.jp".toRegex(),

        // Rumble
        "rumble.com".toRegex(),

        // Odysee/LBRY
        "odysee.com".toRegex(),

        // PeerTube instances (common URL pattern)
        "peertube".toRegex(),
    )

    /**
     * Direct-file extensions that must NEVER be routed to yt-dlp,
     * even if the hosting domain is a known video platform.
     * e.g. reddit.com/attachment.zip or twitter.com/image.png
     */
    private val directFileExtensions = listOf(
        // Archives / installers
        ".zip", ".rar", ".7z", ".tar", ".gz", ".bz2", ".xz",
        ".exe", ".msi", ".dmg", ".pkg", ".deb", ".rpm", ".apk",
        // Direct media (HTTP downloader handles these fine without yt-dlp extraction)
        ".mp4", ".mkv", ".webm", ".avi", ".mov", ".flv", ".ts",
        ".mp3", ".m4a", ".flac", ".wav", ".ogg", ".aac",
        // Images
        ".jpg", ".jpeg", ".png", ".gif", ".webp",
        // Documents
        ".pdf", ".doc", ".docx", ".xls", ".xlsx", ".ppt", ".pptx",
        // Disk images
        ".iso", ".img",
    )

    /**
     * Extract the URL path before any query string, then check its extension.
     * e.g. "https://reddit.com/file.zip?token=abc" → path = "/file.zip" → true
     */
    private fun hasDirectFileExtension(url: String): Boolean {
        val path = url.substringBefore("?").substringBefore("#").lowercase()
        return directFileExtensions.any { path.endsWith(it) }
    }

    /**
     * Returns true only when:
     *  1. The URL belongs to a known video/audio platform, AND
     *  2. The URL does not point to a direct downloadable file.
     *
     * This prevents yt-dlp from intercepting zip files, images, or direct
     * media files hosted on video-platform domains (e.g. reddit.com/r/.../file.zip).
     */
    fun isYtDlpSupported(link: String): Boolean {
        val lower = link.lowercase()
        // Never intercept direct file downloads — let HTTP handle them
        if (hasDirectFileExtension(lower)) return false
        // Only claim known video/audio platforms
        return knownSupportedPatterns.any { it.containsMatchIn(lower) }
    }
}