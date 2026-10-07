package com.abdownloadmanager.resources.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import com.abdownloadmanager.resources.icons.SumacButtonGlyph.append

/**
 * The `buttonSymbol` group of `maximize.svg` from the "Sumac Night Molten Glass Blur" Aurorae
 * window decoration theme: two filled arrows pointing at opposite corners.
 *
 * Rebased onto the theme's 16 unit button box the path spans 6x6 units centred on (8, 8).
 */
const val SUMAC_WINDOW_MAXIMIZE_PATH_DATA =
    "M5.5058,5.0004 C5.197,4.9874 4.9574,5.3072 5.0064,5.6019 L5.0064,8.5019 " +
        "C4.9925,8.8499 5.4025,9.1134 5.7126,8.953 C5.8891,8.8593 6.0089,8.6894 6.1554,8.5566 " +
        "L8.8555,5.8538 C9.1128,5.6189 9.0092,5.1499 8.6857,5.0355 " +
        "C8.536,4.9816 8.3756,5.0091 8.2195,5.0004 Z " +
        "M10.5293,7.0001 C10.4475,6.9925 10.3649,7.0069 10.2874,7.047 " +
        "C10.1109,7.1407 9.9911,7.3087 9.8446,7.4415 L7.1445,10.1462 " +
        "C6.8872,10.3811 6.9908,10.8501 7.3143,10.9645 C7.464,11.0184 7.6225,10.9909 7.7786,10.9996 " +
        "L10.4942,10.9996 C10.803,11.0126 11.0426,10.6908 10.9936,10.3962 L10.9936,7.4981 " +
        "C11.004,7.2371 10.7748,7.023 10.5293,7.0001 Z"

val ABDMIcons.SumacWindowMaximize: ImageVector
    get() {
        if (_SumacWindowMaximize != null) {
            return _SumacWindowMaximize!!
        }
        _SumacWindowMaximize = ImageVector.Builder(
            name = "SumacWindowMaximize",
            defaultWidth = SumacButtonGlyph.BoxSize.dp,
            defaultHeight = SumacButtonGlyph.BoxSize.dp,
            viewportWidth = SumacButtonGlyph.BoxSize,
            viewportHeight = SumacButtonGlyph.BoxSize
        ).apply {
            path(fill = SolidColor(Color.White)) {
                append(SUMAC_WINDOW_MAXIMIZE_PATH_DATA)
            }
        }.build()

        return _SumacWindowMaximize!!
    }

@Suppress("ObjectPropertyName")
private var _SumacWindowMaximize: ImageVector? = null
