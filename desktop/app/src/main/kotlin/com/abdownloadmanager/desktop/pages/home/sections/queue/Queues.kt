package com.abdownloadmanager.desktop.pages.home.sections.queue

import androidx.compose.animation.*
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.PointerMatcher
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.onClick
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.abdownloadmanager.desktop.pages.home.HomeComponent
import com.abdownloadmanager.desktop.pages.home.dropDownloadItemsHere
import com.abdownloadmanager.desktop.pages.home.sections.category.SidebarSectionHeader
import com.abdownloadmanager.resources.Res
import com.abdownloadmanager.shared.pages.home.queue.QueueActions
import com.abdownloadmanager.shared.ui.widget.DelayedTooltipPopup
import com.abdownloadmanager.shared.ui.widget.Text
import com.abdownloadmanager.shared.ui.widget.menu.custom.ShowOptionsInPopup
import com.abdownloadmanager.shared.util.div
import com.abdownloadmanager.shared.util.ui.icon.MyIcons
import com.abdownloadmanager.shared.util.ui.myColors
import com.abdownloadmanager.shared.util.ui.theme.myTextSizes
import com.abdownloadmanager.shared.util.ui.widget.MyIcon
import ir.amirab.downloader.queue.DownloadQueue
import ir.amirab.util.compose.action.MenuItem
import ir.amirab.util.compose.asStringSource
import ir.amirab.util.compose.resources.myStringResource
import ir.amirab.util.ifThen

@Composable
internal fun QueuesSection(
    modifier: Modifier,
    component: HomeComponent,
) {
    val currentSelectedQueue = component.filterState.queueFilter
    val queues by component.queueManager.queues.collectAsState()
    val showQueueOption by component.queueActions.collectAsState()

    fun showQueueOption(downloadQueue: DownloadQueue?) {
        component.showCategoryOptions(downloadQueue)
    }

    fun closeQueueOptions() {
        component.closeQueueOptions()
    }

    Column(modifier = modifier) {
        SidebarSectionHeader(myStringResource(Res.string.queues))

        queues.forEach { queue ->
            key(queue.id) {
                val isSelected = currentSelectedQueue?.id == queue.id
                val queueModel by queue.queueModel.collectAsState()
                val count = queueModel.queueItems.size
                QueueSidebarItem(
                    modifier = Modifier
                        .onClick(
                            matcher = PointerMatcher.mouse(PointerButton.Secondary),
                        ) {
                            showQueueOption(queue)
                        },
                    isSelected = isSelected,
                    name = queueModel.name,
                    count = count,
                    onSelect = {
                        component.onQueueFilterChange(queue.queueModel.value)
                    },
                    onItemsDroppedInQueue = { downloadIds ->
                        component.moveItemsToQueue(queue, downloadIds)
                    },
                )
            }
        }
    }

    showQueueOption?.let {
        QueueOption(
            queueOptionMenuState = it,
            onDismiss = {
                closeQueueOptions()
            }
        )
    }
}

@Composable
private fun QueueSidebarItem(
    modifier: Modifier,
    isSelected: Boolean,
    name: String,
    count: Int,
    onSelect: () -> Unit,
    onItemsDroppedInQueue: (List<Long>) -> Unit,
) {
    var isDraggingOnMe by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    Box(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 1.dp)
            .dropDownloadItemsHere(
                onDragIn = { isDraggingOnMe = true },
                onDragDone = { isDraggingOnMe = false },
                onItemsDropped = onItemsDroppedInQueue,
            )
            .clip(RoundedCornerShape(4.dp))
            .background(
                if (isSelected) myColors.selectedRow
                else if (isHovered) myColors.onBackground / 0.05f
                else Color.Transparent
            )
            .hoverable(interactionSource)
            .selectable(
                selected = isSelected,
                onClick = onSelect,
            )
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        if (isDraggingOnMe) {
            DelayedTooltipPopup(
                {},
                myStringResource(Res.string.move_to_this_queue),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = name,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                fontSize = myTextSizes.base,
                color = if (isSelected) myColors.selectedRowText else myColors.onBackground,
            )
            Text(
                text = count.toString(),
                maxLines = 1,
                fontSize = myTextSizes.sm,
                color = if (isSelected) myColors.selectedRowDimText else myColors.dimText,
            )
        }
    }
}

@Composable
fun QueueOption(
    queueOptionMenuState: QueueActions,
    onDismiss: () -> Unit,
) {
    ShowOptionsInPopup(
        MenuItem.SubMenu(
            icon = MyIcons.queue,
            title = queueOptionMenuState.mainQueueModel?.name?.asStringSource()
                ?: Res.string.queues.asStringSource(),
            queueOptionMenuState.menu,
        ),
        onDismiss
    )
}
