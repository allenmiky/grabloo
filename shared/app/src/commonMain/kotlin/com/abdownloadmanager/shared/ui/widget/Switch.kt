package com.abdownloadmanager.shared.ui.widget

import com.abdownloadmanager.shared.util.ui.myColors
import ir.amirab.util.ifThen
import com.abdownloadmanager.shared.util.div
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

import androidx.compose.ui.graphics.SolidColor

@Composable
fun Switch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier.width(38.dp).height(22.dp),
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    val trackBrush = if (checked) {
        myColors.primaryButtonGradient
    } else {
        SolidColor(myColors.divider)
    }
    Box(
        modifier
            .clip(CircleShape)
            .ifThen(!enabled) {
                alpha(0.5f)
            }
            .background(trackBrush)
            .toggleable(
                value = checked,
                onValueChange = onCheckedChange,
                enabled = enabled,
                role = Role.Switch,
                interactionSource = interactionSource,
                indication = null
            )
            .padding(2.5.dp)
            .fillMaxSize()
    ) {
        val knobAlignment by animateFloatAsState(if (checked) 1f else -1f)
        Box(
            Modifier
                .fillMaxHeight()
                .aspectRatio(1f, true)
                .align(BiasAlignment(knobAlignment, 0f))
                .clip(CircleShape)
                .background(Color.White)
        )
    }
}
