# Baton Privacy Policy
**Date:** 2026-08-21
**Build:** v1.8.0
**Status:** Internal R&D / pre-deployment

This is the privacy policy for Baton v1.8.0. The build is private R&D, not yet deployed to any user, and not yet on any app store. This document is the policy that will be published with the first public build (Play Store / open source) — the wording is final, only the contact details will be filled in at release time.

---

## What Baton is

Baton ("Kaavalan note") is a private note + instruction tracker for IPS officers. The v1.5.0+ build is **local-only**: every byte of your data lives on your device, in a SQLCipher-encrypted database. There is no cloud sync, no analytics, no telemetry, no crash reporting.

## Data Baton collects

**None.** Baton does not collect, transmit, or store any of your data on any server. Specifically:

- We do not collect your name, email, phone, or any PII.
- We do not collect your device identifier, advertising ID, or any device fingerprint.
- We do not collect your location.
- We do not collect your contacts, calendar, microphone, or camera input (the camera is used only to capture photos you explicitly take inside the app, and the photos stay on your device).
- We do not run analytics, crash reporting, or any third-party SDK that transmits data off-device.

## Data Baton stores on your device

- People you add (name, designation, station, phone, tier, cadence, last interaction).
- Instructions / cases you add (title, raw text, person linkage, due date, status).
- Capture notes (text, voice, photo) you create.
- Important dates (birthdays, anniversaries, court dates).
- A 24-word recovery phrase (hashed — the phrase itself is never persisted, only the SHA-256 of the words).
- A hash-chained audit log of every state change (used for legal chain-of-custody; lives entirely on your device).
- A daily local backup (plaintext JSON, in the app's private storage).

All of this is in the app's private storage, encrypted with SQLCipher (AES-256). The encryption key is held in Android's Keystore via `EncryptedSharedPreferences`. A thief who pulls the SD card cannot read the data without the Keystore-backed key.

## When data leaves your device

**In v1.5.0+: never.** There is no network call in the v1.5.0 build. The pre-v1.5.0 sync code paths are present in the binary but no-op without Supabase credentials.

If you choose to enable a future cloud-sync build, the network path will be:
- HTTPS to the configured Supabase project (hosted on a third-party cloud provider).
- The fields listed above, under "Data Baton stores on your device".
- No additional data.

The cloud-sync path is opt-in (a Settings toggle). It is off by default.

## Your rights

Because your data lives entirely on your device, "your rights" reduce to:

1. **Export.** Settings → Data → Export gives you a CSV or JSON file of the people + instructions + tags tables. The file lands in your app's private storage; you can then share it to any location you choose (Drive, SD card, another app).
2. **Backup.** Settings → Back up now runs a one-shot full-table backup (7 tables: people / instructions / tags / captures / important dates / person links / instruction tags) to the app's private storage. The file is plaintext JSON; you can move it to an encrypted location.
3. **Erase.** Settings → Erase all data wipes the local DB. This is irreversible — there is no "undo" because there is no cloud copy.
4. **Recovery.** Settings → Privacy → Recovery phrase displays your 24-word recovery phrase (with hold-to-reveal). The phrase is the only way to recover a forgotten passphrase. **If you lose the phrase and forget the passphrase, your data is irrecoverable.** Write the phrase down on paper and store the paper somewhere physically safe.
5. **Vault mode.** Settings → Privacy → Vault mode is a behavioural toggle that hides the rows you mark "hidden" from every list / search / detail screen. This is a UX affordance, not a cryptographic guarantee (the rows are still on disk, just filtered out of the UI). See `docs/threat-model.md` for the threat model.

## Children

Baton is not intended for use by children under 18. The build is a tool for IPS officers; we do not knowingly collect data from minors. If you are under 18, do not use Baton.

## Changes to this policy

This is the first published version. Any change will:
- Be reflected in a new release with a "Privacy policy updated" note in the release notes.
- Be dated at the top of this document.
- Be communicated to the user in the app (a Settings → About banner) for at least one release before the change takes effect.

For the v1.5.0+ local-only build, the only material change that could happen in the future is the cloud-sync toggle becoming available. If and when that happens, this document will be updated to spell out the new data flow.

## Contact

This is a private R&D build. The contact details will be added before any public release.

- **Email:** (to be added)
- **Project URL:** (to be added)
- **Source code:** (to be added if open-sourced)

For the v1.5.0+ local-only build, there is no data processor to contact because no data leaves the device. The contact channel exists for the future cloud-sync build.

---

This document is the authoritative privacy policy for Baton. It is published with the first public build and updated only with a release note.
