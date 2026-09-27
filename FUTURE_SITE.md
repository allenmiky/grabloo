# Grabbo — Future Branding & Site Integration Guide

This guide documents **exactly which files and UI menus** consume the website, social, and donation links once your website and brand infrastructure are ready.

---

## 1. Quick Master Switch (`shared/app/build.gradle.kts`)

Because we made the UI dynamic, updating these variables in `shared/app/build.gradle.kts` will **automatically light up the corresponding buttons and menus** across the entire application without needing to write any UI code:

```kotlin
// File: shared/app/build.gradle.kts (around line 110)
buildConfigField("PROJECT_WEBSITE", provider { "https://your-grabbo-site.com" })
buildConfigField("DONATE_LINK", provider { "https://your-grabbo-site.com/donate" })
buildConfigField("TELEGRAM_CHANNEL", provider { "https://t.me/your_grabbo_channel" })
buildConfigField("TELEGRAM_GROUP", provider { "https://t.me/your_grabbo_group" })
buildConfigField("PROJECT_SOURCE_CODE", provider { "" }) // Keep empty if closed source
buildConfigField("PROJECT_TRANSLATIONS", provider { "" }) // Add Crowdin/Weblate URL when ready
```

---

## 2. Where Each Link Appears in the App & Menus

### A. Website URL (`PROJECT_WEBSITE`)
When you set `PROJECT_WEBSITE`, it automatically enables:
1. **Desktop About Page**:
   - Primary website text link under the app icon (`desktop/app/.../pages/about/AboutPage.kt:145`)
   - Earth icon button in the social row (`AboutPage.kt:194`)
2. **Android About Page**:
   - `MainWebsite` clickable link under version number (`android/app/.../pages/about/AboutPage.kt:208`)
   - Earth icon button in the social row (`AboutPage.kt:264`)
3. **Desktop & Android Help / Three-Dot Menu**:
   - "Website" item inside the `Support and Community` menu (`CommonActionFactories.kt:210`)

---

### B. Donate Link (`DONATE_LINK`)
When you set `DONATE_LINK`, it automatically enables:
1. **Desktop About Page**:
   - Heart icon "Donate" button in the left sidebar (`AboutPage.kt:132`)
2. **Android About Page**:
   - Heart icon "Donate" button (`AboutPage.kt:247`)
3. **Desktop & Android Help Menu**:
   - "Donate" menu item (`HomeComponent.kt:225`)

---

### C. Telegram Community (`TELEGRAM_CHANNEL` & `TELEGRAM_GROUP`)
When either link is set, it automatically enables:
1. **About Pages (Desktop & Android)**:
   - Speaker icon (Channel) button (`AboutPage.kt:208`)
   - Group icon (Discussion group) button (`AboutPage.kt:215`)
2. **Desktop & Android Help Menu**:
   - "Telegram" sub-menu containing Channel and Group items (`CommonActionFactories.kt:216`)

---

### D. Source Code (`PROJECT_SOURCE_CODE`)
*(Currently empty because the app is closed-source/private)*
If in the future you open-source Grabbo:
- Setting this URL will auto-enable the "View source code" button in About screens and the Help menu item.

---

## 3. Windows Installer Packaging (`desktop/app/build.gradle.kts`)

When building the Windows `.exe` installer (NSIS), update the `extraParams` map around line 205:

```kotlin
// File: desktop/app/build.gradle.kts
extraParams = mapOf(
    "native_messaging_host_binary_name" to nativeMessagingHostBinaryName,
    "cli_binary_name" to cliBinaryName,
    "app_publisher" to "Grabbo",
    "app_version_with_build" to "${getAppVersionStringForPackaging(TargetFormat.Exe)}.0",
    "source_code_url" to "", // or your public repo
    "project_website" to "https://your-grabbo-site.com",
    "copyright" to "© 2024-present Grabbo",
    "header_image_file" to project.file("resources/installer/grabbo-header-image.bmp"),
    "sidebar_image_file" to project.file("resources/installer/grabbo-sidebar-image.bmp")
)
```

---

## 4. Browser Extension Sync (`ab-download-manager-browser-integration/`)

When your website is live:
1. **`src/utils/Constants.ts`**:
   ```typescript
   const website = "https://your-grabbo-site.com"
   export default {
       packageName: "com.grabbo.app",
       defaultPort: 15151,
       website: website,
       downloadAppUrl: website + "/download",
       repository: "",
       authHeaderName: "X-Api-Key"
   }
   ```
2. **`package.json`**:
   Set `"homepage": "https://your-grabbo-site.com"`.
3. **Options UI (`OptionUi.tsx`)**:
   Will automatically render the website link in the footer once `Constants.website` is non-empty.

---

## 5. Current Rebranding Status Checklist

- [x] **App Display Name**: "Grabbo" (desktop, android strings, window titles)
- [x] **Data Directory**: Migrated to `~/.grabbo` (`VersionUtil.kt`)
- [x] **Incomplete File Extension**: Migrated to `.grabbo.part` (`IncompleteFileUtil.kt`)
- [x] **Desktop / Autostart Shortcuts**: Migrated to `com.grabbo.app.desktop` (`install.sh`, `uninstall.sh`)
- [x] **Native Messaging Bridge**: Unified under `com.grabbo.app` (app + extension)
- [x] **Installer Assets**: Renamed to `grabbo-header-image.bmp` and `grabbo-sidebar-image.bmp`
- [x] **Android Application Class**: Migrated to `GrabboApp` and synced with `AndroidManifest.xml`
- [x] **UI Dead Links Cleanup**: All blank/legacy external buttons safely hidden until configured
- [ ] **Custom Domain Live**: (e.g. `grabbo.app` — to be deployed)
- [ ] **Community Links Created**: (Telegram / X / Discord — to be added)