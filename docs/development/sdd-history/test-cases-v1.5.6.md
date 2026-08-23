# Baton v1.5.6 — Comprehensive QA Test Plan

**Date:** 2026-08-17
**Target:** v1.5.6 (next release)
**Baseline:** 291/0/0/7 (v1.5.5)
**Approach:** Design 60+ genuine test cases from real code, execute them, no fabrication

---

## 1. Scope — what we're testing

The app is split into 8 user-visible surfaces and 5 ViewModels:

| Surface | Files | Buttons / Interactive Elements |
|---|---|---|
| Home tab | `HomeScreen.kt`, `HomeViewModel.kt` | FAB, PersonRow tap, NoteBar (text/camera/mic), storage badge |
| AddPerson sheet | `AddPersonSheet.kt` | Name, Designation, Station, Save, Cancel, Back |
| Person detail | `PersonDetailScreen.kt`, `PersonDetailViewModel.kt` | Back, AddInstruction, MarkSensitive, Drop, Reopen, MarkDone, DraftNudge, Instruction rows |
| Today tab | `TodayScreen.kt`, `TodayViewModel.kt` | Review, InstructionCard tap, MarkDone, Drop, Reopen |
| Capture sheet | `CaptureSheet.kt`, `CaptureViewModel.kt` | Text, Extract, Save, Close, NoPeopleCard, ModelNotReadyCard, ConfirmationCard, TagPicker, CalendarSwitch |
| Settings sheet | `SettingsSheet.kt`, `SettingsViewModel.kt` | Tag create, Model download, Erase, Stuck outbox retry |
| NoteBar | `NoteBar.kt` | Text click, Camera click, Mic click |
| AddInstructionForPerson sheet | `PersonDetailScreen.kt` (VAULT-003) | Text, Save, Cancel |
| Nudge sheet | `NudgeSheet.kt` | Tone chips, Text, Copy, Share, Cancel |
| Drop dialog | `PersonDetailScreen.kt` | Reason text, Confirm, Cancel |

---

## 2. Test categories

We cover 8 categories:
1. **F** — Functional / happy path
2. **E** — Edge cases (empty/long/unicode/special chars)
3. **S** — State transitions (mark-done, drop, reopen, sensitive toggle)
4. **P** — Permissions (camera, mic, storage, calendar)
5. **R** — Error recovery (extraction failure, photo failure, voice failure, model download)
6. **N** — Navigation (back stack, deep links, share-receive, tile)
7. **D** — Data persistence (rotation, process death, share-text restore)
8. **A** — Accessibility (contentDescription, onClickLabel, TalkBack flow)

---

## 3. Test cases (60 total)

### F — Functional / happy path (12 cases)

| ID | Test | Surface | Type |
|---|---|---|---|
| F-01 | Open app cold → Home tab shows "Add person" empty state with primary-coloured button | Home | Unit (HomeScreenTest) |
| F-02 | Tap FAB → AddPerson sheet opens with empty Name/Designation/Station | Home | On-device (UI dump) |
| F-03 | Type "Kavitha" in Name → Save enabled; tap Save → sheet closes; person appears in Home list | AddPerson + Home | On-device |
| F-04 | Tap a person row → PersonDetail opens with name in top bar, instruction list (empty state) | PersonDetail | On-device |
| F-05 | Tap "Add instruction for {name}" → AddInstructionForPerson sheet opens | PersonDetail | On-device |
| F-06 | Type a note + Save → instruction appears in timeline | PersonDetail | On-device |
| F-07 | Tap Today tab → empty brief ("Nothing on your plate.") | Today | On-device |
| F-08 | Tap NoteBar text → Capture sheet opens | Capture | On-device |
| F-09 | Type "Inspector Kumar should call DSP before 5pm tomorrow" + tap Extract → LLM produces proposal | Capture | On-device (requires model) |
| F-10 | Edit proposal fields (Person/Action/Text) → Save → instruction created | Capture | On-device |
| F-11 | Tap Settings tab → sheet opens with Tags, Models, About, Erase sections | Settings | On-device |
| F-12 | Tap Erase → confirmation dialog → Cancel → sheet still open | Settings | On-device |

### E — Edge cases (10 cases)

| ID | Test | Surface | Type |
|---|---|---|---|
| E-01 | AddPerson: name with leading/trailing whitespace → trimmed before save | AddPerson | Unit (AddPerson logic) |
| E-02 | AddPerson: empty name → Save button disabled (greyed) | AddPerson | On-device |
| E-03 | AddPerson: 200-char name → fits in row (no truncation) | AddPerson | On-device |
| E-04 | AddPerson: Tamil Unicode name (காவலன் நோட்) → saves correctly | AddPerson | On-device |
| E-05 | AddPerson: emoji name (👮‍♀️ Officer) → saves correctly | AddPerson | On-device |
| E-06 | Capture: 2000-char text → text field scrolls, save still works | Capture | On-device |
| E-07 | Capture: text with newlines + tabs → preserves formatting in rawText | Capture | Unit (CaptureViewModel) |
| E-08 | Capture: text with only whitespace ("   ") → canExtract = false | Capture | Unit |
| E-09 | Capture: very long person name in proposal (50+ chars) → field doesn't overflow | Capture | On-device |
| E-10 | TagPicker: free tag with 100-char name → truncated to 40 chars (take(40)) | Capture / Settings | Unit (CaptureViewModel.onAddFreeTag) |

### S — State transitions (10 cases)

| ID | Test | Surface | Type |
|---|---|---|---|
| S-01 | Person: Mark done → instruction status = DONE, person re-opens instruction (Re-open) → status = OPEN | PersonDetail | Unit (PersonDetailViewModel + RoomInstructionRepo) |
| S-02 | Person: Mark done → "Done {time}" line appears under instruction | PersonDetail | On-device |
| S-03 | Person: Drop → reason dialog → empty reason → instruction status = DROPPED, no reason stored | PersonDetail | Unit |
| S-04 | Person: Drop → reason dialog → "duplicate" → reason = "duplicate" | PersonDetail | On-device |
| S-05 | Person: Re-open a DONE instruction → status = OPEN, completedAt cleared | PersonDetail | Unit |
| S-06 | Person: Re-open a DROPPED instruction → status = OPEN, droppedReason cleared | PersonDetail | Unit |
| S-07 | Person: Mark as sensitive → "Stays on this phone, never backed up." + button flips to "Remove sensitive flag" | PersonDetail | On-device |
| S-08 | Person: Remove sensitive flag → button flips back to "Mark as sensitive" + subtitle "Stays on this phone." | PersonDetail | On-device |
| S-09 | Today: Mark done → row disappears from "Needs you today" section | Today | On-device |
| S-10 | Today: Drop → row disappears | Today | On-device |

### P — Permissions (4 cases)

| ID | Test | Surface | Type |
|---|---|---|---|
| P-01 | Camera: first install (no perm) → tap camera → system perm dialog → deny → inline "Camera permission denied" error on capture sheet | Home + Capture | On-device (revoke perm first) |
| P-02 | Camera: grant perm → tap camera → system camera launches | Home | On-device |
| P-03 | Mic: first install (no perm) → tap mic → system perm dialog → deny → inline "Microphone permission denied" error | Home + Capture | On-device |
| P-04 | Mic: grant perm → tap mic → foreground service starts, notification posts | Home | On-device (verify with `dumpsys notification`) |

### R — Error recovery (5 cases)

| ID | Test | Surface | Type |
|---|---|---|---|
| R-01 | Model not downloaded → open capture sheet → "On-device AI is not downloaded yet" card + Download button | Capture | On-device (fresh install or after erase) |
| R-02 | Tap Download → "Downloading on-device model." + LinearProgressIndicator + percent | Capture | On-device |
| R-03 | Wait for download → "Ready 1223 MB" row in Settings → Models, Extract enabled on capture sheet | Capture + Settings | On-device |
| R-04 | LLM returns null proposal → "No instruction found. Try rephrasing." error in capture sheet, sheet stays open | Capture | Unit (CaptureViewModel) |
| R-05 | Save fails (Room write throws) → "Could not save note. Try again." error, sheet stays open | Capture | Unit (with mock failing repo) |

### N — Navigation (5 cases)

| ID | Test | Surface | Type |
|---|---|---|---|
| N-01 | Open person detail → press back → returns to Home tab, person list visible | PersonDetail | On-device |
| N-02 | Open AddPerson sheet from capture's NoPeopleCard → dismiss capture, add person, capture sheet re-opens with new person | Capture → AddPerson | On-device |
| N-03 | Share text from another app to Baton → capture sheet opens with text pre-filled | ShareReceiver | On-device (use `adb shell am start -a SEND -t text/plain --es android.intent.extra.TEXT "test"`) |
| N-04 | Open capture sheet → tap Close (X) → sheet closes | Capture | On-device |
| N-05 | Press back on capture sheet (IME not visible) → sheet closes | Capture | On-device |

### D — Data persistence (6 cases)

| ID | Test | Surface | Type |
|---|---|---|---|
| D-01 | Type text in capture sheet → swipe down (sheet dismiss) → re-open → text restored | Capture | On-device (F-09 SavedStateHandle) |
| D-02 | Same → press home → kill app from recents → re-open → text restored from SavedStateHandle | Capture | On-device |
| D-03 | Add 3 people + 5 instructions → rotate phone → all data still there | All | On-device |
| D-04 | Add person + instructions → force-stop app → re-open → all data still there (Room persistence) | All | On-device |
| D-05 | Mark instruction as done → restart app → status still DONE | All | On-device |
| D-06 | Mark person as sensitive → restart app → still sensitive | All | On-device |

### A — Accessibility (8 cases)

| ID | Test | Surface | Type |
|---|---|---|---|
| A-01 | Person row: onClickLabel = "Open person" (TalkBack announces action) | Home | Static (AccessibilityContentDescriptionTest) |
| A-02 | Open count badge: contentDescription = "3 open instructions" (or "1 open instruction") | Home | Static |
| A-03 | Stale dot: contentDescription announces "stale" (not just "dot") | Home | Static |
| A-04 | All IconButton / Button in NoteBar have contentDescription | NoteBar | Static |
| A-05 | Confidence chip (High/Medium/Low) has contentDescription per band | Capture | Static |
| A-06 | Today row: onClickLabel = "Open instruction" | Today | Static |
| A-07 | No red color in the app (AdhdUxFindingTests enforcement) | All | Static |
| A-08 | Confidence chip colors hit WCAG AA contrast (F-23 fix verification) | Capture | Unit (ConfirmationCardTest) |

---

## 4. Test execution plan

### Phase 1: Unit tests (offline)
- Add new test cases for edge cases E-01, E-07, E-08, E-10
- Add state-transition tests S-01, S-03, S-05, S-06
- Add error-recovery tests R-04, R-05
- Add a11y static tests A-01..A-07 (some already exist)
- **Target: 300+ passing tests, 0 regressions**

### Phase 2: On-device integration tests (emulator)
- Use the MindAnchorTest AVD (Pixel 6, 1080x2400, Android 14)
- Build debug APK, install, drive with `adb shell input` + uiautomator dump
- Synthetic data: 4-5 people, 6+ instructions, 2+ tags
- Cover F-01..F-12, E-01..E-10, S-01..S-10, P-01..P-04, R-01..R-03, N-01..N-05, D-01..D-06, A-08

### Phase 3: Bugs found → fix → release v1.5.6

---

## 5. Bugs found in v1.5.4/v1.5.5 (already known, for context)

| Bug | Status |
|---|---|
| v1.5.4: Photo button crashes with SecurityException on first install | ✅ Fixed in v1.5.4 |
| v1.5.4: Extract shows "No connection" on first install (legacy SHA check) | ✅ Fixed in v1.5.4 |
| v1.5.4: Qwen 3 1.7B URL 404 | ✅ Fixed (enacimie mirror) |
| v1.5.5: Capture sheet bottom buttons pushed off-screen when both cards visible | ✅ Fixed in v1.5.5 |

---

## 6. What this test plan does NOT cover (out of scope for v1.5.6)

- Cloud sync (vault mode has no cloud)
- Multi-device behaviour (no auth, single device)
- Battery / memory / thermal benchmarking
- Performance under load (1000+ instructions)
- Localization (English only by spec)
- Tablet layouts (only phone layout)
- Tablet / foldable (phone-first)

These are explicitly out of scope per the v1.5.x design and the user's "vault mode only" direction.

---
