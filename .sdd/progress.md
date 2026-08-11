# Baton progress ledger

Branch: m0/skeleton
Tags: m0-skeleton, m0-final, m1-capture

## M0 status: COMPLETE — APK installed, sign-in live, RLS verified, list shows user data

## M1 status: COMPLETE — 8 tasks shipped, 46/46 unit tests green, debug + release APK built, m1-capture tag pushed

## M2 status: PARTIAL — 2 of 8 tasks shipped (photo + share image), 52/52 unit tests green, debug APK rebuilt

### M2-T1 + M2-T2: share intent accepts image + in-app camera + ML Kit OCR
- commit c4e71e5
- ShareIntake.inspect(intent) returns `Result.Text` or `Result.Image`
- ShareReceiverActivity handles both; images flow through `PhotoCapture.recognize` (ML Kit TextRecognition.getClient(LATIN)) then forward to MainActivity
- CameraLauncher wraps `ActivityResultContracts.TakePicture` + FileProvider; `newCaptureUri(context)` writes a placeholder JPEG in cacheDir/captures/ and hands the camera a content:// URI
- NoteBar now exposes `onTextClick`, `onCameraClick`, `onMicClick`; camera icon is enabled and clickable
- CaptureViewModel.onPhotoTextRecognized(text) pre-fills the sheet and opens it
- AndroidManifest: CAMERA permission + FileProvider for cacheDir/captures/ + camera feature
- 14 ShareIntakeTest cases (8 → 14): text, image (png/jpeg/star), null MIME, missing extras
- Live e2e on emulator-5554: app launches with the new APK, NoteBar shows the enabled camera + mic icons

### M2 status: T3-T8 deferred (see Carry-forward)

The M2 plan covered 8 tasks. T1+T2 (photo + share image) shipped. T3-T8
(Whisper.cpp JNI, voice foreground service, quick-settings tile, lock-
screen widget, Room mirror, Postgrest + Realtime sync, conflict
resolution, multi-device smoke test) are deferred. Rationale:

- **Whisper JNI (T3)** is a multi-day lift: vendor whisper.cpp at
  b4600 alongside llama.cpp, write `whisper_jni.cpp` (PCM byte
  array → text), add the `ggml-tiny.en.bin` model download (75 MB
  separate from the 1.1 GB Qwen model), wire OkHttp + SHA-256 verify.
  Realistic in a 4-hour focused session; out of scope for this
  drive.
- **Voice foreground service (T4)** is the surrounding plumbing
  (RECORD_AUDIO + FOREGROUND_SERVICE_MICROPHONE permissions,
  `microphone` foreground service type, AudioRecord at 16 kHz, a
  Channel<PCM> from service to VM). Depends on T3.
- **Quick-settings tile + lock-screen widget (T5)** is the
  always-available UX. Depends on T4.
- **Room mirror + Postgrest writer (T6)** is a big rewrite of the
  data layer. Every repo writes through to Room, then enqueues
  sync ops. M3 work.
- **Realtime subscription (T7)** is smaller than T6 — a single
  `client.realtime.channel(...).on("postgres_changes", ...)` per
  table that re-fetches on event. M2.1 candidate.
- **Conflict resolution (T8)** depends on T6.

### Finding test status (M2)

| Test | Status |
|---|---|
| FT-2.1 photo capture → confirmation card | Code path ready; real-device run gated on a JPEG fixture in cacheDir + a real device (the emulator camera can take a picture but the OCR'd text depends on the lighting + the printed sample). |
| FT-2.2 voice capture → confirmation card | Deferred with M2-T3. |
| FT-2.3 image share → confirmation card | Code path ready; same gated-on-real-device caveat. |
| FT-2.4 multi-device sync | Deferred with M2-T7. |
| FT-2.5 offline → online | Deferred with M2-T6. |

### Live e2e verified on emulator-5554

- App installs and launches with the M2-T1+T2 APK without crash
- NoteBar shows the enabled camera + mic icons (previously 0.4 alpha)
- Sign-in flow intact; Home shows existing persons

### What's deployed / ready

- **APK**: `app/build/outputs/apk/debug/app-debug.apk` (rebuilt with M2 photo path)
- **Supabase project**: cfnmpqwfvhlnbblxqesm (South Asia / Mumbai)
- **Schema**: 12 migrations, 11 tables, 42 RLS policies
- **Test users**: baton.m0+demo@baton.app, baton.m0+userb@baton.app
- **Branch**: m0/skeleton
- **Tags**: m0-skeleton, m0-final, m1-capture

### Carry-forward to M2.1 / M3

1. **Whisper JNI (M2-T3)**: vendor whisper.cpp, write `whisper_jni.cpp`,
   add `ggml-tiny.en.bin` model. ~4 hours focused work.
2. **Voice foreground service (M2-T4)**: pipe AudioRecord PCM
   into WhisperBridge; one-shot service. ~2 hours.
3. **Quick-settings tile + lock-screen widget (M2-T5)**: TileService
   + AppWidget. ~1 hour.
4. **Room mirror + Postgrest writer (M2-T6 / M3)**: rewrite
   repositories to write-through to Room, add sync queue, drain
   via WorkManager. ~6 hours.
5. **Realtime subscription (M2-T7)**: per-table postgres_changes
   subscription that triggers a re-fetch. ~2 hours. Can ship
   before the Room mirror; the Home tab just needs a small
   refactor.
6. **Conflict resolution (M2-T8)**: last-write-wins on
   `updated_at`, audit in `sync_conflicts`. ~2 hours.

### Carry-forward to M3 (from M1 progress)

- People list (Home tab) already wired (M0). M3 adds the badge
  + person detail timeline.
- Full MCP server (all 7 resources + 4 tools) at the cloud
  Edge Function.
- The "is_sensitive" flag is set on the schema but never read.
  M3 hooks it into the sync engine.
- Voice (Whisper.cpp) + photo (ML Kit OCR) on the note bar —
  **photo is DONE in M2-T2; voice still pending M2-T3**.
- WorkManager on-demand init (the lint-disabled provider).
- 5-shot prompt could grow to 8-shot for better accuracy.
- The M1 "title" is `action + ' — ' + person`; M2 prompt should
  return a proper `title` field.
- Sign-out UI (M5).
