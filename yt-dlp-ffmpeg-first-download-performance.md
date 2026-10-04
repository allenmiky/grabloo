# Grabbo — yt-dlp / FFmpeg "pehli baar" download bohot slow (#yt-dlp-fast-download-issue)

## 1. Summary

Jab koi user **pehli baar** koi YouTube (ya koi bhi yt-dlp supported) link add karta hai, tab Grabbo
`yt-dlp` aur `FFmpeg` binaries khud download karta hai. Aaj ka total payload **~185 MB** hai aur wo
**sequentially, ek hi TCP connection par** download hota hai. GitHub se speed 20–225 KB/s range me
rahni hai (is machine par measure ki hui), is liye pehla download **~14 se 30+ minutes** le leta hai,
aur beech me app ka UI freeze bhi ho jata hai (extraction main thread par).

Root cause ek hi line me: **hum 146 MB ka FFmpeg build chun rahe hain, har baar GitHub API se latest
version resolve kar rahe hain, aur download single-connection + bina resume ke ho raha hai.**

Is document me poora diagnosis (measurements + code references), expected impact, aur prioritized fix
plan hai. Plan ka **sabse pehla kaam (Priority #1)** pinned manifest + chhota/system FFmpeg source hai —
wo baaki sab ka foundation bhi hai.

---

## 2. Measurements (2026-09-28, is machine se live measure ki gayi)

| Cheez | Value | Note |
|---|---|---|
| `yt-dlp_linux` (GitHub latest, tag `2026.08.19`) | **38.6 MB** | PyInstaller binary, ismein se chhota koi official static variant nahi |
| `ffmpeg-master-latest-linux64-gpl.tar.xz` (BtbN/FFmpeg-Builds) | **145.9 MB** | aaj jo code chunta hai |
| `ffmpeg-master-latest-linux64-lgpl.tar.xz` | 133.1 MB | still bada |
| **Total first-run payload (aaj)** | **~185 MB** | 38.6 + 145.9 |
| GitHub direct download speed | **19 KB/s – 225 KB/s** (bahut variable) | 8 MB range request, 45 s me 0.8–5.6 MB |
| 4 parallel range connections (GitHub) | aggregate **~150 KB/s** | per-connection 30–42 KB/s → **parallel ka koi fayda nahi**, bandwidth capped hai |
| Cloudflare speed baseline (same machine) | **~680 KB/s** | matlab bottleneck GitHub/CDN, local link nahi |
| johnvansickle static FFmpeg (`ffmpeg-6.0.1-amd64-static.tar.xz`) | **41.9 MB** | **3.5x chhota**, immutable `old-releases/` me pinned, static, GPL |
| `ghfast.top` mirror se wahi yt-dlp range | **336 KB/s** | GitHub direct se 1.5x–17x behtar (is machine par) |
| `gh-proxy.net` | 401 (mirror list update karna padega) | is liye built-in list dynamic rakhni hogi |
| GitHub API `BtbN/FFmpeg-Builds/releases/latest` | **95 KB**, ~2 s | sirf ek asset ka URL nikalne ke liye |
| GitHub API `yt-dlp/yt-dlp/releases/latest` | **52 KB**, ~2 s | same |

### Isi se time ka hisaab

| Scenario | yt-dlp | FFmpeg | Total (sequential) |
|---|---|---|---|
| 225 KB/s (best case measured) | 2.9 min | 10.8 min | **~13.7 min** |
| 100 KB/s (typical slow link) | 6.4 min | 24 min | **~30 min** |
| 20 KB/s (worst measured) | 32 min | 2 h | **~2.5 h** |

### Real-world evidence (is machine par)

- Stale partial download pada mila:
  `~/.grabbo/system/downloadData/bin/yt-dlp_linux` = **6,000,075 bytes** (expected 38.6 MB).
  Aaj ka code resume nahi karta, is liye agli baar ye **zero se** dobara download hoga. Yehi
  "no resume" ka real proof hai.
- Installed app ka size: `/opt/Grabbo` = **176 MB** (mostly bundled JRE).

---

## 3. Pehli baar ke fetch ka timeline (code walk)

Ye poora sequence **ek hi coroutine** par, **sequentially** chalta hai:

| # | Step | Code | Note |
|---|---|---|---|
| 1 | Missing binaries check | `Binaries.getMissing(binDir)` — `shared/utils/.../binarymanager/Binaries.kt:47-53` | `file.exists() && file.canExecute()` |
| 2 | GitHub API → yt-dlp latest | `BinaryManager.getYtDlpInfo()` — `shared/utils/.../binarymanager/BinaryManager.kt:46-55` → `fetchLatestRelease` `:298-312` | 52 KB JSON, no timeout, no cache, **unauthenticated** (60 req/hr/IP) |
| 3 | yt-dlp download (38.6 MB) | `BinaryManager.downloadYtDlp` `:69-98` → `downloadFile` `:130-196` | single `OkHttpClient()` (`:44`), single `execute()` (`:142`), 8 KB buffer, 500 ms progress tick |
| 4 | size verify + move + chmod | `BinaryManager.kt:80-87` | sirf **size** check, sha256 nahi |
| 5 | GitHub API → FFmpeg latest | `BinaryManager.getFFmpegInfo()` `:57-67` → `fetchLatestRelease` | 95 KB JSON, doosra round trip |
| 6 | FFmpeg download (145.9 MB) | `BinaryManager.downloadFFmpeg` `:100-128` → `downloadFile` | wahi single-connection path |
| 7 | "100% complete" emit | `BinaryManager.kt:112-120` | **extract hone se PEHLE** hi 100% bhej diya jaata hai |
| 8 | extract (tar -xf, ~30–90 s) | `BinaryManager.kt:121-125` → `extractTar` `:227-259` | **`Dispatchers.Main` par blocking call → UI freeze** (`downloadFFmpeg` suspend hai par `withContext(IO)` sirf `downloadFile` ke andar hai) |
| 9 | arch walk + move + chmod | `BinaryManager.kt:244-258`, `makeExecutable` `:281-285` | Main thread par |
| 10 | dialog close, download resume | `DesktopAddSingleDownloadComponent.kt:60-72`, `:111-122` | user ka download **poora blocked** hai jab tak ye sab na ho |

Download trigger sirf tab hota hai jab user yt-dlp link add karta hai (ya retry) — startup par kuch
nahi hota (`App.kt` / `AppComponent` me koi fetch trigger nahi). Gate: `awaitRequiredBinaries()`
`DesktopAddSingleDownloadComponent.kt:111-122`; dialog: `DesktopBinaryDownloadComponent.kt:30-32`.

Binaries ka real path `~/.grabbo/system/downloadData/bin/` hai (`DefinedPaths.kt:30` +
`AppInfo.kt:73-76`), aur **portable install** me `<installationFolder>/.grabbo/system/downloadData/bin/`
(`PortableUtil.kt:23-24`).

---

## 4. Root causes

1. **Sequential downloads** — `shared/app/.../binarymanager/BinaryDownloadService.kt:49-54`: do alag
   `if` block, ek coroutine, saath hi global `Mutex` (`:23`). Chhota `yt-dlp` pehle poora download
   hota hai, phir 146 MB FFmpeg. Wall clock = sum, max nahi.
2. **Single connection** — `BinaryManager.kt:130-196` me app ka apna segmented downloader use nahi
   hota. Lekin **measurement yehi kehti hai ki segments se speed nahi aayegi** (4 parallel connections
   ka aggregate 150 KB/s, single ka 225 KB/s — GitHub per-IP bandwidth limit). Is liye multi-connection
   sirf **optional** hai; asli zaroorat **resume + `.part`** hai (interruption par zero se restart na ho).
3. **Koi resume nahi, interruption par pura loss** — `BinaryManager.kt:191` error par partial file
   delete kar deta hai, `.part`/Range/`If-Range` kuch nahi. 146 MB ka download 90% par ruk gaya to
   user zero se dobara download karega.
4. **Galat (sabse bada) asset chuna ja raha hai** — `BinaryManager.kt:57-67` hamesha
   `*-linux64-gpl.tar.xz` (145.9 MB) leti hai. 42 MB ka static build, ya system par installed
   `ffmpeg` (0 bytes) — dono available hain.
5. **Har attempt par 2× GitHub API call** — `BinaryManager.kt:298-312`. Sirf ek asset URL/size chahiye,
   iske liye 95 KB + 52 KB JSON. Koi pinning, cache, ya timeout nahi; unauthenticated rate limit
   (60/hr) par failure ka real chance hai, aur slow/blocked networks par 10 s+ connect timeout.
6. **Extraction UI thread par + poori archive extract** — `BinaryManager.kt:122` (call site) →
   `:227-259`. Sirf `ffmpeg`/`ffprobe` chahiye, lekin poora tarball (docs, man pages, examples)
   extract hota hai, aur `xz` single-threaded hai. `tar -I 'xz -T0'` + `--wildcards` se fix hota hai.
7. **Koi integrity check nahi (sha256)** — `:80` sirf yt-dlp ka size check karta hai; FFmpeg ka koi
   check nahi, aur sha256 kabhi nahi. Archive fail ho to `:124` use delete kar deta hai → 146 MB
   dobara download.
8. **UX + observability** — `BinaryDownloadPage.kt:100-104` ek hi `BinaryDownloadProgress?` dikhata
   hai (dono binaries ek saath hone ka model hi nahi hai), cancel button nahi (`:84-88` close sirf
   `isComplete` par), ETA nahi, phase nahi, 100% extract se pehle dikhta hai. Aur logging **release
   builds me bilkul nahi** hoti — `EntrypointInitializer.kt:35-42` log file path sirf
   `isInDebugMode()` me deta hai, matlab production me issue reproduce hi nahi ho sakta.

### 4.1 🔴 Live bug: macOS pe FFmpeg download hi nahi ho sakta (aaj)

BtbN/FFmpeg-Builds ke current `latest` release me **49 assets hain aur unme ZERO macOS assets hain**
(sirf `linux64`, `linuxarm64`, `win64`, `winarm64` — GPL/LGPL/shared/versioned). Lekin code
`BinaryManager.kt:63` macOS ke liye `ffmpeg-master-latest-macos64-gpl.tar.xz` /
`ffmpeg-master-latest-macosarm64-gpl.tar.xz` maangta hai, aur `toBinaryInfo` (`:288-289`) asset na
milne par throw karta hai:

```
Could not find ffmpeg-master-latest-macos64-gpl.tar.xz in the latest ffmpeg release
```

Matlab **macOS pe yt-dlp downloads aaj completely broken hain** (binary fetch fail → dialog fail).
Isi tarah `yt-dlp/FFmpeg-Builds` repo me sirf Windows + Linux builds hain (9 assets), macOS ke liye
koi pinned static source nahi. Is liye macOS ka plan alag hai — neeche Phase 0.2 me dekha hai.

---

## 5. Expected impact

| | Aaj | Plan ke baad |
|---|---|---|
| First-run payload (Linux, ffmpeg installed nahi) | ~185 MB | **~80 MB** (41.9 MB ffmpeg + 38.6 MB yt-dlp) |
| First-run payload (system ffmpeg present) | ~185 MB | **~38.6 MB** (FFmpeg 0 bytes) |
| First-run payload (Windows) | ~185 MB | ~185 MB (wahan 42 MB static build exist nahi karta) |
| macOS | **broken** | system ffmpeg (brew) ya actionable error |
| GitHub API round trips | 2 (unauthenticated, ~2 s+ each) | 0 (pinned manifest) |
| Wall clock @ 100 KB/s, GitHub direct | ~30 min | **~13 min**, aur mirror se **2–6 min** |
| Interrupted download | zero se restart | resume + mirror failover |
| UI | 100% dikha kar freeze | phase-wise progress + cancel |

---

## 6. Plan (prioritized)

### Phase 0 — payload aur resilience (sabse zyada fayda)

**0.1 + 0.2 (Priority #1, ek hi slice me): pinned manifest + API hatao + FFmpeg source tiers + sha256 + logging + tests**

- Naya `BinarySource` model: `url`, `size`, `sha256`, `version`, `isGithubHosted` (proxy eligibility).
- Pinned manifest (repo me, code ke saath) — GitHub API **deletion**:
  - **yt-dlp: tag `2026.08.19`** (SHA-256 release ke `SHA2-256SUMS` se verified):
    - `yt-dlp.exe` → `66674953fe251b89f4d08c5f0e35e0728679bd67ab3d7d05c0562af101dd3e7a`
    - `yt-dlp_linux` → `58162f9bfdc27458ea47bfcb311cf47028f17d8154a8bf7d689861d46399230a`
    - `yt-dlp_macos` → `0f192b7ec147ab6288885d6351d9ab67367640029b4377576ef46dd79cf7b202`
  - **FFmpeg tier-1 (Linux x64/arm64):** `https://johnvansickle.com/ffmpeg/old-releases/ffmpeg-6.0.1-{amd64,arm64}-static.tar.xz`
    (41.9 MB, pinned/immutable, static GPL). `old-releases/` me koi checksum file **nahi** hai (`.md5`
    bhi 404) — is liye SHA-256 build time par file download karke nikala ja raha hai aur manifest me
    embed hoga. ⚠️ verify karna hai ke is build me **`ffprobe` maujood hai** (nahi to `getMissing` kabhi
    clear nahi hoga → infinite retry; mitigation: extract ke baad dono check, warna tier-2 pe fallback).
  - **FFmpeg tier-2 (Linux + Windows, fallback):** `BtbN/FFmpeg-Builds` pinned **dated tag**
    `autobuild-2026-09-27-13-04` — is release me official **`checksums.sha256`** asset hai, is liye
    SHA-256 verify possible hai. (`latest` tag rolling hai, is liye pin nahi kar sakte.)
  - **macOS: system-first + actionable error.** BtbN macOS assets chura chuke hain aur
    `yt-dlp/FFmpeg-Builds` me mac nahi hai. Is liye: system `ffmpeg`/`ffprobe` use karo (brew — Mac par
    common), warna clear error: *"FFmpeg nahi mila. `brew install ffmpeg` chala kar dobara try karein."*
  - **FFmpeg tier 0 (zero bytes):** system par `ffmpeg` **aur** `ffprobe` PATH me maujood ho aur
    `ffmpeg -version` ka major >= 4 ho → use karo. `binDir` me marker file likho
    (`ffmpeg-location.txt` = us system ffmpeg ka directory), taaki `Binaries.getFFmpegDir()` wahi
    return kare aur yt-dlp ko `--ffmpeg-location` (`YtDlpCli.kt:217-218`, value `YtDlpDownloadJob` se
    aati hai) usi par mile. `Binaries.getMissing()` (`:47-53`) ko ye marker consider karna hoga.
  - ⚠️ **System se sirf `ffmpeg`/`ffprobe` uthana safe hai.** `yt-dlp` kabhi system se nahi — hamesha
    pinned download `binDir` se. Wajah: `YtDlpCli` me koi version check nahi hai, purana system yt-dlp
    chupke se naye sites parse karna fail karega. System detection `Platform.Desktop` se guarded hoga
    (`Platform.Android` alag hai, `Platform.kt:7`).
- Files: naya `BinarySources.kt`, `SystemBinaries.kt`; edits `BinaryManager.kt:46-67`, `:287-312`,
  `Binaries.kt`, `commonTest` (manifest/asset selection/`getMissing`/marker logic).

**0.3 Mirrors (built-in list + settings override)**
- Model: har source ke paas apna `url`; GitHub-hosted assets built-in proxy list se try hote hain
  (johnvansickle direct — proxy prefix us par nahi lagta).
- Strategy: candidates ka chhota probe (≈256 KB) → winner se download → error par next candidate.
- Setting: `binaryDownloadMirrors` (comma/newline separated). Files: `IAppSettingsModel.kt`,
  `IAppSettingsModel.desktop.kt`, `IAppSettingsModel.android.kt`,
  `desktop/.../storage/AppSettingsStorage.kt:44-84`, `DesktopSettingsComponent.kt`/`SettingsPage.kt`,
  `en_US.properties`.

**0.4 Resume + retry + timeouts**
- `downloadFile` (`:130-196`): `.part` file + HTTP `Range` resume, error par partial file mat
  delete karo (`:191` hatao), read timeout 10 s → 60 s, 3 backoff retries + mirror failover,
  `Content-Length` verify, stale `.part` cleanup.
- Multi-connection segmented download **optional** — measurement ke mutabiq GitHub par bandwidth
  capped hai, is liye ye pehla kaam nahi; resume zaroori hai.

### Phase 1 — concurrency aur UI thread

- `BinaryDownloadService.downloadMissingBinaries` (`:43-68`): `coroutineScope { async { yt-dlp }; async { ffmpeg } }`
  — dono parallel; `Mutex` global ke bajaye per-binary. `_downloadProgress` →
  `Map<String, BinaryDownloadProgress>`.
- `extract()` ko `withContext(Dispatchers.IO)` (abhi Main par), `tar -I 'xz -T0' -xf ... --wildcards
  '*/ffmpeg' '*/ffprobe'` (4-core par 2–3x), aur extract fail par archive delete mat karo (`:124`).

### Phase 2 — background prefetch + UI

- App start (window show hone ke baad) par low-priority IO prefetch: guard `areBinariesAvailable()` +
  "already attempted" flag + battery-save check; add-download dialog me yt-dlp link detect hote hi
  trigger (`DesktopAddSingleDownloadComponent.kt:111-122`).
- `BinaryDownloadPage.kt`: per-binary rows, phase enum (resolve/download/verify/extract), ETA,
  combined progress, **Cancel** button (abhi `:84-88` close sirf `isComplete` par enabled hai),
  retry sirf failed component ka. Strings `en_US.properties:572-578`.

### Phase 3 — observability + tests

- `thisLogger()` (kermit: `shared/utils/.../logger/AppLogger.kt:44`) per-phase timing + bytes/s.
  **Release builds me bhi file log** — `FileLogWriter` me size cap/rotation, aur
  `EntrypointInitializer.kt:35-42` se `takeIf { isInDebugMode() }` hatao (release me `minSeverity = Info`).
- `commonTest`: manifest/hash resolution, platform asset selection, `getMissing` + system marker,
  parallel orchestration, resume math.

---

## 7. Priority order (kaam kis order me karna hai)

| # | Kaam | Kyun pehle | Files |
|---|---|---|---|
| **1** | **0.1 + 0.2 ek slice me**: `BinarySource` + pinned manifest + GitHub API hatao + sha256 + FFmpeg tiers (system → 42 MB → pinned BtbN) + logging + tests | Foundation **aur** sabse bada user-visible win (185 MB → 80 MB / 0 MB, 2 API round-trips gone). #2–#5 ka prerequisite bhi | naya `BinarySources.kt`, `SystemBinaries.kt`, `BinaryManager.kt:46-67`, `:287-312`, `Binaries.kt`, `YtDlpCli.kt:217-218` |
| 2 | 0.3 Mirrors | Actual network par **1.5–17x** (measured) | mirror resolver + settings files |
| 3 | 0.4 Resume/retry | 146 MB ka download bekaar na jaye | `BinaryManager.kt:130-196`, `:191` |
| 4 | Phase 1 parallel + extract IO thread | ~15% jaldi + **UI freeze bug fix** | `BinaryDownloadService.kt:43-68`, `BinaryManager.kt:122` |
| 5 | Phase 2 prefetch + UI | Wait invisible, cancel/ETA | `AppComponent`, `BinaryDownloadPage.kt` |
| 6 | **Tier −1: bundled binaries** (optional) | Offline/offline-install users ke liye 0 MB | `Binaries.kt` (`getMissing` order: `bin/` → `resources/bin` → system PATH), packaging |
| 7 | Phase 3 full logs/tests polish | Maintainability | — |

**Is document ke hisaab se pehle Priority #1 ka slice implement hoga.**

---

## 8. Verification (har phase ke baad)

1. Fresh `downloadData` (`DefinedPaths.kt:30` → `~/.grabbo/system/downloadData`) se 2 end-to-end timed
   runs, `AppLog.log` timings ke saath compare karna.
2. Slow/unstable link par resume test (interruption ke baad `.part` se continue, zero se restart nahi).
3. Installed system `ffmpeg` wale machine par **0-byte** path test (tier 0 marker).
4. Fallback test: tier 1 URL ko force-fail karke tier 2 (pinned build) par jaana verify karna.
5. `commonTest`: manifest/hash, asset selection, `getMissing`, marker logic, orchestration.
6. UI thread check: extract ke waqt app responsive rahe (thread dump / freeze detector).
7. macOS: system ffmpeg tier + actionable error message verify karna (asset hi nahi hai ab).

---

## 9. Locked decisions

| # | Decision | Choice |
|---|---|---|
| 1 | **Multi-connection engine** | **Optional, abhi nahi.** GitHub per-IP bandwidth limit lagata hai (4 parallel = 150 KB/s aggregate vs single 225 KB/s). Asli zaroorat **resume + `.part`** hai |
| 2 | **System binaries** | Sirf `ffmpeg`/`ffprobe` system se (version check ke saath). `yt-dlp` **hamesha pinned** download — version mismatch se bachne ke liye |
| 3 | **Bundled binaries (Tier −1)** | **Optional offline packaging** hi. Bundling me 3 cost hain: installer **176 MB → ~360 MB**, yt-dlp/ffmpeg update ke liye full app reinstall, aur **Apache-2.0 app me static GPL FFmpeg bundle karna licensing obligation** create karta hai |
| 4 | **macOS FFmpeg source** | **System-first + clear actionable error** (`brew install ffmpeg`). Koi unpinned/unverified macOS source introduce nahi. BtbN mac assets wapas aaye to tab add karenge |
| 5 | **JohnVanSickle SHA-256** | **41.9 MB (amd64) + ~30 MB (arm64) download karke hash embed karenge** + verify script. 41.9 MB vs 146 MB ka win real hai |
| 6 | **Logging** | **Release builds me bhi file log** (size-capped/rotation ke saath), `minSeverity = Info`; debug me `Verbose` |

---

## 10. Risks / jaan-boojh kar rakhi gayi cheezein

- **System FFmpeg version risk**: bahut purana system ffmpeg merge fail kar sakta hai — is liye
  minimum version check (major >= 4) + sirf wahi accept karna jismein `ffmpeg` **aur** `ffprobe` dono
  hon, aur wahi directory me hon.
- **JohnVanSickle build me `ffprobe`**: verify nahi hua (index page se confirm nahi hua). Agar missing
  hua to `getMissing` kabhi clear nahi hoga — is liye extract ke baad dono binaries check karke hi
  commit hoga, warna tier-2 pe fallback.
- **JohnVanSickle `old-releases/` me koi published checksum nahi** — SHA-256 hum khud download karke
  nikaal rahe hain (trust-on-first-use, community mirror ka standard practice). Ek verify script
  (`scripts/verify-binaries-manifest.sh`) saath me add hoga.
- **Mirror trust**: built-in proxies third-party hain. Unka use sirf GitHub-hosted public release
  assets ke liye, aur sha256 verify ke saath — taaki mirror se aaye content verify ho.
- **BtbN "master-latest" re-bhakta hai** — is liye master-latest pin nahi kar sakte; pinned
  `old-releases`/dated tags hi use honge (`autobuild-...`).
- **Pinned versions ka maintenance**: pinned yt-dlp/ffmpeg ko manually update karna padega — Phase 2
  me update script + alag "check for binary updates" flow (ETag, on-demand) aayega.
- **App ka apna downloader reuse nahi kar rahe** binaries ke liye: wo download list/queue me user ko
  dikhta hai aur speed-limit/queue ke saath compete karta hai. Dedicated fetcher rahega, bas wo
  resume-capable banega.
- **GitHub API ka hatna** ek behavior change hai: naye versions ab turant pick nahi honge.
- **Release logging** file size badha sakta hai — is liye `FileLogWriter` me size cap + rotation.
