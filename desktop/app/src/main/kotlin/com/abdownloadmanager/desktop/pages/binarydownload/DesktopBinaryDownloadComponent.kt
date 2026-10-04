package com.abdownloadmanager.desktop.pages.binarydownload

import com.abdownloadmanager.shared.binarymanager.BinaryDownloadService
import com.abdownloadmanager.shared.util.BaseComponent
import com.arkivanov.decompose.ComponentContext
import ir.amirab.util.binarymanager.BinaryDownloadProgress
import ir.amirab.util.binarymanager.BinaryPhase
import ir.amirab.util.binarymanager.Binaries
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

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

    // Per-binary progress
    val ytDlpProgress = binaryDownloadService.ytDlpProgress
    val ffmpegProgress = binaryDownloadService.ffmpegProgress

    // Combined error
    val errorMessage = binaryDownloadService.lastError

    // Retry state - which binary failed
    private val _failedBinaries = MutableStateFlow<Set<String>>(emptySet())
    val failedBinaries = _failedBinaries.asStateFlow()

    init {
        startDownload()
    }

    fun retry() {
        if (_isComplete.value) {
            _failedBinaries.value = emptySet()
            startDownload()
        }
    }

    fun retryFailed() {
        if (_isComplete.value) {
            _isComplete.value = false
            _isSuccess.value = false
            scope.launch {
                val success = try {
                    binaryDownloadService.retryFailed()
                } catch (e: kotlinx.coroutines.CancellationException) {
                    false
                }
                _isSuccess.value = success
                _isComplete.value = true
                if (!success) {
                    val missing = Binaries.getMissing(binaryDownloadService.binDir)
                    _failedBinaries.value = missing.toSet()
                }
                onComplete(success)
            }
        }
    }

    fun cancel() {
        binaryDownloadService.cancel()
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
            val success = try {
                binaryDownloadService.ensureBinariesAvailable()
            } catch (e: kotlinx.coroutines.CancellationException) {
                false
            }
            _isSuccess.value = success
            _isComplete.value = true
            if (!success) {
                // Track which binaries failed
                val missing = Binaries.getMissing(binaryDownloadService.binDir)
                _failedBinaries.value = missing.toSet()
            }
            onComplete(success)
        }
    }

    data class Config(
        val id: String = java.util.UUID.randomUUID().toString(),
    )
}