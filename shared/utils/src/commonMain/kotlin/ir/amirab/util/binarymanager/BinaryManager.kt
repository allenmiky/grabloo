package ir.amirab.util.binarymanager

import ir.amirab.util.platform.Arch
import ir.amirab.util.platform.Platform
import ir.amirab.util.platform.Platform.Desktop
import ir.amirab.util.withoutJPackageEnvVariable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.zip.ZipFile

data class BinaryInfo(
    val name: String,
    val version: String,
    val downloadUrl: String,
    val expectedSize: Long = -1,
)

data class BinaryDownloadProgress(
    val binaryName: String,
    val bytesDownloaded: Long,
    val totalBytes: Long,
    val speed: Long,
    val isComplete: Boolean,
    val error: String? = null,
)

class BinaryDownloadException(message: String) : IOException(message)

object BinaryManager {
    private const val YT_DLP_REPO = "yt-dlp/yt-dlp"
    private const val FFMPEG_REPO = "BtbN/FFmpeg-Builds"
    private const val USER_AGENT = "Grabbo"

    private val json = Json {
        ignoreUnknownKeys = true
    }

    private val client by lazy { okhttp3.OkHttpClient() }

    suspend fun getYtDlpInfo(): BinaryInfo {
        val release = fetchLatestRelease(YT_DLP_REPO)
        val assetName = when (Platform.getCurrentPlatform()) {
            is Desktop.Windows -> "yt-dlp.exe"
            is Desktop.Linux -> "yt-dlp_linux"
            is Desktop.MacOS -> "yt-dlp_macos"
            else -> throw BinaryDownloadException("yt-dlp is not supported on this platform")
        }
        return release.toBinaryInfo(Binaries.YT_DLP, assetName)
    }

    suspend fun getFFmpegInfo(): BinaryInfo {
        val release = fetchLatestRelease(FFMPEG_REPO)
        val isArm64 = Arch.getCurrentArch() == Arch.Arm64
        val assetName = when (Platform.getCurrentPlatform()) {
            is Desktop.Windows -> if (isArm64) "ffmpeg-master-latest-winarm64-gpl.zip" else "ffmpeg-master-latest-win64-gpl.zip"
            is Desktop.Linux -> if (isArm64) "ffmpeg-master-latest-linuxarm64-gpl.tar.xz" else "ffmpeg-master-latest-linux64-gpl.tar.xz"
            is Desktop.MacOS -> if (isArm64) "ffmpeg-master-latest-macosarm64-gpl.tar.xz" else "ffmpeg-master-latest-macos64-gpl.tar.xz"
            else -> throw BinaryDownloadException("ffmpeg is not supported on this platform")
        }
        return release.toBinaryInfo(Binaries.FFMPEG, assetName)
    }

    suspend fun downloadYtDlp(
        binDir: File,
        onProgress: (BinaryDownloadProgress) -> Unit,
    ): File {
        val info = getYtDlpInfo()
        val downloaded = downloadFile(
            info = info,
            destinationDir = binDir,
            fileName = File(info.downloadUrl).name,
            onProgress = onProgress,
        )
        if (info.expectedSize > 0 && downloaded.length() != info.expectedSize) {
            downloaded.delete()
            throw BinaryDownloadException("yt-dlp download is incomplete, please try again")
        }
        val target = File(Binaries.getYtDlpPath(binDir))
        target.parentFile?.mkdirs()
        moveReplacing(downloaded, target)
        makeExecutable(target)
        onProgress(
            BinaryDownloadProgress(
                binaryName = Binaries.YT_DLP,
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
        val info = getFFmpegInfo()
        val archive = downloadFile(
            info = info,
            destinationDir = binDir,
            fileName = "ffmpeg-${info.version}-${File(info.downloadUrl).name}",
            onProgress = onProgress,
        )
        val archiveSize = archive.length()
        onProgress(
            BinaryDownloadProgress(
                binaryName = Binaries.FFMPEG,
                bytesDownloaded = archiveSize,
                totalBytes = archiveSize,
                speed = 0,
                isComplete = true,
            )
        )
        val extracted = try {
            extract(archive, binDir)
        } finally {
            archive.delete()
        }
        extracted.forEach(::makeExecutable)
        return extracted
    }

    private suspend fun downloadFile(
        info: BinaryInfo,
        destinationDir: File,
        fileName: String,
        onProgress: (BinaryDownloadProgress) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        destinationDir.mkdirs()
        val request = okhttp3.Request.Builder()
            .url(info.downloadUrl)
            .header("User-Agent", USER_AGENT)
            .build()
        val destinationFile = File(destinationDir, fileName)
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
                destinationFile.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
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
            destinationFile
        } catch (e: Exception) {
            destinationFile.delete()
            throw e
        } finally {
            response.close()
        }
    }

    private fun extract(archive: File, binDir: File): List<File> {
        return when {
            archive.name.endsWith(".zip") -> extractZip(archive, binDir)
            archive.name.endsWith(".tar.xz") || archive.name.endsWith(".txz") -> extractTar(archive, binDir)
            else -> throw BinaryDownloadException("Unsupported archive format: ${archive.name}")
        }
    }

    private fun extractZip(archive: File, binDir: File): List<File> {
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
        return extracted
    }

    private fun extractTar(archive: File, binDir: File): List<File> {
        val workDir = File(binDir, ".ffmpeg-extract-${System.nanoTime()}")
        workDir.mkdirs()
        val process = ProcessBuilder("tar", "-xf", archive.absolutePath, "-C", workDir.absolutePath)
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
            // builds like BtbN nest the executables in a versioned folder, so they are searched recursively
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
        return extracted
    }

    private fun moveReplacing(source: File, target: File) {
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

    private fun isBinaryName(name: String): Boolean {
        val baseName = name.substringBeforeLast('.', name)
        if (baseName != Binaries.FFMPEG && baseName != Binaries.FFPROBE) return false
        // the archives also ship man pages (ffmpeg.1) and docs (ffmpeg.html), those are not binaries
        val extension = name.substringAfterLast('.', "")
        return extension.isEmpty() || extension.equals("exe", ignoreCase = true)
    }

    private fun makeExecutable(file: File) {
        if (Platform.getCurrentPlatform() !is Desktop.Windows) {
            file.setExecutable(true, false)
        }
    }

    private fun GitHubRelease.toBinaryInfo(binaryName: String, assetName: String): BinaryInfo {
        val asset = assets.firstOrNull { it.name == assetName }
            ?: throw BinaryDownloadException("Could not find $assetName in the latest $binaryName release")
        return BinaryInfo(
            name = binaryName,
            version = tagName,
            downloadUrl = asset.browserDownloadUrl,
            expectedSize = asset.size,
        )
    }

    private suspend fun fetchLatestRelease(repo: String): GitHubRelease = withContext(Dispatchers.IO) {
        val request = okhttp3.Request.Builder()
            .url("https://api.github.com/repos/$repo/releases/latest")
            .header("User-Agent", USER_AGENT)
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body.string()
            if (!response.isSuccessful) {
                throw BinaryDownloadException(
                    "Failed to query $repo: ${response.code} ${response.message}"
                )
            }
            json.decodeFromString<GitHubRelease>(body)
        }
    }


    private fun Desktop.isArm64(): Boolean = Arch.getCurrentArch() == Arch.Arm64

    @Serializable
    private data class GitHubRelease(
        @SerialName("tag_name") val tagName: String,
        val assets: List<GitHubAsset> = emptyList(),
    )

    @Serializable
    private data class GitHubAsset(
        val name: String,
        @SerialName("browser_download_url") val browserDownloadUrl: String,
        val size: Long = -1,
    )

    private const val PROGRESS_INTERVAL_MS = 500L
}
