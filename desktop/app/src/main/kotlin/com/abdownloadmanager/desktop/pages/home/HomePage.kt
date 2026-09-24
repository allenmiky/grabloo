package com.abdownloadmanager.desktop.pages.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.*
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.awtTransferable
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.abdownloadmanager.desktop.actions.gotoSettingsAction
import com.abdownloadmanager.desktop.actions.openQueuesAction
import com.abdownloadmanager.desktop.actions.openSchedulerAction
import com.abdownloadmanager.desktop.actions.stopAllAction
import com.abdownloadmanager.desktop.pages.home.sections.DownloadList
import com.abdownloadmanager.desktop.pages.home.sections.SearchBox
import com.abdownloadmanager.desktop.pages.home.sections.category.Categories
import com.abdownloadmanager.desktop.pages.home.sections.queue.QueuesSection
import com.abdownloadmanager.desktop.window.custom.TitlePosition
import com.abdownloadmanager.desktop.window.custom.WindowEnd
import com.abdownloadmanager.desktop.window.custom.WindowStart
import com.abdownloadmanager.desktop.window.custom.WindowTitlePosition
import com.abdownloadmanager.resources.Res
import com.abdownloadmanager.shared.pages.home.BaseHomeComponent
import com.abdownloadmanager.shared.pages.home.CategoryActions
import com.abdownloadmanager.shared.pages.home.CategoryDeletePromptState
import com.abdownloadmanager.shared.pages.home.ConfirmPromptState
import com.abdownloadmanager.shared.pages.home.DeletePromptState
import com.abdownloadmanager.shared.ui.widget.*
import com.abdownloadmanager.shared.ui.widget.menu.custom.MenuBar
import com.abdownloadmanager.shared.ui.widget.menu.custom.ShowOptionsInPopup
import com.abdownloadmanager.shared.ui.widget.menu.native.NativeMenuBar
import com.abdownloadmanager.shared.util.LocalSpeedUnit
import com.abdownloadmanager.shared.util.LocalSizeUnit
import com.abdownloadmanager.shared.util.TimeNames
import com.abdownloadmanager.shared.util.category.Category
import com.abdownloadmanager.shared.util.category.rememberIconPainter
import com.abdownloadmanager.shared.util.convertPositiveBytesToSizeUnit
import com.abdownloadmanager.shared.util.convertPositiveSizeToHumanReadable
import com.abdownloadmanager.shared.util.convertPositiveSpeedToHumanReadable
import com.abdownloadmanager.shared.util.convertTimeRemainingToHumanReadable
import com.abdownloadmanager.shared.util.div
import com.abdownloadmanager.shared.util.mvi.HandleEffects
import com.abdownloadmanager.shared.util.ui.WithContentAlpha
import com.abdownloadmanager.shared.util.ui.WithContentColor
import com.abdownloadmanager.shared.util.ui.WithTitleBarDirection
import com.abdownloadmanager.shared.util.ui.icon.MyIcons
import com.abdownloadmanager.shared.util.ui.myColors
import com.abdownloadmanager.shared.util.ui.theme.myShapes
import com.abdownloadmanager.shared.util.ui.theme.myTextSizes
import com.abdownloadmanager.shared.util.ui.widget.MyIcon
import ir.amirab.downloader.downloaditem.DownloadJobStatus
import ir.amirab.downloader.monitor.ProcessingDownloadItemState
import ir.amirab.downloader.monitor.UiPart
import ir.amirab.util.compose.IconSource
import ir.amirab.util.compose.action.AnAction
import ir.amirab.util.compose.action.MenuItem
import ir.amirab.util.compose.asStringSource
import ir.amirab.util.compose.asStringSourceWithARgs
import ir.amirab.util.compose.localizationmanager.WithLanguageDirection
import ir.amirab.util.compose.resources.myStringResource
import ir.amirab.util.desktop.LocalFrameWindowScope
import ir.amirab.util.ifThen
import ir.amirab.util.platform.Platform
import ir.amirab.util.platform.isMac
import kotlinx.coroutines.launch
import java.awt.datatransfer.DataFlavor
import java.io.File


@Composable
fun HomePage(component: HomeComponent) {
    val listState by component.downloadList.collectAsState()
    var isDragging by remember { mutableStateOf(false) }

    var showDeletePromptState by remember {
        mutableStateOf(null as DeletePromptState?)
    }

    var showDeleteCategoryPromptState by remember {
        mutableStateOf(null as CategoryDeletePromptState?)
    }

    var showConfirmPrompt by remember {
        mutableStateOf(null as ConfirmPromptState?)
    }

    val coroutineScope = rememberCoroutineScope()
    val lazyListState = rememberLazyListState()
    val tableState = component.tableState

    HandleEffects(component) { effect ->
        when (effect) {
            is BaseHomeComponent.Effects.Common -> {
                when (effect) {
                    is BaseHomeComponent.Effects.Common.DeleteItems -> {
                        if (effect.list.isNotEmpty()) {
                            showDeletePromptState = DeletePromptState(
                                downloadList = effect.list,
                                finishedCount = effect.finishedCount,
                                unfinishedCount = effect.unfinishedCount,
                            )
                        }
                    }

                    is BaseHomeComponent.Effects.Common.DeleteCategory -> {
                        showDeleteCategoryPromptState = CategoryDeletePromptState(effect.category)
                    }

                    is BaseHomeComponent.Effects.Common.AutoCategorize -> {
                        showConfirmPrompt = ConfirmPromptState(
                            title = Res.string.confirm_auto_categorize_downloads_title.asStringSource(),
                            description = Res.string.confirm_auto_categorize_downloads_description.asStringSource(),
                            onConfirm = component::onConfirmAutoCategorize
                        )
                    }

                    is BaseHomeComponent.Effects.Common.ResetCategoriesToDefault -> {
                        showConfirmPrompt = ConfirmPromptState(
                            title = Res.string.confirm_reset_to_default_categories_title.asStringSource(),
                            description = Res.string.confirm_reset_to_default_categories_description.asStringSource(),
                            onConfirm = component::onConfirmResetCategories
                        )
                    }

                    is BaseHomeComponent.Effects.Common.ScrollToDownloadItem -> {
                        val id = effect.downloadId
                        val positionOrNull = tableState
                            .getItemPosition(listState) { it.id == id }
                            .takeIf { it != -1 }
                        positionOrNull?.let { index ->
                            if (effect.skipIfVisible) {
                                val isVisible = lazyListState.layoutInfo.visibleItemsInfo
                                    .any { it.index == index }
                                if (isVisible) {
                                    return@let
                                }
                            }
                            coroutineScope.launch {
                                lazyListState.scrollToItem(index)
                            }
                        }
                    }
                }
            }

            is HomeComponent.Effects -> {
                when (effect) {
                    HomeComponent.Effects.BringToFront -> {
                        // handled else where
                    }
                }
            }
            else -> {}
        }
    }
    showDeletePromptState?.let {
        ShowDeletePrompts(
            deletePromptState = it,
            onCancel = {
                showDeletePromptState = null
            },
            onConfirm = {
                showDeletePromptState = null
                component.confirmDelete(it)
            })
    }
    showDeleteCategoryPromptState?.let {
        ShowDeleteCategoryPrompt(
            deletePromptState = it,
            onCancel = {
                showDeleteCategoryPromptState = null
            },
            onConfirm = {
                showDeleteCategoryPromptState = null
                component.onConfirmDeleteCategory(it)
            })
    }
    showConfirmPrompt?.let {
        ShowConfirmPrompt(
            promptState = it,
            onCancel = {
                showConfirmPrompt = null
            },
            onConfirm = {
                showConfirmPrompt?.onConfirm?.invoke()
                showConfirmPrompt = null
            }
        )
    }
    val mergeTopBar = shouldMergeTopBarWithTitleBar(component)
    if (mergeTopBar) {
        WindowTitlePosition(
            TitlePosition(
                centered = true,
                afterStart = true,
                padding = PaddingValues(end = 32.dp)
            )
        )
        WindowStart {
            HomeMenuBar(component, Modifier.fillMaxHeight())
        }
    } else {
        WindowTitlePosition(
            TitlePosition(centered = false, afterStart = false)
        )
    }

    Box(
        Modifier
            .fillMaxSize()
            .dragAndDropTarget(
                shouldStartDragAndDrop = {
                    if (it.awtTransferable.isDataFlavorSupported(DownloadItemListDataFlavor)) {
                        // this item is ours we don't want to use our download item for import list usage
                        return@dragAndDropTarget false
                    } else it.awtTransferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor) ||
                            it.awtTransferable.isDataFlavorSupported(DataFlavor.stringFlavor)
                },
                target = remember {
                    object : DragAndDropTarget {
                        private fun onDraggedIn(event: DragAndDropEvent) {
                            if (event.awtTransferable.isDataFlavorSupported(DataFlavor.stringFlavor)) {
                                component.onExternalTextDraggedIn {
                                    (event.awtTransferable.getTransferData(
                                        DataFlavor.stringFlavor
                                    ) as String)
                                }
                                return
                            }

                            if (event.awtTransferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                                component.onExternalFilesDraggedIn {
                                    (event.awtTransferable.getTransferData(DataFlavor.javaFileListFlavor) as List<*>).filterIsInstance<File>()
                                }
                                return
                            }
                        }

                        override fun onStarted(event: DragAndDropEvent) {
                            isDragging = true
                            onDraggedIn(event)
                        }

                        override fun onEnded(event: DragAndDropEvent) {
                            isDragging = false
                            component.onDragExit()
                        }

                        override fun onDrop(event: DragAndDropEvent): Boolean {
                            isDragging = false
                            if (Platform.isMac()) {
                                onDraggedIn(event)
                            }
                            component.onDropped()
                            return true
                        }
                    }
                }
            )
    ) {
        Column(
            Modifier.alpha(
                animateFloatAsState(if (isDragging) 0.2f else 1f).value
            )
        ) {
            IdmToolbar(component)
            Spacer(
                Modifier.fillMaxWidth()
                    .height(1.dp)
                    .background(myColors.divider)
            )
            Row(Modifier.weight(1f)) {
                val categoriesWidth by component.categoriesWidth.collectAsState()
                Box(
                    Modifier
                        .width(categoriesWidth)
                        .fillMaxHeight()
                        .background(myColors.sidebar)
                ) {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .padding(top = 4.dp, bottom = 4.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Categories(
                            modifier = Modifier.fillMaxWidth(),
                            component = component,
                        )
                        Spacer(Modifier.size(8.dp))
                        QueuesSection(
                            modifier = Modifier.fillMaxWidth(),
                            component = component,
                        )
                    }
                }
                // Splitter handle
                Handle(
                    Modifier.width(1.dp)
                        .fillMaxHeight()
                        .background(myColors.divider)
                ) { delta ->
                    component.setCategoriesWidth { it + delta }
                }
                Column(Modifier.weight(1f)) {
                    var lastSelected by remember { mutableStateOf(null as Long?) }
                    DownloadList(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        downloadList = listState,
                        downloadOptions = component.downloadOptions.collectAsState().value,
                        onRequestCloseOption = {
                            component.onRequestCloseDownloadItemOption()
                        },
                        onRequestOpenOption = { itemState ->
                            component.onRequestOpenDownloadItemOption(itemState)
                        },
                        selectionList = component.selectionList.collectAsState().value,
                        onItemSelectionChange = { id, checked ->
                            lastSelected = id
                            component.onItemSelectionChange(id, checked)
                        },
                        onRequestOpenDownload = {
                            component.openFileOrShowProperties(it)
                        },
                        onNewSelection = {
                            component.newSelection(ids = it)
                        },
                        lastSelectedId = lastSelected,
                        tableState = tableState,
                        fileIconProvider = component.fileIconProvider,
                        categoryManager = component.categoryManager,
                        lazyListState = lazyListState,
                        failedDownloadReasons = component.failedDownloads.collectAsState().value,
                    )
                    Spacer(
                        Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(myColors.divider)
                    )
                    DownloadDetailsPanel(component)
                    Spacer(
                        Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(myColors.divider)
                    )
                    Footer(component)
                }
            }
        }
        NotificationArea(
            Modifier
                .width(310.dp)
                .padding(24.dp)
                .align(Alignment.BottomEnd)
        )
        AnimatedVisibility(
            visible = isDragging,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            DragWidget(
                Modifier.fillMaxSize()
                    .wrapContentSize(Alignment.Center),
                component.currentActiveDrops.value?.size,
            )
        }
    }
}

@Composable
private fun shouldMergeTopBarWithTitleBar(component: HomeComponent): Boolean {
    val mergeTopBarWithTitleBarInSettings = component.mergeTopBarWithTitleBar.collectAsState().value
    if (!mergeTopBarWithTitleBarInSettings) return false
    val density = LocalDensity.current
    val widthDp = density.run {
        LocalWindowInfo.current.containerSize.width.toDp()
    }
    return widthDp > 700.dp
}


@Composable
private fun ShowDeletePrompts(
    deletePromptState: DeletePromptState,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    val shape = myShapes.defaultRounded
    Dialog(onDismissRequest = onCancel) {
        Column(
            Modifier
                .clip(shape)
                .border(2.dp, myColors.onBackground / 10, shape)
                .background(
                    Brush.linearGradient(
                        listOf(
                            myColors.surface,
                            myColors.background,
                        )
                    )
                )
                .padding(16.dp)
                .width(IntrinsicSize.Max)
                .widthIn(max = 260.dp)
        ) {
            Text(
                myStringResource(Res.string.confirm_delete_download_items_title),
                fontWeight = FontWeight.Bold,
                fontSize = myTextSizes.xl,
                color = myColors.onBackground,
            )
            Spacer(Modifier.height(12.dp))
            val finishedCount = deletePromptState.finishedCount
            val unfinishedCount = deletePromptState.unfinishedCount
            Text(
                when {
                    deletePromptState.hasBothFinishedAndUnfinished() -> {
                        Res.string.confirm_delete_download_finished_and_unfinished_items_description.asStringSourceWithARgs(
                            Res.string.confirm_delete_download_finished_and_unfinished_items_description_createArgs(
                                finishedCount = finishedCount.toString(),
                                unfinishedCount = unfinishedCount.toString(),
                            )
                        )
                    }

                    deletePromptState.hasUnfinishedDownloads -> {
                        Res.string.confirm_delete_download_unfinished_items_description.asStringSourceWithARgs(
                            Res.string.confirm_delete_download_unfinished_items_description_createArgs(
                                count = unfinishedCount.toString(),
                            )
                        )
                    }

                    else -> {
                        Res.string.confirm_delete_download_items_description.asStringSourceWithARgs(
                            Res.string.confirm_delete_download_items_description_createArgs(
                                count = finishedCount.toString()
                            ),
                        )
                    }
                }.rememberString(),
                fontSize = myTextSizes.base,
                color = myColors.onBackground,
            )
            if (deletePromptState.hasFinishedDownloads) {
                Spacer(Modifier.height(12.dp))
                val alsoDeleteFileInteractionSource = remember { MutableInteractionSource() }
                Row(
                    Modifier
                        .clickable(
                            interactionSource = alsoDeleteFileInteractionSource,
                            indication = null
                        ) {
                            deletePromptState.alsoDeleteFile = !deletePromptState.alsoDeleteFile
                        },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CheckBox(
                        value = deletePromptState.alsoDeleteFile,
                        onValueChange = {
                            deletePromptState.alsoDeleteFile = it
                        },
                        modifier = Modifier
                            // the Row itself is clickable (focusable) so we don't need to focus this checkbox
                            // is there a better way?
                            .focusProperties { canFocus = false },
                        interactionSource = alsoDeleteFileInteractionSource,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        myStringResource(Res.string.also_delete_file_from_disk),
                        fontSize = myTextSizes.base,
                        color = myColors.onBackground,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val confirmFocusRequester = remember { FocusRequester() }
                LaunchedEffect(Unit) {
                    confirmFocusRequester.requestFocus()
                }
                Spacer(Modifier.weight(1f))
                ActionButton(
                    text = myStringResource(Res.string.delete),
                    onClick = onConfirm,
                    focusedBorderColor = SolidColor(myColors.error),
                    contentColor = myColors.error,
                    modifier = Modifier.focusRequester(confirmFocusRequester)
                )
                Spacer(Modifier.width(8.dp))
                ActionButton(text = myStringResource(Res.string.cancel), onClick = onCancel)
            }
        }
    }
}

@Composable
private fun ShowConfirmPrompt(
    promptState: ConfirmPromptState,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    val shape = RoundedCornerShape(8.dp)
    Dialog(onDismissRequest = onCancel) {
        Column(
            Modifier
                .clip(shape)
                .border(1.dp, Color(0xFF404040), shape)
                .background(Color(0xFF262626))
                .padding(14.dp)
                .width(IntrinsicSize.Max)
                .widthIn(max = 360.dp)
        ) {
            Text(
                text = promptState.title.rememberString(),
                fontWeight = FontWeight.SemiBold,
                fontSize = myTextSizes.xl,
                color = Color(0xFFE6E6E6),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = promptState.description.rememberString(),
                fontSize = myTextSizes.base,
                color = Color(0xFF93939A),
            )
            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val confirmFocusRequester = remember { FocusRequester() }
                LaunchedEffect(Unit) {
                    confirmFocusRequester.requestFocus()
                }
                Spacer(Modifier.weight(1f))
                ActionButton(
                    text = myStringResource(Res.string.cancel),
                    onClick = onCancel,
                    backgroundColor = SolidColor(Color(0xFF4A4A4A)),
                    contentColor = Color(0xFFE6E6E6),
                )
                Spacer(Modifier.width(8.dp))
                ActionButton(
                    text = myStringResource(Res.string.ok),
                    onClick = onConfirm,
                    backgroundColor = SolidColor(Color(0xFF2F7BF2)),
                    contentColor = Color.White,
                    modifier = Modifier.focusRequester(confirmFocusRequester)
                )
            }
        }
    }
}

@Composable
private fun ShowDeleteCategoryPrompt(
    deletePromptState: CategoryDeletePromptState,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    val shape = RoundedCornerShape(8.dp)
    Dialog(onDismissRequest = onCancel) {
        Column(
            Modifier
                .clip(shape)
                .border(1.dp, Color(0xFF404040), shape)
                .background(Color(0xFF262626))
                .padding(14.dp)
                .width(IntrinsicSize.Max)
                .widthIn(max = 360.dp)
        ) {
            Text(
                myStringResource(
                    Res.string.confirm_delete_category_item_title,
                    Res.string.confirm_delete_category_item_title_createArgs(
                        name = deletePromptState.category.name
                    ),
                ),
                fontWeight = FontWeight.SemiBold,
                fontSize = myTextSizes.xl,
                color = Color(0xFFE6E6E6),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                myStringResource(
                    Res.string.confirm_delete_category_item_description,
                    Res.string.confirm_delete_category_item_description_createArgs(
                        value = deletePromptState.category.name
                    )
                ),
                fontSize = myTextSizes.base,
                color = Color(0xFF93939A),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                myStringResource(Res.string.your_download_will_not_be_deleted),
                fontSize = myTextSizes.base,
                color = Color(0xFF93939A),
            )
            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val confirmFocusRequester = remember { FocusRequester() }
                LaunchedEffect(Unit) {
                    confirmFocusRequester.requestFocus()
                }
                Spacer(Modifier.weight(1f))
                ActionButton(
                    text = myStringResource(Res.string.cancel),
                    onClick = onCancel,
                    backgroundColor = SolidColor(Color(0xFF4A4A4A)),
                    contentColor = Color(0xFFE6E6E6),
                )
                Spacer(Modifier.width(8.dp))
                ActionButton(
                    text = myStringResource(Res.string.delete),
                    onClick = onConfirm,
                    backgroundColor = SolidColor(Color(0xFFE5484D)),
                    contentColor = Color.White,
                    focusedBorderColor = SolidColor(Color(0xFFE5484D)),
                    modifier = Modifier.focusRequester(confirmFocusRequester),
                )
            }
        }
    }
}

@Composable
fun DragWidget(
    modifier: Modifier,
    linkCount: Int?,
) {
    val shape = RoundedCornerShape(12.dp)
    val background = myColors.onBackground / 10
    Column(
        modifier
            .clip(shape)
            .background(background)
            .padding(8.dp)
            .dashedBorder(
                shape = shape,
                width = 2.dp,
                color = myColors.onBackground,
                on = 1.dp,
                off = 4.dp
            )
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        MyIcon(
            MyIcons.download,
            null,
            Modifier.size(36.dp),
        )
        Text(
            text = myStringResource(Res.string.drop_link_or_file_here),
            fontSize = myTextSizes.xl
        )
        if (linkCount != null && Platform.isMac().not()) {
            when {
                linkCount > 0 -> {
                    Text(
                        myStringResource(
                            Res.string.n_links_will_be_imported,
                            Res.string.n_links_will_be_imported_createArgs(
                                count = linkCount.toString()
                            )
                        ),
                        fontSize = myTextSizes.base,
                        color = myColors.success,
                    )
                }

                linkCount == 0 -> {
                    Text(myStringResource(Res.string.nothing_will_be_imported))
                }
            }
        }
    }
}


@Composable
private fun HomeMenuBar(
    component: HomeComponent,
    modifier: Modifier,
) {
    val nativeMenuBarWithTitleBarInSettings by component.useNativeMenuBar.collectAsState()
    val menu = component.menu
    if (nativeMenuBarWithTitleBarInSettings) {
        val scope = LocalFrameWindowScope.current
        NativeMenuBar(scope, menu)
    } else {
        MenuBar(
            modifier,
            menu
        )
    }
}

@Composable
private fun IdmToolbar(component: HomeComponent) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(myColors.toolbar)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        PrimaryToolbarButton(
            icon = MyIcons.add,
            title = "Add URL",
            onClick = { component.requestEnterNewURL() },
        )
        ToolbarAction(component.resumeAction, "Resume")
        ToolbarAction(component.pauseAction, "Pause")
        ToolbarAction(stopAllAction, "Stop all")
        ToolbarAction(component.deleteAction, "Delete")
        ToolbarAction(openQueuesAction, "Queues")
        ToolbarAction(openSchedulerAction, "Scheduler")
        ToolbarAction(gotoSettingsAction, "Options")
        Spacer(Modifier.weight(1f))
        HomeSearch(
            component = component,
            modifier = Modifier.height(24.dp)
        )
    }
}

@Composable
private fun PrimaryToolbarButton(
    icon: IconSource,
    title: String,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    Tooltip(title.asStringSource()) {
        Row(
            Modifier
                .height(24.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(if (isHovered) Color(0xFF4A8DF6) else Color(0xFF2F7BF2))
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick
                )
                .padding(horizontal = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            WithContentColor(Color.White) {
                MyIcon(icon, null, Modifier.size(13.dp))
                Text(
                    text = title,
                    maxLines = 1,
                    fontSize = 11.sp,
                    color = Color.White,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun ToolbarAction(action: AnAction, customTitle: String? = null) {
    val icon by action.icon.collectAsState()
    val title by action.title.collectAsState()
    val enabled by action.isEnabled.collectAsState()
    val displayTitle = customTitle ?: title.rememberString()
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    Tooltip(displayTitle.asStringSource()) {
        Row(
            Modifier
                .height(24.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(if (isHovered && enabled) Color(0x28FFFFFF) else Color(0x33000000))
                .clickable(
                    enabled = enabled,
                    interactionSource = interactionSource,
                    indication = null,
                ) { action() }
                .ifThen(!enabled) { alpha(0.4f) }
                .padding(horizontal = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            WithContentAlpha(1f) {
                icon?.let { MyIcon(it, null, Modifier.size(13.dp)) }
                Text(
                    text = displayTitle,
                    maxLines = 1,
                    fontSize = 11.sp,
                    color = Color(0xFFE6E6E6),
                )
            }
        }
    }
}

@Composable
private fun DownloadDetailsPanel(component: HomeComponent) {
    val selection by component.selectionList.collectAsState()
    val list by component.downloadList.collectAsState()
    val item = selection.singleOrNull()?.let { id -> list.find { it.id == id } }
    if (item == null) {
        Box(
            Modifier
                .fillMaxWidth()
                .background(myColors.panel)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                "Select a download to see its connections.",
                fontSize = myTextSizes.sm,
                color = myColors.dimText,
            )
        }
        return
    }
    Row(
        Modifier
            .fillMaxWidth()
            .background(myColors.panel)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (item is ProcessingDownloadItemState) {
            ConnectionSegments(
                parts = item.parts,
                completed = item.status == DownloadJobStatus.Finished,
                modifier = Modifier
                    .width(200.dp)
                    .height(14.dp),
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                item.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = myTextSizes.base,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(2.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val totalSize = convertPositiveSizeToHumanReadable(
                    item.contentLength,
                    LocalSizeUnit.current,
                ).rememberString()
                if (item is ProcessingDownloadItemState && item.hasProgress) {
                    DetailsLabeled(
                        myStringResource(Res.string.size),
                        "${convertPositiveSizeToHumanReadable(item.progress, LocalSizeUnit.current).rememberString()} / $totalSize",
                    )
                    DetailsLabeled(
                        myStringResource(Res.string.speed),
                        if (item.status == DownloadJobStatus.Downloading && item.speed > 0) {
                            convertPositiveSpeedToHumanReadable(item.speed, LocalSpeedUnit.current)
                        } else {
                            "--"
                        },
                    )
                    DetailsLabeled(
                        myStringResource(Res.string.time_left),
                        item.remainingTime?.let {
                            convertTimeRemainingToHumanReadable(it, TimeNames.ShortNames)
                        } ?: "--",
                    )
                } else {
                    DetailsLabeled(myStringResource(Res.string.size), totalSize)
                }
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            val pauseEnabled by component.pauseAction.isEnabled.collectAsState()
            val deleteEnabled by component.deleteAction.isEnabled.collectAsState()
            ActionButton(
                text = myStringResource(Res.string.cancel),
                onClick = { component.deleteAction() },
                enabled = deleteEnabled,
                backgroundColor = myColors.secondaryButtonGradient,
                contentColor = myColors.onBackground,
            )
            ActionButton(
                text = myStringResource(Res.string.pause),
                onClick = { component.pauseAction() },
                enabled = pauseEnabled,
                backgroundColor = myColors.secondaryButtonGradient,
                contentColor = myColors.onBackground,
            )
            ActionButton(
                text = myStringResource(Res.string.open_folder),
                onClick = { component.openFolder(item.id) },
                backgroundColor = myColors.secondaryButtonGradient,
                contentColor = myColors.onBackground,
            )
            Spacer(Modifier.width(4.dp))
            SpeedLimitSwitch(component)
        }
    }
}

@Composable
private fun DetailsLabeled(label: String, value: String) {
    Column {
        Text(label, fontSize = myTextSizes.xs, color = myColors.dimText, maxLines = 1)
        Text(value, fontSize = myTextSizes.sm, maxLines = 1)
    }
}

@Composable
private fun ConnectionSegments(
    parts: List<UiPart>,
    completed: Boolean,
    modifier: Modifier = Modifier,
) {
    val segments = remember(parts) {
        val n = parts.size
        if (n == 0) {
            emptyList()
        } else if (n <= 8) {
            parts.map { it.percent ?: 0 }
        } else {
            val bucketSize = (n + 7) / 8
            parts.chunked(bucketSize).take(8).map { chunk ->
                val weight = chunk.sumOf { it.partSpace.toDouble() }.coerceAtLeast(0.0)
                val avg = if (weight == 0.0) {
                    chunk.map { it.percent ?: 0 }.average()
                } else {
                    chunk.sumOf { it.partSpace.toDouble() * (it.percent ?: 0) } / weight
                }
                avg.toInt()
            }
        }
    }
    Row(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(8) { i ->
            val percent = if (completed) 100 else segments.getOrNull(i) ?: 0
            val shape = RoundedCornerShape(3.dp)
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(shape)
                    .background(myColors.divider)
            ) {
                if (percent > 0) {
                    Spacer(
                        Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(percent / 100f)
                            .background(if (completed) myColors.successGradient else myColors.primaryButtonGradient)
                    )
                }
            }
        }
    }
}

@Composable
private fun SpeedLimitSwitch(component: HomeComponent) {
    val speedLimit by component.globalSpeedLimit.collectAsState()
    var lastLimit by remember { mutableStateOf(0L) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column {
            Text(
                myStringResource(Res.string.settings_global_speed_limiter),
                fontSize = myTextSizes.xs,
                color = myColors.dimText,
                maxLines = 1,
            )
            Text(
                if (speedLimit > 0) {
                    convertPositiveSpeedToHumanReadable(speedLimit, LocalSpeedUnit.current)
                } else {
                    myStringResource(Res.string.unlimited)
                },
                fontSize = myTextSizes.sm,
                maxLines = 1,
            )
        }
        Spacer(Modifier.width(8.dp))
        Switch(
            checked = speedLimit > 0,
            onCheckedChange = { on ->
                if (on) {
                    val restore = lastLimit.takeIf { it > 0 } ?: (1024L * 1024L)
                    component.globalSpeedLimit.value = restore
                } else {
                    if (speedLimit > 0) {
                        lastLimit = speedLimit
                    }
                    component.globalSpeedLimit.value = 0L
                }
            }
        )
    }
}

@Composable
private fun Footer(component: HomeComponent) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(myColors.background)
            .padding(vertical = 4.dp, horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val activeCount by component.activeDownloadCountFlow.collectAsState()
        val listState by component.downloadList.collectAsState()
        val totalCount = listState.size
        val size by component.globalSpeedFlow.collectAsState(0)
        val speed = convertPositiveBytesToSizeUnit(size, LocalSpeedUnit.current)
        val speedText = if (speed != null && size > 0) "${speed.formatedValue()} ${speed.unit}/s" else "0 B/s"

        Text(
            "$activeCount active  ·  $speedText  ·  $totalCount total",
            fontSize = myTextSizes.sm,
            color = myColors.dimText,
        )
        Spacer(Modifier.weight(1f))
        val speedLimit by component.globalSpeedLimit.collectAsState()
        val speedLimitText = if (speedLimit > 0) {
            convertPositiveSpeedToHumanReadable(speedLimit, LocalSpeedUnit.current)
        } else {
            "off"
        }
        Text(
            "Speed limit: $speedLimitText",
            fontSize = myTextSizes.sm,
            color = myColors.dimText,
            modifier = Modifier.clickable {
                if (speedLimit > 0) {
                    component.globalSpeedLimit.value = 0L
                } else {
                    component.globalSpeedLimit.value = 1024L * 1024L
                }
            }
        )
    }
}

@Composable
fun HomeSearch(
    component: HomeComponent,
    modifier: Modifier,
    textPadding: PaddingValues = PaddingValues(horizontal = 6.dp),
) {
    val searchBoxInteractionSource = remember { MutableInteractionSource() }
    val isFocused by searchBoxInteractionSource.collectIsFocusedAsState()
    WithLanguageDirection {
        SearchBox(
            text = component.filterState.textToSearch,
            onTextChange = {
                component.filterState.textToSearch = it
            },
            textPadding = textPadding,
            interactionSource = searchBoxInteractionSource,
            placeholder = "Search downloads",
            modifier = modifier
                .width(
                    animateDpAsState(
                        if (isFocused) 220.dp else 170.dp
                    ).value
                )
        )
    }
}





