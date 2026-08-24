# Baton Operations Runbook (v2.0.0)

This is a short, operational guide for the user (and a future IT
admin) when something goes wrong with Baton. v2.0.0 is local-only,
so "operations" is mostly about the device + the on-device DB +
the on-device encrypted backup, not a server.

---

## "My data is gone" — recovery

### If you have a CSV/JSON export in your Downloads / Drive / SD card

1. Open Baton.
2. v2.0.0 does not yet have an import flow. The export in v1.7.x
   and v1.9.x was an **archive**, not a round-trip. (Import is a
   v2.0.1 follow-up.)
3. To re-create the data, open the CSV in any spreadsheet app
   and type the rows back in via the Baton Home → "+ New person"
   / Person detail → "+ New instruction" flow.
4. If you had photo captures, those are **not** in the CSV export.
   Only the text metadata is. The JPEGs in `filesDir/captures/`
   on the old device are not recoverable from the CSV.

### If you have a local backup in `filesDir/backups/`

1. The local backup is a daily JSON snapshot written by
   `BackupWorker`. The file is named `baton-backup-<timestamp>.json`.
2. The file is **plaintext JSON** but lives inside the
   SQLCipher-encrypted app sandbox. Copying it off-device
   decrypts it (it's plaintext). Keep it somewhere safe.
3. There is no in-app restore flow in v2.0.0. The restore is
   manual: open the JSON, copy rows into the new install by
   hand. (A future v2.0.1 will add a SAF-driven restore path.)

### If you have neither (truly gone)

1. v2.0.0 is local-only. There is no server-side backup.
2. If you used the **recovery phrase** (12 words) when you
   first set up the app, you can restore the SQLCipher-encrypted
   DB on a fresh install: install Baton → on first launch,
   choose "Restore from recovery phrase" → enter the 12 words
   in order. The recovery phrase decrypts the existing DB file
   **on the same device** (the encryption is device-bound —
   the passphrase is generated on first launch and stored in
   Keystore). A cross-device restore is a v2.x feature.
3. If you did NOT save the recovery phrase, the data is
   unrecoverable. This is the threat-model trade-off — see
   `docs/threat-model.md` §5.

---

## "The app crashes on launch"

1. Check `cacheDir/crashes/` for the latest crash log. The
   Settings → About → "Report a problem" button (if wired) or
   the OS-level crash dialog will offer to attach the log.
2. The most common cause is a corrupted SQLCipher DB (e.g. a
   force-stop during a write). The error message is usually
   "file is not a database" or "database is locked".
3. **Fix:** Settings → Data → "Erase all data" (requires
   confirmation). This wipes the local DB. Re-create your
   data from the most recent backup.
4. If the crash is in `AppInitializer.runOnAppStart()`, check
   `adb logcat | grep BatonAppInit` for the `loadLibrary(sqlcipher)`
   error. That means the native lib failed to load — the
   most common cause is a custom ROM that stripped the
   SQLCipher AAR. Reinstall the APK from the official source
   (GitHub Releases) and verify the SHA-256 fingerprint.

---

## "The app is slow / ANR"

1. The brief generator runs on every cold start. If you have
   1000+ instructions, the brief can take 2-3 seconds. This
   is the v2.0 trade-off; the `BriefGenerator` is a coroutine
   on the IO dispatcher and the UI shows a skeleton during
   the load.
2. If the brief ANRs for >5 seconds, file an issue with the
   `BriefGenerator` trace. The known slow path is
   `instructions_fts` (FTS4 index) on devices with <4 GB RAM.
3. The Quick Note widget is the lowest-latency capture path.
   2x2 widget + tap = 1.5 s to keyboard (measured on a Pixel 6).

---

## "I forgot my vault PIN"

1. The vault PIN is the 4-6 digit code that gates the
   "hidden" list. It is **not** the recovery phrase.
2. The vault PIN is a SHA-256 hash in `SecurePreferences`. It
   is not recoverable. The "Erase all data" path also clears
   the vault PIN hash.
3. There is no backdoor. A coercive adversary who can compel
   the user to unlock the device cannot extract the hidden
   rows from a behavioural vault either — see
   `docs/threat-model.md` §3.2.

---

## "I want to back up my data"

1. **Settings → Data → Export (CSV)** writes a UTF-8 CSV to
   a SAF-chosen URI. The CSV has one row per person,
   instruction, capture (text only), and tag.
2. **Settings → Data → Export (JSON)** writes a JSON snapshot
   to a SAF-chosen URI. The JSON has the same content as
   the CSV but in a structured form.
3. **Settings → Data → "Back up now"** writes a daily
   JSON snapshot to `filesDir/backups/baton-backup-<timestamp>.json`.
   This is automatic (the `BackupWorker` runs every 24 h).
4. The CSV / JSON exports are **plaintext**. The on-device
   `filesDir/backups/` is inside the SQLCipher-encrypted
   sandbox, but the moment you copy a file off-device, the
   SQLCipher protection ends. Treat the exported files like
   any other plaintext PII.

---

## "I want to migrate from v1.x"

### From v1.8.0+ to v2.0.0

1. v2.0.0 is a drop-in replacement. Your data is preserved
   through the MIGRATION_8_9 → MIGRATION_14_15 chain.
2. Install v2.0.0 over the v1.x install. The app icon and
   launcher entry are unchanged.
3. On first launch, the "What's new" entry in Settings
   shows the v2.0.0 highlights (no launch-time modal — the
   v1.6.0 design rule).

### From v1.7.x or earlier to v2.0.0

1. **First: back up.** The v2-v7 destructive Room migration
   will wipe the local DB on upgrade. Use Settings → Data →
   Export (CSV or JSON) on the old build.
2. Install v2.0.0.
3. v2.0.0 does not have an import flow (a v2.0.1 follow-up).
   The exported CSV/JSON is your archive. To re-create the
   data, type it back in by hand.

### From v2.0.0 to a future v2.0.1+

1. v2.0.0's Room schema is at v15. v2.0.1+ will add migrations
   in the MIGRATION_15_16 chain.
2. The `sync_queue` and `app_state` tables are in the schema
   for forward-compat with optional future cloud sync. A
   v2.x that re-enables cloud will use these tables without
   a schema migration.

---

## "I want to verify the APK"

1. The production keystore is unchanged since v1.9.0. The
   SHA-256 fingerprint of the v2.0.0 APK is published on the
   GitHub Release page.
2. `apksigner verify --print-certs app-arm64-v8a-release.apk`
   should print the certificate SHA-256.
3. `sha256sum app-arm64-v8a-release.apk` should match the
   fingerprint on the release page.

---

## "I want to wipe the app (e.g. before donating the device)"

1. **Settings → Data → "Erase all data"** (requires
   confirmation). This wipes the SQLCipher-encrypted DB and
   the local backups in `filesDir/backups/`.
2. The `cacheDir/crashes/` is not wiped by the in-app flow.
   To wipe the crash log too: Settings → Apps → Baton →
   Storage → Clear cache.
3. For a hard reset: factory-reset the device after the
   in-app wipe. The Keystore-backed encryption keys are
   device-scoped and are destroyed by the factory reset.

---

## Build / install (for maintainers)

```bash
# Debug build
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew :app:assembleDebug
# Test
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew :app:testDebugUnitTest
# Lint
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew :app:lintDebug
# Install on device
adb install -r app/build/outputs/apk/debug/app-arm64-v8a-debug.apk
```

## Reporting an issue

1. Settings → "Report a problem" (or the OS-level "Send
   feedback" if the app is crashing) attaches the latest
   crash log from `cacheDir/crashes/`.
2. The mailto is set in `BatonApplication.workManagerConfiguration`'s
   `ReportProblemIntent` helper. Edit the recipient before
   shipping if you're not the maintainer.
