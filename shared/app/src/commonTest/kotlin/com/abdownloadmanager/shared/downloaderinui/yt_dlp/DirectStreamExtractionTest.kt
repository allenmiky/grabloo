package com.abdownloadmanager.shared.downloaderinui.yt_dlp

import ir.amirab.downloader.downloaditem.yt_dlp.YtDlpFormat
import ir.amirab.downloader.downloaditem.yt_dlp.YtDlpVideoInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DirectStreamExtractionTest {

    @Test
    fun testDirectStreamExtractionAndMapping() {
        val videoFormat = YtDlpFormat(
            formatId = "137",
            extension = "mp4",
            resolution = "1920x1080",
            frameRate = 30.0,
            fileSize = 150_000_000L,
            videoCodec = "avc1.640028",
            audioCodec = "none",
            note = "1080p",
            protocol = "https",
            url = "https://rr1---sn-example.googlevideo.com/videoplayback?id=123",
            httpHeaders = mapOf("User-Agent" to "Mozilla/5.0"),
        )

        val audioFormat = YtDlpFormat(
            formatId = "140",
            extension = "m4a",
            resolution = null,
            frameRate = null,
            fileSize = 10_000_000L,
            videoCodec = "none",
            audioCodec = "mp4a.40.2",
            note = "medium audio",
            protocol = "https",
            url = "https://rr1---sn-example.googlevideo.com/videoplayback?id=456",
            httpHeaders = mapOf("User-Agent" to "Mozilla/5.0"),
        )

        val videoInfo = YtDlpVideoInfo(
            title = "Test Video",
            duration = 120.0,
            fileSize = 160_000_000L,
            thumbnailUrl = "https://example.com/thumb.jpg",
            isLive = false,
            formats = listOf(videoFormat, audioFormat),
            requestedFormats = listOf(videoFormat, audioFormat),
        )

        assertTrue(videoInfo.hasDirectMediaStreams)
        assertNotNull(videoInfo.videoStream)
        assertEquals("137", videoInfo.videoStream?.formatId)
        assertEquals("https://rr1---sn-example.googlevideo.com/videoplayback?id=123", videoInfo.videoStream?.url)

        assertNotNull(videoInfo.audioStream)
        assertEquals("140", videoInfo.audioStream?.formatId)
        assertEquals("https://rr1---sn-example.googlevideo.com/videoplayback?id=456", videoInfo.audioStream?.url)

        val responseInfo = videoInfo.toResponseInfo()
        assertEquals(2, responseInfo.requestedFormats.size)
        assertEquals("137", responseInfo.requestedFormats[0].formatId)
        assertEquals("140", responseInfo.requestedFormats[1].formatId)
    }

    @Test
    fun testProgressiveSingleStream() {
        val progressiveFormat = YtDlpFormat(
            formatId = "18",
            extension = "mp4",
            resolution = "640x360",
            frameRate = 30.0,
            fileSize = 25_000_000L,
            videoCodec = "avc1.42001E",
            audioCodec = "mp4a.40.2",
            note = "360p",
            protocol = "https",
            url = "https://example.com/single_video.mp4",
            httpHeaders = mapOf("Referer" to "https://example.com"),
        )

        val videoInfo = YtDlpVideoInfo(
            title = "Single Stream Video",
            duration = 60.0,
            fileSize = 25_000_000L,
            thumbnailUrl = null,
            isLive = false,
            formats = listOf(progressiveFormat),
            requestedFormats = listOf(progressiveFormat),
            directUrl = progressiveFormat.url,
            httpHeaders = progressiveFormat.httpHeaders,
        )

        assertTrue(videoInfo.hasDirectMediaStreams)
        assertNotNull(videoInfo.videoStream)
        assertEquals("https://example.com/single_video.mp4", videoInfo.directUrl)
        assertEquals("https://example.com", videoInfo.httpHeaders["Referer"])
    }
}
