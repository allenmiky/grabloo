package com.abdownloadmanager.shared.downloaderinui.yt_dlp.add

object YtDlpUrlPatterns {

    // yt-dlp supports 1000+ sites. We'll match common video/streaming platforms.
    // For unknown domains, we fall back to trying yt-dlp as a last resort.
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

        // Reddit
        "reddit.com".toRegex(),
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

        // PeerTube instances (common pattern)
        "peertube".toRegex(),

        // Generic m3u8/mpd (HLS/DASH) - many sites
        // These might also be caught by HLS downloader, but yt-dlp can handle them too
    )

    fun isYtDlpSupported(link: String): Boolean {
        val lower = link.lowercase()
        // Check known patterns
        return knownSupportedPatterns.any { it.containsMatchIn(lower) }
    }

    // Called as fallback: if HTTP/HLS both fail, try yt-dlp for any URL
    fun shouldTryAsFallback(link: String): Boolean {
        val lower = link.lowercase()
        // Don't try for obvious direct file links
        if (lower.endsWith(".mp4") || lower.endsWith(".mkv") || lower.endsWith(".webm") ||
            lower.endsWith(".mp3") || lower.endsWith(".m4a") || lower.endsWith(".flac") ||
            lower.endsWith(".zip") || lower.endsWith(".rar") || lower.endsWith(".7z") ||
            lower.endsWith(".exe") || lower.endsWith(".apk") || lower.endsWith(".dmg") ||
            lower.endsWith(".iso") || lower.endsWith(".img")) {
            return false
        }
        // Don't try for obvious non-video pages (but this is heuristic)
        return true
    }
}