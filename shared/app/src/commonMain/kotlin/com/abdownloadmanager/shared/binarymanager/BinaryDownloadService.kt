package com.abdownloadmanager.shared.binarymanager

import com.abdownloadmanager.shared.util.DefinedPaths
import co.touchlab.kermit.Severity
import ir.amirab.util.binarymanager.BinaryManager
import ir.amirab.util.binarymanager.BinaryDownloadProgress
import ir.amirab.util.binarymanager.BinaryPhase
import ir.amirab.util.binarymanager.Binaries
import ir.amirab.util.logger.thisLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
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

    // Per-binary progress tracking
    private val _ytDlpProgress = MutableStateFlow<BinaryDownloadProgress?>(null)
    private val _ffmpegProgress = MutableStateFlow<BinaryDownloadProgress?>(null)

    val ytDlpProgress = _ytDlpProgress.asStateFlow()
    val ffmpegProgress = _ffmpegProgress.asStateFlow()

    // Combined progress for backward compatibility
    val downloadProgress = combine(_ytDlpProgress, _ffmpegProgress) { ytDlp, ffmpeg ->
        // Surface whichever binary is still in flight. Once both report complete (or one of them
        // was never started) fall back to the first non-null so the UI keeps something to render
        // instead of flipping back to null.
        when {
            ytDlp?.isComplete == false -> ytDlp
            ffmpeg?.isComplete == false -> ffmpeg
            else -> ytDlp ?: ffmpeg
        }
    }

    private val _isDownloading = MutableStateFlow(false)
    val isDownloading = _isDownloading.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError = _lastError.asStateFlow()

    // For cancel support
    private var downloadJob: Job? = null

    fun areBinariesAvailable(): Boolean = Binaries.getMissing(binDir).isEmpty()

    fun getMissingBinariesMessage(): String = Binaries.getMissingMessage(binDir)

    suspend fun ensureBinariesAvailable(): Boolean {
        if (areBinariesAvailable()) return true
        return downloadMissingBinaries()
    }

    /** Background prefetch - non-blocking, low priority */
    fun startBackgroundPrefetch() {
        if (areBinariesAvailable()) return
        if (_isDownloading.value) return

        scope.launch(Dispatchers.IO + kotlinx.coroutines.CoroutineName("BinaryPrefetch")) {
            // downloadMissingBinaries() acquires downloadLock internally — do NOT wrap it in
            // another downloadLock.withLock here or the two withLock calls deadlock each other.
            downloadMissingBinaries()
        }
    }

    private suspend fun downloadMissingBinaries(): Boolean = downloadLock.withLock {
        if (areBinariesAvailable()) return@withLock true

        _isDownloading.value = true
        _lastError.value = null
        _ytDlpProgress.value = null
        _ffmpegProgress.value = null

        // Track the job of the coroutine that is actually doing the download. A fresh Job() here
        // was detached from the work, so cancel() cancelled an empty job and the download kept
        // running to completion while the UI pretended it had stopped.
        downloadJob = currentCoroutineContext().job
        val success = try {
            binDir.mkdirs()
            // Keep the real reason per binary. Swallowing it (the old "if (!needsOther) throw e")
            // left the UI showing only "Failed to download: yt-dlp", which hides things like a
            // checksum mismatch or a 404 from an expired mirror.
            val failures = mutableListOf<String>()

            val missing = Binaries.getMissing(binDir)
            thisLogger().log(
                Severity.Info,
                "",
                null,
                "Downloading missing binaries: ${missing.joinToString(", ").ifEmpty { "<none>" }}"
                        + " into ${binDir.absolutePath}",
            )
            val needsYtDlp = missing.contains(Binaries.YT_DLP)
            val needsFfmpeg = missing.any { it == Binaries.FFMPEG || it == Binaries.FFPROBE }

            if (needsYtDlp) {
                try {
                    BinaryManager.downloadYtDlp(binDir) { progress ->
                        _ytDlpProgress.value = progress
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    thisLogger().log(Severity.Error, "", e, "yt-dlp download failed")
                    failures += "${Binaries.YT_DLP}: ${e.message ?: e::class.simpleName}"
                }
            }

            if (needsFfmpeg) {
                try {
                    BinaryManager.downloadFFmpeg(binDir) { progress ->
                        _ffmpegProgress.value = progress
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    thisLogger().log(Severity.Error, "", e, "ffmpeg download failed")
                    failures += "${Binaries.FFMPEG}: ${e.message ?: e::class.simpleName}"
                }
            }

            val stillMissing = Binaries.getMissing(binDir)
            if (stillMissing.isNotEmpty()) {
                _lastError.value = buildString {
                    append("Failed to download: ${stillMissing.joinToString(", ")}")
                    if (failures.isNotEmpty()) {
                        append(" (")
                        append(failures.joinToString("; "))
                        append(")")
                    }
                }
                false
            } else {
                true
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Log it. _lastError only feeds the UI, so a failure here used to leave no trace
            // anywhere: the bin dir would sit empty and nothing would say why.
            thisLogger().log(Severity.Error, "", e, "Binary download failed")
            _lastError.value = e.message ?: "Failed to download the required components"
            false
        } finally {
            _isDownloading.value = false
            downloadJob = null
            _ytDlpProgress.value = _ytDlpProgress.value?.copy(phase = if (_ytDlpProgress.value?.isComplete == true) BinaryPhase.Complete else BinaryPhase.Error)
            _ffmpegProgress.value = _ffmpegProgress.value?.copy(phase = if (_ffmpegProgress.value?.isComplete == true) BinaryPhase.Complete else BinaryPhase.Error)
        }
        return success
    }

    /** Cancel ongoing download */
    fun cancel() {
        downloadJob?.cancel()
    }

    /** Retry only the failed/missing binaries */
    suspend fun retryFailed(): Boolean {
        return downloadMissingBinaries()
    }

    /** Reset error state */
    fun clearError() {
        _lastError.value = null
    }
}