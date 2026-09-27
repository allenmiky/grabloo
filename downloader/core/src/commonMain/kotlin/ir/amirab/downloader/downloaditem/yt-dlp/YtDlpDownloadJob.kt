package ir.amirab.downloader.downloaditem.yt_dlp

import ir.amirab.downloader.DownloadManager
import ir.amirab.util.binarymanager.Binaries
import ir.amirab.downloader.destination.DownloadDestination
import ir.amirab.downloader.destination.SimpleDownloadDestination
import ir.amirab.downloader.downloaditem.DownloadJob
import ir.amirab.downloader.downloaditem.DownloadJobExtraConfig
import ir.amirab.downloader.downloaditem.DownloadJobStatus
import ir.amirab.downloader.downloaditem.DownloadStatus
import ir.amirab.downloader.downloaditem.IDownloadItem
import ir.amirab.downloader.exception.DownloadValidationException
import ir.amirab.downloader.exception.TooManyErrorException
import ir.amirab.downloader.utils.ExceptionUtils
import ir.amirab.downloader.utils.printStackIfNOtUsual
import ir.amirab.util.tryLocked
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import java.io.File
import kotlin.time.Duration.Companion.milliseconds

/**
 * yt-dlp does its work in stages and reports the current one on stdout. the ui has no part list to
 * show for this downloader, so this is what fills that gap and tells the user why the download is
 * still running before the first byte arrives.
 */
sealed interface YtDlpPhase {
    /** nothing useful was reported yet */
    data object Starting : YtDlpPhase

    /** yt-dlp is resolving the page and picking formats, no bytes are moving yet */
    data class Resolving(val message: String) : YtDlpPhase

    /** the media itself is on the wire, the progress bar is the thing to watch now */
    data object Downloading : YtDlpPhase

    /**
     * containers are being merged or cleaned up. this stage can take a noticeable amount of time on
     * large files, so showing it stops the ui from looking frozen while it happens.
     */
    data class PostProcessing(val message: String) : YtDlpPhase
}

private val logVideoIdPrefix = Regex("""^\S{8,24}:\s+""")
private val logWhitespace = Regex("""\s+""")
private val logBareProgress = Regex("""^\d+(\.\d+)?\s?%.*""")
private val logPostProcessingTags = setOf(
    "merger",
    "fixup",
    "metadata",
    "postprocess",
    "embedsubtitle",
    "sponsorblock",
)

class YtDlpDownloadJob(
    override val downloadItem: YtDlpDownloadItem,
    downloadManager: DownloadManager,
) : DownloadJob(
    downloadManager = downloadManager,
) {
    private val binDir: File = Binaries.getBinDir(downloadManager.downloadDataFolder)

    @Volatile
    private var destination: SimpleDownloadDestination? = null

    /**
     * the destination is normally created by [actualBoot], but this is also reachable from
     * [DownloadJob.downloadRemoved] and from the error handling paths. deriving it from the item
     * keeps those paths working instead of failing with an uninitialized property crash.
     */
    override fun getDestination(): DownloadDestination {
        return destination ?: createDestination(
            folder = downloadItem.folder,
            name = downloadItem.name,
        ).also { destination = it }
    }

    private val _progress = MutableStateFlow<YtDlpDownloadProgress?>(null)
    val progressFlow = _progress.asStateFlow()

    private val _phase = MutableStateFlow<YtDlpPhase>(YtDlpPhase.Starting)
    val phaseFlow = _phase.asStateFlow()

    private var downloadedSizeBeforeRetry = 0L
    private var retryJob: Job? = null
    private val retryLock = Mutex()

    override suspend fun actualBoot() {
        // http and hls jobs do the same, every path that touches the destination
        // (runYtDlp, downloadRemoved, onDownloadFinishedBeforeSave) runs after boot.
        initializeDestination()
        val missingBinaries = Binaries.getMissing(binDir)
        if (missingBinaries.isNotEmpty()) {
            throw object : DownloadValidationException(
                Binaries.getMissingMessage(binDir)
            ) {
                override fun isCritical(): Boolean = true
            }
        }
        downloadedSizeBeforeRetry = getDownloadedSize()
    }

    override fun initializeDestination() {
        destination = createDestination(
            folder = downloadItem.folder,
            name = downloadItem.name,
        )
    }

    private fun createDestination(folder: String, name: String): SimpleDownloadDestination {
        return SimpleDownloadDestination(
            file = File(folder, name),
            emptyFileCreator = downloadManager.emptyFileCreator,
            appendExtensionToIncompleteDownloads = false,
            downloadId = id,
        )
    }

    override suspend fun reset() {
        pause()
        setDestination(null)
        downloadItem.contentLength = IDownloadItem.LENGTH_UNKNOWN
        downloadItem.status = DownloadStatus.Added
        downloadItem.startTime = null
        downloadItem.completeTime = null
        downloadedSizeBeforeRetry = 0
        _progress.value = null
        _phase.value = YtDlpPhase.Starting
        saveState()
        downloadManager.onDownloadItemChange(downloadItem)
    }

    private fun setDestination(dest: SimpleDownloadDestination?) {
        destination = dest ?: createDestination(downloadItem.folder, downloadItem.name)
    }

    override suspend fun resume() {
        if (isDownloadActive.value) return
        _isDownloadActive.update { true }
        resumeWithNewScope(
            newActiveScope = createAndInitializeDownloadScope(),
            isInFirstResume = true,
        )
    }

    fun createAndInitializeDownloadScope(): CoroutineScope {
        val newActiveScope = newScopeBasedOn(scope).also { activeDownloadScope = it }
        return newActiveScope
    }

    private suspend fun resumeWithNewScope(
        newActiveScope: CoroutineScope,
        isInFirstResume: Boolean,
    ) {
        newActiveScope.launch {
            boot()
            onDownloadResuming()
            try {
                runYtDlp()
                onDownloadFinished()
            } catch (e: Exception) {
                e.printStackIfNOtUsual()
                val isCancelled = !newActiveScope.isActive || !scope.isActive || ExceptionUtils.isNormalCancellation(e)
                val shouldStop = when {
                    isCancelled -> true
                    e is DownloadValidationException -> e.isCritical()
                    else -> false
                }
                if (shouldStop) {
                    scope.launch { pause(e) }
                } else {
                    downloadFailedRetryOrPause(e = e, isInFirstResume = isInFirstResume)
                }
            }
        }.join()
    }

    private suspend fun runYtDlp() {
        val requestedOutput = getDestination().outputFile
        if (requestedOutput.exists() && requestedOutput.length() > 0) {
            downloadItem.contentLength = requestedOutput.length()
            return
        }
        downloadItem.status = DownloadStatus.Downloading
        if (downloadItem.startTime == null) {
            downloadItem.startTime = System.currentTimeMillis()
        }
        saveState()
        _phase.value = YtDlpPhase.Starting
        // kept local so a retry that goes back through resumeWithNewScope reports itself again
        var reportedResumed = false
        _progress.update {
            YtDlpDownloadProgress(
                downloadedBytes = 0,
                totalBytes = IDownloadItem.LENGTH_UNKNOWN.coerceAtLeast(0),
                speed = 0,
                remainingSeconds = 0,
            )
        }
        val resolvedPathOutput = File(binDir, "resolved-output-$id.txt")
        val resolved = try {
            YtDlpCli.download(
                binaryPath = Binaries.getYtDlpPath(binDir),
                ffmpegDir = Binaries.getFFmpegDir(binDir),
                link = downloadItem.link,
                outputFilePath = requestedOutput.absolutePath,
                formatSelector = downloadItem.selectedFormatId,
                resolvedPathOutput = resolvedPathOutput,
                ytCookies = downloadItem.ytCookies,
                onProgress = { progress ->
                    if (!reportedResumed) {
                        // the first progress line means bytes are actually moving, which is what
                        // the rest of the app uses to tell "resuming" apart from "downloading".
                        reportedResumed = true
                        _phase.value = YtDlpPhase.Downloading
                        onDownloadResumed()
                    }
                    _progress.update { progress }
                    if (progress.totalBytes > 0) {
                        downloadItem.contentLength = progress.totalBytes
                    }
                },
                onLogLine = { logLine -> handleLogLine(logLine, reportedResumed) },
            )
        } finally {
            resolvedPathOutput.delete()
        }
        val finalOutput = applyResolvedOutput(resolved ?: requestedOutput)
        downloadItem.contentLength = finalOutput.length()
    }

    /**
     * yt-dlp decides the container extension based on the selected format, so the real file name
     * is only known after the download ran. the item is updated to match the file that was created.
     */
    /**
     * yt-dlp prefixes most of its output with a stage tag. "[download]" is dropped because the
     * progress bar already covers it, the merge/fixup tags happen after the last byte was written,
     * and every other tag belongs to a site extractor, which all report the resolving stage.
     */
    private fun handleLogLine(logLine: YtDlpLogLine, reportedResumed: Boolean) {
        val message = cleanLogMessage(logLine.message) ?: return
        val tag = logLine.tag?.lowercase()
        val phase = when {
            tag == "download" -> return
            tag in logPostProcessingTags -> YtDlpPhase.PostProcessing(message)
            tag == null -> {
                if (reportedResumed) YtDlpPhase.PostProcessing(message)
                else YtDlpPhase.Resolving(message)
            }
            else -> YtDlpPhase.Resolving(message)
        }
        _phase.value = phase
    }

    private fun cleanLogMessage(message: String): String? {
        var text = message
        val idPrefix = logVideoIdPrefix.matchEntire(text)
        if (idPrefix != null) {
            text = text.substring(idPrefix.value.length)
        }
        text = text.replace(logWhitespace, " ").trim()
        if (text.isEmpty()) return null
        // the line our own --print-to-file writes about itself, it is not a download stage
        if (text.startsWith("Writing '")) return null
        if (logBareProgress.matches(text)) return null
        return text
    }

    private suspend fun applyResolvedOutput(resolvedFile: File): File {
        val newName = resolvedFile.name
        val newFolder = resolvedFile.parentFile?.absolutePath
        val nameChanged = newName != downloadItem.name
        val folderChanged = newFolder != null && newFolder != downloadItem.folder
        if (nameChanged || folderChanged) {
            downloadItem.name = newName
            if (newFolder != null) {
                downloadItem.folder = newFolder
            }
            saveDownloadItem()
            downloadManager.onDownloadItemChange(downloadItem)
        }
        return createDestination(
            folder = downloadItem.folder,
            name = downloadItem.name,
        ).also { destination = it }.outputFile
    }

    override fun getDownloadedSize(): Long {
        return _progress.value?.downloadedBytes ?: 0
    }

    override suspend fun changeConfig(
        updater: (IDownloadItem) -> Unit,
        extraConfig: DownloadJobExtraConfig?,
    ): IDownloadItem {
        boot()
        val previousItem = downloadItem.copy()
        val newItem = previousItem.copy().apply(updater)
        val shouldUpdateDestination = previousItem.folder != newItem.folder ||
                previousItem.name != newItem.name
        if (shouldUpdateDestination && isDownloadActive.value) {
            pause()
        }
        downloadItem.applyFrom(newItem)
        if (shouldUpdateDestination) {
            initializeDestination()
        }
        extraConfig?.let { extraConfigsReceived(it) }
        saveDownloadItem()
        return downloadItem
    }

    private val delayForEachRetry = 3_000L
    var failedDownloadTries = 0
    var _maxAllowedRetries: Int? = null

    fun getMaxAllowedRetries(): Int {
        return _maxAllowedRetries ?: downloadManager.settings.maxDownloadRetryCount
    }

    private fun downloadFailedRetryOrPause(e: Throwable, isInFirstResume: Boolean) {
        scope.launch {
            if (e is DownloadValidationException && e.isCritical()) {
                pause(e)
                return@launch
            }
            val downloadedSize = getDownloadedSize()
            if (downloadedSize > downloadedSizeBeforeRetry) {
                failedDownloadTries = 0
            } else {
                failedDownloadTries++
            }
            downloadedSizeBeforeRetry = downloadedSize
            val retriedCount = (failedDownloadTries - 1).coerceAtLeast(0)
            if (retriedCount < getMaxAllowedRetries()) {
                retry(isInFirstResume)
            } else {
                pause(TooManyErrorException(e))
            }
        }
    }

    fun retry(isInFirstResume: Boolean) {
        scope.launch {
            val newScopeResult = retryLock.tryLocked {
                val job = async {
                    saveState()
                    cancelDownloadScope()
                    _status.update { DownloadJobStatus.Retrying(delayForEachRetry) }
                    delay(delayForEachRetry.milliseconds)
                    createAndInitializeDownloadScope()
                }
                retryJob = job
                job.await()
            }
            newScopeResult.getOrNull()?.let { resumeWithNewScope(it, isInFirstResume) }
        }
    }

    suspend fun cancelDownloadScope() {
        activeDownloadScope?.coroutineContext?.job?.cancelAndJoin()
        activeDownloadScope = null
    }

    suspend fun cancelRetry() {
        retryJob?.cancel()
        retryJob = null
    }

    override suspend fun pause(throwable: Throwable) {
        boot()
        failedDownloadTries = 0
        cancelRetry()
        cancelDownloadScope()
        onDownloadCanceled(throwable)
    }

    override fun onDownloadFinishedBeforeSave() {
        downloadItem.contentLength = getDestination().outputFile.length()
    }

    private var lastSavedDownloadItem: YtDlpDownloadItem? = null

    private suspend fun saveDownloadItem() {
        val copy = downloadItem.copy()
        if (lastSavedDownloadItem != copy) {
            downloadManager.dlListDb.update(downloadItem)
            lastSavedDownloadItem = copy
        }
    }

    override suspend fun saveState() {
        saveDownloadItem()
    }

    override fun reloadSettings() {}

    override suspend fun extraConfigsReceived(config: DownloadJobExtraConfig) {}
}
