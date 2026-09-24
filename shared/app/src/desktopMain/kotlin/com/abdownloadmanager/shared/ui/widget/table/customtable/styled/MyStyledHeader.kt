package com.abdownloadmanager.shared.ui.widget.table.customtable.styled

import com.abdownloadmanager.shared.util.ui.myColors
import com.abdownloadmanager.shared.ui.widget.table.customtable.TableScope
import com.abdownloadmanager.shared.util.ui.WithContentAlpha
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.abdownloadmanager.shared.util.ui.theme.myShapes

import androidx.compose.foundation.border

@Composable
fun TableScope.MyStyledTableHeader(
    itemHorizontalPadding: Dp,
    content: @Composable () -> Unit,
) {
    val shape = myShapes.defaultRounded
    WithContentAlpha(0.85f) {
        Box(
            Modifier
                .widthIn(getTableSize().visibleWidth)
                .clip(shape)
                .border(0.5.dp, myColors.divider, shape)
                .background(myColors.toolbar)
                .padding(vertical = 3.dp, horizontal = itemHorizontalPadding)
        ) {
            content()
        }
    }
}
