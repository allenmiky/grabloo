# Grabbo — Future Site Configuration

This file documents all URLs/handles needed for the Grabbo rebrand. Replace placeholders when your new infrastructure is ready.

---

## Primary Domain

| Purpose | Placeholder | Actual Value (TODO) |
|---------|-------------|---------------------|
| Main website | `https://grabbo.app` | |
| Download page | `https://grabbo.app/download` | |
| Donate page | `https://grabbo.app/donate` | |
| API/docs | `https://grabbo.app/api` | |

---

## Social / Community

| Platform | Placeholder | Actual Value (TODO) |
|----------|-------------|---------------------|
| Telegram Channel | `https://t.me/grabbo` | |
| Telegram Group | `https://t.me/grabbo_discussion` | |
| Twitter/X | `https://x.com/grabbo_app` | |
| GitHub Discussions | `https://github.com/amir1376/ab-download-manager/discussions` | (keep current) |
| Matrix/Discord | `https://matrix.to/#/#grabbo:matrix.org` | |

---

## Distribution / Store Links

| Platform | Placeholder | Actual Value (TODO) |
|----------|-------------|---------------------|
| GitHub Releases | `https://github.com/amir1376/ab-download-manager/releases` | (keep current) |
| Winget | `winget install grabbo` | (need new ID: `grabbo` or `yourname.grabbo`) |
| Scoop | `scoop install grabbo` | (need new bucket entry) |
| Homebrew | `brew install --cask grabbo` | (need new cask) |
| AUR (Arch) | `yay -S grabbo` | (need new PKGBUILD) |
| Flatpak | `flatpak install flathub app.grabbo.Grabbo` | (need new Flatpak ID) |
| Snap | `snap install grabbo` | (need new Snap ID) |

---

## Browser Extensions

| Browser | Current ID/URL | New ID/URL (TODO) | Notes |
|---------|----------------|-------------------|-------|
| Firefox (AMO) | `firefox-integration@abdownloadmanager.com` | **KEEP AS-IS** | Changing breaks existing installs |
| Chrome Web Store | `bbobopahenonfdgjgaleledndnnfhooj` | **KEEP AS-IS** | Changing breaks existing installs |
| Edge Add-ons | (same as Chrome) | **KEEP AS-IS** | |
| Firefox listing URL | `https://addons.mozilla.org/firefox/addon/ab-download-manager/` | Update when you publish new version | |
| Chrome listing URL | `https://chromewebstore.google.com/detail/grabbo/...` | Update when you publish new version | |

---

## Translation / Localization

| Platform | Current | New (TODO) |
|----------|---------|------------|
| Crowdin Project | `ab-download-manager` | Migrate or rename project |
| Crowdin URL | `https://crowdin.com/project/ab-download-manager` | |

---

## GitHub / Code Hosting

| Item | Current | New (TODO) |
|------|---------|------------|
| Main repo | `https://github.com/amir1376/ab-download-manager` | (keep — git history) |
| Extension repo | `https://github.com/amir1376/ab-download-manager-browser-integration` | (keep) |
| Website repo | `https://github.com/amir1376/ab-download-manager-website` | Rename to `grabbo-website` |
| Issue tracker | GitHub Issues | (keep) |
| Wiki | `https://github.com/amir1376/ab-download-manager/wiki` | (keep) |

---

## Installer / Packaging Metadata

| Field | Placeholder |
|-------|-------------|
| App name (display) | `Grabbo` |
| App name (binary) | `Grabbo` / `Grabbo.exe` / `Grabbo.app` |
| CLI binary | `GrabboCli` |
| Native messaging host | `GrabboNativeMessagingHost` |
| Data directory | `~/.grabbo` (currently `~/.abdm`) |
| Windows installer header image | `resources/installer/grabbo-header.bmp` |
| Windows installer sidebar image | `resources/installer/grabbo-sidebar.bmp` |
| macOS DMG background | `resources/installer/grabbo-dmg-background.png` |
| NSIS script template | `resources/installer/nsis-script-template.nsi` (update strings inside) |
| Copyright | `© 2024-present Grabbo` |
| Publisher/Vendor | `Grabbo` |

---

## Badge / Shield URLs (for README.md)

```markdown
<!-- Release badge -->
![GitHub Release](https://img.shields.io/github/v/release/amir1376/ab-download-manager?color=greenlight&label=latest%20release)

<!-- Website badge (update domain) -->
![Website](https://img.shields.io/badge/website-grabbo.app-purple?labelColor=gray)

<!-- Telegram badges (update handles) -->
![Telegram Channel](https://img.shields.io/badge/Telegram-Channel-blue?logo=telegram&labelColor=gray)
![Telegram Group](https://img.shields.io/badge/Telegram-Group-blue?logo=telegram&labelColor=gray)

<!-- Crowdin badge (update project) -->
![Crowdin](https://badges.crowdin.net/grabbo/localized.svg)

<!-- Extension badges (update when you publish new listings) -->
![Firefox](https://img.shields.io/amo/v/grabbo?label=Firefox&logo=firefoxbrowser)
![Chrome](https://img.shields.io/chrome-web-store/v/NEW_CHROME_ID?label=Chrome&logo=googlechrome)
```

---

## Files That Reference These URLs (Auto-Update Targets)

When you fill in the actual values above, update these files:

### Main Repo
- `README.md` — all links, badges, install commands
- `DONATE.md` — donate URL
- `.github/FUNDING.yml` — custom funding URL
- `.github/workflows/winget.yml` — identifier (when you have new Winget ID)

### Extension Repo (`ab-download-manager-browser-integration/`)
- `README.md` — install link, repo table
- `package.json` — `homepage`, `repository`, `displayName`, `description`
- `src/utils/Constants.ts` — `website`, `downloadAppUrl`, `packageName`
- `public/_locales/en/messages.json` — all user-visible strings

### Shared Config (Build-Time)
- `shared/app/build.gradle.kts` — `PROJECT_WEBSITE`, `DONATE_LINK`, `PROJECT_TRANSLATIONS`, `TELEGRAM_*`, `INTEGRATION_*`, `PROJECT_GITHUB_*`

---

## Migration Checklist

- [ ] Register domain (e.g., `grabbo.app`)
- [ ] Deploy website
- [ ] Create Telegram channel/group
- [ ] Publish Firefox extension (new listing or update existing)
- [ ] Publish Chrome extension (new listing or update existing)
- [ ] Submit Winget package under new ID
- [ ] Submit Homebrew cask
- [ ] Submit Scoop bucket entry
- [ ] Create Flatpak/Snap/AUR packages
- [ ] Migrate/rename Crowdin project
- [ ] Update all `.md` files with real URLs
- [ ] Update `shared/app/build.gradle.kts` BuildConfig values
- [ ] Rebuild and release

---

## Notes

- **Extension IDs** (`firefox-integration@abdownloadmanager.com`, Chrome extension ID) — **DO NOT CHANGE**. These are tied to existing user installs. Create new listings if needed, but keep old IDs for auto-update continuity.
- **GitHub repo URLs** — Keep as-is. They're part of git history. You can add redirect repos later if you rename.
- **Internal package names** (`com.abdownloadmanager.*`, `ir.amirab.*`) — Intentionally unchanged for upstream merge compatibility.