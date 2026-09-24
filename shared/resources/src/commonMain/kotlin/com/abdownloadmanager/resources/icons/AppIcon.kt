package com.abdownloadmanager.resources.icons

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val ABDMIcons.AppIcon: ImageVector
    get() {
        if (_AppIcon != null) {
            return _AppIcon!!
        }
        val blue = Color(0xFF2F7BF2)
        val lightBlue = Color(0xFFE4EEFD)
        val darkBlue = Color(0xFF1E5BBD)
        
        _AppIcon = ImageVector.Builder(
            name = "AppIcon",
            defaultWidth = 48.dp,
            defaultHeight = 48.dp,
            viewportWidth = 48f,
            viewportHeight = 48f
        ).apply {
            // Light facet background fills
            path(
                fill = SolidColor(lightBlue),
                pathFillType = PathFillType.NonZero
            ) {
                // Left claw facet
                moveTo(14f, 8f)
                lineTo(20f, 8f)
                lineTo(20f, 16f)
                lineTo(14f, 16f)
                close()
                // Right claw facet
                moveTo(28f, 8f)
                lineTo(34f, 8f)
                lineTo(34f, 16f)
                lineTo(28f, 16f)
                close()
                // Left claw arm facet
                moveTo(14f, 12f)
                lineTo(4f, 20f)
                lineTo(8f, 32f)
                lineTo(14f, 38f)
                lineTo(12f, 26f)
                lineTo(18f, 18f)
                close()
                // Right claw arm facet
                moveTo(34f, 12f)
                lineTo(44f, 20f)
                lineTo(40f, 32f)
                lineTo(34f, 38f)
                lineTo(36f, 26f)
                lineTo(30f, 18f)
                close()
                // Center arrow shaft
                moveTo(21f, 6f)
                lineTo(27f, 6f)
                lineTo(27f, 29f)
                lineTo(21f, 29f)
                close()
                // Center arrow head
                moveTo(13f, 29f)
                lineTo(35f, 29f)
                lineTo(24f, 43f)
                close()
            }
            
            // Primary Blue Claw Structure and Arrow
            path(
                fill = SolidColor(blue),
                pathFillType = PathFillType.EvenOdd
            ) {
                // Top center shaft guide
                moveTo(21f, 6f)
                lineTo(27f, 6f)
                lineTo(27f, 20f)
                lineTo(24f, 23f)
                lineTo(21f, 20f)
                close()
                
                // Downward Center Arrow Head
                moveTo(24f, 45f)
                lineTo(11f, 28f)
                lineTo(19f, 28f)
                lineTo(19f, 20f)
                lineTo(21f, 20f)
                lineTo(21f, 30f)
                lineTo(24f, 30f)
                lineTo(24f, 41f)
                lineTo(27f, 30f)
                lineTo(29f, 30f)
                lineTo(29f, 20f)
                lineTo(29f, 28f)
                lineTo(37f, 28f)
                close()

                // Left Joint Plate
                moveTo(13f, 7f)
                lineTo(20f, 7f)
                lineTo(20f, 17f)
                lineTo(16f, 17f)
                lineTo(13f, 14f)
                close()

                // Right Joint Plate
                moveTo(35f, 7f)
                lineTo(28f, 7f)
                lineTo(28f, 17f)
                lineTo(32f, 17f)
                lineTo(35f, 14f)
                close()

                // Left Outer Claw Arm
                moveTo(14f, 8f)
                lineTo(3f, 19f)
                lineTo(5f, 29f)
                lineTo(13f, 39f)
                lineTo(14f, 37f)
                lineTo(7f, 28f)
                lineTo(5f, 20f)
                lineTo(14f, 10f)
                close()

                // Left Inner Claw Arm
                moveTo(18f, 16f)
                lineTo(9f, 23f)
                lineTo(10f, 35f)
                lineTo(12f, 34f)
                lineTo(11f, 25f)
                lineTo(18f, 18f)
                close()

                // Right Outer Claw Arm
                moveTo(34f, 8f)
                lineTo(45f, 19f)
                lineTo(43f, 29f)
                lineTo(35f, 39f)
                lineTo(34f, 37f)
                lineTo(41f, 28f)
                lineTo(43f, 20f)
                lineTo(34f, 10f)
                close()

                // Right Inner Claw Arm
                moveTo(30f, 16f)
                lineTo(39f, 23f)
                lineTo(38f, 35f)
                lineTo(36f, 34f)
                lineTo(37f, 25f)
                lineTo(30f, 18f)
                close()
            }

            // Bolt circles / eyelets
            path(
                fill = SolidColor(darkBlue),
                pathFillType = PathFillType.EvenOdd
            ) {
                // Left Bolt
                moveTo(16.5f, 11f)
                lineTo(17.5f, 11f)
                lineTo(18f, 11.5f)
                lineTo(18f, 12.5f)
                lineTo(17.5f, 13f)
                lineTo(16.5f, 13f)
                lineTo(16f, 12.5f)
                lineTo(16f, 11.5f)
                close()
                // Right Bolt
                moveTo(31.5f, 11f)
                lineTo(32.5f, 11f)
                lineTo(33f, 11.5f)
                lineTo(33f, 12.5f)
                lineTo(32.5f, 13f)
                lineTo(31.5f, 13f)
                lineTo(31f, 12.5f)
                lineTo(31f, 11.5f)
                close()
            }
        }.build()

        return _AppIcon!!
    }

@Suppress("ObjectPropertyName")
private var _AppIcon: ImageVector? = null
