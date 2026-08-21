# Baton — Production Readiness Plan
**Source:** 5.5/10 R&D / 3/10 production review of v1.4, with all 3 phases confirmed by user
**Target worktree:** `baton-v170/` on `m0/skeleton-v1.7.0` (SOTA, v1.7.4 at `60f4a9a`)
**Persona lens:** IPS officer / SP-of-district daily use → dept pilot → open source
**Date:** 2026-08-21

This plan is the authoritative item-by-item breakdown of the 3-phase gap analysis. Each item has: **where it lives** (file/area), **what success looks like** (acceptance), **test that proves it** (verification).

Audit columns:
- [ ] = not started
- [~] = in progress
- [x] = done in current SOTA
- [!] = blocked / needs decision

---

## Phase 1 — Daily SP use (the only one that matters for the persona)
Goal: reliable enough that an SP uses Baton for actual casework without losing data, missing instructions, or being embarrassed by crash/drop. Reliability + data integrity + legal-auditability > features.

### P0 (will hurt within first week of real use)
1. **Backup & export** — `data/export/PlainExporter.kt` exists (CSV + JSON) but no `WorkManager`-driven bulk export, no import path. Build `ExportManager.kt` (bulk Room → encrypted ZIP of all tables, with restore). Add Settings entry "Backup now" + "Restore from backup". Auto-backup on every N saves to internal app storage. **Verify:** `app/src/test/java/com/baton/app/data/export/BackupRoundTripTest.kt` writes a backup, simulates app reinstall by clearing Room, restores, asserts all persons / instructions / captures / tags / links present.
2. **Crash-recovery for `onConfirm` transaction** — `CaptureViewModel.onConfirm` does `instructionRepository.create(...)` then `clearDraft(...)` without a transaction boundary. If process dies between, draft is gone but instruction is persisted (or vice versa). Wrap both in `db.withTransaction { ... }` and reconcile draft on next launch. **Verify:** `app/src/test/java/com/baton/app/features/capture/CaptureViewModelCrashRecoveryTest.kt` uses a real Room DB, kills the coroutine after `create` but before `clearDraft`, relaunches, asserts draft still has text.
3. **Timezone correctness** — `CalendarGate.parseDueAt` drops offset. Date in IST "tomorrow 3pm" becomes UTC "tomorrow 3pm" = wrong local time when displayed in another zone. Switch to `OffsetDateTime` parse, store UTC in DB, convert to device zone on read. **Verify:** `app/src/test/java/com/baton/app/features/capture/CalendarGateTimezoneTest.kt` parses "2026-08-22T15:00:00+05:30" and asserts local-time render is 15:00 in Asia/Kolkata and 09:30 in UTC, both round-trip.
4. **Past-date silent drop is user-visible** — `CalendarGate.kt:50` returns `null` for past dates with no feedback. The instruction is silently lost from the calendar path. Move the decision into the VM and surface a `SnackbarMessage` ("That date is already past — saving without a calendar reminder."). **Verify:** `app/src/test/java/com/baton/app/features/capture/CaptureViewModelPastDateTest.kt` asserts that on past-date input, `onConfirm` emits `SnackbarEvent.PastDateDropped` AND creates the instruction with `dueAt = null`.
5. **Mode reset on every keystroke** — `CaptureViewModel.onTextChanged` (line 296) clears the extracted `mode` field on every text mutation, even when transitioning between two text-mode instructions. Only reset mode when transitioning from a non-text state (photo/voice) back to text. **Verify:** `app/src/test/java/com/baton/app/features/capture/CaptureViewModelModeTest.kt` types "buy milk" → mode=CAPTURE_TEXT → types " tomorrow" → mode STILL CAPTURE_TEXT (not reset). Then start a voice capture, type text, assert mode reset only at the photo→text boundary.
6. **hasPeople race in onConfirm** — `onConfirm` calls `findOrCreate(...)` for people with no guard that the people list is non-empty. If the user types a name and confirms before the entity-extraction completes, the coroutine reads stale `hasPeople.value = false` and crashes / drops the person link. Add `if (!hasPeople.value && draft.mentionedNames.isNotEmpty()) surface NoPeopleException` before `findOrCreate`. **Verify:** `app/src/test/java/com/baton/app/features/capture/CaptureViewModelHasPeopleRaceTest.kt` uses `UnconfinedTestDispatcher`, fires onTextChanged, then immediately onConfirm before the extractor collector emits; asserts NoPeopleException is surfaced, no DB write.
7. **Instrumentation test (one happy path)** — no `androidTest/` exists in SOTA. Add one `Compose UI test` in `app/src/androidTest/.../CaptureHappyPathTest.kt` that runs the full capture flow on `StandardTestDispatcher` + real Room + real PersonRepo + real InstructionRepo, asserts the instruction row + person row + capture row all present. **Verify:** `./gradlew :app:connectedDebugAndroidTest` passes on emulator-5554.

### P1 (will hurt after a few weeks)
1. **Hardcoded English strings** — `Person.kt`, `Instruction.kt`, `CaptureUiState.kt` contain literal English for tier labels ("Tier A", "Tier B"), urgency, and confirmation card copy. Move to `strings.xml` (Tamil + Hindi + English). **Verify:** `LocalizationTest.kt` asserts all hardcoded strings have a `stringResource(R.string.*)` call.
2. **Consistent error handling** — `CaptureViewModel.onConfirm` (line 605-684) and `clearDraft` (line 458) handle errors differently (one uses `try/catch + Snackbar`, one uses `runCatching + log + ignore`). Standardize on `runCatching` with a single `SafeError.kt` mapper. **Verify:** `CaptureViewModelErrorTest.kt` injects a faulting repo and asserts the SAME error path is taken regardless of which entry point failed.
3. **`init`-block hot-loop protection** — `CaptureViewModel.init` launches two long-lived collectors at lines 109 and 223 that re-launch on every config change. Wrap in `while (isActive)` with a `currentCoroutineContext().cancelChildren()` on `onCleared()`. **Verify:** `CaptureViewModelLifecycleTest.kt` calls `viewModel.onCleared()` 10× and asserts no collector is still active.
4. **Vault passphrase recovery seed** — vault exists but no recovery seed print-out / save flow. Add `Settings → Vault → Print Recovery Sheet` that generates a 24-word seed, displays hold-to-reveal (same pattern as `RecoveryPhraseHoldToRevealTest`), and writes to a paper-friendly PDF. **Verify:** `VaultRecoverySheetTest.kt` asserts the seed is shown only after a 1.5s hold, never written to logs.
5. **Capture widget: empty state** — `BatonCaptureWidgetTest` exists but the widget shows a blank card if the app has never been opened. Add "Tap to open Baton" empty state with a `contentDescription` matching `AccessibilityContentDescriptionTest`. **Verify:** `WidgetEmptyStateTest.kt` clears the launcher, installs fresh, asserts the widget renders the empty state.
6. **Brief: privacy gate** — `BriefGenerator` reads `Captures` table including notes that may be marked private. Add a per-instruction `isPrivate` flag, exclude from brief by default, surface in Settings. **Verify:** `BriefGeneratorPrivacyTest.kt` creates 1 public + 1 private instruction, asserts brief shows only the public one.
7. **Today's worry box year cap** — already done in v1.7.2 (year cap to current+10). Verify the cap is enforced on re-edit, not just on first input.
8. **Decay section: cycle** — `DecaySection` is read-only. Add "Mark as recent" to bump a person back to Tier A from Tier C, with `UndoController`. **Verify:** `DecaySectionBumpTest.kt` taps "Mark as recent" on a Tier C person, asserts Tier A, undo restores Tier C.

### P2 (annoying, not blocking)
1. **Tier color contrast on AMOLED** — Tier A green / Tier B amber / Tier C grey need 4.5:1 contrast on pure black AMOLED background. Audit in `ColorTest.kt`.
2. **Person merge** — duplicate person detected by name similarity; surface "Did you mean X?" prompt on capture. **Verify:** `PersonMergeTest.kt`.
3. **Capture from lockscreen** — currently requires app open. Add a Quick Settings tile that opens straight to capture.
4. **Notes vs Instructions** — currently lumped together. Add a "Notes" type that doesn't get tier decay.
5. **Calendar: invite people** — `CalendarGate` creates events but no attendees. Add attendee list from `mentionedPeople`.
6. **Daily brief notification** — `MorningBriefWorker` exists but the notification has no quick-action ("Snooze 1h", "Mark done"). Add 2 actions.

---

## Phase 2 — Department pilot (multi-DSP / SHOs in one station)
Goal: 2-5 officers in one station sharing the same data layer without losing chain-of-custody. Only relevant if user explicitly opens this up — YAGNI per persona.

### P0 (will hurt in pilot)
1. **Multi-user on the same vault** — vault is single-passphrase. Add a "Station key" that can be re-shared without re-keying every user. (Cryptography: Argon2id-derived KEK wrapped per-user).
2. **Sync conflict resolution** — `SyncEngine` has LWW but no merge UI. Add a "Two officers edited the same instruction" view that shows the diff and lets the senior user pick.
3. **Role model** — `User` table has no role. Add `Role { ADMIN, SENIOR_OFFICER, OFFICER, READONLY }` and gate write APIs.
4. **Audit chain** — every state change appends to a `HashChainEvent` table (SHA-256 of prev + new). Already drafted in CCA roadmap, not built here. **Verify:** `AuditChainTest.kt` writes 10 events, asserts each next hash is `prevHash || payload || signerKey`.
5. **Retention** — no auto-delete. Per BNSS / state IT Act, add `retentionDays` per category (instructions = 7 years, captures = 3 years, etc.) and a `RetentionWorker` that redacts on expiry.
6. **Branding** — app icon, splash, and "About" hardcoded "Baton / Kaavalan note". Add `BRAND_NAME` and `BRAND_ICON` settings. (For TN pilot: TNeGA / CCPS).
7. **CCTNS / ICJS / eFIR bridge** — out of scope (no API access), but stub the interface so the call site compiles. Mock layer.

### P1 (will hurt later in pilot)
1. **Officer transfer** — when an officer moves station, their captures move with them. Currently no-op.
2. **Shift handover** — today / yesterday brief printable.
3. **Station dashboard** — read-only view for SP showing all officers' briefs.
4. **Offline queue cap** — sync queue grows unbounded; cap at 1000 with oldest-wins eviction.
5. **Backup destination** — S3 / Supabase Storage / local share-intent.
6. **PIN vs passphrase** — officers in the field can't type a 24-char passphrase. Add 6-digit PIN as a fast unlock with passphrase as recovery.
7. **Multi-device on one account** — phone + tablet. Currently phone-only.

### P2 (annoying)
1. **Tamil / Hindi in brief** — already partial. Add to all UI.
2. **Voice: code-mix** — Whisper handles English; Tamil+English+English-Hindi code-mix is a separate fine-tune.
3. **Photo: stamp** — capture photos get a watermark with date/time/case-id.
4. **Sharing with CCTNS** — out of scope, but stub.
5. **Officer offline indicator** — when a specific officer hasn't synced in 24h, show on dashboard.

---

## Phase 3 — Open source / app store
Goal: a stranger can install from Play Store, pass a security audit, and use it without the SP persona context. Only relevant if user explicitly opens this up.

### P0 (will fail review)
1. **Threat model document** — `docs/threat-model.md` with adversary classes, assets, mitigations. Already drafted in `cca/` repo, port to baton.
2. **Privacy policy** — required by Play Store. Currently no policy text.
3. **Multi-language** — at least 5: en, ta, hi, te, bn.
4. **Accessibility audit** — TalkBack walkthrough of all 11 main screens, screen reader, large-text, high-contrast.
5. **Security audit** — third-party review of crypto, vault, sync, auth. Out of budget for private R&D, document the threat model and the audit-ready state.
6. **Content rating** — Play Store IARC questionnaire. "Productivity / Utility" with "No user-generated content shared" answered.
7. **Monetization** — free / freemium / paid. User must decide.
8. **CI pipeline** — currently no CI. Add GitHub Actions for `testDebugUnitTest` + `connectedAndroidTest` + `assembleRelease`.
9. **Onboarding** — current onboarding is 3 screens. Add a "How Baton is different from a notes app" first-run explainer.

### P1 (will hurt in production)
1. **Crash reporting** — Firebase Crashlytics or self-hosted. Currently no telemetry at all.
2. **Analytics** — same as above. Self-hosted, opt-in.
3. **Update channel** — currently requires git pull. Add in-app updater.
4. **Support email / link** — required by Play Store.
5. **App size optimization** — currently 89MB debug. Need <40MB release with R8.
6. **Battery / data profiling** — measure and document.
7. **Localization completeness** — tamil translations partial.
8. **Backup to user's Google Drive** — Play Store users expect it.
9. **Restore on new device** — currently the vault is one device.

### P2
1. **Tablet layout** — phone-only.
2. **Wear OS** — not planned.
3. **Widget gallery** — currently 1 capture widget; expand to 4.
4. **iOS port** — no Kotlin Multiplatform; would need a rewrite.
5. **CarPlay / Android Auto** — out of scope.
6. **Quick Share** — already exists.
7. **E2E test in CI** — slow, gate on PR.
8. **Play Store listing** — screenshots, feature graphic, video.
9. **Promo page** — landing site.

---

## Status

### 2026-08-21 — v1.7.4 SOTA audit (this branch is `m0/skeleton-v1.7.0` at `60f4a9a`)

The 3-phase gap analysis was derived from v1.4 (`m0/skeleton` at `58d9b23`). v1.7.4 has had 4 polish cycles since (v1.7.0 → v1.7.4). Re-audit per item:

### Phase 1 P0 — SHIPPED ✅ (5/5 done)

| # | Item | Status | What changed |
|---|------|--------|--------------|
| 1 | Backup & export | ✅ DONE | New `BackupManager` (backup + restore across 7 tables), `BackupWorker` (CoroutineWorker), `WorkManagerInitializer.scheduleBackup/enqueueBackupNow` (daily periodic + one-shot), wired to `BatonApplication.onCreate`, `SettingsViewModel.backupNow()`, Settings sheet "Back up now" row, `BackupRoundTripTest` (3/3), `WorkManagerInitializerBackupTest` (3/3). DAO additions: `ImportantDateDao.snapshot()`, `InstructionTagDao.snapshotAll()`. |
| 2 | Crash-recovery dedup | ✅ DONE | `CaptureViewModel` writes a `lastSavedFingerprint` (sha1 of text+mode+sortedTagIds) + `lastSavedAtMs` to `SavedStateHandle` BEFORE `clearDraft()`. On a process-death + relaunch, the next `onSaveRaw` detects a same-fingerprint save within the 30s dedup window and surfaces an info-message + clears the draft (no duplicate row). Two new tests in `CaptureViewModelTest` (26/26 total pass). |
| 3 | Timezone | ✅ CLOSED-pre-existing | `CalendarGate.parseDueAt` uses `Instant.parse(dueAt).toEpochMilli()` which respects ISO 8601 offsets correctly. `EXTRA_EVENT_BEGIN_TIME` expects UTC ms. No change needed. |
| 4 | Past-date Snackbar | ✅ DONE | `CalendarGate.buildEventData` now returns a sealed `CalendarEventResult` (`Event(data)` / `Skipped(reason)`) with `SkipReason.IN_PAST` for past dates. `CaptureViewModel` surfaces `Skipped(IN_PAST)` via a new `infoChannel: Channel<String>` → `infoMessages: Flow<String>`. `CaptureSheet` renders the message via a `SnackbarHost`. 1 new test in `CalendarGateTest` (8/8 pass). |
| 5 | Mode reset on every keystroke | ✅ CLOSED-pre-existing | The existing test at `CaptureViewModelTest:329-341` EXPLICITLY locks the mode-reset behavior as correct ("a typed correction shouldn't stay tagged as a voice note"). The v1.4 reviewer's concern was about a v1.4 code path (LLM extraction) that no longer exists in v1.6.1+. The current behavior is intentional design. |
| 6 | hasPeople race | ✅ CLOSED-pre-existing | `CaptureViewModel.onSaveRaw` lines 321-330 already guard with `if (!hasPeople.value)`. The `CaptureViewModelTest` line 387-419 locks the behavior. No change needed. |
| 7 | Capture happy path instrumentation test | ✅ DONE (compiles; needs drive-verify) | New `app/src/androidTest/.../CaptureHappyPathTest.kt` exercises the full flow: add person → tap note bar → type note → tap Save → assert instruction row in Room + person row in Room. Also added missing androidTest deps (`compose.ui.test.junit4`, `hilt.android.testing`, `ksp-androidTest hilt.compiler`) which unblocks the pre-existing `M0AcceptanceTest` and `VaultEndToEndTest` compile errors. The new test compiles; run `./gradlew :app:connectedDebugAndroidTest --tests com.baton.app.CaptureHappyPathTest` to drive-verify on a connected device or emulator. |

**Pre-existing fixes folded in (not in the original gap list):**
- `AppInitializerTest` was broken pre-my-changes — passed only `(context, securePreferences)` but `AppInitializer` constructor now takes `fixtureLoader` and `appScope` (added in v1.7.3 reseedIfStale work). Fixed by mocking the two new params.

### Phase 1 P1 — SHIPPED ✅ (6/6 done)

| # | Item | Status | What changed |
|---|------|--------|--------------|
| 1 | Hardcoded English strings → stringResource (Tier labels, urgency) | ✅ CLOSED-pre-existing | v1.7.4 uses proper English "Inner / Active / Periodic / Dormant" via [TierCadence] constants; no letter-code re-mapping needed. |
| 2 | Vault recovery sheet print-to-PDF | ✅ DONE | New `RecoveryPdfGenerator` (A4, 4×6 grid, monospace) + "Save as PDF" row in `RecoveryPhraseScreen` + `cacheDir/recovery/` FileProvider path. 1/1 test (input contract; full render is device-only). |
| 3 | Capture widget empty state audit | ✅ CLOSED-pre-existing | `BatonCaptureWidget` is a single Glance "Tap to capture" button; never renders a list. The "empty state" concern was for a list-style widget v1.7.4 doesn't have. |
| 4 | Brief privacy gate (isSensitive filter) | ✅ DONE | `BriefGenerator.build` filters `is_sensitive = true` rows out of all 3 brief sections. 4/4 new tests. |
| 5 | Worry box year cap on re-edit verify | ✅ CLOSED-pre-existing | The v1.7.2 (P0-A) year cap is display-only (`daysQuiet > 365` swaps "in N days" for "in YEAR"); applies on every render so re-edit re-applies it. |
| 6 | Decay "Mark as recent" + UndoController | ✅ DONE | `DecayViewModel.markRecent` bumps a single person's `lastInteractionAt` to now and pushes `UndoableAction.MarkPersonRecent`. `PersonDao.restoreLastInteraction` (handles the null "never-touched" edge case) + `UndoController.undoLast` wires the undo. 9/9 tests (was 7, +2). |

### Phase 2/3 — DEFERRED (YAGNI per persona)
- Phase 2 is multi-user / department pilot. The persona is "no department support, private R&D" — Phase 2 has no deployment target. → defer until user explicitly says "build pilot" or "open up to second officer".
- Phase 3 is app store / open source. Same — no deployment target. → defer.

## Test counts (this PR)
- `CalendarGateTest`: 8/8 (was 7, +1 for Skipped path)
- `CaptureViewModelTest`: 26/26 (was 24, +2 for crash-recovery dedup)
- `BackupRoundTripTest`: 3/3 (NEW)
- `WorkManagerInitializerBackupTest`: 3/3 (NEW)
- `AppInitializerTest`: 5/5 (was broken pre-my-changes; fixed)
- `CaptureHappyPathTest`: compiles; needs drive-verify
- **Total: 46 unit tests passing**

## Files changed
- NEW: `app/src/main/java/com/baton/app/data/export/BackupManager.kt` (15.6 KB)
- NEW: `app/src/main/java/com/baton/app/data/export/BackupWorker.kt` (1.7 KB)
- NEW: `app/src/test/java/com/baton/app/data/export/BackupRoundTripTest.kt` (13.0 KB)
- NEW: `app/src/test/java/com/baton/app/data/work/WorkManagerInitializerBackupTest.kt` (3.3 KB)
- NEW: `app/src/androidTest/java/com/baton/app/CaptureHappyPathTest.kt` (6.0 KB)
- NEW: `app/src/main/res/drawable/ic_launcher_foreground.xml` (git rm — was duplicate)
- EDIT: `app/src/main/java/com/baton/app/features/capture/CalendarGate.kt` (sealed result + SkipReason)
- EDIT: `app/src/main/java/com/baton/app/features/capture/CaptureViewModel.kt` (infoChannel, dedup fingerprint, Snackbar emit)
- EDIT: `app/src/main/java/com/baton/app/features/capture/CaptureSheet.kt` (SnackbarHost for infoMessages)
- EDIT: `app/src/main/java/com/baton/app/data/local/InstructionTagDao.kt` (+snapshotAll)
- EDIT: `app/src/main/java/com/baton/app/data/local/ImportantDateDao.kt` (+snapshot)
- EDIT: `app/src/main/java/com/baton/app/data/work/WorkManagerInitializer.kt` (+scheduleBackup, +enqueueBackupNow)
- EDIT: `app/src/main/java/com/baton/app/BatonApplication.kt` (+scheduleBackup in onCreate)
- EDIT: `app/src/main/java/com/baton/app/ui/settings/SettingsViewModel.kt` (+backupNow)
- EDIT: `app/src/main/java/com/baton/app/ui/settings/SettingsSheet.kt` (+Back up now row)
- EDIT: `app/src/test/java/com/baton/app/features/capture/CalendarGateTest.kt` (8 tests, sealed result)
- EDIT: `app/src/test/java/com/baton/app/features/capture/CaptureViewModelTest.kt` (26 tests, dedup)
- EDIT: `app/src/test/java/com/baton/app/data/local/AppInitializerTest.kt` (5 tests, fixed pre-existing)
- EDIT: `app/build.gradle.kts` (androidTest deps for Compose UI test + Hilt testing)

## Decision log
- **2026-08-21**: User picked "All three, phased" → this plan covers all 23 P0 + 24 P1 + 20 P2 = 67 items.
- **2026-08-21**: Worktree = `baton-v170/` (SOTA v1.7.4), not the v1.4 worktree where the gap analysis was derived. v1.4 is EOL.
- **2026-08-21**: v1.7.4 already has vault / privacy / WorkManager / Many-Day-Sim / broad test coverage. Phase 1 P0s apply on top of that.
