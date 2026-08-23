# v1.9.6 — Undo snackbar name + DecayRow hint + Today's win copy

## Goal
Three drive-verify bugs caught in v1.9.5, all small, all in the user's
visible path on a first run. Total ~80 lines across 4 files, ~10
tests. Should land in <2 hours of worker time and recover 8.5+/10.

## Drive-verify evidence (the user-caught bugs)

1. **Snackbar UUID bug** — swipe-right on B. Ramesh Naidu shows
   **"Mark recent 96ldae"** instead of "Mark recent B. Ramesh Naidu".
   `MainActivity.kt:285` uses `action.id.take(6)` (first 6 chars of
   the contact's UUID). Visible to the user on the very first swipe.
   All 4 `UndoableAction` variants have the same shape; the bug
   applies to all of them.

2. **"Tap the note bar" copy bug** — `todays_win_summary_zero` in
   `strings.xml:373` says "No captures today yet. Tap the note bar
   to start." The note bar lives on the **Home** tab, not on the
   Today tab. A user landing on Today with no captures has no
   "note bar" to tap here. The empty state points to a UI element
   on a different screen.

3. **No discoverability for the new gestures** — v1.9.5 added
   swipe-right and long-press on DecayRow cards. There is no on-
   screen hint anywhere. A first-time user has zero way to know
   these exist. `strings.xml` has no `swipe`/`gesture`/`long-press`
   string. The `version: N` SharedPreferences pattern used by the
   `reseedIfStale()` re-render in Home is the right shape for a
   one-time discoverability hint.

## Fixes (specific)

### Fix 1 — Snackbar UUID bug

**File: `app/src/main/java/com/baton/app/data/undo/UndoableAction.kt`**

Add a `displayName` property to the sealed interface with concrete
overrides per case:

```kotlin
sealed interface UndoableAction {
    val id: String
    val label: String
    val displayName: String   // <-- new

    data class DeletePerson(
        override val id: String,
        val name: String,
        val row: ...,
    ) : UndoableAction {
        override val label: String get() = "Person"
        override val displayName: String get() = name
    }

    data class DeleteInstruction(
        override val id: String,
        val title: String,
        val row: ...,
    ) : UndoableAction {
        override val label: String get() = "Instruction"
        override val displayName: String get() = title
    }

    data class DeleteCapture(
        override val id: String,
        val preview: String,
        val row: ...,
    ) : UndoableAction {
        override val label: String get() = "Capture"
        override val displayName: String get() = preview
    }

    data class MarkPersonRecent(
        override val id: String,
        val name: String,
        val previousLastInteractionAt: Long?,
        val previousUpdatedAt: String,
    ) : UndoableAction {
        override val label: String get() = "Mark recent"
        override val displayName: String get() = name
    }
}
```

**File: `app/src/main/java/com/baton/app/MainActivity.kt:285`**

Change:
```kotlin
message = "${action.label} ${action.id.take(6)}",
```
to:
```kotlin
message = "${action.label} ${action.displayName}",
```

Add a small comment explaining why: "the snackbar message must
show the human-readable name, not a UUID fragment".

### Fix 2 — Today's win copy

**File: `app/src/main/res/values/strings.xml:373`**

Change:
```xml
<string name="todays_win_summary_zero">No captures today yet. Tap the note bar to start.</string>
```
to:
```xml
<string name="todays_win_summary_zero">No captures today yet. Add one from the Home tab.</string>
```

Rationale: the Today's win card is on the Today tab. It cannot
itself contain a "note bar" (the NoteBar lives on the Home tab).
The copy should direct the user to the right screen. The new copy
"Add one from the Home tab" is honest about where the action is.

### Fix 3 — DecayRow gesture discoverability hint

**File: `app/src/main/java/com/baton/app/data/preferences/BatonPreferences.kt`**

Add a new key:
```kotlin
val decayGestureHintShown = booleanPreferencesKey("decay_gesture_hint_shown_v1")
```

**File: `app/src/main/res/values/strings.xml`**

Add a new string:
```xml
<string name="decay_gesture_hint">Swipe right or long-press a card to mark someone as recent.</string>
```

**File: `app/src/main/java/com/baton/app/ui/today/decay/DecaySection.kt`**

Render a one-time `AssistChip` or small `Text` hint above the
filter chips when:
- the section is non-empty
- `state.rows` count >= 3 (so the hint is worth showing)
- `BatonPreferences.decayGestureHintShown == false`

When dismissed (tap the chip's close icon OR after the user marks
a row recent), set the preference to `true` so it never shows
again. Implementation: pass a `LocalContext` (or hilt-injected
`BatonPreferences` via the DecayViewModel) and a `remember`
boolean for the local "dismissed this session" state.

The simplest shape:
- Wrap the existing section in a Column.
- At the top, if the hint should show, render an `AssistChip`
  with the hint text and a small "Got it" close affordance.
- On tap, set the pref to `true` and `state = false` to hide.

The hint must:
- be subtle (low-arousal, calm, same palette)
- not be sticky / not block the list
- not appear on second run

**File: `app/src/main/java/com/baton/app/ui/today/decay/DecayViewModel.kt`**

If a `BatonPreferences` injection is available, add a
`gestureHintShown: StateFlow<Boolean>` collected from prefs, and
a `dismissGestureHint()` that sets the pref to `true`. This keeps
the UI stateless. If injection is not available, fall back to
`LocalContext.current` reading the DataStore directly — either is
acceptable.

### Tests

**File: `app/src/test/java/com/baton/app/data/undo/UndoableActionTest.kt`** (new)

- `displayName for DeletePerson returns name`
- `displayName for DeleteInstruction returns title`
- `displayName for DeleteCapture returns preview`
- `displayName for MarkPersonRecent returns name`

**File: `app/src/test/java/com/baton/app/ui/today/decay/DecaySectionHintTest.kt`** (new)

- hint shown when rows >= 3 and pref = false
- hint hidden when pref = true
- tapping hint dispatches dismiss + sets pref

(If Compose UI testing is too heavy, the logic can be moved into
the ViewModel as `shouldShowHint(rows, prefShown)` and unit-tested
there.)

### Bump

- `app/build.gradle.kts:248-249`:
  - `versionCode = 37` → `versionCode = 38`
  - `versionName = "1.9.5"` → `versionName = "1.9.6"`

## Out of scope (deferred)

These stay in the deferred list for v1.9.7+:
- "Compact redistribute chip" (recovers 6th card in DecaySection)
- TalkBack verification of swipe + long-press
- Real device screenshots for Play Store
- AGENTS.md at repo root

## Acceptance criteria

- [ ] All 4 `UndoableAction` types expose a non-empty `displayName`
- [ ] Swipe-right on any DecayRow shows snackbar "Mark recent <full person name>"
- [ ] Same fix applies to DeletePerson / DeleteInstruction / DeleteCapture
- [ ] Today's win empty state no longer says "Tap the note bar"
- [ ] DecayRow hint appears on first run when >= 3 quiet contacts, dismisses on tap, never appears again
- [ ] `versionCode = 38`, `versionName = "1.9.6"` in `app/build.gradle.kts`
- [ ] All existing 541 unit tests still pass + 4-6 new tests pass
- [ ] `gradlew :app:assembleRelease` produces APK
- [ ] Phone install + drive-verify: swipe shows person name, hint shows on first launch, copy says "Add one from the Home tab"

## Subagent workflow

1. **Worker** (this subagent) — implements all 3 fixes + tests + version bump + runs unit tests + builds APK.
2. **Verifier** (separate, read-only) — re-reads diff + runs unit tests, reports critical/important findings.
3. **Me** — re-runs build, installs on phone ZD2232FCR5, drive-verifies all 3 fixes, publishes tag + release.

## Why this is "v1.9.6 polish" not "v2.0"

v2.0 is reserved for the public-release cut (Play Store assets, full
a11y pass, recovery phrase UX hardening, etc.). v1.9.6 closes the
remaining user-visible gaps from the v1.9.x polish series.
