# Baton — Agent Guide

Baton is an open-source Android project (Apache 2.0). This file is the entry point for any AI coding agent (Mavis, Codex, Cursor, Aider, etc.) working in this repo.

## What this project is

A native Android (Kotlin/Compose) app for an IPS officer with ADHD. It tracks instructions flowing in from superiors and out to subordinates, with ML Kit on-device OCR for photo capture, Supabase for sync/auth, and a cloud MCP server for desktop tools.

**Read [`docs/superpowers/specs/2026-08-10-baton-design.md`](docs/superpowers/specs/2026-08-10-baton-design.md) before doing anything.** It is the source of truth for architecture, data model, and design rules.

## How to work in this repo

### Module layout (as-built, v1.9.6)

The plan in `docs/superpowers/specs/2026-08-10-baton-design.md` describes a multi-module layout (`ai/`, `features/`, `shared/`, `server/`) — that was aspirational. v1.9.6 is a single-module Android app:

```
baton/
├── app/                                # Android app (Kotlin + Compose)
│   ├── src/main/java/com/baton/app/
│   │   ├── MainActivity.kt             # single-activity host
│   │   ├── BatonApplication.kt         # Hilt entry
│   │   ├── data/                       # persistence + sync
│   │   │   ├── local/                  # Room/SQLCipher (AppDatabase + DAOs)
│   │   │   ├── sync/                   # Supabase sync engine (dormant in v1.5.0+)
│   │   │   ├── vault/                  # VaultModeHolder (Visible/Hidden)
│   │   │   ├── ocr/                    # ML Kit OCR wrapper
│   │   │   ├── undo/                   # UndoableAction + UndoController
│   │   │   ├── dev/                    # FixtureLoader (synthetic-data.json)
│   │   │   ├── auth/                   # SecurePreferences (Argon2id + AES-GCM)
│   │   │   └── appstate/               # EnergyState hook (MindAnchor v2.x)
│   │   ├── di/                         # Hilt modules (DatabaseModule, ...)
│   │   ├── features/capture/           # voice/text/photo capture
│   │   ├── ui/
│   │   │   ├── home/                   # HomeScreen + AddPersonSheet
│   │   │   ├── today/                  # decay/brief/TodayScreen
│   │   │   ├── settings/               # SettingsSheet + ViewModel
│   │   │   └── components/             # BatonAccent, design system atoms
│   │   ├── data/work/                  # WorkManager workers
│   │   └── ...
│   ├── src/test/                       # 525+ unit tests
│   ├── src/androidTest/                # instrumented tests
│   ├── build.gradle.kts
│   └── src/main/assets/                # synthetic-data.json fixture
├── gradle/libs.versions.toml           # version catalog
├── .github/workflows/                  # self-hosted Android CI
├── docs/                               # threat model, design rules, plan, MCP contract
├── supabase/functions/mcp-server/      # Cloud MCP server (v0.4.0)
└── AGENTS.md                           # you are here
```

### Design rules (non-negotiable)

- **Never introduce a "red overdue" badge or streak counter.** Use "carried over" framing.
- **No third-party AI.** No API calls to OpenAI / Anthropic / Google Cloud AI / any cloud LLM. The only AI in v1.9.6 is on-device ML Kit OCR. (The on-device LLM was removed in v1.6.1 — see [`docs/architecture/ai-strategy.md`](docs/architecture/ai-strategy.md) for the why.)
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
- **ML Kit Text Recognition v2** (Latin script) for photo OCR. **This is the only AI in v1.9.6** — the v1.5.x llama.cpp + Whisper.cpp stack was removed in v1.6.1 because cold-start + inference on a 1.7B LLM blew the 5-second capture budget. See [`docs/architecture/ai-strategy.md`](docs/architecture/ai-strategy.md) for the full rationale and the v2.x plan.
- **Android system `SpeechRecognizer`** for voice capture. On-device variant is requested via `EXTRA_PREFER_OFFLINE`; actual on-device vs cloud is a device-dependent property the app cannot enforce. v2.x may swap to ML Kit on-device speech.
- **Supabase** (Postgres + Auth + Storage + Realtime + Edge Functions) for cloud
- **Gradle Version Catalog** (libs.versions.toml) for dependency management

### Testing rules

- **Test the user-visible behavior, not the implementation.**
- **"Finding tests"** — tests that assert conclusions from the design (e.g., "the home screen never shows a red overdue badge") are required for the ADHD UX rules. If a rule exists in this file, there must be a test that fails if the rule is broken.
- **Reproduce any quoted number.** If you write "5 seconds", there's a benchmark.

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

These may come in v1.1+.

## Build commands (planned)

```bash
./gradlew :app:assembleDebug           # Build debug APK
./gradlew :app:testDebugUnitTest       # Run unit tests
./gradlew :app:lintDebug               # Lint
./gradlew :app:connectedAndroidTest    # Instrumented tests (requires device)
```

## Repo conventions

- **Trunk-based.** Single `main` branch, no long-lived feature branches.
- **Commit messages:** imperative, present tense ("Add capture flow", not "Added").
- **PR titles** = commit messages. No `feat:` / `fix:` prefixes unless conventional commits is enforced.
- **Squash-merge** to `main`. Each commit on `main` should be a logical unit.

## Related repos

- **MindAnchor** (`github.com/sampathmannam/MindAnchor`) — shares the `app-anchor-crypto` module and provides energy/notification state via MCP.
- **Rowdy-Baby / CCA** (`github.com/sampathmannam/Rowdy-Baby`) — separate project for crime analytics. Not integrated with Baton in v1.
