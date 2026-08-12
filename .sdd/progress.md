# Baton progress ledger
Branch: m0/skeleton
Tags: m0-skeleton, m0-final, m1-capture, m2-capture, m2-final, m3-final, **m4-final**

## M0 status: COMPLETE — APK installed, sign-in live, RLS verified, list shows user data

## M1 status: COMPLETE — 8 tasks shipped, 46/46 unit tests green, debug + release APK built, m1-capture tag pushed

## M2 status: COMPLETE — 8/8 tasks shipped, 94/94 unit tests green, debug + release APK built, m2-final tag + GitHub release pushed

## M3 status: COMPLETE — 8/8 tasks shipped (M3-T7 tags landed in this milestone), 114/114 unit tests green + 6 ignored, debug + release APK built, m3-final tag + GitHub release pushed

## M4 status: COMPLETE — 6/6 sub-tasks shipped (M3.5 NavHost, M4-T1..T6), 123/123 unit tests green + 6 ignored, debug + signed release APK built, **m4-final tag + GitHub release pushed**

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

