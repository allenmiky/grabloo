package com.abdownloadmanager.shared.util.ui.icon

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import okio.FileSystem
import okio.Path.Companion.toPath
import org.jetbrains.skia.Image

actual fun loadGrabboLogo(): ImageBitmap {
    val bytes = FileSystem.RESOURCES.read("com/abdownloadmanager/shared/logo/grabbo_logo.png".toPath()) {
        readByteArray()
    }
    return Image.makeFromEncoded(bytes).toComposeImageBitmap()
}
