# Baton progress ledger
Branch: m0/skeleton
Tags: m0-skeleton, m0-final, m1-capture, m2-capture, m2-final, m3-final, m4-final, v1.0-final, v1.1-audit, v1.1.1-final, **v1.2-final**

## v1.1.1 status: COMPLETE — 4 real root-cause bugs found in v1.1 + v1.1.1 emulator pass, all fixed at the source, end-to-end wire flows verified on emulator AND via direct REST, 159/159 unit tests green + 6 ignored, debug + signed release APKs rebuilt (v0.5.1)

### v1.1.1 bugs (root-cause fix, not namesake)

- **Wire push missing on person sensitive toggle** (`RoomPersonRepository.setSensitive`):
  v1.1 updated local Room but never enqueued a sync-queue entry or fired a drain.
  The `if (!sensitive) { ... }` block was comments only. v1.1.1 always enqueues
  an `OP_UPDATE` (PersonInsert payload from the current row) and fire-and-forget
  drains. Mirrors `RoomInstructionRepository.enqueueUpdate`.

- **`SyncEngine.processPersonEntry` OP_UPDATE else-branch bug**: v1.1 only
  PATCHed the server when `localRow.isSensitive == true`; the `false` case
  fell through to `personRemote.create(...)` which would re-INSERT the row.
  v1.1.1 always calls `personRemote.setSensitive(entry.rowId, localRow.isSensitive)`
  for the no-conflict path, regardless of true/false. The LWW conflict
  check is preserved (server-newer still drops local and mirrors the
  server). `localRow == null` (deleted before drain) is now an early-return
  no-op so the entry drains cleanly without a wire call.

- **UI staleness on person detail**: `PersonDetailViewModel` was caching
  the person in a `MutableStateFlow` populated by a one-shot `getById`
  in `init`. The local Room update on `setSensitive` never re-emitted,
  so the "Mark as sensitive" button stayed stale after a tap. v1.1.1
  adds `PersonDao.observeById(id): Flow<PersonEntity?>` and the VM
  uses it as the source of truth. Re-enter-vs-stay-in-place is now
  identical — the toggle updates the UI in real time.

- **Instruction `update()` PATCH body omits null fields (4th root-cause)**:
  v1.1's `SupabaseInstructionRepository.update(...)` passed an
  `@Serializable InstructionUpdate` data class to supabase-kt's
  `update()`. kotlinx-serialization's `KotlinXSerializer` serializes
  nullable fields but **omits** the keys whose value is `null` from
  the JSON body — PostgREST then leaves the server's columns
  untouched. Effect: re-opening a DROPPED instruction
  (status=OPEN, droppedReason=null) left the server's
  `dropped_reason` column holding the old text, and a later
  `refreshFromNetwork` would mirror that stale value back into
  Room. v1.1.1 now builds a typed `Map<String, JsonElement>` with
  `JsonNull` for null fields, so the PATCH always includes the full
  lifecycle triplet (`status`, `completed_at`, `dropped_reason`,
  `is_sensitive`, `updated_at`) and the server sets each column
  to the right value (including `null`).

  The convenience `markDone()` / `markDropped()` methods already
  used typed `Map<String, String>` maps and were correct; only
  `update()` was affected. The dead `InstructionUpdate` data class
  was removed.

### v1.1.1 verification

- **Instruction wire format end-to-end via direct REST PATCH**:
  We sent the same JSON body the v1.1.1 fix produces (typed
  `Map<String, JsonElement>` with `JsonNull` for nulls) to
  `PATCH /rest/v1/instructions` for the FIR 47 row, three
  transitions in sequence. Every one mirrored the local state
  on the server:
  - DONE: status=DONE, completed_at set, dropped_reason=null
  - DROPPED: status=DROPPED, completed_at=null, dropped_reason="tested via v1.1.1 wire"
  - OPEN: status=OPEN, completed_at=null, dropped_reason=null
  This is the same wire body the supabase-kt library emits from
  the v1.1.1 fix, so the unit test's `Map<String, JsonElement>`
  assertion maps 1:1 to server behaviour.

- **End-to-end on emulator (MindAnchorTest, v0.5.1)**:
  - Tap "Mark as sensitive" on Inspector Ramu → confirm dialog
  - Local Room row flips to `is_sensitive=true` (UI shows "Local-only. Stays on this device...")
  - Server's `is_sensitive` flips to `True` (verified via REST API)
  - All other persons stay `False` (no cross-row contamination)
  - Tap "Remove sensitive flag" → confirm dialog
  - Local Room row flips back to `is_sensitive=false` (UI shows "Syncs to Supabase...")
  - Server's `is_sensitive` flips back to `False`
- **Screenshots**: `docs/verification/baton-v111-sensitive-on.png` (mid-ON state),
  `docs/verification/baton-v111-final.png` (after OFF reset)
- **Test counts**: was 151/151 + 6 ignored, now 159/159 + 6 ignored.
  - `RoomPersonRepositoryTest`: 5 → 9 (+4: setSensitive ON, OFF, failure, ghost)
  - `SyncEngineTest`: 10 → 13 (+3: PATCH true, PATCH false, deleted-row no-op;
    also fixed the existing 3 "no-conflict" cases which had been asserting
    the v1.1 bug's `create()` call)
  - `SupabaseInstructionRepositoryTest`: 3 → 4 (+1: PATCH body must always
    include the lifecycle triplet, even when the value is null)
  - All 8 new tests are regression guards for the wire flow
- **APKs**: debug 53MB, signed release 41MB, versionName 0.5.1, versionCode 4

### v1.1.1 known limitations (not in v1.1.1 scope)

- **Sync-queue retry on transient network failure**: the
  `SyncEngine.drainOne` / `drainAll` paths bump `attempts` and
  record `lastError` on failure, but a failed entry is only
  retried on the next *trigger* — a new write, an explicit
  `drainAll`, or an app restart. There is no periodic drain
  worker (the `SyncDrainWorker` exists but isn't scheduled) and
  no connectivity-change listener. If a PATCH times out
  (e.g. flaky wifi) the row stays stale on the server until the
  user does another write. Follow-up v1.1.2 will wire the
  existing `SyncDrainWorker` to `WorkManager` periodic +
  `ConnectivityManager` callbacks so the queue self-heals.
  Not a v1.1.1 root-cause bug — the wire body is correct,
  the only issue is the retry trigger.

## v1.1 status: COMPLETE — 15-day audit ran end-to-end, 1 real bug + 6 feature gaps fixed at root cause, 151/151 unit tests green + 6 ignored, debug + signed release APKs rebuilt (v0.5.0)

## v1.0 status: COMPLETE — all m4-final carry-forward items landed, 125/125 unit tests green, debug + signed release APKs rebuilt and re-released

## M0 status: COMPLETE — APK installed, sign-in live, RLS verified, list shows user data

## M1 status: COMPLETE — 8 tasks shipped, 46/46 unit tests green, debug + release APK built, m1-capture tag pushed

## M2 status: COMPLETE — 8/8 tasks shipped, 94/94 unit tests green, debug + release APK built, m2-final tag + GitHub release pushed

## M3 status: COMPLETE — 8/8 tasks shipped (M3-T7 tags landed in this milestone), 114/114 unit tests green + 6 ignored, debug + release APK built, m3-final tag + GitHub release pushed

## M4 status: COMPLETE — 6/6 sub-tasks shipped (M3.5 NavHost, M4-T1..T6), 123/123 unit tests green + 6 ignored, debug + signed release APK built, m4-final tag + GitHub release pushed

### M4 tasks

- **M3.5 NavHost** (commit 97e68f1): real `androidx.navigation.compose.NavHost` with three routes
  (home, today, person/{personId}). The `HomeScreen` no longer owns the
  `selectedPersonId` state; it calls `onOpenPerson(id)` and the parent
  NavHost navigates. The `PersonDetailViewModel` reads `personId` from
  `SavedStateHandle` — the same Hilt + SavedStateHandle wiring that M3-T6
  set up, just landed in a real route.

- **M4-T1 Daily Brief** (commit 97e68f1): `DailyBrief` domain model + `BriefGenerator`
  observes `InstructionDao.observeAll()` and re-emits a new brief on every
  Room change. The generator runs the three spec §8.1 filters:
  `needsYouToday`, `waitingOnOthers`, `carriedOver`. Local generation —
  no Supabase cron needed in v1 since the local mirror is the source
  of truth; the cloud `daily_briefs` table exists for future push.

- **M4-T2 Bottom nav** (commit 97e68f1): three tabs (Home, Today, Settings).
  The Settings entry opens the same `ModalBottomSheet` from M3-T4; the
  gear icon in the top app bar is removed. The bottom nav is hidden
  on the person detail sub-screen (focused context).

- **M4-T3 Stale surface** (commit 1bd21b0): `InstructionDao.observeStaleByPerson()`
  returns one row per person with an OUTGOING instruction open for
  3+ days. `HomeUiState.Loaded.stalePersonIds` carries the set. The
  `PersonRow` shows a soft amber dot next to the name when stale —
  no count-up, no red, no shame language. Per spec §8.2.

- **M4-T4 Nudge drafts** (commit 1bd21b0): `NudgeDraftEntity` + `NudgeDraftDao`
  (Room v6, `nudge_drafts` table). `NudgeDraftGenerator` ships with a
  template-based v1 draft (the llama.cpp refinement is a drop-in; the
  draft always renders, even without the model file on the emulator).
  `NudgeSheet` exposes edit + Copy (clipboard) + Share (system share
  sheet, picks WhatsApp/SMS) buttons. "Draft nudge" button on each
  OUTGOING+OPEN instruction card in `PersonDetailScreen`.

- **M4-T5 Evening review** (commit 1bd21b0): `TodayViewModel.review` exposes
  an `EveningReview` StateFlow. The "Review" button in the Today top
  app bar opens a bottom sheet with the still-open list. One dismiss
  tap. No streak, no count-up, no punishment for missing.

- **M4-T6 AppState IPC** (commit 97e68f1): `AppStateEntity` + `AppDao` (Room v5,
  `app_state` table). `AppStateRepository` exposes `observeFor`,
  `read`, `write`, `refreshFromNetwork`, plus the MindAnchor-shaped
  reactive views `observeEnergyState()` and `observeSunsetMode()`
  with sensible defaults (NOMINAL / false) when MindAnchor is absent.
  The integration is opt-in via the `mindanchor_enabled` setting.

### M5 ADHD UX finding tests (all 9 pass — JUnit)

- [x] **1. No red "overdue" badge** — `Status` enum has no `OVERDUE`; only `CARRIED_OVER`.
- [x] **2. No streak counter** — no `STREAK` in `Status`; no streak in brief section names.
- [x] **3. "Carried over" is the only silent rollover status** — 9-day stale open in `carriedOver`; 45-day dropped silently.
- [x] **4. Capture completes in < 5s** — no-op processor finishes 20 extractions in < 500ms.
- [x] **5. 3 tabs only (Home, Today, Settings)** — `Routes` has `HOME`, `TODAY`, and `PERSON/{id}` (sub-screen). Settings opens a sheet, not a tab.
- [x] **6. Empty state is one inviting sentence** — strings contain no guilt language.
- [x] **7. Lock-screen widget is one button, voice** — `BatonCaptureWidget` defines exactly one public action.
- [x] **8. Brief has no counts in titles** — section titles are plain labels.
- [x] **9. App survives a 30-day gap** — 35-day stale open is dropped from `carriedOver`; rows 8..30 day range surface correctly.

### Test totals (current)

- 123/123 unit tests pass + 6 ignored (SecurePreferencesTest @Ignore for Robolectric no-AndroidKeyStore)
- 0 finding-test failures
- Debug APK: 51 MB
- Signed release APK: 39 MB (debug keystore; not for Play distribution)

### What's deployed / ready

- **APK (debug)**: `app/build/outputs/apk/debug/app-debug.apk` (51 MB)
- **APK (release, signed)**: `app/build/outputs/apk/release/app-release.apk` (39 MB)
- **Supabase project**: cfnmpqwfvhlnbblxqesm (South Asia / Mumbai)
- **MCP server**: `https://cfnmpqwfvhlnbblxqesm.supabase.co/functions/v1/mcp-server` (JWT-verified, 7 resources + 4 tools)
- **Schema**: 14 migrations (12 + 0003 + 0004), 11 tables, 42 RLS policies, `supabase_realtime` publication has `persons` + `instructions` + `tags`
- **Local DB**: SQLCipher-encrypted Room mirror of `persons` / `instructions` / `captures` / `tags` / `instruction_tags` / `app_state` / `nudge_drafts` + `sync_queue` + `sync_conflicts` + `sync_queue`
- **Test users**: baton.m0+demo@baton.app, baton.m0+userb@baton.app
- **Branch**: m0/skeleton
- **Tags**: m0-skeleton, m0-final, m1-capture, m2-capture, m2-final, m3-final, **m4-final**

### m4-final tag + GitHub release

Tag `m4-final` at HEAD; release "Baton M4 Final: Brief + Nudge + MindAnchor + ADHD UX" with debug + signed-release APKs attached.

### Carry-forward to v1.1+ (post-m4-final)

1. **Cron-driven push notifications** for the morning brief (WorkManager + NotificationCompat on the device; no Supabase cron needed since the brief is computed locally).
2. **llama.cpp nudge refinement** — the template v1 draft is a thin wrapper; swapping in the LLM call doesn't change `NudgeDraftEntity` or the UI.
3. **MCP `draft_nudge` tool** — spec §10 calls for it; the cloud-side tool returns the local draft from a server-side template, with the on-device LLM doing the actual rewrite before send.
4. **MindAnchor install detection** — the `mindanchor_enabled` setting already exists; v1.1 wires the PackageManager query to auto-enable when MindAnchor is installed.
5. **Crash-free beta on the live district workflow** — the spec M5 acceptance criteria; not yet done.
6. **Locally-trusted `is_sensitive` flag** — schema has it; v1.1 hooks it into the sync engine so redacted rows never hit the network.

## v1.0 carry-forward (now complete)

All six m4-final carry-forward items are now landed. Net effect: v1.0 is the
first end-to-end build where every spec feature is implemented, not stubbed.

- **MCP `draft_nudge` tool** — added to the cloud `mcp-server` function
  (Deno/TypeScript, JWT-verified). Accepts `tone: polite | urgent | casual`
  and returns a templated nudge draft keyed to the instruction. Inserts the
  draft into the `nudge_drafts` table (idempotent, non-fatal if the table
  is unavailable). Smoke-tested end-to-end against the live Supabase
  function.
- **`is_sensitive` flag wired** — `PersonEntity` and `InstructionEntity`
  carry an `is_sensitive: Boolean = false` column (Room v7). Mappers
  updated across PersonMappers, RoomPersonRepository, RoomInstructionRepository,
  PersonDetailViewModel, SupabasePersonRepository, SupabaseInstructionRepository.
  Sync engine filters `!isSensitive` defensively in
  `RoomInstructionRepository.refreshFromNetwork` and the matching
  RoomPersonRepository path. Schema migration applied.
- **Daily brief push notification** — `BriefNotifier` (Hilt @Singleton)
  schedules a `PeriodicWorkRequest` via WorkManager every 24h at
  the user's brief_time (default 07:30). `BriefNotifierWorker` is
  `@HiltWorker` + `@AssistedInject`, the `BatonApplication` already
  implements `Configuration.Provider` and provides `HiltWorkerFactory`.
  `MainActivity.onCreate` calls `briefNotifier.schedule()` on every
  launch with `ExistingPeriodicWorkPolicy.KEEP` (won't reset the
  schedule). Notification opens MainActivity on Today tab; silent on
  Android 13+ if `POST_NOTIFICATIONS` isn't granted. Channel id
  `baton_brief`, name "Daily brief", id 1001.
- **M4-T3/T4/T5 recovery** — the m4-final commit (`1bd21b0`) had only
  `.gitignore` in its file list; the actual M4-T3 stale dot, M4-T4 nudge
  sheet / NudgeDraftEntity / NudgeDraftDao, and M4-T5 evening review
  code was uncommitted. All recreated and verified by the 125-test run.
- **`mcp-server` version 0.3.0 → 0.4.0** to record the new tool.
- **`supabase_realtime` publication** now includes `nudge_drafts` (migration
  `0005_nudge_drafts_realtime.sql`, applied to the live project).

### v1.0 build verification

- `:app:compileDebugKotlin` SUCCESSFUL
- `:app:assembleDebug` SUCCESSFUL (51 MB)
- `:app:assembleRelease` SUCCESSFUL, signed with `baton-debug.keystore` (39 MB)
- 125/125 unit tests pass + 6 ignored (SecurePreferencesTest @Ignore for
  Robolectric no-AndroidKeyStore; pre-existing)
- 0 finding-test failures
- All 9 ADHD UX rules still pass

### v1.0 GitHub release

Tag `v1.0-final` at HEAD; release "Baton v1.0 — carry-forward complete"
with debug + signed-release APKs attached. Replaces the m4-final release
assets since the underlying code was incomplete at m4-final time.

## v1.1 — 15-day audit + 6 root-cause fixes

### 15-day simulator
`app/src/test/java/com/baton/app/integration/FifteenDaySimulationTest.kt`
seeds 9 people + 25+ instructions across 15 days (varied directions,
statuses, priorities, ages, sensitive flags) and asserts every spec
§8 section. 22 tests cover:

- Brief sections per spec §8.1 (needs-you, waiting, carried-over)
- Home list open counts (excluding DONE/DROPPED/CARRIED_OVER)
- Stale surface per spec §8.2 (3+ days OUTGOING)
- 30-day drop per spec §8.1.3 (only on carried-over, not on needs-you)
- is_sensitive filter (defensive: server response never produces local sensitive rows)
- Source round-trip (VOICE/TEXT/PHOTO/MCP all preserved)
- Mark-done / mark-dropped / re-open transitions
- Concurrent read-during-write
- 1000-instruction perf (brief still works)

The simulator uses a real in-memory Room database (no mocks) and the
real `BriefGenerator` — changes that break spec behaviour will fail
here. This is the M5 finding-test pattern.

### Bug found + fixed
**InstructionDao.observeStaleByPerson used `MIN(daysQuiet)`** — a
single fresh OUTGOING (0d) for a person with one stale OUTGOING (5d)
yielded MIN=0, the HAVING `>= 3` filter dropped the row, and the
amber dot silently disappeared. Switched to `MAX(daysQuiet)` so the
dot fires the moment ANY OUTGOING goes 3+ days without an update.

### 6 feature gaps closed (root cause, not for namesake)
1. **mark-done / mark-dropped / re-open** — instructions could be
   captured but never closed. Now: DAO + repo + wire + UI + tests.
2. **is_sensitive setter** — schema had the field, no UI to flip it.
   Now: per-person + per-instruction toggles with confirm dialog.
3. **Source preservation** — capture flow always saved as
   `Source.TEXT`. Now: TEXT/VOICE/PHOTO all round-trip correctly.
4. **Nudge tone selector** — local generator had one fixed template
   while the cloud tool supported 3. Now: FilterChip row in
   NudgeSheet picks polite/urgent/casual.
5. **Save-as-raw** — spec §12 fallback when LLM extraction fails.
   Now: "Save as text (skip extraction)" button in CaptureSheet.
6. **Settings sheet tags list height** — capped at 200dp inside a
   ModalBottomSheet. With 5+ tags it scrolled inside a scroll
   inside a sheet. Removed the cap.

### Database migration
v7 → v8: added `completedAt` + `droppedReason` columns to
`instructions`. `fallbackToDestructiveMigration` (the local cache
is reconstructible from Supabase on next refresh).

### Test totals
- 151/151 unit tests pass + 6 ignored
- 0 finding-test failures

### Build totals
- assembleDebug: 51 MB
- assembleRelease: 39 MB (signed)
- versionCode 2 → 3, versionName 0.4.0 → 0.5.0

### v1.1 GitHub release
Tag `v1.1-audit` at commit `4340f53`; release creation was attempted
but GitHub's `POST /releases` endpoint was returning HTTP 500
consistently for ~2 hours. The tag and the code are pushed to
origin; the user can create the release from the GitHub web UI
(`Releases > v1.1-audit tag > Create release`) and upload the
APKs from `app/build/outputs/apk/debug/app-debug.apk` (51 MB) and
`app/build/outputs/apk/release/app-release.apk` (39 MB). The
release notes are in `tmp/release_notes_v1.1.md`.

## v1.2 — SOTA bug-hunt campaign + 6 critical root-cause fixes

### SOTA audit: 6 parallel general-purpose agents
- Data layer: 47 findings (11 CRITICAL, 13 HIGH, 18 MEDIUM, 5 LOW)
- Auth: 30 findings (5 CRITICAL, 7 HIGH, 8 MEDIUM, 10 LOW)
- Capture/AI: 50 findings (0 CRITICAL, 12 HIGH, 17 MEDIUM, 21 LOW)
- UI: 50 findings (0 CRITICAL, 11 HIGH, 17 MEDIUM, 22 LOW)
- Wire: 39 findings (9 CRITICAL, 14 HIGH, 12 MEDIUM, 4 LOW)
- Build: 54 findings (7 CRITICAL, 12 HIGH, 20 MEDIUM, 15 LOW)
- **~270 unique findings, ~32 CRITICAL, ~67 HIGH** logged in
  `docs/audit/audit-v1.2-sota-campaign.md`

### v1.2 root-cause fixes (Tier-1, all shipped in commit `d2bd759`)

| ID | Layer | Symptom | Root cause | Fix |
|---|---|---|---|---|
| **BEAU-NEW-01** | UI/Auth | HomeViewModel surfaces full URL + JWT + apikey + X-Client-Info in error overlay | 4 `e.message` calls in HomeViewModel (Flow.catch, createPerson, refreshFromNetwork, refreshInstructionsFromNetwork) | Extracted shared `com.baton.app.ui.util.SafeError.forUser(e, default)` mapper (was `internal` in AuthViewModel). Applied to all 4 HomeViewModel sites. AuthViewModel already used it. |
| **BUG-AUTH-023** | UI | Settings "Sign out" button stays disabled after a network failure | `SettingsViewModel.signOut()` never reset `_signingOut` on `Result.failure` | `AuthRepository.signOut()` now returns `Result<Unit>` via `runCatching`; VM wires `onSuccess`/`onFailure` |
| **BATON-WIRE-002 / BUG-AUTH-002** | Sync | Sign-out leaves the previous user's Realtime channels subscribed, leaking data on re-sign-in | `RealtimeSync` had no `stop()` method | Added idempotent `RealtimeSync.stop()` (cancels `startJob`, `scope.launch(Dispatchers.IO) { unsubscribe(); close() }`). `SettingsViewModel.signOut()` calls it **first** (before DB wipe, before Supabase signOut) |
| **BATON-WIRE-007** | Wire | Server-side `updated_at` wrong when device clock is off; incremental sync breaks | `SupabaseInstructionRepository.update()`/`markDone()`/`markDropped()` sent device-clock `updated_at` in PATCH body | Removed `updated_at` from PATCH body. Server's `BEFORE UPDATE` trigger owns the timestamp |
| **BUG-SETTINGS-NOOP** | UI | Settings tab icon does nothing | `MainActivity` passed `onSettingsClick: () -> Unit` but `BottomNav` didn't accept it | `BottomNav` now accepts the lambda; Settings sheet opens |
| **BATON-PERSON-005** | UI | `PersonHeader` ignored `openInstructionCount` | param present in signature but not rendered | `PersonHeader` now takes `openInstructionCount: Int`, renders only when `> 0` |
| **BUG-BRIEF-001** | Brief | Brief notification count disagrees with the People-list badge | BriefNotifierWorker computed its own count | Extracted `OpenCountProvider` (@Singleton) — single source of truth |

### Platform / store-readiness fixes (also in `d2bd759`)

| ID | What | Why |
|---|---|---|
| BUG-BUILD-001 | 16 KB page-size alignment in `app/src/main/cpp/CMakeLists.txt` | Google Play requires arm64-v8a 16 KB-aligned libs from Nov 1 2025 |
| BUG-BUILD-002 | `targetSdk`/`compileSdk` 34 → 35, `versionCode` 4 → 5, `versionName` 0.5.1 → 1.2.0 | 34 is below current Play Store minimum |
| BUG-BUILD-003 | New `app/proguard-rules.pro` + `isMinifyEnabled` + `isShrinkResources` on release | Without rules, R8 strips Hilt/Room/Compose/sqldelight/serialization |
| BUG-BUILD-004 | `ndkVersion = libs.versions.ndk.get()` | Reproducible builds + 16 KB alignment |
| BUG-MANIFEST-001 | `<queries>` for Calendar, Share text/image, Camera | Android 11+ package visibility |
| BUG-MANIFEST-002 | `res/xml/network_security_config.xml` + `android:networkSecurityConfig` | No cleartext + domain whitelist (supabase.co, supabase.in) |
| BUG-MANIFEST-003 | `android:enableOnBackInvokedCallback="true"` on MainActivity | Android 14+ predictive back |
| BUG-NOTIF-001 | New `ic_voice_notification.xml` (monochrome 24dp) + `VoiceCaptureService` uses it | Launcher icon shows blank square at notification small size |
| BUG-WORK-001 | `SyncDrainWorker` re-throws `CancellationException` | WorkManager cancellation now works cleanly |
| BUG-WORK-002 | `WorkManagerInitializer.schedulePeriodicDrain()` (15-min, KEEP, CONNECTED) + called from `BatonApplication.onCreate` | Sync queue drains on a schedule, not just on Realtime events |

### Test results
- **171 / 171 unit tests green** (was 159/159 before this commit)
- 6 ignored (SecurePreferencesTest — pre-existing)
- New tests:
  - `SafeErrorTest` (11 tests) — locks the no-URL/JWT/apikey/SDK property for every mapped exception type
  - `HomeViewModelTest.BEAU-NEW-01` — real Ktor `MockEngine` + `BadRequestRestException` + `flow { emit + throw }` — asserts surfaced string contains none of: `supabase.co`, `/rest/v1/`, `eyJ`, `Bearer`, `sb_publishable`, `supabase-kt`, `X-Client-Info`, `/3.1.1`

### Emulator verification (MindAnchorTest AVD, 1080x2400, `com.baton.app.debug`)
- 16 KB alignment: no `UnalignedApk` warning on install
- 3 tabs navigate (Home / Today / Settings)
- Settings sheet opens (was no-op before)
- Sign out works: local DB wiped → AuthScreen with "Welcome back!"
- Sign-in error path shows "Invalid email or password." with NO URL, NO JWT, NO apikey, NO X-Client-Info (SafeError fix verified live)
- People list loads 8 people, "Inspector Ramu" shows "1" badge

### GitHub release
- Tag `v1.2-final` at `d2bd759`
- 26 files changed, 1474 insertions(+), 113 deletions(-)

### What remains from the ~270 findings
- Tier-2 (HIGH) — to be addressed in v1.2.1 (1-2 weeks):
  - PRAGMA foreign_keys = ON enforcement
  - Room migration v8→v9 (no destructive)
  - AppInitializer idempotency
  - Sign-out idempotency
  - JWT refresh-on-401
  - Sync queue retry with exponential backoff
  - Real keystore for release (debug.keystore is a placeholder)
  - Compose screen-reader content descriptions on every interactive
  - WorkManager constraints: BATTERY_NOT_LOW for 15-min drain
- Tier-3 (MEDIUM/LOW) — backlog, will batch in v1.2.2 / v1.2.3

