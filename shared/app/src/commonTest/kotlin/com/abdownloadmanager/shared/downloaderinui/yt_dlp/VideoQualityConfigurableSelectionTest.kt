package com.abdownloadmanager.shared.downloaderinui.yt_dlp

import ir.amirab.util.compose.asStringSource
import ir.amirab.util.datasize.CommonSizeConvertConfigs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [Configurable.set] used to validate against the list that existed when the configurable was built,
 * so every quality that arrived later was rejected and the picker did nothing when clicked. the
 * options only exist after the link check, so this is the path that matters.
 */
class VideoQualityConfigurableSelectionTest {

    private fun configurable(available: MutableStateFlow<List<VideoQualityOption>>) =
        VideoQualityConfigurable(
            title = "Quality".asStringSource(),
            description = "".asStringSource(),
            backedBy = MutableStateFlow(VideoQualityOptions.BEST),
            availableQualities = available,
            currentSizeUnit = { CommonSizeConvertConfigs.BinaryBytes },
        )

    @Test
    fun aQualityThatArrivesAfterTheConfigurableWasBuiltCanBeSelected() {
        val available = MutableStateFlow(VideoQualityOptions.NOT_CHECKED_YET)
        val configurable = configurable(available)
        val real = VideoQualityOption(id = "2160p60", label = "2160p60 (4K)", formatSelector = "401+140/b")

        available.value = listOf(VideoQualityOptions.BEST, real)

        assertTrue(configurable.set(real), "a quality shown in the list was refused")
        assertEquals("2160p60", configurable.stateFlow.value.id)
    }

    @Test
    fun theBestEntryStaysSelectable() {
        val available = MutableStateFlow(VideoQualityOptions.NOT_CHECKED_YET)
        val configurable = configurable(available)

        assertTrue(configurable.set(VideoQualityOptions.BEST))
    }

    /**
     * a value that is not on the list is still refused, otherwise a stale entry from a previous
     * link could be carried into the new download.
     */
    @Test
    fun aQualityThatIsNoLongerOfferedIsRefused() {
        val available = MutableStateFlow(VideoQualityOptions.NOT_CHECKED_YET)
        val configurable = configurable(available)
        val old = VideoQualityOption(id = "2160p60", label = "2160p60 (4K)", formatSelector = "401+140/b")

        available.value = listOf(VideoQualityOptions.BEST)
        assertFalse(configurable.set(old))
        assertEquals("best", configurable.stateFlow.value.id)
    }

    @Test
    fun theRenderedListFollowsTheFlow() {
        val available = MutableStateFlow(VideoQualityOptions.NOT_CHECKED_YET)
        val configurable = configurable(available)

        assertEquals(listOf("best"), configurable.possibleValues.map { it.id })

        available.value = listOf(VideoQualityOptions.BEST, VideoQualityOptions.AUDIO_ONLY)
        assertEquals(listOf("best", "audio"), configurable.possibleValues.map { it.id })
    }

    private fun option(id: String, isAlternative: Boolean = false) = VideoQualityOption(
        id = id,
        label = id,
        formatSelector = "$id/b",
        isAlternative = isAlternative,
    )

    private val listWithHidden = listOf(
        VideoQualityOptions.BEST,
        option("1080p-h264"),
        option("1080p-av1", isAlternative = true),
        VideoQualityOptions.AUDIO_ONLY,
    )

    /**
     * the add page shows the traditional list, the settings dialog has no room for a button so it
     * shows everything. both have to accept a selection of the other kind.
     */
    @Test
    fun theExtraCodecsAreHiddenUntilTheyAreAskedFor() {
        val available = MutableStateFlow(VideoQualityOptions.NOT_CHECKED_YET)
        val configurable = configurable(available)
        available.value = listWithHidden

        assertEquals(
            listOf("best", "1080p-h264", "audio"),
            configurable.visibleQualities.value.map { it.id },
        )
        assertEquals(1, configurable.hiddenQualityCount.value)
    }

    @Test
    fun theShowAllButtonRevealsTheRestAndThenGoesAway() {
        val available = MutableStateFlow(VideoQualityOptions.NOT_CHECKED_YET)
        val configurable = configurable(available)
        available.value = listWithHidden

        configurable.toggleShowingAllQualities()

        assertEquals(listOf("best", "1080p-h264", "1080p-av1", "audio"), configurable.visibleQualities.value.map { it.id })
        assertEquals(0, configurable.hiddenQualityCount.value, "nothing is left to reveal")
        assertTrue(configurable.isShowingAllQualities.value)

        configurable.toggleShowingAllQualities()

        assertEquals(listOf("best", "1080p-h264", "audio"), configurable.visibleQualities.value.map { it.id })
        assertEquals(1, configurable.hiddenQualityCount.value, "the button must come back")
    }

    @Test
    fun aHiddenQualityCanStillBeSelected() {
        val available = MutableStateFlow(listWithHidden)
        val configurable = configurable(available)
        val hidden = available.value[2]

        assertTrue(hidden.isAlternative, "this entry is the point of the button")
        assertTrue(configurable.set(hidden), "a quality shown after show all was refused")
    }

    /**
     * [possibleValues] is what the generic settings dialog renders, and it cannot offer the button,
     * so it must not hide anything or the app would silently drop codecs there.
     */
    @Test
    fun theSettingsListIsNeverFiltered() {
        val available = MutableStateFlow(listWithHidden)
        val configurable = configurable(available)

        assertEquals(listOf("best", "1080p-h264", "1080p-av1", "audio"), configurable.possibleValues.map { it.id })
    }

    @Test
    fun theButtonIsNotOfferedWhenThereIsNothingToReveal() {
        val available = MutableStateFlow(VideoQualityOptions.NOT_CHECKED_YET)
        val configurable = configurable(available)

        assertEquals(0, configurable.hiddenQualityCount.value)
    }
}
