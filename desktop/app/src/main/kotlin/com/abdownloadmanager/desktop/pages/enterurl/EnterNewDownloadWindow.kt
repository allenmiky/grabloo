package com.abdownloadmanager.desktop.pages.enterurl

import androidx.compose.runtime.Composable
import com.abdownloadmanager.desktop.AppComponent
import com.abdownloadmanager.shared.util.rememberChild
import ir.amirab.util.compose.resources.myStringResource

@Composable
fun EnterNewDownloadWindow(
    appComponent: AppComponent
) {
    val child = appComponent.addUrlDialogSlot.rememberChild()
    child?.let {
        AddUrlDialog(
            component = it,
            onDismiss = { appComponent.closeEnterNewURLWindow() }
        )
    }
}

