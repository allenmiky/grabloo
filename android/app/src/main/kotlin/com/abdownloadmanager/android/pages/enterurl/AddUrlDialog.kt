package com.abdownloadmanager.android.pages.enterurl

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandIn
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.abdownloadmanager.android.ui.SheetHeader
import com.abdownloadmanager.android.ui.SheetTitle
import com.abdownloadmanager.android.ui.SheetUI
import com.abdownloadmanager.resources.Res
import com.abdownloadmanager.shared.downloaderinui.DownloaderInUiRegistry
import com.abdownloadmanager.shared.pages.adddownload.AddDownloadConfig
import com.abdownloadmanager.shared.pages.adddownload.AddDownloadCredentialsInUiProps
import com.abdownloadmanager.shared.pages.adddownload.ImportOptions
import com.abdownloadmanager.shared.pages.adddownload.SilentImportOptions
import com.abdownloadmanager.shared.pages.adddownload.single.BaseAddSingleDownloadComponent
import com.abdownloadmanager.shared.repository.BaseAppRepository
import com.abdownloadmanager.shared.storage.ILastSavedLocationsStorage
import com.abdownloadmanager.shared.storage.ISelectQueueStorage
import com.abdownloadmanager.shared.util.BaseComponent
import com.abdownloadmanager.shared.util.ClipboardUtil
import com.abdownloadmanager.shared.util.DownloadSystem
import com.abdownloadmanager.shared.util.category.CategoryManager
import com.abdownloadmanager.shared.util.mvi.ContainsEffects
import com.abdownloadmanager.shared.util.mvi.HandleEffects
import com.abdownloadmanager.shared.util.mvi.supportEffects
import com.abdownloadmanager.shared.util.perhostsettings.PerHostSettingsManager
import com.arkivanov.decompose.ComponentContext
import com.abdownloadmanager.android.pages.addDownload.single.AndroidAddSingleDownloadComponent
import ir.amirab.downloader.NewDownloadItemProps
import ir.amirab.downloader.downloaditem.DownloadJobExtraConfig
import ir.amirab.downloader.downloaditem.EmptyContext
import ir.amirab.downloader.queue.QueueManager
import ir.amirab.downloader.utils.OnDuplicateStrategy
import ir.amirab.downloader.utils.orDefault
import ir.amirab.util.compose.StringSource
import ir.amirab.util.flow.combineStateFlows
import ir.amirab.util.flow.mapStateFlow
import ir.amirab.util.flow.onEachLatest
import ir.amirab.util.toSingleLine
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.selects.select
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import ir.amirab.downloader.downloaditem.http.HttpDownloadCredentials
import com.abdownloadmanager.shared.ui.widget.*
import com.abdownloadmanager.shared.util.ui.WithContentAlpha
import com.abdownloadmanager.shared.util.ui.WithContentColor
import com.abdownloadmanager.shared.util.ui.icon.MyIcons
import com.abdownloadmanager.shared.util.ui.myColors
import com.abdownloadmanager.shared.util.ui.theme.myShapes
import com.abdownloadmanager.shared.util.ui.theme.mySpacings
import com.abdownloadmanager.shared.util.ui.theme.myTextSizes
import com.abdownloadmanager.shared.util.ui.widget.MyIcon
import ir.amirab.util.compose.asStringSource
import ir.amirab.util.compose.resources.myStringResource

@Composable
fun AddUrlDialog(
    component: AddUrlDialogComponent,
    onDismiss: () -> Unit,
) {
    val linkFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        linkFocus.requestFocus()
        component.onPageOpen()
    }

    HandleEffects(component) { effect ->
        when (effect) {
            is AddUrlDialogComponent.Effects.BringToFront -> {
            }
        }
    }

    val dialogState = remember { mutableStateOf(true) }
    LaunchedEffect(component) {
        if (component == null) {
            dialogState.value = false
        } else {
            dialogState.value = true
        }
    }

    AnimatedVisibility(
        visible = dialogState.value,
        enter = fadeIn() + expandIn { it },
        exit = fadeOut() + shrinkOut { it }
    ) {
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(
                usePlatformDefaultWidth = false
            )
        ) {
            val shape = myShapes.defaultRounded
            Column(
                Modifier
                    .clip(shape)
                    .border(2.dp, myColors.onBackground / 10, shape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                myColors.surface,
                                myColors.background,
                            )
                        )
                    )
                    .width(IntrinsicSize.Max)
                    .widthIn(min = 340.dp, max = 460.dp)
            ) {
                WithContentColor(myColors.onBackground) {
                    Column(
                        Modifier
                            .padding(16.dp)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            myStringResource(Res.string.add_download),
                            fontSize = myTextSizes.xl,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(16.dp))

                        FormLabel(myStringResource(Res.string.links_one_per_line))
                        Spacer(Modifier.height(4.dp))
                        UrlTextArea(
                            text = component.links.collectAsState().value,
                            onTextChange = component::setLinks,
                            modifier = Modifier
                                .focusRequester(linkFocus)
                                .fillMaxWidth()
                                .minLines(3)
                                .maxLines(6),
                        )
                        Spacer(Modifier.height(4.dp))
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            ActionButton(
                                text = myStringResource(Res.string.paste),
                                onClick = {
                                    component.setLinks(
                                        ClipboardUtil.read()
                                            .orEmpty()
                                    )
                                },
                            )
                        }
                        Spacer(Modifier.height(12.dp))

                        FormLabel(myStringResource(Res.string.name))
                        Spacer(Modifier.height(4.dp))
                        NameTextField(
                            text = component.name.collectAsState().value,
                            onTextChange = component::setName,
                        )
                        Spacer(Modifier.height(12.dp))

                        FormLabel(myStringResource(Res.string.save_to))
                        Spacer(Modifier.height(4.dp))
                        LocationTextField(
                            text = component.folder.collectAsState().value,
                            onTextChange = component::setFolder,
                            lastUsedLocations = component.lastUsedLocations.collectAsState().value,
                            onRemoveLocation = component::removeFromLastUsedLocations,
                        )
                        Spacer(Modifier.height(12.dp))

                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ConnectionsSelect(component)
                            Spacer(Modifier.width(16.dp))
                            QueueSelect(component)
                        }
                        Spacer(Modifier.height(8.dp))

                        LabeledCheckbox(
                            value = component.startNow.collectAsState().value,
                            onValueChange = component::setStartNow,
                            modifier = Modifier,
                            description = myStringResource(Res.string.start_now),
                        )
                        Spacer(Modifier.height(8.dp))

                        component.errorText.collectAsState().value?.let { errorRes ->
                            Text(
                                errorRes.rememberString(),
                                fontSize = myTextSizes.sm,
                                color = myColors.error,
                            )
                            Spacer(Modifier.height(8.dp))
                        }

                        Spacer(Modifier.weight(1f))

                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            ActionButton(
                                text = myStringResource(Res.string.cancel),
                                onClick = onDismiss,
                            )
                            Spacer(Modifier.width(8.dp))
                            PrimaryMainActionButton(
                                text = myStringResource(Res.string.add),
                                modifier = Modifier,
                                enabled = component.canAdd.collectAsState().value,
                                onClick = {
                                    component.onAdd()
                                    onDismiss()
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

class AddUrlDialogComponent(
    ctx: ComponentContext,
    private val appRepository: BaseAppRepository,
    private val queueManager: QueueManager,
    private val selectQueueStorage: ISelectQueueStorage,
    private val categoryManager: CategoryManager,
    private val perHostSettingsManager: PerHostSettingsManager,
    private val lastSavedLocationsStorage: ILastSavedLocationsStorage,
    private val downloadSystem: DownloadSystem,
    private val onRequestAdd: (List<AddDownloadCredentialsInUiProps>) -> Unit,
) : BaseComponent(ctx),
    ContainsEffects<AddUrlDialogComponent.Effects> by supportEffects() {

    private val _links = MutableStateFlow("")
    val links = _links.asStateFlow()

    private val _name = MutableStateFlow("")
    val name = _name.asStateFlow()

    private val _folder = MutableStateFlow(appRepository.saveLocation.value)
    val folder = _folder.asStateFlow()

    private val _connections = MutableStateFlow(8)
    val connections = _connections.asStateFlow()

    private val _queueId = MutableStateFlow<Long?>(null)
    val queueId = _queueId.asStateFlow()

    private val _startNow = MutableStateFlow(true)
    val startNow = _startNow.asStateFlow()

    private val _errorText = MutableStateFlow<ir.amirab.util.compose.StringSource?>(null)
    val errorText = _errorText.asStateFlow()

    private val _lastUsedLocations = lastSavedLocationsStorage.lastUsedSaveLocations
    val lastUsedLocations: StateFlow<List<String>> = _lastUsedLocations.asStateFlow()

    val queues = queueManager.queues
        .stateIn(
            scope,
            SharingStarted.WhileSubscribed(),
            emptyList()
        )

    private val _canAdd = MutableStateFlow(false)
    val canAdd = _canAdd.asStateFlow()

    init {
        links
            .map { it.split('\n').map { it.trim() }.filter { it.isNotEmpty() } }
            .distinctUntilChanged()
            .onEach { urlList ->
                val hasValidUrl = urlList.any { isValidUrl(it) }
                _canAdd.value = hasValidUrl
                _errorText.value = if (urlList.isNotEmpty() && !hasValidUrl) {
                    Res.string.invalid_url.asStringSource()
                } else null
            }
            .launchIn(scope)
    }

    fun setLinks(text: String) {
        _links.value = text
    }

    fun setName(text: String) {
        _name.value = text.toSingleLine()
    }

    fun setFolder(folder: String) {
        _folder.value = folder
    }

    fun setConnections(count: Int) {
        _connections.value = count
    }

    fun setQueueId(queueId: Long?) {
        _queueId.value = queueId
    }

    fun setStartNow(startNow: Boolean) {
        _startNow.value = startNow
    }

    fun removeFromLastUsedLocations(location: String) {
        _lastUsedLocations.update { it.filter { it != location } }
    }

    fun onAdd() {
        val urlList = _links.value.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
        val credsList = urlList.map { url ->
            val fileName = if (urlList.size == 1 && _name.value.isNotBlank()) {
                _name.value
            } else {
                nameOf(url) ?: "download"
            }
            AddDownloadCredentialsInUiProps(
                credentials = HttpDownloadCredentials(
                    link = url,
                ),
                extraConfig = AddDownloadCredentialsInUiProps.Configs(
                    suggestedName = fileName,
                ),
            )
        }
        onRequestAdd(credsList)
    }

    fun onPageOpen() {
        scope.launch {
            val clipboard = ClipboardUtil.read() ?: return@launch
            val links = clipboard.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
            if (links.size == 1 && isValidUrl(links.first())) {
                setLinks(links.first())
            }
        }
    }

    fun bringToFront() {
        sendEffect(Effects.BringToFront)
    }

    private fun isValidUrl(url: String): Boolean {
        return url.startsWith("http://") || url.startsWith("https://")
    }

    private fun nameOf(url: String): String? {
        try {
            return java.net.URL(url).path.split('/').last().takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            return null
        }
    }

    companion object {
        fun create(
            ctx: ComponentContext,
            appRepository: BaseAppRepository,
            queueManager: QueueManager,
            selectQueueStorage: ISelectQueueStorage,
            categoryManager: CategoryManager,
            perHostSettingsManager: PerHostSettingsManager,
            lastSavedLocationsStorage: ILastSavedLocationsStorage,
            downloadSystem: DownloadSystem,
            onRequestAdd: (List<AddDownloadCredentialsInUiProps>) -> Unit,
        ) = AddUrlDialogComponent(
            ctx = ctx,
            appRepository = appRepository,
            queueManager = queueManager,
            selectQueueStorage = selectQueueStorage,
            categoryManager = categoryManager,
            perHostSettingsManager = perHostSettingsManager,
            lastSavedLocationsStorage = lastSavedLocationsStorage,
            downloadSystem = downloadSystem,
            onRequestAdd = onRequestAdd,
        )
    }

    sealed interface Effects {
        data object BringToFront : Effects
        sealed interface Platform : Effects
    }
}

@Composable
private fun UrlTextArea(
    text: String,
    onTextChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    errorText: String? = null,
) {
    MyTextFieldWithIcons(
        text,
        onTextChange,
        myStringResource(Res.string.download_link),
        modifier = modifier.fillMaxWidth(),
        singleLine = false,
        minLines = 1,
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
        errorText = errorText,
    )
}

@Composable
private fun NameTextField(
    text: String,
    onTextChange: (String) -> Unit,
    errorText: String? = null,
) {
    MyTextFieldWithIcons(
        text,
        onTextChange,
        myStringResource(Res.string.name),
        modifier = Modifier.fillMaxWidth(),
        errorText = errorText,
    )
}

@Composable
private fun LocationTextField(
    text: String,
    onTextChange: (String) -> Unit,
    lastUsedLocations: List<String>,
    onRemoveLocation: (String) -> Unit,
) {
    MyTextFieldWithIcons(
        text,
        onTextChange,
        myStringResource(Res.string.save_to),
        modifier = Modifier.fillMaxWidth(),
        start = {
            MyIcon(
                MyIcons.folder,
                null,
                Modifier
                    .padding(horizontal = 8.dp)
                    .size(15.dp),
            )
        },
        end = {
            MyTextFieldIcon(MyIcons.paste) {
                // TODO: open folder picker
            }
        },
    )
}

@Composable
private fun FormLabel(text: String) {
    Text(
        text,
        fontSize = myTextSizes.sm,
        color = myColors.dimText,
        fontWeight = FontWeight.Medium,
    )
}

@Composable
private fun ConnectionsSelect(component: AddUrlDialogComponent) {
    val options = listOf(1, 2, 4, 8, 16, 32)
    var open by remember { mutableStateOf(false) }
    val value = component.connections.collectAsState().value
    Row(
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "${myStringResource(Res.string.connections)}:",
            fontSize = myTextSizes.sm,
            color = myColors.dimText,
        )
        Spacer(Modifier.width(8.dp))
        ActionButton(
            text = value.toString(),
            onClick = { open = true },
        )
    }
    if (open) {
        SelectPopup(
            title = myStringResource(Res.string.connections),
            items = options,
            onDismiss = { open = false },
            onItemSelected = { component.setConnections(it) },
        )
    }
}

@Composable
private fun QueueSelect(component: AddUrlDialogComponent) {
    val queues = component.queues.collectAsState().value
    var open by remember { mutableStateOf(false) }
    val selectedQueueId = component.queueId.collectAsState().value
    val selectedQueueName = queues.firstOrNull { it.id == selectedQueueId }?.name ?: myStringResource(Res.string.default)
    Row(
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "${myStringResource(Res.string.queue_label)}:",
            fontSize = myTextSizes.sm,
            color = myColors.dimText,
        )
        Spacer(Modifier.width(8.dp))
        ActionButton(
            text = selectedQueueName,
            onClick = { open = true },
        )
    }
    if (open) {
        SelectPopup(
            title = myStringResource(Res.string.queue_label),
            items = queues,
            renderItem = { it.name },
            onDismiss = { open = false },
            onItemSelected = { component.setQueueId(it.id) },
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
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        val shape = myShapes.defaultRounded
        Column(
            Modifier
                .clip(shape)
                .border(2.dp, myColors.onBackground / 10, shape)
                .background(
                    Brush.linearGradient(
                        colors = listOf(myColors.surface, myColors.background)
                    )
                )
                .widthIn(min = 140.dp, max = 280.dp)
                .width(IntrinsicSize.Max)
        ) {
            WithContentColor(myColors.onBackground) {
                Column {
                    Text(
                        title,
                        Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp),
                        fontSize = myTextSizes.sm,
                        color = myColors.dimText,
                    )
                    // a video can report more qualities than fit on the screen, three codecs per
                    // resolution, so the list has to scroll instead of running off the screen
                    Column(
                        Modifier
                            .heightIn(max = 400.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        items.forEach { item ->
                            Text(
                                renderItem(item),
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { onItemSelected(item) }
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}