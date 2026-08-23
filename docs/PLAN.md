# Baton — Project Plan

> Living document. Updated as decisions land. Owner: Sampath (Amaithi Labs).

## 0. Snapshot — where we are (2026-08-23)

| | |
|---|---|
| Latest release | **v1.9.6** "Drive-verify polish #6" — tagged ~14 min before this doc was drafted |
| Working branch | `m0/skeleton-v1.7.0` (head: `3293f70`, 3 ahead of `v1.9.0-work`) |
| Deploy branch | `v1.9.0-work` (37 commits ahead of `main`) |
| `main` | Stale at v1.6.5 (versionCode 21) — **3 minor versions behind** |
| CI | **Red for 13+ consecutive runs** on every branch (Baton Android CI) |
| Tests | 72 test files in `app/src/test/`, target 525 unit tests passing |
| Kotlin sources | 223 files in a single `:app` module |
| Releases shipped | 16+ since 2026-08-14 (v1.4.3 → v1.9.6 in 9 days) |
| Public status | Private repo, TBD license, no public release yet |
| Authoring | "Amaithi Labs" (recent) + "Sampath" (older) — both `sampathmannam@gmail.com` |

The codebase is **shipping pace of a startup, hygiene of a one-person R&D project**. That's fine for now — the plan is to harden the parts that will hurt the most when v2.0 / public release arrives.

---

## 1. P0 — Stop the bleeding (this week)

These are the things that will compound into real damage if left another week.

### 1.1 Fix the red CI
- Pull the **last failing run log** (`gh run view <id> --repo sampathmannam/baton --log-failed`) and read it.
- Goal: get the `Baton Android CI` workflow green on the **default branch** at least.
- Add a **status badge** to README so the next red build is immediately visible.
- **Definition of done:** 3 consecutive green runs on `main` *and* `v1.9.0-work`.

### 1.2 Sync `main` to current shipping code
- `main` is at v1.6.5. Reality is v1.9.6. New clones, contributors, and CI all see the wrong version.
- Options:
  - **a) Fast-forward `main`** to `v1.9.0-work` (or `m0/skeleton-v1.7.0`).
  - **b) Re-cut history** (squash-merge `v1.9.0-work` → `main`).
  - **c) Adopt a different default branch** (e.g. `m0/skeleton-v1.7.0` becomes `main`, old `main` retires).
- **Recommendation: (c)** — the actual development IS on `m0/skeleton-v1.7.0`. AGENTS.md already says "trunk-based, single main." Make that true.
- Update GitHub default branch setting, archive `m0/skeleton-v1.7.0`, and make `main` track the real history.

### 1.3 Rewrite the README
- Current README says **"Status: Pre-implementation. Design is final."** — this is wrong.
- Reality: v1.9.6 is shipping signed APKs to (a private) distribution.
- The README should reflect: target user, what it does, the 7 design principles, current state, how to build, threat model link.
- Link the **threat model** (`docs/privacy/threat-model.md` if you write one — see §4.2) and the **design doc** as the source of truth.

### 1.4 Update AGENTS.md to match reality
- AGENTS.md describes a 5-module layout (`ai/`, `data/`, `features/`, `shared/`, `server/`) that **doesn't exist**. Only `:app` is real.
- Either (a) **edit AGENTS.md to describe the current flat layout** and treat the multi-module split as v2.0 work, or (b) **start the refactor** (§3.1).
- **Recommendation: (a) for now.** Trunk-based rule is good, but the false promise of multi-module is worse than honestly saying "monolith for v1, modularise at v2."

---

## 2. P1 — Stabilise (next 2 weeks)

### 2.1 Branch strategy: from 17 to 1
- `m0/skeleton-v1.4.*` (8 branches) — audit each: `git log --oneline origin/main..origin/m0/skeleton-v1.4.2-data02`. Anything not merged into v1.9.x? Delete. Anything unique? Save or merge.
- `m0/skeleton-v1.6.6` — predates 1.7.0, almost certainly subsumed. Delete unless `git log` shows unique work.
- After audit, **target: 1 default branch + maybe 1 hotfix branch for the next 2 weeks**.
- Add **branch protection** on `main`: require CI green, no force-push, no direct push (use PRs — even self-PRs for the audit trail).

### 2.2 `.sdd/` cleanup
- `.sdd/` is **121 entries committed** (QA dumps, fix-*.py scripts, csv/json exports, screenshots).
- It looks like a personal scratch dir. Add to `.gitignore`:
  ```
  .sdd/
  ```
- **Before you gitignore it, check for anything worth keeping**:
  - `.sdd/qa-findings.md`, `.sdd/integration-report.md`, `.sdd/progress.md` — are these the design-decision trail? If yes, move to `docs/development/sdd-history/` and keep.
  - `dump-text.py`, `find-tap.py`, `parse-ui.py`, `qa-drive.py` — are these reproducible QA tools? If yes, move to `tools/qa/`.
  - QA dumps (`*.xml`, `*.png`) — ephemeral, never useful long-term, safe to delete.
- After cleanup, **commit a single "chore: remove .sdd scratch artifacts"** with the kept items in their new home.

### 2.3 Release hygiene
- Release notes are great, but **the versionCode / versionName in `app/build.gradle.kts` is still pinned to v1.6.3 (versionCode 21)**. That's how the build still emits old-APK metadata.
- **Make versionCode + versionName driven by the latest tag** — either:
  - Inject from CI at build time (`./gradlew -PversionCode=37 -PversionName=1.9.6`).
  - Or read from a `version.properties` file that's the single source of truth.
  - Or automate via the `gradle-git-version` / `axion-release-plugin` pattern.
- This bug means **the signed v1.9.6 APK was probably built with the wrong versionCode**. Worth a build + `aapt dump badging` to verify.

### 2.4 Adopt a "ready to merge" checklist
Every PR (even self-PRs) should tick:
- [ ] `Baton Android CI` green
- [ ] No new `// TODO` in changed files (or each TODO has an owner + ticket)
- [ ] At least one "finding test" for any new ADHD-UX rule (`features/adhd/AdhdUxFindingTests.kt` is the pattern)
- [ ] No `.sdd/` artifacts staged
- [ ] Release-notes file added if it's a release commit

---

## 3. P2 — Architecture & quality (next 4–6 weeks)

### 3.1 The multi-module split (or: stop promising it)
- **Option A — Split now.** Convert `:app` to `:app` + `:data` + `:features:capture` + `:features:home` + `:features:today` + `:features:settings` + `:shared:crypto` + `:shared:ui` + `:ai:llama` + `:ai:whisper` + `:ai:ocr` + `:server` (Supabase). Lots of work, big payoff in testability, AI-agent reviewability, and "sharable with MindAnchor."
- **Option B — Don't split. Update AGENTS.md.** Stay monolith for v1.x. Do the split as a precondition for v2.0. MindAnchor crypto sharing happens via a **published artifact** (e.g. `com.amaithi:anchor-crypto:1.x.x` from a separate repo or local Maven) until the split.
- **Recommendation: Option B for v1.x, Option A starts at v2.0.0-pre1.** Ship v1.9.7, v1.9.8, v1.9.9 with the monolith, then spend a 2-week refactor window before cutting 2.0.

### 3.2 The MindAnchor crypto question
- AGENTS.md says `app-anchor-crypto` is **shared with MindAnchor**. MindAnchor is `github.com/sampathmannam/MindAnchor` (also Kotlin).
- Right now: I don't see `app-anchor-crypto` as a module in this repo. Where is it?
  - Could be a Git submodule (check `.gitmodules`).
  - Could be a published artifact (check `libs.versions.toml` for a `com.amaithi:anchor-crypto` dep).
  - Could be **copy-pasted** into both repos (common, low-effort, drift risk).
- **Decide and document.** The privacy posture depends on it.

### 3.3 On-device AI hygiene
- v1.6.1 was **"drop LLM completely"**. v1.9.0 brought back **"LLM graceful fallback"** (per commit `4482517f`). The story on AI is unclear.
- AGENTS.md says: **llama.cpp + Qwen 3 1.7B Q4_K_M default**, Phi-4-mini / Gemma 3 4B on flagships. Downloaded at first run, cached locally, **never in repo**.
- Action: write a single **`docs/ai/model-strategy.md`** that says: which models are supported, what triggers fallback, what's the cold-start time, what happens on a low-RAM device. Tests assert the fallback paths.

### 3.4 The 7 design principles → enforced tests
- AGENTS.md lists 7 non-negotiables. There's `AdhdUxFindingTests.kt` for some. **For each principle, there must be a test that fails if the rule is broken.** Audit:
  1. "One next action. No what-do-I-do screens." — test?
  2. "Tabs = 3. Capture one tap away." — `DesignRulesTest.kt` partially?
  3. "Carried over, not overdue. No red badges." — covered?
  4. "Capture < 5s. CI fails on regression." — benchmark test?
  5. "Forgive inconsistency." — testable?
  6. "Energy-aware (MindAnchor)." — needs MCP mock?
  7. "External scaffolding, not rigid." — testable? probably not directly, but review-checklist-able.
- **Target: every principle has at least one automated guard by v2.0.0-pre1.**

### 3.5 MCP server: deploy & test
- v1.9.0 added the **cloud MCP server as a Supabase Edge Function**. The README mentions Claude Desktop integration.
- Action: write **`docs/mcp/server-contract.md`** with the full tool list, auth model, rate limits, and a test harness. Right now it's a black box.

### 3.6 Privacy posture: make the threat model public-ready
- AGENTS.md says **"no third-party AI ever sees it"** and **"no analytics, no telemetry, no crash reporting that sends data off-device."**
- v1.9.0 added an **in-app crash log to cacheDir** + a "Share crash log" row. That's on-policy.
- For Play Store / public release, you need a **public threat model** document. Sketch at `docs/privacy/threat-model.md` covering:
  - What we collect (nothing, by design)
  - What we store locally vs in Supabase
  - What we encrypt (vault mode: Argon2id + AES-GCM)
  - What happens if the user loses their device
  - What happens if Supabase is breached
  - What happens if a third party (e.g. Google Play Services) sees metadata
  - Recovery phrase (BIP39) flow

---

## 4. P2 — Ship & grow (next 4–8 weeks)

### 4.1 v1.9.7: the "honest" release
- **Goal:** prove the new release hygiene works end-to-end.
- Picks up small drive-verify polish #7 from `m0/skeleton-v1.7.0` if there is one.
- First release cut from the **newly-promoted `main` branch** with correct `versionCode` driven from tag.
- Includes the README rewrite + AGENTS.md update.

### 4.2 v1.9.8: privacy doc drop
- Publish the threat model as **`docs/privacy/threat-model.md`** in the repo (private still).
- Add a "Privacy" row in Settings that links to it (offline copy).
- Add a "Privacy Promise" line to the Play Store listing.

### 4.3 v2.0.0-pre1: the multi-module refactor
- 2-week window, **feature freeze** on `:app` while the split happens.
- Goal: `app-anchor-crypto` lives in `:shared:crypto`, fully published to local Maven for MindAnchor to consume.
- CI runs on every new module independently.

### 4.4 v2.0.0: public release candidate
- **License decision** (AGPL? Apache 2.0? Custom "Personal use only"?)
- **Play Store listing** (already drafted per v1.9.0 release notes — review + ship)
- **First public tag.** Optional: a "v2.0.0-public" branch with secrets scrubbed.
- **Marketing surface**: nilamind (3 stars) is your only public repo with traction. Cross-link from Baton release notes.

### 4.5 Post-v2.0: what "success" looks like
- **Adoption:** even one IPS officer using Baton daily, beyond yourself, is the win. Public release lets you find them.
- **Revenue model:** none for v1.x. v2.x could explore: government procurement (Tamil Nadu police is your natural customer base), paid feature for non-government users, or "Baton for Founders" / "Baton for Lawyers" forks.
- **Contributions:** a single outside PR is more valuable than 100 stars in the first month. Set up `CONTRIBUTING.md` once you have at least one external beta user.

---

## 5. Open questions (need a decision from you)

1. **What's the relationship with MindAnchor today?** Is the crypto module copy-pasted, a submodule, or a published artifact? (`git -C baton ls-files | grep -i crypto` and `cat .gitmodules 2>/dev/null` would answer in 10 seconds.)
2. **Is the v1.9.6 APK actually signed with versionCode 37 / versionName 1.9.6?** The `app/build.gradle.kts` on main is pinned to v1.6.3 metadata — suspicious.
3. **Amaithi Labs** — is this a registered entity, a brand, or just a new git author identity? Affects licensing + Play Store publisher account decisions.
4. **Public release scope:** is Baton going public, or is it staying private for personal/IPS use? The plan assumes public-by-v2.0, but if private-only, the privacy-doc work, Play Store listing, and licensing are all optional.
5. **Solo forever, or hire?** The plan is sized for solo. If you plan to add even one collaborator, branch protection + PR reviews + `CONTRIBUTING.md` move from P2 to P0.

---

## 6. What I'll do next (proposed)

If you say "go", in order:

1. **§1.2** — promote `m0/skeleton-v1.7.0` to `main` (or whatever you prefer), update GitHub default branch, archive the old branches.
2. **§1.1** — pull the last CI failure log, fix the red build, get to 3 consecutive greens.
3. **§2.2** — `.sdd/` audit + `.gitignore` + move keepers to `tools/qa/` and `docs/development/`.
4. **§1.3 + §1.4** — README + AGENTS.md rewrite to match v1.9.6 reality.
5. **§2.3** — wire versionCode/versionName to the latest tag.
6. **Then come back to you** for the §5 open questions before any public-release or multi-module work.

Each step ends with a commit on a topic branch + a PR (self-PR if solo) so the audit trail is clean.
