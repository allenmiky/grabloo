package com.abdownloadmanager.shared.ui.widget.menu.custom

import com.abdownloadmanager.shared.util.div
import com.abdownloadmanager.shared.util.ui.myColors
import com.abdownloadmanager.shared.util.ui.theme.myTextSizes
import ir.amirab.util.ifThen
import ir.amirab.util.compose.action.MenuItem
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import com.abdownloadmanager.shared.ui.widget.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun MenuBar(
    modifier: Modifier = Modifier,
    subMenuList: List<MenuItem.SubMenu>,
) {
    var openedItem: MenuItem.SubMenu? by remember {
        mutableStateOf(null)
    }
    val onRequestClose = {
        openedItem = null
    }
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        for (subMenu in subMenuList) {
            val isSelected = openedItem == subMenu
            val interactionSource = remember { MutableInteractionSource() }
            val isHovered by interactionSource.collectIsHoveredAsState()
            LaunchedEffect(isHovered) {
                if (isHovered && openedItem != null) {
                    openedItem = subMenu
                }
            }
            Box {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .hoverable(interactionSource)
                        .clickable {
                            openedItem = if (isSelected) null else subMenu
                        }
                        .background(
                            when {
                                isSelected -> myColors.selectedRow
                                isHovered -> myColors.onBackground / 0.1f
                                else -> Color.Transparent
                            }
                        )
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    val text = subMenu.title.collectAsState().value.rememberString()
                    Text(
                        text = text,
                        maxLines = 1,
                        fontSize = myTextSizes.sm,
                        color = if (isSelected) Color.White else myColors.onBackground,
                    )
                }
                if (isSelected) {
                    MyDropDown(
                        onDismissRequest = onRequestClose,
                        focusable = false,
                    ) {
                        CompositionLocalProvider(
                            LocalMenuBoxClip provides RoundedCornerShape(6.dp)
                        ) {
                            SubMenu(subMenu, onRequestClose = onRequestClose)
                        }
                    }
                }
            }
        }
    }
}
