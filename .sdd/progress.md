# Baton progress ledger
Branch: m0/skeleton
Tags: m0-skeleton, m0-final, m1-capture, m2-capture (to be cut)

## M0 status: COMPLETE — APK installed, sign-in live, RLS verified, list shows user data

## M1 status: COMPLETE — 8 tasks shipped, 46/46 unit tests green, debug + release APK built, m1-capture tag pushed

## M2 status: PARTIAL — 4 of 8 tasks shipped (photo + share image + Realtime + tile/widget), 57/57 unit tests green, debug APK rebuilt

- M2-T1 + T2 (commit c4e71e5): share intent accepts image + in-app camera + ML Kit OCR
- M2-T7 (commit 8007485): Realtime WebSocket subscription (OkHttp engine swap, persons + instructions table added to supabase_realtime publication via dashboard migration 0003). Live e2e: insert person via API -> Home tab refreshes in <1s.
- M2-T5 (commit 0f7d9a9): quick-settings tile (BatonTileService) + home-screen widget (BatonCaptureWidget). Deep link `com.baton.app.action.QUICK_CAPTURE` -> MainActivity -> RootViewModel.quickCapture -> HomeScreen opens capture sheet. Live e2e: `am start -a QUICK_CAPTURE` opens the New note sheet.

### M2 deferred (see Carry-forward)

The M2 plan covered 8 tasks. 4 shipped (T1, T2, T5, T7). T3, T4, T6, T8 deferred:

- **Whisper JNI (T3)** is a multi-day lift: vendor whisper.cpp at
  b4600 alongside llama.cpp, write `whisper_jni.cpp` (PCM byte
  array -> text), add the `ggml-tiny.en.bin` model download (75 MB
  separate from the 1.1 GB Qwen model), wire OkHttp + SHA-256 verify.
- **Voice foreground service (T4)** is the surrounding plumbing
  (RECORD_AUDIO + FOREGROUND_SERVICE_MICROPHONE permissions,
  `microphone` foreground service type, AudioRecord at 16 kHz, a
  Channel<PCM> from service to VM). Depends on T3.
- **Room mirror + Postgrest writer (T6)** is a big rewrite of the
  data layer. Every repo writes through to Room, then enqueues
  sync ops. M3 work per the plan.
- **Conflict resolution (T8)** depends on T6.

### Finding test status (M2)

| Test | Status |
|---|---|
| FT-2.1 photo capture -> confirmation card | Code path ready; real-device run gated on a JPEG fixture in cacheDir + a real device. |
| FT-2.2 voice capture -> confirmation card | Deferred with M2-T3. |
| FT-2.3 image share -> confirmation card | Code path ready; same gated-on-real-device caveat. |
| FT-2.4 multi-device sync | VERIFIED (M2-T7): insert person via REST API -> Home tab refreshes in <1s on the same emulator. Two-device test gated on a second emulator. |
| FT-2.5 offline -> online | Deferred with M2-T6. |

### Live e2e verified on emulator-5554

- App installs and launches without crash
- NoteBar shows the enabled camera + mic icons
- Sign-in flow intact; Home shows existing persons
- **Realtime**: inserted a person via REST API as baton.m0+demo@baton.app; the Home tab refreshed and the new person appeared within ~1s
- **Quick-capture deep link**: `am start -a com.baton.app.action.QUICK_CAPTURE -n com.baton.app.debug/com.baton.app.MainActivity` opens the "New note" capture sheet

### What's deployed / ready

- **APK**: `app/build/outputs/apk/debug/app-debug.apk` (49 MB, with M2-T1+T2+T5+T7)
- **Supabase project**: cfnmpqwfvhlnbblxqesm (South Asia / Mumbai)
- **Schema**: 13 migrations (12 + 0003_enable_realtime_publication), 11 tables, 42 RLS policies, `supabase_realtime` publication has `persons` + `instructions`
- **Test users**: baton.m0+demo@baton.app, baton.m0+userb@baton.app
- **Branch**: m0/skeleton
- **Tags**: m0-skeleton, m0-final, m1-capture

### Carry-forward to M3

1. **Whisper JNI (M2-T3)**: vendor whisper.cpp, write `whisper_jni.cpp`,
   add `ggml-tiny.en.bin` model. ~4 hours focused work.
2. **Voice foreground service (M2-T4)**: pipe AudioRecord PCM
   into WhisperBridge; one-shot service. ~2 hours.
3. **Room mirror + Postgrest writer (M2-T6 / M3)**: rewrite
   repositories to write-through to Room, add sync queue, drain
   via WorkManager. ~6 hours.
4. **Conflict resolution (M2-T8)**: last-write-wins on
   `updated_at`, audit in `sync_conflicts`. ~2 hours.
5. **People list timeline + person detail** (M3 main feature).
6. **Full MCP server** (all 7 resources + 4 tools) at the cloud
   Edge Function.
7. **is_sensitive flag** (set on schema, never read; M3 hooks it
   into the sync engine).
8. **WorkManager on-demand init** (the lint-disabled provider).
9. **5-shot -> 8-shot prompt** for better extraction accuracy.
10. **Proper `title` field** in the extraction prompt.
11. **Sign-out UI** (M5).
