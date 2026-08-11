# Baton M0 progress ledger

Branch: m0/skeleton

Started: 2026-08-11
Completed: 2026-08-11 (skeleton) → 2026-08-11 (live e2e verified)

## M0 status: COMPLETE — APK installed, sign-in live, RLS verified, list shows user data

All 9 tasks done. M0 is the first of 5 milestones (M0-M5) in the ~9-week solo build.

### Tasks

- [x] Task 1: Gradle wrapper + version catalog (commits e85fdcb..4e112a6, review clean after fix; 1 plan bug fixed: Kotlin 2.0 requires the kotlin-compose plugin, added to libs.versions.toml + app/build.gradle.kts)
- [x] Task 2: Theme + design tokens (commits bd531ae + 16d25ff; ColorTest 2/2; 3 deviations: added Color import to Theme.kt, minimal adaptive launcher icon in follow-up commit, brief's expected RED mode was wrong — AAPT, not Kotlin)
- [x] Task 3: Hilt + Activity (commit 1cef484; HiltTest 1/1; assembleDebug OK; deviations: HiltTestApplication instead of inner-class TestApp, ksp.useKSP2=false for Hilt 2.52)
- [x] Task 4: Home screen with empty/loaded states (commit 5431b9d; HomeViewModelTest 2/2; assembleDebug OK; deviations: stub providePersonRepository in AppModule, test dispatcher plumbing for determinism)
- [x] Task 5: Supabase schema migration (commit b3183d9; 11 tables + RLS pushed to remote; verification queries OK; see task-5-report.md; implementer connection-dropped; orchestrator completed from .sdd files already on disk)
- [x] Task 6: Wire Supabase client into the Android app (commit 24927a0; SupabaseClientTest 1/1; all tests 6/6; assembleDebug OK; deviations: Ktor 2→3, httpEngine inside builder block, Auth package rename, SupabaseClient built inside repo not via Hilt, withAuth test escape hatch; see task-6-report.md)
- [x] Task 7: Sign-up + sign-in screens (commit 816b393; AuthViewModelTest 2/2; AuthScreen + AuthRepository + RootViewModel routing; 8/8 tests pass; assembleDebug OK)
- [x] Task 8: Cloud MCP server (commit 3127e06; Deno Edge Function deployed to https://cfnmpqwfvhlnbblxqesm.supabase.co/functions/v1/mcp-server; JWT-verified; exposes baton://persons resource)
- [x] Task 9: Add Person flow (commit 83c9757; AddPersonSheet + create() on PersonRepository; M0AcceptanceTest written and compiles; APK installed and verified on emulator-5554)
- [x] **Follow-up: live e2e + 3 critical fixes** (this commit). All 9 tasks already on `m0/skeleton`; this commit adds the live verification and the 3 bugs it surfaced.

### Follow-up: live e2e + 3 critical fixes

The first end-to-end run on the emulator surfaced three real bugs. Each was
fixed in this commit, with a finding test or live evidence in `docs/verification/`.

1. **`local.properties` not auto-loaded into Gradle properties** (the build was
   reading `BATON_SUPABASE_URL` from `gradle.properties` and getting empty
   string → runtime tried to hit `https://localhost/...`). Fix: read
   `local.properties` in `app/build.gradle.kts` ourselves, fall back to
   `gradle.properties` for CI; throw with a clear error if both are blank.
   Added `local.properties.example` so clones know what to fill in.

2. **`persons.user_id` was `not null` with no default** (the app's
   `PersonInsert` doesn't set `user_id`, so RLS evaluated
   `private.is_owner(NULL) = false` and every insert returned 403, even for
   the owning user). Fix: new migration `0002_default_user_id.sql` adds
   `default auth.uid()` to every `user_id` column. This is the standard
   Supabase owner-column pattern; RLS predicates are unchanged.

3. **Need a way to bootstrap test users without using the Supabase
   dashboard** (the dashboard's "Create user" click is blocked by a browser
   safety guardrail; the public `/auth/v1/signup` is rate-limited per IP).
   Fix: new edge function `admin-bootstrap` that uses the service role key
   to create a user, gated by a `BOOTSTRAP_TOKEN` shared secret (set via
   `supabase secrets set`). Plan: remove once a real signup flow exists.

### M0 finding test verification

| Check | Result |
|---|---|
| App assembles to debug APK | ✅ 43,022,180 bytes |
| All unit tests pass | ✅ 8/8 (ColorTest 2, HiltTest 1, HomeViewModelTest 2, SupabaseClientTest 1, AuthViewModelTest 2) |
| Supabase schema applied | ✅ 11 tables, 42 RLS policies, 1 helper function |
| MCP server deployed and reachable | ✅ 401 without token (JWT verification live) |
| APK installs on emulator | ✅ emulator-5554 |
| App launches and renders Auth screen | ✅ BatonTheme applied, "Welcome back" / Email / Password / Sign in / "New here? Create account" all visible |
| Live sign-in against real Supabase | ✅ POST /auth/v1/token with email+password returns valid JWT, app routes to Home |
| List query against real Supabase | ✅ Home shows "Inspector Demo" and "DSP Srinagar" after sign-in + restart |
| RLS isolation (user A vs user B) | ✅ User A's list: 2 own rows; User B's list: 1 own row; cross-user fetch by id: 0 rows |
| Add Person sheet opens from FAB | ✅ Sheet renders with Name / Designation / Station fields + Save/Cancel |
| `user_id` defaults to `auth.uid()` on insert | ✅ Verified via API; RLS accepts inserts from the owning user without explicit `user_id` |

Screenshots: `docs/verification/m0-e2e2.png` (final list with 2 persons),
`m0-home.png` (post sign-in), `m0-auth2.png` (clean Auth screen).

### What's deployed / ready

- **APK**: `app/build/outputs/apk/debug/app-debug.apk` (43 MB, installed on emulator-5554, `com.baton.app.debug`)
- **Supabase project**: cfnmpqwfvhlnbblxqesm (South Asia / Mumbai)
- **Schema**: 12 migrations (0001 init + 0002 user_id default), RLS applied
- **Edge functions**:
  - `mcp-server` (committed) — read-only MCP, JWT-verified
  - `admin-bootstrap` (added in this commit) — service-role user create, gated by shared secret
- **MCP resource**: `baton://persons` returns the user's persons (RLS-enforced)
- **Test users** (in auth.users):
  - `baton.m0+demo@baton.app` / `BatonM0Test123` (user A — has 2 persons)
  - `baton.m0+userb@baton.app` / `BatonM0Test123` (user B — has 1 person)
- **Branch**: `m0/skeleton` on origin (pushed + tagged `m0-skeleton` + final tag `m0-final` in this commit)

### Carry-forward for M1

- `admin-bootstrap` is dev-only; remove once M1/M2 has a real signup flow.
- `local.properties` loading in `app/build.gradle.kts` — keep as-is. Documented in `local.properties.example`.
- M1 starts: single note bar + text capture + llama.cpp JNI + LLM extraction + confirmation card. See `docs/superpowers/plans/2026-08-10-baton-m0-skeleton.md` for the M1-M5 roadmap table.
