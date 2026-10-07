package com.abdownloadmanager.resources.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import com.abdownloadmanager.resources.icons.SumacButtonGlyph.append

/**
 * The `buttonSymbol` group of `minimize.svg` from the "Sumac Night Molten Glass Blur" Aurorae
 * window decoration theme: a single horizontal round capped stroke.
 *
 * Rebased like [SUMAC_WINDOW_CLOSE_PATH_DATA], it spans 8x2 units on the 16 unit button box. That is
 * wider than the bar the app shipped before and roughly four times as thick.
 */
const val SUMAC_WINDOW_MINIMIZE_PATH_DATA = "M5,8 L11,8"

val ABDMIcons.SumacWindowMinimize: ImageVector
    get() {
        if (_SumacWindowMinimize != null) {
            return _SumacWindowMinimize!!
        }
        _SumacWindowMinimize = ImageVector.Builder(
            name = "SumacWindowMinimize",
            defaultWidth = SumacButtonGlyph.BoxSize.dp,
            defaultHeight = SumacButtonGlyph.BoxSize.dp,
            viewportWidth = SumacButtonGlyph.BoxSize,
            viewportHeight = SumacButtonGlyph.BoxSize
        ).apply {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = SumacButtonGlyph.MinimizeStrokeWidth,
                strokeLineCap = StrokeCap.Round,
            ) {
                append(SUMAC_WINDOW_MINIMIZE_PATH_DATA)
            }
        }.build()

        return _SumacWindowMinimize!!
    }

@Suppress("ObjectPropertyName")
private var _SumacWindowMinimize: ImageVector? = null
