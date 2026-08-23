# Play Store IARC Content Rating — Baton v1.9.0
**Date:** 2026-08-21
**Source questionnaire:** IARC (International Age Rating Coalition)
**Build:** v1.9.0

This is the filled-in IARC questionnaire for the Baton ("Kaavalan note") app submission to the Play Store. The answers are final; the IARC system assigns the rating automatically.

---

## Category: Productivity / Utility
**Verified:** Baton is a private instruction tracker for IPS officers. It is a personal-productivity tool with no user-generated content shared to other users.

## Violence
**Answer:** No
**Rationale:** Baton does not depict, encourage, or facilitate violence.

## Sexual content
**Answer:** No
**Rationale:** Baton does not contain sexual content.

## Language
**Answer:** No
**Rationale:** Baton does not contain profanity, crude humor, or mature language.

## Controlled substances (drugs, alcohol, tobacco)
**Answer:** No
**Rationale:** Baton does not depict, encourage, or facilitate the use of controlled substances.

## Gambling
**Answer:** No
**Rationale:** Baton does not contain gambling or simulated gambling.

## User interaction
**Answer:** No user-to-user interaction
**Rationale:** Baton is a single-user, local-only app. There is no chat, no friend list, no public profile, no user-generated content shared with other users. The v1.5.0+ vault-mode build has no cloud sync at all; a future v2.x pilot with 2-5 officers in one station is the only scenario where user-to-user interaction would happen, and that's a separate (private) build.

## Location sharing
**Answer:** No location sharing
**Rationale:** Baton does not request `ACCESS_FINE_LOCATION` or `ACCESS_COARSE_LOCATION`. The app does not transmit location data to any server.

## Personal information sharing
**Answer:** No personal information is shared with third parties
**Rationale:** Baton collects no data. The full privacy policy is in `docs/privacy-policy.md`. The Play Store listing's "Data safety" section should mirror the privacy policy's "we do not collect any data" answer.

## In-app purchases
**Answer:** No
**Rationale:** Baton is free. There is no premium tier, no ads, no monetization (Phase 3 P0 #7 is deferred per the persona's "no deployment target" rule; the decision is "free, forever").

## Data collection
**Answer:** No data is collected
**Per-category answers (the Play Store "Data safety" questionnaire):**
| Category | Collected? | Shared? | Can user request deletion? |
|----|----|----|----|
| Location | No | No | n/a |
| Personal info (name, email, phone) | No | No | n/a |
| Financial info | No | No | n/a |
| Health and fitness | No | No | n/a |
| Messages | No | No | n/a |
| Photos and videos | No (kept on device) | No | n/a (Settings → Erase all data wipes them) |
| Audio files | No (kept on device) | No | n/a |
| Files and docs | No (kept on device) | No | n/a |
| Calendar | No (events are created on the user's calendar via the system Intent, not collected) | No | n/a |
| Contacts | No | No | n/a |
| App activity (interactions, in-app search) | No | No | n/a |
| Web browsing | No (no in-app browser, no WebView) | No | n/a |
| App info and performance (crash logs, diagnostics) | No (the v1.9.0 in-app crash log is a local file the user can share; it is NOT collected by the app) | No | n/a |
| Device or other IDs | No | No | n/a |

---

## Resulting rating

The IARC system will assign one of: **E** (Everyone), **E10+** (Everyone 10+), **T** (Teen), **M** (Mature 17+), or **AO** (Adults Only 18+).

**Expected rating:** **E** (Everyone).
**Rationale:** All categories are "No" except user-generated content (which is "not shared"). The IARC algorithm maps this to the lowest age band.

## Submission notes

- The rating must be re-verified on every release that changes the questionnaire answers. v1.9.0's changes (Drive backup, crash log, update channel) do NOT change any of the answers above.
- A future v2.x cloud-sync build WILL change the answers (user-generated content shared with the cloud server). The next IARC review at the cloud-sync build is the trigger for a re-rating.
- The Play Store listing's "Data safety" section must mirror this document's "Data collection" table verbatim.

This document is the authoritative IARC questionnaire for Baton. It is updated on every release that changes the answers.
