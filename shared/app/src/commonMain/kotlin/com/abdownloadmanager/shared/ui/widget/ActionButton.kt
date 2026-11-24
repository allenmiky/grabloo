package com.abdownloadmanager.shared.ui.widget

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.abdownloadmanager.shared.util.brighten
import com.abdownloadmanager.shared.util.div
import com.abdownloadmanager.shared.util.ui.WithContentAlpha
import com.abdownloadmanager.shared.util.ui.WithContentColor
import com.abdownloadmanager.shared.util.ui.myColors
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.style.TextAlign
import com.abdownloadmanager.shared.util.ui.theme.myTextSizes
import ir.amirab.util.ifThen

@Composable
fun ActionButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    backgroundColor: Brush = SolidColor(Color.Unspecified),
    hoverBackgroundColor: Brush = SolidColor(Color.Unspecified),
    disabledBackgroundColor: Brush = SolidColor(Color.Unspecified),
    borderColor: Brush = SolidColor(Color.Unspecified),
    focusedBorderColor: Brush = SolidColor(Color.Unspecified),
    disabledBorderColor: Brush = SolidColor(Color.Unspecified),
    contentColor: Color = Color.Unspecified,
    contentPadding: PaddingValues = PaddingValues(vertical = 3.dp, horizontal = 14.dp),
    focusRingColor: Color = Color.Unspecified,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    start: (@Composable RowScope.() -> Unit)? = null,
    end: (@Composable RowScope.() -> Unit)? = null,
) {
    val isFocused by interactionSource.collectIsFocusedAsState()
    val isHovered by interactionSource.collectIsHoveredAsState()
    val shape = RoundedCornerShape(5.dp)
    val mColors = myColors

    fun brushOr(brush: Brush, default: Brush): Brush {
        return if (brush is SolidColor && !brush.value.isSpecified) default else brush
    }

    val normalBg = mColors.secondaryButtonGradient
    val hoverBg = Brush.verticalGradient(
        listOf(
            mColors.secondaryButtonTop.brighten(),
            mColors.secondaryButtonBottom.brighten(),
        )
    )
    val currentBg = when {
        !enabled -> brushOr(disabledBackgroundColor, brushOr(backgroundColor, normalBg))
        isHovered -> brushOr(hoverBackgroundColor, hoverBg)
        else -> brushOr(backgroundColor, normalBg)
    }
    val currentBorder = brushOr(borderColor, SolidColor(if (mColors.isLight) mColors.divider else mColors.contrast / 0.4f))
    val currentFocusedBorder = brushOr(focusedBorderColor, SolidColor(mColors.focusedBorderColor))
    val currentDisabledBorder = brushOr(disabledBorderColor, currentBorder)
    val currentContentColor = if (contentColor.isSpecified) contentColor else mColors.onBackground
    val currentFocusRingColor = if (focusRingColor.isSpecified) focusRingColor else mColors.focusedBorderColor / 0.45f
    Row(
        modifier
            .defaultMinSize(minWidth = 56.dp, minHeight = 26.dp)
            .clip(shape)
            .ifThen(isFocused) {
                border(2.dp, currentFocusRingColor, shape)
            }
            .border(
                1.dp,
                when {
                    !enabled -> currentDisabledBorder
                    isFocused -> currentFocusedBorder
                    else -> currentBorder
                },
                shape
            )
            .background(currentBg)
            .combinedClickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                role = Role.Button,
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(contentPadding),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WithContentColor(currentContentColor) {
            WithContentAlpha(if (enabled) 1f else 0.4f) {
                start?.let {
                    it()
                }
                Text(
                    text = text,
                    modifier = Modifier,
                    fontSize = myTextSizes.base,
                    maxLines = 1,
                    softWrap = false,
                    textAlign = TextAlign.Center,
                )
                end?.let {
                    it()
                }
            }
        }
    }
}
