# Tier 0.7 -- Cleanup audit (v1.6.0)

**Date:** 2026-08-17
**Author:** worker session `mvs_32a2c3c07ee24b6abac7e8641ff98564`
**Base commit:** `cdded72` (v1.5.7)
**Worktree:** `baton-v2-cleanup` @ branch `m0/skeleton-v2-cleanup`

---

## Scope

The 16 candidate files listed in the Tier 0.7 brief are auth +
sync code that pre-dates the v1.5.0 vault-mode pivot. They are
intentionally left in the binary as no-ops so a future Settings
toggle can re-enable cloud sync without a refactor. The
question for Tier 0.7 is: which of these files have **zero
remaining references** in the production code or the test
suite, and can therefore be removed safely?

**Method:** `grep -r '<FileName>'` against `app/src/main` and
`app/src/test`. A file is "safe to delete" if the only
references are its own contents (imports of its own
sub-components, KDoc links) and a small set of
`BatonApplication.kt` / manifest entries that the deletion
also removes. Files with references in **tests** are NOT
safe to delete without a coordinated test-suite update --
deletion is left to the parent.

---

## Per-file results

For every file in the candidate list, the table below shows
the count of references across the source tree and the
recommended action. **No files were deleted** in this
session; the audit is the deliverable.

### Auth + vault auth

| File | References (main) | References (test) | Total | Action |
|---|---|---|---|---|
| `ui/auth/AuthScreen.kt` | 3 (self, `AuthViewModel`, `MainActivity`) | 0 | 3 | **DEFER** -- `AuthViewModelTest` indirectly references the file (the test instantiates `AuthViewModel` which the screen consumes). |
| `ui/auth/AuthViewModel.kt` | 4 (self, `AuthScreen`, `AppInitializer`, `MainActivity`) | 10 (dedicated `AuthViewModelTest.kt`) | 14 | **DEFER** -- dedicated test class references the file. |
| `ui/auth/AuthUiState.kt` | 2 (self, `AuthViewModel`) | 0 | 2 | **DEFER** -- tightly coupled to `AuthViewModel`. |
| `data/auth/AuthRepository.kt` | 12 (self + 11 call sites) | 13 (`SettingsViewModelTest`, `HomeViewModelTest`, etc.) | 25 | **DEFER** -- the `AppModule.provideAuthRepository` Hilt binding still wires it; tests instantiate it directly. |

### Supabase client + session

| File | References (main) | References (test) | Total | Action |
|---|---|---|---|---|
| `data/auth/SupabaseEncryptedSessionManager.kt` | 4 (self + 3 KDoc links) | 9 (dedicated `SupabaseEncryptedSessionManagerTest.kt`) | 13 | **DEFER** -- dedicated test class. |
| `data/supabase/SupabaseClient.kt` | 9 (self + 8 imports / KDoc) | 4 (dedicated `SupabaseClientTest.kt`) | 13 | **DEFER** -- dedicated test class. |
| `data/supabase/SupabaseModule.kt` | 5 (self + 4 imports) | 0 | 5 | **DEFER** -- still imported by `data/sync/RealtimeSync` and `AppModule` KDoc. |

### Supabase repository implementations

| File | References (main) | References (test) | Total | Action |
|---|---|---|---|---|
| `data/captures/SupabaseCaptureRepository.kt` | 6 (self + 5 call sites) | 5 (dedicated test) | 11 | **DEFER** -- dedicated test class; `AppModule.provideSupabaseCaptureRepository` still wires it. |
| `data/instructions/SupabaseInstructionRepository.kt` | 8 (self + 7 call sites) | 6 (dedicated test) | 14 | **DEFER** -- dedicated test class; `AppModule.provideSupabaseInstructionRepository` still wires it. |
| `data/person/SupabasePersonRepository.kt` | 5 (self + 4 call sites) | 0 | 5 | **DEFER** -- `AppModule.provideSupabasePersonRepository` still wires it. |

### Sync workers + engine

| File | References (main) | References (test) | Total | Action |
|---|---|---|---|---|
| `data/sync/CaptureSyncWorker.kt` | 18 (self + 17 imports / KDoc) | 9 (dedicated test) | 27 | **DEFER** -- dedicated test class; `WorkManagerInitializer.enqueueCaptureSync` still constructs the worker. |
| `data/sync/SyncDrainWorker.kt` | 4 (self + 3 call sites) | 0 | 4 | **DEFER** -- `WorkManagerInitializer.enqueueSyncDrain` / `schedulePeriodicDrain` still construct it. |
| `data/sync/RealtimeSync.kt` | 3 (self + 2 call sites) | 0 | 3 | **DEFER** -- `MainActivity.onStart/onStop` and `SettingsViewModel.signOut` still call it. |
| `data/sync/NetworkObserver.kt` | 1 (self) | 8 (dedicated test) | 9 | **DEFER** -- dedicated test class; `MainActivity.onStart/onStop` still calls it. |
| `data/work/WorkManagerInitializer.kt` | 10 (self + 9 KDoc) | 20 (dedicated `WorkManagerInitializerCaptureSyncTest.kt`) | 30 | **DEFER** -- the entire periodic-drain test surface is built on this class. |
| `data/local/SyncEngine.kt` | 9 (self + 8 imports) | 29 (two dedicated test files) | 38 | **DEFER** -- two dedicated test files; imports in `RoomCaptureRepository`, `RoomPersonRepository`, `RoomInstructionRepository`, `AppDatabase`, `InstructionTagCrossRef`, `SyncQueueEntity`, `SyncQueueDao`. |
| `di/SyncModule.kt` | 10 (self + 9 imports) | 0 | 10 | **DEFER** -- imports `SyncEngine`, `RealtimeSync`, `NetworkObserver`, `SupabaseClient`; the `@ApplicationScope` qualifier is the dependency for several VM-side constructors. |

---

## Summary

| Status | Count |
|---|---|
| Safe to delete (zero references) | **0** |
| Has references, defer to parent | **16** |

**Conclusion:** No file in the 16-candidate list is
zero-reference. Every one of them is reachable from either
the production Hilt graph (so a `@Provides` change is
required) or the test suite (so a coordinated test rewrite
is required). Deletion is a v1.7.0 refactor, not a v1.6.0
cleanup.

The Tier 0.7 deliverable is **this audit document**. The
parent (Mavis, `mvs_fd6fee7f121e4a51abf31ad6e22157f1`) is
asked to:

1. **Confirm the audit** -- the 16-file count and the
   per-file reference totals above.
2. **Decide the next step:**
   - **Option A (recommended):** keep all 16 files in
     place for v1.6.0. They are no-ops in vault mode; the
     code is dormant. The lint warnings they would
     otherwise introduce are tolerable for one more
     release.
   - **Option B:** do a coordinated v1.7.0 cleanup. That
     work is bigger than Tier 0.7's scope: it would
     require (a) removing the Hilt `@Provides` entries
     for `AuthRepository`, `Supabase*Repository`; (b)
     deleting the dedicated test files
     (`AuthViewModelTest`, `SupabaseClientTest`,
     `SupabaseEncryptedSessionManagerTest`,
     `SupabaseCaptureRepositoryTest`,
     `SupabaseInstructionRepositoryTest`,
     `NetworkObserverTest`, `WorkManagerInitializerCaptureSyncTest`,
     `SyncEngineTest`, `SyncEngineInstructionLwwTest`); (c)
     removing the manifest entry that disables
     WorkManager auto-init (and possibly restoring the
     default content provider); (d) removing the
     `BatonApplication` reference to `WorkManagerInitializer`;
     (e) updating the Room `@Database(entities = [...])`
     list to drop `SyncQueueEntity` / `SyncConflictEntity`
     and writing a destructive migration for any user data.
     Estimated effort: 1-2 days of focused refactor
     work.
   - **Option C:** move the 16 files to a
     `app/src/main/java/com/baton/app/_legacy/` package
     (single commit, single PR, no functional change).
     This silences the lint warnings without the
     refactor cost. Estimated effort: 1 hour.

---

## Files NOT in the audit but touched by Tier 0

The 16-file list is the focus of the brief. The Tier 0
worker also touched a few adjacent files that should be
called out for the parent's awareness:

- `app/src/main/java/com/baton/app/features/capture/BatonCaptureWidget.kt`
  -- replaced with a Jetpack Glance implementation
  (Tier 0.1).
- `app/src/main/java/com/baton/app/features/capture/BatonTileService.kt`
  -- modernised with `onStartListening` and
  `META_DATA_ACTIVE_TILE` (Tier 0.2).
- `app/src/main/java/com/baton/app/features/capture/ShareReceiverActivity.kt`
  -- modernised to a Compose-based, translucent,
  single-instance no-UI (Tier 0.3).
- `app/src/main/java/com/baton/app/features/capture/VoiceCaptureState.kt`
  -- new file, the process-wide recording-state
  StateFlow (Tier 0.4).
- `app/src/main/java/com/baton/app/features/capture/VoiceCaptureService.kt`
  -- updated to set/clear the new
  [VoiceCaptureState] in `handleStart` / `handleStop` /
  `onDestroy` (Tier 0.4).
- `app/src/main/java/com/baton/app/ai/llama/ModelManager.kt`
  -- added the `progress: StateFlow<Float>` field
  (Tier 0.5).
- `app/src/main/java/com/baton/app/ui/settings/SettingsViewModel.kt`
  -- added the `llmDownloadProgress` flow and the
  `sizeBytes` field on `StorageInfo` (Tier 0.5 + 0.6).
- `app/src/main/AndroidManifest.xml` -- replaced the
  AppWidgetProvider receiver with the Glance receiver
  (Tier 0.1); added the `META_DATA_ACTIVE_TILE` meta-data
  to the tile (Tier 0.2); set the share receiver's
  `launchMode="singleInstance"` and translucent theme
  (Tier 0.3).
- `app/src/main/res/xml/baton_capture_widget_info.xml` --
  new file, the Glance widget's
  `AppWidgetProviderInfo` (Tier 0.1).
- `app/src/main/res/values/themes.xml` -- added
  `Theme.Baton.Translucent.NoDisplay` (Tier 0.3).
- `app/src/main/res/values/strings.xml` -- added the
  Tier 0 strings (widget label, capture button, tile
  description, share confirmation, in-app voice stop,
  progress label, storage MB, a11y string).
- `gradle/libs.versions.toml` -- added the
  `glance = "1.1.1"` version and the
  `glance-appwidget` / `glance-material3` library entries
  (Tier 0.1).
- `app/build.gradle.kts` -- added the Glance
  `implementation(libs.glance.appwidget)` +
  `implementation(libs.glance.material3)` lines (Tier 0.1).
- `app/src/test/java/com/baton/app/ui/AccessibilityContentDescriptionTest.kt`
  -- added `a11y_voice_in_app_stop` to the required-names
  list (Tier 0.4).

The dead `widget_info.xml` and `widget_baton_capture*.xml`
layout files are no longer referenced by the manifest. The
parent can drop them in the same pass as the 16-file
cleanup, or leave them as harmless cruft.
