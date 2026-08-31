# KaavalanNote redesign Stage 1 verification

## Commits

- Starting commit: `1b33b22674797bc03eea47a983af2b6c393ae124`
- Ending commit: the commit containing this checkpoint, with subject `refactor: simplify instruction lifecycle` (resolve as `git rev-parse HEAD` after commit)

## Implementation

- Reduced persisted/domain statuses to `TO_DO`, `WAITING`, and `DONE`.
- Reduced priorities to `NORMAL` and `URGENT`; deprecated aliases keep existing staged callers compiling without adding enum values.
- Added action summary, hard deadline, follow-up, archive time, responsible person, group label, local revision, migration-review marker, and migration metadata.
- Added timeline/person observations ordered by follow-up when present, otherwise hard deadline. Hard deadline remains a separate field.
- Added draft/patch/update-result lifecycle APIs with optimistic `updatedAt` conflict detection.
- Added transactional create, update, done, archive, restore, and permanent-delete paths using local Room data.
- Reused `AuditChainWriter` for `CREATED`, `AI_PROPOSAL_CONFIRMED`, `FIELD_CHANGED`, `STATUS_CHANGED`, `ARCHIVED`, and `RESTORED`. Audit payloads contain only status/revision metadata.
- Added Room database version 17 and registered `MIGRATION_16_17`.
- Added one neutral fallback branch in the legacy person-detail status renderer because Kotlin cannot treat companion aliases as exhaustive enum branches. Stage 2 owns the full UI migration.

## Files changed

- `app/src/main/java/com/kaavalan/note/data/instructions/Instruction.kt`
- `app/src/main/java/com/kaavalan/note/data/instructions/InstructionRepository.kt`
- `app/src/main/java/com/kaavalan/note/data/instructions/RoomInstructionRepository.kt`
- `app/src/main/java/com/kaavalan/note/data/local/entities/InstructionEntity.kt`
- `app/src/main/java/com/kaavalan/note/data/local/InstructionDao.kt`
- `app/src/main/java/com/kaavalan/note/data/local/AppDatabase.kt`
- `app/src/main/java/com/kaavalan/note/di/DatabaseModule.kt`
- `app/src/main/java/com/kaavalan/note/ui/home/PersonDetailScreen.kt` (compile-only staged compatibility)
- `app/src/test/java/com/kaavalan/note/data/instructions/InstructionLifecycleTest.kt`
- `app/src/test/java/com/kaavalan/note/data/timeline/TimelineGroupingTest.kt`
- `app/src/test/java/com/kaavalan/note/di/Migration16To17Test.kt`
- `app/src/test/java/com/kaavalan/note/features/adhd/AdhdUxFindingTests.kt` (obsolete status assertion)
- `app/src/test/java/com/kaavalan/note/integration/FifteenDaySimulationTest.kt` (obsolete status assertions)
- `docs/verification/kaavalan-redesign-stage1.md`

## Test-first evidence

### RED

- `.\gradlew.bat testDebugUnitTest --tests "com.kaavalan.note.data.instructions.InstructionLifecycleTest"`
  - `BUILD FAILED` in 1m32s.
  - Expected missing-contract failures included `InstructionDraft`, `InstructionPatch`, `UpdateResult`, new fields, timeline observations, archive/restore, and the epoch-millis done API.
  - The run also exposed an invalid JUnit `assertIs` import in the new test; it was corrected before production implementation continued.
- `.\gradlew.bat testDebugUnitTest --tests "com.kaavalan.note.data.timeline.TimelineGroupingTest"`
  - `BUILD FAILED` in 13s.
  - Expected failures: missing `TimelineBucket`, `timelineBucket`, `timelineAtEpochMs`, and deadline/follow-up fields.
- `.\gradlew.bat testDebugUnitTest --tests "com.kaavalan.note.di.Migration16To17Test"`
  - `BUILD FAILED` in 9s.
  - Expected failure: missing `AppDatabase.MIGRATION_16_17`.
- `.\gradlew.bat testDebugUnitTest --tests "com.kaavalan.note.data.instructions.InstructionLifecycleTest" --console=plain`
  - After self-review test addition: 4 tests completed, 1 failed.
  - Expected failure: `legacy audience updates keep responsible person and group label synchronized`.

### GREEN

- `.\gradlew.bat testDebugUnitTest --tests "com.kaavalan.note.data.instructions.InstructionLifecycleTest" --console=plain`
  - 4 tests, 0 failures/errors/skips; `BUILD SUCCESSFUL` in 1m41s.
- `.\gradlew.bat testDebugUnitTest --tests "com.kaavalan.note.data.timeline.TimelineGroupingTest" --console=plain`
  - 2 tests, 0 failures/errors/skips; `BUILD SUCCESSFUL` in 17s.
- `.\gradlew.bat testDebugUnitTest --tests "com.kaavalan.note.di.Migration16To17Test" --console=plain`
  - 2 tests, 0 failures/errors/skips; `BUILD SUCCESSFUL` in 9s.
- `.\gradlew.bat testDebugUnitTest --tests "com.kaavalan.note.features.adhd.AdhdUxFindingTests" --tests "com.kaavalan.note.integration.FifteenDaySimulationTest" --console=plain`
  - `BUILD SUCCESSFUL` in 1m01s after aligning obsolete seven-status assertions with Stage 1.
- `$env:JAVA_TOOL_OPTIONS='-XX:TieredStopAtLevel=1'; .\gradlew.bat testDebugUnitTest --no-daemon --max-workers=1 --console=plain`
  - 586 tests: 574 passed, 12 skipped, 0 failures, 0 errors.
  - `BUILD SUCCESSFUL` in 2m51s.
  - C1-only execution was used because an earlier unmitigated full run hit a BellSoft JDK 17 C2 `EXCEPTION_ACCESS_VIOLATION` while compiling Robolectric's `SQLiteStatement.executeUpdateDelete`; that run had no assertion failure.

### Regression and environment runs

- `.\gradlew.bat testDebugUnitTest --console=plain`
  - First regression run: 585 tests, 6 obsolete seven-status assertion failures, 12 skipped. The failing classes were rerun GREEN after their assertions were aligned with Stage 1.
- `.\gradlew.bat testDebugUnitTest --console=plain`
  - Later run: the Gradle test worker exited non-zero after the BellSoft JDK C2 access violation; no test assertion failed before the JVM crash.
- `$env:JAVA_TOOL_OPTIONS='-XX:TieredStopAtLevel=1'; .\gradlew.bat testDebugUnitTest --no-daemon -Dorg.gradle.workers.max=1 --console=plain`
  - Did not run tests: PowerShell/Gradle parsed `.gradle.workers.max=1` as a task. Corrected to the native `--max-workers=1` option used by the final GREEN run.

## Migration mapping and counts

Representative v16 input contained every old status plus one unknown-ownership row:

| Legacy status / ownership | Count | v17 result | Review/archive evidence |
|---|---:|---|---|
| `OPEN` | 1 | `TO_DO` | no marker |
| `ACK_PENDING` / outgoing | 1 | `WAITING` | no marker |
| `IN_PROGRESS` / incoming | 1 | `TO_DO` | no marker |
| `WAITING_ON_OTHER` / outgoing | 1 | `WAITING` | no marker |
| `DONE` | 1 | `DONE` | completed data preserved |
| `CARRIED_OVER` | 1 | `TO_DO` | no marker |
| `DROPPED` | 1 | `TO_DO` | archived; `legacy_status=DROPPED` |
| `IN_PROGRESS` / unknown ownership | 1 | `TO_DO` | migration review required |

Final fixture counts: `TO_DO=5`, `WAITING=2`, `DONE=1`, archived `=1`, migration review `=1`. Priorities map `LOW/NORMAL→NORMAL` and `HIGH/URGENT→URGENT`.

The migration adds nine columns and three indexes with `ALTER TABLE`/index creation, updates values in place, and does not rebuild or drop the instructions table. The migration test verifies all 8 instruction rows remain, raw text/title/deadline/capture/create/update timestamps and person links remain unchanged, the local revision initializes to 1, and an unrelated recoverable attachment row remains unchanged.

## Privacy and data preservation

- Original `rawText` remains independent of editable `actionSummary`.
- Existing legacy columns are retained; no record or attachment is deleted by migration.
- No `fallbackToDestructiveMigration` was added.
- Core lifecycle and timeline operations use local Room transactions and require no network.
- Audit payloads exclude original captures, API keys, and raw AI request/response content.
- No DeepSeek key, token, private key, credential, or external-delivery state was added to Room, logs, source, or BuildConfig.
- No WhatsApp or other external intent mutates status or completion state in this stage.

## Self-review

- Fixed a discovered consistency gap where legacy `setAudience` updated old audience columns but not the new responsible-person/group-label fields; added RED→GREEN lifecycle coverage.
- Confirmed the required repository methods are all overridden by `RoomInstructionRepository`; fail-fast interface defaults exist only so legacy test doubles compile.
- Confirmed timeline ordering uses follow-up first and preserves deadline independently.
- Confirmed status/priority enum entries are exactly the approved values.
- Confirmed migration SQL has deterministic mappings and no destructive operation.
- Confirmed changed legacy UI/test files are directly required by staged enum compatibility; no unrelated feature or dependency was added.

## Remaining risks

- Deprecated status/priority aliases intentionally emit warnings until Stage 2 migrates remaining callers.
- Schema export remains disabled, but migration coverage now combines the representative raw fixture with a complete AppDatabase downgrade/migrate/reopen test that passes Room's full v17 schema validation.
- The Windows BellSoft JDK 17 C2 compiler crash required C1-only full-suite execution; all tests then completed with zero failures.

## Independent-review remediation

Follow-up commit: the commit containing this section, with subject `fix: preserve instruction data across lifecycle paths`.

### Verified findings and fixes

1. **Relationship retention (Critical).** Confirmed that `InstructionDao.upsert` used `INSERT OR REPLACE`; replacing an existing instruction triggered the `instruction_tags` cascading FK and removed links. Added `@Update updateExisting` and changed repository update, lifecycle, legacy patch, and sensitivity mutations to in-place updates. Creation remains the only repository path using insert/replace.
2. **Responsible-person observation (Important).** Confirmed `observeForPerson` matched only `personId`. It now matches `personId OR responsiblePersonId`, while retaining archive filtering and follow-up/deadline ordering.
3. **Backup/plain import preservation (Important).** Confirmed the existing full-backup and plain CSV/JSON formats omitted all nine Stage 1 fields and restored raw legacy enum strings. Existing formats now append optional Stage 1 keys/columns, read older files defensively, preserve original text separately, and normalize legacy status/priority through the domain mappings. No Stage 7 backup architecture was added.
4. **Compatibility archive semantics (Important).** Confirmed both `markDropped` overloads changed status via `DROPPED→DONE`, and `reopen` changed it via `OPEN→TO_DO`. Compatibility archive/restore now update archive metadata in place and preserve the current simplified status/completion state.
5. **Complete Room schema validation (Important).** Confirmed prior tests validated migration SQL only against a partial fixture. Added a complete AppDatabase test that creates v17, downgrades only the instructions table/user version to v16, reopens with production `MIGRATION_16_17`, and verifies Room opens and validates every v17 table/index. The production migration itself passed once the test harness correctly forced Room's lazy database opens; no migration SQL change was needed.
6. **Permanent delete distinction (Minor).** Added coverage proving ordinary mutations retain tag links, while explicit permanent delete removes the instruction, FTS row, and dependent instruction-tag links.

### Review-fix RED evidence

- `.\gradlew.bat testDebugUnitTest --tests "com.kaavalan.note.data.instructions.InstructionLifecycleTest" --console=plain`
  - 6 tests, 1 failure in 1m08s: `ordinary lifecycle mutations preserve instruction tag relationships`.
  - Root cause: `INSERT OR REPLACE` deleted/reinserted the parent and cascaded the cross-ref.
- `.\gradlew.bat testDebugUnitTest --tests "com.kaavalan.note.data.instructions.InstructionLifecycleTest.observe for person includes instructions assigned by responsible person" --console=plain`
  - 1 test, 1 failure in 15s: empty result (`NoSuchElementException`).
  - Root cause: DAO predicate matched only `personId`.
- `.\gradlew.bat testDebugUnitTest --tests "com.kaavalan.note.data.export.BackupRoundTripTest.backup restore preserves Stage 1 fields and normalizes legacy enums" --tests "com.kaavalan.note.data.export.PlainImporterTest.*Stage 1*" --console=plain`
  - 3 tests, 3 failures in 26s.
  - Root cause: Stage 1 fields were absent and `ACK_PENDING/HIGH` restored unchanged.
- `.\gradlew.bat testDebugUnitTest --tests "com.kaavalan.note.data.instructions.InstructionLifecycleTest.compatibility archive and reopen preserve simplified status" --console=plain`
  - 1 test, 1 failure in 18s.
  - Root cause: compatibility aliases mutated `WAITING` to `DONE`/`TO_DO`.
- Complete-schema migration test harness RED sequence:
  - First attempt: compile failure in 4s because this RoomDatabase is not Kotlin `Closeable` and cannot use `.use`.
  - Second attempt: 1 failure in 16s because Room's seed database had not been forced open, so no tables existed.
  - Third attempt: 1 failure in 18s because `isOpen` was asserted before the migrated Room connection was forced open.
  - These were harness failures while adding the missing coverage; once corrected, the existing production migration passed full Room validation without a production schema change.

### Review-fix GREEN evidence

- Lifecycle relationship retention plus permanent-delete coverage: `InstructionLifecycleTest` GREEN in 1m34s after switching known-existing entities to `@Update`.
- Responsible-person focused test: GREEN in 48s after broadening the query.
- Backup/CSV/JSON Stage 1 focused tests: 3/3 GREEN in 43s after format preservation/normalization changes.
- Compatibility archive focused test: GREEN in 1m03s after preserving status independently.
- Complete Room-open migration validation: GREEN in 14s; Room opened the migrated complete database at user version 17.
- Covering suites command:
  - `$env:JAVA_TOOL_OPTIONS='-XX:TieredStopAtLevel=1'; .\gradlew.bat testDebugUnitTest --no-daemon --max-workers=1 --tests "com.kaavalan.note.data.instructions.InstructionLifecycleTest" --tests "com.kaavalan.note.di.Migration16To17Test" --tests "com.kaavalan.note.data.export.BackupRoundTripTest" --tests "com.kaavalan.note.data.export.BackupManagerTest" --tests "com.kaavalan.note.data.export.PlainExporterTest" --tests "com.kaavalan.note.data.export.PlainImporterTest" --tests "com.kaavalan.note.data.local.RoomInstructionRepositoryTest" --tests "com.kaavalan.note.data.local.RoomInstructionRepositoryCrashRecoveryTest" --tests "com.kaavalan.note.data.local.InstructionDaoTest" --tests "com.kaavalan.note.data.local.FtsSearchTest" --tests "com.kaavalan.note.data.instructions.HierarchyDispatchEndToEndTest" --console=plain`
  - `BUILD SUCCESSFUL` in 1m09s.
- Full JVM command (forced fresh execution):
  - `$env:JAVA_TOOL_OPTIONS='-XX:TieredStopAtLevel=1'; .\gradlew.bat testDebugUnitTest --no-daemon --max-workers=1 --rerun-tasks --console=plain`
  - `BUILD SUCCESSFUL` in 5m43s; 594 tests total, 582 passed, 12 skipped, 0 failures, 0 errors; all 31 tasks executed.

### Follow-up files changed

- `app/src/main/java/com/kaavalan/note/data/export/BackupManager.kt`
- `app/src/main/java/com/kaavalan/note/data/export/PlainExporter.kt`
- `app/src/main/java/com/kaavalan/note/data/export/PlainImporter.kt`
- `app/src/main/java/com/kaavalan/note/data/instructions/RoomInstructionRepository.kt`
- `app/src/main/java/com/kaavalan/note/data/local/InstructionDao.kt`
- `app/src/test/java/com/kaavalan/note/data/export/BackupRoundTripTest.kt`
- `app/src/test/java/com/kaavalan/note/data/export/PlainImporterTest.kt`
- `app/src/test/java/com/kaavalan/note/data/instructions/InstructionLifecycleTest.kt`
- `app/src/test/java/com/kaavalan/note/data/local/RoomInstructionRepositoryTest.kt`
- `app/src/test/java/com/kaavalan/note/di/Migration16To17Test.kt`
- `docs/verification/kaavalan-redesign-stage1.md`

### Follow-up privacy/scope review

- Original capture remains independent in `rawText`; backup/import additions serialize it unchanged and keep `actionSummary` separate.
- No audit payload, API key, credential, raw AI payload, external intent, network service, dependency, or Stage 7 backup component was added.
- Backup/import changes are optional-field extensions to existing JSON/CSV code only and remain backward-compatible with prior files.
- Ordinary lifecycle changes now retain relationships; only explicit permanent delete performs dependent cleanup.
