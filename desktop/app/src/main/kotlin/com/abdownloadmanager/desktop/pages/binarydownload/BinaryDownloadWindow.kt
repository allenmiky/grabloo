package com.abdownloadmanager.desktop.pages.binarydownload

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.v2.WindowBoundsProvider
import androidx.compose.ui.window.v2.WindowPositionProvider
import androidx.compose.ui.window.v2.WindowSizeProvider
import androidx.compose.ui.window.v2.rememberWindowState
import com.abdownloadmanager.desktop.AppComponent
import com.abdownloadmanager.desktop.window.custom.CustomWindow
import com.abdownloadmanager.shared.util.ui.theme.LocalUiScale
import ir.amirab.util.desktop.screen.applyUiScale

@Composable
fun BinaryDownloadWindow(
    appComponent: AppComponent,
) {
    appComponent.binaryDownloadDialogSlot.collectAsState().value.child?.instance?.let {
        BinaryDownloadWindow(it)
    }
}

@Composable
private fun BinaryDownloadWindow(
    component: DesktopBinaryDownloadComponent,
) {
    val state = rememberWindowState(
        initialBoundsProvider = WindowBoundsProvider(
            sizeProvider = WindowSizeProvider.Fixed(
                size = DpSize(460.dp, 320.dp).applyUiScale(LocalUiScale.current)
            ),
            positionProvider = WindowPositionProvider.CenteredOnScreen
        )
    )
    CustomWindow(
        state = state,
        resizable = false,
        onRequestToggleMaximize = null,
        onRequestMinimize = null,
        onCloseRequest = component::onRequestClose,
    ) {
        BinaryDownloadPage(component)
    }
}
