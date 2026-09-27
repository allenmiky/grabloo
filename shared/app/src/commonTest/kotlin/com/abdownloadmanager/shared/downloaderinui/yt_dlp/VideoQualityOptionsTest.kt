package com.abdownloadmanager.shared.downloaderinui.yt_dlp

import ir.amirab.downloader.downloaditem.yt_dlp.YtDlpFormat
import ir.amirab.util.datasize.CommonSizeConvertConfigs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * a trimmed version of what yt-dlp really reports for a 4K60 video. every height comes in three
 * codecs, and that is the whole point of these tests: the picker has to offer the streams that are
 * there, not a fixed ladder, and not a single silently chosen codec either.
 */
private fun youtube4k60Formats(): List<YtDlpFormat> = listOf(
    storyboard(),
    // the only stream an unverified client used to get, 360p pre-muxed
    format("18", height = 360, fps = 30.0, vcodec = "avc1.42001E", acodec = "mp4a.40.2", size = 28_526_904, tbr = 359),
    format("134", height = 360, fps = 30.0, vcodec = "avc1.4d401e", size = 18_298_496, tbr = 231),
    format("243", height = 360, fps = 30.0, vcodec = "vp09.00.30.08", ext = "webm", size = 24_117_376, tbr = 304),
    format("135", height = 480, fps = 30.0, vcodec = "avc1.4d401f", size = 28_311_552, tbr = 356),
    format("397", height = 480, fps = 30.0, vcodec = "av01.0.04M.08", size = 25_999_104, tbr = 328),
    // 720p60 and up only exist at 60fps on this video
    format("298", height = 720, fps = 60.0, vcodec = "avc1.4d4020", size = 150_538_650, tbr = 1898),
    format("302", height = 720, fps = 60.0, vcodec = "vp09.00.51.08", ext = "webm", size = 112_721_459, tbr = 1421),
    format("299", height = 1080, fps = 60.0, vcodec = "avc1.64002a", size = 257_698_304, tbr = 3248),
    format("399", height = 1080, fps = 60.0, vcodec = "av01.0.09M.08", size = 124_462_643, tbr = 1568),
    format("400", height = 1440, fps = 60.0, vcodec = "av01.0.12M.08", size = 312_475_648, tbr = 3940),
    format("315", height = 2160, fps = 60.0, vcodec = "vp09.00.51.08", ext = "webm", size = 1_361_630_720, tbr = 17174),
    format("401", height = 2160, fps = 60.0, vcodec = "av01.0.13M.08", size = 712_654_000, tbr = 8982),
    // hls duplicates of the same qualities
    format("231", height = 360, fps = 30.0, vcodec = "avc1.4D401E", protocol = "m3u8_native"),
    format("311", height = 720, fps = 60.0, vcodec = "avc1.4D4020", protocol = "m3u8_native"),
    // audio, aac has a slightly higher reported bitrate than opus on this video
    format("140", height = 0, fps = null, vcodec = "none", acodec = "mp4a.40.2", size = 10_273_477, tbr = 129),
    format("251", height = 0, fps = null, vcodec = "none", acodec = "opus", ext = "webm", size = 10_206_224, tbr = 128),
)

private fun format(
    id: String,
    height: Int,
    fps: Double?,
    vcodec: String = "none",
    acodec: String = "none",
    ext: String = "mp4",
    size: Long? = null,
    tbr: Long? = null,
    protocol: String = "https",
    dynamicRange: String? = "SDR",
    drm: Boolean = false,
) = YtDlpFormat(
    formatId = id,
    extension = ext,
    resolution = if (height > 0) "${height * 16 / 9}x$height" else null,
    frameRate = fps,
    fileSize = size,
    videoCodec = vcodec,
    audioCodec = acodec,
    note = if (id == "18") "360p" else null,
    explicitHeight = height.takeIf { it > 0 },
    explicitWidth = if (height > 0) height * 16 / 9 else null,
    protocol = protocol,
    bitrate = tbr,
    dynamicRange = dynamicRange,
    isDrm = drm,
)

private fun storyboard() = YtDlpFormat(
    formatId = "sb0",
    extension = "mhtml",
    resolution = "320x180",
    frameRate = 0.2,
    fileSize = null,
    videoCodec = "none",
    audioCodec = "none",
    note = "storyboard",
    explicitHeight = 180,
    explicitWidth = 320,
    protocol = "mhtml",
)

/** the resolution entries, without the fixed "best" and "audio" ones. */
private fun List<VideoQualityOption>.videoIds(): List<String> =
    map { it.id }.filterNot { it == "best" || it == "audio" }

private fun List<VideoQualityOption>.videoOption(id: String): VideoQualityOption =
    single { it.id == id }

class VideoQualityOptionsTest {

    @Test
    fun everyRealStreamGetsItsOwnEntry() {
        val options = VideoQualityOptions.getAvailableQualities(youtube4k60Formats())

        assertEquals(
            listOf(
                "2160p60-vp9", "2160p60-av1",
                "1440p60-av1",
                "1080p60-h264", "1080p60-av1",
                "720p60-h264", "720p60-vp9",
                "480p30-h264", "480p30-av1",
                "360p30-h264", "360p30-vp9",
            ),
            options.videoIds(),
        )
    }

    /**
     * the original bug: only the 360p pre-muxed stream came back, so only one resolution could be
     * offered. the list must never collapse to a single entry.
     */
    @Test
    fun doesNotCollapseToThePreMuxed360pStream() {
        val options = VideoQualityOptions.getAvailableQualities(youtube4k60Formats())

        assertEquals(13, options.size, "got ${options.map { it.id }}")
        assertTrue(options.any { it.id.startsWith("2160p60") })
        assertTrue(options.any { it.id.startsWith("1080p60") })
    }

    /**
     * youtube offers up to three codecs per height and they differ a lot in size, a 4K av1 stream
     * is roughly half the vp9 one. hiding that would make the choice for the user.
     */
    @Test
    fun everyCodecOfAResolutionIsOffered() {
        val options = VideoQualityOptions.getAvailableQualities(youtube4k60Formats())

        val fourK = options.filter { it.id.startsWith("2160p60") }
        assertEquals(listOf("2160p60-vp9", "2160p60-av1"), fourK.map { it.id })
        assertEquals("2160p60 (4K) · VP9", fourK.first().label)
        assertEquals("2160p60 (4K) · AV1", fourK.last().label)
        // the bigger stream is listed first, and the two really do differ in size
        assertTrue(fourK.first().fileSizeBytes!! > fourK.last().fileSizeBytes!!)
    }

    @Test
    fun theMostPlayableCodecComesFirstInsideAResolution() {
        val options = VideoQualityOptions.getAvailableQualities(youtube4k60Formats())

        assertEquals(
            listOf("1080p60-h264", "1080p60-av1"),
            options.videoIds().filter { it.startsWith("1080p60") },
        )
    }

    @Test
    fun noTwoEntriesShareTheSameHeightFrameRateAndCodec() {
        val options = VideoQualityOptions.getAvailableQualities(youtube4k60Formats())

        val keys = options.videoIds()
        assertEquals(keys.size, keys.toSet().size, "duplicate entries in $keys")
    }

    @Test
    fun aSixtyFpsOnlyVideoReportsTheSixtyFpsFrameRate() {
        val options = VideoQualityOptions.getAvailableQualities(youtube4k60Formats())

        val sevenTwenty = options.videoIds().filter { it.startsWith("720p") }
        assertEquals(listOf("720p60-h264", "720p60-vp9"), sevenTwenty)
    }

    @Test
    fun aThirtyFpsOnlyVideoReportsItsOwnFrameRate() {
        val formats = listOf(
            format("134", height = 360, fps = 30.0, vcodec = "avc1.4d401e", size = 100, tbr = 231),
            format("135", height = 480, fps = 30.0, vcodec = "avc1.4d401f", size = 200, tbr = 356),
            format("136", height = 720, fps = 30.0, vcodec = "avc1.4d401f", size = 300, tbr = 700),
        )

        val options = VideoQualityOptions.getAvailableQualities(formats)

        assertEquals(listOf("720p30-h264", "480p30-h264", "360p30-h264"), options.videoIds())
    }

    @Test
    fun theFrameRateIsTakenFromTheVideoNotAssumed() {
        val formats = listOf(
            format("136", height = 720, fps = 24.0, vcodec = "avc1.4d401f", size = 300, tbr = 700),
        )

        val option = VideoQualityOptions.getAvailableQualities(formats).videoOption("720p24-h264")

        assertEquals("720p24 (HD) · H.264", option.label)
    }

    @Test
    fun aVideoWithBothFrameRatesGetsTwoEntriesForThatResolution() {
        val formats = listOf(
            format("136", height = 720, fps = 30.0, vcodec = "avc1.4d401f", size = 300, tbr = 700),
            format("298", height = 720, fps = 60.0, vcodec = "avc1.4d4020", size = 900, tbr = 1898),
        )

        val options = VideoQualityOptions.getAvailableQualities(formats)

        assertEquals(listOf("720p60-h264", "720p30-h264"), options.videoIds())
    }

    @Test
    fun hlsAndStoryboardStreamsAreNotOffered() {
        val formats = youtube4k60Formats()
        val options = VideoQualityOptions.getAvailableQualities(formats)

        val pinned = options.mapNotNull { it.formatSelector?.substringBefore('/')?.substringBefore('+') }
        assertTrue(pinned.none { it == "231" || it == "311" }, "an hls stream was offered")
        assertTrue(pinned.none { it == "sb0" }, "the storyboard was offered")
        assertTrue(options.none { it.label.contains("storyboard") })
    }

    @Test
    fun drmProtectedStreamsAreNotOffered() {
        val formats = listOf(
            format("299", height = 1080, fps = 60.0, vcodec = "avc1.64002a", size = 900, tbr = 3248, drm = true),
            format("135", height = 480, fps = 30.0, vcodec = "avc1.4d401f", size = 200, tbr = 356),
        )

        val options = VideoQualityOptions.getAvailableQualities(formats)

        assertTrue(options.none { it.id.startsWith("1080p") }, "drm stream leaked into ${options.map { it.id }}")
        assertTrue(options.any { it.id.startsWith("480p") })
    }

    /**
     * "best" has to mean the biggest stream, and it has to match the size the form already shows for
     * the same link, otherwise the page contradicts itself.
     */
    @Test
    fun theBestEntryPicksTheBiggestStream() {
        val options = VideoQualityOptions.getAvailableQualities(youtube4k60Formats())

        val best = options.first()
        assertEquals("best", best.id)
        assertEquals("Best Quality (Auto) · VP9 · 2160p60 (4K)", best.label)
        assertEquals("315+140/bv*[height<=2160][fps>=50]+ba/b[height<=2160][fps>=50]/b", best.formatSelector)
        // 315 (1361630720) + 140 (10273477)
        assertEquals(1_371_904_197L, best.fileSizeBytes)
    }

    /**
     * a pre-muxed stream reports its video and audio bitrate together, so it must not be compared
     * against a video only stream by bitrate.
     */
    @Test
    fun aPreMuxedStreamDoesNotBeatAVideoOnlyStreamOfTheSameCodec() {
        val formats = listOf(
            // 18 is 360p and pre-muxed, and its tbr already includes the audio track
            format("18", height = 360, fps = 30.0, vcodec = "avc1.42001E", acodec = "mp4a.40.2", size = 28_000, tbr = 359),
            format("134", height = 360, fps = 30.0, vcodec = "avc1.4d401e", size = 18_000, tbr = 231),
            format("140", height = 0, fps = null, vcodec = "none", acodec = "mp4a.40.2", size = 10_000, tbr = 129),
        )

        val option = VideoQualityOptions.getAvailableQualities(formats).videoOption("360p30-h264")

        assertEquals("134+140/bv*[height<=360]+ba/b[height<=360]/b", option.formatSelector)
    }

    @Test
    fun everyVideoEntryCarriesAPinAndAHeightCappedFallback() {
        val formats = youtube4k60Formats()
        val options = VideoQualityOptions.getAvailableQualities(formats)

        for (option in options) {
            if (option.id == "audio") continue
            val selector = requireNotNull(option.formatSelector) { "${option.id} has no selector" }
            val pin = selector.substringBefore('/')
            val fallback = selector.substringAfter('/')
            assertTrue(fallback.contains("bv*[height<="), "${option.id} cannot fall back: $selector")
            assertTrue(fallback.endsWith("/b"), "${option.id} has no last resort: $selector")
            // the pinned id has to be a stream this link actually reported
            assertTrue(
                formats.any { it.formatId == pin.split('+').first() },
                "${option.id} pinned an unknown id: $selector",
            )
        }
    }

    /**
     * the ids are pinned so the download matches the entry that was picked, but a queued download
     * can run long after the link was checked, and an id yt-dlp no longer knows must not fail the
     * whole download, so a height capped fallback has to follow the pin.
     */
    @Test
    fun thePinnedIdsAreFollowedByAHeightCappedFallback() {
        val options = VideoQualityOptions.getAvailableQualities(youtube4k60Formats())

        assertEquals(
            "299+140/bv*[height<=1080][fps>=50]+ba/b[height<=1080][fps>=50]/b",
            options.videoOption("1080p60-h264").formatSelector,
        )
    }

    /**
     * a 24 or 30fps entry must not be capped to its exact frame rate, or the fallback would reject
     * the very stream the entry was built from. only a high frame rate entry needs the filter.
     */
    @Test
    fun onlyHighFrameRateEntriesFilterOnFrameRate() {
        val options = VideoQualityOptions.getAvailableQualities(youtube4k60Formats())

        assertTrue(
            options.videoOption("480p30-h264").formatSelector!!.contains("[fps<=").not(),
            "a 30fps entry must not filter on fps",
        )
        assertTrue(
            options.videoOption("1080p60-h264").formatSelector!!.contains("[fps>=50]"),
            "a 60fps entry must keep 60fps",
        )
    }

    @Test
    fun theAudioEntryOnlyAsksForAudio() {
        val options = VideoQualityOptions.getAvailableQualities(youtube4k60Formats())

        val audio = options.last()
        assertEquals("audio", audio.id)
        assertEquals("Audio Only · AAC", audio.label)
        assertEquals("ba/b", audio.formatSelector)
    }

    @Test
    fun sizesAreEstimatedFromTheVideoAndAudioStreams() {
        val options = VideoQualityOptions.getAvailableQualities(youtube4k60Formats())

        // 299 (257698304) + 140 (10273477)
        assertEquals(267_971_781L, options.videoOption("1080p60-h264").fileSizeBytes)
        assertEquals(10_273_477L, options.last().fileSizeBytes)
    }

    @Test
    fun aStreamThatAlreadyHasAudioIsNotPairedWithAnotherOne() {
        val formats = listOf(
            format("18", height = 360, fps = 30.0, vcodec = "avc1.42001E", acodec = "mp4a.40.2", size = 100, tbr = 359),
            format("140", height = 0, fps = null, vcodec = "none", acodec = "mp4a.40.2", size = 50, tbr = 129),
        )

        val option = VideoQualityOptions.getAvailableQualities(formats).videoOption("360p30-h264")

        // 18 already carries audio, so merging 140 into it would be wrong
        assertEquals("18/bv*[height<=360]+ba/b[height<=360]/b", option.formatSelector)
    }

    @Test
    fun hdrIsBadged() {
        val formats = listOf(
            format("299", height = 1080, fps = 60.0, vcodec = "avc1.64002a", size = 900, tbr = 3248, dynamicRange = "PQ"),
        )

        val option = VideoQualityOptions.getAvailableQualities(formats).videoOption("1080p60-h264")

        assertEquals("1080p60 (Full HD) · H.264 · HDR", option.label)
    }

    /**
     * nothing measured yet, or a site that reports no usable stream: the picker must not invent
     * resolutions, only "best", because a fixed ladder would offer a 1080p entry for a 480p video
     * and quietly download something else under that name.
     */
    @Test
    fun noQualityIsInventedBeforeTheLinkHasBeenChecked() {
        assertEquals(listOf("best"), VideoQualityOptions.NOT_CHECKED_YET.map { it.id })
        assertEquals(
            listOf("best"),
            VideoQualityOptions.getAvailableQualities(emptyList()).map { it.id },
        )
        assertEquals(
            listOf("best"),
            VideoQualityOptions.getAvailableQualities(listOf(storyboard())).map { it.id },
        )
    }

    /**
     * the case that made a hardcoded ladder pointless: a 480p video must never be offered 1080p,
     * and every entry it does get has to be a stream that really exists.
     */
    @Test
    fun aLowResolutionVideoOnlyOffersQualitiesItActuallyHas() {
        val formats = listOf(
            format("18", height = 360, fps = 30.0, vcodec = "avc1.42001E", acodec = "mp4a.40.2", size = 28_000, tbr = 359),
            format("134", height = 360, fps = 30.0, vcodec = "avc1.4d401e", size = 18_000, tbr = 231),
            format("135", height = 480, fps = 30.0, vcodec = "avc1.4d401f", size = 28_300, tbr = 356),
            format("140", height = 0, fps = null, vcodec = "none", acodec = "mp4a.40.2", size = 10_000, tbr = 129),
        )

        val options = VideoQualityOptions.getAvailableQualities(formats)

        assertEquals(listOf("best", "480p30-h264", "360p30-h264", "audio"), options.map { it.id })
        assertTrue(options.none { it.id.startsWith("1080p") }, "offered a height it does not have")
        assertTrue(options.none { it.id.startsWith("2160p") }, "offered 4K for a 480p video")
        for (option in options) {
            val cap = Regex("""\[height<=(\d+)]""").find(option.formatSelector.orEmpty())
            if (option.id == "audio" || cap == null) continue
            assertTrue(
                cap.groupValues[1].toInt() <= 480,
                "${option.id} caps above what the video has",
            )
        }
    }

    /**
     * nobody wants to scroll past twenty six entries to find 1080p, so the default list is one entry
     * per resolution: the codec that plays everywhere.
     */
    @Test
    fun theDefaultListIsOnePlayableEntryPerResolution() {
        val options = VideoQualityOptions.getAvailableQualities(youtube4k60Formats())

        assertEquals(
            listOf(
                "best",
                "2160p60-vp9", "1440p60-av1", "1080p60-h264",
                "720p60-h264", "480p30-h264", "360p30-h264",
                "audio",
            ),
            options.filterNot { it.isAlternative }.map { it.id },
        )
    }

    @Test
    fun theHiddenEntriesAreTheExtraCodecs() {
        val options = VideoQualityOptions.getAvailableQualities(youtube4k60Formats())

        assertEquals(
            listOf("2160p60-av1", "1080p60-av1", "720p60-vp9", "480p30-av1", "360p30-vp9"),
            options.filter { it.isAlternative }.map { it.id },
        )
    }

    @Test
    fun h264IsTheDefaultWhereverItExists() {
        val defaultIds = VideoQualityOptions.getAvailableQualities(youtube4k60Formats())
            .filterNot { it.isAlternative }
            .map { it.id }

        assertTrue(defaultIds.contains("1080p60-h264"), "got $defaultIds")
        assertTrue(defaultIds.contains("720p60-h264"), "got $defaultIds")
    }

    /**
     * hiding a codec is a presentation choice, saying nothing about it is not: the traditional 4K
     * pick on this video is vp9, and vp9 in an mp4 is exactly what needs warning about.
     */
    @Test
    fun aWarningIsShownEvenOnAnEntryThatIsNotHidden() {
        val options = VideoQualityOptions.getAvailableQualities(youtube4k60Formats())

        val fourK = options.single { it.id == "2160p60-vp9" }
        assertTrue(!fourK.isAlternative, "the 4K vp9 entry is the default one")
        assertTrue(fourK.warning!!.contains("VP9"), "got ${fourK.warning}")
    }

    @Test
    fun av1AndVp9EntriesCarryAWarning() {
        val options = VideoQualityOptions.getAvailableQualities(youtube4k60Formats())

        assertTrue(options.videoOption("2160p60-av1").warning!!.contains("AV1"))
        assertTrue(options.videoOption("720p60-vp9").warning!!.contains("VP9"))
        assertNull(options.videoOption("1080p60-h264").warning)
    }

    /**
     * the warning belongs to the list entry, not to the label, because the closed picker shows that
     * same label for the selected value and a second line under it would just be noise.
     */
    @Test
    fun theWarningIsAddedByTheListEntryOnly() {
        val option = VideoQualityOptions.getAvailableQualities(youtube4k60Formats())
            .videoOption("2160p60-av1")

        val closed = option.describeAsLabel(CommonSizeConvertConfigs.BinaryBytes)
        assertFalse(closed.contains("\n"), "the closed picker must stay one line: $closed")

        assertEquals(
            "$closed\n! ${option.warning}",
            option.describeAsOptionLabel(CommonSizeConvertConfigs.BinaryBytes),
        )
    }

    @Test
    fun anHdrEntryIsWarnedAboutEvenWhenItsCodecIsSafe() {
        val formats = listOf(
            format("299", height = 1080, fps = 60.0, vcodec = "avc1.64002a", size = 900, tbr = 3248, dynamicRange = "PQ"),
        )

        val option = VideoQualityOptions.getAvailableQualities(formats).videoOption("1080p60-h264")

        assertTrue(option.warning!!.contains("HDR"), "got ${option.warning}")
    }

    @Test
    fun aVideoWithNoAudioStreamStillOffersVideoQualities() {
        val formats = listOf(
            format("299", height = 1080, fps = 60.0, vcodec = "avc1.64002a", size = 900, tbr = 3248),
        )

        val options = VideoQualityOptions.getAvailableQualities(formats)

        assertEquals(listOf("best", "1080p60-h264"), options.map { it.id })
    }
}
