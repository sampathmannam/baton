# Baton

![Baton app icon](docs/icon-shield-1024.png)

**An ADHD-friendly, **local-only** instruction tracker for IPS officers and other coordination-heavy roles.**

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
- **Local-only by design (v2.0).** All data lives in a SQLCipher-encrypted Room DB on the device. No cloud sync, no remote auth, no analytics. The only network call is the in-app "check for updates" against the public GitHub Releases API (no auth, no PII). See [`docs/threat-model.md`](docs/threat-model.md) for the full threat model.
- **On-device AI.** All LLM and STT inference runs on-device (llama.cpp + Whisper.cpp JNI). **ML Kit OCR** is the one third-party SDK; see "What this is NOT" below.
- **Vault mode.** Optional hidden storage for the sensitive subset of your data. The whole app is local-only, but vault-mode rows are also gated behind a 4-6 digit PIN and the hidden list lives in a separate Room table.
- **Backup.** Local export to a SAF-chosen CSV or JSON. No cloud backup; the user owns the bytes.

## What this is NOT (v2.0)

Baton v2.0.0 is deliberately narrow. It is **not**:

- **A multi-device app.** No cloud sync, no shared state between devices. Each device is its own source of truth. The v1.x "phone ↔ laptop ↔ tablet" sync via Supabase is gone.
- **A cloud-backed app.** No remote auth, no account, no email, no Supabase. The device is the principal.
- **A team app.** No shared instructions, no delegation, no @-mentions. Single-officer use only.
- **An analytics product.** No usage telemetry, no funnel events, no A/B test scaffolding. Crash logs stay in `cacheDir/crashes/` and never leave the device unless the user explicitly taps "Report a problem" in Settings.
- **An enterprise-IT app.** No MDM hooks, no remote admin, no policy enforcement, no audit-log shipping. The audit chain is a local append-only table that the officer can review in-app.
- **A free-of-every-third-party app.** ML Kit OCR uses Google Play Services. The threat model documents this and the user can disable OCR in Settings if they need to. The LLM and STT are on-device and do not call out.

If you need any of the above, v1.x is in the [GitHub Releases](../../releases) history. v2.0 is a deliberate narrowing, not a step backward.

## Design principles

These are non-negotiable, applied at the component level:

1. **One next action.** No "what do I do now?" screens. Drill-down only.
2. **Show less, not more.** Tabs = 3. Capture is always one tap away.
3. **"Carried over", never "overdue."** No red badges, no streaks, no shame.
4. **Capture in < 5 seconds.** Measured. CI fails if it regresses.
5. **Forgive inconsistency.** Skip the review for a month → still works, still calm.
6. **Local-first.** No data leaves the device unless the user explicitly exports it.
7. **External scaffolding, not rigid.** Suggestions, not diktats.

See [`docs/superpowers/specs/2026-08-10-baton-design.md`](docs/superpowers/specs/2026-08-10-baton-design.md) for the full design.

## Status

**v2.0.0 — "Local-only by design"** (in progress, August 2026).
Single-officer, single-device, local-only. 490 unit tests pass (`./gradlew :app:testDebugUnitTest`).

## Releases

Every release ships a signed `app-arm64-v8a-release.apk` with a SHA-256 fingerprint and the production keystore (unchanged since v1.9.0). See [GitHub Releases](../../releases) for the full list, starting from v1.4.3.

## Stack

- **Android:** Kotlin, Jetpack Compose, Hilt, Room/SQLCipher, WorkManager
- **On-device AI:** llama.cpp (JNI) for LLM, Whisper.cpp (JNI) for STT, ML Kit for OCR
- **Networking:** Ktor + OkHttp (only used by the in-app "Check for updates" → GitHub Releases API)
- **Local encryption:** SQLCipher (`net.zetetic:sqlcipher-android:4.6.1`), Argon2id + AES-GCM for vault-mode rows, BIP39 recovery phrase
- **Shared with MindAnchor:** `app-anchor-crypto` Kotlin module (Argon2id + AES-GCM + SQLCipher setup)

## Build

```bash
# 1. (Optional) Copy the local.properties template — only needed for
#    the SHA-256 keystore fingerprint and the Google Maps API key.
#    v2.0 has no Supabase config; the local.properties is empty
#    by default.
cp local.properties.example local.properties
# 2. Build a debug APK
./gradlew :app:assembleDebug
# 3. Run unit tests
./gradlew :app:testDebugUnitTest
# 4. Lint
./gradlew :app:lintDebug
```

Full test suite + lint + assemble is what CI runs on every push. See [`.github/workflows/build.yml`](.github/workflows/build.yml).

## Repo layout

This is a single-module Android project (`:app`). The multi-module split described in early drafts of `AGENTS.md` is a **v2.0 plan** — see [`docs/PRODUCTION_READINESS_PLAN.md`](docs/PRODUCTION_READINESS_PLAN.md) §3.1.

```
baton/
├── app/                          # The whole app (Kotlin + Compose)
│   ├── src/main/java/com/baton/app/
│   │   ├── ui/                   # home, today, settings, privacy, components, theme
│   │   ├── features/             # capture, theme, onboarding, vault, adhd
│   │   ├── data/                 # local (Room/SQLCipher), vault, export
│   │   ├── di/                   # Hilt modules, migrations
│   │   ├── qa/                   # in-app QA hooks
│   │   └── integration/          # cross-feature tests
│   └── src/test/                 # 80+ test files
├── docs/
│   ├── superpowers/specs/        # Design source-of-truth
│   ├── development/sdd-history/  # Pre-1.0 QA reports, dev diary
│   ├── threat-model.md           # Local-only threat model (v2.0)
│   └── PRODUCTION_READINESS_PLAN.md  # Living project plan
├── tools/
│   ├── qa/                       # Reusable QA scripts (qa-drive.py, etc.)
│   └── synthetic-data/           # Test fixture generators
└── .github/workflows/            # CI: unit-test + lint + assemble
```

## Project docs

- [`docs/PRODUCTION_READINESS_PLAN.md`](docs/PRODUCTION_READINESS_PLAN.md) — living project plan, priorities, open questions
- [`docs/superpowers/specs/2026-08-10-baton-design.md`](docs/superpowers/specs/2026-08-10-baton-design.md) — design source of truth
- [`AGENTS.md`](AGENTS.md) — guide for AI coding agents working in this repo
- [`docs/threat-model.md`](docs/threat-model.md) — local-only threat model
- [`docs/development/sdd-history/`](docs/development/sdd-history/) — pre-1.0 QA reports + dev diary

## Privacy posture (v2.0)

- All user data stays on the device. SQLCipher-encrypted Room DB at `filesDir/databases/baton.db`.
- The only outbound network call is the in-app "Check for updates" → `api.github.com/repos/sampathmannam/baton/releases`. No auth, no PII, no analytics cookies.
- No third-party AI provider ever sees your data. LLM (llama.cpp) and STT (Whisper.cpp) run on-device. ML Kit OCR is a third-party SDK that uses Google Play Services — see [`docs/threat-model.md`](docs/threat-model.md) §8.2 for the precise surface and the user-facing toggle.
- No analytics, no telemetry, no crash reporting that sends data off-device. In-app crash log stays in `cacheDir/crashes/`.
- Threat model: [`docs/threat-model.md`](docs/threat-model.md).

## License

TBD — will follow once the project is ready for a public release. Currently private.

## Related projects

- **MindAnchor** (`github.com/sampathmannam/MindAnchor`) — shares the `app-anchor-crypto` Kotlin module. v2.0 integration is suspended (no cloud to ship cross-app state through); the on-device vault crypto is still shared.
- **CCA / Kaavalan** (`github.com/sampathmannam/cca`, `kaavalan-mobile-forensics`) — separate projects for crime analytics and mobile forensics. Not integrated with Baton in v1 or v2.
