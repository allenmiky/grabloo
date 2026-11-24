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
import ir.amirab.downloader.connection.HttpDownloaderClient
import ir.amirab.downloader.downloaditem.http.HttpDownloadItem
import ir.amirab.downloader.downloaditem.http.HttpDownloadJob
import ir.amirab.downloader.part.RangedPart
import ir.amirab.downloader.part.RangedParts
import ir.amirab.util.platform.Platform
import ir.amirab.util.withoutJPackageEnvVariable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
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
    private val httpClient: HttpDownloaderClient? = null,
) : DownloadJob(
    downloadManager = downloadManager,
) {
    private val binDir: File = Binaries.getBinDir(downloadManager.downloadDataFolder)

    @Volatile
    private var destination: SimpleDownloadDestination? = null

    private val _parts = MutableStateFlow<List<RangedPart>>(emptyList())
    val partsFlow = _parts.asStateFlow()
    fun getParts(): List<RangedPart> = _parts.value

    @Volatile
    private var activeHttpJob: HttpDownloadJob? = null

    @Volatile
    private var cachedVideoInfo: YtDlpVideoInfo? = null

    @Volatile
    private var cachedVideoInfoTime: Long = 0L

    @Volatile
    private var completedVideoBytes: Long = 0L

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

    private val _extractingProgress = MutableStateFlow(0)
    val extractingProgressFlow = _extractingProgress.asStateFlow()

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
        restoreSavedPartsIfAny()
        downloadedSizeBeforeRetry = getDownloadedSize()
    }

    private suspend fun restoreSavedPartsIfAny() {
        if (_parts.value.isNotEmpty()) return
        val vParts = (downloadManager.partListDb.getParts(-id - 1000L) as? RangedParts)?.list
        val aParts = (downloadManager.partListDb.getParts(-id - 2000L) as? RangedParts)?.list
        val parts = when {
            vParts != null && vParts.isNotEmpty() -> vParts
            aParts != null && aParts.isNotEmpty() -> aParts
            else -> emptyList()
        }
        if (parts.isNotEmpty()) {
            _parts.value = parts
        }
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
        downloadManager.partListDb.removeParts(-id - 1000L)
        downloadManager.partListDb.removeParts(-id - 2000L)
        cleanupTempFiles()
        _parts.value = emptyList()
        completedVideoBytes = 0L
        cachedVideoInfo = null
        cachedVideoInfoTime = 0L
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

    private fun cleanupTempFiles() {
        val folders = setOfNotNull(
            File(downloadItem.folder),
            destination?.outputFile?.parentFile,
        )
        for (folder in folders) {
            runCatching {
                folder.listFiles { _, name ->
                    name.startsWith(".temp_v_${id}_") || name.startsWith(".temp_a_${id}_")
                }?.forEach { it.delete() }
            }
        }
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
        if (downloadItem.status == DownloadStatus.Completed && requestedOutput.exists() && requestedOutput.length() > 0) {
            return
        }
        downloadItem.status = DownloadStatus.Downloading
        if (downloadItem.startTime == null) {
            downloadItem.startTime = System.currentTimeMillis()
        }
        saveState()
        _phase.value = YtDlpPhase.Starting

        val directSuccess = try {
            tryDirectStreamDownload(requestedOutput)
        } catch (e: Exception) {
            if (!currentCoroutineContext().isActive || ExceptionUtils.isNormalCancellation(e)) {
                throw e
            }
            e.printStackIfNOtUsual()
            false
        }

        if (directSuccess) {
            return
        }

        _parts.value = emptyList()
        runYtDlpCli(requestedOutput)
    }

    private suspend fun getVideoInfo(forceRefresh: Boolean = false): YtDlpVideoInfo? {
        val cached = cachedVideoInfo
        val now = System.currentTimeMillis()
        // YouTube CDN URLs expire after ~6 hours. Reuse cached info within 3 hours
        // so pause/resume is instantaneous without triggering extraction delays.
        if (!forceRefresh && cached != null && (now - cachedVideoInfoTime) < 3 * 3600 * 1000L) {
            return cached
        }
        _phase.value = YtDlpPhase.Resolving(
            if (cached != null) "Refreshing stream info..." else "Extracting stream info..."
        )
        _extractingProgress.value = 12
        val progressTicker = (activeDownloadScope ?: scope).launch {
            val steps = listOf(
                25 to 250L,
                42 to 350L,
                60 to 450L,
                75 to 550L,
                85 to 700L,
                92 to 1000L,
                96 to 1500L,
            )
            for ((target, delayMs) in steps) {
                delay(delayMs.milliseconds)
                _extractingProgress.update { maxOf(it, target) }
            }
        }
        val fresh = try {
            runCatching {
                YtDlpCli.fetchVideoInfo(
                    binaryPath = Binaries.getYtDlpPath(binDir),
                    link = downloadItem.link,
                    formatSelector = downloadItem.selectedFormatId,
                    ytCookies = downloadItem.ytCookies,
                )
            }.getOrNull()
        } finally {
            progressTicker.cancel()
            _extractingProgress.value = 100
        }
        if (fresh != null) {
            cachedVideoInfo = fresh
            cachedVideoInfoTime = now
        }
        return fresh
    }

    private suspend fun tryDirectStreamDownload(requestedOutput: File): Boolean {
        if (httpClient == null) return false
        val info = getVideoInfo(forceRefresh = false) ?: return false

        if (info.isLive || !info.hasDirectMediaStreams) {
            return false
        }

        val vStream = info.videoStream
        val aStream = info.audioStream

        // Guard against the extractor handing us something that is not a real video stream:
        // a storyboard/thumbnail format has no video codec, and a suspiciously tiny "video"
        // is almost always a preview image. Falling back to the CLI is the safe option.
        if (vStream != null && (!vStream.hasVideo || (vStream.fileSize ?: 0L) in 1..<100_000L)) {
            return false
        }

        val totalExpectedLength = (vStream?.fileSize ?: 0L) + (aStream?.fileSize ?: 0L)
        if (totalExpectedLength > 0) {
            downloadItem.contentLength = totalExpectedLength
            saveState()
        }

        // Ensure the destination file name has a proper media extension.
        // If the download was added without an extension (e.g. YouTube video title only),
        // determine the right container extension and apply it so the OS, media players,
        // and FFmpeg muxer all recognize the file.
        val targetExt = determineTargetExtension(vStream, aStream)
        val currentExt = requestedOutput.extension.lowercase()
        val finalOutput = if (currentExt in knownMediaExtensions) {
            requestedOutput
        } else {
            val newFile = File(
                requestedOutput.parentFile ?: File(downloadItem.folder),
                "${requestedOutput.name}.$targetExt"
            )
            applyResolvedOutput(newFile)
        }

        if (vStream != null && aStream != null && vStream != aStream) {
            // Objective 2: FFmpeg pre-flight check before starting dual-stream download.
            // If FFmpeg is unavailable we fallback to yt-dlp CLI immediately rather than
            // downloading both streams and only then finding out merging will fail.
            if (!isFFmpegAvailable()) {
                return false
            }
            downloadDualStreamsAndMerge(vStream, aStream, finalOutput)
            return true
        }

        val singleStream = vStream ?: aStream ?: return false
        downloadSingleStream(singleStream, finalOutput)
        return true
    }

    private val knownMediaExtensions = setOf(
        "mp4", "mkv", "webm", "avi", "mov", "flv", "m4v", "ts", "3gp",
        "m4a", "mp3", "opus", "aac", "ogg", "wav", "flac"
    )

    private fun determineTargetExtension(vStream: YtDlpFormat?, aStream: YtDlpFormat?): String {
        val vExt = vStream?.extension?.lowercase()
        val aExt = aStream?.extension?.lowercase()
        return when {
            // Dual stream: video mp4 + audio m4a/aac -> mp4
            vExt == "mp4" && (aExt == null || aExt == "m4a" || aExt == "mp4" || aExt == "aac") -> "mp4"
            // Both webm -> webm
            vExt == "webm" && (aExt == null || aExt == "webm" || aExt == "opus") -> "webm"
            // Mixed or VP9/Opus in unknown containers -> mkv is the safest universal container
            vExt != null && aExt != null && vExt != aExt -> "mkv"
            vExt != null -> vExt
            aExt != null -> aExt
            else -> "mp4"
        }
    }

    /**
     * Returns true when a usable FFmpeg executable can be found.
     * Checks the bundled/system marker path first, then falls back to PATH lookup.
     */
    private fun isFFmpegAvailable(): Boolean {
        val ffmpegDir = Binaries.getFFmpegDir(binDir)
        val ext = if (Platform.getCurrentPlatform() is Platform.Desktop.Windows) ".exe" else ""
        val inFfmpegDir = File(ffmpegDir, "ffmpeg$ext")
        if (inFfmpegDir.exists() && inFfmpegDir.canExecute()) return true
        val defaultPath = Binaries.getFFmpegPath(binDir)
        if (File(defaultPath).exists() && File(defaultPath).canExecute()) return true
        // Last resort: try the system PATH
        return runCatching {
            val p = ProcessBuilder("ffmpeg", "-version")
                .withoutJPackageEnvVariable()
                .redirectErrorStream(true)
                .start()
            p.waitFor() == 0
        }.getOrElse { false }
    }

    private suspend fun downloadSingleStream(
        stream: YtDlpFormat,
        requestedOutput: File,
    ) {
        _phase.value = YtDlpPhase.Downloading
        onDownloadResumed()
        downloadStreamWithHttp(
            format = stream,
            outputFile = requestedOutput,
            subId = -id - 1000L,
        )
        if (requestedOutput.exists()) {
            downloadItem.contentLength = requestedOutput.length()
        }
    }

    private suspend fun downloadDualStreamsAndMerge(
        vStream: YtDlpFormat,
        aStream: YtDlpFormat,
        requestedOutput: File,
    ) {
        val folder = requestedOutput.parentFile ?: File(downloadItem.folder)
        val tempVideoFile = File(folder, ".temp_v_${id}_${vStream.formatId}.${vStream.extension}")
        val tempAudioFile = File(folder, ".temp_a_${id}_${aStream.formatId}.${aStream.extension}")
        val tempVideoDoneFile = File(folder, ".temp_v_${id}_${vStream.formatId}.done")
        val tempAudioDoneFile = File(folder, ".temp_a_${id}_${aStream.formatId}.done")

        try {
            // 1. Download video stream.
            // A stream is ONLY complete when its .done marker file exists.
            // We cannot check tempVideoFile.length() == fileSize because EmptyFileCreator
            // pre-allocates sparse files to the nominal length at download start.
            val videoComplete = tempVideoFile.exists() && tempVideoDoneFile.exists()
            if (videoComplete) {
                completedVideoBytes = vStream.fileSize ?: tempVideoFile.length()
            } else {
                _phase.value = YtDlpPhase.Downloading
                onDownloadResumed()
                downloadStreamWithHttp(
                    format = vStream,
                    outputFile = tempVideoFile,
                    subId = -id - 1000L,
                )
                tempVideoDoneFile.createNewFile()
                completedVideoBytes = vStream.fileSize ?: tempVideoFile.length()
            }

            // 2. Download audio stream.
            val audioComplete = tempAudioFile.exists() && tempAudioDoneFile.exists()
            if (!audioComplete) {
                _phase.value = YtDlpPhase.Downloading
                onDownloadResumed()
                downloadStreamWithHttp(
                    format = aStream,
                    outputFile = tempAudioFile,
                    subId = -id - 2000L,
                )
                tempAudioDoneFile.createNewFile()
            }

            // 3. Merge with FFmpeg — but only if both files actually contain data.
            // An empty temp file means the sub-download reported success without writing bytes
            // (sparse file / premature .done); muxing it would produce an audio-only result.
            val vSize = tempVideoFile.length()
            val aSize = tempAudioFile.length()
            if (vSize <= 0L || aSize <= 0L) {
                throw IOException(
                    "Stream file is empty before merge: video=$vSize bytes audio=$aSize bytes"
                )
            }
            _parts.value = emptyList()
            _phase.value = YtDlpPhase.PostProcessing("Merging video and audio...")
            mergeStreamsWithFFmpeg(tempVideoFile, tempAudioFile, requestedOutput)

            if (requestedOutput.exists()) {
                downloadItem.contentLength = requestedOutput.length()
            }
        } finally {
            // Objective 3: Always clean temp files once the merged output is confirmed good.
            // If merging failed, keep the temp files so the next retry can resume from them.
            if (requestedOutput.exists() && requestedOutput.length() > 0) {
                tempVideoFile.delete()
                tempAudioFile.delete()
                tempVideoDoneFile.delete()
                tempAudioDoneFile.delete()
            }
        }
    }

    private suspend fun mergeStreamsWithFFmpeg(
        videoFile: File,
        audioFile: File,
        outputFile: File,
    ) = withContext(Dispatchers.IO) {
        val ffmpegPath = resolveFFmpegExecutable(binDir)
        if (outputFile.exists()) {
            outputFile.delete()
        }
        val muxer = when (outputFile.extension.lowercase()) {
            "mp4", "m4v" -> "mp4"
            "webm" -> "webm"
            "mkv" -> "matroska"
            "m4a" -> "ipod"
            "ogg" -> "ogg"
            else -> "mp4"
        }
        val process = ProcessBuilder(
            ffmpegPath,
            "-y",
            "-i", videoFile.absolutePath,
            "-i", audioFile.absolutePath,
            "-c", "copy",
            "-f", muxer,
            outputFile.absolutePath,
        )
            .withoutJPackageEnvVariable()
            .redirectErrorStream(true)
            .start()

        val output = process.inputStream.bufferedReader().use { it.readText() }
        val exitCode = process.waitFor()
        if (exitCode != 0) {
            throw IOException("FFmpeg merge failed with code $exitCode: ${output.takeLast(300)}")
        }
    }

    private fun resolveFFmpegExecutable(binDir: File): String {
        val ffmpegDir = Binaries.getFFmpegDir(binDir)
        val ext = if (Platform.getCurrentPlatform() is Platform.Desktop.Windows) ".exe" else ""
        val inFfmpegDir = File(ffmpegDir, "ffmpeg$ext")
        if (inFfmpegDir.exists() && inFfmpegDir.canExecute()) {
            return inFfmpegDir.absolutePath
        }
        val defaultPath = Binaries.getFFmpegPath(binDir)
        if (File(defaultPath).exists()) {
            return defaultPath
        }
        return "ffmpeg"
    }

    private suspend fun downloadStreamWithHttp(
        format: YtDlpFormat,
        outputFile: File,
        subId: Long,
    ) {
        val client = httpClient ?: throw IllegalStateException("HTTP client not available")
        val streamUrl = format.url ?: throw IllegalStateException("Format URL is null")

        val streamItem = HttpDownloadItem(
            id = subId,
            folder = outputFile.parentFile?.absolutePath ?: downloadItem.folder,
            name = outputFile.name,
            link = streamUrl,
            headers = format.httpHeaders + (downloadItem.headers ?: emptyMap()),
            preferredConnectionCount = downloadItem.preferredConnectionCount,
            speedLimit = downloadItem.speedLimit,
        )

        val job = HttpDownloadJob(
            downloadItem = streamItem,
            downloadManager = downloadManager,
            client = client,
            notifyDownloadManager = false,
        )
        activeHttpJob = job

        try {
            // boot() loads the saved part progress from partListDb (range chunks on disk).
            // After booting, we update the link to the freshly-fetched CDN URL so that
            // any already-completed chunks are kept but the next HTTP range request goes
            // to a live (non-expired) URL.  This is the core of URL-expiry resume support.
            job.boot()
            job.downloadItem.link = streamUrl
            job.downloadItem.headers = format.httpHeaders + (downloadItem.headers ?: emptyMap())

            coroutineScope {
                val partsSync = launch {
                    while (isActive) {
                        val currentParts = job.getParts()
                        if (currentParts.isNotEmpty()) {
                            _parts.value = currentParts
                        }
                        delay(100.milliseconds)
                    }
                }
                try {
                    // HttpDownloadJob.resume() returns as soon as the part downloaders are
                    // dispatched — it does NOT wait for the bytes to arrive. Waiting here for
                    // a terminal state is what lets the caller proceed only once the stream
                    // is really Finished (or Canceled), instead of marking a .done marker on
                    // a download that has only just started.
                    job.resume()
                    job.status.first {
                        it is DownloadJobStatus.Finished ||
                            it is DownloadJobStatus.Canceled
                    }
                } finally {
                    partsSync.cancel()
                    val currentParts = job.getParts()
                    if (currentParts.isNotEmpty()) {
                        _parts.value = currentParts
                    }
                }
            }

            val finalStatus = job.status.value
            if (finalStatus is DownloadJobStatus.Canceled) {
                throw finalStatus.e
            }
            if (finalStatus !is DownloadJobStatus.Finished) {
                throw IOException("Stream download did not complete: $finalStatus")
            }
        } finally {
            activeHttpJob = null
            if (job.status.value is DownloadJobStatus.Finished) {
                downloadManager.partListDb.removeParts(subId)
            }
        }
    }

    private suspend fun runYtDlpCli(requestedOutput: File) {
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
        val parts = _parts.value
        var downloaded = if (parts.isNotEmpty()) {
            parts.sumOf { it.howMuchProceed() }
        } else {
            _progress.value?.downloadedBytes ?: 0L
        }
        downloaded += completedVideoBytes
        return downloaded
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
        activeHttpJob?.let { subJob ->
            scope.launch {
                runCatching {
                    subJob.changeConfig(
                        updater = { subItem ->
                            subItem.speedLimit = newItem.speedLimit
                            subItem.preferredConnectionCount = newItem.preferredConnectionCount
                        },
                        extraConfig = extraConfig,
                    )
                }
            }
        }
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
        activeHttpJob?.pause(throwable)
        cancelDownloadScope()
        onDownloadCanceled(throwable)
    }

    override fun downloadRemoved(
        removeOutputFile: Boolean,
    ) {
        super.downloadRemoved(removeOutputFile)
        // Objective 3: Use runBlocking so partListDb cleanup is guaranteed to run
        // synchronously even if `scope` is cancelled (e.g. app shutdown during removal).
        runCatching {
            kotlinx.coroutines.runBlocking {
                downloadManager.partListDb.removeParts(-id - 1000L)
                downloadManager.partListDb.removeParts(-id - 2000L)
            }
        }
        cleanupTempFiles()
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

    override fun reloadSettings() {
        activeHttpJob?.reloadSettings()
    }

    override suspend fun extraConfigsReceived(config: DownloadJobExtraConfig) {}
}
