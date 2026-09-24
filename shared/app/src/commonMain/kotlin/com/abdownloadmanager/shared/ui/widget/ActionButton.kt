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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.abdownloadmanager.shared.util.div
import com.abdownloadmanager.shared.util.ui.LocalContentColor
import com.abdownloadmanager.shared.util.ui.WithContentAlpha
import com.abdownloadmanager.shared.util.ui.WithContentColor
import com.abdownloadmanager.shared.util.ui.myColors
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import com.abdownloadmanager.shared.util.ui.theme.myShapes
import com.abdownloadmanager.shared.util.ui.theme.mySpacings
import com.abdownloadmanager.shared.util.ui.theme.myTextSizes
import ir.amirab.util.ifThen

@Composable
fun ActionButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    backgroundColor: Brush = SolidColor(Color(0xFF4A4A4A)),
    hoverBackgroundColor: Brush = SolidColor(Color(0xFF565656)),
    disabledBackgroundColor: Brush = SolidColor(Color(0xFF333333)),
    borderColor: Brush = SolidColor(Color(0x66000000)),
    focusedBorderColor: Brush = SolidColor(Color(0xFF2F7BF2)),
    disabledBorderColor: Brush = SolidColor(Color(0x33000000)),
    contentColor: Color = Color(0xFFE6E6E6),
    contentPadding: PaddingValues = PaddingValues(vertical = 2.dp, horizontal = 12.dp),
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    start: (@Composable RowScope.() -> Unit)? = null,
    end: (@Composable RowScope.() -> Unit)? = null,
) {
    val isFocused by interactionSource.collectIsFocusedAsState()
    val isHovered by interactionSource.collectIsHoveredAsState()
    val shape = RoundedCornerShape(4.dp)
    val currentBg = when {
        !enabled -> disabledBackgroundColor
        isHovered -> hoverBackgroundColor
        else -> backgroundColor
    }
    Row(
        modifier
            .heightIn(min = 24.dp)
            .height(24.dp)
            .clip(shape)
            .ifThen(isFocused) {
                border(2.dp, Color(0x732F7BF2), shape)
            }
            .border(1.dp, if (isFocused) focusedBorderColor else (if (enabled) borderColor else disabledBorderColor), shape)
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
        WithContentColor(contentColor) {
            WithContentAlpha(if (enabled) 1f else 0.5f) {
                start?.let {
                    it()
                }
                Text(
                    text = text,
                    modifier = Modifier,
                    fontSize = myTextSizes.base,
                    maxLines = 1,
                    softWrap = false,
                )
                end?.let {
                    it()
                }
            }
        }
    }
}
