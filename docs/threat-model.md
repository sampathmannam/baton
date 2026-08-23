# Baton Threat Model
**Date:** 2026-08-23
**Build:** v1.9.6 (commit `3293f70` on `m0/skeleton-v1.7.0`)
**Persona:** IPS officer / SP-of-district, single-device, no cloud

A short, operational threat model for Baton. Not an academic exercise — the goal is to spell out, in two pages, what Baton defends, what it doesn't, and what the user has to do for the things in between.

## v1.8.0 → v1.9.6 diff (what changed in this rev)

The threat model is largely stable across v1.8.0 → v1.9.6. The only material change is **defense §3.4 ("no cloud") is now misleading**: the v1.5.0 "sync is dormant" claim is still true for the per-device `sync_queue`, but **v1.9.0 added a cloud MCP server** (`supabase/functions/mcp-server/index.ts`, v0.4.0, public contract in `docs/mcp/server-contract.md`). The MCP server exposes read-only resources and write tools (e.g. `draft_nudge`, `add_capture`) that the user's desktop tools can call.

The threat model has NOT been rewritten to account for the MCP server's attack surface. The user has not run a v1.9.x release against a real Supabase instance (the local build uses `https://placeholder.supabase.co`). The MCP server is documented but not security-audited. See §8 below for the gap.

The v1.9.5 swipe-right gesture on the "Quiet a while" card does not change the threat model.

---

## 1. Adversary classes

| Class | What they have | What they're after |
|------|----|----|
| **A1. Forensics (lost / stolen device)** | Physical possession of the device, hours to weeks of access | The local DB; the recovery phrase; the photo captures |
| **A2. Coercive (officer forced to unlock)** | The user, physically, in front of the device | A "deniable" vault mode; a believable "nothing here" answer |
| **A3. Shoulder-surfer / bystander** | The screen, for a few seconds | A specific instruction text; a person name |
| **A4. Network eavesdropper** | The Wi-Fi / cellular path | Anything that leaves the device (currently nothing — v1.5.0 vault mode) |
| **A5. Malicious app on the same device** | `QUERY_ALL_PACKAGES` or accessibility abuse | Cross-app data leakage |
| **A6. Cloud / server-side attacker** | The Supabase DB (v1.5.0 has none) | n/a — no cloud in v1.5.0+ |

The v1.5.0 vault-mode build eliminates A4 and A6. A1, A2, A3, A5 are the operational threats.

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
v1.5.0 is local-only. There is no periodic sync, no backup to Supabase, no push notification. The pre-v1.5.0 sync code paths are still in the binary (the per-write `enqueueCaptureSync` fires a one-shot worker that no-ops without Supabase credentials) so a future Settings toggle can re-enable cloud without a refactor.

**Threat-model rationale**: the cost of a cloud data breach is a single SQL injection away from leaking the entire database. The benefit (cross-device sync) is small for a single-officer pilot. Local-only is the right trade-off for v1.5.0; a v2.x pilot with 2-5 officers in one station is the trigger for re-enabling cloud.

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

## 4. Known gaps (defended-in-v2.x items)

- **Multi-officer pilot** (Phase 2 in the production-readiness plan): the v1.8.0 build has 1 device = 1 officer. A pilot with 2-5 officers needs the multi-user key sharing (`MultiUserKeySharing`) wired to the actual SQLCipher key derivation. The class is built + tested; the wire-up is a v2.x change.
- **Cryptographic vault mode**: v1.8.0 vault mode is behavioural (UI filter). A v2.x moves hidden rows to a second SQLCipher DB with a second passphrase.
- **Multi-device sync**: v1.8.0 has no sync. v2.x re-enables the dormant sync code path (it still compiles in v1.8.0; the per-write `enqueueCaptureSync` no-ops without Supabase creds).
- **Server-side redaction**: the `RetentionWorker` redacts audit payloads on the device. A cloud-sync v2.x needs the same retention on the server (out of scope for the local-only build).

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
| A4. Network eavesdropper | YES (vacuous) | No network in v1.5.0+ |
| A5. Malicious app on same device | YES | App sandbox + Keystore (no accessibility service) |
| A6. Cloud / server attacker | YES (vacuous) | No cloud in v1.5.0+ |

## 7. v2.x roadmap (defends the gaps)

- Wire `MultiUserKeySharing` to `VaultCrypto` so the SQLCipher key is the unwrapped SMK (not derived from the passphrase). This unlocks the 2-5 officer pilot.
- Move hidden vault-mode rows to a second SQLCipher DB with a second passphrase. This makes vault mode cryptographic, not behavioural.
- Re-enable the dormant sync code path for multi-device (phone + tablet) use. The chain's `signingKey` is a JWT `sub` claim so the server can re-validate.
- Add a third-party security audit before any public release. The audit cost is out of scope for private R&D; the threat-model doc is the audit-ready state.

---

## 8. Known v1.9.6 gaps (added 2026-08-23, before this rev ships as the v1.9.6 threat model)

These items are NOT defended in v1.9.6 and SHOULD be addressed before the first public release:

### 8.1 Cloud MCP server (defends-against gap)
- v1.9.0 added a Supabase-hosted MCP server (`supabase/functions/mcp-server/index.ts`, v0.4.0). The contract is in `docs/mcp/server-contract.md`. The server exposes 7 read-only resources and 5 write tools.
- **Risk**: if the Supabase project is misconfigured (e.g. RLS policies on `persons` and `instructions` are too permissive, or the anon key has more than `read`/`write` to the user's own rows), the MCP server is a data-exfiltration path.
- **Mitigation today**: the v0.4.0 contract explicitly says all tools scope to `auth.uid()`. RLS policies in the Supabase migrations enforce this. **This has not been independently audited.**
- **Action before public release**: third-party security audit of the MCP server's RLS policies and the Edge Function code. Out of scope for private R&D; required for Play Store launch.

### 8.2 Voice capture privacy (the one real v1.x AI risk)
- `android.speech.SpeechRecognizer` is platform-managed. On a Pixel with the Google app installed, audio MAY leave the device and hit Google's cloud recognizer.
- **Action before public release**: surface this in Settings → Privacy. Either with a one-liner "Voice capture uses the system speech recognizer (your device may send audio to Google)" or by replacing the system recognizer with **ML Kit on-device speech** (Android 13+, ~30 MB APK impact).
- See `docs/architecture/ai-strategy.md` §3 for the full analysis.

### 8.3 Recovery phrase downgrade
- v1.8.0 added a 24-word BIP39-style recovery phrase. The hash-chain audit log's `signingKey` is a device-scoped UUID, not a user JWT.
- **Risk**: a device-scoped UUID means a cloud-sync re-validation of the chain cannot prove the user signed the row.
- **Action before public release**: when sync is enabled (v2.x), change the chain's `signingKey` to a server-issued JWT `sub` claim.

### 8.4 "No accessibility service" claim
- v1.8.0 doc claims Baton does not request `BIND_ACCESSIBILITY_SERVICE` and does not include any accessibility-service export. **Verified 2026-08-23**: `AndroidManifest.xml` has no `<service>` element with `BIND_ACCESSIBILITY_SERVICE` permission. Claim holds.

### 8.5 What's NOT changed from v1.8.0
- All v1.8.0 defenses (§3.1 SQLCipher, §3.2 vault mode, §3.3 FLAG_SECURE on recovery, §3.5 hash chain, §3.6 sanitised error messages, §3.7 sandbox) still hold in v1.9.6. The code paths are unchanged.
- The v2.x roadmap (§7) still applies.

---

This document is the authoritative threat model for Baton v1.9.6. A future v2.x pilot or public release MUST update this document as part of the release readiness gate.
