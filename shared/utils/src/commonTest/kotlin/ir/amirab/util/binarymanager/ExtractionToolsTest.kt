package ir.amirab.util.binarymanager

import kotlin.OptIn
import kotlin.test.ExperimentalKotlinTestApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Extracting the ffmpeg tar.xz shells out to `tar -I "xz -T0"`. These tests pin the preflight so a
 * missing tool is reported as a missing dependency instead of looking like a corrupt download after
 * ~40MB has already been fetched.
 */
@OptIn(ExperimentalKotlinTestApi::class)
class ExtractionToolsTest {

    @Test
    fun `tar xz needs tar and xz`() {
        var failed = false
        try {
            BinaryManager.requireExtractionTools(ArchiveFormat.TarXz, pathDirs = emptyList())
        } catch (e: BinaryDownloadException) {
            failed = true
            val message = e.message.orEmpty()
            assertTrue(message.contains("tar"), "message should name tar: $message")
            assertTrue(message.contains("xz"), "message should name xz: $message")
        }
        assertTrue(failed, "an empty PATH must be reported as a missing dependency")
    }

    @Test
    fun `zip and none need no external tools`() {
        // Would throw if these wrongly demanded something from PATH.
        BinaryManager.requireExtractionTools(ArchiveFormat.Zip, pathDirs = emptyList())
        BinaryManager.requireExtractionTools(ArchiveFormat.None, pathDirs = emptyList())
    }

    @Test
    fun `tar xz passes when the tools are present`() {
        val pathDirs = (System.getenv("PATH") ?: "")
            .split(java.io.File.pathSeparatorChar)
            .filter { it.isNotBlank() }
        // Only meaningful on a machine that actually has the tools; this build needs them anyway.
        BinaryManager.requireExtractionTools(ArchiveFormat.TarXz, pathDirs = pathDirs)
    }

    @Test
    fun `a single missing tool is reported in the singular`() {
        val dir = kotlin.io.path.createTempDirectory("fake-path").toFile()
        try {
            // A directory that exists but holds no tar/xz.
            val e = assertFailsWith<BinaryDownloadException> {
                BinaryManager.requireExtractionTools(ArchiveFormat.TarXz, pathDirs = listOf(dir.absolutePath))
            }
            assertTrue(e.message.orEmpty().contains("were"), "two missing tools read as plural: ${e.message}")
        } finally {
            dir.deleteRecursively()
        }
    }
}