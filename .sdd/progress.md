# Baton M1 progress ledger

Branch: m0/skeleton
Tag: m1-capture at acf8c2e

Started: 2026-08-11
Completed: 2026-08-11 (skeleton → live e2e) → 2026-08-11 (M1 capture + save + share + calendar)

## M0 status: COMPLETE — APK installed, sign-in live, RLS verified, list shows user data

## M1 status: COMPLETE — 8 tasks shipped, 46/46 unit tests green, debug + release APK built, m1-capture tag pushed

### M1-T1: Single note bar UI + capture sheet (text-only)
- commit 1fbb677
- NoteBar (floating), CaptureSheet (ModalBottomSheet), CaptureViewModel + UiState
- 8/8 unit tests pass
- Fix: ModalBottomSheet.onDismissRequest syncs VM so scrim/BACK/ESC re-arm openSheet()

### M1-T2: Captures table write-through to Supabase
- commit c5ffccf
- data/captures/{Capture, CaptureRepository, SupabaseCaptureRepository}
- 8/8 unit tests pass
- Fix: AGP-default gitignored `captures/`; re-included `data/captures/`

### M1-T3: llama.cpp JNI integration
- commit fd64850
- app/src/main/cpp/{CMakeLists.txt, llama_jni.cpp, llama-cpp/ (vendored b4600)}
- ai/llama/{LlamaBridge, ModelManager, LlamaError}
- 13 MB native libs: libbaton-llama.so + libllama.so + libggml-{base,cpu}.so + libc++_shared + libomp
- NDK 27.3.13750724 + CMake 3.22.1 + arm64-v8a only

### M1-T4: On-device LLM extraction + GBNF grammar + confirmation card
- commit d3f8678
- assets/prompts/extract_v1.txt (5-shot prompt)
- assets/grammars/instruction.gbnf (constrained JSON)
- ai/extraction/Extractor.kt + Hilt binding in CaptureModule
- features/capture/ConfirmationCard.kt (High/Medium/Low confidence chip, no red)
- di/AppModule.kt provides OkHttpClient
- 21/21 unit tests pass (5 new ExtractorTest)
- Live e2e on emulator-5554: app launches, NoteBar opens sheet, sheet re-opens after dismiss

### M1-T5: Save confirmation → instructions + persons (FT-1.2, FT-1.3)
- commit 53389a6
- data/instructions/{Instruction, InstructionRepository, SupabaseInstructionRepository}
- SupabasePersonRepository.findByName + findOrCreate
- CaptureViewModel.onConfirm: findOrCreate person + create instruction
- Prompt fix: URGENT → HIGH (schema enum is LOW/NORMAL/HIGH)
- 27/27 unit tests pass (3 new SupabaseInstructionRepositoryTest with MockEngine, 3 new CaptureViewModelTest save flow tests)
- Live e2e: programmatic POST to /rest/v1/instructions with the same shape the app sends returns 201 + row readable back

### M1-T6: CalendarContract integration (FT-1.4)
- commit c402101
- features/capture/CalendarGate.kt: buildEventData (pure JVM) + toIntent (Android-only)
- CaptureViewModel.calendarIntents: Flow<CalendarEventData>
- AndroidManifest: WRITE_CALENDAR with maxSdkVersion=32
- 38/38 unit tests pass (8 new CalendarGateTest + 3 new VM tests)
- Live e2e: deferred (LLM 404 blocks confirmation card; full calendar flow unblocked on a real device with the model)

### M1-T7: Share-target ingest (FT-1.5)
- commit 9b16b2a
- AndroidManifest: <activity-alias> for ACTION_SEND + text/plain
- features/capture/{ShareIntake, ShareReceiverActivity}
- MainActivity + RootViewModel + HomeScreen wire shared text → pre-filled capture sheet
- 46/46 unit tests pass (8 new ShareIntakeTest under Robolectric)
- Live e2e: Baton appears in system share sheet as "Save to Baton", tap dispatches ACTION_SEND, capture sheet opens with shared text pre-filled

### M1-T8: Model URL + SHA fix, release APK, m1-capture tag
- commit acf8c2e
- Fixed model_url.txt: enacimie/Qwen3-1.7B-Q4_K_M-GGUF (was a 404 placeholder)
- Computed real SHA-256: 54e0d3dbd2388f3c414bf31fb3e22e4954c8edcf4ab83e315d44995bea764eb9
- build.gradle.kts: lint.disable RemoveWorkManagerInitializer for release
- app-release-unsigned.apk built (25 MB)
- git tag -a m1-capture pushed

### Finding test status (FT-1.x)

| Test | Path | Status |
|---|---|---|
| FT-1.1 | Real LLM extraction on emulator | Blocked: 1.1 GB model download + emulator CPU too slow for <30s inference. Unblocked on a real device. |
| FT-1.2 | Save → instruction + person rows | Wire format verified live via direct POST; full UI flow blocked on FT-1.1 |
| FT-1.3 | Cross-restart read of new instruction | Verified: M0 read path picks up M1-T5 test data (Inspector Ramu) |
| FT-1.4 | Calendar event created from confirmation card | Code path covered by 3 VM tests + 8 CalendarGateTest; live e2e blocked on FT-1.1 |
| FT-1.5 | Share text → capture sheet pre-fill | Verified live on emulator-5554 (chooser → Baton → sheet with pre-filled text) |

### Live e2e verified on emulator-5554

- App installs and launches without crash
- Sign-in against real Supabase returns valid JWT, app routes to Home
- Home shows 3 persons (Inspector Demo, DSP Srinagar, Inspector Ramu) — proves the M0 read path picks up M1-T5 writes
- NoteBar at bottom opens the capture sheet
- Sheet re-opens cleanly after BACK-dismiss (the M1-T1 fix)
- Type + Extract round-trips a row to the `captures` table (M1-T2 wire confirmed)
- Direct POST to /rest/v1/instructions with the app's shape returns 201 (M1-T5 wire confirmed)
- "Save to Baton" appears in the system share sheet; tap opens capture sheet with shared text pre-filled (M1-T7)

### What's deployed / ready

- **APKs**:
  - `app/build/outputs/apk/debug/app-debug.apk` (~36 MB, installed on emulator-5554)
  - `app/build/outputs/apk/release/app-release-unsigned.apk` (25 MB)
- **Supabase project**: cfnmpqwfvhlnbblxqesm (South Asia / Mumbai)
- **Schema**: 12 migrations (0001 init + 0002 user_id default)
- **Edge functions**: mcp-server, admin-bootstrap
- **Test users**: baton.m0+demo@baton.app, baton.m0+userb@baton.app
- **Branch**: m0/skeleton
- **Tags**: m0-skeleton, m0-final, m1-capture

### Carry-forward to M2

- Voice (Whisper.cpp) + photo (ML Kit OCR) on the note bar
- Image MIME type on the share intent (text only in M1)
- WorkManager on-demand init (the lint-disabled provider)
- 5-shot prompt could grow to 8-shot for better accuracy
- The M1 "title" is `action + ' — ' + person`; M2 prompt should return a proper `title` field
- Room mirror + sync (M3) — current code reads Supabase directly
- Sign-out UI (M5)
