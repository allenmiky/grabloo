package ir.amirab.util.binarymanager

import ir.amirab.util.platform.Platform
import java.io.File
import java.nio.file.Files
import kotlin.OptIn
import kotlin.test.ExperimentalKotlinTestApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Covers the ffmpeg unpack path.
 *
 * This used to be untested: on a machine with a system ffmpeg, [BinaryManager.getFFmpegInfo]
 * resolves to the system tier and returns before any download, so the download + extract path never
 * runs. Building a tiny tar.xz locally exercises the real `tar -I "xz -T0"` invocation, the
 * directory walk, the binary name filter and the move, without needing the network or the ~41MB
 * archive.
 */
@OptIn(ExperimentalKotlinTestApi::class)
class ExtractTarTest {

    private fun onUnix(): Boolean = Platform.getCurrentPlatform() is Platform.Desktop

    /** Builds `<root>/ffmpeg` and `<root>/ffprobe`, matching the layout the real archives use. */
    private fun buildArchive(root: File): File {
        val payload = File(root, "ffmpeg-testroot")
        payload.mkdirs()
        listOf("ffmpeg", "ffprobe").forEach { name ->
            val f = File(payload, name)
            f.writeText("#!/bin/sh\necho $name 1.0\n")
            assertTrue(f.setExecutable(true, false))
        }
        val archive = File(root, "ffmpeg-test.tar.xz")
        val proc = ProcessBuilder("tar", "-I", "xz -T0", "-cf", archive.absolutePath, "-C", root.absolutePath, "ffmpeg-testroot")
            .redirectErrorStream(true)
            .start()
        val err = proc.inputStream.bufferedReader().readText()
        val exit = proc.waitFor()
        assertEquals(0, exit, "failed to build the fixture archive: $err")
        assertTrue(archive.length() > 0, "fixture archive is empty")
        return archive
    }

    @Test
    fun `extracts ffmpeg and ffprobe out of a tar xz into the bin dir`() {
        if (!onUnix()) return
        val root = Files.createTempDirectory("extract-tar").toFile()
        try {
            val archive = buildArchive(root)
            val binDir = File(root, "bin")
            binDir.mkdirs()

            val extracted = BinaryManager.extractTar(archive, binDir)

            assertEquals(2, extracted.size, "expected both binaries, got ${extracted.map { it.name }}")
            val names = extracted.map { it.name }.sorted()
            assertEquals(listOf("ffmpeg", "ffprobe"), names)

            // They must land directly in binDir, not stay inside the archive's own folder.
            listOf("ffmpeg", "ffprobe").forEach { name ->
                val landed = File(binDir, name)
                assertTrue(landed.isFile, "$name was not extracted into the bin dir")
                assertTrue(landed.canExecute(), "$name should still be executable after the move")
            }

            // No leftovers from the extraction workspace.
            assertTrue(
                binDir.listFiles()?.none { it.name.startsWith(".ffmpeg-extract-") } ?: true,
                "the extraction workspace should be cleaned up",
            )
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `an archive without any ffmpeg binary is rejected`() {
        if (!onUnix()) return
        val root = Files.createTempDirectory("extract-tar-empty").toFile()
        try {
            val payload = File(root, "unrelated")
            payload.mkdirs()
            File(payload, "readme.txt").writeText("nothing to see")

            val archive = File(root, "ffmpeg-empty.tar.xz")
            val proc = ProcessBuilder("tar", "-I", "xz -T0", "-cf", archive.absolutePath, "-C", root.absolutePath, "unrelated")
                .redirectErrorStream(true)
                .start()
            proc.inputStream.bufferedReader().readText()
            assertEquals(0, proc.waitFor())

            val binDir = File(root, "bin")
            binDir.mkdirs()

            val failed = runCatching { BinaryManager.extractTar(archive, binDir) }.exceptionOrNull()
            assertTrue(
                failed is BinaryDownloadException,
                "an archive without ffmpeg must be rejected, got: $failed",
            )
            assertTrue(
                failed.message.orEmpty().contains("No FFmpeg binary"),
                "message should explain the archive had no binary: ${failed.message}",
            )
        } finally {
            root.deleteRecursively()
        }
    }
}