package com.abdownloadmanager.shared.ui.widget.menu.custom

import ir.amirab.util.compose.action.MenuItem
import androidx.compose.runtime.Composable

@Composable
expect fun ShowOptionsInPopup(
    menu: MenuItem.SubMenu,
    onDismissRequest: () -> Unit,
)

/**
 * this is only used by expect actual ShowOptionsInPopup if their actual implementations need other style remove it here
 */
@Composable
internal fun RenderOptions(
    menu: MenuItem.SubMenu,
    onDismissRequest: () -> Unit
) {
    SubMenu(menu, onDismissRequest)
}
