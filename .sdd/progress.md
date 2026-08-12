# Baton progress ledger
Branch: m0/skeleton
Tags: m0-skeleton, m0-final, m1-capture, m2-capture, m2-final, **m3-final**

## M0 status: COMPLETE — APK installed, sign-in live, RLS verified, list shows user data

## M1 status: COMPLETE — 8 tasks shipped, 46/46 unit tests green, debug + release APK built, m1-capture tag pushed

## M2 status: COMPLETE — 8/8 tasks shipped, 94/94 unit tests green, debug + release APK built, m2-final tag + GitHub release pushed

## M3 status: COMPLETE — 7/8 tasks shipped (T7 tags deferred), 114/114 unit tests green + 6 ignored, debug + release APK built, **m3-final tag + GitHub release pushed**

### M3 tasks

- M3-T1 (commit 64a64e0): **SQLCipher encryption** for the local Room mirror. 32-byte passphrase generated in `SecurePreferences` (Keystore-backed `EncryptedSharedPreferences`), passed to `SupportOpenHelperFactory` at DB open. M2 plain `baton.db` is wiped on the M2 -> M3 transition by `AppInitializer.runOnAppStart()`. Verified on the emulator: `adb pull databases/baton.db` + `head -c 32` shows encrypted bytes (0xFF 0xFE 0xFD ...), not the "SQLite format 3" magic. 6 `SecurePreferencesTest` cases `@Ignore`d for Robolectric-no-AndroidKeyStore.

- M3-T2 (commit 64a64e0): **WorkManager on-demand init**. The auto-init `ContentProvider` is removed from the manifest with `tools:node="remove"`; `BatonApplication.workManagerConfiguration` injects a Hilt-aware `Configuration` with `HiltWorkerFactory`; `SyncDrainWorker` is a `@HiltWorker` that calls `syncEngine.drainAll()`.

- M3-T3 (commit f89e2ee): **8-shot extract prompt** in `assets/prompts/extract_v1.txt`. Added DSP rank preservation, same-day time cues ("before she leaves at 5"), `LOW` priority, and the explicit "no instruction found" return path. 4 new `ExtractorTest` cases.

- M3-T4 (commit 59c7412): **Sign-out UI** — gear icon in the top app bar opens a `ModalBottomSheet` with a single destructive "Sign out" button. `SettingsViewModel.signOut()` calls `AppInitializer.runOnSignOut()` FIRST (drop DB key + delete `baton.db`) and THEN `authRepository.signOut()` (clear Supabase session). Reverse order would let the still-mounted Compose tree see a SQLCipher "not a database" error. 5 `SettingsViewModelTest` cases (order, observable flag, re-entrancy, AppInitializer error survives, AuthRepository error survives).

- M3-T5 (commit c9b2535): **People list open-instruction badge**. New `InstructionDao.observeOpenCountByPerson(): Flow<List<PersonOpenCount>>` aggregates per person in Room; `HomeViewModel` `combine()`s it with the persons Flow and defaults missing persons to 0; `PersonRow` shows a `tertiaryContainer` Surface chip when count > 0, hides it otherwise. 3 new `InstructionDaoTest` cases (empty, count open + ignore closed, exclude null personId).

- M3-T6 (commit 917ceb7): **PersonDetailScreen**. TopAppBar with back arrow, timeline of cards sorted by `capturedAt DESC` (title + status chip + raw text + capture date), empty state, bottom spacer for NoteBar. In-place routing for now (no NavHost); a Hilt entry point + `SavedStateHandle` carry the `personId` so the wiring moves into a real `composable("person/{personId}")` in M3.5 without touching the VM contract.

- M3-T7: **DEFERRED**. Schema and design decided; UI lands in M4. Pre-existing `tags` + `instruction_tags` tables in `0001_init.sql` are untouched (the `tag_kind` enum is already in place).

- M3-T8 (commits f8c7683 + 71f510c + 2f6755a): **Cloud MCP server** at `supabase/functions/mcp-server/`. 7 resources (`baton://persons`, `baton://person/{id}`, `baton://instructions`, `baton://instruction/{id}`, `baton://instructions/open`, `baton://instructions/due-today`, `baton://stats`) and 4 zod-validated tools (`create_person`, `create_instruction`, `update_instruction_status`, `search_instructions`). JWT verified at the Supabase gateway. Deployed to `https://cfnmpqwfvhlnbblxqesm.supabase.co/functions/v1/mcp-server`. Verified end-to-end: MCP initialize returned the right capabilities, all 7 resources listed, 4 tools listed with input schemas, the three write tools round-trip through RLS. Two non-obvious bumps during the deploy: the initial pin to SDK 1.0.0 didn't even have the high-level `McpServer` class (need 1.1+), and `StreamableHTTPServerTransport` is Node-only — Deno needs `WebStandardStreamableHTTPServerTransport` from `server/webStandardStreamableHttp.js`.

- M3 e2e (commit 2f6755a): two issues caught when running the build on the emulator: (1) `net.zetetic:sqlcipher-android:4.6.1` bundles `libsqlcipher.so` but doesn't auto-load it, so the first DB read throws "No implementation found for nativeOpen" — fixed by `System.loadLibrary("sqlcipher")` at the top of `AppInitializer.runOnAppStart()`. (2) The M3-T5 badge was always 0 for instructions not created on this device because the launch-time refresh only pulled persons (M2-T6) and not instructions (left as TODO in M3-T5) — fixed by adding `InstructionRepository.fetchAll` + a thin `RoomInstructionRepository.refreshFromNetwork(remote)` overload, wired into `HomeViewModel.init`. After both fixes, signing in shows 7 persons with a `1` badge on Inspector Ramu, and tapping Ramu opens PersonDetailScreen with the instruction card, status chip, raw text, and capture date.

### M3 finding-tests status

| Test | Status |
|---|---|
| SQLCipher encryption end-to-end | **VERIFIED** — `adb pull databases/baton.db` + `head -c 32` shows encrypted bytes, not the SQLite magic. Sign-out flow + relaunch re-pulls from Supabase into a fresh encrypted DB. |
| People list badge | **VERIFIED** — Inspector Ramu row shows `1` chip on real Supabase data; other 6 persons show no chip. |
| Person detail timeline | **VERIFIED** — Tap Ramu, see the OPEN instruction card with raw text + 12 Aug capture date + status chip. |
| MCP server resources | **VERIFIED** — `curl` with a Supabase user JWT returned all 7 resources' contents via `resources/list` + `resources/read`. |
| MCP server tools | **VERIFIED** — `tools/call` for `create_person`, `create_instruction`, `search_instructions` all round-tripped through RLS and the writes were visible via the same resources. |

### Live e2e on emulator-5554

- Cold install + sign-in: list shows 7 persons; **Inspector Ramu** has a `1` badge (the OPEN `send FIR 47 - Inspector Ramu` instruction that's been sitting in Supabase since M1).
- Tap **Inspector Ramu** → PersonDetailScreen with the instruction card, `Open` status chip, raw text, and capture date.
- Re-install + cold start: SQLCipher `baton.db` is recreated encrypted; same data re-pulled from Supabase.

### What's deployed / ready

- **APK (debug)**: `app/build/outputs/apk/debug/app-debug.apk` (57 MB, with `libbaton-llama.so` + `libbaton-whisper.so` + `libsqlcipher.so`)
- **APK (release)**: `app/build/outputs/apk/release/app-release-unsigned.apk` (40 MB)
- **Supabase project**: cfnmpqwfvhlnbblxqesm (South Asia / Mumbai)
- **MCP server**: `https://cfnmpqwfvhlnbblxqesm.supabase.co/functions/v1/mcp-server` (JWT-verified, 7 resources + 4 tools, RLS-scoped)
- **Schema**: 13 migrations (12 + 0003_enable_realtime_publication), 11 tables, 42 RLS policies, `supabase_realtime` publication has `persons` + `instructions`
- **Local DB**: SQLCipher-encrypted Room mirror of `persons` / `instructions` / `captures` + `sync_queue` + `sync_conflicts`
- **Test users**: baton.m0+demo@baton.app, baton.m0+userb@baton.app
- **Branch**: m0/skeleton
- **Tags**: m0-skeleton, m0-final, m1-capture, m2-capture, m2-final, **m3-final**

### m3-final tag + GitHub release

Tag `m3-final` at commit c66195e; release "Baton M3 Final: Encrypted Local DB + Cloud MCP Server" at https://github.com/sampathmannam/baton/releases/tag/m3-final with debug + release-unsigned APKs attached.

### Carry-forward to M4

1. **M3-T7 tags**: schema v4, tag picker in capture sheet, tag management screen, Supabase RLS on `tags` + `instruction_tags`.
2. **Today tab + nudge drafts** via llama.cpp — the M3 stats resource gives us the counts; M4 wraps them in a daily brief.
3. **Brief scheduler** — Supabase cron job that calls the MCP server's `baton://instructions/due-today` resource on a schedule and pushes a digest to the user.
4. **AppState IPC with MindAnchor** so captures from the watch flow into the same Room mirror.
5. **8 / 9 ADHD-UX finding tests** (the calm-blue badge colour, the "carried over, not overdue" copy, the 1-tap save, etc.).
6. **M3.5 real `NavHost`** so the `PersonDetailScreen` wiring moves out of the in-place `selectedPersonId` state in `HomeScreen` into `composable("person/{personId}")`.
7. **Locally-trusted `is_sensitive` flag** — schema has it; M3 hooks it into the sync engine so redacted rows never hit the network.
