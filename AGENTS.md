# Baton — Agent Guide

Baton is a private Android project. This file is the entry point for any AI coding agent (Mavis, Codex, Cursor, Aider, etc.) working in this repo.

## What this project is

A native Android (Kotlin/Compose) app for an IPS officer with ADHD. It tracks instructions flowing in from superiors and out to subordinates, with on-device AI for capture/extraction, Supabase for sync/auth, and a cloud MCP server for desktop tools.

**Read [`docs/superpowers/specs/2026-08-10-baton-design.md`](docs/superpowers/specs/2026-08-10-baton-design.md) before doing anything.** It is the source of truth for architecture, data model, and design rules.

## How to work in this repo

### Module layout (when implemented)

```
baton/
├── app/                    # Android app (Kotlin + Compose)
│   ├── src/main/...        # UI + capture + sync
│   └── src/test/...        # Unit + UI tests
├── ai/                     # On-device AI module
│   ├── llama/              # llama.cpp JNI wrapper
│   ├── whisper/            # Whisper.cpp JNI wrapper
│   └── ocr/                # ML Kit OCR wrapper
├── data/                   # Persistence + sync
│   ├── db/                 # Room/SQLCipher local DB
│   ├── sync/               # Supabase sync engine
│   └── mcp-client/         # MCP client (for ingest later)
├── features/
│   ├── capture/            # Voice/text/photo capture flow
│   ├── people/             # Person registry + timeline
│   ├── brief/              # Morning brief + evening review
│   ├── nudge/              # AI-drafted nudge flow
│   └── mcp/                # On-device MCP server (optional local)
├── shared/
│   ├── crypto/             # Argon2id + AES-GCM (shared with MindAnchor)
│   ├── ui/                 # Design system, Compose components
│   └── time/               # Time formatting, "carried over" logic
└── server/                 # Supabase project
    ├── functions/          # Edge Functions (brief scheduler, MCP server)
    └── migrations/         # Postgres migrations
```

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
- **Supabase** (Postabase + Auth + Storage + Realtime + Edge Functions) for cloud
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
