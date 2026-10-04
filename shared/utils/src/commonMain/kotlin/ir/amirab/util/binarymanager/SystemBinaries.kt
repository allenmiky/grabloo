package ir.amirab.util.binarymanager

import ir.amirab.util.logger.thisLogger
import ir.amirab.util.platform.Platform
import ir.amirab.util.platform.Platform.Desktop
import ir.amirab.util.withoutJPackageEnvVariable
import co.touchlab.kermit.Severity
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

/**
 * Detects binaries that are already installed on the machine, so they do not have to be
 * downloaded at all. This is the zero-byte tier for ffmpeg.
 *
 * Only ffmpeg/ffprobe are ever taken from the system. yt-dlp is deliberately not, because the
 * downloader does not check its version and a stale system yt-dlp would silently fail on
 * freshly released sites.
 */
object SystemBinaries {
    private const val MIN_FFMPEG_MAJOR_VERSION = 4
    private const val PROBE_TIMEOUT_SECONDS = 8L
    private val versionPattern = Regex("version\\s+n?(\\d+)", RegexOption.IGNORE_CASE)

    data class SystemFfmpegInfo(
        val ffmpegPath: String,
        val ffprobePath: String,
        val ffmpegDir: String,
    )

    private val cachedFfmpegInfo: SystemFfmpegInfo? by lazy { detectSystemFfmpeg() }

    fun findSystemFfmpeg(): SystemFfmpegInfo? = cachedFfmpegInfo

    /**
     * Absolute directory of a usable system ffmpeg + ffprobe pair, or null.
     * The lookup is done once per process: it runs a subprocess, and the answer cannot change
     * while the app runs in any way that matters.
     */
    fun findFFmpegDir(): String? = cachedFfmpegInfo?.ffmpegDir

    private fun detectSystemFfmpeg(): SystemFfmpegInfo? {
        val platform = Platform.getCurrentPlatform()
        if (platform !is Desktop) return null
        val executableSuffix = if (platform is Desktop.Windows) ".exe" else ""
        val pathDirs = (System.getenv("PATH") ?: "")
            .split(File.pathSeparator)
            .filter { it.isNotBlank() }
        thisLogger().log(Severity.Debug, "", null, "Detecting system ffmpeg in ${pathDirs.size} PATH entries")
        for (dir in pathDirs) {
            val directory = File(dir)
            if (!directory.isDirectory) continue
            val ffmpeg = File(directory, "ffmpeg$executableSuffix")
            val ffprobe = File(directory, "ffprobe$executableSuffix")
            if (!Binaries.isAvailable(ffmpeg.absolutePath)) continue
            if (!Binaries.isAvailable(ffprobe.absolutePath)) continue
            if (!isSupportedFFmpeg(ffmpeg.absolutePath)) continue
            thisLogger().log(Severity.Info, "", null, "Using system ffmpeg from ${directory.absolutePath}")
            return SystemFfmpegInfo(
                ffmpegPath = ffmpeg.absolutePath,
                ffprobePath = ffprobe.absolutePath,
                ffmpegDir = directory.absolutePath,
            )
        }
        thisLogger().log(Severity.Info, "", null, "No usable system ffmpeg+ffprobe pair found in PATH")
        return null
    }

    private fun isSupportedFFmpeg(ffmpegPath: String): Boolean {
        val output = runAndRead(ffmpegPath, "-version") ?: return false
        val major = versionPattern.find(output)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
        if (major == null) {
            thisLogger().log(Severity.Debug, "", null, "Could not parse ffmpeg version from $ffmpegPath")
            return false
        }
        if (major < MIN_FFMPEG_MAJOR_VERSION) {
            thisLogger().log(Severity.Debug, "", null, "System ffmpeg at $ffmpegPath is too old (major $major)")
            return false
        }
        return true
    }

    private fun runAndRead(executable: String, vararg args: String): String? = try {
        val process = ProcessBuilder(listOf(executable) + args)
            .withoutJPackageEnvVariable()
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        if (!process.waitFor(PROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            process.destroy()
            null
        } else if (process.exitValue() != 0) {
            null
        } else {
            output
        }
    } catch (e: Exception) {
        thisLogger().log(Severity.Debug, "", null, "Failed to run $executable")
        null
    }

    fun writeMarkerFile(binDir: File, ffmpegDir: String) {
        val markerFile = File(binDir, "ffmpeg-location.txt")
        markerFile.writeText(ffmpegDir, StandardCharsets.UTF_8)
    }

    fun readMarkerFile(binDir: File): String? {
        val markerFile = File(binDir, "ffmpeg-location.txt")
        return if (markerFile.exists()) {
            markerFile.readText(StandardCharsets.UTF_8).trim().takeIf { it.isNotBlank() }
        } else null
    }
}