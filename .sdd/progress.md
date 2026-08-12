# Baton progress ledger
Branch: m0/skeleton
Tags: m0-skeleton, m0-final, m1-capture, m2-capture, m2-final, m3-final, m4-final, v1.0-final, v1.1-audit, **v1.1.1**

## v1.1.1 status: COMPLETE — 2 real root-cause bugs found in v1.1 emulator pass, both fixed at the source, end-to-end wire flow verified on emulator (server's is_sensitive flips on both ON and OFF), 158/158 unit tests green + 6 ignored, debug + signed release APKs rebuilt (v0.5.1)

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

### v1.1.1 verification

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
- **Test counts**: was 151/151 + 6 ignored, now 158/158 + 6 ignored.
  - `RoomPersonRepositoryTest`: 5 → 9 (+4: setSensitive ON, OFF, failure, ghost)
  - `SyncEngineTest`: 10 → 13 (+3: PATCH true, PATCH false, deleted-row no-op;
    also fixed the existing 3 "no-conflict" cases which had been asserting
    the v1.1 bug's `create()` call)
  - All 7 new tests are regression guards for the wire flow
- **APKs**: debug 53MB, signed release 41MB, versionName 0.5.1, versionCode 4

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

