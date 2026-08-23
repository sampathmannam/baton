# Baton v1.6.8 — UI/UX Overhaul Plan

**Status:** In progress
**Branch:** `m0/skeleton-v1.6.6` (amended forward to v1.6.8)
**Target:** Find and fix every UI/UX issue — don't leave any stone unturned.

## Scope

User-facing strings, accessibility, dark-mode adaptation, and resource
type correctness. v1.6.6 fixed the P0 crash + multi-count plurals.
v1.6.7 fixed the 5 Compose deprecations. v1.6.7-r2 fixed the 5 hardcoded
TalkBack a11y strings. v1.6.8 closes the remaining UI/UX debt.

## Issues Found

### 1. Hardcoded user-facing strings (English-only) — 23 sites

These read English to a non-English-locale user. All need a stringResource
migration.

**TodayScreen.kt:**
- L184 `SectionHeader("Waiting on others")` -> R.string.today_section_waiting
- L190 `SectionHeader("Carried over")` -> R.string.today_section_carried_over
- L245 `text = "Nothing on your plate."` -> R.string.today_empty_title
- L250 `text = "When instructions come in, they'll show up here."` -> R.string.today_empty_subtitle
- L373 `text = "Due: ${formatTime(...)}"` -> R.string.today_due_at (formatted)
- L379 `text = "Captured: ${formatTime(...)}"` -> R.string.today_captured_at (formatted)
- L403 `Text("Mark done")` -> R.string.action_mark_done
- L460 `Text("Evening review", ...)` -> R.string.today_evening_review
- L483 `text = "Tap outside to dismiss. Tomorrow's brief picks up from here."` -> R.string.today_review_dismiss_hint

**PersonDetailScreen.kt:**
- L224 `text = "New instruction for $personName"` -> R.string.person_new_instruction_title (formatted)
- L228 `text = "Capture what you want $personName to do. The note will be attributed to them and show up on their timeline."` -> R.string.person_new_instruction_body (formatted)
- L238 `label = { Text("Note") }` -> R.string.person_note_label
- L481 `text = "Done " + formatCapturedAt(...)` -> R.string.person_done_at (formatted)
- L488 `text = "Dropped: ${instruction.droppedReason}"` -> R.string.person_dropped_reason (formatted)
- L525 `TextButton { Text("Draft nudge") }` -> R.string.action_draft_nudge
- L527 `TextButton { Text("Mark done") }` -> R.string.action_mark_done
- L551 `title = { Text("Drop instruction") }` -> R.string.person_drop_title
- L559 `label = { Text("Reason (optional)") }` -> R.string.person_drop_reason_label

**NudgeSheet.kt:**
- L81 `Text("Draft nudge", ...)` -> R.string.action_draft_nudge
- L83 `text = "Edit before sending. ..."` -> R.string.nudge_edit_before_sending
- L133 `label = { Text("Message") }` -> R.string.nudge_message_label
- L140 `"Baton nudge"` (clipboard label) -> R.string.nudge_clipboard_label

**PersonLinksRow.kt:**
- L141 `text = "Add another person first."` -> R.string.links_add_person_first

**ImportantDatesRow.kt:**
- L152 `text = "Date: $date"` -> R.string.important_date_label (formatted)
- L171 `text = { Text("In a week") }` -> R.string.important_date_in_a_week
- L182 `text = "Will save as: $resolvedLabel"` -> R.string.important_date_will_save_as (formatted)

**SettingsSheet.kt:**
- L882 `text = "Failed to sync after multiple retries. Retry to put them back in the queue."` -> R.string.settings_sync_error_retries
- L914 `Text("Tags")` -> R.string.settings_section_tags
- L937 `placeholder = { Text("new-tag") }` -> R.string.settings_tags_placeholder
- L955 `text = "No tags yet. They'll show up here as you create instructions."` -> R.string.settings_tags_empty

**AuthScreen.kt (vault mode AUTH is unused post-vault-pivot, but localise anyway):**
- L63 `Text("Kaavalan note", ...)` -> R.string.app_name (already exists)
- L124 `label = { Text("Email") }` -> R.string.auth_email_label
- L171 `label = { Text("Password") }` -> R.string.auth_password_label
- L255 `Text("Resend code")` -> R.string.auth_resend_code
- L258 `Text("Use password instead")` -> R.string.auth_use_password
- L187, 190 (sign in / create account) -> R.string.auth_sign_in / R.string.auth_create_account
- L66 "Welcome back" -> R.string.auth_welcome_back

**TagPicker.kt:**
- L67 `text = "Tags"` -> R.string.tag_picker_title

### 2. Dark-mode-non-adaptive colors — 4 sites

Hardcoded Color(0xFFXXXXXX) values that look correct in light mode but
are wrong in dark mode (too bright, poor contrast, no theme awareness).

- `TagPicker.kt:163-169` `colorForKind` returns 3 hardcoded colors
  (`0xFF6B7AA1` cool blue, `0xFFB58A4D` warm tertiary, `0xFF6F6F6F`
  neutral grey). In dark mode these are too bright against the
  `surfaceVariant` (0xFF2F2A23). Fix: add `BatonColors.KindBlueLight/
  Dark` and `KindWarmLight/Dark` and `KindNeutralLight/Dark` pairs;
  `colorForKind` reads from `MaterialTheme` (or accepts
  `isSystemInDarkTheme` and selects the pair).
- `TagPicker.kt:173-180` `parseHex` fallback `Color(0xFF6F6F6F)` is
  the same. Not theme-aware but lower-impact (fallback only).
- `HomeScreen.kt:689` stale-person dot `Color(0xFFD9A05B)`. Should
  use `BatonColors.Quiet` (which already adapts via Theme.kt) or
  add `BatonColors.StaleIndicator` theme pair.

### 3. Plural resource type — 2 entries

`<plurals>` with identical "one"/"other" items are wrong type
(user-visible text is fine). Fix: change `<plurals>` to `<string>`.

- `count_carried_over` (both items = "%1$d carried over")
- `count_sensitive` (both items = "%1$d sensitive")

Update call sites in `TodaysWinCard.kt` to use
`stringResource(R.string.count_carried_over)` /
`R.string.count_sensitive` instead of `pluralStringResource`.

### 4. Pre-existing UI/UX debt to NOT touch (scope creep)

Per user "complete overhaul of UI/UX issues" but excluding features:
- ~80 other hardcoded colors in non-user-facing files
  (gradient definitions, illustration strokes) - out of scope
- Layout/spacing fine-tuning - covered by current design
- New features (search bar, brief timing, etc.) - separate task

## Build / Verify

1. Add all new strings to `app/src/values/strings.xml`
2. Update each Kotlin call site to use `stringResource(...)`
3. Update `TagPicker.colorForKind` to read from a theme-aware source
4. Update `HomeScreen` stale dot to use a theme token
5. Convert the 2 `<plurals>` to `<string>`; update call sites
6. Build `assembleDebug`
7. Install on ZD2232FCR5, drive-verify
8. Bump versionCode 24 -> 25, versionName 1.6.7-r2 -> 1.6.8
9. Commit + tag + push

## Acceptance

- `grep -rn 'Text("' app/src/main/java` returns only stringResource-wrapped
  strings (no raw user-facing English literals in the UI surface)
- Dark mode drive-test shows tag chips with the correct tonal
  contrast against the dark surface
- Stale-person dot visible against the dark surface (current
  hardcoded `0xFFD9A05B` was too dark on dark)
- build_*.log shows BUILD SUCCESSFUL with 1 expected warning
- Settings > About reads "1.6.8 (build 25)"
