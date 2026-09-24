package com.abdownloadmanager.shared.ui.widget.menu.custom

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.abdownloadmanager.shared.ui.widget.Text
import com.abdownloadmanager.shared.util.LocalShortCutManager
import com.abdownloadmanager.shared.util.PlatformKeyStroke
import com.abdownloadmanager.shared.util.div
import com.abdownloadmanager.shared.util.ui.ProvideTextStyle
import com.abdownloadmanager.shared.util.ui.WithContentColor
import com.abdownloadmanager.shared.util.ui.icon.MyIcons
import com.abdownloadmanager.shared.util.ui.myColors
import com.abdownloadmanager.shared.util.ui.theme.myTextSizes
import com.abdownloadmanager.shared.util.ui.widget.MyIcon
import ir.amirab.util.compose.action.MenuItem
import ir.amirab.util.compose.modifiers.autoMirror
import ir.amirab.util.ifThen

enum class MenuDisabledItemBehavior {
    Filter,
    LowerOpacity,
}

val LocalMenuDisabledItemBehavior = compositionLocalOf {
    MenuDisabledItemBehavior.LowerOpacity
}
val LocalMenuBoxClip = compositionLocalOf<Shape> {
    RoundedCornerShape(6.dp)
}

/**
 * render a menu
 */
@Composable
fun SubMenu(
    subMenu: MenuItem.SubMenu,
    onRequestClose: () -> Unit,
    header: (@Composable () -> Unit)? = null,
) {
    SubMenu(
        subMenu = subMenu.items.collectAsState().value,
        header = header,
        onRequestClose = onRequestClose,
    )
}

@Composable
fun SubMenu(
    subMenu: List<MenuItem>,
    onRequestClose: () -> Unit,
    header: (@Composable () -> Unit)? = null,
) {
    var openedItem: MenuItem.SubMenu? by remember {
        mutableStateOf(null)
    }
    var lastHoveredItem by remember {
        mutableStateOf(null as MenuItem?)
    }

    WithContentColor(myColors.onMenuColor) {
        val shape = LocalMenuBoxClip.current
        Column(
            Modifier
                .shadow(16.dp, shape)
                .clip(shape)
                .width(IntrinsicSize.Max)
                .widthIn(140.dp)
                .border(1.dp, myColors.divider, shape)
                .background(myColors.surface)
                .padding(vertical = 3.dp)
        ) {
            header?.invoke()
            for (menuItem in subMenu) {
                val interactionSource = remember { MutableInteractionSource() }
                val isHovered by interactionSource.collectIsHoveredAsState()
                LaunchedEffect(isHovered) {
                    if (isHovered) {
                        lastHoveredItem = menuItem
                    }
                }
                RenderMenuItem(
                    menuItem = menuItem,
                    openedItem = openedItem,
                    onRequestCLose = onRequestClose,
                    isSelected = openedItem == menuItem,
                    onRequestOpenItem = {
                        openedItem = it
                    },
                    isHovered = lastHoveredItem == menuItem,
                    modifier = Modifier.hoverable(interactionSource)
                )
            }
        }
    }
}

@Composable
private fun ReactableItem(
    item: MenuItem.ReadableItem,
    onClick: () -> Unit,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    extraContent: @Composable () -> Unit = {},
) {
    val iconModifier = Modifier.size(14.dp)
    val title by item.title.collectAsState()
    val icon by item.icon.collectAsState()
    val itemPadding = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isEnabled = (item as? MenuItem.HasEnable)
        ?.isEnabled
        ?.collectAsState()
        ?.value ?: true
    val isHighlighted = ((isHovered && isEnabled) || isSelected)
    Row(
        modifier
            .ifThen(!isEnabled) { alpha(0.5f) }
            .hoverable(interactionSource)
            .background(
                if (isHighlighted) {
                    myColors.selectedRow
                } else {
                    Color.Transparent
                }
            )
            .clickable(enabled = isEnabled) {
                onClick()
            }
            .then(itemPadding)
            .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        icon?.let { iconPainter ->
            MyIcon(
                iconPainter,
                null,
                iconModifier,
                tint = if (isHighlighted) Color.White else myColors.onBackground
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(
            title.rememberString(),
            Modifier.weight(1f),
            fontSize = myTextSizes.base,
            color = if (isHighlighted) Color.White else myColors.onBackground,
            fontWeight = if (isHighlighted) FontWeight.Medium else FontWeight.Normal,
            softWrap = false,
            maxLines = 1,
        )
        Spacer(Modifier.width(16.dp))
        WithContentColor(if (isHighlighted) Color.White else myColors.onBackground) {
            extraContent()
        }
    }
}

@Composable
private fun RenderMenuItem(
    menuItem: MenuItem,
    openedItem: MenuItem.SubMenu?,
    onRequestCLose: () -> Unit,
    isSelected: Boolean,
    isHovered: Boolean,
    modifier: Modifier = Modifier,
    onRequestOpenItem: (MenuItem.SubMenu?) -> Unit,
) {
    LaunchedEffect(isHovered, menuItem) {
        if (isHovered) {
            if (menuItem is MenuItem.SubMenu) {
                onRequestOpenItem(menuItem)
            } else {
                onRequestOpenItem(null)
            }
        }
    }
    Row(
        modifier
            .fillMaxWidth()
    ) {
        when (menuItem) {
            MenuItem.Separator -> {
                Spacer(
                    Modifier
                        .padding(vertical = 3.dp)
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(myColors.divider)
                )
            }

            is MenuItem.SingleItem -> {
                RenderSingleItem(
                    item = menuItem,
                    isSelected = isSelected,
                    onRequestClose = onRequestCLose,
                )
            }

            is MenuItem.SubMenu -> {
                RenderSubMenuItem(
                    menuItem = menuItem,
                    isSelected = isSelected,
                    onRequestCLose = onRequestCLose,
                    openedItem = openedItem,
                    onRequestOpenItem = onRequestOpenItem,
                )
            }
        }
    }
}

@Composable
private fun RenderSubMenuItem(
    menuItem: MenuItem.SubMenu,
    isSelected: Boolean,
    openedItem: MenuItem.SubMenu?,
    onRequestOpenItem: (MenuItem.SubMenu?) -> Unit,
    onRequestCLose: () -> Unit,
) {
    ReactableItem(
        item = menuItem,
        onClick = {
            onRequestOpenItem(menuItem)
        },
        isSelected = isSelected,
        extraContent = {
            MyIcon(
                MyIcons.next,
                null,
                Modifier
                    .size(12.dp)
                    .autoMirror(),
            )
        })
    if (openedItem == menuItem) {
        SiblingDropDown(
            onDismissRequest = {
                onRequestOpenItem(null)
            }
        ) {
            SubMenu(menuItem, onRequestCLose)
        }
    }
}

@Composable
private fun RenderSingleItem(
    onRequestClose: () -> Unit,
    isSelected: Boolean,
    item: MenuItem.SingleItem,
) {
    val isEnabled by item.isEnabled.collectAsState()
    if (!isEnabled && LocalMenuDisabledItemBehavior.current == MenuDisabledItemBehavior.Filter) {
        return
    }

    val shortcutManager = LocalShortCutManager.current
    val shortcutStroke = remember(shortcutManager, item) {
        shortcutManager?.getShortCutOf(item)
    }
    val onClick = {
        if (item.shouldDismissOnClick) {
            onRequestClose()
        }
        item.onClick()
    }
    ReactableItem(
        item = item,
        onClick = onClick,
        isSelected = isSelected,
        extraContent = {
            if (shortcutStroke != null) {
                RenderShortcutStroke(shortcutStroke)
            }
        }
    )
}

@Composable
private fun RenderShortcutStroke(shortcutStroke: PlatformKeyStroke) {
    val modifiers = remember(shortcutStroke) {
        buildList {
            addAll(shortcutStroke.getModifiers())
            add(shortcutStroke.getKeyText())
        }
    }
    ProvideTextStyle(
        TextStyle(
            fontSize = myTextSizes.xs,
        )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            val shape = RoundedCornerShape(3.dp)
            WithContentColor(myColors.onBackground) {
                Text(
                    modifiers.joinToString("+"),
                    Modifier
                        .clip(shape)
                        .background(myColors.onBackground / 0.1f)
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                )
            }
        }
    }
}
