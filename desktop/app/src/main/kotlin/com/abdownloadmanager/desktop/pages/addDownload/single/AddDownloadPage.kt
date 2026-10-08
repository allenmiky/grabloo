package com.abdownloadmanager.desktop.pages.addDownload.single

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.onClick
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import androidx.compose.ui.window.v2.WindowBoundsProvider
import androidx.compose.ui.window.v2.WindowSizeProvider
import androidx.compose.ui.window.v2.rememberDialogState
import arrow.core.Some
import com.abdownloadmanager.desktop.pages.addDownload.shared.*
import com.abdownloadmanager.desktop.window.custom.BaseOptionDialog
import com.abdownloadmanager.desktop.window.moveSafe
import com.abdownloadmanager.resources.Res
import com.abdownloadmanager.shared.downloaderinui.add.CanAddResult
import com.abdownloadmanager.shared.pages.adddownload.shared.LastUsedLocationsProp
import com.abdownloadmanager.shared.pages.adddownload.shared.RememberFolderProp
import com.abdownloadmanager.shared.pages.adddownload.single.BaseAddSingleDownloadComponent
import com.abdownloadmanager.shared.downloaderinui.yt_dlp.VideoQualityConfigurable
import com.abdownloadmanager.shared.downloaderinui.yt_dlp.describeAsLabel
import com.abdownloadmanager.shared.downloaderinui.yt_dlp.describeAsOptionLabel
import com.abdownloadmanager.shared.ui.configurable.item.IntConfigurable
import com.abdownloadmanager.shared.ui.widget.*
import com.abdownloadmanager.shared.util.ClipboardUtil
import com.abdownloadmanager.shared.util.LocalSizeUnit
import com.abdownloadmanager.shared.util.ThreadCountLimitation
import com.abdownloadmanager.shared.util.div
import com.abdownloadmanager.shared.util.downloaderror.DownloadErrorReason
import com.abdownloadmanager.shared.util.mvi.HandleEffects
import com.abdownloadmanager.shared.util.ui.WithContentAlpha
import com.abdownloadmanager.shared.util.ui.WithContentColor
import com.abdownloadmanager.shared.util.ui.icon.MyIcons
import com.abdownloadmanager.shared.util.ui.myColors
import com.abdownloadmanager.shared.util.ui.theme.myShapes
import com.abdownloadmanager.shared.util.ui.theme.mySpacings
import com.abdownloadmanager.shared.util.ui.theme.myTextSizes
import com.abdownloadmanager.shared.util.ui.widget.MyIcon
import ir.amirab.downloader.connection.IResponseInfo
import ir.amirab.downloader.utils.OnDuplicateStrategy
import ir.amirab.util.compose.asStringSource
import ir.amirab.util.compose.resources.myStringResource
import java.awt.MouseInfo

@Composable
fun AddDownloadPage(
    component: BaseAddSingleDownloadComponent,
) {
    val onDuplicateStrategy by component.onDuplicateStrategy.collectAsState()
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .padding(top = 4.dp, bottom = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            Modifier
                .fillMaxHeight()
                .width(IntrinsicSize.Max)
                .widthIn(max = 460.dp)
                .fillMaxWidth()
        ) {
            val credentials by component.credentials.collectAsState()
            fun setLink(link: String) {
                component.setCredentials(
                    credentials.copy(link = Some(link))
                )
            }

            HandleEffects(component) {
                when (it) {
                    is BaseAddSingleDownloadComponent.Effects.Common -> {
                        when (it) {
                            is BaseAddSingleDownloadComponent.Effects.Common.SuggestUrl -> {
                                setLink(it.link)
                            }
                        }
                    }

                    is BaseAddSingleDownloadComponent.Effects.Platform -> {
                        // support platform effects if any
                    }
                }
            }
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RenderFileTypeAndSize(component)
                    Spacer(Modifier.weight(1f))
                    RenderResumeSupport(component)
                    Spacer(Modifier.width(8.dp))
                    ConfigActionsButtons(component)
                }
                Spacer(Modifier.height(12.dp))
            FormLabel(myStringResource(Res.string.links_one_per_line))
            Spacer(Modifier.height(4.dp))
            UrlTextField(
                text = credentials.link,
                setText = {
                    setLink(it)
                },
            )
            Spacer(Modifier.height(4.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                ActionButton(
                    text = myStringResource(Res.string.paste),
                    modifier = Modifier.defaultMinSize(minWidth = 64.dp),
                    onClick = {
                        setLink(
                            ClipboardUtil.read()
                                .orEmpty()
                        )
                    },
                )
            }
            Spacer(Modifier.height(12.dp))
            val canAddResult by component.canAddResult.collectAsState()
            val name by component.name.collectAsState()
            FormLabel(myStringResource(Res.string.name))
            Spacer(Modifier.height(4.dp))
            NameTextField(
                text = name,
                setText = {
                    component.setName(it)
                },
                errorText = when (canAddResult) {
                    is CanAddResult.DownloadAlreadyExists -> {
                        if (onDuplicateStrategy == null) {
                            myStringResource(Res.string.download_already_exists)
                        } else {
                            null
                        }
                    }

                    CanAddResult.InvalidFileName -> myStringResource(Res.string.invalid_file_name)
                    else -> null
                }.takeIf { name.isNotEmpty() }
            )
            Spacer(Modifier.height(12.dp))
            FormLabel(myStringResource(Res.string.save_to))
            Spacer(Modifier.height(4.dp))
            LocationTextField(
                modifier = Modifier.fillMaxWidth(),
                text = component.folder.collectAsState().value,
                setText = {
                    component.setFolder(it)
                },
                errorText = when (canAddResult) {
                    CanAddResult.CantWriteInThisFolder -> myStringResource(Res.string.cant_write_to_this_folder)
                    else -> null
                },
                lastUsedLocations = LastUsedLocationsProp(
                    locations = component.lastUsedLocations.collectAsState().value,
                    onRequestRemove = component::removeFromLastDownloadLocation,
                ),
                rememberFolderAsDefault = RememberFolderProp(
                    isEnabled = component.isFolderChangedResult.collectAsState().value.isChanged,
                    value = component.rememberFolderAsDefault.collectAsState().value,
                    onValueChange = component::setRememberFolderAsDefault,
                    categoryName = component.isFolderChangedResult.collectAsState().value.categoryOrNull()?.name,
                ),
            )
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .height(IntrinsicSize.Max)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val useCategory by component.useCategory.collectAsState()
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .onClick {
                            component.setUseCategory(!useCategory)
                        }
                        .padding(vertical = 4.dp)
                ) {
                    CheckBox(
                        size = 16.dp,
                        value = useCategory,
                        onValueChange = { component.setUseCategory(it) }
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(myStringResource(Res.string.use_category))
                }
                Spacer(Modifier.width(8.dp))
                CategorySelect(
                    modifier = Modifier.weight(1f),
                    enabled = useCategory,
                    categories = component.categories.collectAsState().value,
                    selectedCategory = component.selectedCategory.collectAsState().value,
                    onCategorySelected = {
                        component.setSelectedCategory(it)
                    },
                )
                Spacer(Modifier.width(8.dp))
                CategoryAddButton(
                    enabled = useCategory,
                    modifier = Modifier.fillMaxHeight(),
                    onClick = {
                        component.addNewCategory()
                    },
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val hasQuality = remember {
                    component.configurables.any { it is VideoQualityConfigurable }
                }
                if (hasQuality) {
                    QualitySelect(component)
                } else {
                    ConnectionsSelect(component)
                }
                Spacer(Modifier.width(16.dp))
                QueueSelect(component)
            }
            Spacer(Modifier.height(8.dp))
            val startQueue by component.selectQueueComponent.startQueue.collectAsState()
            LabeledCheckbox(
                value = startQueue,
                onValueChange = component.selectQueueComponent::setStartQueue,
                description = myStringResource(Res.string.start_now),
            )
            }
            Spacer(Modifier.height(8.dp))
            MainActionButtons(component)
            if (component.showSolutionsOnDuplicateDownloadUi) {
                ShowSolutionsOnDuplicateDownload(component)
            }
            ShowAddToQueueDialog(
                queueComponent = component.selectQueueComponent,
            )
            if (component.showMoreSettings) {
                ExtraConfig(
                    onDismiss = { component.showMoreSettings = false },
                    configurables = component.configurables,
                )
            }
        }
    }
}

@Composable
private fun ShowSolutionsOnDuplicateDownload(component: BaseAddSingleDownloadComponent) {
    val state = rememberDialogState(
        initialBoundsProvider = WindowBoundsProvider(
            sizeProvider = WindowSizeProvider.Unconstrained
        )
    )
    val close = {
        component.showSolutionsOnDuplicateDownloadUi = false
    }
    val onDuplicateStrategy by component.onDuplicateStrategy.collectAsState()
    BaseOptionDialog(
        onCloseRequest = close,
        state = state,
        resizeable = false,
    ) {
        LaunchedEffect(window) {
            window.moveSafe(
                MouseInfo.getPointerInfo().location.run {
                    DpOffset(
                        x = x.dp,
                        y = y.dp
                    )
                }
            )
        }


        val shape = myShapes.defaultRounded
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
        ) {
            WithContentColor(myColors.onBackground) {
                Column(
                    Modifier.widthIn(max = 300.dp)
                ) {
                    WindowDraggableArea(Modifier) {
                        Column(
                            Modifier.padding(vertical = 8.dp, horizontal = 16.dp)
                        ) {
                            Text(
                                myStringResource(Res.string.select_a_solution),
                                Modifier,
                                fontSize = myTextSizes.base
                            )
                            Spacer(Modifier.height(8.dp))
                            WithContentAlpha(0.75f) {
                                Text(
                                    myStringResource(Res.string.select_download_strategy_description),
                                    Modifier,
                                    fontSize = myTextSizes.sm,
                                )
                            }
                        }
                    }
                    Column(
                        Modifier
                            .padding(horizontal = 8.dp)
                            .padding(bottom = 8.dp)
                    ) {
                        Spacer(Modifier.height(4.dp))
                        Divider()
                        Spacer(Modifier.height(4.dp))
                        Column {
                            OnDuplicateStrategySolutionItem(
                                isSelected = onDuplicateStrategy == OnDuplicateStrategy.AddNumbered,
                                title = myStringResource(Res.string.download_strategy_add_a_numbered_file),
                                description = myStringResource(Res.string.download_strategy_add_a_numbered_file_description),
                            ) {
                                component.setOnDuplicateStrategy(OnDuplicateStrategy.AddNumbered)
                                close()
                            }
                            OnDuplicateStrategySolutionItem(
                                isSelected = onDuplicateStrategy == OnDuplicateStrategy.OverrideDownload,
                                title = myStringResource(Res.string.download_strategy_override_existing_file),
                                description = myStringResource(Res.string.download_strategy_override_existing_file_description),
                            ) {
                                component.setOnDuplicateStrategy(OnDuplicateStrategy.OverrideDownload)
                                close()
                            }
                            OnDuplicateStrategySolutionItem(
                                isSelected = null,
                                title = myStringResource(Res.string.download_strategy_update_download_link),
                                description = myStringResource(Res.string.download_strategy_update_download_link_description),
                            ) {
                                component.updateDownloadCredentialsOfOriginalDownload()
                                close()
                            }
                            OnDuplicateStrategySolutionItem(
                                isSelected = null,
                                title = myStringResource(Res.string.download_strategy_show_downloaded_file),
                                description = myStringResource(Res.string.download_strategy_show_downloaded_file_description),
                            ) {
                                component.openDownloadFileForCurrentLink()
                                close()
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OnDuplicateStrategySolutionItem(
    title: String,
    description: String,
    isSelected: Boolean?,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(8.dp)
    ) {
        isSelected?.let {
            CheckBox(isSelected, { onClick() }, size = 12.dp)
        }
        Spacer(Modifier.width(8.dp))
        Column {
            Text(
                title,
                fontSize = myTextSizes.base,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            WithContentAlpha(0.7f) {
                Text(
                    text = description,
                    fontSize = myTextSizes.sm,
                    modifier = Modifier
                )
            }
        }

    }
}


@Composable
private fun Divider() {
    Spacer(
        Modifier.fillMaxWidth()
            .height(1.dp)
            .background(myColors.onBackground / 10),
    )
}


@Composable
fun RenderResumeSupport(component: BaseAddSingleDownloadComponent) {
    val responseResult by component.checkResponseResult.collectAsState()
    val fileInfo = responseResult?.getOrNull()?.takeIf { it.isSuccessFul }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.height(16.dp)

    ) {
        val lineModifier = Modifier.weight(1f)
            .height(1.dp)
            .background(myColors.onBackground / 10)
        Box(lineModifier)
        val canAddToDownloads by component.canAddToDownloads.collectAsState()
        AnimatedVisibility(
            visible = canAddToDownloads && fileInfo != null,
        ) {
            fileInfo?.let { fileInfo ->
                val iconModifier = Modifier
                    .padding(horizontal = 2.dp)
                    .size(10.dp)
                if (fileInfo.resumeSupport) {
                    MyIcon(
                        icon = MyIcons.check,
                        contentDescription = null,
                        modifier = iconModifier,
                        tint = myColors.success
                    )
                } else {
                    MyIcon(
                        icon = MyIcons.clear,
                        contentDescription = null,
                        modifier = iconModifier,
                        tint = myColors.error,
                    )
                }
            }
        }
        Box(lineModifier)


    }
}


@Composable
fun ConfigActionsButtons(component: BaseAddSingleDownloadComponent) {
    val responseResult by component.checkResponseResult.collectAsState()
    Row {
        IconActionButton(MyIcons.refresh, Res.string.refresh.asStringSource()) {
            component.refresh()
        }
        Spacer(Modifier.width(6.dp))
        IconActionButton(
            MyIcons.settings,
            Res.string.settings.asStringSource(),
            indicateActive = component.showMoreSettings,
            requiresAttention = responseResult?.getOrNull()?.requireBasicAuth ?: false
        ) {
            component.showMoreSettings = true
        }
        Spacer(Modifier.width(6.dp))
        component.lastError.collectAsState().value?.let { downloadErrorReason ->
            DownloadErrorInfoButton(
                onClick = component::openDownloadErrorDialog,
                responseInfo = responseResult?.getOrNull(),
                reason = downloadErrorReason,
            )
        }
    }
}

@Composable
private fun MainActionButtons(component: BaseAddSingleDownloadComponent) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val onDuplicateStrategy by component.onDuplicateStrategy.collectAsState()
        val canAddResult by component.canAddResult.collectAsState()
        if (canAddResult is CanAddResult.DownloadAlreadyExists && onDuplicateStrategy == null) {
            ActionButton(
                text = myStringResource(Res.string.show_solutions),
                modifier = Modifier,
                onClick = { component.showSolutionsOnDuplicateDownloadUi = true },
            )
            if (component.shouldShowOpenFile.collectAsState().value) {
                Spacer(Modifier.width(8.dp))
                ActionButton(
                    text = myStringResource(Res.string.open_file),
                    modifier = Modifier,
                    onClick = { component.openExistingFile() },
                )
            }
        } else {
            val canAddToDownloads by component.canAddToDownloads.collectAsState()
            ActionButton(
                text = myStringResource(Res.string.add),
                modifier = Modifier.defaultMinSize(minWidth = 80.dp),
                enabled = canAddToDownloads,
                onClick = {
                    component.selectQueueComponent.onConfirm()
                },
                onLongClick = {
                    component.selectQueueComponent.fastConfirm()
                }

            )
            Spacer(Modifier.width(10.dp))
            PrimaryMainActionButton(
                text = myStringResource(Res.string.download),
                modifier = Modifier.defaultMinSize(minWidth = 92.dp),
                enabled = canAddToDownloads,
                onClick = {
                    component.onRequestDownload()
                },
            )
            if (onDuplicateStrategy != null) {
                Spacer(Modifier.width(10.dp))
                ActionButton(
                    text = myStringResource(Res.string.change_solution),
                    modifier = Modifier.defaultMinSize(minWidth = 80.dp),
                    onClick = { component.showSolutionsOnDuplicateDownloadUi = true },
                )
            }

        }
        Spacer(Modifier.width(10.dp))

        ActionButton(
            text = myStringResource(Res.string.cancel),
            modifier = Modifier.defaultMinSize(minWidth = 80.dp),
            onClick = {
                component.onRequestClose()
            },
        )
    }
}

@Composable
fun RenderFileTypeAndSize(
    component: BaseAddSingleDownloadComponent,
) {
    val isLinkLoading by component.isLinkLoading.collectAsState()
    val linkCheckResult by component.checkResponseResult.collectAsState()
    val fileIconProvider = component.iconProvider
    val iconModifier = Modifier.size(mySpacings.iconSize)
    Box(Modifier.padding(top = 16.dp)) {
        AnimatedContent(
            targetState = isLinkLoading,
            transitionSpec = {
                fadeIn() togetherWith fadeOut()
            }
        ) { loading ->
            if (loading) {
                LoadingIndicator(iconModifier)
            } else {
//                val extension = getExtension(fileInfo?.fileName ?: usersSetFileName) ?: "unknown"
                val downloadItem by component.downloadItem.collectAsState()
                val icon = fileIconProvider.rememberIcon(downloadItem.name)

//                val bitmap = FileIconProvider.getIconOfFileExtension(extension)

                AnimatedContent(
                    linkCheckResult,
                ) { linkCheckResult ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        WithContentAlpha(1f) {
                            when (linkCheckResult) {
                                null -> {
                                    MyIcon(
                                        icon = MyIcons.question,
                                        contentDescription = null,
                                        modifier = iconModifier,
                                    )
                                }

                                else -> {
                                    MyIcon(
                                        icon,
                                        null,
                                        iconModifier
                                    )
                                    val size = component.getLengthString()
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        size.rememberString(),
                                        fontSize = myTextSizes.sm,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

fun getExtension(s: String): String? {
    if (s.isBlank()) return null
    return s.substringAfterLast(".", "")
        .takeIf { it.isNotBlank() }
}


@Composable
private fun DownloadErrorInfoButton(
    onClick: () -> Unit,
    reason: DownloadErrorReason,
    responseInfo: IResponseInfo?,
) {
    val tooltipStringSource = reason.title.asStringSource()
    Tooltip(tooltipStringSource) {
        IconActionButton(
            onClick = {
                onClick()
            },
            contentDescription = tooltipStringSource,
            icon = if (responseInfo?.requireBasicAuth == true) {
                MyIcons.lock
            } else {
                MyIcons.question
            },
            contentColor = myColors.error,
        )
    }
}

@Composable
private fun UrlTextField(
    text: String,
    setText: (String) -> Unit,
    errorText: String? = null,
    modifier: Modifier = Modifier,
) {
    MyTextFieldWithIcons(
        text,
        setText,
        myStringResource(Res.string.download_link),
        modifier = modifier.fillMaxWidth(),
        singleLine = false,
        minLines = 3,
        maxLines = 3,
        start = {
            MyIcon(
                MyIcons.link,
                null,
                Modifier
                    .padding(horizontal = 8.dp)
                    .size(15.dp),
            )
        },
        errorText = errorText
    )
}

@Composable
private fun NameTextField(
    text: String,
    setText: (String) -> Unit,
    errorText: String? = null,
) {
    MyTextFieldWithIcons(
        text,
        setText,
        myStringResource(Res.string.name),
        modifier = Modifier.fillMaxWidth(),
        errorText = errorText,
    )
}

@Composable
private fun FormLabel(text: String) {
    Text(
        text,
        fontSize = myTextSizes.base,
        color = myColors.dimText,
    )
}

@Composable
private fun QualitySelect(component: BaseAddSingleDownloadComponent) {
    val qualityConfigurable = remember {
        component.configurables
            .filterIsInstance<VideoQualityConfigurable>()
            .firstOrNull()
    } ?: return
    val sizeUnit = LocalSizeUnit.current
    val selected by qualityConfigurable.stateFlow.collectAsState()
    val available by qualityConfigurable.visibleQualities.collectAsState()
    val hiddenCount by qualityConfigurable.hiddenQualityCount.collectAsState()
    val showingAll by qualityConfigurable.isShowingAllQualities.collectAsState()
    var open by remember { mutableStateOf(false) }

    val closePopup = {
        qualityConfigurable.resetShowingAllQualities()
        open = false
    }

    LaunchedEffect(open) {
        if (!open) {
            qualityConfigurable.resetShowingAllQualities()
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "${myStringResource(Res.string.quality)}:",
            fontSize = myTextSizes.base,
            color = myColors.dimText,
        )
        Spacer(Modifier.width(8.dp))
        ActionButton(
            text = selected.describeAsLabel(sizeUnit),
            onClick = { open = true },
        )
    }
    if (open) {
        val warningColor = if (myColors.isLight) Color(0xFF6D28D9) else Color(0xFFA78BFA)
        SelectPopup(
            title = myStringResource(Res.string.quality),
            items = available,
            renderItem = { it.describeAsLabel(sizeUnit) },
            itemContent = { item ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 7.dp)
                ) {
                    Text(
                        text = item.describeAsLabel(sizeUnit),
                        fontSize = myTextSizes.base,
                        color = myColors.onBackground,
                    )
                    if (item.warning != null) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "ℹ ${item.warning}",
                            fontSize = myTextSizes.xs,
                            color = warningColor,
                            lineHeight = 14.sp,
                        )
                    }
                }
            },
            onDismiss = closePopup,
            onItemSelected = {
                qualityConfigurable.set(it)
                closePopup()
            },
            footer = if (hiddenCount > 0 || showingAll) {
                {
                    Text(
                        if (showingAll) "Show fewer options" else "Show all $hiddenCount more options",
                        Modifier
                            .fillMaxWidth()
                            .clickable { qualityConfigurable.toggleShowingAllQualities() }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        fontSize = myTextSizes.sm,
                        color = myColors.primary,
                    )
                }
            } else {
                null
            },
        )
    }
}

@Composable
private fun ConnectionsSelect(component: BaseAddSingleDownloadComponent) {
    val threadCountConfigurable = remember {
        component.configurables
            .filterIsInstance<IntConfigurable>()
            .firstOrNull { it.range == 0..ThreadCountLimitation.MAX_ALLOWED_THREAD_COUNT }
    }
    if (threadCountConfigurable == null) {
        return
    }
    val value by threadCountConfigurable.stateFlow.collectAsState()
    val options = remember {
        (listOf(0, 1, 2, 4, 8, 16, 32) + ThreadCountLimitation.MAX_ALLOWED_THREAD_COUNT)
            .distinct()
            .filter { it in threadCountConfigurable.range }
    }
    var open by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "${myStringResource(Res.string.connections)}:",
            fontSize = myTextSizes.base,
            color = myColors.dimText,
        )
        Spacer(Modifier.width(8.dp))
        ActionButton(
            text = threadCountConfigurable.describe(value).rememberString(),
            onClick = { open = true },
        )
    }
    if (open) {
        SelectPopup(
            title = myStringResource(Res.string.connections),
            items = options,
            renderItem = { threadCountConfigurable.describe(it).rememberString() },
            onDismiss = { open = false },
            onItemSelected = {
                threadCountConfigurable.set(it)
                open = false
            },
        )
    }
}

@Composable
private fun QueueSelect(component: BaseAddSingleDownloadComponent) {
    val queues by component.queues.collectAsState()
    val selectedQueueId by component.selectQueueComponent.selectedQueue.collectAsState()
    var open by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "${myStringResource(Res.string.queue_label)}:",
            fontSize = myTextSizes.base,
            color = myColors.dimText,
        )
        Spacer(Modifier.width(8.dp))
        ActionButton(
            text = queues.firstOrNull { it.id == selectedQueueId }?.getQueueModel()?.name
                ?: myStringResource(Res.string.default),
            modifier = Modifier.defaultMinSize(minWidth = 64.dp),
            onClick = { open = true },
        )
    }
    if (open) {
        SelectPopup(
            title = myStringResource(Res.string.queue_label),
            items = queues,
            renderItem = { it.getQueueModel().name },
            onDismiss = { open = false },
            onItemSelected = {
                component.selectQueueComponent.setSelectedQueue(it.id)
                open = false
            },
        )
    }
}

@Composable
private fun <T> SelectPopup(
    title: String,
    items: List<T>,
    onDismiss: () -> Unit,
    onItemSelected: (T) -> Unit,
    renderItem: @Composable (T) -> String = { it.toString() },
    itemContent: (@Composable (T) -> Unit)? = null,
    /**
     * pinned below the scroll area, for a control that has to stay reachable no matter how long
     * the list gets. the quality list uses it for the "show all" toggle.
     */
    footer: (@Composable () -> Unit)? = null,
) {
    val state = rememberDialogState(
        initialBoundsProvider = WindowBoundsProvider(
            sizeProvider = WindowSizeProvider.Unconstrained
        )
    )
    BaseOptionDialog(
        onCloseRequest = onDismiss,
        state = state,
        resizeable = false,
    ) {
        val mouseLocation = remember {
            MouseInfo.getPointerInfo().location.run {
                DpOffset(
                    x = x.dp,
                    y = y.dp
                )
            }
        }
        DisposableEffect(window) {
            val updatePosition = {
                window.moveSafe(mouseLocation)
            }
            val listener = object : ComponentAdapter() {
                override fun componentResized(e: ComponentEvent?) {
                    updatePosition()
                }
            }
            window.addComponentListener(listener)
            updatePosition()
            onDispose {
                window.removeComponentListener(listener)
            }
        }

        val shape = myShapes.defaultRounded
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
        ) {
            WithContentColor(myColors.onBackground) {
                Column(
                    Modifier
                        .widthIn(min = 160.dp, max = 340.dp)
                        .width(IntrinsicSize.Max)
                ) {
                    Text(
                        title,
                        Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp),
                        fontSize = myTextSizes.sm,
                        color = myColors.dimText,
                    )
                    // a video can report more qualities than fit on the screen, three codecs per
                    // resolution, so the list has to scroll instead of running off the window
                    Column(
                        Modifier
                            .heightIn(max = 280.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        items.forEach { item ->
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onItemSelected(item)
                                    }
                            ) {
                                if (itemContent != null) {
                                    itemContent(item)
                                } else {
                                    Text(
                                        renderItem(item),
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 8.dp)
                                    )
                                }
                            }
                        }
                    }
                    footer?.invoke()
                }
            }
        }
    }
}

