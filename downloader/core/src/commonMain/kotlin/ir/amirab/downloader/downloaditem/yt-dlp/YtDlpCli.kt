package ir.amirab.downloader.downloaditem.yt_dlp

import ir.amirab.util.withoutJPackageEnvVariable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
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

object YtDlpCli {
    private const val PROGRESS_PREFIX = "GRABBO_PROGRESS"
    private const val INFORMATION_TIMEOUT_MINUTES = 5L

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
    )

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
    ): YtDlpVideoInfo {
        requireBinary(binaryPath)
        val root = runToJson(
            binaryPath = binaryPath,
            args = baseArgs + buildList {
                add("--dump-single-json")
                addFormatSelector(formatSelector)
                add(link)
            }
        )
        val sizes = root["requested_formats"].asArray().mapNotNull {
            it.numberOrNull("filesize") ?: it.numberOrNull("filesize_approx")
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
        )
    }

    suspend fun fetchFormats(
        binaryPath: String,
        link: String,
    ): List<YtDlpFormat> {
        requireBinary(binaryPath)
        val root = runToJson(
            binaryPath = binaryPath,
            args = baseArgs + listOf("--dump-single-json", link)
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

    /**
     * asks yt-dlp where the file would end up without downloading anything.
     * the output template has to be the real destination, otherwise yt-dlp resolves the path
     * inside its own working directory and ignores the folder the user picked.
     */
    suspend fun resolveOutputFile(
        binaryPath: String,
        ffmpegDir: String,
        link: String,
        outputFilePath: String,
        formatSelector: String? = null,
    ): String {
        requireBinary(binaryPath)
        val output = runToText(
            binaryPath = binaryPath,
            args = baseArgs + buildList {
                add("--ffmpeg-location")
                add(ffmpegDir)
                add("--print")
                add("after_move:filepath")
                addFormatSelector(formatSelector)
                add("-o")
                add(outputFilePath)
                add(link)
            }
        )
        return output
            .lineSequence()
            .map { it.trim() }
            .lastOrNull { it.isNotEmpty() && it != "NA" }
            ?: throw YtDlpCommandFailedException("yt-dlp did not report an output file for $link")
    }

    suspend fun download(
        binaryPath: String,
        ffmpegDir: String,
        link: String,
        outputFilePath: String,
        formatSelector: String? = null,
        onProgress: (YtDlpDownloadProgress) -> Unit,
    ) {
        requireBinary(binaryPath)
        withContext(Dispatchers.IO) {
            val command = buildList {
                add(binaryPath)
                addAll(baseArgs)
                add("--newline")
                add("--ffmpeg-location")
                add(ffmpegDir)
                add("--progress-template")
                add(progressTemplate)
                addFormatSelector(formatSelector)
                add("-o")
                add(outputFilePath)
                add(link)
            }
            val process = ProcessBuilder(*command.toTypedArray())
                .withoutJPackageEnvVariable()
                .redirectErrorStream(true)
                .start()
            val killOnCancel = currentCoroutineContext().job.invokeOnCompletion {
                process.destroy()
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
                            parseProgressLine(text, onProgress)
                            line.setLength(0)
                        } else {
                            line.append(read.toChar())
                        }
                    }
                    if (line.isNotEmpty()) {
                        recordLine(line.toString())
                        parseProgressLine(line.toString(), onProgress)
                    }
                }
                if (!process.waitFor(1, TimeUnit.HOURS)) {
                    process.destroy()
                    throw YtDlpCommandFailedException("yt-dlp did not finish in time")
                }
                val exitCode = process.exitValue()
                if (exitCode != 0) {
                    throw YtDlpCommandFailedException(describeFailure(recentOutput.toList(), exitCode))
                }
            } finally {
                killOnCancel.dispose()
                process.destroy()
            }
        }
    }

    private fun MutableList<String>.addFormatSelector(formatSelector: String?) {
        formatSelector?.takeIf { it.isNotBlank() }?.let {
            add("-f")
            add(it)
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
        return runCatching { json.parseToJsonElement(output).jsonObject }
            .getOrElse {
                throw YtDlpCommandFailedException("yt-dlp returned an unexpected output: $it")
            }
    }

    private suspend fun runToText(
        binaryPath: String,
        args: List<String>,
    ): String = withContext(Dispatchers.IO) {
        val process = ProcessBuilder(binaryPath, *args.toTypedArray())
            .withoutJPackageEnvVariable()
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        if (!process.waitFor(INFORMATION_TIMEOUT_MINUTES, TimeUnit.MINUTES)) {
            process.destroyForcibly()
            throw YtDlpCommandFailedException("yt-dlp did not answer in time for ${args.lastOrNull()}")
        }
        if (process.exitValue() != 0) {
            throw YtDlpCommandFailedException(
                describeFailure(
                    output.lineSequence().map { it.trim() }.toList(),
                    process.exitValue(),
                )
            )
        }
        output
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
        )
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
