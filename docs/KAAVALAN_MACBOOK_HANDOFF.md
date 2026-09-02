# KaavalanNote MacBook handoff

Date: September 2, 2026

## Source of truth

- GitHub repository: `https://github.com/sampathmannam/kaavalan-note.git`
- Development branch: `qwen/kaavalan-redesign`
- Do not continue from `main`; the redesign stages are intentionally isolated on the development branch.

On the MacBook:

```bash
git clone https://github.com/sampathmannam/kaavalan-note.git
cd kaavalan-note
git switch qwen/kaavalan-redesign
git pull --ff-only origin qwen/kaavalan-redesign
```

If the repository is already cloned:

```bash
git fetch origin
git switch qwen/kaavalan-redesign
git pull --ff-only origin qwen/kaavalan-redesign
```

## Verified checkpoint

- Stage 1: simplified instruction lifecycle and migration 16→17.
- Stage 2: Timeline start screen, Timeline/People/Ask AI navigation, filters, grouping, capture entry point, and Ask AI placeholder.
- Stage 3: simplified People profiles, search, active/completed person instructions, private group labels, and migration 17→18.
- Current full verification: 612 JVM tests, 600 passed, 12 skipped, 0 failures, 0 errors.
- Android instrumentation-test sources compile.
- The universal debug APK assembles successfully.

Run the same checkpoint on macOS with JDK 17 and an installed Android SDK:

```bash
./gradlew testDebugUnitTest compileDebugAndroidTestKotlin assembleDebug --no-daemon --max-workers=1 --console=plain
```

The Windows machine needed `JAVA_TOOL_OPTIONS=-XX:TieredStopAtLevel=1` to avoid a BellSoft JDK compiler crash. Do not add that workaround on macOS unless the Mac JDK shows the same problem.

## Continue from Stage 4

Use these documents as the accepted product and execution baseline:

- `docs/superpowers/specs/2026-08-30-kaavalan-note-redesign.md`
- `docs/superpowers/plans/2026-08-30-kaavalan-note-qwen-implementation.md`
- `docs/verification/kaavalan-redesign-stage1.md`
- `docs/verification/kaavalan-redesign-stage2.md`
- `docs/verification/kaavalan-redesign-stage3.md`

Stage 4 is the next implementation stage: consolidate capture, attachments, archive behavior, and reminders, including migration 18→19 and corresponding tests.

## Important boundaries and remaining work

- The redesign branch is not merged into `main`.
- The debug APK is a generated build artifact and is not stored in Git.
- No physical-device release-candidate QA has been completed for the redesign branch.
- DeepSeek integration, redaction approval, Ask AI mutations, WhatsApp drafting, encrypted Google Drive backup/restore, production distribution, and final release hardening are later stages.
- `LegacySearchResults.kt` and the old hierarchy/Today source remain only for compile compatibility. Remove them with the retired surfaces during Stage 8, after replacement tests pass.
- Never commit `local.properties`, signing material, Google credentials, passwords, or a DeepSeek API key. Store secrets in the intended local/runtime secret mechanism only.

## Before making new changes

```bash
git status
git log --oneline --decorate -8
./gradlew testDebugUnitTest --no-daemon --max-workers=1 --console=plain
```

Start new work only from a clean `qwen/kaavalan-redesign` checkout at the latest pushed commit.
