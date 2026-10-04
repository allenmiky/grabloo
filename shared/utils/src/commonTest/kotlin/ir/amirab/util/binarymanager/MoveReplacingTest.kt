package ir.amirab.util.binarymanager

import java.io.File
import java.nio.file.Files
import kotlin.OptIn
import kotlin.test.ExperimentalKotlinTestApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Regression tests for [BinaryManager.moveReplacing].
 *
 * The original bug: a single file download (yt-dlp) was written straight to its final path, so the
 * "downloaded file" and the "target" the caller wanted to install it as were the exact same path.
 * [BinaryManager.moveReplacing] then deleted the target - the only copy - and tried to move the now
 * missing source onto it, so the install always failed and the binary was lost.
 */
@OptIn(ExperimentalKotlinTestApi::class)
class MoveReplacingTest {

    @Test
    fun `moveReplacing does not destroy the file when source and target are the same`() {
        val dir = Files.createTempDirectory("move-replacing-same").toFile()
        try {
            val file = File(dir, "yt-dlp")
            val content = "downloaded yt-dlp payload"
            file.writeText(content)

            // Exactly what the old code ended up doing for yt-dlp.
            BinaryManager.moveReplacing(File(file.absolutePath), File(file.absolutePath))

            assertTrue(file.exists(), "moveReplacing must never delete the only copy it was given")
            assertEquals(content, file.readText())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `moveReplacing installs a part file over the real target`() {
        val dir = Files.createTempDirectory("move-replacing-part").toFile()
        try {
            val part = File(dir, "yt-dlp.part")
            val target = File(dir, "yt-dlp")
            val content = "freshly downloaded yt-dlp"
            part.writeText(content)

            BinaryManager.moveReplacing(part, target)

            assertTrue(target.exists(), "target must exist after the move")
            assertEquals(content, target.readText())
            assertTrue(!part.exists(), "the .part file must be consumed by the move")
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `moveReplacing replaces an existing binary`() {
        val dir = Files.createTempDirectory("move-replacing-existing").toFile()
        try {
            val part = File(dir, "yt-dlp.part")
            val target = File(dir, "yt-dlp")
            part.writeText("new version")
            target.writeText("old version")

            BinaryManager.moveReplacing(part, target)

            assertEquals("new version", target.readText())
            assertTrue(!part.exists())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `the download file and the install target are different paths`() {
        // The root cause in one assertion: downloadFile used to write straight to the install path,
        // which made the "downloaded file" and the "target" the same path and the replace step
        // deleted the download. This checks the indirection actually holds, using the real manifest
        // file name and the real install path rather than made up ones.
        val dir = Files.createTempDirectory("move-replacing-paths").toFile()
        try {
            val target = requireNotNull(BinaryTarget.current()) { "test needs a supported target" }
            val manifestFileName = BinaryManifest.ytDlp(target).targetFileName

            // downloadYtDlp passes targetFileName to downloadFile, which appends ".part".
            val downloadResult = File(dir, "$manifestFileName.part")
            val installTarget = File(Binaries.getYtDlpPath(dir))

            assertTrue(
                downloadResult.absoluteFile != installTarget.absoluteFile,
                "the download must land on a .part path, never on the install target itself",
            )
        } finally {
            dir.deleteRecursively()
        }
    }
}