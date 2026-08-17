# v1.5.7 -- modern app icon for Kaavalan note

A small but visible release: a brand-new launcher icon that fits alongside
modern note-taking and brainstorming apps (Bear, Craft, Reflect, Notion,
Apple Notes). One-tap app-drawer upgrade, no behavioural change.

## What changed

**App icon** (`mipmap-*`, `mipmap-anydpi-v26`):

- Bold sans-serif **K** in off-white (`#F5EFE6`) on a teal-to-coral gradient
  background (`#1F4E5C` -> `#3E7882` -> `#E8967B`, 135 degrees).
- A small coral recording dot (`#FF6B5B`) tucked into the lower-right of
  the K. Subtle nod to the voice-capture entry point.
- Adaptive icon (Android 8.0 / API 26+): `<background>` gradient drawable
  + `<foreground>` transparent K+dot PNG + `<monochrome>` layer for
  Android 13+ themed icons. Legacy mipmap PNGs at mdpi / hdpi / xhdpi /
  xxhdpi / xxxhdpi as fallback for OEM launchers that mask differently.
- Rounded square variant (`ic_launcher_round.png`) for launchers that
  prefer the squircle silhouette.
- High-contrast, no text, no badge -- reads cleanly at 48dp and at 4dp in
  recents view. Modern minimalism, no skeuomorphic shadow.

**Files added / changed**:

- `app/src/main/res/drawable/ic_launcher_background.xml` (gradient)
- `app/src/main/res/drawable/ic_launcher_foreground.png` (432x432, RGBA)
- `app/src/main/res/drawable/ic_launcher_foreground_108.png` (108x108, RGBA)
- `app/src/main/res/mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/ic_launcher.png`
- `app/src/main/res/mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/ic_launcher_round.png`
- `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml` (adaptive + monochrome)
- `app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml`
- `app/src/main/res/values/ic_launcher_background.xml` (fallback solid colour)
- `app/build.gradle.kts` -- `versionCode` 17 -> 18, `versionName` 1.5.6 -> 1.5.7

**Removed**:

- `app/src/main/res/drawable/ic_launcher_foreground.xml` (old vector, replaced
  by the transparent-bg PNG so the gradient background shows through cleanly).

## What did NOT change

- App name: still `Kaavalan note` (user-visible) / `com.baton.app` (package id).
- Functional code: zero source changes outside the icon assets and the
  version bump. No test changes, no data-layer changes, no schema bump.
- Test count: 307/0/0/7 (unchanged -- icon assets are not unit-tested).
- On-device behaviour: every existing screen, sheet, and entry point works
  exactly as in v1.5.6.

## How it was verified

- **Visual, emulator (Pixel 6, Android 14, 1080x2400)**: captured
  app-drawer and recents-view screenshots; the K reads cleanly next to
  Gmail and Chrome with no visible banding, anti-aliasing is sharp, the
  gradient is smooth, the coral dot is positioned correctly.
- **Visual, physical phone (Motorola Signature, Android 17, 1264x2780)**:
  installed via `adb install -r` and re-captured the app drawer. Same
  clean result at higher density. See `icon-v1.5.7-phone-drawer.png` /
  `icon-v1.5.7-phone-recents.png`.
- **Adaptive icon sanity**: confirmed foreground sits inside the 72dp
  safe zone, no clipping at the 66dp circle mask on Android 13 themed
  icons, monochrome layer passes Android Studio's `Asset Studio`
  validation.
- **Round variant sanity**: confirmed `ic_launcher_round.png` displays
  correctly when the launcher applies a circular mask (Pixel launcher's
  "circle" device theme).

## Build details

- `versionCode = 18`, `versionName = "1.5.7"`
- Release APK size: ~28.3 MB (unchanged from v1.5.6 -- icon assets are < 200 KB total)
- Min SDK: 26, target SDK: 34
- Signing: release keystore (`baton-release-2026`)
- Test count: 307/0/0/7
- `gradlew.bat :app:assembleRelease --no-daemon` -- green

## Upgrade notes

Sideload over v1.5.6. No data migration, no schema bump. The icon swap
is the only user-visible change.

## Why now

User asked for a launcher icon "in par with modern note-taking /
brainstorming apps" -- Bear, Craft, Reflect, Notion, Apple Notes. The
new icon hits the same visual language: high-contrast, single bold
glyph, gradient or solid background, no text. It also matches the
"calm" adhd-friendly palette we use across the rest of the app
(off-white, deep teal, coral -- no red).

## Next

- Storage size in MB on the Settings "On this phone" row
- Theme switcher (light / dark / system), persisted in DataStore
- Morning brief notification (BriefNotifier is wired, needs the 9am
  WorkManager schedule + `SCHEDULE_EXACT_ALARM` permission)
- Encrypted vault backup (`.baton-vault` file with user-chosen passphrase,
  export/import SQLCipher DB)
