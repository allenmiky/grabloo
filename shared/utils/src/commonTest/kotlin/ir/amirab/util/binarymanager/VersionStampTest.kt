package ir.amirab.util.binarymanager

import java.io.File
import java.nio.file.Files
import kotlin.OptIn
import kotlin.test.ExperimentalKotlinTestApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for version-aware binary availability.
 *
 * Previously [Binaries.isAvailable] only checked "file exists and is executable", so a binary that
 * had been installed once was considered available forever. Bumping a version in [BinaryManifest]
 * then had no effect on anyone who already had the app installed.
 */
@OptIn(ExperimentalKotlinTestApi::class)
class VersionStampTest {

    private fun pinnedYtDlpVersion(): String {
        val target = requireNotNull(BinaryTarget.current()) { "test needs a supported target" }
        return BinaryManifest.ytDlp(target).version
    }

    private fun installFakeYtDlp(binDir: File) {
        binDir.mkdirs()
        val ytDlp = File(Binaries.getYtDlpPath(binDir))
        ytDlp.writeText("not a real yt-dlp, just a stand-in")
        assertTrue(ytDlp.setExecutable(true, false))
    }

    @Test
    fun `version stamp roundtrips`() {
        val dir = Files.createTempDirectory("version-stamp").toFile().resolve("bin")
        try {
            assertEquals(null, Binaries.readVersionStamp(dir, Binaries.YT_DLP))
            Binaries.writeVersionStamp(dir, Binaries.YT_DLP, "2026.08.19")
            assertEquals("2026.08.19", Binaries.readVersionStamp(dir, Binaries.YT_DLP))
        } finally {
            dir.parentFile.deleteRecursively()
        }
    }

    @Test
    fun `a binary without a stamp counts as missing even when it is on disk`() {
        val dir = Files.createTempDirectory("version-stamp-unstamped").toFile().resolve("bin")
        try {
            installFakeYtDlp(dir)

            // The file is present and executable, so the old existence-only check would have been
            // satisfied and yt-dlp would never have been refreshed after a version bump.
            assertTrue(Binaries.isAvailable(Binaries.getYtDlpPath(dir)))
            assertTrue(
                Binaries.getMissing(dir).contains(Binaries.YT_DLP),
                "an unstamped binary must be reported missing so it gets refreshed once",
            )
        } finally {
            dir.parentFile.deleteRecursively()
        }
    }

    @Test
    fun `a binary stamped with the pinned version counts as available`() {
        val dir = Files.createTempDirectory("version-stamp-current").toFile().resolve("bin")
        try {
            installFakeYtDlp(dir)
            Binaries.writeVersionStamp(dir, Binaries.YT_DLP, pinnedYtDlpVersion())

            assertFalse(
                Binaries.getMissing(dir).contains(Binaries.YT_DLP),
                "a binary matching the pinned version must not be reported missing",
            )
        } finally {
            dir.parentFile.deleteRecursively()
        }
    }

    @Test
    fun `a binary stamped with an older version counts as missing`() {
        val dir = Files.createTempDirectory("version-stamp-stale").toFile().resolve("bin")
        try {
            installFakeYtDlp(dir)
            Binaries.writeVersionStamp(dir, Binaries.YT_DLP, "1999.01.01")

            assertTrue(
                Binaries.getMissing(dir).contains(Binaries.YT_DLP),
                "a stale binary must be reported missing so the new version is downloaded",
            )
        } finally {
            dir.parentFile.deleteRecursively()
        }
    }

    @Test
    fun `manifest entry version describes the source that is actually usable`() {
        val target = requireNotNull(BinaryTarget.current()) { "test needs a supported target" }

        listOf(Binaries.YT_DLP, Binaries.FFMPEG).forEach { name ->
            val entry = requireNotNull(BinaryManifest.entry(name, target)) { "no entry for $name" }
            assertTrue(entry.sources.isNotEmpty(), "$name has no usable source")
            assertEquals(
                entry.sources.first().version,
                entry.version,
                "$name reports a version that does not match the source that will be downloaded",
            )
        }
    }
}