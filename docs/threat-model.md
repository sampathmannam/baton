# Baton Threat Model
**Date:** 2026-08-24
**Build:** v2.0.0 (drop Supabase)
**Persona:** IPS officer / SP-of-district, single-device, **local-only by design**

A short, operational threat model for Baton. Not an academic exercise — the goal is to spell out, in two pages, what Baton defends, what it doesn't, and what the user has to do for the things in between.

**v2.0.0 is a deliberate narrowing** from the v1.x Supabase-backed design. The cloud is gone. The only outbound network call is the in-app "check for updates" against the public GitHub Releases API (no auth, no PII). Every other component runs on-device. This is the most privacy-respecting build of Baton to date; it is also the most narrow (no multi-device, no team, no shared state). The trade-off is intentional and documented in the README "What this is NOT" section.

---

## 1. Adversary classes

| Class | What they have | What they're after |
|------|----|----|
| **A1. Forensics (lost / stolen device)** | Physical possession of the device, hours to weeks of access | The local DB; the recovery phrase; the photo captures |
| **A2. Coercive (officer forced to unlock)** | The user, physically, in front of the device | A "deniable" vault mode; a believable "nothing here" answer |
| **A3. Shoulder-surfer / bystander** | The screen, for a few seconds | A specific instruction text; a person name |
| **A4. Network eavesdropper** | The Wi-Fi / cellular path | Anything that leaves the device |
| **A5. Malicious app on the same device** | `QUERY_ALL_PACKAGES` or accessibility abuse | Cross-app data leakage |
| **A6. Cloud / server-side attacker** | The Baton server (v2.0.0 has none) | n/a — no cloud in v2.0.0+ |
| **A7. Google Play Services** | An always-on Google SDK on the device | The OCR text from a photo capture, if the user has ML Kit OCR enabled |

The v2.0.0 build eliminates A4 and A6 by removing the cloud. A1, A2, A3, A5, A7 are the operational threats. A7 is the one privacy-relevant new surface: the on-device LLM and STT (llama.cpp + Whisper.cpp) are fully on-device, but ML Kit OCR is a Google SDK that runs on Google Play Services. The user can disable OCR in Settings if A7 is in scope.

## 2. Assets

| Asset | Where it lives | Sensitivity |
|------|----|----|
| Person names (PII) | `persons.name` | High |
| Phone numbers | `persons.phone` | High |
| Case details (raw text) | `instructions.rawText` | High |
| Important dates (birthdays, anniversaries) | `important_date` | Medium |
| Capture photos | `filesDir/captures/` (JPEG) | High |
| Recovery phrase (24 words) | `SecurePreferences.recoveryPhraseHash` (SHA-256 only) | Catastrophic if leaked |
| Audit chain | `audit_chain_events` (append-only, SHA-256 hash chain) | High (chain integrity is the legal value) |
| Sync queue | `sync_queue` (v1.5.0 dormant) | Low (deletable, no user data) |
| App version / branding | `BuildConfig` | Trivial |

## 3. Defenses

### 3.1 SQLCipher encryption (defends A1)
The local DB is opened with a 32-byte passphrase generated on first launch and stored in `EncryptedSharedPreferences` (Keystore-backed AES-256-GCM). The on-disk `baton.db` is unreadable without the Keystore. **Defends A1's passive seizure scenario**: a thief who pulls the SD card and walks away cannot read the DB without the OS unlocking the Keystore.

**Does not defend**: an attacker with the device unlocked (the OS lock is the trust boundary). The user is responsible for a strong device lock (biometric or 6-digit PIN at the OS level — not Baton-level).

### 3.2 Vault mode (defends A2)
v1.5.0 added a "deniable vault" toggle. The on-disk schema still contains every row, but a per-row `vaultMode` flag ("visible" / "hidden") filters the UI. The `vaultMode` column is unauthenticated (an attacker with SQL access can read it). **This is behavioural deniability, NOT cryptographic.** The threat-model screen in Settings spells this out to the user.

**Threat-model rationale**: against a coercive adversary who has the device unlocked for a few minutes, the user can flip vault-mode to "hidden" and the hidden rows disappear from every list / search / detail screen. The hidden rows are still on disk in the SQLCipher-encrypted DB.

**Does not defend**: a sophisticated adversary who runs their own SQLCipher-decrypted dump and looks at the `vaultMode` column directly. A cryptographic vault (e.g. hidden rows in a second SQLCipher DB with a different passphrase) is a v2.x item.

### 3.3 FLAG_SECURE on the recovery screen (defends A3)
The `RecoveryPhraseScreen` sets `FLAG_SECURE` on the activity window, which:
- Hides the screen content in the OS task switcher preview.
- Blocks screenshots and screen recordings.
- Re-enables the flag when the screen is popped.

The 24-word phrase is shown with a hold-to-reveal gesture (1.5 s long-press), not a single tap. A shoulder-surfer has to hold the camera on the screen for 1.5 s, which is a strong behavioural signal.

### 3.4 No cloud (defends A4, A6)
v2.0.0 is local-only. There is no periodic sync, no cloud backup, no push notification, no remote auth, no Supabase. The v1.x sync code paths (data/supabase/*, data/sync/*, data/auth/*, ui/auth/*) are deleted; the per-write `enqueueCaptureSync` is a no-op stub. The v1.x outbox table (`sync_queue`) is kept in the schema for forward-compat with a future optional cloud sync, but no rows are written to it in v2.0.0.

**The only outbound network call in v2.0.0** is the in-app "Check for updates" — a GET to `https://api.github.com/repos/sampathmannam/baton/releases` from the `UpdateChecker`. No auth headers, no PII in the request, no cookies. The response includes a JSON array of release metadata (tag_name, html_url, published_at, body). The `User-Agent` header is `Baton/<version>`. GitHub's standard server logs apply (see GitHub's privacy policy).

**Threat-model rationale**: the cost of a cloud data breach is a single SQL injection away from leaking the entire database. The benefit (cross-device sync, team features) is out of scope for the single-officer v2.0.0. A future v2.x that needs cloud must do a fresh threat-model pass — the v1.x threat model does not transfer.

### 3.5 Hash-chained audit log (defends A1 + A6 against tampering)
v1.8.0 added a SHA-256 hash-chain audit log (`audit_chain_events` table). Every state change appends one row; the row's `thisHash` is `SHA-256(payload || prevHash || signingKey)`. A middle-edit by an offline attacker is detectable on the next chain-walk.

**Threat-model rationale**: the chain's value is its integrity, not its confidentiality. The chain doesn't leak data the attacker doesn't already have; it just proves the rows weren't edited. The BNSS / state IT Act 7-year retention window is enforced by `RetentionWorker` (which REDACTS the payload but PRESERVES the chain, so the chain's value is preserved across the retention window).

**Does not defend**: a sophisticated adversary who edits a row AND recomputes the chain from that point forward. The chain only detects single-row edits; a full chain rewrite is detectable only by the per-row `signingKey` (v1.8.0 = a device-scoped UUID; a future v2.x = a user JWT `sub` claim that the server can re-validate).

### 3.6 Sanitised error messages (defends A1 against the "oracle" attack)
`MultiUserKeySharing.unwrap` does NOT distinguish "wrong passphrase" from "tampered share" in the error message — both surface as `VaultError.MasterKeyUnwrap("passphrase did not unwrap the share for user X")`. The AEAD `BadTagException` detail is caught and re-wrapped, not propagated.

**Threat-model rationale**: an attacker with the encrypted share bytes (e.g. a stolen backup) can mount an offline dictionary attack against the user's passphrase. The "right passphrase" vs "wrong passphrase" timing is constant (AES-GCM is constant-time), but the *error message* can leak — if the error said "bad tag" for one input and "version mismatch" for another, the attacker could stage a downgrade attack. The v1.8.0 message surface is uniform.

### 3.7 App-sandbox isolation (defends A5 against most paths)
Android's per-app sandbox isolates Baton's `filesDir`, `databases/`, and `shared_prefs/` from every other app. A malicious app cannot read the SQLCipher DB or the recovery phrase without root. The `EncryptedSharedPreferences` adds Keystore-backed encryption on top of the sandbox.

**Does not defend**: accessibility-service abuse (a malicious accessibility service can read screen contents in real time). Baton does not request `BIND_ACCESSIBILITY_SERVICE` and does not include any accessibility-service export in the manifest.

## 4. Known gaps (defended-in-future-releases items)

v2.0.0 is a deliberately narrow build. The items below are **out of scope** by design, not bugs:

- **Multi-officer / team use**: 1 device = 1 officer. No shared instructions, no delegation, no @-mentions. A future "Baton Teams" v2.x would need a fresh threat-model pass — the multi-user key sharing primitives exist (`MultiUserKeySharing`) but the cloud + team trust model is not built.
- **Cryptographic vault mode**: v2.0.0 vault mode is behavioural (UI filter on the `vaultMode` column). A future v2.x moves hidden rows to a second SQLCipher DB with a second passphrase. Not on the v2.0.0 roadmap.
- **Multi-device sync**: v2.0.0 has no sync. The `sync_queue` table is in the schema for forward-compat with an optional future re-introduction, but no code writes to it.
- **Server-side redaction**: the `RetentionWorker` redacts audit payloads on the device. If/when cloud sync returns, the same retention logic must run on the server (out of scope for v2.0.0).
- **Third-party security audit**: the v2.0.0 build has not been audited by an external security firm. The threat-model doc is the audit-ready state, but no third-party has signed off. The first public release must include an audit.

## 5. What the user is responsible for

Baton is a tool, not a guarantee. The threat model is only as strong as the weakest link the user controls:

1. **OS-level device lock**. Baton's SQLCipher passphrase is in `EncryptedSharedPreferences`, which is unlocked when the OS unlocks. A 4-digit OS PIN is the user's first line of defence.
2. **Recovery phrase storage**. The 24-word recovery phrase is the only way to recover a forgotten passphrase. The user must write it down (the in-app `Save as PDF` flow is the recommended path) and store the paper somewhere physically safe. A photo of the paper on Google Photos is **not safe** — that's an exfiltration channel.
3. **Backup hygiene**. The daily backup is plaintext JSON in `filesDir/backups/`. The on-disk encryption (SQLCipher) protects it at rest, but the user must not move the file to an unencrypted cloud sync folder.
4. **Vault mode honesty**. The vault-mode toggle is a UX affordance, not a cryptographic guarantee. The user must not rely on it against a sophisticated adversary — only against a coercive one.

## 6. Adversary-class summary

| Adversary | Baton defends? | How? |
|----|----|----|
| A1. Forensics (lost / stolen device) | YES | SQLCipher + EncryptedSharedPreferences + Keystore |
| A2. Coercive (forced unlock) | PARTIAL | Vault mode (behavioural deniability) |
| A3. Shoulder-surfer | YES | FLAG_SECURE on recovery + hold-to-reveal |
| A4. Network eavesdropper | YES (vacuous) | No user data on the wire in v2.0.0. The "check for updates" call carries only the public GitHub Releases JSON, no PII. |
| A5. Malicious app on same device | YES | App sandbox + Keystore (no accessibility service) |
| A6. Cloud / server attacker | YES (vacuous) | No cloud in v2.0.0+ |
| A7. Google Play Services (OCR) | PARTIAL (opt-out) | The user can disable OCR in Settings. When OCR is on, photo captures may be sent to Google Play Services for text recognition. The LLM and STT are on-device and do not call out. |

## 7. §8.2 — Third-party SDK surface (v2.0.0)

The v1.x README claim "no third-party AI ever sees your data" is **not accurate** for v2.0.0. The on-device LLM (llama.cpp) and STT (Whisper.cpp) are fully on-device. The exception is **ML Kit OCR**, which is the one third-party SDK that processes user content (the bytes of a photo capture) and uses Google Play Services to do it. Concretely:

- **ML Kit Text Recognition v2** (com.google.mlkit:text-recognition) — runs on Google Play Services on-device. As of v2.0.0, the "On-device" variant is the default (the "Cloud" variant was never wired). The text-recognition model is downloaded by Play Services on first use and runs locally, but Play Services is a Google-controlled SDK on the device. The user-facing toggle is in Settings → Capture: "Enable OCR" (default ON; the "What's this?" link points here).
- **Google Play Services itself** — every Android device with GMS has it. Baton does not add any new Play Services calls; the OCR is the one baton-initiated use.
- **GitHub Releases API** — the "check for updates" call. No auth, no PII.

The v2.0.0 release notes call this out. The threat model acknowledges that "no third-party AI" was an over-claim in v1.x; v2.0.0 is honest about the ML Kit OCR surface and the user-facing opt-out.

## 8. v2.x+ roadmap

v2.0.0 is intentionally the most narrow build. Future v2.x releases could:

- Wire `MultiUserKeySharing` to `VaultCrypto` so the SQLCipher key is the unwrapped SMK (not derived from the passphrase). This unlocks a future "Baton Teams" v2.x.
- Move hidden vault-mode rows to a second SQLCipher DB with a second passphrase. This makes vault mode cryptographic, not behavioural.
- Optionally re-enable the dormant sync code path for multi-device (phone + tablet) use. The chain's `signingKey` is a JWT `sub` claim so the server can re-validate.
- Add a third-party security audit before any public release. The audit cost is out of scope for private R&D; this threat-model doc is the audit-ready state.

Each of the above requires a fresh threat-model pass; the v2.0.0 model does not automatically transfer.

---

This document is the authoritative threat model for Baton v2.0.0. A future v2.x pilot or public release MUST update this document as part of the release readiness gate.
