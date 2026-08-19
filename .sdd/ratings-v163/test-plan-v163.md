# Baton v1.6.3 — 100 systematic test cases (drive on ZD2232FCR5)

Test target: v1.6.3 release APK on phone ZD2232FCR5 (Android 17, 1264x2780, dark mode by default).
Fixture: 12 persons + 36 instructions (3 worries) + 7 captures + 12 tags, loaded from Settings → Developer → Load test data.

Each test is recorded with: ID, screen, action, expected, actual, severity (CRITICAL/HIGH/MED/LOW/PASS).

Severity rubric:
- CRITICAL: crash, data loss, app unusable
- HIGH: feature broken or visually broken (overlap, hidden content, wrong value)
- MED: visual hierarchy / proportions off; defaults wrong; missing polish
- LOW: microcopy, alignment, edge case

## Test groups

A. Onboarding (T01-T05) — first-run + skip
B. Home (T06-T25) — empty, loaded, search, scroll
C. Today (T26-T40) — empty, loaded, sections
D. Person Detail (T41-T55) — loaded, empty, sheets
E. Add Person Sheet (T56-T60)
F. Capture Sheet (T61-T70) — text, voice, photo, permission
G. Settings Sheet (T71-T85) — all sections
H. Recovery Phrase (T86-T88)
I. Theme switching (T89-T91)
J. Quick note bar (T92-T95)
K. Edge cases (T96-T100) — back press, rotation, network, deep links

## A. Onboarding

| ID | Action | Expected |
|---|---|---|
| T01 | Cold-launch app (fresh install) | Onboarding page 1 ("Kaavalan note / Welcome to Baton") renders with the new shield icon (not lock icon) |
| T02 | Tap "Next" 2x | Onboarding advances through all 3 pages, each with the new shield + cream background |
| T03 | Tap "Skip" from page 1 | Jumps straight to Home empty state (no Onboarding once dismissed) |
| T04 | Re-launch after Skip | Home is the start destination, no onboarding |
| T05 | Verify page 3 → Home | "Get started" or last-page CTA → Home empty state with the new shield on the empty state illustration (or empty) |

## B. Home (empty + loaded + search)

| ID | Action | Expected |
|---|---|---|
| T06 | Home empty (no fixture) | "No one yet" headline + subtitle + "Add person" filled button, all centered, no overlap with Quick note bar |
| T07 | Home empty → tap "Add person" | Add Person Sheet opens with name/designation/station fields |
| T08 | Add person "M. Test User / DSP / Warangal" + save | Returns to Home, new row "M. Test User" appears with subtitle "DSP • Warangal" |
| T09 | Home empty → tap Quick note bar | Capture Sheet opens |
| T10 | Load fixture (12 persons) | All 12 rows render, no overlap with Quick note bar at the bottom, count badges "1,2,3" are subtle text not big pills |
| T11 | Scroll Home list to bottom | Last row "Ramesh (informal)" scrolls into view above Quick note bar (no clipping) |
| T12 | Search "Suresh" | 1 Person ("K. Suresh") + 1 Instruction (with group header "G. Swapna" or similar) — no UUIDs in the result |
| T13 | Search "Land dispute" | Instruction results show full title, body in bodySmall, person name in group header |
| T14 | Search "zzznotfound" | Empty state "No results" with bodyLarge + onSurfaceVariant color |
| T15 | Clear search (X button) | Home list returns |
| T16 | Tap a person row | Person Detail screen opens with the right name and subtitle |
| T17 | Tap FAB (+ button) | Add Person Sheet opens (same as T07) |
| T18 | Long-press a person row (if supported) | Verify long-press does nothing destructive |
| T19 | Verify Quick note bar with 0 open count | Quick note bar is the bottomBar, doesn't fade when list scrolls |
| T20 | Verify count badge position | Count "1,2,3" is at the right edge, labelMedium, onSurfaceVariant — not pink, not a circle |
| T21 | Verify "People" title position | Title is at left with 4dp start inset, not flush against screen edge |
| T22 | Verify no row dividers between persons (Obsidian = spacing only) | No 1px dividers between rows |
| T23 | Verify person subtitle format | "Sub-Inspector (Traffic) • District Traffic Wing, Warangal" — single bullet, no extra spaces |
| T24 | Verify stale amber dot | If any person has no activity for 3+ days, a small amber dot appears next to the name (not a count, not red) |
| T25 | Verify navigation bars don't overlap | Bottom nav is above the system 3-button nav, tapping Today/Settings doesn't fire home gesture |

## C. Today

| ID | Action | Expected |
|---|---|---|
| T26 | Today empty (no fixture) | "Nothing on your plate." + "When instructions come in, they'll show up here." centered below search bar, NOT hidden behind it |
| T27 | Today → Review button (top right) | Evening Review Sheet opens |
| T28 | Today loaded (with fixture) | TodaysWinCard + DecaySection + WorryBoxSection + MeetingBriefCard + "Needs you today" / "Waiting on others" / "Carried over" sections |
| T29 | Today section header "Needs you today" | titleMedium style, Semibold, horizontal padding 16dp, 8dp vertical |
| T30 | Today InstructionCard | Card has full-width click target, title in titleSmall Semibold, body in bodyMedium, time in bodySmall |
| T31 | Today no body when title == rawText | Card shows title + time, no duplicate body line |
| T32 | Tap a Today instruction card | Instruction Detail Sheet opens with title + status pill + body + "Mark done" + "Drop" buttons |
| T33 | Today "Mark done" on an instruction | Card disappears, count badge decrements, instruction moves to done state |
| T34 | Today "Reopen" on a done instruction | Card returns to Needs you today |
| T35 | Today "Drop" on an instruction | Confirmation dialog appears (no naked delete) |
| T36 | Today search "Suresh" | Search results on Today show the same person-name group header (not UUID) |
| T37 | Today review sheet | Shows the date + "Still open" list or "Nothing carried over" |
| T38 | Today worry box | Renders worry instructions with calm tertiary tone, not red |
| T39 | Today decay section | "Haven't touched in N days" with stale dot indicators |
| T40 | Today bottom padding | Content scrolls above Quick note bar / system nav, no clipping |

## D. Person Detail

| ID | Action | Expected |
|---|---|---|
| T41 | Tap a person row from Home | Person Detail screen with the name in titleLarge and the designation+station subtitle |
| T42 | Person Detail "Add instruction for X" button | OutlinedButton (NOT filled), full width, 16dp top padding from the subtitle |
| T43 | Tap "Add instruction for X" | AddInstructionForPersonSheet opens with name in title + caption + text field |
| T44 | AddInstructionForPersonSheet — type 200 chars, save | Returns to Person Detail, new instruction appears in the timeline |
| T45 | Person Detail timeline with instructions | Each InstructionRow is a Card with title, status chip, body, time, action row |
| T46 | InstructionRow "Mark done" | Instruction moves to closed, status chip changes to "Done" |
| T47 | InstructionRow "Draft nudge" (OUTGOING) | NudgeSheet opens with the person name + instruction |
| T48 | InstructionRow "Drop" | DropDialog with title + reason field, requires confirmation |
| T49 | InstructionRow "Mark sensitive" | Confirmation dialog explaining the sensitive flag |
| T50 | Person Detail "Linked people" section | Shows linked people or "No links yet." with a + add button |
| T51 | Person Detail "Important dates" section | Shows dates or "No dates yet." with a + add button |
| T52 | Person Detail back arrow | Returns to Home, no back stack corruption |
| T53 | Person Detail sensitive toggle | "Mark as sensitive" / "Remove sensitive flag" — changes the person's is_sensitive flag |
| T54 | Person Detail empty instructions | "No instructions yet. Tap the note bar to start." centered |
| T55 | Person Detail screen with sensitive person | Shows "Stays on this phone, never backed up." subtitle |

## E. Add Person Sheet

| ID | Action | Expected |
|---|---|---|
| T56 | Tap FAB on Home | AddPersonSheet opens with name/designation/station fields |
| T57 | Empty name + Save | Save is disabled, no crash |
| T58 | Add person with all 3 fields | New person row appears in Home |
| T59 | Cancel Add Person | Returns to Home, no new row |
| T60 | Add person with very long name (50 chars) | Name truncates or wraps gracefully in the row |

## F. Capture Sheet

| ID | Action | Expected |
|---|---|---|
| T61 | Tap Quick note bar (text) | CaptureSheet opens with text field |
| T62 | Type 200 chars in capture | Text fits, sheet resizes correctly |
| T63 | Save capture | Returns to Home, no toast flash (calm save) |
| T64 | Tap camera icon in Quick note | Camera permission flow (skip if granted) |
| T65 | Camera permission denied | "Camera permission denied" friendly message |
| T66 | Camera permission granted → TakePicture | Captures photo, OCR runs, text appears in capture sheet |
| T67 | Tap mic icon in Quick note | Mic permission flow (skip if granted) |
| T68 | Mic permission granted → voice capture | VoiceCaptureService starts, recording indicator |
| T69 | Mic permission denied | "Microphone permission denied" friendly message |
| T70 | Capture sheet close (tap outside) | Sheet dismisses, no draft lost warning (or warning if draft exists) |

## G. Settings Sheet

| ID | Action | Expected |
|---|---|---|
| T71 | Settings tab tap | SettingsSheet opens, first fold is "Tags" + "Privacy" sections |
| T72 | Tags section | "No tags yet" if no tags; or list of tags with # + kind chip + delete X |
| T73 | Add tag via "+ #tag" | Text input appears, can save a new tag |
| T74 | Privacy "Vault mode" row | Shows "Visible" / "Hidden" current state, tappable to switch |
| T75 | Privacy "Vault PIN" row | "Not set" or "Set", tappable to open PIN dialog |
| T76 | Set Vault PIN (4 digits) | PIN saved, can unlock |
| T77 | Privacy "Recovery phrase" row | "Not generated" or "Reveal", tappable to open Recovery Phrase screen |
| T78 | Privacy "Threat model" row | "Read the threat model", tappable to open Threat Model screen |
| T79 | Theme section | Light / Dark / System segmented buttons, current selection highlighted |
| T80 | Theme: Light | Sheet background flips to cream, text darkens |
| T81 | Theme: Dark | Sheet background flips to dark slate, text lightens |
| T82 | Theme: System | Follows OS setting |
| T83 | Data section | "Export encrypted vault" + "Import encrypted vault" buttons + plain export (CSV/JSON) |
| T84 | About section | App version (1.6.3 / build 21), storage stats, data mode |
| T85 | Developer section (debug only) | "Load test data" button + "Erase all data" button (debug builds only) |

## H. Recovery Phrase

| ID | Action | Expected |
|---|---|---|
| T86 | Open Recovery Phrase | 12-word BIP39 phrase shown, each word numbered, hold-to-reveal pattern |
| T87 | Recovery Phrase FLAG_SECURE | Screenshot blocked (FLAG_SECURE) while on the screen |
| T88 | Recovery Phrase back arrow | Returns to Settings sheet, no phrase cached |

## I. Theme switching

| ID | Action | Expected |
|---|---|---|
| T89 | Switch Light → Dark | Sheet + Home + Today all flip colors in-place (no restart) |
| T90 | Dark mode on Home | Dark background, light text, indigo shield icon still visible |
| T91 | Switch System | Follows OS; if OS is in light, app is in light |

## J. Quick note bar

| ID | Action | Expected |
|---|---|---|
| T92 | Tap Quick note text | CaptureSheet opens in text mode |
| T93 | Tap Quick note camera | Camera flow (see T64-T66) |
| T94 | Tap Quick note mic | Mic flow (see T67-T69) |
| T95 | Quick note bar with fixture loaded | Bar still visible, doesn't overlap the last person row |

## K. Edge cases

| ID | Action | Expected |
|---|---|---|
| T96 | Hardware back from Home | Should exit app (no back stack) |
| T97 | Hardware back from Person Detail | Returns to Home |
| T98 | Hardware back from Settings sheet | Closes sheet, returns to previous tab |
| T99 | Rotate phone to landscape | App stays on same screen, content reflows (not tested deeply — release target is portrait) |
| T100 | Force-stop + relaunch | State preserved (fixture data + theme + onboarding skipped) |

## Test session plan

1. Run all 100 tests with the fixture loaded + dark mode (default on phone)
2. For each failure, capture: screenshot path + UI dump + repro steps
3. Triage: CRITICAL → fix now; HIGH/MED → fix in this round; LOW → defer to v1.6.4
4. Re-run all 100 after fixes; re-tag v1.6.3-final
