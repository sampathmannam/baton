# v1.2 SOTA Bug-Hunt Campaign — Aggregate Findings

**Date**: 2026-08-13
**Method**: 6 parallel general-purpose agents, each scoped to a different concern.
**Total findings**: ~270 unique, deduplicated across agents.

## Agent scope and finding count

| # | Scope                | Findings | Critical | High | Medium | Low |
|---|----------------------|----------|----------|------|--------|-----|
| 1 | Data + Room + sync   | 47       | 11       | 13   | 18     | 5   |
| 2 | Auth + secure prefs  | 30       | 5        | 7    | 8      | 10  |
| 3 | Capture + AI + a11y  | 50       | 0        | 12   | 17     | 21  |
| 4 | UI / Compose / nav   | 50       | 0        | 11   | 17     | 22  |
| 5 | Wire / network / RLS | 39       | 9        | 14   | 12     | 4   |
| 6 | Build / deploy / 16KB| 54       | 7        | 12   | 20     | 15  |
| **Total** |                | **~270** | **32**   | **69** | **92** | **77** |

## Already-fixed in v1.2 Batch 1 (do not re-fix)

- AuthViewModel: `SafeError.forUser` mapper (was BUG-AUTH-001)
- Settings tab: `onSettingsClick` callback wired up
- AuthRepository.signOut(): `runCatching` wrapper, returns `Result<Unit>`
- SettingsViewModel.signOut(): doesn't reset `_signingOut` back to false (BUG-AUTH-023)
- PersonDetailScreen.PersonHeader: `openInstructionCount: Int` parameter, only renders when > 0
- BriefNotifier: uses new `OpenCountProvider.todayOpenCount()`
- OpenCountProvider.kt (NEW): `@Singleton` with `InstructionDao.snapshot().count { ... }`
- network_security_config.xml (NEW): disallow cleartext, supabase.co/in only
- AndroidManifest.xml: `android:networkSecurityConfig="@xml/network_security_config"`
- SyncDrainWorker: `CancellationException` rethrow
- WorkManagerInitializer: `schedulePeriodicDrain` (15-min, KEEP, CONNECTED)
- BatonApplication: `schedulePeriodicDrain(this)` in `onCreate`
- SupabaseInstructionRepository.update(): typed `Map<String, JsonElement>` with `JsonNull` (v1.1.1)
- RoomPersonRepository.setSensitive: enqueue + drain (v1.1.1)
- SyncEngine.processPersonEntry: always calls `setSensitive` (v1.1.1)
- PersonDetailViewModel: uses `observeById` Flow (v1.1.1)

## Already-fixed in v1.2 Batch 2 (in progress — this PR)

- 16 KB page-size alignment in `app/src/main/cpp/CMakeLists.txt` (F-CRIT-01)
- targetSdk 34 → 35 + compileSdk 34 → 35 + versionCode 4 → 5 + versionName 0.5.1 → 1.2.0 (F-CRIT-02)
- proguard-rules.pro created with Hilt/Room/Compose/kotlinx-serialization/JNI/supabase-kt keep rules (F-CRIT-04)
- isMinifyEnabled = true + isShrinkResources = true for release (F-HIGH-12)
- SettingsViewModelTest updated to match BUG-AUTH-023 (2 tests now pass)

## Top-priority remaining CRITICAL findings (to fix in subsequent batches)

### Build / deploy
- F-CRIT-03 / BUG-AUTH-004: release signed with hardcoded debug keystore `baton123`
- F-CRIT-05: no AAB / bundle config — APK is 39 MB instead of 12-15 MB on device
- F-HIGH-03: keystore password hardcoded in `build.gradle.kts` (same as F-CRIT-03)
- F-HIGH-04: no `android.ndkVersion` pin — AGP picks arbitrary NDK

### Data layer
- BUG-DATA-001: `fallbackToDestructiveMigration` nukes pending writes on schema bump
- BUG-DATA-002: `processCaptureEntry` is a no-op — captures never reach Supabase
- BUG-DATA-003/004: lexicographic ISO-8601 compare broken for fractional seconds + timezones
- BUG-DATA-006: `processInstructionEntry OP_DELETE` is empty
- BUG-DATA-007: `RoomInstructionRepository` never persists locally-created instructions
- BUG-DATA-008: `RoomTagRepository.findOrCreateFree` inserts PENDING_INSERT with no outbox entry
- BUG-DATA-009: PRAGMA foreign_keys never enabled
- BUG-DATA-021: `AppInitializer.runOnAppStart` re-throws UnsatisfiedLinkError
- BUG-DATA-022: sign-out wipes DB but never recreates the AppDatabase singleton
- BUG-DATA-025: undocumented version chain, every bump triggers destructive migration
- BUG-DATA-027: `BriefNotifier.schedule` is never called

### Auth
- BUG-AUTH-002: Realtime WebSocket subscriptions never closed on sign-out
- BUG-AUTH-003: JWT + refresh token in plain SharedPreferences
- BUG-AUTH-004: see Build / deploy above
- BUG-AUTH-010: sign-out doesn't revoke JWT server-side
- BUG-AUTH-011: `SettingsViewModel.signOut` swallows runOnSignOut exception
- BUG-AUTH-006: shared text consumed before auth state observed

### Wire
- BATON-WIRE-001/002: Realtime no reconnect, no per-channel retry
- BATON-WIRE-003: see Build / deploy (SyncDrainWorker fix in Batch 2)
- BATON-WIRE-004: per-write drain swallows 4xx/5xx/timeout into PENDING
- BATON-WIRE-005: RLS denial not distinguishable from 5xx in user-facing error
- BATON-WIRE-006: capture row insert non-idempotent — retry creates duplicates
- BATON-WIRE-007: PATCH body overwrites server's updated_at with device clock
- BATON-WIRE-008: `AuthRepository.signOut` no runCatching (fixed in v1.2 Batch 1)

### Capture / AI
- F-01: BriefNotifier always says "Nothing on your plate" (was v1.2 Batch 1 fix)
- F-02: `POST_NOTIFICATIONS` never requested at runtime (F-CRIT-06)
- F-03: CalendarGate crashes if no Calendar app
- F-04/F-05: VoiceCaptureService startForeground leaks when permission denied
- F-09: Process death while capture sheet open loses half-typed note
- F-10: First LLM extract freezes 30-120s for 1 GB model download

## Files produced

- `docs/audit/audit-capture-ai-a11y.json` — agent 3 findings (50)
- `docs/audit/audit-data-layer.json` — agent 1 (47) — *to be saved*
- `docs/audit/audit-auth.json` — agent 2 (30) — *to be saved*
- `docs/audit/audit-ui-compose.json` — agent 4 (50) — *to be saved*
- `docs/audit/audit-wire.json` — agent 5 (39) — *to be saved*
- `docs/audit/audit-build-deploy.json` — agent 6 (54) — *to be saved*

## Next steps

- [x] v1.2 Batch 1: 7 source fixes (AuthViewModel, MainActivity, AuthRepository, SettingsViewModel, PersonDetailScreen, BriefNotifier, network_security_config, OpenCountProvider, SyncDrainWorker, WorkManagerInitializer, BatonApplication)
- [x] v1.2 Batch 1: SettingsViewModelTest updated (159/159 + 6 ignored)
- [x] v1.2 Batch 2: 16 KB alignment, targetSdk 35, proguard-rules.pro, R8 minify (in source, build pending)
- [ ] v1.2 Batch 2: build + run all 159 tests
- [ ] v1.2 Batch 3: Fix BATON-WIRE-007 (server `updated_at`), POST_NOTIFICATIONS runtime request, `<queries>` block
- [ ] v1.2 Batch 4: Replace debug keystore with real one (Baton needs a proper release keystore), AAB config
- [ ] v1.2 Batch 5: PRAGMA foreign_keys + Room migration for v8→v9, AppInitializer idempotency, sign-out idempotency
- [ ] Phone smoke test: re-verify all 9 phone-specific bugs after Batch 2 build
- [ ] Commit + push v1.2
- [ ] Update GitHub release

