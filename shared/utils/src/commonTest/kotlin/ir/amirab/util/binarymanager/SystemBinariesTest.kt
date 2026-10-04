package ir.amirab.util.binarymanager

import ir.amirab.util.platform.Platform
import ir.amirab.util.platform.Platform.Desktop
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SystemBinariesTest {

    @Test
    fun `findSystemFfmpeg returns a complete runnable pair or nothing at all`() {
        val result = SystemBinaries.findSystemFfmpeg()
        // Detection depends on the machine. A partial pair is worse than no pair, because the
        // caller would adopt an ffmpeg with no ffprobe, so the only contract worth asserting is
        // "all three fields or null".
        if (result == null) return
        assertTrue(result.ffmpegPath.isNotBlank(), "ffmpeg path should not be blank")
        assertTrue(result.ffprobePath.isNotBlank(), "ffprobe path should not be blank")
        assertTrue(result.ffmpegDir.isNotBlank(), "ffmpeg dir should not be blank")
        assertTrue(File(result.ffmpegPath).canExecute(), "reported ffmpeg should be executable")
        assertTrue(File(result.ffprobePath).canExecute(), "reported ffprobe should be executable")
    }

    @Test
    fun `writeMarkerFile and readMarkerFile roundtrip`() {
        val tempDir = Files.createTempDirectory("system-binaries-test").toFile()
        try {
            val testDir = "/usr/local/bin/ffmpeg"
            SystemBinaries.writeMarkerFile(tempDir, testDir)
            val readBack = SystemBinaries.readMarkerFile(tempDir)
            assertEquals(testDir, readBack)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `readMarkerFile returns null when file missing`() {
        val tempDir = Files.createTempDirectory("system-binaries-test").toFile()
        try {
            val readBack = SystemBinaries.readMarkerFile(tempDir)
            assertNull(readBack)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `readMarkerFile returns null for empty file`() {
        val tempDir = Files.createTempDirectory("system-binaries-test").toFile()
        try {
            val markerFile = File(tempDir, "ffmpeg-location.txt")
            markerFile.writeText("", StandardCharsets.UTF_8)
            val readBack = SystemBinaries.readMarkerFile(tempDir)
            assertNull(readBack)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `readMarkerFile returns null for whitespace only`() {
        val tempDir = Files.createTempDirectory("system-binaries-test").toFile()
        try {
            val markerFile = File(tempDir, "ffmpeg-location.txt")
            markerFile.writeText("   \n\t  ", StandardCharsets.UTF_8)
            val readBack = SystemBinaries.readMarkerFile(tempDir)
            assertNull(readBack)
        } finally {
            tempDir.deleteRecursively()
        }
    }
}