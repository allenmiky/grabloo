package com.abdownloadmanager.shared.util.ui.icon

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import okio.FileSystem
import okio.Path.Companion.toPath

actual fun loadGrabboLogo(): ImageBitmap {
    val bytes = FileSystem.RESOURCES.read("com/abdownloadmanager/shared/logo/grabbo_logo.png".toPath()) {
        readByteArray()
    }
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size).asImageBitmap()
}
