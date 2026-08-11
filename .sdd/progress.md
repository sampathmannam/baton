# Baton M0 progress ledger

Branch: m0/skeleton

Started: 2026-08-11


## Tasks


- [x] Task 1: Gradle wrapper + version catalog (commits e85fdcb..4e112a6, review clean after fix; 1 plan bug fixed: Kotlin 2.0 requires the kotlin-compose plugin, added to libs.versions.toml + app/build.gradle.kts)
- [x] Task 2: Theme + design tokens (commits bd531ae + 16d25ff; ColorTest 2/2; 3 deviations: added Color import to Theme.kt, minimal adaptive launcher icon in follow-up commit, brief's expected RED mode was wrong — AAPT, not Kotlin)
- [x] Task 3: Hilt + Activity (commit 1cef484; HiltTest 1/1; assembleDebug OK; deviations: HiltTestApplication instead of inner-class TestApp, ksp.useKSP2=false for Hilt 2.52)
- [x] Task 4: Home screen with empty/loaded states (commit 5431b9d; HomeViewModelTest 2/2; assembleDebug OK; deviations: stub providePersonRepository in AppModule, test dispatcher plumbing for determinism)
- [x] Task 5: Supabase schema migration (commit b3183d9; 11 tables + RLS pushed to remote; verification queries OK; see task-5-report.md; implementer connection-dropped; orchestrator completed from .sdd files already on disk)
- [x] Task 6: Wire Supabase client into the Android app (commit 24927a0; SupabaseClientTest 1/1; all tests 6/6; assembleDebug OK; deviations: Ktor 2→3, httpEngine inside builder block, Auth package rename, SupabaseClient built inside repo not via Hilt, withAuth test escape hatch; see task-6-report.md)
- [ ] Task 7: Sign-up + sign-in screens
- [ ] Task 8: Minimal cloud MCP server
- [ ] Task 9: M0 finding test (end-to-end)
