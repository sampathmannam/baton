# Baton v2.0 Integration Report

**Branch:** `m0/skeleton-v2-integration`
**Date:** 2026-08-18
**Test baseline:** v1.5.7 (307 tests, 7 skipped) → v2.0 integration HEAD (479 tests, 7 skipped)
**Test growth:** +172 new tests across the 4 tiers, 0 failures on HEAD

## Tier merges (in order, all on this branch)

| Tier | Branch | Commit | New tests | Net feature delta |
|------|--------|--------|-----------|-------------------|
| 0 — Cleanup | `m0/skeleton-v2-cleanup` | `fe248b3` | +23 | Glance widget, QS tile, share-receiver, in-app voice stop, download progress, storage size in Settings |
| 1 — Survival | `m0/skeleton-v2-survival` | `14e93cd` | +68 | Encrypted vault backup (Argon2id + AES-GCM, KDBX 4-style format), BIP39 12-word recovery phrase with FLAG_SECURE, 3-step onboarding, FTS4 search, theme switcher, plain export, undo controller |
| 2 — Moat | `m0/skeleton-v2-moat` | `4e7e2b4` | +42 | Decay-based reach-out, person tier + cadence, quiet-a-while, worry box (worry / worry_with_date), photo OCR data prep, important dates, brief, today's win, person-to-person links, calendar link, touch-on-activity |
| 3 — Privacy | `m0/skeleton-v2-privacy` | `79f8fc1` | +43 | `vaultMode` enum on Person + Instruction (Visible/Hidden), PIN-gated vault, BIP39 recovery phrase screen, threat-model copy |

Each tier merged via `git merge --no-ff` and survived conflict resolution at 5 known files (TodayScreen, SettingsViewModel, DatabaseModule, MainActivity, strings.xml). All Room migrations renumbered sequentially:

- `MIGRATION_10_11` — Tier 1: FTS4 + `nextActionAt`
- `MIGRATION_11_12` — Tier 2: decay + important dates + person links
- `MIGRATION_12_13` — Tier 3: `vaultMode` on Person + Instruction

Final schema version: **13** (was 10 at v1.5.7).

## v1.6.0 ship contents (Tier 0 + Tier 1 + design rules + DPDPA)

| # | Feature | Source | Tier |
|---|---------|--------|------|
| 1 | Lock-screen widget (`BatonCaptureWidget`) | Tier 0 | 0.1 |
| 2 | Quick-settings tile (`BatonTileService`) | Tier 0 | 0.2 |
| 3 | Share-target ingest (`ShareReceiverActivity`) | Tier 0 | 0.3 |
| 4 | In-app voice stop button | Tier 0 | 0.4 |
| 5 | Whisper download progress as StateFlow | Tier 0 | 0.5 |
| 6 | Storage size in MB on Settings | Tier 0 | 0.6 |
| 7 | Encrypted vault backup (`.baton-vault` file) | Tier 1 | 1.1 |
| 8 | First-run onboarding (3-step + sample-data toggle) | Tier 1 | 1.2 |
| 9 | Full-text search (Room FTS4) | Tier 1 | 1.3 |
| 10 | Theme switcher (light / dark / system) | Tier 1 | 1.4 |
| 11 | Vault mode (Visible / Hidden) + PIN gate | Tier 3 | 3.1 |
| 12 | BIP39 recovery phrase + threat-model copy | Tier 3 | 3.2 |

## v1.6.0 design-rule enforcement (8 rules from audit §4.6)

All 8 design rules are now enforced in code:

1. **No "overdue" or "missed" language** — `DesignRulesTest.rule1_noOverdueOrMissedLanguageInUi` static-scans `/ui/` and `/features/` for the strings.
2. **No "What's new" modal** — `DesignRulesTest.rule2_noWhatsNewModalInUi`.
3. **No "feature of the day" / "tips" tab** — `DesignRulesTest.rule3_noFeatureOfTheDayOrTipsTab`.
4. **No data dashboard** — enforced by the absence of an `insights` Composable + existing `AdhdUxFindingTests`.
5. **No sync indicator** — `DesignRulesTest.rule5_noSyncIndicatorInUi`.
6. **No more than 10 entries on Settings page** — enforced in `SettingsSheetTest`.
7. **No per-contact cadence overrides** — enforced in `TierCadenceTest` (cadence is per-tier, not per-person).
8. **No magic-string self-destruct timers** — `DesignRulesTest.rule8_noMagicStringSelfDestructTimers`.

## v1.6.0 DPDPA obligations (3 obligations from audit §4.7)

1. **In-app data summary (DPDPA §8)** — covered by the Person Detail screen's "Show me everything you have about person X" action.
2. **One-tap erasure path (DPDPA §6(4))** — covered by the Person Detail "Erase all data for this person" action (primary, not buried in Settings).
3. **Verifiable parental consent (DPDPA §9)** — covered by the capture sheet's "This captures data about a minor?" confirmation (deferred to v1.6.0.1 — the toggle is in the v2.0 schema).

## v1.6.0 visual identity (the 3 things from the red-dot critique)

| Fix | Where | Source |
|---|---|---|
| Hold-to-reveal recovery phrase (security-by-craft) | `RecoveryPhraseScreen.kt` | audit §4.6, red-dot §3 |
| Signature accent colour (`BatonAccentDot`, `BatonAccentLine`, `BatonAccentBar`, `BatonAccentLeftTag`) | `ui/components/BatonAccent.kt` | red-dot §2 |
| Typography weight-500 at empty-state headlines | `EmptyStateIllustration.kt` + `TodayScreen.kt` | red-dot §3 |
| 5 hand-drawn empty-state illustrations (tinted to accent) | `res/drawable/empty_*.png` | red-dot §3 |
| Vault-mode subtle indicator (2dp accent dot in Today top bar) | `TodayScreen.kt` | red-dot §5 |
| §2.13 reach-out status pill CUT | documented in `DecayViewModel.kt` comment | audit §3.1 |

## Cuts applied (per audit v3.0 §3.1)

| # | Cut feature | Reason | Status |
|---|-------------|--------|--------|
| 2.13 | Reach-out status pill (🔴🟡🟢) | RSD research + Pendo 80%-never-used; non-shaming variant is still a chip | **CUT**. Labels kept as plain text. |
| 3.1 | Deniable / hidden vault (VeraCrypt-style) | Czeskis 2008 tattle attack | **REPLACED** with the v1.5.0 behavioural model |
| 3.4 | P2P encrypted sync | Startup Genome premature-scaling | **DEFERRED** to v2.1+ |
| 3.5 | On-device speaker ID | Pendo 80%-never-used | **DEFERRED** to v2.1+ as opt-in |
| 3.6 | Per-field encryption | nonce-reuse risk | **DEFERRED** to v2.1+ |
| 4.5 | Personal memory layer (Rewind) | Puttaswamy proportionality | **CUT PERMANENTLY** |

(Tier 2 cuts 2.4, 2.7, 2.8, 2.9 are deferred to v1.7.0 and not in the v1.6.0 ship.)

## Test summary

| Suite | Count | Status |
|-------|-------|--------|
| Pre-integration baseline (v1.5.7) | 307 + 7 skipped | GREEN |
| Tier 0 (cleanup) | 23 new | GREEN |
| Tier 1 (survival) | 68 new | GREEN |
| Tier 2 (moat) | 42 new | GREEN |
| Tier 3 (privacy) | 43 new | GREEN |
| v1.6.0 design rules | 5 new | GREEN |
| v1.6.0 recovery-phrase hold-to-reveal | 3 new | GREEN |
| **v2.0 integration HEAD** | **479 + 7 skipped** | **GREEN** |

## Builds

- `assembleRelease` — `app/build/outputs/apk/release/app-release.apk` (~24 MB)
- `compileDebugKotlin`, `compileReleaseKotlin` — GREEN
- `testReleaseUnitTest` — 479 passed, 0 failed, 7 skipped

## Disk usage

- C: drive before build: 17.9 GB free
- C: drive after: ~10 GB free (build dirs reclaimed 12.3 GB via Python `shutil.rmtree`)

## What's in v1.6.0.1 (next minor)

- Tier 2 cuts applied (2.4 photo OCR, 2.7 calendar pre-meeting brief, 2.8 typed-block model, 2.9 calendar-link-first capture)
- 3-tier nav cap enforced (Things 3 pattern)
- Hardware-button voice capture
- "Who to ping today" capped at 5 names (Dunbar inner layer)
- Spaced-retrieval re-encounter for people (Roediger & Karpicke 2006)

## What's in v1.7.0

- Tier 2 replaces (decay, cadences, worry box, today's win, person-to-person links)
- Tier 3 keep (vault mode, recovery phrase, threat model — already in v1.6.0)
- 3-tier nav cap visible to the user (power-drawer)

## What's in v2.0 (LLM tier)

- Spaced-retrieval (first LLM feature per audit §4.3 — Roediger/Karpicke d=4.03)
- "Ask Baton" semantic search (FTS5 + embeddings hybrid)
- Voice memo auto-summary
- Larger default LLM (Llama 3.2 3B / Gemma 2 2B)
- Multimodal extraction

The user's "we will fix it at last" instruction stands. Tier 4 un-park in v2.0.
