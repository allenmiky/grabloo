package com.abdownloadmanager.shared.ui.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import com.abdownloadmanager.shared.util.brighten
import com.abdownloadmanager.shared.util.div

@Composable
fun DangerActionButton(
    text: String,
    modifier: Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    val dangerColor = Color(0xFFE5484D)
    ActionButton(
        text = text,
        modifier = modifier,
        enabled = enabled,
        onClick = onClick,
        onLongClick = onLongClick,
        backgroundColor = SolidColor(dangerColor),
        hoverBackgroundColor = SolidColor(dangerColor.brighten()),
        disabledBackgroundColor = SolidColor(dangerColor),
        borderColor = SolidColor(Color.Black / 0.4f),
        focusedBorderColor = SolidColor(dangerColor),
        focusRingColor = Color.Transparent,
        contentColor = Color.White,
    )
}
