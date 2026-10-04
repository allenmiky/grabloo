package ir.amirab.util.binarymanager

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BinaryManifestTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @Test
    fun `yt-dlp manifest entry exists for all platforms`() {
        val linuxEntry = BinaryManifest.ytDlp(BinaryTarget(BinaryOs.Linux, BinaryArch.X64))
        val windowsEntry = BinaryManifest.ytDlp(BinaryTarget(BinaryOs.Windows, BinaryArch.X64))
        val macEntry = BinaryManifest.ytDlp(BinaryTarget(BinaryOs.MacOS, BinaryArch.X64))

        assertEquals(Binaries.YT_DLP, linuxEntry.name)
        assertEquals(Binaries.YT_DLP, windowsEntry.name)
        assertEquals(Binaries.YT_DLP, macEntry.name)

        assertEquals(BinaryManifest.YT_DLP_VERSION, linuxEntry.version)
        assertTrue(linuxEntry.sources.isNotEmpty())
        assertTrue(linuxEntry.sources.firstOrNull()?.isUsable == true)
    }

    @Test
    fun `ffmpeg tier-1 exists for both linux architectures`() {
        val x64Entry = assertNotNull(BinaryManifest.ffmpeg(BinaryTarget(BinaryOs.Linux, BinaryArch.X64)))
        val arm64Entry = assertNotNull(BinaryManifest.ffmpeg(BinaryTarget(BinaryOs.Linux, BinaryArch.Arm64)))

        // Both linux architectures now have a pinned sha256 for the johnvansickle build, so tier 1 is
        // usable everywhere on linux. That matters because the tier 1 url is permanent while the
        // pinned BtbN autobuild tag can be pruned by its author.
        val x64Tier1 = assertNotNull(x64Entry.sources.firstOrNull { it.url.contains("johnvansickle") })
        assertTrue(x64Tier1.isUsable)

        val arm64Tier1 = assertNotNull(arm64Entry.sources.firstOrNull { it.url.contains("johnvansickle") })
        assertTrue(arm64Tier1.isUsable)
    }

    @Test
    fun `ffmpeg tier-2 fallback exists for all desktop platforms`() {
        assertNotNull(BinaryManifest.ffmpeg(BinaryTarget(BinaryOs.Linux, BinaryArch.X64)))
        assertNotNull(BinaryManifest.ffmpeg(BinaryTarget(BinaryOs.Linux, BinaryArch.Arm64)))
        assertNotNull(BinaryManifest.ffmpeg(BinaryTarget(BinaryOs.Windows, BinaryArch.X64)))
        assertNotNull(BinaryManifest.ffmpeg(BinaryTarget(BinaryOs.Windows, BinaryArch.Arm64)))
        // macOS has no bundled ffmpeg - returns null
        assertNull(BinaryManifest.ffmpeg(BinaryTarget(BinaryOs.MacOS, BinaryArch.X64)))
    }

    @Test
    fun `tier ordering puts the permanent johnvansickle build first on linux`() {
        val linuxArm64 = assertNotNull(BinaryManifest.ffmpeg(BinaryTarget(BinaryOs.Linux, BinaryArch.Arm64)))
        val linuxX64 = assertNotNull(BinaryManifest.ffmpeg(BinaryTarget(BinaryOs.Linux, BinaryArch.X64)))
        val winX64 = assertNotNull(BinaryManifest.ffmpeg(BinaryTarget(BinaryOs.Windows, BinaryArch.X64)))

        val arm64Tier1 = assertNotNull(linuxArm64.sources.firstOrNull { it.url.contains("johnvansickle") })
        val arm64Tier2 = assertNotNull(linuxArm64.sources.firstOrNull { it.url.contains("BtbN") })
        val x64Tier1 = assertNotNull(linuxX64.sources.firstOrNull { it.url.contains("johnvansickle") })
        val x64Tier2 = assertNotNull(linuxX64.sources.firstOrNull { it.url.contains("BtbN") })
        val winTier2 = assertNotNull(winX64.sources.firstOrNull { it.url.contains("BtbN") })

        // Both linux architectures prefer tier 1 and keep tier 2 as a fallback.
        assertEquals(arm64Tier1.url, linuxArm64.sources.first().url)
        assertEquals(x64Tier1.url, linuxX64.sources.first().url)
        // Windows has no tier 1 and only has tier 2.
        assertEquals(winTier2.url, winX64.sources.first().url)
    }

    @Test
    fun `every linux target keeps a second ffmpeg source as a fallback`() {
        // Guards the failure mode where pruning the pinned BtbN autobuild tag would otherwise leave
        // linux users without a system ffmpeg with no way to install one at all.
        listOf(BinaryArch.X64, BinaryArch.Arm64).forEach { arch ->
            val entry = assertNotNull(
                BinaryManifest.ffmpeg(BinaryTarget(BinaryOs.Linux, arch)),
                "linux $arch must resolve to some ffmpeg source",
            )
            assertTrue(
                entry.sources.size >= 2,
                "linux $arch should keep at least two usable sources, got ${entry.sources.size}",
            )
        }
    }

    @Test
    fun `all usable sources have sha256 and size`() {
        val targets = listOf(
            BinaryTarget(BinaryOs.Linux, BinaryArch.X64),
            BinaryTarget(BinaryOs.Linux, BinaryArch.Arm64),
            BinaryTarget(BinaryOs.Windows, BinaryArch.X64),
            BinaryTarget(BinaryOs.Windows, BinaryArch.Arm64),
            BinaryTarget(BinaryOs.MacOS, BinaryArch.X64),
        )

        for (target in targets) {
            // ytDlp() is total for every target; ffmpeg() is null on macOS.
            for (source in BinaryManifest.ytDlp(target).sources.filter { it.isUsable }) {
                assertTrue(source.sha256.isNotBlank(), "yt-dlp $target: missing sha256 for ${source.url}")
                assertTrue(source.size > 0, "yt-dlp $target: invalid size for ${source.url}")
            }

            val ffmpeg = BinaryManifest.ffmpeg(target) ?: continue
            for (source in ffmpeg.sources.filter { it.isUsable }) {
                assertTrue(source.sha256.isNotBlank(), "ffmpeg $target: missing sha256 for ${source.url}")
                assertTrue(source.size > 0, "ffmpeg $target: invalid size for ${source.url}")
            }
        }
    }
}
