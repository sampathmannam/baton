# Design rules audit — 2026-08-23

Audits the **7 non-negotiable design rules** from `AGENTS.md` against the test suite. Goal: find which rules are enforced by failing tests, and which are just prose.

## TL;DR

| AGENTS.md rule | Test coverage | Verdict |
|---|---|---|
| 1. One next action (no "what do I do now") | **NONE** | ❌ Gap |
| 2. Show less (Tabs=3, capture one tap) | Test #5 + #7 | ✅ Strong |
| 3. "Carried over" never "overdue" (no red) | Test #1, #3, #8 | ✅ Strong |
| 4. Capture < 5 seconds | Test #4 (text only) | ⚠️ Weak |
| 5. Forgive inconsistency (skip review) | Test #9 (30-day gap) | ⚠️ Partial |
| 6. Energy-aware (MindAnchor) | **NONE** — code exists | ❌ Gap |
| 7. External scaffolding, not rigid | Test #8 (titles only) | ⚠️ Weak |

**Two of seven rules have no test at all. Three more are weakly covered.** This is the most important "rule vs test" gap in the project.

## Inventory

- **`app/src/test/java/com/baton/app/features/adhd/AdhdUxFindingTests.kt`** — the explicit "design rules" test suite. **364 lines, 9 tests** (the class header enumerates all 9 in a numbered list).
- **`app/src/test/java/com/baton/app/ui/components/DesignRulesTest.kt`** — secondary design-rules test file. **6 tests** (full content not audited here).
- **Total:** ~15 design-rule tests across the two files. AGENTS.md says "every rule needs a test that fails if the rule is broken" — coverage of the 7 rules is at best 5/7 (rules 1 and 6 are uncovered).

## Per-rule detail

### ✅ Rule 2: Tabs=3, capture one tap

| Test | What it asserts |
|---|---|
| `AdhdUxFindingTests.` `5 only three top-level routes are defined` | `Routes.HOME` + `Routes.TODAY` exist; Settings is a sheet, not a route; `PERSON` is a sub-screen. |
| `AdhdUxFindingTests.` `7 widget is one button - the mic - not a multi-tap launcher` | `BatonCaptureWidget` has exactly one public action (`ACTION_QUICK_CAPTURE`). |

**Strong.** Two tests cover both the in-app navigation and the lock-screen widget. If either breaks, CI fails.

### ✅ Rule 3: "Carried over" never "overdue", no red

| Test | What it asserts |
|---|---|
| `AdhdUxFindingTests.` `1 no red overdue status` | `Status` enum has no `OVERDUE`; has `CARRIED_OVER`. |
| `AdhdUxFindingTests.` `3 carried over is the only silent rollover status` | `BriefGenerator` puts 9-day-stale into `carriedOver`; 45-day-stale is dropped silently. |
| `AdhdUxFindingTests.` `8 brief titles contain no counts` | Brief section titles ("Needs you today", "Waiting on others", "Carried over") don't start with a digit, don't contain "overdue" or "pending". |

**Strong.** Three tests. The recent v1.4 WCAG AA fix (no-red sign-out button, in v1.7.0) is also covered indirectly by `SettingsSheetTest.kt`'s `BUG-AUDIT-2` cases that were cherry-picked and then integrated separately.

### ⚠️ Rule 4: Capture < 5 seconds

| Test | What it asserts |
|---|---|
| `AdhdUxFindingTests.` `4 capture completes in under 5 seconds for the no-op processor path` | 20 plain-text captures complete in <500ms (P95 budget is 5s per call). |

**Weak.** This test only exercises the no-op text path. Real capture paths are:
- **Voice** (`VoiceCaptureService.kt` — 333 lines, `ACTION_START`/`ACTION_STOP` lifecycle)
- **Photo OCR** (`ml-kit-text-recognition` per `libs.versions.toml`)
- **Voice → STT** (`whisper.cpp` per `libs.versions.toml`)

None of these have a "must complete in 5s" benchmark. The 5s budget is the user-perceived capture latency — voice and photo paths could easily exceed it without anyone noticing until a user complains.

**Action:** Add an instrumented test on Firebase Test Lab (or a local benchmark using `androidx.benchmark`) that exercises the voice path end-to-end and asserts <5s p95. Or, simpler: add a benchmark to the build that runs against a real model and fails CI on regression.

### ⚠️ Rule 5: Forgive inconsistency (skip review for a month)

| Test | What it asserts |
|---|---|
| `AdhdUxFindingTests.` `9 30-day gap survival` | Brief generator drops instructions older than 30 days silently from `carriedOver`. |

**Partial.** The test only verifies the brief's 30-day rule, not the "skip the evening review for a month, still calm" guarantee. The evening review flow has its own skip path that's not directly tested. Also: the wording in AGENTS.md is "skip the review for a month → still works, still calm" — this is testable as "after 30 missed evening reviews, opening the app shows the same calm home screen, not a 30-item review pile."

**Action:** Add a test that simulates 30 missed evening reviews and asserts the home screen doesn't surface a review backlog.

### ❌ Rule 6: Energy-aware (MindAnchor integration)

| Test | What it asserts |
|---|---|
| **NONE** | — |

**Gap — bigger than first appears.** The repository is there:
- `app/src/main/java/com/baton/app/data/appstate/AppState.kt:80` — `observeEnergyState(): Flow<EnergyState>` (defaults to `NOMINAL`)
- `app/src/main/java/com/baton/app/data/appstate/AppState.kt:140` — `enum class EnergyState { NOMINAL, FAIR, LOW, CRITICAL }`
- `app/src/main/java/com/baton/app/data/appstate/AppState.kt:99` — `observeSunsetMode(): Flow<Boolean>`

**But none of these are called anywhere in the app.** A grep for `observeEnergyState` returns only the definition (line 80) and the implementation (line 88). No ViewModel, no UI, no test reads it. The energy-state plumbing is **scaffolded but unwired** — the MindAnchor integration is a ghost feature.

`AGENTS.md` says: "Reads MindAnchor's state, dials down when you're low." Today, nothing dials down. Nothing reads the state. The integration is fully declared in the data layer, but the dial-down behaviour doesn't exist anywhere.

**Action (two parts):**
1. **Decide where the energy state should affect UX** — candidates are: morning brief density (fewer items when low), nudge frequency (skip when low), notification batching (group when low), theme (calmer when low). Pick one for v2.0.
2. **Add a fake `MindAnchorStateProvider` interface** so tests can inject `LOW` and assert the dial-down behaviour. Without this, the integration will continue to be unwired in CI.

Note: the data-layer plumbing itself is well-tested-implicitly by the Supabase round-trip tests. The gap is purely the UI wiring.

### ❌ Rule 1: One next action (no "what do I do now" screens)

| Test | What it asserts |
|---|---|
| **NONE** | — |

**Gap.** AGENTS.md says: "No 'what do I do now?' screens. Drill-down only." This is a UX invariant that has no automated guard. It's hard to test directly, but you can test proxies:
- **Today screen never asks "what now?"** — assert the top-most composable on Today is a single drill-down target, not a "choose your next action" picker.
- **Home screen drill-down is at most 2 levels** — `HomeScreen` → `PersonDetailScreen` and no further; assert no deeper NavHost routes from Home.
- **Settings has no "what do you want to do" page** — assert Settings opens directly into a flat list, not a category picker.

**Action:** Add at least one of the proxies above as a test in `DesignRulesTest.kt`.

### ⚠️ Rule 7: External scaffolding, not rigid

| Test | What it asserts |
|---|---|
| `AdhdUxFindingTests.` `8 brief titles contain no counts` | Brief section titles don't include counts (e.g., "12 things overdue"). |

**Weak.** Test #8 covers the brief section labels, but the broader rule ("suggestions, not diktats") applies to:
- **Nudge dismissability** — every nudge has a dismiss/close action
- **Opt-out paths** — every "always-on" behaviour has a settings toggle
- **Brief expansion** — the brief is collapsible; no "you must read all 7 items" pressure

Code search for `onDismiss` shows it's wired for sheets, but there's no test that asserts a specific nudge is dismissable and stays dismissed after dismiss.

**Action:** Add a test that picks a representative nudge (e.g., the "stale" dot on Today), dismisses it, asserts it doesn't reappear on next app open.

## Summary scorecard

| Coverage | Rules | Rules |
|---|---|---|
| ✅ Strong | 2, 3 | 2 of 7 |
| ⚠️ Weak/Partial | 4, 5, 7 | 3 of 7 |
| ❌ No test | 1, 6 | 2 of 7 |

## Recommended follow-ups (in priority order)

1. **Rule 6 (energy-aware)** — add a test. Code exists; missing test is the highest-leverage gap. Use a fake `MindAnchorStateProvider` to inject a `low` energy state and verify Baton's nudge cadence drops.
2. **Rule 4 (capture <5s voice/photo paths)** — add a benchmark. Without it, voice regressions would land silently.
3. **Rule 1 (one next action)** — add a proxy test (e.g., Today top composable is a single drill-down, not a picker).
4. **Rule 5 (forgive inconsistency)** — add the "30 missed evening reviews" simulation test.
5. **Rule 7 (scaffolding not rigid)** — add a nudge-dismissable test.

Each follow-up is a small, focused PR — a single new test method (or two) per rule. Total estimated work: ~4 hours, spread over 5 PRs.

## What this audit did NOT cover

- **Visual regression** — the rules are about behaviour, not pixels. A separate screenshot test would catch colour regressions that the unit tests miss.
- **Performance benchmarks** — Rule 4's 5s budget is an end-to-end user experience, not a JVM test. The unit test gives a lower bound; the real bound requires a device.
- **Privacy posture tests** — the §3.6 threat model will introduce its own test set; this audit only covers the 7 design rules.

See also: `docs/PLAN.md` §3.4 (the original plan for this audit).
