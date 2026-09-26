package com.abdownloadmanager.shared.binarymanager

import com.abdownloadmanager.shared.pagemanager.BinaryDownloadDialogManager
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * makes it easy to use a platform binary download dialog from a suspend context, the result is
 * resolved once the user either finishes or dismisses the dialog.
 */
suspend fun BinaryDownloadDialogManager.openBinaryDownloadDialogAndAwait(): Boolean {
    return suspendCancellableCoroutine { continuation ->
        openBinaryDownloadDialog { success ->
            if (continuation.isActive) {
                continuation.resume(success)
            }
        }
    }
}
