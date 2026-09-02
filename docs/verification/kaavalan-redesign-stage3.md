# KaavalanNote redesign Stage 3 verification

Date: September 2, 2026

## Implementation

- Replaced the active People screen with a simple searchable list.
- Reduced the editable person profile to name, phone, rank or role, and unit.
- Kept legacy relationship, cadence, tag, person-link, and Vault fields only for staged compatibility and data preservation; they are no longer active People-screen behavior.
- Added person-detail sections for active and completed instructions, including Done and Reopen actions.
- Added private group labels with an optional responsible-person association.
- Added a Room `group_labels` table, DAO, repository, Hilt bindings, and database migration 17→18.
- Made group-label names case-insensitively unique and sorted them case-insensitively.
- Preserved contact import and now retain the selected phone number.
- Kept capture available from People through the existing proven capture surface.
- Did not read, discover, or synchronize WhatsApp groups. Group labels are private local records only.
- Added English and Tamil strings for the simplified People experience.
- Added a compile-only adapter for the obsolete Today search/hierarchy source. Stage 8 should remove that adapter together with the retired Today implementation.

## Data-preservation evidence

- Migration 17→18 creates only the new `group_labels` table and its indexes.
- The migration test inserts a legacy person containing identity, contact, relationship, cadence, interaction, and Vault values, runs the migration, and verifies every legacy value is unchanged.
- The existing complete migration-chain test now runs 16→17→18 through Room and validates the current schema.
- The active UI uses a `PersonProfile` projection containing only `id`, `name`, `phone`, `rankOrRole`, and `unit`; legacy columns are not destructively removed.

## Test-first evidence

### RED

- The first focused Stage 3 test run failed at compilation because the group-label repository, migration 17→18, active person projection, and instruction-section partition did not exist.
- After the initial implementation, production compilation exposed obsolete Today/hierarchy source dependencies. Those were isolated behind a temporary compatibility adapter rather than reintroducing retired behavior into the active People screen.
- The first full regression run exposed four stale tests: one migration test stopped at schema 17, and three UI contract tests still searched for the retired `EmptyState` name. The tests were updated to validate the current schema and `PeopleEmptyState` behavior.

### GREEN

- Focused Stage 3 verification:
  - `$env:JAVA_TOOL_OPTIONS='-XX:TieredStopAtLevel=1'; .\gradlew.bat testDebugUnitTest --tests "com.kaavalan.note.data.groups.*" --tests "com.kaavalan.note.di.Migration17To18Test" --tests "com.kaavalan.note.ui.home.PeopleRedesignContractTest" --tests "com.kaavalan.note.ui.home.PersonInstructionSectionsTest" --tests "com.kaavalan.note.ui.home.HomeViewModelTest" --no-daemon --max-workers=1 --console=plain`
  - `BUILD SUCCESSFUL`; 15 focused tests passed.
- Focused regression verification for the updated migration chain and People empty state:
  - `$env:JAVA_TOOL_OPTIONS='-XX:TieredStopAtLevel=1'; .\gradlew.bat testDebugUnitTest --tests "com.kaavalan.note.di.Migration16To17Test" --tests "com.kaavalan.note.ui.home.HomeScreenTest" --no-daemon --max-workers=1 --console=plain`
  - `BUILD SUCCESSFUL`.
- Final full checkpoint:
  - `$env:JAVA_TOOL_OPTIONS='-XX:TieredStopAtLevel=1'; .\gradlew.bat testDebugUnitTest compileDebugAndroidTestKotlin assembleDebug --no-daemon --max-workers=1 --console=plain`
  - `BUILD SUCCESSFUL` in 3m16s.
  - 612 JVM tests total: 600 passed, 12 skipped, 0 failures, 0 errors.
  - Android instrumentation-test sources compiled successfully.
  - Universal debug APK assembled at `app/build/outputs/apk/debug/app-universal-debug.apk` (95,326,258 bytes).
- `git diff --check` passed.
- A targeted diff scan found no committed email/password pair, DeepSeek key assignment, or common `sk-...` secret token.

## Device verification boundary

- The connected Motorola test phone was deliberately not overwritten at this checkpoint.
- Physical-device installation, normal- and large-font screenshots, process/reboot behavior, reminder delivery, and endurance testing remain release-candidate work.
- This checkpoint is build- and regression-tested, but it is not yet a production-release certification.
