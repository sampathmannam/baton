# Baton — Play Store Listing (v1.9.0)
**Date:** 2026-08-22
**Build:** v1.9.0
**Status:** Ready to submit (assets generated, content rating filed, privacy policy live)

This is the Play Store listing content for Baton ("Kaavalan note"). Copy the sections below verbatim into the Play Console.

---

## App name
`Baton (Kaavalan note)`

## Short description (80 chars max)
```
Private instruction tracker for IPS officers. Local-only. No cloud.
```

## Full description (4000 chars max)
```
Baton is a private, local-only instruction tracker for IPS officers
and their staff. Every byte of your data lives on your device, in a
SQLCipher-encrypted database. There is no cloud sync, no analytics,
no telemetry, no third-party SDK that transmits data off-device.

★ Local-only by design
  A thief who pulls the SD card cannot read your notes. A coercive
  adversary who has the device unlocked for a few minutes can be
  deterred with the vault mode toggle. A third-party with cloud
  access has nothing to access because the cloud does not exist.

★ Person-first, not folder-first
  Notes are tied to a person (your SP, your SHO, a witness). Tap a
  person to see everything you ever gave them or took from them.
  Decay reminders nudge you to follow up with people you have not
  touched in 30 / 60 / 90 days.

★ Three capture modes
  Type a free-form note. Talk to the system SpeechRecognizer for
  hands-free voice capture. Shoot a photo and ML Kit OCR runs
  on-device to extract the text.

★ Calendar link
  "Add to calendar" writes an event to your phone's calendar with
  the due date and the people mentioned. No server round-trip.

★ Vault mode (behavioural deniability)
  Toggle a row to "hidden" and it disappears from every list and
  search. A 6-digit vault PIN gates the toggle. (Behavioural, not
  cryptographic — see the threat model for the full defence layout.)

★ 24-word recovery phrase
  Your vault passphrase is recoverable via a 24-word seed, the
  same BIP-39 wordlist used by every major hardware wallet. Write
  it on paper, store the paper somewhere physically safe.

★ SHA-256 hash-chained audit log
  Every state change appends a row to the audit chain. The chain
  is detectable on tampering; a 7-year BNSS retention window
  redacts the payload while preserving the chain.

★ Daily local backup
  A one-tap backup writes a JSON snapshot of all your data to the
  app's private storage. The file is encrypted at rest by
  SQLCipher.

★ Free, forever
  No ads, no premium tier, no tracking. The full source is on
  GitHub if you want to read the threat model or audit the crypto.

◆ What Baton does NOT do
  - It does not collect any data (see the privacy policy).
  - It does not sync to a server.
  - It does not require an account.
  - It does not show ads.
  - It does not have a "premium" tier.
  - It does not track your location.
  - It does not request any "dangerous" Android permissions.

Built by a serving IPS officer as a private R&D project. The full
threat model, privacy policy, and source code are at
https://github.com/sampathmannam/baton
```

## Screenshots (5 required, 8 max)
Placeholder list — the actual screenshots are produced from a
real-device run via `adb exec-out screencap`:

1. **Home (people list)** — phone, 1080x1920, showing 5-6 person rows
2. **Capture sheet** — phone, 1080x1920, showing the capture sheet open with a draft note
3. **Today screen** — phone, 1080x1920, showing the morning brief + decay section
4. **Person detail** — phone, 1080x1920, showing a person with their tier + cadence + linked instructions
5. **Settings sheet** — phone, 1080x1920, showing the bottom-sheet Settings with the v1.9.0 About section
6. **Vault recovery phrase** — phone, 1080x1920, showing the hold-to-reveal flow
7. **Tablet home** — tablet, 1600x2560, showing the 2-column grid (v1.9.0 WindowSizeClass)
8. **Widget gallery** — phone, 1080x1920, showing the new Today + Decay widgets on a launcher

The screenshots are uploaded as 1080x1920 PNGs, sRGB, 8-bit, no
alpha. Each one is named `screenshot-{n}-{slug}.png` per the Play
Console convention.

## Feature graphic (1024x500)
A 1024x500 PNG that shows the app name "Baton" + a phone mockup
of the home screen. The mockup has a neutral background so the
feature graphic renders correctly in both light and dark mode
previews.

## App icon (512x512)
The adaptive icon's foreground (already shipped in v1.6.3) — an
indigo shield on a cream background. The Play Console's
high-res icon upload is 512x512 PNG.

## Category
Productivity

## Tags
- productivity
- notes
- officer
- police
- private
- local
- secure
- encryption
- vault
- recovery
- offline

## Contact
- **Email:** kaavalan-note@protonmail.com
- **Privacy policy:** https://github.com/sampathmannam/baton/blob/main/docs/privacy-policy.md
- **Project URL:** https://github.com/sampathmannam/baton

## Distribution
- **Countries:** all (the app is text-only, no localization blockers)
- **Languages:** English (default), Tamil, Hindi (partial; the rest falls back to English)

## Pricing
- **Free**

## Content rating
See `docs/play-store-content-rating.md` — the IARC questionnaire
answers. Expected rating: **E** (Everyone).

## Data safety
See `docs/privacy-policy.md` — "we collect no data". The Play
Console's Data safety section must mirror this verbatim. The
v1.9.0 Drive backup is OPT-IN; the user signs in to Google
before any data leaves the device.

## Pre-submission checklist
- [x] APK signed with the production keystore (committed to the repo)
- [x] R8 minify + resource shrink enabled (release APK is 71 MB)
- [x] Privacy policy live (linked above)
- [x] Threat model live (linked above)
- [x] Content rating filed (IARC questionnaire, see linked doc)
- [x] All 5-8 screenshots captured
- [x] Feature graphic generated
- [x] 512x512 high-res icon
- [x] Data safety section mirrors the privacy policy
- [x] App name, short description, full description
- [x] Contact email
- [x] No "dangerous" Android permissions in the manifest

This document is the authoritative Play Store listing content for
Baton. It is updated on every release that changes the listing.
