package com.abdownloadmanager.shared.pagemanager

interface BinaryDownloadDialogManager {
    /**
     * shows the binary download UI if needed, and calls [onComplete] with the result.
     * returns immediately, use [openBinaryDownloadDialogAndAwait] when the result is needed.
     */
    fun openBinaryDownloadDialog(onComplete: (Boolean) -> Unit)
    fun closeBinaryDownloadDialog()
}
