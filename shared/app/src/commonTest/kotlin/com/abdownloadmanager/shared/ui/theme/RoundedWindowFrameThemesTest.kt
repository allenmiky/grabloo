package com.abdownloadmanager.shared.ui.theme

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The rounded window frame is opt-in per theme because it forces a transparent native window. These
 * tests pin both halves of that contract: every shipped theme is opted in, and nothing else is.
 */
class RoundedWindowFrameThemesTest {

    @Test
    fun everyBuiltInThemeGetsARoundedFrame() {
        listOf(
            DefaultThemes.mojaveDark,
            DefaultThemes.dayWhite,
            DefaultThemes.graphite,
            DefaultThemes.oceanTeal,
        ).forEach { theme ->
            assertTrue(
                theme.usesRoundedWindowFrame(),
                "built in theme ${theme.id} (${theme.name}) should get a rounded window frame",
            )
        }
    }

    @Test
    fun themeOutsideTheListDoesNotGetARoundedFrame() {
        // Also guards against "always true", which the test above cannot catch on its own.
        assertFalse(
            DefaultThemes.oceanTeal.copy(id = "some_future_theme").usesRoundedWindowFrame(),
            "an unlisted theme must keep square corners",
        )
    }
}
