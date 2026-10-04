package ir.amirab.util.binarymanager

import ir.amirab.util.platform.Platform
import ir.amirab.util.platform.Platform.Desktop
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BinariesTest {

    @Test
    fun `getMissing returns yt-dlp when not present`() {
        val tempDir = Files.createTempDirectory("binaries-test").toFile()
        try {
            val binDir = Binaries.getBinDir(tempDir)
            val missing = Binaries.getMissing(binDir)
            assertTrue(missing.contains(Binaries.YT_DLP))
            assertTrue(missing.contains(Binaries.FFMPEG))
            assertTrue(missing.contains(Binaries.FFPROBE))
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `getMissing returns empty when all binaries present`() {
        val tempDir = Files.createTempDirectory("binaries-test").toFile()
        try {
            val binDir = Binaries.getBinDir(tempDir)
            binDir.mkdirs()

            // Create dummy executables
            val ytDlp = File(Binaries.getYtDlpPath(binDir))
            val ffmpeg = File(Binaries.getFFmpegPath(binDir))
            val ffprobe = File(Binaries.getFFprobePath(binDir))

            ytDlp.parentFile?.mkdirs()
            ytDlp.writeText("", StandardCharsets.UTF_8)
            ytDlp.setExecutable(true)

            ffmpeg.parentFile?.mkdirs()
            ffmpeg.writeText("", StandardCharsets.UTF_8)
            ffmpeg.setExecutable(true)

            ffprobe.parentFile?.mkdirs()
            ffprobe.writeText("", StandardCharsets.UTF_8)
            ffprobe.setExecutable(true)

            // A binary only counts as available while it matches the pinned version, so stamp them
            // with what the manifest currently pins.
            val target = assertNotNull(BinaryTarget.current())
            Binaries.writeVersionStamp(binDir, Binaries.YT_DLP, BinaryManifest.ytDlp(target).version)
            BinaryManifest.ffmpeg(target)?.let {
                Binaries.writeVersionStamp(binDir, Binaries.FFMPEG, it.version)
            }

            val missing = Binaries.getMissing(binDir)
            assertTrue(missing.isEmpty())
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `getMissing reports present but unstamped binaries as missing`() {
        val tempDir = Files.createTempDirectory("binaries-test-unstamped").toFile()
        try {
            val binDir = Binaries.getBinDir(tempDir)
            binDir.mkdirs()

            val ytDlp = File(Binaries.getYtDlpPath(binDir))
            ytDlp.writeText("", StandardCharsets.UTF_8)
            ytDlp.setExecutable(true)

            assertTrue(Binaries.isAvailable(Binaries.getYtDlpPath(binDir)))
            assertTrue(Binaries.getMissing(binDir).contains(Binaries.YT_DLP))
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `getMissing respects system ffmpeg marker`() {
        val tempDir = Files.createTempDirectory("binaries-test").toFile()
        try {
            val binDir = Binaries.getBinDir(tempDir)
            binDir.mkdirs()

            // Write system ffmpeg marker pointing to a fake dir (no actual binaries created)
            SystemBinaries.writeMarkerFile(binDir, "/fake/system/ffmpeg")

            val missing = Binaries.getMissing(binDir)
            assertTrue(missing.contains(Binaries.YT_DLP))
            assertFalse(missing.contains(Binaries.FFMPEG))
            assertFalse(missing.contains(Binaries.FFPROBE))
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `getFFmpegDir returns marker dir when marker exists`() {
        val tempDir = Files.createTempDirectory("binaries-test").toFile()
        try {
            val binDir = Binaries.getBinDir(tempDir)
            binDir.mkdirs()

            SystemBinaries.writeMarkerFile(binDir, "/custom/ffmpeg/dir")
            val ffmpegDir = Binaries.getFFmpegDir(binDir)

            assertEquals("/custom/ffmpeg/dir", ffmpegDir)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `getFFmpegDir returns binDir when no marker`() {
        val tempDir = Files.createTempDirectory("binaries-test").toFile()
        try {
            val binDir = Binaries.getBinDir(tempDir)
            binDir.mkdirs()

            val ffmpegDir = Binaries.getFFmpegDir(binDir)
            assertEquals(binDir.absolutePath, ffmpegDir)
        } finally {
            tempDir.deleteRecursively()
        }
    }
}