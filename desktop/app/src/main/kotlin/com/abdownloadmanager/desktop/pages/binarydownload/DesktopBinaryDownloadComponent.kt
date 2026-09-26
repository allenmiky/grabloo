package com.abdownloadmanager.desktop.pages.binarydownload

import com.abdownloadmanager.shared.binarymanager.BinaryDownloadService
import com.abdownloadmanager.shared.util.BaseComponent
import com.arkivanov.decompose.ComponentContext
import ir.amirab.util.binarymanager.BinaryDownloadProgress
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

class DesktopBinaryDownloadComponent(
    ctx: ComponentContext,
    private val binaryDownloadService: BinaryDownloadService,
    private val onComplete: (Boolean) -> Unit,
    private val closeComponent: () -> Unit,
) : BaseComponent(ctx) {

    private val _isComplete = MutableStateFlow(false)
    val isComplete: StateFlow<Boolean> = _isComplete.asStateFlow()

    private val _isSuccess = MutableStateFlow(false)
    val isSuccess: StateFlow<Boolean> = _isSuccess.asStateFlow()

    val isDownloading = binaryDownloadService.isDownloading
    val progress = binaryDownloadService.downloadProgress
    val errorMessage = binaryDownloadService.lastError

    init {
        startDownload()
    }

    fun retry() {
        if (_isComplete.value) {
            startDownload()
        }
    }

    fun onRequestClose() {
        if (_isComplete.value) {
            closeComponent()
        }
    }

    private fun startDownload() {
        _isComplete.value = false
        _isSuccess.value = false
        scope.launch {
            val success = binaryDownloadService.ensureBinariesAvailable()
            _isSuccess.value = success
            _isComplete.value = true
            onComplete(success)
        }
    }

    data class Config(
        val id: String = UUID.randomUUID().toString(),
    )
}
