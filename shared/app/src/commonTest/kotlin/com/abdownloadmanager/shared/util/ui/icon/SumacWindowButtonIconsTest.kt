package com.abdownloadmanager.shared.util.ui.icon

import com.abdownloadmanager.resources.icons.SUMAC_WINDOW_CLOSE_PATH_DATA
import com.abdownloadmanager.resources.icons.SUMAC_WINDOW_MAXIMIZE_PATH_DATA
import com.abdownloadmanager.resources.icons.SUMAC_WINDOW_MINIMIZE_PATH_DATA
import com.abdownloadmanager.resources.icons.SUMAC_WINDOW_RESTORE_PATH_DATA
import androidx.compose.ui.geometry.Rect
import com.abdownloadmanager.resources.icons.SumacButtonGlyph
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

/**
 * Checks that the window button artwork really is the artwork from the "Sumac Night Molten Glass
 * Blur" window decoration theme.
 *
 * The expected numbers were measured off the SVGs by walking the `buttonSymbol` group, applying the
 * group transform along with the `translate(-11,16)` that the `hover-center` state applies, and
 * rebasing the result so the `buttonBase` circle sits on (8, 8) of the theme's 16 unit button box.
 */
class SumacWindowButtonIconsTest {

    @Test
    fun `box metrics match the theme config`() {
        // ButtonWidth / ButtonHeight in sumac-night-molten-glass-blurrc.
        assertClose(16f, SumacButtonGlyph.BoxSize, "ButtonWidth/ButtonHeight")
        // r of the buttonBase circle, 6.8499999 in the SVGs.
        assertClose(6.85f, SumacButtonGlyph.CircleRadius, "buttonBase circle r")
        assertClose(8f, SumacButtonGlyph.CircleCenter, "circle centre on the box")
    }

    @Test
    fun `box size for a 12 unit circle lands on the 14 unit box the glyphs are drawn on`() {
        // 12 * (16 / 13.7) = 14.0146
        assertClose(14.0146f, SumacButtonGlyph.BoxSizeForCircle(12f), "box size for a 12dp circle")
    }

    @Test
    fun `close glyph is a 6 by 6 centred cross`() {
        // The path itself only spans 4.3636 units; a round cap adds half the stroke width to each
        // end, which is what brings the painted glyph to the 6 units the theme uses.
        val bounds = SumacButtonGlyph.strokedBoundsOf(
            SUMAC_WINDOW_CLOSE_PATH_DATA,
            SumacButtonGlyph.CloseStrokeWidth,
        )
        assertCentred(bounds, 6f, 6f, "close")
    }

    @Test
    fun `minimize glyph is an 8 by 2 centred bar`() {
        val bounds = SumacButtonGlyph.strokedBoundsOf(
            SUMAC_WINDOW_MINIMIZE_PATH_DATA,
            SumacButtonGlyph.MinimizeStrokeWidth,
        )
        assertCentred(bounds, 8f, 2f, "minimize")
    }

    @Test
    fun `maximize glyph is a 6 by 6 centred pair of arrows`() {
        assertCentred(SumacButtonGlyph.boundsOf(SUMAC_WINDOW_MAXIMIZE_PATH_DATA), 6f, 6f, "maximize")
    }

    @Test
    fun `restore glyph is an 8 by 8 centred pair of arrows`() {
        // restore.svg is the maximise motif rotated -90 degrees and scaled up, so it is wider than
        // the maximise glyph even though both are built from the same two arrows.
        assertCentred(SumacButtonGlyph.boundsOf(SUMAC_WINDOW_RESTORE_PATH_DATA), 8f, 8f, "restore")
    }

    @Test
    fun `stroked glyphs are stroked, not filled`() {
        // Were these filled instead, the artwork would be two hairline rectangles and every
        // width assertion above would still pass.
        val close = SumacButtonGlyph.boundsOf(SUMAC_WINDOW_CLOSE_PATH_DATA)
        assertClose(4.3636f, close.width, "close path width before the caps")
        val minimize = SumacButtonGlyph.boundsOf(SUMAC_WINDOW_MINIMIZE_PATH_DATA)
        assertClose(6f, minimize.width, "minimize path width before the caps")
        assertClose(0f, minimize.height, "minimize path height before the caps")
    }

    @Test
    fun `close glyph is two equal arms, each a 45 degree diagonal`() {
        // The overall bounding box is not enough here: nudging one arm's end point leaves the box
        // untouched, because the other arm still reaches the same two extremes, and each arm on its
        // own is still at 45 degrees. Comparing the arms with each other is what catches it.
        val arms = SumacButtonGlyph.subPathBoundsOf(SUMAC_WINDOW_CLOSE_PATH_DATA)
        if (arms.size != 2) {
            fail("close glyph should be 2 strokes but was ${arms.size}")
        }
        arms.forEachIndexed { index, arm ->
            assertClose(arm.width, arm.height, "close arm $index is not at 45 degrees")
            // The two arms cross on the circle centre, so the same offset moves both.
            assertClose(
                SumacButtonGlyph.CircleCenter - arm.left,
                SumacButtonGlyph.CircleCenter - arm.top,
                "close arm $index does not start on the down-left diagonal",
            )
        }
        assertClose(arms[0].width, arms[1].width, "close arms are different lengths")
    }

    @Test
    fun `maximize and restore glyphs each keep both of their arrows`() {
        // Both glyphs are a pair of separate filled arrows. Dropping one would still leave a box
        // that is nearly the right size, since the surviving arrow covers most of it.
        assertEquals(2, SumacButtonGlyph.subPathBoundsOf(SUMAC_WINDOW_MAXIMIZE_PATH_DATA).size, "maximize arrows")
        assertEquals(2, SumacButtonGlyph.subPathBoundsOf(SUMAC_WINDOW_RESTORE_PATH_DATA).size, "restore arrows")
    }

    @Test
    fun `maximize arrows point at opposite corners`() {
        val arrows = SumacButtonGlyph.subPathBoundsOf(SUMAC_WINDOW_MAXIMIZE_PATH_DATA)
        // One arrow sits in the top left and the other in the bottom right of the same 6 by 6 box,
        // which is what makes them read as the diagonal "expand" motif.
        assertClose(arrows[0].left, arrows[0].top, "first arrow is not anchored to its corner")
        assertClose(
            arrows[1].right - arrows[1].width,
            arrows[1].top,
            "second arrow is not anchored to the opposite corner",
        )
    }

    private fun assertCentred(
        bounds: Rect,
        expectedWidth: Float,
        expectedHeight: Float,
        name: String,
    ) {
        assertClose(expectedWidth, bounds.width, "$name width")
        assertClose(expectedHeight, bounds.height, "$name height")
        assertClose(SumacButtonGlyph.CircleCenter, bounds.center.x, "$name centre x")
        assertClose(SumacButtonGlyph.CircleCenter, bounds.center.y, "$name centre y")
    }
}

private fun assertClose(expected: Float, actual: Float, message: String) {
    if (abs(expected - actual) > TOLERANCE) {
        fail("$message: expected $expected but was $actual (tolerance $TOLERANCE)")
    }
}

private const val TOLERANCE = 0.02f
