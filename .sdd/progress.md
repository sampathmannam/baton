# Baton progress ledger
Branch: m0/skeleton
Tags: m0-skeleton, m0-final, m1-capture, m2-capture, m2-final

## M0 status: COMPLETE — APK installed, sign-in live, RLS verified, list shows user data

## M1 status: COMPLETE — 8 tasks shipped, 46/46 unit tests green, debug + release APK built, m1-capture tag pushed

## M2 status: COMPLETE — 8/8 tasks shipped, 94/94 unit tests green, debug + release APK built, **m2-final tag + GitHub release pushed**

- M2-T1 + T2 (commit c4e71e5): share intent accepts image + in-app camera + ML Kit OCR
- M2-T7 (commit 8007485): Realtime WebSocket subscription (OkHttp engine swap, persons + instructions table added to supabase_realtime publication via dashboard migration 0003). Live e2e: insert person via API -> Home tab refreshes in <1s.
- M2-T5 (commit 0f7d9a9): quick-settings tile (BatonTileService) + home-screen widget (BatonCaptureWidget). Deep link `com.baton.app.action.QUICK_CAPTURE` -> MainActivity -> RootViewModel.quickCapture -> HomeScreen opens capture sheet. Live e2e: `am start -a QUICK_CAPTURE` opens the New note sheet.
- M2-T6 (commit 75e7c43): **Room mirror + write-through sync queue**. Architecture change: UI reads from Room (Flow), writes go through Room + sync outbox + drain to Supabase. Live e2e: created "Room Test_M2T6 / ACP" via AddPersonSheet, appeared in Home within ~1s, verified reach at Supabase REST (id bdd07555-...). Tests: 76/76 green.
- M2-T8 (commit 04b5f84): **last-write-wins conflict resolution** on the `persons` sync queue. SyncEngine now compares local `updated_at` against the server's via `findById`; if the server is newer, the local write is dropped and a row is inserted into `sync_conflicts` for the audit trail. The server's state is mirrored into Room. 6 new tests: server-newer drops + logs, local-newer proceeds, server-null proceeds, no conflict on equal timestamps, multiple conflicts, observe flow ordering. Tests: 82/82 green.
- M2-T3 (commit 912630e): **Whisper.cpp JNI structure** for voice capture. New files: `app/src/main/cpp/whisper_jni.cpp` (PCM-16 -> text via `whisper_full`), `app/src/main/java/com/baton/app/ai/whisper/WhisperBridge.kt` (Kotlin facade), `WhisperError.kt` (sealed errors), `WhisperModelManager.kt` (~75 MB model download with SHA-256 verify). Gradle `vendorWhisperCpp` task downloads whisper.cpp v1.6.0 (last release compatible with the b4600-era ggml that llama.cpp vendors). 8 new tests. Tests: 90/90 green.
- M2-T4 (commit c274b57): **VoiceCaptureService** (microphone foreground service) + mic button wired in NoteBar. AudioRecord at 16 kHz mono PCM-16; recording is streamed to a temp .pcm file, then on stop handed to WhisperBridge.transcribe(). The service sends the transcript (or error) back to the Activity via `ResultReceiver`. Manifest: RECORD_AUDIO + FOREGROUND_SERVICE + FOREGROUND_SERVICE_MICROPHONE perms; `foregroundServiceType="microphone"` on API 34+. Live e2e: tap mic -> permission dialog -> grant -> foreground service starts -> AudioFlinger thread "ready to run" -> mic icon at top of status bar turns green (active recording). 4 new VM tests. Tests: 94/94 green.
- M2 build (commit 1db2691): whisper.cpp v1.6.0 vendor + JNI updated for v1.6+ API (n_threads moved from whisper_context_params to whisper_full_params) + .gitignore for the vendored tree.

### M2 finding-tests status
- **FT-2.1** Photo capture: code path ready, live e2e on real device gated.
- **FT-2.2** Voice capture: code path ready, real audio gated on a real device.
- **FT-2.3** Image share: code path ready, live e2e on real device gated.
- **FT-2.4** Multi-device sync: **VERIFIED** via M2-T7 (Realtime push) + M2-T6 (Room mirror).
- **FT-2.5** Offline -> online: **VERIFIED** via M2-T6 unit test (create keeps local row when remote fails; sync queue retains with `attempts++` and `lastError`).

### m2-final tag + GitHub release

Tag `m2-final` at commit 1db2691; release "Baton M2 Final: Voice + Photo + Sync" at https://github.com/sampathmannam/baton/releases/tag/m2-final with debug + release-unsigned APKs attached.

### Finding test status (M2)

| Test | Status |
|---|---|
| FT-2.1 photo capture -> confirmation card | Code path ready; real-device run gated on a JPEG fixture in cacheDir + a real device. |
| FT-2.2 voice capture -> confirmation card | Deferred with M2-T3. |
| FT-2.3 image share -> confirmation card | Code path ready; same gated-on-real-device caveat. |
| FT-2.4 multi-device sync | **VERIFIED** (M2-T7 + T6): insert person via REST API -> Room is updated -> Home tab Flow re-emits -> UI updates within <1s. |
| FT-2.5 offline -> online | **VERIFIED** (M2-T6): Unit test `create keeps local row when remote fails (offline tolerance)` proves the sync queue retains the entry with attempts++ and lastError; the next drain retries. Live e2e to follow. |

### Live e2e verified on emulator-5554

- App installs and launches without crash
- NoteBar shows the enabled camera + mic icons
- Sign-in flow intact; Home shows existing persons
- **Realtime**: inserted a person via REST API as baton.m0+demo@baton.app; the Home tab refreshed and the new person appeared within ~1s
- **Quick-capture deep link**: `am start -a com.baton.app.action.QUICK_CAPTURE -n com.baton.app.debug/com.baton.app.MainActivity` opens the "New note" capture sheet
- **Room mirror + write-through (M2-T6)**: created "Room Test_M2T6 / ACP" via AddPersonSheet; the row appeared in Home within ~1s; the REST API shows the same row at Supabase with the same client-generated UUID

### What's deployed / ready

- **APK (debug)**: `app/build/outputs/apk/debug/app-debug.apk` (49 MB, with M2-T1+T2+T5+T6+T7)
- **APK (release)**: `app/build/outputs/apk/release/app-release-unsigned.apk` (38 MB)
- **Supabase project**: cfnmpqwfvhlnbblxqesm (South Asia / Mumbai)
- **Schema**: 13 migrations (12 + 0003_enable_realtime_publication), 11 tables, 42 RLS policies, `supabase_realtime` publication has `persons` + `instructions`
- **Local DB**: Room mirror of `persons` / `instructions` / `captures` + `sync_queue` (encryption deferred to M3 audit)
- **Test users**: baton.m0+demo@baton.app, baton.m0+userb@baton.app
- **Branch**: m0/skeleton
- **Tags**: m0-skeleton, m0-final, m1-capture, m2-capture

### Carry-forward to M3

1. **M2-T8 conflict resolution**: last-write-wins on `updated_at`,
   audit in `sync_conflicts`. ~2 hours. `SyncEngine` already
   tracks `attempts` and `lastError`; the merge rule lands in
   the M2-T8 commit.
2. **M2-T3 + T4 Whisper + voice service**: vendor whisper.cpp,
   write `whisper_jni.cpp`, add `ggml-tiny.en.bin` model, wire a
   `microphone` foreground service. ~6 hours total.
3. **M3 People list timeline + person detail**: per-person
   instruction history, person detail screen.
4. **MCP server expansion**: all 7 resources + 4 tools at the
   cloud Edge Function.
5. **is_sensitive flag**: schema has it, M3 hooks it into the
   sync engine.
6. **WorkManager on-demand init** (the lint-disabled provider):
   schedule SyncEngine.drainAll() on a periodic + connectivity
   trigger.
7. **SQLCipher on the local DB**: the dep is in `build.gradle.kts`
   but the M2-T6 build opens a plain Room DB. The key derivation
   lands with the privacy audit (M5).
8. **5-shot -> 8-shot prompt** for better extraction accuracy.
9. **Proper `title` field** in the extraction prompt.
10. **Sign-out UI** (M5).
