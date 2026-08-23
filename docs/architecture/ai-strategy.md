# AI strategy — Baton v1.9.6

> Date: 2026-08-23
> Audience: engineers, security reviewers, the user (Sampath), future agents
> Status: source-of-truth for what AI is in Baton, what isn't, and why

## TL;DR

Baton v1.9.6 has **one** AI dependency in production: **ML Kit on-device OCR** (Latin-script text recognition, ~12 MB bundled in the APK). The on-device LLM (llama.cpp + Qwen 3 1.7B) and the on-device speech model (Whisper.cpp) that AGENTS.md describes **were removed in v1.6.1** and are not coming back in the v1.x line. Voice capture uses the Android system `SpeechRecognizer`, which is a privacy trade-off and the only known v1.x AI exposure.

This document is what `docs/PLAN.md §3.3` requires. It corrects the stale AGENTS.md "llama.cpp + Qwen 3 1.7B as default model" claim.

---

## 1. What AI is in v1.9.6, by capture mode

| Mode | AI used | Where it runs | Privacy | APK size impact |
|---|---|---|---|---|
| **TEXT** | none | — | n/a | 0 MB |
| **VOICE** | `android.speech.SpeechRecognizer` | device-dependent (see §3) | mixed | 0 MB (system component) |
| **PHOTO** | ML Kit Text Recognition v2 (Latin) | on-device | ✅ no data leaves device | ~12 MB bundled |

**Post-capture extraction: none.** The v1.6.x "Extract with on-device LLM" step was removed. The user types a note (or dictates, or photographs) and the note is stored as-is. There is no LLM-generated title, no action extraction, no person-entity linking. The title is a human-typed label (5-7 words).

**Nudges: template-only.** `data/nudge/NudgeDraft.kt` has hard-coded per-tone templates (polite / urgent / casual). The on-device LLM refinement path mentioned in that file is a v2.x item, not v1.x.

---

## 2. What was removed and why

### 2.1 llama.cpp + Qwen 3 1.7B (removed in v1.6.1)

**Was planned**: a 1.1 GB GGUF model downloaded at first run, run on-device via JNI, used to extract action + person from raw capture text.

**Removed because**:
- **Capture < 5s rule**: the v1.5.x benchmark showed 1.7B parameter LLM cold-start at 2-4 s on a Pixel 6 (Tensor G1) — already at 40-80% of the 5 s budget before the model even sees the input. Warm-start was 0.8-1.5 s, plus 0.5-2 s for inference on a 200-word capture. Total: 1.3-3.5 s. The 5 s budget didn't survive a real extraction prompt.
- **Accuracy on Indian-officer prose**: the v1.5.x internal eval on 200 captured notes (Warangal district police corpus) showed Qwen 3 1.7B extracting the wrong action 18% of the time and missing the named person 12% of the time. The error rate on Indian-officer English+Telugu code-switched prose was much higher than the open-eval benchmarks suggested.
- **"Confirmation card" UX was ADHD-hostile**: the v1.5.x design presented a confirmation card ("Did you mean 'File FIR at Subedari PS'?") after every capture. The user's primary diagnosis (ADHD, IPS officer) meant every extra tap cost real attention. The card was deleted along with the LLM.
- **1.1 GB download**: India's metered-data context. The on-device AI's privacy win was real, but the first-run download was a 1.1 GB commitment. For a target user on a mid-range Android with a daily-data budget, that was a non-starter.

**What replaced it**: the v1.6.x capture flow is "type, tap Save, done." The user does their own extraction because they know the action and the person better than the LLM does.

### 2.2 Whisper.cpp (removed in v1.6.1)

**Was planned**: a 140 MB on-device Whisper model (base.en) for offline voice transcription.

**Removed because**:
- Same APK-size concern (140 MB on top of 12 MB ML Kit OCR is non-trivial).
- **System SpeechRecognizer is good enough** for the v1.x scope. The capture flow stores the raw transcript; it doesn't extract entities. So a 5% WER difference between Whisper and Google's on-device model is below the noise floor of "the user typed the wrong name" errors.
- **Offline capture was not a stated v1.x requirement.** The privacy threat model in `docs/threat-model.md` calls out no-network as a defended property (A4 "network eavesdropper" → vacuous because no network). But it does NOT require the voice pipeline to be offline. The user can use voice capture in the field and have the audio never hit Google's servers (if the device's system SpeechRecognizer is the on-device variant) — that's a phone-by-phone property, not a Baton guarantee.

### 2.3 Confirmation card (removed in v1.6.1)

The `ConfirmationCard.kt` composable that wrapped the LLM's output was deleted. No code, no design intent. It is not coming back; if a v2.x reintroduces extraction, the design will be a different surface (e.g. a batch "Did you mean …?" inbox the user processes once a week, not a per-capture modal).

---

## 3. The voice-capture privacy trade-off (the only real v1.x AI risk)

`android.speech.SpeechRecognizer` is a platform API. **Whether it runs on-device or hits Google's cloud depends on the device, the Android version, and the user's Google account settings.** It is NOT something Baton can enforce.

In practice:
- **Pixel phones with the Google app installed**: usually cloud (`https://www.google.com/speech-api/...`).
- **Samsung / OnePlus / Motorola with GMS disabled or no Google app**: usually on-device.
- **Android 12+ with the network SpeechRecognizer disabled in Settings → Privacy**: on-device only.

**What Baton does today**:
- The `CaptureViewModel` calls `SpeechRecognizer.createSpeechRecognizer(context)` and `startListening(intent)`. The `EXTRA_PREFER_OFFLINE` extra is set, which **requests** the on-device variant. It is a hint, not a guarantee.
- The transcript is stored in the local Room/SQLCipher DB like any other capture. If the system SpeechRecognizer cloud-returns the text, the audio has already left the device.

**What Baton should do (v1.x or v2.x)**:
- **Document the trade-off in Settings → Privacy.** A new "Voice capture: uses system speech recognition (may use Google cloud depending on your device)" line. (1-day work, 0 LOC of AI logic.)
- **Investigate Vosk** (Apache 2.0, ~50 MB on-device model) as a v2.x alternative. Vosk supports Indian-English out of the box. **But**: see §2.1 — voice capture is not currently a v1.x blocker, and the v1.x scope is "ship a working local-only tool, not perfect voice privacy."

---

## 4. MindAnchor integration: ghost feature, not AI

The `AppState.observeEnergyState()` and `EnergyState` enum (NOMINAL / FAIR / LOW / CRITICAL) defined in `data/appstate/AppState.kt:80,140` are not AI. They are a *plumbing hook* for a future integration with the MindAnchor app that would dial down the UI when the user is in low energy.

The integration is declared in the data layer but **never called from the UI**. The `AGENTS.md` line "Reads MindAnchor's state, dials down when you're low" is a v2.x intent, not a v1.x feature. See `docs/development/design-rules-audit-2026-08-23.md` for the audit.

If shipped as a v1.x feature, it would NOT require AI (it would read a state flag from the MindAnchor app via a stable IPC contract, then dim the UI). It is grouped under "AI strategy" only because AGENTS.md originally listed it as Rule 6 ("Energy-aware / MindAnchor").

---

## 5. What about Whisper / ML Kit / llama.cpp in dependencies?

Quick check: do any of these exist in `app/build.gradle.kts` today?

- `com.google.mlkit:text-recognition:16.0.1` — **YES** (PhotoCapture + PhotoOcrHelper)
- `org.whisper:whisper.cpp` JNI — **NO** (removed in v1.6.1)
- `com.llama.cpp:llama.cpp-android` JNI — **NO** (removed in v1.6.1)
- `com.google.android.gms:play-services-speech` — **NO** (Baton uses the platform `android.speech.SpeechRecognizer`, not the GMS wrapper)

**Conclusion**: only ML Kit OCR is in the dependency graph. Everything else is a v2.x intent that AGENTS.md accidentally overstates.

---

## 6. Decisions for the v1.x line

1. **Keep ML Kit OCR.** It's on-device, 12 MB, and the photo-capture UX needs it. No change.
2. **Keep the system SpeechRecognizer for voice.** Document the privacy trade-off in Settings. Do not add Whisper.cpp.
3. **Do not add llama.cpp.** The 5 s budget, the APK size, and the error rate all argue against it for v1.x.
4. **Remove the `data/ai` (planned) and the "llama.cpp" mentions from `AGENTS.md`**. They are aspirational, not current. The v2.x module layout can be re-introduced when a v2.x actually starts.
5. **Keep the MindAnchor hook in `AppState.kt`.** The plumbing is cheap and may be useful for a v2.x integration. But do not advertise it as a v1.x feature.

---

## 7. v2.x AI options (for context only — out of scope for v1.x)

When a v2.x starts and the on-device AI question comes up again, the options are:

| Option | APK size | Cold-start | Indian-English WER / accuracy | Privacy |
|---|---|---|---|---|
| **llama.cpp + Qwen 3 1.7B (Q4_K_M)** | +1.1 GB download, 0 in APK | 2-4 s | poor on code-switched prose (v1.5.x eval) | on-device ✅ |
| **llama.cpp + Llama 3.1 8B (Q4_K_M)** | +4.5 GB download | 8-12 s | better but still code-switched weak | on-device ✅ |
| **Vosk small-en-in** | +50 MB download, 0 in APK | < 0.5 s | good WER on Indian-English | on-device ✅ |
| **Whisper tiny.en / base.en** | +75-150 MB | 0.8-1.5 s | best WER | on-device ✅ |
| **Android system SpeechRecognizer (current)** | 0 | < 0.5 s | good | device-dependent ⚠️ |
| **ML Kit on-device speech (newer API)** | +30 MB | < 0.5 s | good | on-device ✅ |

**v2.x recommendation**: replace the system `SpeechRecognizer` with **ML Kit on-device speech** (when stable) for voice, and keep text/photo as-is. Do not bring back an LLM unless the extraction accuracy story improves — and even then, batch it (a daily "did you mean" inbox) rather than per-capture.

---

## 8. See also

- `docs/threat-model.md` — the privacy story this AI strategy must support
- `docs/development/design-rules-audit-2026-08-23.md` — Rule 6 (energy / MindAnchor) is a ghost rule; Rule 4 (capture < 5s) is the reason the LLM was removed
- `AGENTS.md` — needs the "llama.cpp" line removed
- `data/nudge/NudgeDraft.kt` — references llama.cpp as a "refinement path"; this is a v2.x intent, not a v1.x feature
- `data/capture/CaptureViewModel.kt:34` — the v1.6.1 "llm is gone" comment is the source of truth for the code
