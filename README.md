# Baton

![Baton app icon](docs/icon-shield-1024.png)

**An ADHD-friendly instruction tracker for IPS officers and other coordination-heavy roles.**

Baton is built for one job: keeping up with what seniors tell you, what you tell subordinates, and what you told yourself you'd do — without dropping the ball, without shame, and without leaking the data.

The name comes from the police baton: a symbol of authority, and the thing you pass from person to person. The app icon is the Tamil word **காவலன்** (Kaavalan — "guardian") reimagined as a shieldmark, in indigo on cream.

## The problem it solves

Current productivity apps fail people with ADHD because they assume:

- you can feel time passing (you can't — time blindness is clinical)
- a red "overdue" badge motivates (it triggers shame and avoidance)
- a 47-item task list is a useful reference (it's cognitive overload)
- you'll remember to open the app (you won't — out of sight, out of mind)
- you'll do the setup ritual and the weekly review (you won't)

A working IPS officer gets instructions from a dozen people, gives instructions to a dozen more, and is in meetings, on calls, and on the move. Baton is built for *that*.

## What it does

- **Single note bar everywhere.** Speak, type, or snap. The on-device LLM (when enabled) decides what kind of instruction it is and extracts the person, designation, station, FIR number, due date, and tags — automatically. If the LLM isn't available, capture still works as plain text.
- **People-centric.** The home screen is a list of people (SP, DSP, SHOs, IOs) with a quiet badge showing open items per person. Tap a person → their full timeline.
- **Auto-tagging.** Person, designation, station, FIR number, due date, priority markers — all extracted. Free-form `#tags` preserved. Tags have their own management screen.
- **Layered follow-up.** Morning brief, stale-surfacing dot, AI-drafted nudge messages, evening review. All opt-out-able, none punishing.
- **Multi-device.** Phone, laptop, tablet, all in sync. Built on Supabase.
- **MCP server in the cloud.** Other MCP clients (Claude Desktop, etc.) can read your data and trigger nudges.
- **On-device AI only.** No third-party AI provider ever sees your data. llama.cpp + Qwen 3 1.7B (default) or Phi-4-mini / Gemma 3 4B on flagships.
- **MindAnchor integration.** Opt-in. Baton's nudge frequency adapts to your current energy state.
- **Vault mode.** Local-only operation with Argon2id + AES-GCM encryption. No Supabase sync, no leakage. Recovery via BIP39 phrase.

## Design principles

These are non-negotiable, applied at the component level:

1. **One next action.** No "what do I do now?" screens. Drill-down only.
2. **Show less, not more.** Tabs = 3. Capture is always one tap away.
3. **"Carried over", never "overdue."** No red badges, no streaks, no shame.
4. **Capture in < 5 seconds.** Measured. CI fails if it regresses.
5. **Forgive inconsistency.** Skip the review for a month → still works, still calm.
6. **Energy-aware.** Reads MindAnchor's state, dials down when you're low.
7. **External scaffolding, not rigid.** Suggestions, not diktats.

See [`docs/superpowers/specs/2026-08-10-baton-design.md`](docs/superpowers/specs/2026-08-10-baton-design.md) for the full design.

## Status

**v1.9.6 — "Drive-verify polish #6"** (latest, 2026-08-22).
Production-ready: signed APKs ship with each release, in-app crash log, in-app update channel, Drive backup, widget gallery, a11y audit, threat model. 525+ unit tests target (currently failing CI — see [open issues](../../issues)).

## Releases

Every release ships a signed `app-arm64-v8a-release.apk` with a SHA-256 fingerprint and the production keystore (unchanged since v1.9.0). See [GitHub Releases](../../releases) for the full list, starting from v1.4.3.

## Stack

- **Android:** Kotlin, Jetpack Compose, Hilt, Room/SQLCipher, WorkManager
- **On-device AI:** llama.cpp (JNI) for LLM, Whisper.cpp (JNI) for STT, ML Kit for OCR
- **Backend:** Supabase (Postgres + Auth + Storage + Realtime + Edge Functions)
- **MCP:** Cloud MCP server deployed as a Supabase Edge Function
- **Shared with MindAnchor:** `app-anchor-crypto` Kotlin module (Argon2id + AES-GCM + SQLCipher setup)

## Build

```bash
# 1. Copy the local.properties template
cp local.properties.example local.properties
# 2. Fill in BATON_SUPABASE_URL and BATON_SUPABASE_ANON_KEY from your
#    Supabase dashboard. (Vault mode doesn't need these.)
# 3. Build a debug APK
./gradlew :app:assembleDebug
# 4. Run unit tests
./gradlew :app:testDebugUnitTest
# 5. Lint
./gradlew :app:lintDebug
```

Full test suite + lint + assemble is what CI runs on every push. See [`.github/workflows/android-ci.yml`](.github/workflows/android-ci.yml).

## Repo layout

This is a single-module Android project (`:app`). The multi-module split described in early drafts of `AGENTS.md` is a **v2.0 plan** — see [`docs/PLAN.md`](docs/PLAN.md) §3.1.

```
baton/
├── app/                          # The whole app (Kotlin + Compose)
│   ├── src/main/java/com/baton/app/
│   │   ├── ui/                   # home, today, settings, auth, privacy, components, theme
│   │   ├── features/             # capture, theme, onboarding, vault, adhd
│   │   ├── data/                 # captures, vault, sync
│   │   ├── di/                   # Hilt modules, migrations
│   │   ├── qa/                   # in-app QA hooks
│   │   └── integration/          # cross-feature tests
│   └── src/test/                 # 72 test files
├── docs/
│   ├── superpowers/specs/        # Design source-of-truth
│   ├── development/sdd-history/  # Pre-1.0 QA reports, dev diary
│   ├── privacy/                  # Threat model (coming in v1.9.8)
│   └── PLAN.md                   # Living project plan
├── supabase/                     # Supabase migrations + Edge Functions
├── tools/
│   ├── qa/                       # Reusable QA scripts (qa-drive.py, etc.)
│   └── synthetic-data/           # Test fixture generators
└── .github/workflows/            # CI: unit-test + lint + assemble
```

## Project docs

- [`docs/PLAN.md`](docs/PLAN.md) — living project plan, priorities, open questions
- [`docs/superpowers/specs/2026-08-10-baton-design.md`](docs/superpowers/specs/2026-08-10-baton-design.md) — design source of truth
- [`AGENTS.md`](AGENTS.md) — guide for AI coding agents working in this repo
- [`docs/development/sdd-history/`](docs/development/sdd-history/) — pre-1.0 QA reports + dev diary

## Privacy posture

- No third-party AI ever sees your data. On-device LLM only.
- No analytics, no telemetry, no crash reporting that sends data off-device. In-app crash log stays in `cacheDir/crashes/`.
- Vault mode = local-only with Argon2id + AES-GCM + BIP39 recovery phrase.
- Threat model: coming in v1.9.8 (see `docs/PLAN.md` §3.6).

## License

TBD — will follow once the project is ready for a public release. Currently private.

## Related projects

- **MindAnchor** (`github.com/sampathmannam/MindAnchor`) — shares the `app-anchor-crypto` Kotlin module and provides energy/notification state via MCP.
- **CCA / Kaavalan** (`github.com/sampathmannam/cca`, `kaavalan-mobile-forensics`) — separate projects for crime analytics and mobile forensics. Not integrated with Baton in v1.
