## What this does

<!-- 1-3 sentences. What changes, why. -->

## Type of change

- [ ] Bug fix (non-breaking change that fixes an issue)
- [ ] New feature (non-breaking change that adds functionality)
- [ ] Breaking change (fix or feature that would cause existing functionality to change)
- [ ] Documentation only
- [ ] Refactor / cleanup

## Linked issue / spec

<!-- If this closes a GitHub issue, write "Closes #N". If it implements a section of the design doc, link to it. -->

## Design rules checklist (AGENTS.md)

If your change touches UI, verify the non-negotiables:

- [ ] No "red overdue" badge, no streak counter, no shame framing
- [ ] No new third-party AI call
- [ ] No new analytics / telemetry / off-device crash reporting
- [ ] Capture flow is still < 5 seconds
- [ ] If you added or modified a rule, the corresponding "finding test" in `features/adhd/AdhdUxFindingTests.kt` was updated

## Privacy checklist (if relevant)

- [ ] No third-party AI provider sees the data
- [ ] No new analytics / crash reporting path that sends data off-device
- [ ] Vault-mode items stay local-only

## Testing

- [ ] I added unit tests for new behaviour
- [ ] I added UI tests for new screens
- [ ] `./gradlew :app:testDebugUnitTest` passes locally
- [ ] `./gradlew :app:lintDebug` shows no new errors (warnings OK)

## Pre-merge

- [ ] `Baton Android CI` is green on this branch
- [ ] `.sdd/` is **not** in the diff (scratch dir is gitignored, see PLAN.md §2.2)
- [ ] `versionCode` / `versionName` updated if this is a release commit
- [ ] Release notes file added in `docs/` if this is a release commit

## Notes for reviewer

<!-- Anything specific you want the reviewer to look at, or decisions you made that aren't obvious from the diff. -->
