# Baton — Agent Guide

Baton is a private Android project. This file is the entry point for any AI coding agent (Mavis, Codex, Cursor, Aider, etc.) working in this repo.

> **Before doing anything, read [`docs/PLAN.md`](docs/PLAN.md) for current priorities and [`docs/superpowers/specs/2026-08-10-baton-design.md`](docs/superpowers/specs/2026-08-10-baton-design.md) for the design source of truth.**

## What this project is

A native Android (Kotlin/Compose) app for an IPS officer with ADHD. It tracks instructions flowing in from superiors and out to subordinates, with on-device AI for capture/extraction, Supabase for sync/auth, and a cloud MCP server for desktop tools. Currently at **v1.9.6** with weekly shipping cadence.

## How to work in this repo

### Module layout (current — single-module reality)

```
baton/
├── app/                          # The whole app
│   ├── src/main/java/com/baton/app/
│   │   ├── ui/
│   │   │   ├── home/             # HomeScreen, PersonDetail, AddPerson, NudgeSheet
│   │   │   ├── today/            # TodayViewModel, Decay, Win, Worry, Brief
│   │   │   ├── settings/         # SettingsSheet, SettingsViewModel
│   │   │   ├── auth/             # AuthViewModel
│   │   │   ├── privacy/          # RecoveryPhrase, ThreatModel, FlagSecure
│   │   │   ├── components/       # OfflineIndicator, etc.
│   │   │   ├── theme/            # Color, Theme
│   │   │   └── util/             # SafeError
│   │   ├── features/
│   │   │   ├── capture/          # Voice/text/photo capture, share intake, widget
│   │   │   ├── onboarding/       # OnboardingViewModel
│   │   │   ├── vault/            # VaultViewModel
│   │   │   ├── theme/            # ThemeViewModel
│   │   │   └── adhd/             # AdhdUxFindingTests (the "rule" test suite)
│   │   ├── data/
│   │   │   └── captures/         # SupabaseCaptureRepository
│   │   ├── di/                   # Hilt modules, DatabaseModule, migrations
│   │   ├── qa/                   # V156QaTest
│   │   └── integration/          # FifteenDaySimulationTest
│   ├── src/test/                 # 72 test files (unit)
│   └── src/androidTest/          # M0AcceptanceTest, VaultEndToEndTest
├── supabase/                     # Migrations + Edge Functions
├── tools/
│   ├── qa/                       # qa-drive.py + utilities (moved from .sdd/)
│   └── synthetic-data/           # Test fixture generators
├── docs/
│   ├── superpowers/specs/        # Design source of truth
│   ├── development/sdd-history/  # Pre-1.0 QA reports + dev diary
│   └── PLAN.md                   # Living project plan
└── .github/workflows/android-ci.yml
```

### Module layout (planned for v2.0 — see PLAN.md §3.1)

```
baton/
├── app/                    # Android app (UI + capture + sync)
├── ai/                     # On-device AI module (llama / whisper / ocr)
├── data/                   # Persistence + sync (db / sync / mcp-client)
├── features/               # capture, people, brief, nudge, mcp
├── shared/                 # crypto, ui, time
└── server/                 # Supabase project (functions + migrations)
```

The split is **deferred to v2.0.0-pre1** so we ship v1.9.7 / v1.9.8 / v1.9.9 as a monolith first.

### Design rules (non-negotiable)

- **Never introduce a "red overdue" badge or streak counter.** Use "carried over" framing.
- **No API calls to third-party AI.** On-device LLM only (llama.cpp + GGUF).
- **No analytics, no telemetry, no crash reporting that sends data off-device.** Local logs only.
- **The single note bar is the primary input.** Don't add a separate "New task" form.
- **Tabs = 3.** Home (people), Today (brief), Settings. No more.
- **Capture must complete in < 5 seconds.** Measure it; the CI fails if it regresses.
- **Conflict resolution is last-write-wins on `updatedAt`, logged in `SyncConflict` table.** No silent data loss.

### Tech decisions (locked)

- **Kotlin 2.0+**, **Jetpack Compose** for UI, **Material 3** design system
- **Hilt** for DI, **Room + SQLCipher** for local DB
- **WorkManager** for background jobs (brief generation, sync, stale detection)
- **Coroutines + Flow** for async, **kotlinx.serialization** for JSON
- **llama.cpp** + **Qwen 3 1.7B Q4_K_M** as default model (downloaded at first run, cached locally, never in repo)
- **Whisper.cpp** + base.en / small.en model (downloaded at first run)
- **ML Kit Text Recognition** for OCR
- **Supabase** (Postgres + Auth + Storage + Realtime + Edge Functions) for cloud
- **Gradle Version Catalog** (libs.versions.toml) for dependency management

### Testing rules

- **Test the user-visible behavior, not the implementation.**
- **"Finding tests"** — tests that assert conclusions from the design (e.g., "the home screen never shows a red overdue badge") are required for the ADHD UX rules. If a rule exists in this file, there must be a test that fails if the rule is broken.
- **Reproduce any quoted number.** If you write "5 seconds", there's a benchmark.
- **CI runs three jobs** on every push: `unit-test`, `lint` (Android Lint, ktlint, detekt if present), `assemble`. **All three must pass** before merge. Currently the unit-test job is red — see [issues](../../issues) and PLAN.md §1.1.

### Privacy posture

- The user's data is police work. The bar is "no third-party AI ever sees it."
- AI provider = llama.cpp on-device. Period.
- Cloud storage = Supabase, encrypted in transit + at rest, with the option to mark items "sensitive" → local-only.
- No analytics, no telemetry, no crash reporting that sends data off-device.

### Out of scope (for v1)

- Multi-user / team mode
- CCTNS / eCourts integration
- WhatsApp Business API integration
- iOS app
- Wear OS app

These may come in v1.1+ or v2.0+.

## Build commands

```bash
./gradlew :app:assembleDebug           # Build debug APK
./gradlew :app:testDebugUnitTest       # Run unit tests
./gradlew :app:lintDebug               # Lint (warnings don't fail — see workflow)
./gradlew :app:connectedAndroidTest    # Instrumented tests (requires device)
```

CI runs the first three in parallel on every push; the third depends on the first.

## Repo conventions

- **Trunk-based.** Single default branch (currently `m0/skeleton-v1.7.0`, planned to become `main` after the `chore/sync-main-to-v1.9.6` PR merges).
- **Commit messages:** imperative, present tense ("Add capture flow", not "Added").
- **PR titles** = commit messages. No `feat:` / `fix:` prefixes unless conventional commits is enforced.
- **Squash-merge** to default branch. Each commit on the default branch should be a logical unit.
- **Branch protection on default branch:** planned — see PLAN.md §2.1.

## Related repos

- **MindAnchor** (`github.com/sampathmannam/MindAnchor`) — shares the `app-anchor-crypto` module and provides energy/notification state via MCP. Sharing mechanism: TBD — see PLAN.md §3.2 (open question).
- **Rowdy-Baby / CCA** (`github.com/sampathmannam/Rowdy-Baby`, `/cca`) — separate projects for crime analytics. Not integrated with Baton in v1.
- **Kaavalan Mobile Forensics** (`github.com/sampathmannam/kaavalan-mobile-forensics`) — separate project, shares the Tamil/IPS context.
