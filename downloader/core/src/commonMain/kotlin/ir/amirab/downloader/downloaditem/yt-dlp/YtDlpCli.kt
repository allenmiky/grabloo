package ir.amirab.downloader.downloaditem.yt_dlp

import ir.amirab.util.withoutJPackageEnvVariable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

class YtDlpNotInstalledException(message: String) : IOException(message)

class YtDlpCommandFailedException(message: String) : IOException(message)

data class YtDlpDownloadProgress(
    val downloadedBytes: Long,
    val totalBytes: Long,
    val speed: Long,
    val remainingSeconds: Long,
)

/**
 * a human readable line yt-dlp wrote to its output, for example "[Merger] Merging formats".
 * yt-dlp has no notion of parts, so these lines are the only way the ui can tell the user what the
 * process is currently doing instead of showing an empty part list.
 */
data class YtDlpLogLine(
    val tag: String?,
    val message: String,
)

object YtDlpCli {
    private const val PROGRESS_PREFIX = "GRABBO_PROGRESS"
    private const val INFORMATION_TIMEOUT_MINUTES = 5L

    /**
     * used when the user did not pick a format. "bv*" is bestvideo-with-optional-audio and "ba" is
     * bestaudio, so unlike the very common "-f best" this actually gets the high resolution
     * streams: on YouTube "best" is format 18, which is 360p, and that single word is behind a
     * large share of the "yt-dlp only downloads 360p" reports.
     *
     * the trailing fallbacks exist so an unusual site, or a video that only has a pre-muxed stream,
     * can never end up with "Requested format is not available".
     */
    private const val DEFAULT_FORMAT_SELECTOR =
        "bv*+ba/b"

    private val progressTemplate = listOf(
        "%(progress.downloaded_bytes)s",
        "%(progress.total_bytes)s",
        "%(progress.total_bytes_estimate)s",
        "%(progress.speed)s",
        "%(progress.eta)s",
    ).joinToString(prefix = "$PROGRESS_PREFIX:", separator = "|")

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val baseArgs = listOf(
        "--ignore-config",
        "--no-color",
        "--no-warnings",
        "--no-playlist",
        "--js-runtimes",
        "node",
    )

    /**
     * youtube:player_client is deliberately NOT set. yt-dlp picks the client list itself and keeps
     * it in sync with YouTube, because YouTube rotates its clients and requires a "PO Token" on
     * several of them; the default list is the one whose formats download without a token plugin.
     * Pinning a list here is what limited us to format 18 (360p) only, and it breaks again every
     * few yt-dlp releases. Passing nothing was measured to return all 37 formats of a 4K60 video.
     *
     * skip=hls drops the m3u8 manifests. those are duplicates of the https formats that already
     * exist, so they only made the quality list noisier and the extraction about 20% slower.
     */
    private val youtubeExtractorArgs = listOf(
        "--extractor-args",
        "youtube:skip=hls",
    )

    /**
     * yt-dlp's own default clients return every quality without any cookies, so cookies are only
     * needed for videos that are gated behind a login: age restricted, private, members only or
     * "Sign in to confirm you're not a bot" for a flagged ip. the browser extension sends those
     * cookies along with the download, in Netscape format, because the extension can read them
     * directly while yt-dlp's --cookies-from-browser cannot: on linux the chromium cookie store is
     * encrypted with the os keyring, which the jvm process is not allowed to reach, so that option
     * fails there instead of degrading gracefully.
     *
     * @see YtDlpDownloadCredentials.ytCookies
     */
    private fun getCookieArgs(ytCookies: String? = null): List<String> {
        if (!ytCookies.isNullOrBlank()) {
            return try {
                val tmpFile = File.createTempFile("grabbo_cookies_", ".txt")
                tmpFile.deleteOnExit()
                tmpFile.writeText(ytCookies)
                listOf("--cookies", tmpFile.absolutePath)
            } catch (e: Exception) {
                emptyList()
            }
        }
        val userHome = System.getProperty("user.home")
        if (userHome != null) {
            val candidates = listOf(
                File(userHome, ".config/yt-dlp/cookies.txt"),
                File(userHome, ".config/abdownloadmanager/cookies.txt"),
                File(userHome, ".abdownloadmanager/cookies.txt"),
            )
            val existing = candidates.firstOrNull { it.exists() && it.isFile }
            if (existing != null) {
                return listOf("--cookies", existing.absolutePath)
            }
        }
        return emptyList()
    }

    fun requireBinary(binaryPath: String) {
        val file = File(binaryPath)
        if (!file.exists() || !file.canExecute()) {
            throw YtDlpNotInstalledException("yt-dlp binary is not available at $binaryPath")
        }
    }

    suspend fun fetchVideoInfo(
        binaryPath: String,
        link: String,
        formatSelector: String? = null,
        ytCookies: String? = null,
    ): YtDlpVideoInfo {
        requireBinary(binaryPath)
        val root = runToJson(
            binaryPath = binaryPath,
            args = baseArgs + youtubeExtractorArgs + getCookieArgs(ytCookies) + buildList {
                add("--dump-single-json")
                addFormatSelector(formatSelector)
                add(link)
            }
        )
        val sizes = root["requested_formats"].asArray().mapNotNull {
            it.numberOrNull("filesize") ?: it.numberOrNull("filesize_approx")
        }
        val formats = root["formats"].asArray().mapNotNull { it.asFormat() }
        val requestedFormats = root["requested_formats"].asArray().mapNotNull { it.asFormat() }
        val rootFormat = root.asFormat()
        val directUrl = root.textOrNull("url")
        val directHeaders = root.headersOrEmpty("http_headers")
        val finalRequestedFormats = when {
            requestedFormats.isNotEmpty() -> requestedFormats
            rootFormat != null && rootFormat.url != null -> listOf(rootFormat)
            else -> emptyList()
        }
        return YtDlpVideoInfo(
            title = root.textOrNull("title").orEmpty().ifBlank { link },
            duration = root.numberOrNull("duration")?.toDouble(),
            // requested_formats is only filled for the formats that will really be downloaded,
            // summing "formats" would add up every quality that yt-dlp knows about.
            fileSize = sizes.takeIf { it.isNotEmpty() }?.sum()
                ?: root.numberOrNull("filesize")
                ?: root.numberOrNull("filesize_approx"),
            thumbnailUrl = root.textOrNull("thumbnail"),
            isLive = root["is_live"]?.jsonPrimitive?.booleanOrNull ?: false,
            formats = formats,
            requestedFormats = finalRequestedFormats,
            directUrl = directUrl,
            httpHeaders = directHeaders,
        )
    }

    suspend fun fetchFormats(
        binaryPath: String,
        link: String,
        ytCookies: String? = null,
    ): List<YtDlpFormat> {
        requireBinary(binaryPath)
        val root = runToJson(
            binaryPath = binaryPath,
            args = baseArgs + youtubeExtractorArgs + getCookieArgs(ytCookies) + listOf("--dump-single-json", link)
        )
        return root["formats"]
            .asArray()
            .mapNotNull { it.asFormat() }
            .sortedWith(
                compareByDescending<YtDlpFormat> { it.hasVideo }
                    .thenByDescending { it.hasAudio }
                    .thenByDescending { it.height }
                    .thenByDescending { it.frameRate ?: 0.0 }
            )
    }

    suspend fun download(
        binaryPath: String,
        ffmpegDir: String,
        link: String,
        outputFilePath: String,
        formatSelector: String? = null,
        resolvedPathOutput: File? = null,
        ytCookies: String? = null,
        onProgress: (YtDlpDownloadProgress) -> Unit,
        onLogLine: (YtDlpLogLine) -> Unit = {},
    ): File? {
        requireBinary(binaryPath)
        return withContext(Dispatchers.IO) {
            val command = buildList {
                add(binaryPath)
                addAll(baseArgs)
                addAll(youtubeExtractorArgs)
                addAll(getCookieArgs(ytCookies))
                add("--newline")
                add("--ffmpeg-location")
                add(ffmpegDir)
                add("--progress-template")
                add(progressTemplate)
                addFormatSelector(formatSelector)
                add("--merge-output-format")
                add("mp4")
                add("-o")
                add(outputFilePath)
                if (resolvedPathOutput != null) {
                    // the real file name is only known once yt-dlp picked a container, and asking
                    // for it in a separate dry run means extracting the page a second time.
                    // --print-to-file keeps it off stdout so it cannot mix into the progress lines.
                    add("--print-to-file")
                    add("after_move:filepath")
                    add(resolvedPathOutput.absolutePath)
                }
                add(link)
            }
            val process = ProcessBuilder(*command.toTypedArray())
                .withoutJPackageEnvVariable()
                .redirectErrorStream(true)
                .start()
            val killOnCancel = currentCoroutineContext().job.invokeOnCompletion {
                process.destroyForcibly()
            }
            try {
                // yt-dlp streams everything on stdout, keep just enough of the tail to be able to
                // report why it failed once it exits.
                val recentOutput = ArrayDeque<String>()
                fun recordLine(text: String) {
                    val trimmed = text.trim()
                    if (trimmed.isEmpty()) return
                    recentOutput.addLast(trimmed)
                    while (recentOutput.size > 20) {
                        recentOutput.removeFirst()
                    }
                }
                process.inputStream.bufferedReader().use { reader ->
                    val line = StringBuilder()
                    while (true) {
                        val read = reader.read()
                        if (read == -1) break
                        if (read == '\n'.code) {
                            val text = line.toString()
                            recordLine(text)
                            handleLine(text, onProgress, onLogLine)
                            line.setLength(0)
                        } else {
                            line.append(read.toChar())
                        }
                    }
                    if (line.isNotEmpty()) {
                        recordLine(line.toString())
                        handleLine(line.toString(), onProgress, onLogLine)
                    }
                }
                if (!currentCoroutineContext().isActive) {
                    throw CancellationException("Download cancelled")
                }
                if (!process.waitFor(1, TimeUnit.HOURS)) {
                    process.destroyForcibly()
                    if (!currentCoroutineContext().isActive) {
                        throw CancellationException("Download cancelled")
                    }
                    throw YtDlpCommandFailedException("yt-dlp did not finish in time")
                }
                if (!currentCoroutineContext().isActive) {
                    throw CancellationException("Download cancelled")
                }
                val exitCode = process.exitValue()
                if (exitCode != 0) {
                    if (!currentCoroutineContext().isActive) {
                        throw CancellationException("Download cancelled")
                    }
                    throw YtDlpCommandFailedException(describeFailure(recentOutput.toList(), exitCode))
                }
            } finally {
                killOnCancel.dispose()
                if (process.isAlive) {
                    process.destroyForcibly()
                }
            }
            if (resolvedPathOutput == null) {
                return@withContext null
            }
            readResolvedPath(resolvedPathOutput)
        }
    }

    private fun readResolvedPath(output: File): File? {
        return runCatching {
            output.readText()
                .lineSequence()
                .map { it.trim() }
                .lastOrNull { it.isNotEmpty() && it != "NA" }
                ?.let(::File)
                ?.takeIf { it.exists() }
        }.getOrNull()
    }

    private fun MutableList<String>.addFormatSelector(formatSelector: String?) {
        add("-f")
        add(formatSelector?.takeIf { it.isNotBlank() } ?: DEFAULT_FORMAT_SELECTOR)
    }

    /**
     * yt-dlp mixes its human readable status ("[download] Destination: x.webm", "[Merger] Merging
     * formats") with the machine readable progress lines. progress lines go to [onProgress],
     * everything else is reported through [onLogLine] so the caller can surface the current phase.
     */
    private fun handleLine(
        line: String,
        onProgress: (YtDlpDownloadProgress) -> Unit,
        onLogLine: (YtDlpLogLine) -> Unit,
    ) {
        if (line.startsWith(PROGRESS_PREFIX)) {
            parseProgressLine(line, onProgress)
            return
        }
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return
        val tagEnd = trimmed.indexOf(']')
        if (trimmed.startsWith('[') && tagEnd > 0) {
            onLogLine(
                YtDlpLogLine(
                    tag = trimmed.substring(1, tagEnd),
                    message = trimmed.substring(tagEnd + 1).trimStart(),
                )
            )
        } else {
            onLogLine(YtDlpLogLine(tag = null, message = trimmed))
        }
    }

    private fun parseProgressLine(
        line: String,
        onProgress: (YtDlpDownloadProgress) -> Unit,
    ) {
        if (!line.startsWith(PROGRESS_PREFIX)) return
        val parts = line.removePrefix("$PROGRESS_PREFIX:").split('|')
        if (parts.size < 5) return
        onProgress(
            YtDlpDownloadProgress(
                downloadedBytes = parts[0].toLongOrNull() ?: 0L,
                totalBytes = parts[1].toLongOrNull() ?: parts[2].toLongOrNull() ?: 0L,
                // yt-dlp reports the speed as a float (e.g. 147264.43668781072),
                // toLongOrNull would drop it and the ui would always show 0.
                speed = parts[3].toDoubleOrNull()?.toLong() ?: 0L,
                remainingSeconds = parts[4].toLongOrNull() ?: 0L,
            )
        )
    }

    private suspend fun runToJson(
        binaryPath: String,
        args: List<String>,
    ): JsonObject {
        val output = runToText(binaryPath, args)
        parseJsonObject(output)?.let { return it }
        // yt-dlp can surround the document with noise: a js-runtime warning when no runtime is on
        // PATH, or a stray "null" line after the payload. Parsing only the object itself keeps
        // such lines from failing an otherwise successful extraction.
        parseJsonObject(extractJsonObject(output))?.let { return it }
        val errorLine = output.lineSequence()
            .map { it.trim() }
            .lastOrNull { it.startsWith("ERROR:", ignoreCase = true) }
        throw YtDlpCommandFailedException(
            errorLine?.take(300) ?: "yt-dlp returned an unexpected output",
        )
    }

    private fun parseJsonObject(text: String): JsonObject? =
        runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull()

    private fun extractJsonObject(text: String): String {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start < 0 || end <= start) return text
        return text.substring(start, end + 1)
    }

    private suspend fun runToText(
        binaryPath: String,
        args: List<String>,
    ): String = withContext(Dispatchers.IO) {
        val process = ProcessBuilder(binaryPath, *args.toTypedArray())
            .withoutJPackageEnvVariable()
            .redirectErrorStream(true)
            .start()
        // reading the stream on this thread would block until EOF, so a hung yt-dlp would spin
        // forever and the timeout below would never fire; drain it on its own thread instead
        val output = StringBuilder()
        val collector = Thread {
            process.inputStream.bufferedReader().use { reader ->
                reader.forEachLine { line ->
                    synchronized(output) { output.appendLine(line) }
                }
            }
        }.apply {
            isDaemon = true
            start()
        }
        if (!process.waitFor(INFORMATION_TIMEOUT_MINUTES, TimeUnit.MINUTES)) {
            process.destroyForcibly()
            throw YtDlpCommandFailedException("yt-dlp did not answer in time for ${args.lastOrNull()}")
        }
        if (process.exitValue() != 0) {
            val lines = synchronized(output) { output.toString() }
                .lineSequence().map { it.trim() }.toList()
            throw YtDlpCommandFailedException(
                describeFailure(lines, process.exitValue()),
            )
        }
        // the process has exited, so the stream is at EOF and the collector is about to finish
        collector.join()
        synchronized(output) { output.toString() }
    }

    /**
     * yt-dlp reports failures on stderr, but the json modes also print stray lines such as a bare
     * "null" after the real message. Taking the last non blank line therefore hides the actual
     * reason behind useless output, so prefer the "ERROR:" line it always emits on failure.
     */
    private fun describeFailure(lines: List<String>, exitCode: Int): String {
        val meaningful = lines.filter { it.isNotEmpty() }
        val error = meaningful.lastOrNull { it.startsWith("ERROR:", ignoreCase = true) }
        val detail = error
            ?: meaningful.lastOrNull { it != "null" && it != "NA" }
            ?: meaningful.lastOrNull()
        return detail?.take(300) ?: "yt-dlp exited with code $exitCode"
    }

    private fun JsonElement?.asArray(): List<JsonElement> = (this as? JsonArray).orEmpty()

    private fun JsonElement?.asFormat(): YtDlpFormat? {
        val obj = this as? JsonObject ?: return null
        val formatId = obj.textOrNull("format_id") ?: return null
        return YtDlpFormat(
            formatId = formatId,
            extension = obj.textOrNull("ext").orEmpty(),
            resolution = obj.textOrNull("resolution"),
            frameRate = obj.numberOrNull("fps")?.toDouble(),
            fileSize = obj.numberOrNull("filesize") ?: obj.numberOrNull("filesize_approx"),
            videoCodec = obj.textOrNull("vcodec"),
            audioCodec = obj.textOrNull("acodec"),
            note = obj.textOrNull("format_note"),
            explicitHeight = obj.numberOrNull("height")?.toInt(),
            explicitWidth = obj.numberOrNull("width")?.toInt(),
            protocol = obj.textOrNull("protocol"),
            bitrate = obj.numberOrNull("tbr"),
            dynamicRange = obj.textOrNull("dynamic_range"),
            isDrm = obj["has_drm"]?.jsonPrimitive?.booleanOrNull ?: false,
            url = obj.textOrNull("url"),
            httpHeaders = obj.headersOrEmpty("http_headers"),
        )
    }

    private fun JsonObject.headersOrEmpty(key: String): Map<String, String> {
        val headersObj = this[key] as? JsonObject ?: return emptyMap()
        return headersObj.entries.associate { (k, v) ->
            k to ((v as? JsonPrimitive)?.content.orEmpty())
        }.filterValues { it.isNotBlank() }
    }

    private fun JsonObject.textOrNull(key: String): String? =
        (this[key] as? JsonPrimitive)
            ?.takeIf { it !is JsonNull }
            ?.content
            ?.takeIf { it.isNotBlank() && it != "NA" }

    private fun JsonObject.numberOrNull(key: String): Long? {
        val primitive = this[key] as? JsonPrimitive ?: return null
        return primitive.longOrNull ?: primitive.doubleOrNull?.toLong()
    }

    private fun JsonElement?.numberOrNull(key: String): Long? = (this as? JsonObject)?.numberOrNull(key)
}
