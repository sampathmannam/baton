# KaavalanNote redesign Stage 2 verification

Date: September 2, 2026

## Implementation

- Made Timeline the start destination.
- Replaced the old Home / Today / Settings bottom navigation with exactly Timeline / People / Ask AI.
- Added one instruction list grouped in this order: Late, Today, Next 7 days, Later.
- Added All, To do, Waiting, and Done filters.
- Kept Urgent as the only elevated priority and made ownership explicit with “Action with you” and “Waiting on another person”.
- Added loading, empty, recoverable-error, and content states.
- Kept capture visible from Timeline. Until the dedicated Stage 4 capture flow lands, it opens the existing proven capture sheet through People.
- Kept Settings reachable from the Timeline top bar rather than as a fourth tab.
- Added an honest Ask AI placeholder; no DeepSeek request is made and no API key is requested or stored in this stage.
- Added English and Tamil strings for the new primary navigation and Timeline.

## Test-first evidence

### RED

- `$env:JAVA_TOOL_OPTIONS='-XX:TieredStopAtLevel=1'; .\gradlew.bat testDebugUnitTest --tests "com.kaavalan.note.ui.timeline.*" --no-daemon --max-workers=1 --console=plain`
  - Failed at unit-test compilation because `TimelineFilter`, `TimelineUiState`, and `buildTimelineSections` did not exist.

### GREEN

- Focused Timeline tests plus debug APK assembly:
  - `$env:JAVA_TOOL_OPTIONS='-XX:TieredStopAtLevel=1'; .\gradlew.bat testDebugUnitTest --tests "com.kaavalan.note.ui.timeline.*" assembleDebug --no-daemon --max-workers=1 --console=plain`
  - `BUILD SUCCESSFUL` in 4m59s.
- Final full checkpoint:
  - `$env:JAVA_TOOL_OPTIONS='-XX:TieredStopAtLevel=1'; .\gradlew.bat testDebugUnitTest compileDebugAndroidTestKotlin assembleDebug --no-daemon --max-workers=1 --console=plain`
  - `BUILD SUCCESSFUL` in 1m47s.
  - 603 JVM tests total: 591 passed, 12 skipped, 0 failures, 0 errors.
  - Android instrumentation-test sources compiled successfully.
  - Universal debug APK assembled at `app/build/outputs/apk/debug/app-universal-debug.apk` (95,317,682 bytes).

## Reliability follow-up found by the full suite

- The first full Stage 2 run exposed a pre-existing probabilistic recovery-phrase test failure.
- A random one-word BIP39 substitution has a 1-in-16 chance of retaining a valid 12-word checksum, so the test could fail even when the validator was correct.
- The test now selects a deterministic one-word substitution with an invalid checksum. The production mnemonic implementation was not changed.

## Device verification boundary

- The connected Motorola test phone was deliberately not overwritten at this intermediate checkpoint.
- Installation, normal-font screenshots, large-font screenshots, interaction driving, and endurance testing remain part of the release-candidate device QA stage.
