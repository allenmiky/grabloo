package com.abdownloadmanager.desktop.pages.home.sections.category

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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.abdownloadmanager.resources.Res
import com.abdownloadmanager.shared.pages.home.CategoryActions
import com.abdownloadmanager.shared.pages.home.category.DefinedStatusCategories
import com.abdownloadmanager.shared.pages.home.category.DownloadStatusCategoryFilter
import com.abdownloadmanager.shared.ui.widget.DelayedTooltipPopup
import com.abdownloadmanager.shared.ui.widget.Text
import com.abdownloadmanager.shared.ui.widget.menu.custom.ShowOptionsInPopup
import com.abdownloadmanager.shared.util.category.Category
import com.abdownloadmanager.shared.util.category.rememberIconPainter
import com.abdownloadmanager.shared.util.div
import com.abdownloadmanager.shared.util.ui.WithContentAlpha
import com.abdownloadmanager.shared.util.ui.icon.MyIcons
import com.abdownloadmanager.shared.util.ui.myColors
import com.abdownloadmanager.shared.util.ui.theme.myTextSizes
import com.abdownloadmanager.shared.util.ui.widget.MyIcon
import ir.amirab.downloader.downloaditem.DownloadJobStatus
import ir.amirab.util.compose.action.MenuItem
import ir.amirab.util.compose.asStringSource
import ir.amirab.util.compose.resources.myStringResource
import ir.amirab.util.ifThen
import sh.calvin.reorderable.ReorderableColumn
import sh.calvin.reorderable.ReorderableListItemScope

@Composable
fun Categories(
    modifier: Modifier,
    component: HomeComponent,
) {
    val currentTypeFilter = component.filterState.typeCategoryFilter
    val currentStatusFilter = component.filterState.statusFilter
    val currentQueueFilter = component.filterState.queueFilter
    val categories by component.categoryManager.categoriesFlow.collectAsState()
    val allDownloads by component.allDownloadsFlow.collectAsState()
    val showCategoryOption by component.categoryActions.collectAsState()

    fun showCategoryOption(item: Category?) {
        component.showCategoryOptions(item)
    }

    fun closeCategoryOptions() {
        component.closeCategoryOptions()
    }

    Column(modifier = modifier) {
        SidebarSectionHeader("Downloads")

        for (statusCategory in DefinedStatusCategories.values()) {
            val count = if (statusCategory == DefinedStatusCategories.All) {
                allDownloads.size
            } else {
                allDownloads.count { statusCategory.accept(it) }
            }
            val isSelected = currentQueueFilter == null && currentTypeFilter == null && currentStatusFilter == statusCategory
            SidebarItem(
                title = statusCategory.name.rememberString(),
                count = count,
                isSelected = isSelected,
                onClick = {
                    component.onCategoryFilterChange(statusCategory, null)
                },
            )
        }

        Spacer(Modifier.height(8.dp))

        // Categories Section Header
        SidebarSectionHeader(myStringResource(Res.string.categories))

        ReorderableColumn(
            list = categories,
            onSettle = { from, to ->
                component.reorderCategory(from, to - from)
            },
        ) { index, category, isDragging ->
            key(category.id) {
                ReorderableItem {
                    val isSelected = currentQueueFilter == null && currentTypeFilter == category
                    val count = category.items.size
                    CategorySidebarItem(
                        modifier = Modifier
                            .onClick(
                                matcher = PointerMatcher.mouse(PointerButton.Secondary),
                            ) {
                                showCategoryOption(category)
                            },
                        category = category,
                        isSelected = isSelected,
                        count = count,
                        onItemsDropped = {
                            component.moveItemsToCategory(category, it)
                        },
                        onClick = {
                            component.onCategoryFilterChange(DefinedStatusCategories.All, category)
                        },
                        isDragging = isDragging,
                    )
                }
            }
        }
    }

    showCategoryOption?.let {
        CategoryOption(
            categoryOptionMenuState = it,
            onDismiss = {
                closeCategoryOptions()
            }
        )
    }
}

@Composable
fun SidebarSectionHeader(title: String) {
    Text(
        text = title,
        fontSize = myTextSizes.sm,
        color = myColors.dimText,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        maxLines = 1,
    )
}

@Composable
fun SidebarItem(
    title: String,
    count: Int,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    Box(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 1.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(
                if (isSelected) myColors.selectedRow
                else if (isHovered) myColors.onBackground / 0.05f
                else Color.Transparent
            )
            .hoverable(interactionSource)
            .selectable(
                selected = isSelected,
                onClick = onClick,
            )
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
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
private fun ReorderableListItemScope.CategorySidebarItem(
    modifier: Modifier,
    category: Category,
    isSelected: Boolean,
    count: Int,
    onItemsDropped: (ids: List<Long>) -> Unit,
    onClick: () -> Unit,
    isDragging: Boolean,
) {
    var isDraggingOnMe by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val shouldShowDragIcon = isHovered && !isDraggingOnMe || isDragging

    Box(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 1.dp)
            .dropDownloadItemsHere(
                onDragIn = { isDraggingOnMe = true },
                onDragDone = { isDraggingOnMe = false },
                onItemsDropped = onItemsDropped,
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
                onClick = onClick,
            )
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        if (isDraggingOnMe) {
            DelayedTooltipPopup(
                {},
                myStringResource(Res.string.move_to_this_category),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = category.name,
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
            AnimatedVisibility(visible = shouldShowDragIcon) {
                MyIcon(
                    MyIcons.grip,
                    null,
                    Modifier
                        .draggableHandle()
                        .size(12.dp)
                        .padding(start = 4.dp)
                        .alpha(if (isDragging) 1f else 0.5f)
                )
            }
        }
    }
}

@Composable
fun CategoryOption(
    categoryOptionMenuState: CategoryActions,
    onDismiss: () -> Unit,
) {
    ShowOptionsInPopup(
        MenuItem.SubMenu(
            icon = categoryOptionMenuState.categoryItem?.rememberIconPainter(),
            title = categoryOptionMenuState.categoryItem?.name?.asStringSource()
                ?: Res.string.categories.asStringSource(),
            categoryOptionMenuState.menu,
        ),
        onDismiss
    )
}
