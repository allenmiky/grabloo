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

    fun getFFmpegDir(binDir: File): String {
        val marker = SystemBinaries.readMarkerFile(binDir)
        return marker ?: File(getFFmpegPath(binDir)).parent
    }

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

    /**
     * Version stamp written next to a binary once it has been installed.
     *
     * Without it a binary that is already on disk looks permanently "available", so bumping a
     * version in [BinaryManifest] would never reach anyone who installed an earlier build: the file
     * exists, so nothing is ever downloaded again and they silently keep the old binary forever.
     * yt-dlp in particular needs frequent updates because site changes break older releases.
     */
    fun getVersionStampPath(binDir: File, name: String): File = File(binDir, "$name.version")

    fun readVersionStamp(binDir: File, name: String): String? =
        getVersionStampPath(binDir, name)
            .takeIf { it.isFile }
            ?.readText()
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

    fun writeVersionStamp(binDir: File, name: String, version: String) {
        if (version.isBlank()) return
        binDir.mkdirs()
        getVersionStampPath(binDir, name).writeText(version)
    }

    fun removeVersionStamp(binDir: File, name: String) {
        getVersionStampPath(binDir, name).delete()
    }

    /** Versions the current manifest pins, keyed by binary name. */
    private fun pinnedVersions(): Map<String, String> {
        val target = BinaryTarget.current() ?: return emptyMap()
        return buildMap {
            // ytDlp() is total for every target, ffmpeg() is null on macOS where no bundled build
            // is pinned and the system tier has to cover it.
            put(YT_DLP, BinaryManifest.ytDlp(target).version)
            BinaryManifest.ffmpeg(target)?.let { put(FFMPEG, it.version) }
        }
    }

    /**
     * True when the installed binary matches what the manifest pins. A missing stamp counts as
     * outdated on purpose: it means the binary predates version tracking, so it gets refreshed once
     * and is stamped from then on.
     */
    private fun isVersionCurrent(binDir: File, name: String, pinned: Map<String, String>): Boolean {
        val wanted = pinned[name] ?: return true
        return readVersionStamp(binDir, name) == wanted
    }

    fun getMissing(binDir: File): List<String> {
        // Check for system ffmpeg marker first
        val hasSystemFfmpeg = SystemBinaries.readMarkerFile(binDir) != null
        val pinned = pinnedVersions()

        return listOf(
            YT_DLP to getYtDlpPath(binDir),
            FFMPEG to getFFmpegPath(binDir),
            FFPROBE to getFFprobePath(binDir),
        ).filterNot { (name, path) ->
            if (name == FFMPEG || name == FFPROBE) {
                // A system ffmpeg is never version pinned here, so the marker settles it.
                hasSystemFfmpeg || (isAvailable(path) && isVersionCurrent(binDir, name, pinned))
            } else {
                isAvailable(path) && isVersionCurrent(binDir, name, pinned)
            }
        }.map { it.first }
    }

    fun getMissingMessage(binDir: File): String {
        val missing = getMissing(binDir)
        if (missing.isEmpty()) return ""
        return "yt-dlp is not ready yet, missing components in ${binDir.absolutePath}: " +
            missing.joinToString(", ")
    }
}
