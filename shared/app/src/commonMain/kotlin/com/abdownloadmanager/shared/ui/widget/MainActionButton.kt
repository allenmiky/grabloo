package com.abdownloadmanager.shared.ui.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import com.abdownloadmanager.shared.util.div
import com.abdownloadmanager.shared.util.ui.myColors


@Composable
fun PrimaryMainActionButton(
    text: String,
    modifier: Modifier,
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val backgroundColor = SolidColor(myColors.primary)
    val borderColor = SolidColor(myColors.primary)
    val disabledBackgroundColor = SolidColor(myColors.primary / 0.5f)
    val disabledBorderColor = SolidColor(myColors.primary / 0.5f)
    ActionButton(
        text = text,
        modifier = modifier,
        enabled = enabled,
        onClick = onClick,
        onLongClick = onLongClick,
        backgroundColor = backgroundColor,
        disabledBackgroundColor = disabledBackgroundColor,
        borderColor = borderColor,
        disabledBorderColor = disabledBorderColor,
        contentColor = Color.White,
    )
}
