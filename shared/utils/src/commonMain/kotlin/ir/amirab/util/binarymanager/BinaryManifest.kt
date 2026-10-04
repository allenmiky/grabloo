package ir.amirab.util.binarymanager

import ir.amirab.util.platform.Arch
import ir.amirab.util.platform.Platform
import ir.amirab.util.platform.Platform.Desktop

/**
 * Platform/arch pair a binary has to be resolved for.
 * It is kept separate from [Platform]/[Arch] so the manifest can be resolved (and tested)
 * for any target without depending on the machine the tests run on.
 */
enum class BinaryOs { Linux, Windows, MacOS }

enum class BinaryArch { X64, Arm64 }

data class BinaryTarget(val os: BinaryOs, val arch: BinaryArch) {
    val isWindows: Boolean get() = os == BinaryOs.Windows

    companion object {
        /** null when the current platform has no binaries (android) or an unsupported arch */
        fun current(): BinaryTarget? {
            val os = when (Platform.getCurrentPlatform()) {
                is Desktop.Windows -> BinaryOs.Windows
                is Desktop.Linux -> BinaryOs.Linux
                is Desktop.MacOS -> BinaryOs.MacOS
                else -> return null
            }
            val arch = when (Arch.getCurrentArch()) {
                Arch.X64 -> BinaryArch.X64
                Arch.Arm64 -> BinaryArch.Arm64
                else -> return null
            }
            return BinaryTarget(os, arch)
        }
    }
}

enum class ArchiveFormat { None, TarXz, Zip }

/**
 * One immutable, pinned location a binary can be fetched from.
 * [size] and [sha256] always travel with the url so nothing is trusted on size alone,
 * and [isGithubHosted] tells the mirror layer whether proxy prefixes may be applied.
 */
data class BinarySource(
    val url: String,
    val size: Long,
    val sha256: String,
    val version: String = "",
    val isGithubHosted: Boolean = true,
) {
    val isUsable: Boolean get() = sha256.isNotBlank() && size > 0
}

/**
 * A binary as it is pinned in the manifest, with its sources already ordered by preference
 * (first usable source wins, the rest act as fallbacks).
 */
data class BinaryManifestEntry(
    val name: String,
    val version: String,
    val targetFileName: String,
    val archive: ArchiveFormat,
    val sources: List<BinarySource>,
)

/**
 * Pinned download manifest.
 *
 * Nothing here is resolved at runtime: the GitHub API used to be queried on every attempt,
 * which cost two round trips (~2s each) and broke whenever an upstream release renamed or
 * dropped an asset. Versions are pinned here instead, so a download either starts immediately
 * or fails loudly.
 *
 * Bumping a version:
 *  - yt-dlp: take the tag, the three asset sizes and the hashes from the release `SHA2-256SUMS`.
 *  - johnvansickle ffmpeg: `old-releases/` publishes no checksums, so download the tarball once,
 *    `sha256sum` it, and make sure the archive still contains both `ffmpeg` and `ffprobe`
 *    (`tar -tJf <file> | grep -E '/(ffmpeg|ffprobe)$'`). A source without a hash is treated as
 *    unusable and skipped, so an unverified build can never be shipped by accident.
 *  - BtbN ffmpeg: take a dated `autobuild-*` tag (never the rolling `latest`) and read
 *    sizes/hashes from the `checksums.sha256` asset of that same tag.
 */
object BinaryManifest {
    const val YT_DLP_VERSION = "2026.08.19"
    const val FFMPEG_JVS_VERSION = "6.0.1"
    const val FFMPEG_BTBN_TAG = "autobuild-2026-09-27-13-04"
    private const val FFMPEG_BTBN_BUILD = "N-126905-gb87602a63a"

    /**
     * sha256 and size of `ffmpeg-6.0.1-amd64-static.tar.xz` from the `old-releases` archive.
     *
     * While these were empty the johnvansickle source was unusable, so linux x64 used the pinned
     * BtbN build. With them filled in the johnvansickle build is tier 1 again (~41MB versus ~153MB)
     * and, more importantly, it lives under a permanent `old-releases` url. BtbN prunes old
     * `autobuild-*` tags, so pinning only BtbN meant a hard failure for every linux user without a
     * system ffmpeg once that tag was deleted. BtbN stays as the fallback.
     */
    private const val JVS_AMD64_SHA256 = "28268bf402f1083833ea269331587f60a242848880073be8016501d864bd07a5"
    private const val JVS_AMD64_SIZE = 41_164_188L

    private fun executableName(base: String, target: BinaryTarget): String =
        if (target.isWindows) "$base.exe" else base

    private fun ytDlpAssetName(target: BinaryTarget): String = when (target.os) {
        BinaryOs.Linux -> "yt-dlp_linux"
        BinaryOs.Windows -> "yt-dlp.exe"
        BinaryOs.MacOS -> "yt-dlp_macos"
    }

    private fun ytDlpReleaseUrl(assetName: String): String =
        "https://github.com/yt-dlp/yt-dlp/releases/download/$YT_DLP_VERSION/$assetName"

    private fun jvsUrl(target: BinaryTarget): String {
        val arch = if (target.arch == BinaryArch.Arm64) "arm64" else "amd64"
        return "https://johnvansickle.com/ffmpeg/old-releases/" +
            "ffmpeg-$FFMPEG_JVS_VERSION-$arch-static.tar.xz"
    }

    private fun btbnUrl(assetName: String): String =
        "https://github.com/BtbN/FFmpeg-Builds/releases/download/$FFMPEG_BTBN_TAG/$assetName"

    private fun btbnAssetName(target: BinaryTarget): String {
        val arch = when (target.arch) {
            BinaryArch.X64 -> "64"
            BinaryArch.Arm64 -> "arm64"
        }
        return when (target.os) {
            BinaryOs.Linux -> "ffmpeg-$FFMPEG_BTBN_BUILD-linux$arch-gpl.tar.xz"
            BinaryOs.Windows -> "ffmpeg-$FFMPEG_BTBN_BUILD-win$arch-gpl.zip"
            BinaryOs.MacOS -> ""
        }
    }

    private fun btbnArchiveFormat(target: BinaryTarget): ArchiveFormat =
        if (target.os == BinaryOs.Windows) ArchiveFormat.Zip else ArchiveFormat.TarXz

    /** sha256 of `yt-dlp_*` assets of release [YT_DLP_VERSION] (from the release SHA2-256SUMS) */
    private fun ytDlpSha256(target: BinaryTarget): String = when (target.os) {
        BinaryOs.Linux -> "58162f9bfdc27458ea47bfcb311cf47028f17d8154a8bf7d689861d46399230a"
        BinaryOs.Windows -> "66674953fe251b89f4d08c5f0e35e0728679bd67ab3d7d05c0562af101dd3e7a"
        BinaryOs.MacOS -> "0f192b7ec147ab6288885d6351d9ab67367640029b4377576ef46dd79cf7b202"
    }

    /** sha256 of the `johnvansickle` `old-releases` archives, computed locally (no upstream checksums) */
    private fun jvsSha256(target: BinaryTarget): String = when (target.arch) {
        BinaryArch.Arm64 -> "7dbd8e2f47bd83de591b9d6ea70e67d32d9aa97e7d47ae402b60c2fe3fd4d0ab"
        // Must reference the constant, not a quoted placeholder: a quoted "JVS_AMD64_SHA256" is a
        // non blank string, so the source still looked usable and every download would then fail
        // checksum verification against a hash that was never a hash.
        BinaryArch.X64 -> JVS_AMD64_SHA256
    }

    private fun jvsSize(target: BinaryTarget): Long = when (target.arch) {
        BinaryArch.Arm64 -> 18_994_172L
        BinaryArch.X64 -> JVS_AMD64_SIZE
    }

    private fun btbnSha256(target: BinaryTarget): String = when (target.os) {
        BinaryOs.Linux -> when (target.arch) {
            BinaryArch.X64 -> "731a9a0616c6bf4f97858038b53ae6f959fc2e2c7fc40b5d535cc96d6ef93055"
            BinaryArch.Arm64 -> "5d2b61633a64bbb7033a6a9bb13da5a790493e990ea0cd985dc9fa2e51559fde"
        }

        BinaryOs.Windows -> when (target.arch) {
            BinaryArch.X64 -> "7915e450f56f9abaeb7c3120086cea6b893d53340a1bc930977e93d31efd50ba"
            BinaryArch.Arm64 -> "fef404c0539623ea32f50092b1162119f6bd503e52b6f3f875b4fe68a80c76b2"
        }

        BinaryOs.MacOS -> ""
    }

    private fun btbnSize(target: BinaryTarget): Long = when (target.os) {
        BinaryOs.Linux -> when (target.arch) {
            BinaryArch.X64 -> 152_936_700L
            BinaryArch.Arm64 -> 128_916_488L
        }

        BinaryOs.Windows -> when (target.arch) {
            BinaryArch.X64 -> 196_021_280L
            BinaryArch.Arm64 -> 137_193_697L
        }

        BinaryOs.MacOS -> 0L
    }

    fun ytDlp(target: BinaryTarget): BinaryManifestEntry {
        val assetName = ytDlpAssetName(target)
        return BinaryManifestEntry(
            name = Binaries.YT_DLP,
            version = YT_DLP_VERSION,
            targetFileName = executableName(Binaries.YT_DLP, target),
            archive = ArchiveFormat.None,
            sources = listOf(
                BinarySource(
                    url = ytDlpReleaseUrl(assetName),
                    size = ytDlpSize(target),
                    sha256 = ytDlpSha256(target),
                    version = YT_DLP_VERSION,
                )
            ),
        )
    }

    private fun ytDlpSize(target: BinaryTarget): Long = when (target.os) {
        BinaryOs.Linux -> 40_446_224L
        BinaryOs.Windows -> 17_840_399L
        BinaryOs.MacOS -> 37_146_048L
    }

    /**
     * ffmpeg is resolved in tiers:
     *  - tier 1 (linux only): the 19/42MB pinned johnvansickle static builds, ~3.5x smaller than BtbN.
     *  - tier 2: the pinned BtbN build, which is the only option on windows.
     *  - macos has no tier: BtbN dropped its macos assets and no verified static macos build is
     *    pinned here, so macos relies on the system ffmpeg tier and reports a clear error otherwise.
     */
    fun ffmpeg(target: BinaryTarget): BinaryManifestEntry? {
        val sources = buildList {
            if (target.os == BinaryOs.Linux) {
                add(
                    BinarySource(
                        url = jvsUrl(target),
                        size = jvsSize(target),
                        sha256 = jvsSha256(target),
                        version = FFMPEG_JVS_VERSION,
                        isGithubHosted = false,
                    )
                )
            }
            val btbnAsset = btbnAssetName(target)
            if (btbnAsset.isNotEmpty()) {
                add(
                    BinarySource(
                        url = btbnUrl(btbnAsset),
                        size = btbnSize(target),
                        sha256 = btbnSha256(target),
                        version = FFMPEG_BTBN_TAG,
                    )
                )
            }
        }
        if (sources.none { it.isUsable }) return null
        val usable = sources.filter { it.isUsable }
        return BinaryManifestEntry(
            name = Binaries.FFMPEG,
            // The label has to describe the build that actually gets fetched. On linux the
            // johnvansickle source is listed but unusable (its sha256 is still empty), so tier 2
            // (BtbN) is what wins, and hardcoding the johnvansickle version here used to mislabel
            // the installed binary.
            version = usable.first().version,
            targetFileName = executableName(Binaries.FFMPEG, target),
            archive = btbnArchiveFormat(target),
            sources = usable,
        )
    }

    fun entry(binaryName: String, target: BinaryTarget): BinaryManifestEntry? = when (binaryName) {
        Binaries.YT_DLP -> ytDlp(target)
        Binaries.FFMPEG -> ffmpeg(target)
        else -> null
    }
}
