package com.abdownloadmanager.shared.ui.theme

import com.abdownloadmanager.shared.util.ui.MyColors

/**
 * Themes that ask for a rounded window frame.
 *
 * A rounded frame needs a transparent native window to be visible, and that is a bigger visual
 * change than a theme should silently make, so this is an explicit opt-in list rather than "every
 * theme".
 *
 * Windows is deliberately not special cased here. The desktop layer skips the custom frame there
 * because the system already rounds the window itself.
 */
private val roundedWindowFrameThemeIds = setOf(
    "mojave_dark", // Mojave Dark
    "day_white",
    "graphite",
    "ocean_teal",
)

/**
 * True when this theme wants a rounded window frame.
 *
 * This only answers the "does the theme want it" half. Callers must still turn it off while the
 * window is maximized, and on platforms that use the system window decoration.
 */
fun MyColors.usesRoundedWindowFrame(): Boolean = id in roundedWindowFrameThemeIds
