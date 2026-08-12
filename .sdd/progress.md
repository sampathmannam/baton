# Baton progress ledger
Branch: m0/skeleton
Tags: m0-skeleton, m0-final, m1-capture, m2-capture

## M0 status: COMPLETE — APK installed, sign-in live, RLS verified, list shows user data

## M1 status: COMPLETE — 8 tasks shipped, 46/46 unit tests green, debug + release APK built, m1-capture tag pushed

## M2 status: PARTIAL — 5 of 8 tasks shipped (photo + share image + Realtime + tile/widget + Room mirror), 76/76 unit tests green, debug + release APK built

- M2-T1 + T2 (commit c4e71e5): share intent accepts image + in-app camera + ML Kit OCR
- M2-T7 (commit 8007485): Realtime WebSocket subscription (OkHttp engine swap, persons + instructions table added to supabase_realtime publication via dashboard migration 0003). Live e2e: insert person via API -> Home tab refreshes in <1s.
- M2-T5 (commit 0f7d9a9): quick-settings tile (BatonTileService) + home-screen widget (BatonCaptureWidget). Deep link `com.baton.app.action.QUICK_CAPTURE` -> MainActivity -> RootViewModel.quickCapture -> HomeScreen opens capture sheet. Live e2e: `am start -a QUICK_CAPTURE` opens the New note sheet.
- M2-T6 (commit 75e7c43): **Room mirror + write-through sync queue**. Architecture change: UI reads from Room (Flow), writes go through Room + sync outbox + drain to Supabase. Live e2e: created "Room Test_M2T6 / ACP" via AddPersonSheet, appeared in Home within ~1s, verified reach at Supabase REST (id bdd07555-...). Tests: 76/76 green.

### M2 deferred (see Carry-forward)

The M2 plan covered 8 tasks. 5 shipped (T1, T2, T5, T6, T7). T3, T4, T8 deferred:

- **Whisper JNI (T3)** is a multi-day lift: vendor whisper.cpp at
  b4600 alongside llama.cpp, write `whisper_jni.cpp` (PCM byte
  array -> text), add the `ggml-tiny.en.bin` model download (75 MB
  separate from the 1.1 GB Qwen model), wire OkHttp + SHA-256 verify.
- **Voice foreground service (T4)** is the surrounding plumbing
  (RECORD_AUDIO + FOREGROUND_SERVICE_MICROPHONE permissions,
  `microphone` foreground service type, AudioRecord at 16 kHz, a
  Channel<PCM> from service to VM). Depends on T3.
- **Conflict resolution (T8)** depends on the Room mirror. The
  SyncEngine's `recordFailure` / `lastError` columns are in place;
  T8 adds the last-write-wins merge logic.

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
