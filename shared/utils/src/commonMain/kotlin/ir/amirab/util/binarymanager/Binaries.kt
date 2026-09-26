package ir.amirab.util.binarymanager

import ir.amirab.util.platform.Platform
import ir.amirab.util.platform.Platform.Desktop
import java.io.File

/**
 * single source of truth for where the external binaries live, both the downloader and the UI use this.
 */
object Binaries {
    const val YT_DLP = "yt-dlp"
    const val FFMPEG = "ffmpeg"
    const val FFPROBE = "ffprobe"

    const val BIN_DIR_NAME = "bin"

    /**
     * the single place that decides where the binaries live.
     * both the downloader (which only knows the download data folder) and the UI go through this,
     * so they can never disagree about the path.
     */
    fun getBinDir(downloadDataDir: File): File {
        return File(downloadDataDir, BIN_DIR_NAME)
    }

    fun getYtDlpPath(binDir: File): String = getPath(binDir, YT_DLP)

    fun getFFmpegPath(binDir: File): String = getPath(binDir, FFMPEG)

    fun getFFprobePath(binDir: File): String = getPath(binDir, FFPROBE)

    fun getFFmpegDir(binDir: File): String = File(getFFmpegPath(binDir)).parent

    fun getPath(binDir: File, baseName: String): String {
        val extension = when (Platform.getCurrentPlatform()) {
            is Desktop.Windows -> ".exe"
            else -> ""
        }
        return File(binDir, "$baseName$extension").absolutePath
    }

    fun isAvailable(path: String): Boolean {
        val file = File(path)
        return file.exists() && file.canExecute()
    }

    fun getMissing(binDir: File): List<String> {
        return listOf(
            YT_DLP to getYtDlpPath(binDir),
            FFMPEG to getFFmpegPath(binDir),
            FFPROBE to getFFprobePath(binDir),
        ).filterNot { (_, path) -> isAvailable(path) }.map { it.first }
    }

    fun getMissingMessage(binDir: File): String {
        val missing = getMissing(binDir)
        if (missing.isEmpty()) return ""
        return "yt-dlp is not ready yet, missing components in ${binDir.absolutePath}: " +
            missing.joinToString(", ")
    }
}
