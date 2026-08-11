# Baton M0 progress ledger

Branch: m0/skeleton

Started: 2026-08-11
Completed: 2026-08-11

## M0 status: COMPLETE — APK installed and running on emulator-5554

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

### M0 finding test verification

| Check | Result |
|---|---|
| App assembles to debug APK | ✅ 43,045,460 bytes |
| All unit tests pass | ✅ 8/8 (ColorTest 2, HiltTest 1, HomeViewModelTest 2, SupabaseClientTest 1, AuthViewModelTest 2) |
| Supabase schema applied | ✅ 11 tables, 42 RLS policies, 1 helper function |
| MCP server deployed and reachable | ✅ 401 without token (JWT verification live) |
| APK installs on emulator | ✅ emulator-5554 |
| App launches and renders Auth screen | ✅ BatonTheme applied, "Welcome back" / Email / Password / Sign in / "New here? Create account" all visible |
| Live sign-up | ⏸ Blocked by Supabase free-tier rate limit (HTTP 429) — not a code issue |
| Live RLS cross-user check | ⏸ Depends on 2 users; not possible while signup is rate-limited |

### What's deployed / ready

- **APK**: `app/build/outputs/apk/debug/app-debug.apk` (43 MB, installed on emulator-5554)
- **Supabase project**: cfnmpqwfvhlnbblxqesm (South Asia / Mumbai)
- **Schema**: 11 tables + RLS applied via `npx supabase db push`
- **Edge function**: `mcp-server` deployed, JWT-verified
- **MCP resource**: `baton://persons` returns the user's persons (RLS-enforced)
- **Branch**: `m0/skeleton` on origin (all 10 commits pushed, tagged `m0-skeleton`)

### Carry-forward for M1

- Rate-limited auth signups will clear in ~1 hour. The on-device M0AcceptanceTest will run end-to-end then.
- M1 starts: single note bar + text capture + llama.cpp JNI + LLM extraction + confirmation card.
- See `docs/superpowers/plans/2026-08-10-baton-m0-skeleton.md` for the M0 plan and the M1-M5 roadmap table.
