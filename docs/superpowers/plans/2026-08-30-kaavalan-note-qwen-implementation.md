# KaavalanNote Qwen Implementation Plan

> **For agentic workers:** Qwen is the primary implementation agent. Execute this plan one stage at a time, use test-driven development, and stop for review after every stage. Do not perform a whole-project rewrite.

**Goal:** Convert the existing Android application into the approved private, local-first instruction and follow-up ledger while preserving user data and proven platform integrations.

**Architecture:** Retain the Kotlin, Jetpack Compose, Room/SQLCipher, Hilt, and WorkManager application. Make the encrypted local database the source of truth; isolate capture, instruction-domain, AI/redaction, reminders, WhatsApp composition, and backup behind narrow interfaces. DeepSeek receives only user-approved redacted content and can return proposals but cannot mutate records directly.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, Room, SQLCipher, Hilt, WorkManager, Android Keystore/security-crypto, ML Kit Text Recognition, Android SpeechRecognizer, Ktor or OkHttp for DeepSeek, Google Drive REST/OAuth, JUnit, MockK, Robolectric, Turbine, Room testing, Compose UI tests.

## Global Constraints

- Work in a real Git checkout of `sampathmannam/kaavalan-note`; do not initialize Git inside the downloaded API snapshot.
- Read `docs/superpowers/specs/2026-08-30-kaavalan-note-redesign.md` before every stage.
- Never delete or destructively migrate an existing record or attachment.
- Preserve the original capture independently of all AI-derived fields.
- Core capture, Timeline, People, search, status editing, attachments, archive, and reminders must work offline.
- Status values are exactly `TO_DO`, `WAITING`, and `DONE`.
- Priority values are exactly `NORMAL` and `URGENT`.
- WhatsApp is write-only composition: never read chats, infer delivery, or change status after opening an intent.
- No local LLM, Whisper, shared backend, Supabase synchronization, Vault mode, relationship-decay feature, Worry Box, Today's Win, or meeting brief may remain in the finished runtime.
- iOS implementation is excluded from this plan; preserve the versioned logical backup/export schema for a later iOS project.
- Retain Android SpeechRecognizer and ML Kit on-device photo OCR.
- Every DeepSeek call requires locally redacted content; new captures first show a transmission preview, and all record mutations require confirmation.
- The DeepSeek API key must never enter Room, logs, exports, backups, crash reports, source control, or BuildConfig.
- Keep code and schema changes minimal and reviewable. Preserve unrelated user changes.
- Every stage ends with focused tests, `./gradlew testDebugUnitTest`, a commit, and a written checkpoint report.

## Stage 0: Establish the trustworthy baseline

**Primary files:** `README.md`, `app/build.gradle.kts`, `gradle/libs.versions.toml`, `app/src/main/AndroidManifest.xml`, existing tests under `app/src/test` and `app/src/androidTest`.

**Deliverable:** A real branch with reproducible baseline evidence and no product changes.

- [ ] Confirm `git status --short --branch`, current commit, JDK 17, Android SDK availability, and that no secrets are tracked.
- [ ] Run `./gradlew testDebugUnitTest` and record passed, failed, and skipped counts without editing tests to manufacture a pass.
- [ ] Run `./gradlew assembleDebug` and record the generated APK path and SHA-256.
- [ ] Compile instrumentation tests with `./gradlew compileDebugAndroidTestKotlin`; remove the existing Gradle source-exclusion workaround only after the three excluded tests have been repaired or deliberately deleted because their product feature is removed.
- [ ] Create `docs/verification/kaavalan-redesign-baseline.md` containing commands, environment, results, known failures, and the exact starting commit.
- [ ] Commit only the baseline report with `git commit -m "docs: record redesign baseline"`.
- [ ] Stop and report. Do not begin Stage 1 if unit tests or debug assembly fail; provide the smallest root-cause diagnosis.

## Stage 1: Simplify the instruction domain and migrate safely

**Modify:**

- `app/src/main/java/com/kaavalan/note/data/instructions/Instruction.kt`
- `app/src/main/java/com/kaavalan/note/data/instructions/InstructionRepository.kt`
- `app/src/main/java/com/kaavalan/note/data/instructions/RoomInstructionRepository.kt`
- `app/src/main/java/com/kaavalan/note/data/local/entities/InstructionEntity.kt`
- `app/src/main/java/com/kaavalan/note/data/local/InstructionDao.kt`
- `app/src/main/java/com/kaavalan/note/data/local/AppDatabase.kt`
- `app/src/main/java/com/kaavalan/note/di/DatabaseModule.kt`

**Create tests:**

- `app/src/test/java/com/kaavalan/note/data/instructions/InstructionLifecycleTest.kt`
- `app/src/test/java/com/kaavalan/note/di/Migration16To17Test.kt`
- `app/src/test/java/com/kaavalan/note/data/timeline/TimelineGroupingTest.kt`

**Required interface:**

```kotlin
enum class Status { TO_DO, WAITING, DONE }
enum class Priority { NORMAL, URGENT }
enum class TimelineBucket { LATE, TODAY, NEXT_7_DAYS, LATER }

interface InstructionRepository {
    fun observeTimeline(): Flow<List<Instruction>>
    fun observeForPerson(personId: String): Flow<List<Instruction>>
    suspend fun create(draft: InstructionDraft): Instruction
    suspend fun update(id: String, expectedUpdatedAt: String, patch: InstructionPatch): UpdateResult
    suspend fun markDone(id: String, completedAtEpochMs: Long)
    suspend fun archive(id: String, archivedAtEpochMs: Long)
    suspend fun deletePermanently(id: String)
}
```

- [ ] Write failing lifecycle, timeline-boundary, and Room migration tests first.
- [ ] Add explicit fields for action summary, hard deadline, follow-up time, archive time, responsible person, group label, and local revision. Reuse existing columns only when semantics match exactly.
- [ ] Implement Room migration 16→17 with deterministic mappings from the approved spec. Ambiguous legacy ownership becomes `TO_DO` and receives a migration-review marker.
- [ ] Preserve raw text, timestamps, person links, and recoverable attachments. Do not use `fallbackToDestructiveMigration`.
- [ ] Reuse the existing audit-chain mechanism for created, confirmed AI proposal, field change, status change, archive, and restore events without storing API keys or raw DeepSeek payloads.
- [ ] Make timeline ordering use follow-up when present, otherwise deadline; keep deadline visible independently.
- [ ] Run migration tests against representative v16 databases containing every old status.
- [ ] Run all JVM tests and commit with `git commit -m "refactor: simplify instruction lifecycle"`.
- [ ] Stop with migration counts and before/after schema evidence.

## Stage 2: Replace navigation and build the Timeline

**Modify:** `app/src/main/java/com/kaavalan/note/MainActivity.kt`.

**Create:**

- `app/src/main/java/com/kaavalan/note/ui/timeline/TimelineScreen.kt`
- `app/src/main/java/com/kaavalan/note/ui/timeline/TimelineViewModel.kt`
- `app/src/main/java/com/kaavalan/note/ui/timeline/TimelineUiState.kt`
- `app/src/main/java/com/kaavalan/note/ui/ask/AskAiScreen.kt`
- Matching JVM and Compose tests under `app/src/test/java/com/kaavalan/note/ui/timeline`.

- [ ] Write failing tests for Late, Today, Next 7 days, Later; All, To do, Waiting, Done filters; and urgent rendering.
- [ ] Make Timeline the start destination and expose exactly Timeline, People, and Ask AI in bottom navigation.
- [ ] Render one efficient list with stable keys and section headers; clearly distinguish action with me from waiting on another person.
- [ ] Keep the capture action visible on Timeline and People.
- [ ] Add loading, empty, and recoverable-error states without adding dashboard cards or productivity summaries.
- [ ] Run focused Compose tests, all JVM tests, and `assembleDebug`.
- [ ] Commit with `git commit -m "feat: add instruction timeline"` and stop with screenshots at normal and large font sizes.

## Stage 3: Simplify People and private group labels

**Modify:** Person domain/entity/repository files, `HomeScreen.kt`, `HomeViewModel.kt`, `PersonDetailScreen.kt`, `PersonDetailViewModel.kt`, and `AddPersonSheet.kt`.

**Create:** `GroupLabel.kt`, `GroupLabelEntity.kt`, `GroupLabelDao.kt`, repository, migration 17→18, and tests.

- [ ] Write failing tests proving a person contains only name, phone, rank/role, unit, and linked instructions.
- [ ] Remove tier, cadence, last-interaction, important-date, person-link, tag, and Vault UI behavior from active product paths.
- [ ] Add private group labels with optional responsible-person association on each instruction; do not access WhatsApp's group database.
- [ ] Preserve legacy person identity and contact fields during migration.
- [ ] Add person search and active/completed instruction sections.
- [ ] Run migration, repository, ViewModel, and Compose tests.
- [ ] Commit with `git commit -m "feat: simplify people and group labels"` and stop with data-preservation evidence.

## Stage 4: Consolidate capture, attachments, archive, and reminders

**Modify:** Existing capture files under `features/capture`, `PhotoOcrHelper.kt`, `NextActionDatePicker.kt`, `InstructionDetailSheet.kt`, `WorkManagerInitializer.kt`, and manifest declarations.

**Create:**

- `data/attachments/Attachment.kt`, `AttachmentEntity.kt`, `AttachmentDao.kt`, `AttachmentRepository.kt`
- `data/reminders/ReminderScheduler.kt`, `ReminderWorker.kt`, `ReminderReceiver.kt`
- Migration 18→19 and corresponding tests

- [ ] Write failing tests that save the raw capture before downstream processing and copy selected photos/documents into app-private storage.
- [ ] Preserve text, Android SpeechRecognizer, ML Kit OCR, document picker, and Android Share capture.
- [ ] Support photos and documents only; reject unsupported media with a clear message.
- [ ] Implement searchable Done archive and confirmed permanent deletion that also removes private attachment copies.
- [ ] Implement one follow-up notification with Open, Done, and Snooze actions. Snooze choices are one hour, tomorrow 9:00 AM, and custom.
- [ ] Reconcile notifications after edits, reboot, timezone change, and restore. Ignored reminders remain Late and do not repeat.
- [ ] Run capture, file-lifecycle, reminder, reboot, timezone, and Compose tests.
- [ ] Commit with `git commit -m "feat: harden capture attachments and reminders"` and stop with offline evidence.

## Stage 5: Add local redaction and DeepSeek proposals

**Create:**

- `data/ai/AiGateway.kt`, `DeepSeekGateway.kt`, `AiModels.kt`, `AiRequestWorker.kt`, `AiJobStore.kt`, `AiJobEntity.kt`, `AiJobDao.kt`
- `data/redaction/RedactionService.kt`, `RedactionModels.kt`
- `data/auth/DeepSeekKeyStore.kt`
- `features/ai/TransmissionPreviewSheet.kt`, `ProposalReviewSheet.kt`
- Focused unit tests for every new component

**Required interfaces:**

```kotlin
interface RedactionService {
    fun redact(input: String, context: RedactionContext): RedactedPayload
    fun rehydrate(input: String, mapping: Map<String, String>): String
}

interface AiGateway {
    suspend fun extract(payload: RedactedPayload): AiResult<InstructionProposal>
    suspend fun answer(payload: RedactedPayload): AiResult<AiAnswer>
    suspend fun draftFollowUp(payload: RedactedPayload, style: DraftStyle): AiResult<String>
}
```

- [ ] Write tests for names, phones, locations, case references, repeated placeholders, Tamil/mixed text, malformed JSON, timeouts, stale results, retries, and log redaction.
- [ ] Store the API key only through Keystore-backed secure preferences; add a connection test that does not persist or log response content.
- [ ] Save captures before automatically preparing the redacted transmission preview. Contact DeepSeek only after approval.
- [ ] Validate structured responses locally and rehydrate placeholders only in memory.
- [ ] Persist idempotent AI jobs without original plaintext payloads. Reject stale proposals when the record revision has changed.
- [ ] Add and test Room migration 19→20 for queued AI-job metadata; store record IDs, operation type, revision, retry timing, and non-sensitive error category only.
- [ ] Apply no AI-generated mutation until the user confirms the proposal card.
- [ ] Run unit tests with a fake HTTP engine; never use the real API key in automated tests.
- [ ] Commit with `git commit -m "feat: add privacy-gated DeepSeek proposals"` and stop with request-body and log-safety evidence.

## Stage 6: Complete Ask AI and WhatsApp composition

**Modify:** `AskAiScreen.kt`, person detail, instruction detail, and the existing delivery/composer path.

**Create:** `features/ai/AskAiViewModel.kt`, `data/whatsapp/WhatsAppComposer.kt`, and tests.

- [ ] Write tests showing Ask AI receives only explicitly selected, redacted local context and returns answers or proposed changes.
- [ ] Support questions, summaries, and proposals for create, status, deadline, and follow-up changes; require confirmation for every proposal.
- [ ] Produce one follow-up draft and refinements Shorter, Firmer, Softer, Tamil, and English.
- [ ] Open WhatsApp with prepared text where Android supports it; otherwise use a chooser with the text preserved.
- [ ] Prove that intent launch, cancellation, and return cannot set sent, acknowledged, or done.
- [ ] Run JVM, Compose, and intent tests.
- [ ] Commit with `git commit -m "feat: add Ask AI and WhatsApp drafting"` and stop with mutation-safety evidence.

## Stage 7: Finish encrypted Drive backup and safe restore

**Modify:** Existing files under `data/backup`, OAuth callback, Settings, WorkManager initialization, `local.properties.example`, and backup tests.

- [ ] Write failing round-trip tests covering database, attachment manifest, version, counts, integrity hash, 30-backup retention, wrong recovery secret, corruption, interrupted upload, and API-key exclusion.
- [ ] Consolidate duplicate backup implementations under `data/backup`; remove plaintext export from user-facing production paths.
- [ ] Keep Data export as a manual encrypted, versioned archive written through Android's Storage Access Framework; use the same platform-neutral logical manifest as Drive backup.
- [ ] Create the encrypted archive locally, validate it, upload it, verify the Drive object, and only then record success.
- [ ] Stage and validate restore into temporary app-private storage before an atomic active-data replacement.
- [ ] Configure daily constrained backup plus Back up now and retain the latest 30 successful objects.
- [ ] Replace the OAuth placeholder through documented local/CI configuration. Do not commit OAuth secrets.
- [ ] Run fake-Drive tests, JVM suite, and debug assembly.
- [ ] Commit with `git commit -m "feat: complete encrypted Drive recovery"` and stop for real Google OAuth/device verification.

## Stage 8: Remove obsolete product paths and stale dependencies

**Remove only after replacement tests pass:** brief, decay, win, worry, tags, important dates, person links, hierarchy dispatch/roster, Vault mode/PIN, CCTNS, Supabase sync/outbox/conflict code, and their obsolete UI/tests/assets.

- [ ] Build a reference list with `rg` before deleting each feature family; preserve utilities still used by the approved product.
- [ ] Remove dead manifest components, Hilt bindings, workers, database entities/DAOs, navigation routes, resources, dependencies, comments, and documentation.
- [ ] Remove Supabase libraries and unused Ktor modules only after Gradle dependency resolution and DeepSeek networking pass.
- [ ] Keep SQLCipher, security-crypto, WorkManager, CameraX/ML Kit, SpeechRecognizer integration, Drive/OAuth, and the chosen HTTP client.
- [ ] Repair instrumentation sources instead of excluding dead files from Gradle.
- [ ] Run `./gradlew clean testDebugUnitTest compileDebugAndroidTestKotlin assembleDebug lintDebug`.
- [ ] Commit with `git commit -m "refactor: remove obsolete Kaavalan features"` and stop with before/after dependency and APK-size reports.

## Stage 9: Localization, acceptance, and release candidate

**Modify:** English/Tamil string resources, onboarding, privacy copy, README, runbook, privacy policy, threat model, Play listing, and changelog.

- [ ] Move all user-facing strings to resources and provide complete English and Tamil translations.
- [ ] Add accessibility labels, dynamic-font checks, touch-target checks, screen-reader ordering, and color-independent status cues.
- [ ] Run all automated checks: `./gradlew clean testDebugUnitTest lintDebug assembleDebug bundleRelease compileDebugAndroidTestKotlin`.
- [ ] On a physical Android phone, test offline capture, reboot, timezone change, reminders, OCR, speech, document attachment, WhatsApp chooser, DeepSeek approval/retry, Google OAuth, backup, wrong-secret restore, successful restore, Tamil, and large text.
- [ ] Verify the DeepSeek key is absent from Room, logs, exported archives, backup plaintext after decryption, APK resources, and source control.
- [ ] Produce `docs/verification/kaavalan-redesign-release.md` with commands, device/OS, pass/fail results, screenshots, APK/AAB paths and SHA-256 values, unresolved risks, and rollback instructions.
- [ ] Commit with `git commit -m "release: prepare KaavalanNote redesign candidate"`.
- [ ] Stop. Do not upload to Google Play until the user approves the release evidence and supplies required console authorization.

## Qwen checkpoint contract

At the end of every stage, Qwen must report:

1. Starting and ending commit.
2. Files added, modified, and removed.
3. Tests written before implementation.
4. Exact commands and observed results.
5. Data-migration or privacy evidence relevant to the stage.
6. Remaining risks or failures.
7. A clear request for approval before the next stage.

Qwen must pause immediately for authentication, physical-device interaction, OAuth consent, signing credentials, Play Console actions, an ambiguous destructive migration, or a baseline failure. Those are the only expected Codex/user intervention points.
