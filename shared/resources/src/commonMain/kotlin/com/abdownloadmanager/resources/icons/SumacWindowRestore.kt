package com.abdownloadmanager.resources.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import com.abdownloadmanager.resources.icons.SumacButtonGlyph.append

/**
 * The `buttonSymbol` group of `restore.svg` from the "Sumac Night Molten Glass Blur" Aurorae window
 * decoration theme.
 *
 * The theme builds this one by rotating the maximise motif by -90 degrees and scaling it up, which
 * is why it spans 8x8 units on the 16 unit button box where the maximise glyph spans 6x6. The
 * rotation is already baked into the path below.
 */
const val SUMAC_WINDOW_RESTORE_PATH_DATA =
    "M4.0005,8.4703 C3.9929,8.5522 4.0073,8.6347 4.0473,8.7123 " +
        "C4.1411,8.8887 4.309,9.0086 4.4418,9.1551 L7.1466,11.8551 " +
        "C7.3815,12.1125 7.8505,12.0088 7.9648,11.6854 C8.0187,11.5357 7.9912,11.3772 8,11.2211 " +
        "L8,11.2211 C8.0129,8.1967 7.6912,7.9571 7.3965,8.006 L7.3965,8.006 " +
        "C4.2374,7.9957 4.0234,8.2249 4.0005,8.4703 Z " +
        "M8.0004,7.4942 C7.9874,7.803 8.3072,8.0426 8.6019,7.9936 L8.6019,7.9936 " +
        "C11.8499,8.0075 12.1134,7.5975 11.953,7.2874 C11.8593,7.1109 11.6893,6.9911 11.5566,6.8446 " +
        "L8.8538,4.1445 C8.6189,3.8872 8.1499,3.9908 8.0355,4.3143 " +
        "C7.9816,4.464 8.0091,4.6244 8.0004,4.7805 Z"

val ABDMIcons.SumacWindowRestore: ImageVector
    get() {
        if (_SumacWindowRestore != null) {
            return _SumacWindowRestore!!
        }
        _SumacWindowRestore = ImageVector.Builder(
            name = "SumacWindowRestore",
            defaultWidth = SumacButtonGlyph.BoxSize.dp,
            defaultHeight = SumacButtonGlyph.BoxSize.dp,
            viewportWidth = SumacButtonGlyph.BoxSize,
            viewportHeight = SumacButtonGlyph.BoxSize
        ).apply {
            path(fill = SolidColor(Color.White)) {
                append(SUMAC_WINDOW_RESTORE_PATH_DATA)
            }
        }.build()

        return _SumacWindowRestore!!
    }

@Suppress("ObjectPropertyName")
private var _SumacWindowRestore: ImageVector? = null
