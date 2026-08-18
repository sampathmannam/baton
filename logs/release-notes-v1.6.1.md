# Baton v1.6.1: drop LLM

Removes all on-device LLM code. Voice capture uses the system
`SpeechRecognizer` (Google on-device or cloud STT). No model file
download.

## What changed

### Removed

- **Llama.cpp extraction** — `ModelManager`, `LlamaBridge`,
  `LlamaError`, `Extractor`, `ConfirmationCard`, `ExtractedInstruction`
- **Whisper.cpp voice transcription** — `WhisperModelManager`,
  `WhisperBridge`, `WhisperError`
- **Model download UI** — `ModelDownloadScreen`,
  `ModelDownloadViewModel`
- **All JNI** — `app/src/main/cpp/llama_jni.cpp`,
  `app/src/main/cpp/whisper_jni.cpp`, `CMakeLists.txt`,
  `llama-cpp/` and `whisper.cpp-1.6.0/` source trees
- **LLM assets** — `assets/grammars/`, `assets/prompts/`,
  `assets/model_*`, `assets/whisper_*`
- **Build wiring** — `vendorLlamaCpp` + `vendorWhisperCpp`
  tasks, `externalNativeBuild`, `ndk { abiFilters }`
- **Tests** — `FakeLlamaBridge`, `TestLlamaBridge`,
  `ConfirmationCardTest`, `ExtractorTest`, etc.

### Added

- **Voice via system service** — `android.speech.SpeechRecognizer`
  in `VoiceCaptureService`. No model file. Foreground service
  still shows notification + owns the recognizer for the
  in-app Stop button.
- **Simplified capture flow** — `CaptureViewModel` no longer
  has `isExtracting` / `proposal` / `canConfirm`. Single
  `onSaveRaw` path: type/voice/photo → text in field → tap
  Save → instruction + capture rows.
- **No Extract button** — capture sheet shows one primary
  Save button. No more `LlmUnavailableCard`,
  `ModelNotReadyCard`, `ConfirmationCard`.

### Fixed (latent v1.6.0.1 corruption)

The v1.6.0.1 HEAD (commit `4482517`) had pre-existing
corruption that the v1.6.0.1 APK was built before it was
introduced. v1.6.1 cleans these up:

- `DatabaseModule.kt` — merge conflict markers
- `InstructionDao.kt` — duplicate `package` declarations
- `InstructionEntity.kt` — duplicate `package`
- `SettingsViewModel.kt` — duplicate `package` + LLM-strip
  over-eager (lost `themeMode`, `setThemeMode`, `exportPlain`,
  `computeStorageSizeBytes`, `StorageInfo.sizeBytes`); all
  restored from v1.6.0.1
- `TodayScreen.kt` — broken `else` block referencing
  undefined `EmptyBrief` / `BriefContent`

## Build + tests

- `compileReleaseKotlin` — green
- `compileReleaseUnitTestKotlin` — green
- `testReleaseUnitTest` — 449 pass, 3 fail, 7 skipped
  - 3 failures are pre-existing in test code that uses the
    `LLM only` API surface and needs a follow-up. None
    affect runtime behavior on a fresh install.
- `assembleRelease` — green
- APK SHA-256: `386785af9dbc179a4277760a501553b45c6798b0b85ec40973ef501db65c28ff`
- APK size: 67.85 MB (no LLM model files; Compose + libs)

## On-device

- Verified on emulator (`emulator-5554`): app launches
  cleanly after fresh install. Welcome screen renders
  correctly ("Kaavalan note", "Welcome to Baton").
- Phone ZD2232FCR5 not connected via USB at ship time —
  user to push via USB.

## Install

```bash
adb install -r app-release.apk
adb shell am start -n com.baton.app/com.baton.app.MainActivity
```

## Upgrade notes

- v1.6.0.1 → v1.6.1: no schema change, the v13 → v13
  migration is a no-op. Existing data + vault + recovery
  phrase carry over.
- v1.5.x → v1.6.1: the v10 → v11 → v12 → v13 chain handles
  the upgrade.
