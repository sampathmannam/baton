# Baton M1 — Single note bar + capture + on-device LLM

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The user opens Baton, taps the single note bar, types (or in M2: speaks / snaps), and the on-device LLM extracts a structured `INSTRUCTION` (person, action, due_at, priority, instruction_text) into a confirmation card. Confirming the card creates the instruction, auto-creates the person if they're new, and optionally adds a calendar event. Sharing a text/photo from another app lands in the same flow.

**M1 scope (in):** text capture path, llama.cpp JNI + Qwen 3 1.7B Q4, GBNF-constrained JSON extraction, confirmation card, person auto-creation, CalendarContract toggle, share-target ingest (text only — images land in M2 alongside OCR).
**M1 scope (out):** voice (Whisper), photo OCR, Realtime sync, multi-device, person detail timeline, brief/nudge, full MCP, sign-out UI, release-signed APK.

**Architecture delta from M0:**
- New feature module `features/capture/` (note bar, capture sheet, confirmation card).
- New data module `data/captures/` (raw captures table mirror, write-through to Supabase `captures`).
- New AI module `ai/llama/` (JNI bindings + Kotlin facade + GBNF grammar loader).
- Bottom navigation gets 3 tabs (Home, Today, Settings) but only Home is wired in M1; Today and Settings show a "coming in M4/M5" placeholder card. The note bar floats above all three.

**Tech stack additions:**
- `llama.cpp` built from source (CMake) into a JNI `.so` for arm64-v8a.
- Qwen 3 1.7B Q4_K_M GGUF model (~1.1 GB), downloaded on first run into `models/` (gitignored), shipped as a fallback URL in code.
- `androidx.security:security-crypto` for at-rest encryption of the model file path / partial SHA (not the model itself — too large for EncryptedFile).
- `kotlinx.coroutines` Flow + the existing `stateIn` pattern from Home for the capture sheet's state.
- No new Gradle plugins; no Hilt changes; no schema changes (M0's `captures` + `instructions` + `persons` tables are exactly the M1 shape).

**Finding tests for M1 (the test points that define "done"):**
1. **FT-1.1** Tap note bar → type "Tell SHO Ramu to send FIR 47 by Friday" → see a confirmation card with `person="SHO Ramu"`, `action="send FIR 47"`, `due_at=<next Friday>`, `priority="NORMAL"`. (text path, on-device LLM)
2. **FT-1.2** Confirm the card → an `instructions` row exists, a `persons` row for "SHO Ramu" exists (auto-created), the instruction is `status=OPEN`, `direction=OUTBOUND`.
3. **FT-1.3** Open the app a second time → the new instruction and person still appear (proves the M0 read path picks up writes from the M1 write path).
4. **FT-1.4** Toggle "Add to Calendar" on a confirmation card that has `due_at` → confirm → a calendar event exists with the right title and start time.
5. **FT-1.5** Share a text from another app → Baton opens with the shared text in the capture sheet → confirm → instruction created with the shared text as `raw_text`.

**Global constraints (carried from M0, all apply):**
- Min SDK 26, target 34, arm64-v8a only.
- No red / overdue / shame language or colour tokens.
- All data per-user via RLS (M0 schema enforces; M1 just respects it).
- No third-party analytics, no telemetry, no cloud AI.
- No git operations outside the workspace.
- Imperative-mood commit messages, one commit per task, all green tests at each commit.

---

## Task 1: Single note bar UI + capture sheet (text-only)

**Files:**
- Create: `app/src/main/java/com/baton/app/features/capture/NoteBar.kt` (the bottom floating bar visible on every tab)
- Create: `app/src/main/java/com/baton/app/features/capture/CaptureSheet.kt` (modal bottom sheet, text input + confirm button)
- Create: `app/src/main/java/com/baton/app/features/capture/CaptureViewModel.kt`
- Create: `app/src/main/java/com/baton/app/features/capture/CaptureUiState.kt`
- Modify: `app/src/main/java/com/baton/app/ui/MainActivity.kt` (wrap HomeScreen in a Scaffold that has the NoteBar as `bottomBar`)
- Create: `app/src/test/java/com/baton/app/features/capture/CaptureViewModelTest.kt`
- Test: `app/src/androidTest/java/com/baton/app/M1CaptureAcceptanceTest.kt` (drives the note bar, types, sees the sheet — but does NOT assert LLM output, that comes in Task 4)

**Step 1.1: Write the failing unit test first**

`CaptureViewModelTest` covers the state machine:
- onTextChanged("hello") → state.text == "hello", state.canConfirm == false (no LLM yet)
- onTextChanged("") → state.canConfirm == false
- onConfirm() without an LLM result → state stays Idle (the LLM result wiring is Task 4; for now, confirm is a no-op)
- dismissSheet() → state.isVisible == false

**Step 1.2: Implement the ViewModel + state**

`CaptureUiState`:
```
data class CaptureUiState(
  val isVisible: Boolean = false,
  val text: String = "",
  val isExtracting: Boolean = false,   // true while the LLM is running
  val proposal: ExtractedInstruction? = null,  // null until LLM returns
  val error: String? = null,
)
```

`CaptureViewModel` exposes the four events and a `StateFlow<CaptureUiState>`. In Task 1, the LLM is a stub that just sets `isExtracting=false, proposal=null` immediately; the real JNI binding lands in Task 4. **Finding test guard:** the test must allow the stub to return a deterministic proposal when wired; don't bake the stub into the test, parameterize it.

**Step 1.3: Implement the NoteBar and CaptureSheet**

`NoteBar`: a `Surface` with a single TextField placeholder "Tap to add a note…", a small mic icon (greyed, M2) and a camera icon (greyed, M2). Tapping anywhere on the bar opens the sheet. For M1, only the text field is wired.

`CaptureSheet`: a `ModalBottomSheet` with:
- A `TextField` (multi-line, max 4 lines visible) bound to `text`
- An "Extract" primary button (enabled iff `text.isNotBlank() && !isExtracting`)
- An "x" close button

**Step 1.4: Wire into MainActivity**

Replace the current `HomeScreen` body with a `Scaffold` that has the NoteBar as `bottomBar`. The NoteBar is a singleton (one VM, hoisted above the nav graph); tapping it sets `captureViewModel.openSheet()`.

**Step 1.5: Acceptance test**

`M1CaptureAcceptanceTest` (androidTest): force the app to the Home tab, find the NoteBar (use `contentDescription="Add note"`), tap it, find the capture sheet's TextField, type "Tell SHO Bandipora to send FIR 47 by Friday", assert the sheet is visible. This test runs in CI; it does NOT assert LLM output (that requires Task 4).

**Step 1.6: Build, install, run the acceptance test, commit, push.**

```
./gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:connectedDebugAndroidTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
git add -A
git commit -m "M1-T1: single note bar UI + text capture sheet, no LLM"
git push -u origin m1/capture
```

---

## Task 2: Captures table mirror + write-through to Supabase

**Files:**
- Create: `app/src/main/java/com/baton/app/data/captures/Capture.kt`
- Create: `app/src/main/java/com/baton/app/data/captures/CaptureRepository.kt` (interface)
- Create: `app/src/main/java/com/baton/app/data/captures/SupabaseCaptureRepository.kt` (impl)
- Create: `app/src/main/java/com/baton/app/di/CapturesModule.kt` (Hilt)
- Modify: `app/src/main/java/com/baton/app/features/capture/CaptureViewModel.kt` (insert a capture row when the user taps Extract, before the LLM runs)
- Create: `app/src/test/java/com/baton/app/data/captures/SupabaseCaptureRepositoryTest.kt`
- Modify: `.sdd/progress.md` (update status)

**Step 2.1: Write the failing test**

`SupabaseCaptureRepositoryTest` covers:
- `create(rawText="…", mode=TEXT)` → POST /rest/v1/captures with `{raw_text, mode, processed=false}`, returns the inserted row
- The repo uses the same `private.is_owner` RLS pattern (user_id is filled by the DB default added in M0's 0002 migration)

**Step 2.2: Implement the data class + repository**

`Capture`: `id, userId, mode (TEXT/VOICE/PHOTO), rawText, audioUri?, imageUri?, processed: Boolean, createdAt`.

`SupabaseCaptureRepository.create(rawText, mode)` mirrors `SupabasePersonRepository.create()` from M0: build a `CaptureInsert` with `raw_text`, `mode`; `client.postgrest.from("captures").insert(...).select().decodeSingle()`. Set `processed=false`. Returns the row.

**Step 2.3: Wire into CaptureViewModel**

On `Extract`:
1. Insert a `captures` row with `mode=TEXT, raw_text=text, processed=false` → wait for the id.
2. Hand the row + text to the LLM stub (Task 4 wires the real thing). For now, the stub returns `null` and the sheet stays open with an error toast "No instruction found — try again".
3. When the stub returns a proposal (M1-T4), mark the capture `processed=true` and set `state.proposal = proposal`.

**Step 2.4: Build, install, run the test, commit, push.**

```
./gradlew.bat :app:testDebugUnitTest
git add -A
git commit -m "M1-T2: captures table write-through to Supabase, processed=false on create"
git push origin m1/capture
```

---

## Task 3: llama.cpp JNI integration (no extraction yet)

**Files:**
- Create: `app/src/main/cpp/CMakeLists.txt`
- Create: `app/src/main/cpp/llama_jni.cpp` (the JNI bridge)
- Modify: `app/build.gradle.kts` (add `externalNativeBuild { cmake { … } }`, NDK abiFilters)
- Create: `app/src/main/java/com/baton/app/ai/llama/LlamaBridge.kt` (the Kotlin facade)
- Create: `app/src/main/java/com/baton/app/ai/llama/ModelManager.kt` (downloads the Qwen 3 1.7B Q4 GGUF on first run, into `filesDir/models/`)
- Create: `app/src/main/assets/qwen3-1.7b-q4_k_m.gguf` — NOT. Place a 1-byte placeholder file and document the real download URL. Actual model download happens at first run via OkHttp from the URL in `app/src/main/assets/model_url.txt`.
- Create: `app/src/main/assets/model_url.txt` (one line: the GitHub release / HuggingFace URL for the Q4_K_M GGUF)
- Create: `app/src/test/java/com/baton/app/ai/llama/LlamaBridgeTest.kt`
- Modify: `gradle/libs.versions.toml` (add NDK + OkHttp + Coroutines deps if not present)

**Step 3.1: NDK + CMake**

The `externalNativeBuild` block targets `arm64-v8a` only (per the global constraint). Build llama.cpp as a static library from the official `ggerganov/llama.cpp` release tag pinned in `libs.versions.toml` (use the same `b####` tag Android Studio's AI plugin uses, e.g. `b4600` as of M1 start). The CMakeLists pulls in `llama.cpp/CMakeLists.txt` via `add_subdirectory` after vendoring the source under `app/src/main/cpp/llama-cpp/`. Vendor via a Gradle task that runs at configuration time and downloads + extracts the release tarball if `app/src/main/cpp/llama-cpp/` doesn't exist (gated by `gradle.taskGraph.whenReady` — no new plugin, just a `tasks.register`).

**Step 3.2: JNI bridge**

`llama_jni.cpp` exposes four native methods on a `LlamaBridge` Kotlin class:
- `nativeLoad(modelPath: String, nCtx: Int, nThreads: Int): Long` — returns a non-zero handle.
- `nativeInfer(handle: Long, prompt: String, grammar: String?, maxTokens: Int): String` — returns the raw completion.
- `nativeGetLastEvalMs(handle: Long): Long` — for the §11 "measure before you claim" logging.
- `nativeFree(handle: Long)`.

Keep the JNI surface narrow. No batch API, no embeddings, no token-level streaming in M1.

**Step 3.3: ModelManager**

On first run, check `filesDir/models/qwen3-1.7b-q4_k_m.gguf`. If absent, download from `assets/model_url.txt` to a `.part` file, verify SHA-256 against `assets/model_sha256.txt`, then atomically move. Show progress in a `Flow<DownloadProgress>` so the NoteBar can render a small "downloading model 47%" pill. After M1 ships, the model lives in `filesDir/models/`, NOT in `models/` in the repo (gitignored).

**Step 3.4: LlamaBridge Kotlin facade**

Wraps the JNI calls. Single-threaded (`Mutex` in front of the handle — the model is too big to keep two contexts in memory on the RTX-2050-shaped deployment target). Exposes a `suspend fun infer(prompt, grammar, maxTokens=512)` that hops to a dedicated single-thread dispatcher (`Executors.newSingleThreadExecutor().asCoroutineDispatcher()`), calls `nativeInfer`, and returns the string. Times the call and emits a structured `Log` event (local-only) with `evalMs` and `tokenCount`.

**Step 3.5: Unit test**

`LlamaBridgeTest`:
- Calling `infer` before `load` throws `IllegalStateException`.
- After `load("/non/existent/path", 512, 2)`, `infer("hi")` throws a clean `LlamaError.ModelNotFound` (not a SIGSEGV — wrap native calls in `try { … } catch (UnsatisfiedLinkError) { … }`).
- The `ModelManager.download` happy path is tested with a `MockEngine` (OkHttp) returning a 1024-byte payload; assert the file lands in `tempDir/models/`.

The actual LLM output is NOT unit-tested — that's a finding test in Task 4.

**Step 3.6: Build, install, commit, push.**

Run on the emulator. The NDK build is the slow step (first build ~5-8 min). Subsequent builds are incremental.

```
./gradlew.bat :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
git add -A
git commit -m "M1-T3: llama.cpp JNI bridge, Qwen 3 1.7B Q4 download on first run"
git push origin m1/capture
```

---

## Task 4: LLM extraction prompt + GBNF grammar + confirmation card

This is the heart of M1. It produces FT-1.1, FT-1.2, FT-1.3.

**Files:**
- Create: `app/src/main/assets/prompts/extract_v1.txt` (the system prompt)
- Create: `app/src/main/assets/grammars/instruction.gbnf` (the constrained grammar)
- Create: `app/src/main/java/com/baton/app/ai/extraction/ExtractedInstruction.kt`
- Create: `app/src/main/java/com/baton/app/ai/extraction/Extractor.kt` (orchestrates prompt + grammar + LlamaBridge)
- Create: `app/src/main/java/com/baton/app/features/capture/ConfirmationCard.kt`
- Modify: `app/src/main/java/com/baton/app/features/capture/CaptureViewModel.kt` (use the Extractor)
- Modify: `app/src/main/java/com/baton/app/features/capture/CaptureSheet.kt` (render the card when `state.proposal != null`)
- Create: `app/src/test/java/com/baton/app/ai/extraction/ExtractorTest.kt`
- Create: `app/src/androidTest/java/com/baton/app/M1ExtractionAcceptanceTest.kt` (the FT-1.1 finding test — RUNS the real LLM on the emulator)

**Step 4.1: Define the ExtractedInstruction data class**

```
@Serializable
data class ExtractedInstruction(
  val person: String?,            // null if no person is mentioned
  val action: String,             // the verb phrase; never blank
  val due_at: String?,            // ISO 8601; null if no time cue
  val priority: String,           // "NORMAL" | "URGENT" | "LOW"
  val instruction_text: String,   // the full instruction in clean prose
  val confidence: Double,         // 0.0 - 1.0, model's self-reported
)
```

**Step 4.2: Write the prompt**

`extract_v1.txt` (full text in the asset, summarised here):
- System: "You are a personal assistant for an Indian Police Service officer. Extract a structured instruction from the user's free-text note. The note is in English; names and places may be in romanised Hindi/Urdu/Tamil/Telugu. Output ONLY valid JSON matching the schema. Do not include explanations, code fences, or extra keys."
- User template: `"{raw_text}\n\nReturn JSON with keys: person, action, due_at (ISO 8601 in Asia/Kolkata, or null), priority (NORMAL/URGENT/LOW), instruction_text, confidence (0-1)."`
- Include 3 few-shot examples in the system prompt: (1) "Ramu needs to file the FIR by Friday" → `{person:"Ramu", action:"file FIR", due_at:<next Friday 17:00 IST>, priority:"NORMAL", instruction_text:"Ramu to file the FIR by Friday", confidence:0.9}`, (2) "URGENT call SP Bandipora immediately" → `{person:"SP Bandipora", action:"call", due_at:<now+5min>, priority:"URGENT", instruction_text:"Call SP Bandipora immediately", confidence:0.95}`, (3) "sunday review pending cases" → `{person:null, action:"review pending cases", due_at:<next Sunday 10:00 IST>, priority:"NORMAL", instruction_text:"Review pending cases on Sunday", confidence:0.7}`.

**Step 4.3: Write the GBNF grammar**

`instruction.gbnf` constrains output to:
```
root   ::= object
object ::= "{" ws "\"person\":" ws person ws "," ws "\"action\":" ws string ws "," ws "\"due_at\":" ws due ws "," ws "\"priority\":" ws priority ws "," ws "\"instruction_text\":" ws string ws "," ws "\"confidence\":" ws number ws "}"
person ::= string | "null"
due ::= string | "null"
priority ::= "\"NORMAL\"" | "\"URGENT\"" | "\"LOW\""
string ::= "\"" char* "\""
char ::= [^"\\] | "\\" escape
number ::= [0-9] ("." [0-9])?
ws ::= [ \t\n]*
```
This is what FT-1.1 actually depends on: the GBNF grammar means the LLM can ONLY produce valid JSON matching the schema. If extraction fails, it's a model-quality problem, not a parsing problem.

**Step 4.4: Implement the Extractor**

```
class Extractor(
  private val llama: LlamaBridge,
  private val prompts: AssetPrompts,
  private val grammars: AssetGrammars,
  private val clock: Clock = Clock.System,
) {
  suspend fun extract(rawText: String): Result<ExtractedInstruction> = runCatching {
    val prompt = prompts.systemPrompt() + "\n\n" + prompts.userTemplate().format(rawText)
    val raw = llama.infer(prompt, grammar = grammars.instructionGbnf(), maxTokens = 256)
    Json { ignoreUnknownKeys = true; isLenient = true }
      .decodeFromString<ExtractedInstruction>(raw)
  }.recoverCatching { e ->
    // Log the raw LLM output locally for diagnosis (no cloud upload)
    Log.w("Extractor", "raw LLM output: $rawText", e)
    throw ExtractionFailed(rawText, e)
  }
}
```

**Step 4.5: Implement the ConfirmationCard**

A card inside the bottom sheet that appears when `state.proposal != null`. Renders editable fields:
- Person (TextField, auto-completes from existing persons)
- Action (TextField)
- Due at (date+time picker, with a "no due date" switch)
- Priority (segmented: NORMAL / URGENT / LOW)
- Instruction text (TextField, multi-line)
- Confidence (small chip: "High" / "Medium" / "Low" coloured… no red. Amber for Medium, dim grey for Low. No red, per global constraint.)
- "Add to Calendar" toggle (M1-T5 wires it; M1-T4 just shows the toggle, disabled, with "coming next" hint)
- Confirm + Cancel buttons

**Step 4.6: Unit test (no LLM)**

`ExtractorTest` uses a fake `LlamaBridge` that returns a canned JSON string. Asserts that the data class parses correctly, that `confidence < 0.5` results in `Result.failure` (per design decision: low-confidence extractions are dropped, the user retypes), and that the grammar is loaded from assets.

**Step 4.7: Finding test FT-1.1 (real LLM, on emulator)**

`M1ExtractionAcceptanceTest`:
1. Force-stop and re-launch the app, sign in as user A.
2. Wait for the model download to complete (poll `filesDir/models/qwen3-1.7b-q4_k_m.gguf` existence).
3. Tap the note bar.
4. Type "Tell SHO Ramu to send FIR 47 by Friday".
5. Tap Extract.
6. Wait up to 30 seconds for the proposal to render.
7. Assert: the ConfirmationCard is showing with `person="SHO Ramu"`, `action="send FIR 47"`, `due_at` within the next 7 days, `priority="NORMAL"`.

This is slow (5-8 min model download + 2-5 sec inference on the emulator CPU). It runs in `connectedDebugAndroidTest`. The result is a binary "the LLM extracted what we expected" assertion. If it fails, the fix is in the prompt or the grammar, not in the code.

**Step 4.8: Build, install, run the finding test, commit, push.**

```
./gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:connectedDebugAndroidTest
git add -A
git commit -m "M1-T4: on-device LLM extraction with GBNF-constrained JSON, confirmation card"
git push origin m1/capture
```

---

## Task 5: Save confirmation → instructions + persons (FT-1.2, FT-1.3)

**Files:**
- Create: `app/src/main/java/com/baton/app/data/instructions/Instruction.kt`
- Create: `app/src/main/java/com/baton/app/data/instructions/InstructionRepository.kt`
- Create: `app/src/main/java/com/baton/app/data/instructions/SupabaseInstructionRepository.kt`
- Modify: `app/src/main/java/com/baton/app/data/person/SupabasePersonRepository.kt` (add `findByName(name, station): Person?` and `findOrCreate(name, designation, station): Person` for auto-create)
- Modify: `app/src/main/java/com/baton/app/features/capture/CaptureViewModel.kt` (on Confirm, save instruction + person)
- Create: `app/src/test/java/com/baton/app/data/instructions/SupabaseInstructionRepositoryTest.kt`
- Create: `app/src/androidTest/java/com/baton/app/M1SaveAcceptanceTest.kt` (FT-1.2)

**Step 5.1: Define Instruction**

Same shape as the spec's `instructions` table (M0's migration). Direction defaults to `OUTBOUND` (the user is the one tracking the instruction), status defaults to `OPEN`, source defaults to `TEXT`.

**Step 5.2: Repositories**

`SupabaseInstructionRepository.create(...)` does the same insert+select pattern. `SupabasePersonRepository.findOrCreate` does a SELECT by `(name, station)` and INSERTs if absent — the unique constraint `unique(user_id, name, designation, station)` already prevents duplicates.

**Step 5.3: Wire the save**

On Confirm:
1. `personRepo.findOrCreate(proposal.person, designation=null, station=null)` → `personId`.
2. `instructionRepo.create(personId=personId, action=proposal.action, due_at=proposal.due_at, priority=proposal.priority, instruction_text=proposal.instruction_text, source=TEXT)` → `instruction`.
3. Mark the `captures` row `processed=true` and link it to the instruction via `capture_id` (or store the link in the instruction's `source_capture_id` — check spec §4.5; if not there, add to schema in Task 8).
4. Dismiss the sheet. Show a snackbar: "Saved. Open in Today tab." (Today tab is M4; for M1 the snackbar just says "Saved.").

**Step 5.4: Finding test FT-1.2**

`M1SaveAcceptanceTest`:
1. Continue from FT-1.1's state (or re-do the type + extract + confirm flow).
2. After Confirm, query `instructions` via the Supabase REST API with the user's JWT.
3. Assert: 1 row with the right fields, `user_id` matches, `status="OPEN"`, `direction="OUTBOUND"`.
4. Query `persons` and assert: 1 row for "SHO Ramu" with `user_id` matching.
5. Force-stop and re-launch the app. Sign in.
6. Assert: the new person appears in the People list (proves the M0 read path picks up M1 writes).

**Step 5.5: Commit, push.**

```
git add -A
git commit -m "M1-T5: save confirmation creates instruction + auto-creates person, FT-1.2/1.3"
git push origin m1/capture
```

---

## Task 6: CalendarContract integration (FT-1.4)

**Files:**
- Create: `app/src/main/java/com/baton/app/features/capture/CalendarGate.kt` (Kotlin wrapper around `CalendarContract.Events.Insert`)
- Modify: `app/src/main/java/com/baton/app/features/capture/ConfirmationCard.kt` (wire the toggle to a callback)
- Modify: `app/src/main/java/com/baton/app/features/capture/CaptureViewModel.kt` (after save, if `state.addToCalendar` was true and `due_at != null`, fire the intent)
- Modify: `app/src/main/java/com/baton/app/AndroidManifest.xml` (add `WRITE_CALENDAR` permission with `android:maxSdkVersion="32"` and the runtime-permission request flow)
- Create: `app/src/test/java/com/baton/app/features/capture/CalendarGateTest.kt`
- Create: `app/src/androidTest/java/com/baton/app/M1CalendarAcceptanceTest.kt` (FT-1.4)

**Step 6.1: CalendarGate**

Two responsibilities: (a) check `Manifest.permission.WRITE_CALENDAR` and request it via `ActivityResultContracts.RequestPermission`, (b) build and launch the `Intent(Intent.ACTION_INSERT).setData(CalendarContract.Events.CONTENT_URI).putExtra(...)` with title, description, begin time (parsed from `due_at`), end time (`begin + 15 min` default), and a calendar-id from `CalendarContract.Events.CALENDAR_ID` (default calendar).

**Step 6.2: Wire the toggle**

The toggle in the ConfirmationCard is a `Boolean` state. On Confirm, if the toggle is on AND `due_at != null`, the VM first requests the permission, then launches the intent. The save flow runs in parallel (the calendar event is a copy, not the source of truth).

**Step 6.3: Test**

`CalendarGateTest`: a unit test that verifies the Intent has the right `EXTRA_BEGIN_TIME` for a known `due_at`. The actual calendar write is not unit-testable; the finding test covers it.

`M1CalendarAcceptanceTest`: do the same flow as FT-1.1 with the calendar toggle on. After Confirm + Accept calendar permission, query the calendar provider via `contentResolver.query(CalendarContract.Events.CONTENT_URI, …)` and assert a row exists with the right title and start time.

**Step 6.4: Commit, push.**

```
git add -A
git commit -m "M1-T6: CalendarContract toggle on confirmation card, FT-1.4"
git push origin m1/capture
```

---

## Task 7: Share-target ingest (FT-1.5)

**Files:**
- Modify: `app/src/main/AndroidManifest.xml` (add an `<activity-alias>` for `ACTION_SEND` with `mimeType="text/plain"`)
- Create: `app/src/main/java/com/baton/app/features/capture/ShareReceiverActivity.kt` (a no-UI activity that pulls the shared text out of the intent and hands it to the CaptureViewModel)
- Create: `app/src/main/java/com/baton/app/features/capture/ShareIntake.kt` (Kotlin helper to extract `Intent.EXTRA_TEXT` and route it)
- Modify: `app/src/main/java/com/baton/app/ui/MainActivity.kt` (handle the deep link from ShareReceiverActivity: open the capture sheet pre-populated)
- Create: `app/src/test/java/com/baton/app/features/capture/ShareIntakeTest.kt`
- Create: `app/src/androidTest/java/com/baton/app/M1ShareAcceptanceTest.kt` (FT-1.5)

**Step 7.1: Manifest**

```
<activity-alias
  android:name=".features.capture.ShareReceiverActivity"
  android:targetActivity=".ui.MainActivity"
  android:exported="true"
  android:label="@string/share_to_baton">
  <intent-filter>
    <action android:name="android.intent.action.SEND" />
    <category android:name="android.intent.category.DEFAULT" />
    <data android:mimeType="text/plain" />
  </intent-filter>
</activity-alias>
```

`M2 will add image/* mimeType here.`

**Step 7.2: ShareReceiverActivity**

A no-UI activity (`@AndroidEntryPoint`, `finish() onCreate`). Reads the intent's `EXTRA_TEXT`, then launches `MainActivity` with an extra `EXTRA_SHARED_TEXT=...` and `FLAG_ACTIVITY_CLEAR_TOP | SINGLE_TOP`. MainActivity's existing onNewIntent handler opens the capture sheet pre-filled with the text.

**Step 7.3: Test**

`ShareIntakeTest`: a unit test that builds a fake `ACTION_SEND` intent with `EXTRA_TEXT="Find SHO Ramu and ask about case 47"` and asserts `ShareIntake.extract(intent) == "Find SHO Ramu and ask about case 47"`.

`M1ShareAcceptanceTest`: 
1. Launch Baton via `am start -a android.intent.action.SEND -t text/plain --es android.intent.extra.TEXT "Find SHO Ramu about case 47"`.
2. Wait for the app to be in the foreground.
3. Assert: the capture sheet is visible with the shared text in the TextField.
4. Tap Extract → wait for proposal → Confirm.
5. Assert: an instruction was created with `raw_text="Find SHO Ramu about case 47"` in the captures table.

**Step 7.4: Commit, push.**

```
git add -A
git commit -m "M1-T7: share-target ingest for text, FT-1.5"
git push origin m1/capture
```

---

## Task 8: M1 finding tests + release-APK build

**Files:**
- Modify: `app/build.gradle.kts` (add a real `signingConfig` for release; use the existing `~/.android/debug.keystore` so we don't need a fresh keystore for M1)
- Create: `app/src/main/java/com/baton/app/data/capture/M1FindingTests.kt` (the 5 finding tests in one file, runnable as `connectedDebugAndroidTest --tests "*.M1FindingTests"`)
- Create: `docs/superpowers/plans/2026-08-11-baton-m1-acceptance.md` (the M1 acceptance doc; one section per finding test with its screenshot, expected vs actual, and the run command)
- Tag: `m1-capture` on the branch tip

**Step 8.1: Run all 5 finding tests in CI order**

```
./gradlew.bat :app:connectedDebugAndroidTest \
  --tests "com.baton.app.M1ExtractionAcceptanceTest" \
  --tests "com.baton.app.M1SaveAcceptanceTest" \
  --tests "com.baton.app.M1CalendarAcceptanceTest" \
  --tests "com.baton.app.M1ShareAcceptanceTest" \
  --tests "com.baton.app.M1CaptureAcceptanceTest"
```

Each test is independent and has its own setup; they can run in any order but serial is fine (model is loaded once and cached).

**Step 8.2: Build the release APK**

```
./gradlew.bat :app:assembleRelease
```

The release APK is signed with the debug keystore (acceptable for M1 sideloading on the user's device; a real upload keystore is M5). The APK is at `app/build/outputs/apk/release/app-release.apk`, ~45 MB (debug + JNI .so + model URL string).

**Step 8.3: Install on the real device (not the emulator)**

The emulator can't run llama.cpp at usable speed (ARM translation overhead). The user's Android phone is the deployment shape. Install via `adb install -r app-release.apk` (the user will do this — agent cannot physically install on the real device).

**Step 8.4: Tag and write the acceptance doc**

```
git tag -f m1-capture
git push origin m1-capture
```

The acceptance doc is the M1 ledger. It includes:
- The 5 finding test results (status, evidence screenshot, expected vs actual)
- A "what works / what doesn't / what M2 needs" summary
- A pointer to the M2 plan (TBD; it covers Whisper, OCR, Realtime sync, multi-device, person detail timeline)

---

## Carry-forward notes for M2

- M2 should add image/* to the share-target manifest filter (M1-T7).
- The `captures.processed=true` link to the `instructions` row is via `captures.id` referenced from `instructions` (or `source_capture_id` on instructions if the spec adds it). Confirm with spec before M2-T1; if not, add migration 0003.
- The Qwen 3 1.7B Q4 model lives in `filesDir/models/`. The model URL in `assets/model_url.txt` should be a stable GitHub release URL; a 1.1 GB download on first run is acceptable but the user should be warned in the NoteBar pill.
- The ConfirmationCard is reusable; M2 wraps it for voice (Whisper output → same prompt + grammar) and photo (ML Kit OCR output → same prompt + grammar).
- The bottom navigation still has 3 tabs but only Home is wired. M4 brings Today (brief), M5 brings Settings. Don't add them in M2 — keeps the per-milestone diff reviewable.

## Risks I'm flagging now

1. **Qwen 3 1.7B on a mid-range Android phone CPU may be 10-30s per inference** (rough estimate from RunPod A100 = 5.5 min for similar work; phones are 10-50x slower than A100 for this). The ConfirmationCard can show an `isExtracting` spinner for that long. If the user finds it too slow, M2 ships a "use server-side" toggle (gated by user preference, off by default — privacy is the core constraint).
2. **llama.cpp CMake integration is the highest-risk task in M1.** The vendoring dance (download → extract → CMake add_subdirectory) is finicky on Windows NDK. If Task 3 takes more than 2 days, the fallback is to use the official prebuilt JNI .so from `ggerganov/llama.cpp` releases (less work, slightly older llama.cpp). Decision: try the CMake path first; switch to prebuilt if it doesn't compile in a day.
3. **The GBNF grammar's `date` field accepts any string** — it can't validate "next Friday" → ISO 8601. The model is told to do it in the prompt, and the Kotlin code validates after. If the model produces a non-ISO string, the confirmation card shows it as raw text in the date field, the user corrects it. This is intentional: better to be wrong-but-fixable than to silently drop a date.
4. **The `confidence` field is a self-report, not calibrated.** Treat `< 0.5` as "drop", but the threshold may need tuning in M2 once we have real data. Don't expose it to the user as a number; show it as High/Medium/Low chips only.

## M1 → M2 transition checklist

- [ ] All 5 finding tests green on a real device
- [ ] Release APK installed and runs the happy path end-to-end
- [ ] `m1-capture` tag pushed
- [ ] `docs/superpowers/plans/2026-08-11-baton-m1-acceptance.md` written
- [ ] `docs/superpowers/specs/2026-08-10-baton-design.md` §4.5 and §7 cross-checked; any spec deltas noted
- [ ] M2 plan started: Whisper.cpp JNI + foreground mic service + quick-tile + lock-screen widget
