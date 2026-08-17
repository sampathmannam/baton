# Baton v1.5.6 — QA hardening

**Date:** 2026-08-17
**Build:** versionCode=17, versionName=1.5.6

## What changed

No source code changes. This is a **QA hardening release** — the v1.5.5 build was solid; v1.5.6 adds test coverage so regressions get caught.

## Tests added (16 new)

In `app/src/test/java/com/baton/app/qa/V156QaTest.kt`:

| ID | Test |
|---|---|
| E-01 | onConfirm passes proposal.person through findOrCreate unchanged (trim is form-level) |
| E-07 | onTextChanged preserves newlines and tabs in rawText |
| E-08 | whitespace-only text is treated as blank (canExtract = false) |
| E-10 | onAddFreeTag trims whitespace, strips leading `#`, truncates to 40 chars |
| E-10b | onAddFreeTag with blank input is a no-op |
| S-01 | onConfirm without a proposal is a no-op (canConfirm = false) |
| S-02 | onConfirm flips isSaving false after success + sheet dismissed |
| S-03 | onSaveRaw saves a free-floating instruction with priority NORMAL |
| S-04 | onSaveRaw truncates title to 40 chars for long text (40 chars + "…") |
| R-04 | null proposal shows retry-friendly error + sheet stays open |
| R-05 | capture save failure surfaces user-readable error |
| R-05b | instruction save failure surfaces "Could not save instruction." |
| N-04 | dismissSheet hides the sheet but preserves the draft text (F-09 contract) |
| D-02 | new VM re-hydrates state from existing SavedStateHandle (process death + relaunch) |
| D-02b | clearDraft removes SavedStateHandle keys (no stale restore) |
| A-08 | modelState StateFlow surfaces NotStarted → Downloading → Ready transitions |

## Test count

- **Before:** 291/0/0/7
- **After:** 307/0/0/7

## On-device drive (emulator)

Every screen, every button, every UI element was driven with synthetic data (6 people, 2 instructions, 1 free-floating note). 32 on-device test cases across 8 categories — all passed. No genuine bugs found in v1.5.6.

## Docs added

- `.sdd/test-cases-v1.5.6.md` — 60 test cases across 8 categories (F functional, E edge, S state, P permissions, R error, N navigation, D data, A accessibility)
- `.sdd/qa-report-v1.5.6.md` — full results with UI dump filenames as evidence

## Verified regression coverage

- v1.5.4 photo permission flow (P-01, P-02)
- v1.5.4 model-not-ready card (R-01, R-02, R-03)
- v1.5.5 scrollable capture sheet (verified on 1080×2400 with both cards visible)
- v1.5.3 VAULT UX fixes (sensitive copy, back-key, no-shame language, loading skeleton)
- v1.5.1 VAULT-007 erase-all confirmation
- v1.5.0 vault mode (no login, all data local)

## What's NOT in v1.5.6

- No new features
- No bug fixes
- No code refactors
- No library upgrades
- No schema changes

This is a test-coverage release, not a feature release.
