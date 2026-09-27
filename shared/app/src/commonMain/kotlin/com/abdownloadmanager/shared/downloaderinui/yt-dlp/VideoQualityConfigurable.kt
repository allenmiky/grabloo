package com.abdownloadmanager.shared.downloaderinui.yt_dlp

import com.abdownloadmanager.shared.ui.configurable.Configurable
import com.abdownloadmanager.shared.ui.configurable.item.EnumConfigurable
import com.abdownloadmanager.shared.util.convertPositiveBytesToHumanReadable
import ir.amirab.downloader.downloaditem.yt_dlp.YtDlpFormat
import ir.amirab.util.compose.StringSource
import ir.amirab.util.compose.asStringSource
import ir.amirab.util.datasize.ConvertSizeConfig
import ir.amirab.util.flow.combineStateFlows
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.roundToInt

/**
 * [fileSizeBytes] is kept apart from [label] on purpose: the label has to be a plain string so the
 * quality list can be built and tested without compose, while the size depends on the unit the user
 * picked and has to be re-rendered whenever that setting changes.
 *
 * [isAlternative] marks the entries that are hidden until the user asks for the full list, and
 * [warning] is only rendered inside that list, never on the entry that is currently selected.
 */
data class VideoQualityOption(
    val id: String,
    val label: String,
    val formatSelector: String?,
    val fileSizeBytes: Long? = null,
    val isAlternative: Boolean = false,
    val warning: String? = null,
)

fun VideoQualityOption.describeAsLabel(sizeUnit: ConvertSizeConfig): String {
    val size = fileSizeBytes?.takeIf { it > 0 }
        ?.let { convertPositiveBytesToHumanReadable(it, sizeUnit) }
    return if (size == null) label else "$label · $size"
}

/**
 * the list entry, with the warning on its own line. a warning is never mixed into [label] itself,
 * because that same label is what the closed picker shows for the current selection, where a two
 * line warning under the value would just be noise.
 */
fun VideoQualityOption.describeAsOptionLabel(sizeUnit: ConvertSizeConfig): String {
    val text = describeAsLabel(sizeUnit)
    val note = warning ?: return text
    return "$text\n! $note"
}

/** one built entry, kept with the values it was built from so nothing has to be parsed back out. */
private data class QualityEntry(
    val height: Int,
    val fps: Int,
    val codec: String,
    val option: VideoQualityOption,
)

private const val HIGH_FPS_THRESHOLD_INT = 50

/**
 * one entry per real stream, keyed by resolution, frame rate and codec. youtube offers three codecs
 * per height and they differ a lot in size, a 4K av1 stream is roughly half the vp9 one, so
 * collapsing them would hide a choice the user cares about and quietly pick for them.
 */
object VideoQualityOptions {

    val BEST = VideoQualityOption(
        id = "best",
        label = "Best Quality (Auto)",
        formatSelector = null,
    )

    private val HEIGHT_NAMES = mapOf(
        2160 to "4K",
        1440 to "2K",
        1080 to "Full HD",
        720 to "HD",
        480 to "SD",
    )

    val AUDIO_ONLY = VideoQualityOption(
        id = "audio",
        label = "Audio Only",
        formatSelector = "ba/b",
    )

    /**
     * what the list holds before the link has been checked, and when a site reports no usable
     * stream. deliberately only "best": offering a fixed ladder of resolutions here would show a
     * 1080p entry for a 480p video, and picking it would quietly hand back something else, because
     * the selector can only cap a height that actually exists. "best" is always honest, it just
     * lets yt-dlp choose.
     */
    val NOT_CHECKED_YET: List<VideoQualityOption> = listOf(BEST)

    fun heightLabel(height: Int, fps: Int): String {
        val frameRate = fps.takeIf { it > 0 }?.toString().orEmpty()
        val name = HEIGHT_NAMES[height]
        return if (name != null) "${height}p$frameRate ($name)" else "${height}p$frameRate"
    }

    /**
     * a stable, readable id per entry. the fps and codec are part of it because the same height can
     * appear several times, and a duplicate id would make one entry overwrite the other.
     */
    private fun optionId(height: Int, fps: Int, codec: String): String =
        "${height}p${fps.takeIf { it > 0 } ?: ""}-${codec.filter { it.isLetterOrDigit() }.lowercase()}"

    /**
     * the order the codecs are listed in inside one resolution: the most playable one first, so the
     * entry at the top of a group is the safe choice for anyone who does not read the whole list.
     */
    private fun codecRank(codec: String): Int = when (codec) {
        "H.264" -> 0
        "VP9" -> 1
        "H.265" -> 2
        "AV1" -> 3
        else -> 4
    }

    /**
     * the app merges everything into mp4, and how many players can actually decode what ends up
     * inside that container varies a lot. this is the whole reason the common list is worth
     * preferring, so the entries that need a newer player say so instead of failing on the phone.
     */
    private fun warningFor(codec: String, isHdr: Boolean): String? {
        val codecWarning = when (codec) {
            "AV1" -> "AV1 needs a newer player (Android 11+, VLC 3.0+). On older devices this can " +
                "play as a black screen or not at all."
            "VP9" -> "VP9 inside an MP4 container is not supported by every player."
            "H.265" -> "H.265 needs a newer player than H.264 does."
            else -> null
        }
        val hdrWarning = if (isHdr) "HDR needs a capable display and player." else null
        return listOfNotNull(codecWarning, hdrWarning).takeIf { it.isNotEmpty() }?.joinToString(" ")
    }

    /**
     * m3u8 streams are skipped because they duplicate the https formats of the same quality while
     * downloading over a chunked stream, which is much slower and cannot be resumed as reliably.
     * drm protected streams are skipped because they cannot be downloaded at all.
     */
    private fun List<YtDlpFormat>.selectable(): List<YtDlpFormat> =
        filter { it.isProgressive && !it.isDrm }

    /**
     * a pre-muxed stream reports the bitrate of its video *and* its audio together, so comparing it
     * against a video only stream by bitrate would crown the wrong one. a separate audio stream is
     * merged in later anyway, so a video only stream is always the better representative.
     */
    private fun List<YtDlpFormat>.preferVideoOnly(): List<YtDlpFormat> =
        if (any { !it.hasAudio }) filter { !it.hasAudio } else this

    private fun List<YtDlpFormat>.bestBy(
        isBetter: (candidate: YtDlpFormat, currentBest: YtDlpFormat) -> Boolean,
    ): YtDlpFormat = reduce { best, candidate ->
        if (isBetter(candidate, best)) candidate else best
    }

    fun getAvailableQualities(formats: List<YtDlpFormat>): List<VideoQualityOption> {
        val usable = formats.selectable()
        val videoFormats = usable.filter { it.hasVideo && it.height > 0 }
        if (videoFormats.isEmpty()) {
            return NOT_CHECKED_YET
        }
        val audioFormat = usable
            .filter { !it.hasVideo && it.hasAudio }
            .maxByOrNull { it.bitrate ?: 0L }

        val options = mutableListOf(bestOption(videoFormats, audioFormat))

        val grouped = videoFormats.groupBy { format ->
            Triple(format.height, fpsOf(format), format.videoCodecName().orEmpty())
        }
        val groups = grouped.keys
            .sortedWith(
                compareByDescending<Triple<Int, Int, String>> { it.first }
                    // the higher frame rate variant of a resolution is the better one, so it leads
                    .thenByDescending { it.second }
                    // then the most playable codec of that resolution
                    .thenBy { codecRank(it.third) }
            )

        val videoOptions = groups.map { (height, fps, codec) ->
            // every stream in a group already shares the codec, so the biggest one is the pick
            val representative = grouped.getValue(Triple(height, fps, codec))
                .preferVideoOnly()
                .bestBy { candidate, best -> candidate.isBiggerStreamThan(best) }
            QualityEntry(
                height = height,
                fps = fps,
                codec = codec,
                option = VideoQualityOption(
                    id = optionId(height, fps, codec),
                    label = buildString {
                        append(heightLabel(height, fps))
                        if (codec.isNotBlank()) append(" · ").append(codec)
                        if (representative.isHdr) append(" · HDR")
                    },
                    formatSelector = selectorFor(representative, audioFormat, height, fps),
                    fileSizeBytes = estimatedSize(representative, audioFormat),
                    // a warning is shown whenever it applies, not only on the hidden entries: the
                    // traditional 4K pick is vp9, and vp9 in an mp4 is exactly what needs saying
                    warning = warningFor(codec, representative.isHdr),
                ),
            )
        }

        // the traditional list is one entry per resolution, the codec that plays everywhere. the
        // rest wait behind the "show all" button instead of being pushed on everyone.
        val traditionalPerResolution = videoOptions
            .groupBy { it.height to it.fps }
            .mapValues { (_, entries) -> entries.minBy { codecRank(it.codec) }.option.id }

        options.addAll(
            videoOptions.map { entry ->
                val isTraditional = traditionalPerResolution[entry.height to entry.fps] == entry.option.id
                entry.option.copy(isAlternative = !isTraditional)
            }
        )

        if (audioFormat != null) {
            options.add(
                AUDIO_ONLY.copy(
                    label = audioFormat.audioCodecName()
                        ?.let { "${AUDIO_ONLY.label} · $it" }
                        ?: AUDIO_ONLY.label,
                    fileSizeBytes = audioFormat.fileSize,
                )
            )
        }
        return options
    }

    private fun fpsOf(format: YtDlpFormat): Int = (format.frameRate ?: 0.0).roundToInt()

    /**
     * picks the best stream overall. the ids are pinned so the download matches the entry the user
     * picked, and the height capped part behind it is what keeps the download from failing outright
     * when the streams moved on in between: an id yt-dlp no longer knows is skipped, not an error.
     */
    private fun bestOption(
        videoFormats: List<YtDlpFormat>,
        audioFormat: YtDlpFormat?,
    ): VideoQualityOption {
        val best = videoFormats.preferVideoOnly()
            .bestBy { candidate, currentBest -> candidate.isBiggerStreamThan(currentBest) }
        val topHeight = videoFormats.maxOf { it.height }
        val topFps = fpsOf(best)
        return VideoQualityOption(
            id = BEST.id,
            label = buildString {
                append(BEST.label)
                best.videoCodecName()?.let { append(" · ").append(it) }
                if (best.height > 0) {
                    append(" · ").append(heightLabel(best.height, topFps))
                }
                if (best.isHdr) append(" · HDR")
            },
            formatSelector = selectorFor(best, audioFormat, topHeight, topFps),
            fileSizeBytes = estimatedSize(best, audioFormat),
        )
    }

    private fun selectorFor(
        videoFormat: YtDlpFormat,
        audioFormat: YtDlpFormat?,
        height: Int,
        fps: Int,
    ): String {
        val cap = "[height<=$height]"
        // only a high frame rate entry needs the filter. capping a 24 or 30fps entry too would
        // exclude the stream it was built from, since youtube reports exactly these numbers.
        val frameRate = if (fps >= HIGH_FPS_THRESHOLD_INT) "[fps>=$HIGH_FPS_THRESHOLD_INT]" else ""
        val fallback = "bv*$cap$frameRate+ba/b$cap$frameRate/b"
        if (audioFormat == null || !videoFormat.hasVideo) {
            return fallback
        }
        val pinned = if (videoFormat.hasAudio) {
            videoFormat.formatId
        } else {
            "${videoFormat.formatId}+${audioFormat.formatId}"
        }
        return "$pinned/$fallback"
    }

    private fun estimatedSize(videoFormat: YtDlpFormat, audioFormat: YtDlpFormat?): Long? {
        val video = videoFormat.fileSize?.takeIf { it > 0 }
        val audio = audioFormat?.fileSize?.takeIf { it > 0 }
        return when {
            video != null && audio != null -> video + audio
            video != null -> video
            audio != null -> audio
            else -> null
        }
    }
}

/**
 * extends [EnumConfigurable] rather than only the base so it inherits the enum renderer, which is
 * registered on both desktop and android. without that the entry renders in the add page and then
 * throws when the settings dialog looks up a renderer for its key.
 */
class VideoQualityConfigurable(
    title: StringSource,
    description: StringSource,
    backedBy: MutableStateFlow<VideoQualityOption>,
    val availableQualities: StateFlow<List<VideoQualityOption>>,
    /**
     * read on every render instead of captured, so switching the size unit in the settings updates
     * the quality labels too.
     */
    private val currentSizeUnit: () -> ConvertSizeConfig,
) : EnumConfigurable<VideoQualityOption>(
    title = title,
    description = description,
    backedBy = backedBy,
    describe = { it.describeAsLabel(currentSizeUnit()).asStringSource() },
    possibleValues = availableQualities.value,
    valueToString = { listOf(it.label, it.id) },
) {
    /**
     * the qualities are only known after the link has been checked, so the list has to be read on
     * every access instead of being frozen at construction time, otherwise it would stay empty and
     * [Configurable.set] would refuse every value.
     *
     * this is the full list, hidden entries included, because the settings dialog renders the
     * configurables generically and has nowhere to put a "show all" button. the add page uses
     * [visibleQualities] instead and does have one.
     */
    override val possibleValues: List<VideoQualityOption>
        get() = availableQualities.value

    private val showAllQualities = MutableStateFlow(false)

    /**
     * the traditional list first, the extra codecs behind the "show all" button. a video reports up
     * to three codecs per resolution and nobody wants to scroll past twenty six entries to find
     * 1080p, but hiding them entirely would take the choice away.
     */
    val visibleQualities: StateFlow<List<VideoQualityOption>> =
        combineStateFlows(availableQualities, showAllQualities) { all, showAll ->
            if (showAll) all else all.filterNot { it.isAlternative }
        }

    val isShowingAllQualities: StateFlow<Boolean> = showAllQualities.asStateFlow()

    /** only meaningful while the list still hides something. */
    val hiddenQualityCount: StateFlow<Int> =
        combineStateFlows(availableQualities, showAllQualities) { all, showAll ->
            if (showAll) 0 else all.count { it.isAlternative }
        }

    fun toggleShowingAllQualities() {
        showAllQualities.value = !showAllQualities.value
    }

    fun resetShowingAllQualities() {
        showAllQualities.value = false
    }
}
