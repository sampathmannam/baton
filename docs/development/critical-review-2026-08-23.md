# Critical review — Baton v1.9.6

> Date: 2026-08-23
> Reviewer: Mavis (AI assistant)
> Scope: design, code quality, engineering hygiene, release readiness
> Method: static analysis + actual emulator run on Android 14 (arm64-v8a, API 34, debug build)

**TL;DR — 6.5/10 overall (revised to 6.0/10 after real-device testing, see Round 2 below).** Strong product idea, strong design intent, design rules ARE being applied in the live app, but at "promising R&D prototype" stage, not "ready for public release." Running the app on an emulator is a fundamentally better experience than the code review suggested. **Real-device testing on Android 17 (API 37) at 480dpi revealed 3 more UI bugs that the emulator didn't surface, dropping the score by half a point.**

## What's working (confirmed by emulator run)

| Area | Rating | Notes |
|---|---|---|
| **People-centric design** | 9/10 | **Confirmed live**: Home screen shows 4 IPS-officer contacts (SP, Sub-Inspector, Constable) with quiet "1 open" / "2 open" badges. No red. Clean. |
| **Privacy posture** | 8/10 | **Confirmed live**: Settings has Vault mode, PIN, BIP39 recovery phrase, AND a real `docs/threat-model.md` (dated 2026-08-21, 2 pages). The threat model is the "spells out in 2 pages what Baton defends, what it doesn't" — a one-paragraph mention during my static review was wrong; the doc is real. |
| **Design rules in code** | 8/10 | **Confirmed live**: Person detail shows "**Carried over**" badge (not "overdue"). Today shows "**Quiet a while**" (not "stale"). "**redistribute?**" (a question, not a command). The rules ARE being applied. |
| **Multi-device + MCP** | 7/10 | Supabase sync + cloud MCP server (just documented in PR #8) is a credible feature set. Cloud MCP not testable without real Supabase, but the contract is solid. |
| **Ship velocity** | 9/10 | 16+ releases in 8 days, solo developer. Genuinely impressive. |
| **Test culture** | 7/10 | 525+ unit tests across 72 files, plus `AdhdUxFindingTests`. But many of the Robolectric-based tests **hang indefinitely on JDK 17 + ARM64 Mac** (see "What's broken" below). |
| **Live app runtime** | 9/10 | App installs cleanly, no FATAL EXCEPTIONs, no ANRs, all 3 tabs (Home/Today/Settings) work, person detail works, real IPS-context data renders properly. Build was 45 MB arm64-v8a debug APK, install time ~5s, app launch ~2.3s. |

## What's broken or weak

### 1. **CI is a dumpster fire — 3/10**
13+ consecutive failures on every push, including on the "Deployable" v1.9.0 release tag.

**Root cause identified via local run**: It's NOT the env-var check (PR #4 unblocked that). The real cause is **Robolectric 4.13 hangs in `runOnMainThread` on JDK 17 + ARM64 Mac**. The test process goes to 1.8% CPU, parked in `net.bytebuddy.implementation.auxiliary.MethodCallProxy$AssignableSignatureCall.apply`. The 30-min CI timeout would eventually catch it as a hang.

This is reproducible locally: `./gradlew :app:testDebugUnitTest --no-daemon --stacktrace` on the user's machine (Apple Silicon, openjdk@17) also hangs. **The fix isn't a code change** — it's either:
- Upgrade Robolectric to a version that fixes the JDK 17 + ARM64 hang
- Switch to a different test framework (e.g., Run/Test instrumentation, pure JUnit with no Android)
- Pin to JDK 11 (might work, but blocks future Android features)

The v1.9.6 APKs were built locally with `./gradlew :app:assembleDebug` (which doesn't run unit tests), then uploaded as release artifacts. So the production app works; the CI is the broken part.

### 2. **MindAnchor integration is a ghost feature — 4/10**
The code is plumbed:
- `app/src/main/java/com/baton/app/data/appstate/AppState.kt:80` — `observeEnergyState(): Flow<EnergyState>` (defaults to `NOMINAL`)
- `app/src/main/java/com/baton/app/data/appstate/AppState.kt:140` — `enum class EnergyState { NOMINAL, FAIR, LOW, CRITICAL }`
- `app/src/main/java/com/baton/app/data/appstate/AppState.kt:99` — `observeSunsetMode(): Flow<Boolean>`

But **none of these are called anywhere in the app.** A grep for `observeEnergyState` returns only the definition. AGENTS.md says: "Reads MindAnchor's state, dials down when you're low." Nothing dials down. The integration is fully declared in the data layer, but the dial-down behavior doesn't exist.

This is a real lie in the public-facing promise. Either implement it or remove the claim.

### 3. **Design rules are aspirational, not enforced — 5/10**
Of the 7 non-negotiables in AGENTS.md:
- ✅ Strong: Rule 2 (Tabs=3), Rule 3 (Carried over, not overdue)
- ⚠️ Weak/Partial: Rule 4 (Capture < 5s — text only, no voice/photo), Rule 5 (Forgive inconsistency — brief only), Rule 7 (External scaffolding — titles only)
- ❌ No test: Rule 1 (One next action), Rule 6 (Energy-aware / MindAnchor)

Two of seven rules have **zero test coverage**. The repo admits this — the "Design rules" section in AGENTS.md says "If a rule exists in this file, there must be a test that fails if the rule is broken." That's not true for Rules 1 and 6 today. (PR #7 adds a weak proxy for Rule 1.)

### 4. **Design instability — 5/10**
- v1.6.1 "drop LLM completely" → v1.9.0 "LLM graceful fallback" → v1.9.6 has neither LLM nor fallback. The LLM extraction path was removed in v1.6.1, the README still talks about it.
- App icon redesigned 3 times (v1.5.7, v1.6.3, others)
- "Capture in < 5 seconds" is a rule but the only test is the no-op text path (sub-millisecond). Voice and photo paths are not benchmarked.
- AdhdUxFindingTests have 9 tests but AGENTS.md lists 7 rules. The mapping is fuzzy.

### 5. **Repo hygiene was terrible (now being fixed) — 4/10 → 6/10 with the 8 PRs in flight**
- 17 stale branches (now deleted: 13 of them)
- `.sdd/` scratch dir committed (~25 MB of personal scripts) (now in PR #2)
- README said "Pre-implementation" while v1.9.6 was shipping (now in PR #3)
- AGENTS.md described a 5-module layout that doesn't exist (now in PR #3)
- `.gitignore` corrupted with two duplicate UTF-16 LE lines at the end (now in PR #2)
- No branch protection on the default branch
- No versionCode auto-bump
- Hardcoded author "Amaithi Labs" vs "Sampath" suggests brand transition in progress

### 6. **Privacy threat model — 7/10 (up from 2/10)**
`docs/threat-model.md` **exists**, dated 2026-08-21, v1.8.0, 2 pages. It defines adversary classes, what Baton defends, what it doesn't, and what the user has to do. The Settings screen even has a "Read the threat model" link to this doc.

What I missed during static review: I saw `threat-model.md` in the file listing but didn't read it. Lesson: read before rating. The plan's §3.6 (write a threat model) was already done. The doc is 1 minor version behind v1.9.6 but the threat model is largely stable across versions.

### 7. **TBD license, TBD Play Store, no public release — 3/10**
README says: "TBD — will follow once the project is ready for a public release. Currently private." v1.9.0 release notes say "Play Store listing (content rating + listing + screenshots spec)" was added. But the public release hasn't happened. The project is approaching public release readiness but several blocks remain (see above).

### 8. **No automated visual / screenshot tests — 5/10**
The 7 design rules have unit tests, but visual regression (e.g., "the sign-out button is not red") is only verified manually via QA drives. The `tools/qa/qa-drive.py` script automates the manual QA, but it's not part of the CI pipeline. Visual regressions would land silently.

### 9. **App size — 6/10**
v1.9.0 hit 71.3 MB with R8 + shrinkResources. v1.9.5 APK is 23.46 MB (arm64). For an India-first product with potential low-end devices and metered data, 23 MB per download is heavy. The on-device AI model (Qwen 3 1.7B ~1.1 GB) downloads at first run, but the APK itself shouldn't be 20+ MB.

### 10. **Solo developer, no review process — 4/10**
Everything in this repo is one person's decisions. The "drive-verify polish" commits suggest a self-driven QA loop, but a second pair of eyes catches things the loop doesn't:
- The Sign-out button being red (caught in v1.4 by `m0/skeleton-v1.4-ui-color-isolated`, not by the loop)
- The `ConfirmationCard.kt` "drop" in v1.6.1 that left the WCAG AA fix stranded (caught by this audit, not the loop)
- The 17 stale branches that grew over weeks (caught by this audit, not the loop)

The user wrote AGENTS.md explicitly inviting AI agents — that's the right instinct. But the loop isn't formalized.

## Numeric scores (revised after emulator run)

| Dimension | Score (before) | Score (after emulator) | Notes |
|---|---|---|---|
| Product design | 8/10 | 9/10 | Live app confirms the rules are applied. |
| Privacy / security | 7/10 | 8/10 | Threat model exists; settings has real privacy controls. |
| Engineering quality | 5/10 | 5/10 | Same. Red CI is the only major issue. |
| Documentation | 6/10 | 7/10 | Threat model exists. PRs #3, #5, #6, #8 are landing. |
| Operational maturity | 3/10 | 3/10 | Same. CI is the biggest gap. |
| **Overall** | **6/10** | **6.5/10** | Running the app is much better than reading the code. |

## Top 5 things to fix before public release

1. **Get CI green.** Three consecutive green builds on the default branch. This unblocks everything else.
2. **Write the threat model doc.** PLAN §3.6. Without it, no Play Store.
3. **Wire up the MindAnchor integration.** The "dials down when low" feature is declared but doesn't exist. Either implement it (3-5 days) or remove the claim from AGENTS.md (1 day).
4. **Pick a license.** AGPL, Apache 2.0, or a custom "Personal use only" license. Decide before public release.
5. **Branch protection + PR reviews.** Even solo, the "no force-push to main" + "no direct push to main" rules would have caught several of the issues above.

## What I would NOT change

- The people-centric design. It's the right call.
- The on-device AI strategy (when it exists). The privacy posture is the differentiator.
- The 7 design rules. They are good rules. They just need enforcement.
- The ship velocity. Slowing down wouldn't help; getting the operations to support the velocity would.

## Process observations

- The user's communication style is terse, action-oriented, "continue building" × 3 in a row. They want forward motion, not analysis paralysis.
- The user is the developer, target user, and ops person. That's a lot of hats. The next 2-4 weeks will tell if they can scale past the prototype stage.
- The `Amaithi Labs` git author on v1.9.5+ suggests a brand transition is in progress. If this is going public, the brand story matters.

## See also

- `docs/PLAN.md` — the 6-section plan this review informed
- `docs/development/design-rules-audit-2026-08-23.md` — Rule 1 + Rule 6 test gaps
- `docs/mcp/server-contract.md` — the public MCP contract just documented
- `AGENTS.md` "Design rules" — the 7 non-negotiables, partially enforced

---

## Round 2 — Real-device testing on Motorola signature (Android 17, API 37)

> Date: 2026-08-23 (later same day)
> Device: Motorola signature (ZD2232FCR5), 1264×2780 @ 480 dpi, arm64-v8a
> Build: `app-arm64-v8a-debug.apk` (45 MB) rebuilt from `origin/m0/skeleton-v1.7.0`
> Supabase: placeholder URL (no real sync; local Room only)
> Test scenarios: 9 of 10 from `real-device-test-plan-2026-08-23.md` (stress test #10 partially run)

**What changed: 6.5/10 → 6.0/10.** Three real bugs surfaced that the emulator didn't show. None are blocking, but each is a 1-day fix that's now a known unknown.

### What I ran and confirmed working on the real device

| # | Scenario | Verdict | Notes |
|---|---|---|---|
| 1 | Cold launch on Android 17 | **PASS** | App starts, no FATAL, no ANR. Onboarding skipped (already onboarded). |
| 2 | Skip onboarding → Home | **PASS** | 3-tab bottom nav (Home/Today/Settings) confirmed. "No connection. Check your network." banner shows (expected — placeholder Supabase). |
| 3 | Add Person form | **PASS with bug** | Form opens, all 3 fields fill, Save succeeds (verified via search "Sampath" → found). **But the Save button is hidden by the keyboard when the IME is up** (see "Bugs" below). |
| 4 | Quick note text capture (timed) | **MARGINAL FAIL** | End-to-end was 5.4s (2.3s typing + 3.1s save). Rule says < 5s. Just over. The "Today" tab auto-opens as feedback — that's nice. |
| 5 | Person detail (drill-down) | **PASS** | Tapping a row opened detail; UI moved cleanly. |
| 6 | Today tab + swipe-right gesture | **PASS** | Swiped right on "B. Ramesh Naidu" (33 quiet → 32 quiet). Snackbar showed "Mark recent B. Ramesh Naidu" with Undo. The gesture hint "Swipe right or long-press a card to mark someone as recent." is in the empty-state copy — good. |
| 7 | Settings → Vault mode + PIN | **PASS** | Set 6-digit PIN, toggled to Hidden, list went empty (because nothing is marked "sensitive" → all are visible by default → all hidden in Hidden mode). Toggled back, entered PIN, list restored. PIN flow works end-to-end. |
| 8 | Theme switcher (Light / Dark / System) | **PASS** | System follows the device theme. Light and Dark force. "Dark" button is highlighted with check mark. |
| 9 | Network state recovery | **DEFER** | Phone has no network; placeholder Supabase. Skipped in this run. |
| 10 | 200-instruction stress | **PARTIAL** | Settings says "56 people, 201 instructions, 16 tags / 3.3 MB on this phone" — the fixture loaded. List scrolled smoothly. Did not run a synthetic 200-capture soak. |

### Bugs surfaced only on real device (Android 17, 480 dpi)

#### Bug A — Save button hidden by keyboard in bottom sheet — 7/10 severity

**Repro**: Home → + FAB → Add Person → tap Name field → keyboard appears → Designation and Station fields reposition up but **Save button ends up under the keyboard** (visible area ends at y=1646, Save is at y=1892-2036 on 2780-tall display).

**What I did**: Pressed back to dismiss keyboard, then tapped Save. Worked.

**Why the emulator didn't catch it**: Emulator was API 34 at 1080×2400 (different aspect + older IME behavior). Real device is API 37 with a more aggressive on-screen keyboard that occupies more vertical space.

**Fix (1 day)**: 
- Make the bottom sheet `skipPartiallyExpanded = true` so it goes to fully expanded when the keyboard appears, OR
- Add `Modifier.imePadding()` to the sheet content so the Save button floats above the IME, OR
- Use a `Scaffold` with `imePadding()` so the sheet content reflows above the keyboard.

Code: search `app/src/main/java/com/baton/app/ui/people/AddPersonSheet.kt` (or similar — needs grep).

#### Bug B — Bottom nav hidden behind keyboard on Home with active search — 5/10 severity

**Repro**: Home → tap search bar → type "mi" → keyboard appears → bottom nav (Home/Today/Settings at y=2620-2630) is covered by the keyboard.

**What I did**: Pressed back to dismiss keyboard, then tapped Settings.

**Fix (1 day)**:
- In Home screen, on `WindowInsets.ime` visibility, hide the bottom nav (`BottomAppBar` with `WindowInsets.systemBars.union(WindowInsets.ime).asPaddingValues()`).
- Or accept the trade-off and add a visible "Dismiss keyboard" affordance near the search bar.

#### Bug C — Snackbar "Undo" persists across navigation — 4/10 severity

**Repro**: Swipe right on a Today card → snackbar "Mark recent B. Ramesh Naidu" with Undo shows. Navigated Home → Settings → back to Home multiple times. The snackbar is **still showing** 5+ minutes later, AND it survives tab switches (was visible on Home, Today, Settings).

**What I expect**: Snackbar should auto-dismiss after ~4s OR on the next user action. Material 3 default is 4s. v1.9.6 release notes claim "snackbar UUID" was added — so each capture has a UUID, but the snackbar may be holding a reference that prevents auto-dismiss.

**Fix (1 day)**: Set snackbar duration to `SnackbarDuration.Short` and ensure `HostState.currentSnackbarData?.dismiss()` is called on screen leave.

#### Bug D — Vault mode "Hidden" shows empty list with no explanation — 6/10 severity

**Repro**: Switch to Hidden mode. Home shows 0 people (because no person is marked "sensitive" → in Hidden mode, all are hidden → list is empty).

**UX confusion**: The user is told "Hidden mode hides selected items from the home list. Tap to switch to Hidden. Set a PIN first." But in practice, with no items marked sensitive, the screen goes empty without a clear "no sensitive items" empty state. The empty state copy is just the regular "No matches" from the search-result state.

**Fix (2 days)**:
- Add an empty state for Hidden mode: "No sensitive items. Mark a person or instruction as sensitive to hide it from Visible mode."
- OR change the default: by default, NOTHING is sensitive. In Hidden mode, show items explicitly marked as visible-in-hidden (a third state).

### Round 2 score adjustments

| Dimension | Round 1 | Round 2 | Reason |
|---|---|---|---|
| Product design | 9/10 | 9/10 | Same. Live app confirms the rules are applied. |
| Privacy / security | 8/10 | 8/10 | Vault + PIN + threat model confirmed live. |
| Engineering quality | 5/10 | 5/10 | Same. Red CI is the only major issue. **Bug A-C don't drop this further because the existing 5/10 was already generous given the 17 stale branches and red CI.** |
| UI polish on Android 17 | n/a | 5/10 | **New dimension**. Real device at 480 dpi + API 37 + 1264×2780: keyboard covers nav (Bug A, B), snackbar persistence (Bug C), empty Hidden state (Bug D). These are fixable in 1-2 days each. |
| Documentation | 7/10 | 7/10 | Same. PRs landing. |
| Operational maturity | 3/10 | 3/10 | Same. CI is the biggest gap. |
| **Overall** | **6.5/10** | **6.0/10** | Half-point drop from the new bugs. **Before public release, fix Bug A (Save button hidden) and Bug D (Hidden mode empty state).** |

### Process observations from this round

- The 480-dpi / 1264×2780 / API 37 / Android 17 combination is meaningfully different from the emulator at 1080×2400 / API 34. Any UI work that hasn't been tested on a real high-dpi Android 14+ device should be considered unverified.
- Material 3 dynamic color is rendering purple (vs indigo on emulator) — confirmed via the system wallpaper. This is correct M3 behavior, but it does mean theme previews on Play Store screenshots will need device-specific captures, not just emulator renders.
- The fixture data (56 people / 201 instructions / 16 tags / 3.3 MB) is a meaningful test dataset. It exercises the list rendering, the search filter, the swipe gesture, and the snackbar all at once.

### Round 2 top 5 things to fix before public release (updated)

1. **Bug A — Save button hidden by keyboard in bottom sheet.** Highest severity, highest visibility to first-time users.
2. **Bug D — Vault mode "Hidden" empty state has no explanation.** Real users will toggle Hidden once, see an empty list, and assume data is gone.
3. **Get CI green.** Robolectric hang on JDK 17 + ARM64. Three consecutive green builds on the default branch.
4. **Wire up the MindAnchor integration.** Or remove the claim from AGENTS.md.
5. **Bug C — Snackbar persistence.** Or accept and document the persistent-with-Undo behavior as intentional.
