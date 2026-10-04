package ir.amirab.util.binarymanager

import ir.amirab.util.logger.thisLogger
import ir.amirab.util.platform.Arch
import ir.amirab.util.platform.Platform
import ir.amirab.util.platform.Platform.Desktop
import ir.amirab.util.withoutJPackageEnvVariable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import co.touchlab.kermit.Severity
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.zip.ZipFile

data class BinaryInfo(
    val name: String,
    val version: String,
    val downloadUrl: String,
    val expectedSize: Long = -1,
    val sha256: String = "",
    val archiveFormat: ArchiveFormat = ArchiveFormat.None,
    val targetFileName: String = "",
)

data class BinaryDownloadProgress(
    val binaryName: String,
    val phase: BinaryPhase = BinaryPhase.Resolve,
    val bytesDownloaded: Long = 0,
    val totalBytes: Long = 0,
    val speed: Long = 0,
    val isComplete: Boolean = false,
    val error: String? = null,
) {
    val percent: Int
        get() = if (totalBytes > 0) ((bytesDownloaded * 100.0) / totalBytes).coerceIn(0.0, 100.0).toInt() else 0
}

enum class BinaryPhase {
    Resolve,
    Download,
    Verify,
    Extract,
    Complete,
    Error,
}

class BinaryDownloadException(message: String) : IOException(message)

object BinaryManager {
    private const val USER_AGENT = "Grabbo"
    private const val DEFAULT_BUFFER_SIZE = 8192
    private const val PROGRESS_INTERVAL_MS = 500L

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val client by lazy { okhttp3.OkHttpClient() }

    suspend fun getYtDlpInfo(): BinaryInfo {
        val logger = thisLogger()
        val startTime = System.currentTimeMillis()
        val target = BinaryTarget.current()
            ?: throw BinaryDownloadException("yt-dlp is not supported on this platform")
        val entry = BinaryManifest.ytDlp(target)
        val source = entry.sources.firstOrNull { it.isUsable }
            ?: throw BinaryDownloadException("No usable yt-dlp source for target: $target")
        val result = BinaryInfo(
            name = Binaries.YT_DLP,
            version = entry.version,
            downloadUrl = source.url,
            expectedSize = source.size,
            sha256 = source.sha256,
            archiveFormat = entry.archive,
            targetFileName = entry.targetFileName,
        )
        logger.log(Severity.Info, "", null, "yt-dlp resolve took ${System.currentTimeMillis() - startTime} ms, version=${result.version}, size=${result.expectedSize}, sha256=${result.sha256.take(16)}...")
        return result
    }

    suspend fun getFFmpegInfo(): BinaryInfo {
        val logger = thisLogger()
        val startTime = System.currentTimeMillis()

        // Try system ffmpeg first (tier 0)
        val systemFfmpeg = SystemBinaries.findSystemFfmpeg()
        if (systemFfmpeg != null) {
            logger.log(Severity.Info, "", null, "Using system ffmpeg: ${systemFfmpeg.ffmpegPath}, ffprobe: ${systemFfmpeg.ffprobePath}")
            return BinaryInfo(
                name = Binaries.FFMPEG,
                version = "system",
                downloadUrl = "",
                expectedSize = 0,
                sha256 = "",
            )
        }

        val target = BinaryTarget.current()
            ?: throw BinaryDownloadException("ffmpeg is not supported on this platform")

        val entry = BinaryManifest.ffmpeg(target)
        if (entry == null) {
            // macOS - no bundled ffmpeg available
            throw BinaryDownloadException(
                "FFmpeg not available for macOS via bundled sources. " +
                "Please install FFmpeg via Homebrew: `brew install ffmpeg`"
            )
        }

        val source = entry.sources.firstOrNull { it.isUsable }
            ?: throw BinaryDownloadException("No usable ffmpeg source for target: $target")

        val result = BinaryInfo(
            name = Binaries.FFMPEG,
            version = entry.version,
            downloadUrl = source.url,
            expectedSize = source.size,
            sha256 = source.sha256,
            archiveFormat = entry.archive,
            targetFileName = entry.targetFileName,
        )
        logger.log(Severity.Info, "", null, "ffmpeg resolve took ${System.currentTimeMillis() - startTime} ms, version=${result.version}, size=${result.expectedSize}, sha256=${result.sha256.take(16)}...")
        return result
    }

    suspend fun downloadYtDlp(
        binDir: File,
        onProgress: (BinaryDownloadProgress) -> Unit,
    ): File {
        val logger = thisLogger()
        val resolveStart = System.currentTimeMillis()
        val info = getYtDlpInfo()
        onProgress(BinaryDownloadProgress(binaryName = Binaries.YT_DLP, phase = BinaryPhase.Resolve))
        logger.log(Severity.Info, "", null, "yt-dlp resolve took ${System.currentTimeMillis() - resolveStart} ms")

        val downloadStart = System.currentTimeMillis()
        onProgress(BinaryDownloadProgress(binaryName = Binaries.YT_DLP, phase = BinaryPhase.Download))
        val downloaded = downloadFile(
            info = info,
            destinationDir = binDir,
            fileName = info.targetFileName.ifBlank { File(info.downloadUrl).name },
            onProgress = { progress ->
                onProgress(progress.copy(phase = BinaryPhase.Download))
            },
        )
        logger.log(Severity.Info, "", null, "yt-dlp download took ${System.currentTimeMillis() - downloadStart} ms, bytes=${downloaded.length()}")

        val verifyStart = System.currentTimeMillis()
        onProgress(BinaryDownloadProgress(binaryName = Binaries.YT_DLP, phase = BinaryPhase.Verify))
        verifyChecksum(downloaded, info.sha256, "yt-dlp")
        logger.log(Severity.Info, "", null, "yt-dlp verify took ${System.currentTimeMillis() - verifyStart} ms")

        val target = File(Binaries.getYtDlpPath(binDir))
        target.parentFile?.mkdirs()
        moveReplacing(downloaded, target)
        makeExecutable(target)
        // Stamp only after the binary is in place, so an interrupted install leaves the previous
        // stamp (or none) and is retried instead of looking up to date.
        Binaries.writeVersionStamp(binDir, Binaries.YT_DLP, info.version)
        onProgress(
            BinaryDownloadProgress(
                binaryName = Binaries.YT_DLP,
                phase = BinaryPhase.Complete,
                bytesDownloaded = target.length(),
                totalBytes = target.length(),
                speed = 0,
                isComplete = true,
            )
        )
        return target
    }

    suspend fun downloadFFmpeg(
        binDir: File,
        onProgress: (BinaryDownloadProgress) -> Unit,
    ): List<File> {
        val logger = thisLogger()
        val resolveStart = System.currentTimeMillis()
        onProgress(BinaryDownloadProgress(binaryName = Binaries.FFMPEG, phase = BinaryPhase.Resolve))
        val info = getFFmpegInfo()
        logger.log(Severity.Info, "", null, "ffmpeg resolve took ${System.currentTimeMillis() - resolveStart} ms")

        // System ffmpeg - no download needed, just write marker
        if (info.version == "system") {
            val systemFfmpeg = SystemBinaries.findSystemFfmpeg()
                ?: throw BinaryDownloadException("System ffmpeg disappeared during download")
            SystemBinaries.writeMarkerFile(binDir, systemFfmpeg.ffmpegDir)
            onProgress(
                BinaryDownloadProgress(
                    binaryName = Binaries.FFMPEG,
                    phase = BinaryPhase.Complete,
                    bytesDownloaded = 0,
                    totalBytes = 0,
                    speed = 0,
                    isComplete = true,
                )
            )
            return listOf(File(systemFfmpeg.ffmpegPath), File(systemFfmpeg.ffprobePath))
        }

        // Log stale partial files
        val stalePartials = binDir.listFiles { file ->
            file.name.endsWith(".part") || file.name.startsWith("ffmpeg-") && (file.name.endsWith(".tar.xz") || file.name.endsWith(".zip"))
        } ?: emptyArray()
        if (stalePartials.isNotEmpty()) {
            logger.log(Severity.Warn, "", null, "Found ${stalePartials.size} stale partial files in binDir: ${stalePartials.map { it.name }.joinToString(", ")}")
            stalePartials.forEach { it.delete() }
        }

        val downloadStart = System.currentTimeMillis()
        onProgress(BinaryDownloadProgress(binaryName = Binaries.FFMPEG, phase = BinaryPhase.Download))
        val archive = downloadFile(
            info = info,
            destinationDir = binDir,
            fileName = "ffmpeg-${info.version}-${File(info.downloadUrl).name}",
            onProgress = { progress ->
                onProgress(progress.copy(phase = BinaryPhase.Download))
            },
        )
        logger.log(Severity.Info, "", null, "ffmpeg download took ${System.currentTimeMillis() - downloadStart} ms, bytes=${archive.length()}")

        val verifyStart = System.currentTimeMillis()
        onProgress(BinaryDownloadProgress(binaryName = Binaries.FFMPEG, phase = BinaryPhase.Verify))
        verifyChecksum(archive, info.sha256, "ffmpeg")
        logger.log(Severity.Info, "", null, "ffmpeg verify took ${System.currentTimeMillis() - verifyStart} ms")

        val extractStart = System.currentTimeMillis()
        onProgress(BinaryDownloadProgress(binaryName = Binaries.FFMPEG, phase = BinaryPhase.Extract))
        val extracted = try {
            extract(archive, binDir, info.archiveFormat)
        } catch (e: Exception) {
            // Keep the archive behind when extraction fails so it can be inspected, the next
            // download attempt sweeps it up as a stale partial.
            throw e
        }
        // A successful run must not leave a ~150MB archive sitting in the download folder.
        archive.delete()
        logger.log(Severity.Info, "", null, "ffmpeg extract took ${System.currentTimeMillis() - extractStart} ms, extracted=${extracted.map { it.name }.joinToString(", ")}")
        extracted.forEach(::makeExecutable)
        Binaries.writeVersionStamp(binDir, Binaries.FFMPEG, info.version)
        onProgress(
            BinaryDownloadProgress(
                binaryName = Binaries.FFMPEG,
                phase = BinaryPhase.Complete,
                bytesDownloaded = extracted.sumOf { it.length() },
                totalBytes = extracted.sumOf { it.length() },
                speed = 0,
                isComplete = true,
            )
        )
        return extracted
    }

    internal fun verifyChecksum(file: File, expectedSha256: String, label: String) {
        if (expectedSha256.isBlank() || expectedSha256.startsWith("PENDING", ignoreCase = true)) {
            thisLogger().log(Severity.Warn, "", null, "$label: SHA-256 not available in manifest, skipping verification")
            return
        }
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var read: Int
            while (input.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
            }
        }
        val actualSha256 = digest.digest().joinToString("") { "%02x".format(it) }
        if (!actualSha256.equals(expectedSha256, ignoreCase = true)) {
            throw BinaryDownloadException("$label checksum mismatch: expected $expectedSha256, got $actualSha256")
        }
    }

    private suspend fun downloadFile(
        info: BinaryInfo,
        destinationDir: File,
        fileName: String,
        onProgress: (BinaryDownloadProgress) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        val logger = thisLogger()
        val startTime = System.currentTimeMillis()

        destinationDir.mkdirs()

        // Always download into "<fileName>.part" and let the caller publish it under the real name
        // only after the checksum has been verified.
        //
        // Writing straight to the destination had two consequences:
        //  - an interrupted download left a truncated file under the real name, and
        //  - for single file downloads (yt-dlp) the downloaded file and the "target" the caller
        //    replaces were the exact same path, so the replace step deleted the only copy it had
        //    and then failed while copying the now missing source onto itself.
        val partFile = File(destinationDir, "$fileName.part")
        if (partFile.exists()) {
            logger.log(Severity.Warn, "", null, "Found stale partial download: ${partFile.name} (${partFile.length()} bytes), deleting")
            partFile.delete()
        }

        val request = okhttp3.Request.Builder()
            .url(info.downloadUrl)
            .header("User-Agent", USER_AGENT)
            .build()
        val response = client.newCall(request).execute()
        try {
            if (!response.isSuccessful) {
                throw BinaryDownloadException(
                    "Failed to download ${info.name}: ${response.code} ${response.message}"
                )
            }
            val body = response.body
            val totalBytes = body.contentLength().takeIf { it > 0 } ?: info.expectedSize
            var downloaded = 0L
            var lastReported = 0L
            var lastReportTime = System.currentTimeMillis()
            body.byteStream().use { input ->
                partFile.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    // Checked every buffer so a cancel actually stops a large download instead of
                    // running to completion; this is just a job state check, so it is cheap.
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        val now = System.currentTimeMillis()
                        if (now - lastReportTime >= PROGRESS_INTERVAL_MS) {
                            val elapsed = (now - lastReportTime).coerceAtLeast(1)
                            onProgress(
                                BinaryDownloadProgress(
                                    binaryName = info.name,
                                    bytesDownloaded = downloaded,
                                    totalBytes = totalBytes,
                                    speed = (downloaded - lastReported) * 1000 / elapsed,
                                    isComplete = false,
                                )
                            )
                            lastReported = downloaded
                            lastReportTime = now
                        }
                    }
                }
            }
            onProgress(
                BinaryDownloadProgress(
                    binaryName = info.name,
                    bytesDownloaded = downloaded,
                    totalBytes = totalBytes,
                    speed = 0,
                    isComplete = true,
                )
            )
            logger.log(Severity.Info, "", null, "${info.name} download took ${System.currentTimeMillis() - startTime} ms, bytes=$downloaded")
            partFile
        } catch (e: Exception) {
            partFile.delete()
            throw e
        } finally {
            response.close()
        }
    }

    /**
 * Dispatches on the format the manifest declared instead of sniffing the file name: the archive is
 * still a ".part" file at this point, so its name no longer ends in a usable extension.
 */
private fun extract(archive: File, binDir: File, format: ArchiveFormat): List<File> {
    return when (format) {
        ArchiveFormat.Zip -> extractZip(archive, binDir)
        ArchiveFormat.TarXz -> extractTar(archive, binDir)
        ArchiveFormat.None ->
            throw BinaryDownloadException("Expected ${archive.name} to be an archive, but it is not")
    }
}

    private fun extractZip(archive: File, binDir: File): List<File> {
        val logger = thisLogger()
        val startTime = System.currentTimeMillis()
        val extracted = mutableListOf<File>()
        ZipFile(archive).use { zip ->
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                val name = entry.name.substringAfterLast('/')
                if (entry.isDirectory || !isBinaryName(name)) continue
                val outFile = File(binDir, name)
                zip.getInputStream(entry).use { input ->
                    outFile.outputStream().use { output -> input.copyTo(output) }
                }
                extracted.add(outFile)
            }
        }
        if (extracted.isEmpty()) {
            throw BinaryDownloadException("No FFmpeg binary was found inside ${archive.name}")
        }
        logger.log(Severity.Info, "", null, "zip extract took ${System.currentTimeMillis() - startTime} ms, extracted=${extracted.map { it.name }.joinToString(", ")}")
        return extracted
    }

    internal fun extractTar(archive: File, binDir: File): List<File> {
        val logger = thisLogger()
        val startTime = System.currentTimeMillis()
        requireExtractionTools(ArchiveFormat.TarXz)
        val workDir = File(binDir, ".ffmpeg-extract-${System.nanoTime()}")
        workDir.mkdirs()
        val process = ProcessBuilder("tar", "-I", "xz -T0", "-xf", archive.absolutePath, "-C", workDir.absolutePath)
            .withoutJPackageEnvVariable()
            .redirectErrorStream(true)
            .start()
        val error = process.inputStream.bufferedReader().use { it.readText() }
        if (!process.waitFor(10, TimeUnit.MINUTES)) {
            process.destroy()
            workDir.deleteRecursively()
            throw BinaryDownloadException("Timed out while extracting ${archive.name}")
        }
        if (process.exitValue() != 0) {
            workDir.deleteRecursively()
            throw BinaryDownloadException("Failed to extract ${archive.name}: ${error.take(200)}")
        }
        val extracted = mutableListOf<File>()
        try {
            workDir.walkTopDown().filter { it.isFile && isBinaryName(it.name) }.forEach { source ->
                val outFile = File(binDir, source.name)
                moveReplacing(source, outFile)
                extracted.add(outFile)
            }
        } finally {
            workDir.deleteRecursively()
        }
        if (extracted.isEmpty()) {
            throw BinaryDownloadException("No FFmpeg binary was found inside ${archive.name}")
        }
        logger.log(Severity.Info, "", null, "tar extract took ${System.currentTimeMillis() - startTime} ms, extracted=${extracted.map { it.name }.joinToString(", ")}")
        return extracted
    }

    internal fun moveReplacing(source: File, target: File) {
        // Replacing a file with itself deletes the only copy before it can be moved, which loses the
        // download entirely. Callers download into a ".part" file precisely so this cannot happen,
        // but bail out loudly here rather than silently destroying data if one ever is.
        if (source.absoluteFile == target.absoluteFile) {
            thisLogger().log(
                Severity.Warn,
                "",
                null,
                "moveReplacing called with identical source and target (${target.name}), skipping",
            )
            return
        }
        target.parentFile?.mkdirs()
        if (target.exists() && !target.delete()) {
            source.delete()
            throw BinaryDownloadException("Could not replace ${target.name}, close the app and try again")
        }
        if (!source.renameTo(target)) {
            source.copyTo(target, overwrite = true)
            source.delete()
        }
    }

    /**
 * Fails early with a clear message when the external tools needed to unpack an archive are absent.
 *
 * Extracting tar.xz shells out to `tar -I "xz -T0"`. Without this check a machine missing `xz` only
 * finds out after downloading ~40MB, and the resulting tar error reads like a corrupt download
 * rather than a missing dependency.
 */
internal fun requireExtractionTools(
        format: ArchiveFormat,
        pathDirs: List<String> = (System.getenv("PATH") ?: "")
            .split(File.pathSeparatorChar)
            .filter { it.isNotBlank() },
    ) {
        val tools = when (format) {
            ArchiveFormat.TarXz -> listOf("tar", "xz")
            // zip is unpacked in process, nothing external is needed.
            ArchiveFormat.Zip, ArchiveFormat.None -> emptyList()
        }
        val missing = tools.filter { tool ->
            pathDirs.none { dir -> File(dir, tool).canExecute() }
        }
        if (missing.isNotEmpty()) {
            throw BinaryDownloadException(
                "Cannot unpack the downloaded archive, ${missing.joinToString(" and ")} " +
                    "${if (missing.size > 1) "were" else "was"} not found in PATH"
            )
        }
    }

private fun isBinaryName(name: String): Boolean {
        val baseName = name.substringBeforeLast('.', name)
        if (baseName != Binaries.FFMPEG && baseName != Binaries.FFPROBE) return false
        val extension = name.substringAfterLast('.', "")
        return extension.isEmpty() || extension.equals("exe", ignoreCase = true)
    }

    private fun makeExecutable(file: File) {
        if (Platform.getCurrentPlatform() !is Desktop.Windows) {
            file.setExecutable(true, false)
        }
    }
}