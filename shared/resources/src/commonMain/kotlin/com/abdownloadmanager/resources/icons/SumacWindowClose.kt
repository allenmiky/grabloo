package com.abdownloadmanager.resources.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import com.abdownloadmanager.resources.icons.SumacButtonGlyph.append

/**
 * The `buttonSymbol` group of `close.svg` from the "Sumac Night Molten Glass Blur" Aurorae window
 * decoration theme: two round capped strokes crossing at 45 degrees.
 *
 * The source numbers live around x = 24 because the theme authors the glyph in its own group and
 * positions it later. Applying the `hover-center` offset of `translate(-11,16)` and moving the
 * origin to the corner of the 16 unit button box leaves the path below, an X spanning 6x6 units
 * (37.5% of the box) centred on (8, 8).
 */
const val SUMAC_WINDOW_CLOSE_PATH_DATA =
    "M5.8182,5.8182 L10.1818,10.1818 M10.1818,5.8182 L5.8182,10.1818"

val ABDMIcons.SumacWindowClose: ImageVector
    get() {
        if (_SumacWindowClose != null) {
            return _SumacWindowClose!!
        }
        _SumacWindowClose = ImageVector.Builder(
            name = "SumacWindowClose",
            defaultWidth = SumacButtonGlyph.BoxSize.dp,
            defaultHeight = SumacButtonGlyph.BoxSize.dp,
            viewportWidth = SumacButtonGlyph.BoxSize,
            viewportHeight = SumacButtonGlyph.BoxSize
        ).apply {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = SumacButtonGlyph.CloseStrokeWidth,
                strokeLineCap = StrokeCap.Round,
            ) {
                append(SUMAC_WINDOW_CLOSE_PATH_DATA)
            }
        }.build()

        return _SumacWindowClose!!
    }

@Suppress("ObjectPropertyName")
private var _SumacWindowClose: ImageVector? = null
