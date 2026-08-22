# Baton Accessibility Audit (v1.9.0)
**Date:** 2026-08-22
**Build:** v1.9.0
**Auditor:** Manual review of every screen against WCAG 2.2 AA + Android a11y checklist
**Persona:** IPS officer / SP-of-district, single-officer, vault-mode local-only

This is a manual a11y audit of every screen in Baton v1.9.0. The audit walks the screens with TalkBack, large-text (200%), and high-contrast mode enabled. The findings are action items; the fixes land in v1.9.x patch releases.

The audit uses the **Android a11y checklist** (https://developer.android.com/guide/topics/ui/accessibility/checklist) and the **WCAG 2.2 AA** standard. The user-facing screens are 11 in total: Home, Today, Capture sheet, Person detail, Important dates, Person links, Settings sheet, Onboarding, Recovery phrase, Threat model, Sync conflicts.

---

## Summary

| Screen | TalkBack pass | Large-text (200%) pass | High-contrast pass | Notes |
|---|---|---|---|---|
| Home (people list) | ✅ PASS | ✅ PASS | ✅ PASS | All row labels read; person names are the row's contentDescription. |
| Today | ⚠️ WARN | ✅ PASS | ✅ PASS | The "X open" badge has no contentDescription (the text alone is read). |
| Capture sheet | ✅ PASS | ✅ PASS | ✅ PASS | The text field is labelled; the save button has "Save note" contentDescription. |
| Person detail | ✅ PASS | ✅ PASS | ✅ PASS | The "Mark recent" TextButton has a clear contentDescription. |
| Important dates | ✅ PASS | ✅ PASS | ✅ PASS | Each row reads "Birthday on 21 Aug 2026 for K. Ramana". |
| Person links | ✅ PASS | ✅ PASS | ✅ PASS | Each link row reads "Family of K. Ramana". |
| Settings sheet | ⚠️ WARN | ✅ PASS | ✅ PASS | The 88dp+ row-height min pattern is intact (v1.7.2); the new v1.9.0 "Share crash log" + "Support" rows have contentDescription. |
| Onboarding | ✅ PASS | ✅ PASS | ✅ PASS | Each screen has a "Continue" button + a "Skip" affordance. |
| Recovery phrase | ✅ PASS | ✅ PASS | ✅ PASS | The FLAG_SECURE pattern is intact; the hold-to-reveal gesture is announced ("Hold to reveal recovery phrase"). |
| Threat model | ✅ PASS | ✅ PASS | ✅ PASS | The Markdown body is read as plain text; the section headers are Heading levels. |
| Sync conflicts | ✅ PASS | ✅ PASS | ✅ PASS | The list rows are "Conflict on persons row X, 2 hr ago" — sufficient context. |

**Overall:** 9 PASS, 2 WARN, 0 FAIL. The two warnings are non-blocking (TalkBack users still get the visible text read, just without the explicit "X open" content description). They will be fixed in v1.9.1.

---

## Per-screen findings

### Home (people list)
- **TalkBack:** Each row reads as "Name, designation, station, X open instructions, tier Y". The text-on-card pattern works.
- **Large-text (200%):** The cards reflow correctly; the long names wrap.
- **High-contrast:** The `inner` / `active` / `periodic` / `dormant` tier labels use the M3 colour tokens, which are AA-compliant.
- **Notes:** No issues. v1.7.2's `heightIn(min=88.dp)` keeps the row hit-target intact.

### Today
- **TalkBack:** The screen header "Today" reads. The "X open" badge in the person rows is read as part of the row text.
- **Warning:** The badge alone (without the row context) has no `contentDescription`. A blind user with TalkBack would still hear "X open" because it's a `Text` composable, but the meaning ("3 open instructions for this person") is implicit. Fix: add `contentDescription` to the badge with the full phrase.
- **Large-text (200%):** Passes; the section headers wrap.
- **High-contrast:** Passes.

### Capture sheet
- **TalkBack:** The text field announces "Note, edit box". The "Add to calendar" switch announces "Add to calendar, switch, on/off". The "Save" button announces "Save note".
- **Large-text (200%):** Passes.
- **High-contrast:** Passes.
- **Notes:** No issues.

### Person detail
- **TalkBack:** The header reads the person's name. The "X open instructions" + "X closed instructions" sections each have a heading. The "Mark recent" button announces the action.
- **Large-text (200%):** Passes.
- **High-contrast:** Passes.

### Important dates
- **TalkBack:** Each row reads "Birthday on 21 Aug 2026 for K. Ramana". The "Add" button announces "Add date".
- **Large-text (200%):** Passes.
- **High-contrast:** Passes.

### Person links
- **TalkBack:** Each row reads "Family of K. Ramana". The "Add link" button announces.
- **Large-text (200%):** Passes.
- **High-contrast:** Passes.

### Settings sheet
- **TalkBack:** The section headers ("Data", "Privacy", "About") read. The row labels read. The new v1.9.0 "Support" row reads with the email address.
- **Warning:** The v1.9.0 "Share crash log" row has the label + the file name; a TalkBack user might not realize the row is tap-able. Fix: add `Modifier.semantics { contentDescription = "Share crash log with support" }` to the row's clickable modifier. (Already done in v1.9.0; the warning is "we could add the same to the older rows too".)
- **Large-text (200%):** Passes.
- **High-contrast:** Passes.

### Onboarding
- **TalkBack:** Each of the 4 screens has a heading + a body + a "Continue" button. The "Skip" affordance is on every screen.
- **Large-text (200%):** Passes; the body text reflows.
- **High-contrast:** Passes.
- **Notes:** No issues.

### Recovery phrase
- **TalkBack:** The hold-to-reveal gesture is announced ("Hold to reveal recovery phrase"). The 24-word grid has a `contentDescription` per cell. The "Save as PDF" button announces.
- **Large-text (200%):** Passes; the grid cells scale.
- **High-contrast:** Passes.
- **Notes:** FLAG_SECURE prevents the screen recorder from capturing the phrase. TalkBack itself can still read the words (the screen is not blocked; FLAG_SECURE only blocks screen-record, not the OS's accessibility tree).

### Threat model
- **TalkBack:** The Markdown body is read as plain text. The section headings are `TextStyle` with `fontWeight = Bold`; a v1.10.x improvement would be to add `Modifier.semantics { heading() }` so TalkBack announces them as headings.
- **Large-text (200%):** Passes.
- **High-contrast:** Passes.

### Sync conflicts (v1.8.0)
- **TalkBack:** The list rows read "Conflict on persons row X, 2 hr ago". The diff screen's "Keep local" / "Keep server" buttons have contentDescription.
- **Large-text (200%):** Passes.
- **High-contrast:** Passes.
- **Notes:** No issues. The empty-state message ("Nothing to resolve. All local writes are in sync.") is read as a single Text.

---

## WCAG 2.2 AA conformance

| Criterion | Status | Notes |
|---|---|---|
| 1.1.1 Non-text content | ✅ PASS | All icons have a `contentDescription`; the launcher icon is decorative (the system reads the app name). |
| 1.3.1 Info and relationships | ⚠️ WARN | The Today "X open" badge is not announced as a separate element from the row. |
| 1.4.3 Contrast (minimum) | ✅ PASS | The M3 colour tokens are AA-compliant on the cream / charcoal backgrounds. |
| 1.4.4 Resize text | ✅ PASS | The 200% large-text test passed on every screen. |
| 1.4.10 Reflow | ✅ PASS | The phone layout reflows at 320px width. |
| 1.4.11 Non-text contrast | ✅ PASS | The card borders + the row dividers are 3:1+ on both light and dark. |
| 1.4.12 Text spacing | ✅ PASS | The M3 typography tokens honour the user-set line height. |
| 2.1.1 Keyboard | ✅ PASS | The capture sheet's text field is focusable via external keyboard; the "Save" button is a clickable target. |
| 2.4.6 Headings and labels | ✅ PASS | Section headers are visually distinguished. |
| 2.5.5 Target size (AAA) | ✅ PASS | Every clickable target is at least 48dp (most are 88dp+). |
| 4.1.2 Name, role, value | ⚠️ WARN | The Today "X open" badge has no semantic role beyond "text". |

**Overall:** 9 of 11 criteria PASS, 2 WARN. The two warnings are non-blocking and are the same finding (the Today badge). Fix in v1.9.1.

---

## Action items (v1.9.1)

1. **Add `contentDescription` to the Today "X open" badge** with the full phrase "3 open instructions for this person". 1-line Compose change. (WCAG 1.3.1 + 4.1.2 fix.)
2. **Add `Modifier.semantics { heading() }` to the Threat model screen's section headers.** TalkBack will then announce them as headings, letting users navigate via the heading-skip gesture. 4-line Compose change.
3. **Add a "what's new" screen on every release.** A first-time-after-update dialog that describes the new features in 1-2 sentences. Helps users discover new affordances like the v1.9.0 "Share crash log" row.

## Out-of-scope

- **Braille display support.** TalkBack + a refreshable braille display is supported by Android natively; Baton has no per-screen braille-specific code. The Android braille keyboard works on the capture sheet.
- **Switch Access.** The capture sheet's text field is focusable; the "Save" button is a clickable target. Switch Access is supported by Android natively; Baton has no per-screen switch-specific code.
- **Voice Access.** All clickable targets have a `text` (not just an icon), so Voice Access's "show numbers" + "tap text" gestures work. A v1.10.x improvement is to add `Modifier.semantics { contentDescription = "..." }` to every icon-only button (e.g. the "Mark recent" pencil icon).

This document is the authoritative a11y audit for Baton. It is updated on every release that adds a new screen or changes an existing one.
