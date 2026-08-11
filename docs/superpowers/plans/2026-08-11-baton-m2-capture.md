# Baton M2 — Voice + photo + sync

> **For agentic workers:** This plan continues from M1's `m1-capture` tag on `m0/skeleton`. M1 ships text capture + on-device LLM + save + calendar + share-target (text). M2 adds voice (Whisper.cpp) + photo (ML Kit OCR) + Supabase sync (Postgrest + Realtime).

**M2 scope (in):**
- **Photo capture**: in-app camera (or gallery pick), ML Kit Text Recognition v2, raw text → existing Extractor pipeline. New `mode=PHOTO` capture row, `image_uri` in `captures` table.
- **Voice capture**: Whisper.cpp JNI integrated as a sibling to M1's llama.cpp. Foreground mic service. Direct PCM push pattern. New `mode=VOICE` capture row, `audio_uri` in `captures` table.
- **Quick-settings tile + lock-screen widget**: one-tap voice capture from anywhere. Both are pre-reqs for the "always-available capture" UX the spec calls out.
- **Share-target ingest: images**: extend M1's text/plain activity-alias to also accept `image/*`. The OCR runs on the shared image, then the same confirmation card flow.
- **Supabase sync**: Postgrest for writes, Realtime for reads. Local Room mirror with sync queue. Conflict resolution: last-write-wins on `updated_at`.
- **Multi-device smoke test**: same Supabase user, two emulators, instruction created on A appears on B within 5s.

**M2 scope (out):**
- Brief scheduler + nudge drafts (M4)
- People list / person detail / tags (M3)
- AppState IPC with MindAnchor (M4)
- Release-signed APK (M5)

**Architecture delta from M1:**
- New native lib: `libwhisper.so` + small JNI surface (transcribe PCM byte array)
- Whisper model: `ggml-tiny.en.bin` (~75 MB, much smaller than Qwen 1.7B)
- Room DB with sync queue table; on every write, push to Supabase; on every read, fall back to Room, refresh in background
- Realtime subscription per table; on `postgres_changes` event, update Room + StateFlow

**Finding tests for M2:**
1. **FT-2.1** Photo capture: tap photo icon → grant camera → take picture of text → confirmation card shows with the OCR'd text
2. **FT-2.2** Voice capture: tap mic icon → grant RECORD_AUDIO → speak "Tell SHO Ramu to send FIR 47 by Friday" → confirmation card shows with the transcribed text
3. **FT-2.3** Image share: share an image from another app → Baton opens → OCR runs → confirmation card
4. **FT-2.4** Multi-device sync: open Baton on emulator-5554 + emulator-5556 (same user) → save an instruction on A → it appears on B within 5s (Realtime push)
5. **FT-2.5** Offline → online: airplane mode → save instruction → no error (writes to local) → back online → syncs within 10s

**Global constraints (carried from M0, all apply):**
- Min SDK 26, target 34, arm64-v8a only
- No red / overdue / shame language or colour tokens
- All data per-user via RLS
- No third-party analytics, no telemetry, no cloud AI
- No git operations outside the workspace
- Imperative-mood commit messages, one commit per task, all green tests at each commit

---

## Task 1: Extend share intent to accept `image/*`

**Files:**
- Modify: `app/src/main/AndroidManifest.xml` (add a second `<data>` element to the existing `<activity-alias>`)
- Modify: `app/src/main/java/com/baton/app/features/capture/ShareIntake.kt` (recognise image/*, return a tagged result that distinguishes text vs image)
- Modify: `app/src/main/java/com/baton/app/features/capture/ShareReceiverActivity.kt` (handle both)
- Modify: `app/src/test/java/com/baton/app/features/capture/ShareIntakeTest.kt` (add image path tests)

**Step 1.1: Manifest**

Add to the existing `<activity-alias>`:
```xml
<data android:mimeType="text/plain" />
<data android:mimeType="image/*" />
```

**Step 1.2: ShareIntake**

Replace `extractText` with a `Kind` enum + `extract()` returning a sealed result (text or image URI). M1's text path is preserved; new image path returns a `content://` URI from `Intent.EXTRA_STREAM`.

**Step 1.3: ShareReceiverActivity**

Branch on the kind: text → forward as today; image → call OCR (no-op stub for M2-T1; ML Kit wiring lands in T2) → forward the OCR'd text.

**Step 1.4: Tests**

8 existing tests + 3 new: `image/*` with valid `EXTRA_STREAM` returns the URI; `image/*` with no `EXTRA_STREAM` returns null; text-vs-image is dispatched correctly.

---

## Task 2: Photo capture + ML Kit OCR

**Files:**
- Add dep: `com.google.mlkit:text-recognition:16.0.1` to `libs.versions.toml` + `app/build.gradle.kts`
- Create: `app/src/main/java/com/baton/app/features/capture/PhotoCapture.kt` (CameraX wrapper + ML Kit `TextRecognizer`)
- Modify: `app/src/main/java/com/baton/app/features/capture/CaptureMode.kt` (already has PHOTO; wire the icon)
- Modify: `app/src/main/java/com/baton/app/features/capture/NoteBar.kt` (enable the camera icon; opens camera)
- Modify: `app/src/main/java/com/baton/app/features/capture/CaptureViewModel.kt` (`onPhotoCaptured(uri)` slot)
- Create: `app/src/test/java/com/baton/app/features/capture/PhotoCaptureTest.kt` (Robolectric)

**Step 2.1: ML Kit wiring**

ML Kit `TextRecognition.getClient(LATIN)`. Pure-Kotlin API: `recognizer.process(InputImage.fromFilePath(ctx, uri))`.

**Step 2.2: CameraX**

`ActivityResultContracts.TakePicture()` to launch the system camera; pre-create a file in `cacheDir/captures/`, pass the `FileProvider` URI as the destination.

**Step 2.3: Wire to NoteBar**

The camera icon on the NoteBar (currently greyed out per the M1 plan) becomes enabled. Tap → camera launches → result comes back → VM calls `PhotoCapture.recognize(uri)` → text fed to existing `onExtract` flow.

**Step 2.4: Capture row**

The `captures` row gets `mode=PHOTO`, `raw_text=<OCR result>`, `image_uri=<FileProvider URI>`. Already supported by the M0 schema and the M1-T2 SupabaseCaptureRepository.

**Step 2.5: Test**

Robolectric test: given a fixture image (write a small PNG with text on it to `cacheDir`), `PhotoCapture.recognize` returns the expected text. Skip if the CI environment can't render Tesseract; fall back to a unit test of the URI-handling logic.

---

## Task 3: Whisper.cpp JNI

**Files:**
- Add: `app/src/main/cpp/whisper_jni.cpp` (PCM byte array → String transcript)
- Modify: `app/src/main/cpp/CMakeLists.txt` (add whisper.cpp as a sub-library, vendored b4600 alongside llama.cpp)
- Modify: `app/build.gradle.kts` (extend `vendorLlamaCpp` task to vendor whisper.cpp; or add a parallel `vendorWhisperCpp`)
- Create: `app/src/main/assets/whisper_url.txt` (URL to ggml-tiny.en.bin)
- Create: `app/src/main/assets/whisper_sha256.txt`
- Create: `app/src/main/java/com/baton/app/ai/whisper/WhisperBridge.kt` (Kotlin facade)
- Create: `app/src/main/java/com/baton/app/ai/whisper/WhisperModelManager.kt` (downloads the model)
- Create: `app/src/test/java/com/baton/app/ai/whisper/WhisperBridgeTest.kt` (interface test; real inference gated on a real device)

**Step 3.1: Vendor whisper.cpp**

The M1 `vendorLlamaCpp` Gradle task fetches llama.cpp at `b4600` into `app/src/main/cpp/llama-cpp/`. Add a parallel `vendorWhisperCpp` for whisper.cpp. The same `b4600` tag carries both projects.

**Step 3.2: JNI surface**

`nativeTranscribe(pcmBytes: ByteArray, sampleRate: Int = 16000): String` — pushes PCM (16-bit little-endian, mono) into whisper.cpp's `whisper_full`, returns the concatenated text.

**Step 3.3: Model download**

`whisper_url.txt` points to `https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny.en.bin` (~75 MB). SHA-256 verified on first download.

**Step 3.4: Test**

Pure-JVM: assert that `WhisperBridge.transcribe` returns the right error when not loaded. Real inference: integration test on a real device (emulator CPU is too slow for <2s Whisper per the M1 plan's note).

---

## Task 4: Voice capture — foreground service + mic button

**Files:**
- Add: `app/src/main/java/com/baton/app/features/capture/VoiceCaptureService.kt` (foreground `microphone` type; owns the mic + WhisperBridge)
- Add: `app/src/main/AndroidManifest.xml` (`RECORD_AUDIO`, `FOREGROUND_SERVICE_MICROPHONE` permissions, `<service>` declaration)
- Modify: `app/src/main/java/com/baton/app/features/capture/NoteBar.kt` (enable the mic icon; starts the service)
- Modify: `app/src/main/java/com/baton/app/features/capture/CaptureViewModel.kt` (consume the service's transcript via a Channel<ByteArray> / Flow<String>)
- Create: `app/src/test/java/com/baton/app/features/capture/VoiceCaptureServiceTest.kt`

**Step 4.1: Foreground service**

`Service` with `foregroundServiceType="microphone"`, `AudioRecord` at 16 kHz, pushes PCM chunks to WhisperBridge. On stop, emits the final transcript to a `Channel<String>` that the VM consumes.

**Step 4.2: Mic icon**

The mic icon on the NoteBar becomes enabled. Tap → start the service (which requests `RECORD_AUDIO` runtime permission first if not granted). The service is one-shot: starts, records, transcribes, emits, stops.

**Step 4.3: Wire to VM**

`onVoiceTranscript(text)` slot: feeds the text into `onTextChanged()` + `onExtract()` — same as the share path. The capture row is `mode=VOICE`, `audio_uri=<file>`.

**Step 4.4: Test**

Robolectric test for the service lifecycle. Real audio: integration test on a real device.

---

## Task 5: Quick-settings tile + lock-screen widget

**Files:**
- Add: `app/src/main/java/com/baton/app/features/capture/VoiceTileService.kt` (TileService)
- Add: `app/src/main/java/com/baton/app/features/capture/VoiceCaptureWidget.kt` (AppWidgetProvider for the lock screen)
- Modify: `app/src/main/AndroidManifest.xml` (declare both)

**Step 5.1: Quick-settings tile**

A `TileService` that, when clicked, starts `VoiceCaptureService` from the background. Tiles are visible in the system quick-settings shade.

**Step 5.2: Lock-screen widget**

A minimal `AppWidget` with a single mic button. Tapping it launches MainActivity with a deep-link that starts the voice capture flow.

**Step 5.3: Test**

Smoke test: `adb shell cmd statusbar add-tile com.baton.app.debug/.features.capture.VoiceTileService` then tap the tile. Live e2e on emulator.

---

## Task 6: Local Room mirror + sync queue + Postgrest writer

**Files:**
- Add deps: `androidx.room:room-runtime`, `room-ktx`, `room-compiler` (already in libs.versions.toml per M0 but maybe unused)
- Create: `app/src/main/java/com/baton/app/data/local/AppDatabase.kt` (Room)
- Create: `app/src/main/java/com/baton/app/data/local/entities/{PersonEntity, InstructionEntity, CaptureEntity, SyncQueueEntity}.kt`
- Create: `app/src/main/java/com/baton/app/data/local/Converters.kt` (enum ↔ String)
- Create: `app/src/main/java/com/baton/app/data/sync/SyncEngine.kt` (drains the queue, posts to Supabase)
- Create: `app/src/main/java/com/baton/app/data/sync/SyncWorker.kt` (WorkManager worker, periodic drain)
- Modify: `app/src/main/java/com/baton/app/data/captures/SupabaseCaptureRepository.kt` etc. to write-through to Room
- Modify: `app/src/main/java/com/baton/app/ui/home/HomeViewModel.kt` to read from Room (with a network refresh)

**Step 6.1: Room schema**

Mirrors the Supabase tables: `persons`, `instructions`, `captures`, plus a `sync_queue` table (`id, table, row_id, op [INSERT/UPDATE/DELETE], payload_json, created_at, attempts`).

**Step 6.2: Repositories write-through**

Every `create()` / `update()` call:
1. Insert into Room.
2. Enqueue a sync operation.
3. Trigger `SyncEngine.drain()` (which immediately calls Postgrest if online, else leaves the queue for the next worker tick).

**Step 6.3: Read path**

The home / capture flow always reads from Room (fast, offline-first). A background `SyncEngine.refresh()` pulls from Supabase and updates Room.

**Step 6.4: Test**

Unit test of `SyncEngine` with `MockEngine` (Postgrest mock) + in-memory Room. The end-to-end "create locally → sync → read back" path is the FT-2.5 offline test.

---

## Task 7: Realtime subscription

**Files:**
- Add dep: `supabase-realtime-kt` (already in libs.versions.toml)
- Create: `app/src/main/java/com/baton/app/data/sync/RealtimeSync.kt` (subscribes to `postgres_changes` on each table)
- Modify: `app/src/main/java/com/baton/app/data/sync/SyncEngine.kt` (wires Realtime events → Room updates)

**Step 7.1: Subscribe**

`client.realtime.channel("baton-sync").on("postgres_changes", { event, payload -> ... })`. Per-table subscription (one for persons, one for instructions, one for captures).

**Step 7.2: Apply events**

On `INSERT` / `UPDATE`: upsert into Room. On `DELETE`: remove. RLS ensures we only get events for our own rows.

**Step 7.3: Test**

Multi-device: same Supabase user on two emulators. Create an instruction on A → B's Room is updated via Realtime → Home re-renders. End-to-end finding test (FT-2.4).

---

## Task 8: Conflict resolution + m2-capture tag

**Files:**
- Modify: `app/src/main/java/com/baton/app/data/sync/SyncEngine.kt` (add `updated_at`-based last-write-wins)
- Add: `app/src/main/java/com/baton/app/data/sync/SyncConflict.kt` + `sync_conflicts` table (audit trail of dropped writes)

**Step 8.1: Last-write-wins**

On a write, compare the local `updated_at` with the server's `updated_at`. If the server is newer, drop the local write and log to `sync_conflicts`.

**Step 8.2: Live e2e**

Two emulators, same user. Edit a person on A while offline on B. Reconnect B. The A-edit wins (it was later). The B-edit is logged in `sync_conflicts`.

**Step 8.3: Tag `m2-capture`**

Same as M1: `git tag -a m2-capture -m "..."` and push.

---

## Carry-forward to M3

- The People list (Home tab) is already wired (M0). M3 adds the badge + person detail.
- The MCP server is currently the minimal `baton://persons` resource. M3 expands to all 7 resources + 4 tools.
- The "is_sensitive" flag is set on the schema but never read. M3 hooks it into the sync engine.
