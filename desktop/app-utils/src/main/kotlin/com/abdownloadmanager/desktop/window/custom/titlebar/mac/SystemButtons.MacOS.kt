package com.abdownloadmanager.desktop.window.custom.titlebar.mac

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.onClick
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.abdownloadmanager.desktop.window.custom.WindowCloseButtonTooltip
import com.abdownloadmanager.desktop.window.custom.WindowMinimizeTooltip
import com.abdownloadmanager.desktop.window.custom.WindowToggleMaximizeTooltip
import com.abdownloadmanager.desktop.window.custom.isWindowFocused
import com.abdownloadmanager.desktop.window.custom.isWindowMaximized
import com.abdownloadmanager.desktop.window.custom.titlebar.SystemButtonType
import com.abdownloadmanager.resources.icons.SumacButtonGlyph
import com.abdownloadmanager.shared.util.darker
import com.abdownloadmanager.shared.util.div
import com.abdownloadmanager.shared.util.ui.icon.MyIcons
import com.abdownloadmanager.shared.util.ui.myColors
import com.abdownloadmanager.shared.util.ui.widget.MyIcon
import ir.amirab.util.compose.IconSource

// Traffic light colours taken from the "Sumac Night Molten Glass" window decoration SVGs
// (close.svg, minimize.svg, maximize.svg). Each button pairs the artwork's bright fill with the
// darker variant it uses for the glyph, so the glyph stays readable once it appears.
private val TrafficRed = Color(0xFFFD585F)
private val TrafficRedGlyph = Color(0xFF7D0008)
private val TrafficYellow = Color(0xFFFEBE30)
private val TrafficYellowGlyph = Color(0xFFB2710F)
private val TrafficGreen = Color(0xFF48D151)
private val TrafficGreenGlyph = Color(0xFF0F7901)

/** Diameter of the coloured dot. */
private val TrafficLightSize = 12.dp

/**
 * The glyphs are laid out on a 16 unit box that also contains the 13.7 unit circle of the source
 * artwork, so drawing the box at [TrafficLightSize] / (13.7/16) keeps the two in proportion.
 */
private val TrafficLightGlyphSize = SumacButtonGlyph.BoxSizeForCircle(TrafficLightSize.value).dp

@Composable
internal fun MacOSSystemButtons(
    onRequestClose: () -> Unit,
    onRequestMinimize: (() -> Unit)?,
    onToggleMaximize: (() -> Unit)?,
    buttons: List<SystemButtonType>,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isUserInThisArea by interactionSource.collectIsHoveredAsState()
    Row(
        // Toolbar is aligned center vertically, so I fill that and place it on top
        modifier = Modifier
            .padding(horizontal = 6.dp)
            .hoverable(interactionSource)
            .fillMaxHeight().wrapContentHeight(Alignment.Top),
        verticalAlignment = Alignment.Top
    ) {
        buttons.forEach {
            when (it) {
                SystemButtonType.Close -> {
                    CloseButton(onRequestClose, isUserInThisArea)
                }

                SystemButtonType.Minimize -> {
                    MinimizeButton(onRequestMinimize, isUserInThisArea)
                }

                SystemButtonType.Maximize -> {
                    ToggleMaximizeButton(onToggleMaximize, isUserInThisArea)
                }
            }
        }
    }
}

@Composable
private fun MinimizeButton(onRequestMinimize: (() -> Unit)?, isUserInThisArea: Boolean) {
    onRequestMinimize?.let {
        WindowMinimizeTooltip {
            SystemButton(
                onClick = onRequestMinimize,
                modifier = Modifier,
                backgroundColor = TrafficYellow,
                glyphColor = TrafficYellowGlyph,
                icon = MyIcons.windowButtonMinimize,
                isUserInThisArea = isUserInThisArea,
            )
        }
    }
}

@Composable
private fun ToggleMaximizeButton(onToggleMaximize: (() -> Unit)?, isUserInThisArea: Boolean) {
    onToggleMaximize?.let {
        WindowToggleMaximizeTooltip {
            SystemButton(
                onClick = onToggleMaximize,
                modifier = Modifier,
                backgroundColor = TrafficGreen,
                glyphColor = TrafficGreenGlyph,
                icon = if (isWindowMaximized()) {
                    MyIcons.windowButtonRestore
                } else {
                    MyIcons.windowButtonMaximize
                },
                isUserInThisArea = isUserInThisArea,
            )
        }
    }
}

@Composable
private fun CloseButton(onRequestClose: () -> Unit, isUserInThisArea: Boolean) {
    WindowCloseButtonTooltip {
        SystemButton(
            onRequestClose,
            modifier = Modifier,
            backgroundColor = TrafficRed,
            glyphColor = TrafficRedGlyph,
            icon = MyIcons.windowButtonClose,
            isUserInThisArea = isUserInThisArea,
        )
    }
}

@Composable
private fun SystemButton(
    onClick: () -> Unit,
    backgroundColor: Color,
    glyphColor: Color,
    unfocusedBackgroundColor: Color = myColors.onBackground / 0.2f,
    icon: IconSource,
    isUserInThisArea: Boolean,
    modifier: Modifier = Modifier,
) {
    val isWindowFocused = isWindowFocused()
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    Box(
        modifier = modifier
            .hoverable(interactionSource)
            .onClick { onClick() }
            .fillMaxHeight()
            .wrapContentHeight()
            .padding(horizontal = 6.dp)
            .background(
                animateColorAsState(
                    when {
                        !isWindowFocused -> unfocusedBackgroundColor
                        isHovered -> backgroundColor.darker()
                        else -> backgroundColor
                    }
                ).value,
                CircleShape
            )
            .requiredSize(TrafficLightSize)
    ) {
        if (
            isUserInThisArea && isWindowFocused
        ) {
            MyIcon(
                icon = icon,
                tint = glyphColor,
                modifier = Modifier
                    .align(Alignment.Center)
                    // The source SVGs draw each glyph on a 16x16 box holding a circle of radius
                    // 6.85, so the circle is 13.7/16 of the box. Scaling to our 12dp circle means
                    // drawing the 16 unit box at 12dp / (13.7/16) = 14dp.
                    .size(TrafficLightGlyphSize),
                contentDescription = null,
            )
        }
    }
}
