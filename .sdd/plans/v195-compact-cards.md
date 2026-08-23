# v1.9.5 — Compact DecayRow (drop Mark recent button, add swipe-right + long-press)

## Goal
Make the Today screen's "Quiet a while" DecayRow cards use the screen
properly. The v1.9.4 layout (3 files, versionCode 35 → 36) ships horizontal
siblings "Mark recent" + "Quiet a while" pill on the right. The Mark recent
TextButton eats ~80dp of horizontal space, which forces the days-quiet
text to ellipsize as "haven't touched in 93 d..." instead of the full
"haven't touched in 93 days".

**Result we want (drive-verified on emulator-5554):** each DecayRow card
drops to ~110dp height (was ~165dp), days-quiet shows the full text
"haven't touched in 93 days" (no ellipsis), 6+ full cards visible on the
Today screen instead of 5. Status pill stays for the glanceable state
(amber Quiet / green On track / muted brown Getting due).

## Why this is the right move
- The "Mark recent" button is a per-row action competing with the
  status pill. Both are state-signals-of-sorts (state of being
  "not recent" + the action to fix it). Combining them
  wastes horizontal space and visual attention.
- The user already has two ways to mark someone recent from the
  Person Detail screen. Adding a third (the row button) was
  excessive affordance.
- Swipe-right-to-mark-recent is a standard Material 3 gesture
  for list items with a side-effect action. The user is
  already familiar with the pattern from Mail/Keep/Maps.
- Long-press is a discoverable escape hatch for users who don't
  know about the swipe gesture.

## Concrete changes (3 files)

### 1. `app/src/main/java/com/baton/app/ui/today/decay/DecaySection.kt`

Replace the `DecayRow` composable:
- Drop the `androidx.compose.material3.TextButton(onClick = onMarkRecent, ...)`
  entirely.
- Drop the `Spacer(Modifier.width(4.dp))` between the button and pill.
- Keep the `ReachOutPill(row.status)` as the only right-side control.
- The left Column's `maxLines = 1` on days-quiet and
  `TextOverflow.Ellipsis` can be removed because the right column
  is now narrow enough to show the full text "haven't touched in
  93 days" without truncation. Verify by reading the actual
  rendered width after the change.
- Make the whole Card a `combinedClickable(onClick = onClick,
  onLongClick = onLongClick)` instead of `clickable`. The
  long-press opens the action sheet.

Add a `var showActionSheet by remember { mutableStateOf(false) }`
at the DecayRow level, and an `onLongClick: () -> Unit = { showActionSheet = true }`
parameter.

Add a ModalBottomSheet at the end of the composable, rendered
when `showActionSheet`:
- Title: "B. Ramesh Naidu" (or `row.name`)
- 2 TextButton actions: "Mark as recent" (onClick: onMarkRecent
  + showActionSheet = false) and "Cancel" (onClick: showActionSheet
  = false)
- Sheet state: `rememberModalBottomSheetState(skipPartiallyExpanded = true)`

Wrap the Card in a Box to host the sheet OR use the host-aware
pattern (sibling below the Card, gated on the showActionSheet state).

### 2. `app/src/main/java/com/baton/app/ui/today/decay/DecayRowSwipe.kt` (new file)

Or: keep all in `DecaySection.kt` for simplicity. New top-level
`BoxWithConstraints` wrapper around the Card that listens for
horizontal drag and, on drag-right past a threshold (e.g. 96dp),
fires `onMarkRecent()` with a Snackbar "Marked as recent. [Undo]".

The undo Snackbar must re-add the lastInteractionAt to its old
value (or 0 if the user already had lastInteractionAt = now).
Pull the existing `UndoController` pattern from
`DecayViewModel.markRecent` — it already supports this.

Pseudocode:
```kotlin
@Composable
private fun SwipeableDecayRow(
    row: DecayRow,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMarkRecent: () -> Unit,
) {
    val density = LocalDensity.current
    val thresholdPx = with(density) { 96.dp.toPx() }
    var offsetX by remember { mutableStateOf(0f) }
    var markedRecent by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(row.id) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (offsetX > thresholdPx && !markedRecent) {
                            markedRecent = true
                            onMarkRecent()
                        } else {
                            scope.launch { offsetX = animateTo(0f) }
                        }
                    },
                    onDrag = { _, dragAmount -> offsetX += dragAmount },
                )
            }
    ) {
        // Background layer (visible while swiping): "Mark recent" label
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.tertiaryContainer)
                .padding(start = 24.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text("Mark recent", color = MaterialTheme.colorScheme.onTertiaryContainer)
        }
        // Foreground Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(offsetX.toInt(), 0) },
        ) {
            DecayRowContent(...)
        }
    }
}
```

The Undo snackbar is wired through the existing
`DecayViewModel.markRecent` — already returns the new value
that the snackbar handler restores via `markRecent(row)` (idempotent).

### 3. `app/build.gradle.kts`

- `versionCode = 37`
- `versionName = "1.9.5"`
- No new dependencies — `detectHorizontalDragGestures` and
  `pointerInput` are in Compose Foundation which is already a dep.

## What stays the same
- Schema (v15)
- Test count baseline (533 tests, 0 failures)
- Mark recent action (same DB call)
- Status pill (ReachOutPill composable)
- All other screens (Home, Settings, PersonDetail)

## Drive-verify
- emulator-5554 (1080x2400, 420dpi, Android 14)
- Steps: install → launch → Skip onboarding (4x) → Today tab
- Expected: 6+ full DecayRow cards visible on first paint
  (was 5 in v1.9.4)
- Expected: each card's days-quiet text shows "haven't touched
  in 93 days" with no ellipsis
- Expected: status pill (amber "Quiet a while") is the only
  right-side control
- Expected: swipe-right on a card reveals "Mark recent" background
  label, fires markRecent on threshold, shows Snackbar "Marked as
  recent. [Undo]" on top
- Expected: long-press on a card opens ModalBottomSheet with
  "Mark as recent" + "Cancel"
- Save screenshot to
  `docs/play-store-screenshots/_v195-today.png`

## Tests to update / add
- `DecaySectionTest.kt` — update the snapshot of the rendered
  DecayRow tree (the new layout has fewer children, no TextButton)
- `DecayViewModelTest.kt` — no change (markRecent behavior is
  unchanged, only the UI affordance moved)
- New: `DecayRowSwipeTest.kt` — a Compose UI test that:
  - drags a card 120dp to the right
  - asserts that `onMarkRecent` was called
  - drags only 60dp and releases
  - asserts that `onMarkRecent` was NOT called
  - long-presses a card
  - asserts that the action sheet is visible

## Risk
- The swipe gesture must coexist with the LazyColumn's scroll
  gesture. `detectHorizontalDragGestures` activates only when
  the drag is dominantly horizontal, so vertical scrolling is
  unaffected in practice. If we see scroll conflicts in
  drive-verify, add a `requireHorizontal` direction filter.
- The ModalBottomSheet for long-press may collide with the
  main Today screen's bottom bar / search bar. Use the standard
  M3 sheet (no custom backdrop colour) and verify nothing else
  is shown.
- A user who taps the Mark recent TextButton in v1.9.4 will
  find no button in v1.9.5. The new affordances (swipe-right,
  long-press) need to be discoverable. A small "Swipe right or
  long-press to mark recent" hint label visible for the first
  N launches (or forever) helps. Use the existing
  "Draft nudge" pattern from PersonDetailScreen.

## Out of scope
- Migrating the same pattern to PersonRow on Home (the count
  badge there is fine, no Mark recent needed)
- Migrating to a dedicated "Manage quiet contacts" screen
  (deferred to v1.10.0)

## Success criteria
1. Today screen shows 6+ full DecayRow cards on first paint
   (emulator-5554, 1080x2400)
2. Days-quiet text shows the full "haven't touched in 93 days"
   with no ellipsis (verify with uiautomator dump)
3. Swipe-right fires markRecent with Snackbar Undo
4. Long-press opens ModalBottomSheet with "Mark as recent"
5. Tap card opens Person Detail (unchanged)
6. testDebugUnitTest 533+8 = 541 tests pass
7. APK signs with the production keystore
   (CN=Baton, OU=Engineering, O=Baton, L=City, ST=State, C=IN,
   SHA-256 4caec153…1b4b0d9)
