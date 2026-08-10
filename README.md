# Baton

**An ADHD-friendly instruction tracker for IPS officers and other coordination-heavy roles.**

Baton is built for one job: keeping up with what seniors tell you, what you tell subordinates, and what you told yourself you'd do — without dropping the ball, without shame, and without leaking the data.

The name comes from the police baton: a symbol of authority, and the thing you pass from person to person.

## The problem it solves

Current productivity apps fail people with ADHD because they assume:

- you can feel time passing (you can't — time blindness is clinical)
- a red "overdue" badge motivates (it triggers shame and avoidance)
- a 47-item task list is a useful reference (it's cognitive overload)
- you'll remember to open the app (you won't — out of sight, out of mind)
- you'll do the setup ritual and the weekly review (you won't)

A working IPS officer gets instructions from a dozen people, gives instructions to a dozen more, and is in meetings, on calls, and on the move. Baton is built for *that*.

## What it does

- **Single note bar everywhere.** Speak, type, or snap. The on-device LLM decides what kind of instruction it is and extracts the person, designation, station, FIR number, due date, and tags — automatically.
- **People-centric.** The home screen is a list of people (SP, DSP, SHOs, IOs) with a quiet badge showing open items per person. Tap a person → their full timeline.
- **Auto-tagging.** Person, designation, station, FIR number, due date, priority markers — all extracted. Free-form `#tags` preserved. Tags have their own management screen.
- **Layered follow-up.** Morning brief, stale-surfacing dot, AI-drafted nudge messages, evening review. All opt-out-able, none punishing.
- **Multi-device.** Phone, laptop, tablet, all in sync. Built on Supabase.
- **MCP server in the cloud.** Other MCP clients (Claude Desktop, etc.) can read your data and trigger nudges.
- **On-device AI only.** No third-party AI provider ever sees your data. llama.cpp + Qwen 3 1.7B (default) or Phi-4-mini / Gemma 3 4B on flagships.
- **MindAnchor integration.** Opt-in. Baton's nudge frequency adapts to your current energy state.

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

**Pre-implementation.** Design is final. Implementation roadmap in the design doc.

## Stack

- **Android:** Kotlin, Jetpack Compose, Hilt, Room/SQLCipher, WorkManager
- **On-device AI:** llama.cpp (JNI) for LLM, Whisper.cpp (JNI) for STT, ML Kit for OCR
- **Backend:** Supabase (Postgres + Auth + Storage + Realtime + Edge Functions)
- **MCP:** Cloud MCP server deployed as a Supabase Edge Function
- **Shared with MindAnchor:** `app-anchor-crypto` Kotlin module (Argon2id + AES-GCM + SQLCipher setup)

## License

TBD — will follow once the project is ready for a public release. Currently private.
