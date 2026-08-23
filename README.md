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

- **Single note bar everywhere.** Speak, type, or snap a photo. The text lands as-is, time-stamped, in your timeline.
- **People-centric.** The home screen is a list of people (SP, DSP, SHOs, IOs) with a quiet badge showing open items per person. Tap a person → their full timeline.
- **Quiet semantics.** "Carried over", not "overdue". "Quiet a while", not "stale". "Redistribute?", not "delete". A swipe-right on a quiet contact marks them recent.
- **Photo OCR, on-device.** Snap a hand-written note; ML Kit Text Recognition v2 reads it on-device. No cloud AI, no third-party calls.
- **Vault mode.** A PIN-protected hidden list for sensitive items. Behavioural deniability, not cryptographic (see [threat model](docs/threat-model.md) §3.2).
- **Multi-device via Supabase.** Phone + laptop + tablet, end-to-end encrypted in transit. A cloud MCP server exposes the same data to your desktop tools (Claude Desktop, etc.).
- **12-word recovery phrase.** BIP39-style. The only way back in if this phone is lost.
- **No third-party AI ever sees your data.** No analytics, no telemetry, no crash reporting. Local logs only.

## Design principles

These are non-negotiable, applied at the component level:

1. **One next action.** No "what do I do now?" screens. Drill-down only.
2. **Show less, not more.** Tabs = 3. Capture is always one tap away.
3. **"Carried over", never "overdue."** No red badges, no streaks, no shame.
4. **Capture in < 5 seconds.** Measured. CI fails if it regresses.
5. **Forgive inconsistency.** Skip the review for a month → still works, still calm.
6. **Energy-aware.** *(v2.x — the data plumbing is in place but the dial-down UI is not yet wired.)*
7. **External scaffolding, not rigid.** Suggestions, not diktats.

See [`AGENTS.md`](AGENTS.md) for the developer-facing rules and `docs/architecture/ai-strategy.md` for the AI strategy (short version: in v1.9.6 the only AI is ML Kit on-device OCR; the v1.5.x llama.cpp + Whisper.cpp stack was removed in v1.6.1 because the 5-second capture budget couldn't survive cold-start + inference).

## Status

**v1.9.6 — first public release.** Active development on the `m0/skeleton-v1.7.0` branch; 16+ releases shipped in 8 days; 30+ unit + UI tests, 56-person synthetic fixture for manual QA. See [`CHANGELOG.md`](CHANGELOG.md) (TODO) for the per-release notes.

## Stack

- **Android:** Kotlin 2.0+ / Jetpack Compose / Hilt / Room + SQLCipher / WorkManager / Material 3
- **On-device AI:** ML Kit Text Recognition v2 (~12 MB bundled, Latin script). That's it. See [`docs/architecture/ai-strategy.md`](docs/architecture/ai-strategy.md) for why.
- **Backend:** Supabase (Postgres + Auth + Storage + Realtime + Edge Functions). Cloud sync is opt-in; local-only is the default.
- **MCP:** Cloud MCP server at `supabase/functions/mcp-server/` (v0.4.0). Public contract in [`docs/mcp/server-contract.md`](docs/mcp/server-contract.md).
- **Privacy / security:** Argon2id + AES-GCM vault mode, SQLCipher-encrypted DB, FLAG_SECURE on the recovery screen, hash-chained audit log. Full operational story in [`docs/threat-model.md`](docs/threat-model.md).

## Building

```bash
# Prereqs: JDK 17, Android SDK with platform-34 + build-tools 34.0.0
./gradlew :app:assembleDebug         # Build debug APK
./gradlew :app:testDebugUnitTest     # Run unit tests
./gradlew :app:lintDebug             # Kotlin lint
./gradlew :app:connectedAndroidTest  # Instrumented tests (requires device)
```

The debug APK ships in `app/build/outputs/apk/debug/app-arm64-v8a-debug.apk` (~45 MB) and `app-universal-debug.apk` (~75 MB). The release APK is minified with R8 + shrinkResources (~23 MB arm64).

## Repo conventions

- **Trunk-based.** Single `main` branch; short-lived feature branches off `m0/skeleton-v1.7.0`; `work/**` is scratch.
- **Commit messages:** imperative, present tense ("Add capture flow", not "Added").
- **PRs:** include what + why + how-to-verify. Use the [PR template](.github/pull_request_template.md).
- **No force-push to `main`.**

## CI

GitHub Actions. The CI is hosted on a self-hosted runner (the developer's Mac); it's "unlimited" in the sense that GitHub's hosted-minute billing doesn't apply. See `.github/workflows/` for the workflows.

## License

[Apache 2.0](LICENSE). Copyright 2026 Sampath Mannam (Amaithi Labs).

Contributions welcome. By submitting a pull request, you agree to license your contribution under the same terms.

## Author

Sampath Mannam — solo developer. The user, the developer, the ops person, the QA. Reach me via kaavalan-note@protonmail.com (the support email in the app's Settings → About).

The brand name on v1.9.5+ is **Amaithi Labs** (Tamil: "platform" / "stage", from அமைதி — calm / composure). The git author on v1.9.5+ is `Amaithi Labs`; the README and the app's settings email use `Sampath Mannam` directly.
