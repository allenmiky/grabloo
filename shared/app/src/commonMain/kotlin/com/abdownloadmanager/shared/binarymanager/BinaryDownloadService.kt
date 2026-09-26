package com.abdownloadmanager.shared.binarymanager

import com.abdownloadmanager.shared.util.DefinedPaths
import ir.amirab.util.binarymanager.BinaryManager
import ir.amirab.util.binarymanager.BinaryDownloadProgress
import ir.amirab.util.binarymanager.Binaries
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

/**
 * keeps the external binaries (yt-dlp / ffmpeg) available, and reports the download progress to the UI.
 */
class BinaryDownloadService(
    definedPaths: DefinedPaths,
    private val scope: CoroutineScope,
) {
    val binDir: File = Binaries.getBinDir(definedPaths.downloadDataDir.toFile())

    private val downloadLock = Mutex()

    private val _downloadProgress = MutableStateFlow<BinaryDownloadProgress?>(null)
    val downloadProgress = _downloadProgress.asStateFlow()

    private val _isDownloading = MutableStateFlow(false)
    val isDownloading = _isDownloading.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError = _lastError.asStateFlow()

    fun areBinariesAvailable(): Boolean = Binaries.getMissing(binDir).isEmpty()

    fun getMissingBinariesMessage(): String = Binaries.getMissingMessage(binDir)

    suspend fun ensureBinariesAvailable(): Boolean {
        if (areBinariesAvailable()) return true
        return downloadMissingBinaries()
    }

    private suspend fun downloadMissingBinaries(): Boolean = downloadLock.withLock {
        if (areBinariesAvailable()) return@withLock true
        _isDownloading.value = true
        _lastError.value = null
        try {
            binDir.mkdirs()
            if (Binaries.getMissing(binDir).contains(Binaries.YT_DLP)) {
                BinaryManager.downloadYtDlp(binDir) { progress -> _downloadProgress.value = progress }
            }
            if (Binaries.getMissing(binDir).any { it == Binaries.FFMPEG || it == Binaries.FFPROBE }) {
                BinaryManager.downloadFFmpeg(binDir) { progress -> _downloadProgress.value = progress }
            }
            val stillMissing = Binaries.getMissing(binDir)
            if (stillMissing.isNotEmpty()) {
                _lastError.value = "Failed to download: ${stillMissing.joinToString(", ")}"
                false
            } else {
                true
            }
        } catch (e: Exception) {
            _lastError.value = e.message ?: "Failed to download the required components"
            false
        } finally {
            _isDownloading.value = false
        }
    }
}
