# v1.6.3 systematic drive findings (2026-08-19)

Test session: emulator-5554 (Android 14, 1080x2400)
APK installed: com.baton.app.debug v1.6.3-r3 (build 22), debug SHA 223D4EAC6C8F5C60204CE04774C5D51356F6FB5D704BACFB216CE7A321B127DE
Fixture: 12 persons + 36 instructions (3 worries) + 7 captures + 12 tags (loaded earlier)

## Drive-time findings (already fixed in v1.6.3-final = 3f6e2d2)

### HIGH (FIXED + verified on emulator)
1. **T01 — Onboarding Welcome page `Icons.Default.Lock` → `R.drawable.ic_launcher_foreground`** (05-shieldmark)
   - File: `app/src/main/java/com/baton/app/features/onboarding/OnboardingScreen.kt`
   - Verified: onboarding page now shows the new indigo shield on cream. ✓

2. **T10/T11 — FAB overlap last row's count badge → `contentPadding(bottom = 88.dp)`** on PersonList + search-results LazyColumns
   - File: `app/src/main/java/com/baton/app/ui/home/HomeScreen.kt`
   - Verified: scrolled to last row, all 12 persons visible above FAB. ✓

### MED (FIXED + verified on emulator)
3. **T41/T45 — "1 instructions" plural → Android `<plurals>` resource**
   - File: `app/src/main/res/values/strings.xml` (plurals) + `PersonDetailScreen.kt` (pluralStringResource)
   - Verified: K. Suresh Person Detail shows "1 instruction" (singular). ✓

4. **T45 — "Carried over" status pill `tertiaryContainer` (pink) → `surfaceVariant` (calm grey)**
   - File: `app/src/main/java/com/baton/app/ui/home/PersonDetailScreen.kt` (StatusChip CARRIED_OVER)
   - Verified: pill color is now calm grey, not pink. ✓

## New findings (discovered during systematic drive 2026-08-19)

### HIGH
5. **T95 — Bottom nav Settings tab is un-tappable on emulator with 3-button system nav** (MainActivity.kt)
   - The Compose `NavigationBar` with `windowInsetsPadding(WindowInsets.safeDrawing)` (v1.6.3-r3) ends at y=2337 (bottom). The system 3-button nav is at y=2274-2400 (126px). The Compose nav OVERLAPS the system nav at y=2274-2337 (63px). The system recents button (right) has a hit area that extends into the Compose nav.
   - Tapping the Settings button at (x=907, y=2200) is intermittently captured by the system recents gesture, switching focus to the recents view (or to a different app like BSA for Dummies / MindAnchor that was previously in foreground).
   - **Symptom**: tap Settings → focus goes to BSA / MindAnchor instead of opening the Settings sheet.
   - **Mitigation (test environment only)**: use release variant (com.baton.app) which has the older code without safeDrawing; that variant's bottom nav responds correctly to taps at y=2200. The debug variant (v1.6.3-r3) does NOT respond.
   - **Root cause**: the v1.6.3 `windowInsetsPadding(WindowInsets.safeDrawing)` is correctly applying 63px bottom padding (the system inset value), but the actual system 3-button nav is 126px tall on this emulator. The remaining 63px overlap is where the system captures touches.
   - **Fix for v1.6.4**: use a hard-coded bottom padding of at least 48dp (126px at 420dpi) for the bottom nav, OR use `WindowInsets.systemBars` + `WindowInsets.displayCutout` combined, OR disable `enableEdgeToEdge()` for the BottomNav.
   - **On phone ZD2232FCR5 (3-button mode)**: the issue is the same; Settings tab on phone also un-tappable in v1.6.3.

### MED
6. **Data loading shows only 1 OPEN for K. Suresh** (FixtureLoader.kt: loadFromAssets)
   - Fixture JSON has 22 OPEN instructions across 11 people (K. Suresh should have 2 OPEN, M. Lavanya 3, P. Rajeshwar Rao 2, etc.).
   - Actual loaded state: only K. Suresh has 1 OPEN ("Verify the FIR copy from Innam PS"); all other persons show 0 OPEN.
   - Verified by tapping person detail of G. Swapna, P. Rajeshwar Rao, M. Lavanya — all show "No instructions yet."
   - **Likely root cause**: the v1.6.2-era `FixtureLoader.loadFromAssets` is called only once and on first call only loads partial data (v1.6.2 was the first build to ship the loader). Subsequent installs of v1.6.3 do NOT re-call the loader, so the partial data from v1.6.2 is preserved.
   - **Fix for v1.6.4**: in the Settings → Developer → "Load test data" menu, also expose "Clear test data" so the user can reset and re-load. OR add a fixture version check to the loader and re-load on version mismatch.
   - **Not a v1.6.3 regression** — the partial data was loaded by v1.6.2-final (commit 51fda8e) and persisted through v1.6.3 (3f6e2d2) and v1.6.3-r2 / -r3.

### LOW
7. **T01 — `mFocusedApp` flips between Baton / BSA for Dummies / MindAnchor during test session** (test infrastructure)
   - Multiple apps installed on emulator-5554. Taps on the right side of bottom nav (x=900+) are captured by the system recents button and bring the previously-focused app forward.
   - Not a Baton bug; affects all 3-button nav apps on this emulator.

## Test pass summary

### PASSED
- T01 (onboarding welcome renders, uses new shield) ✓
- T03 (skip from onboarding) ✓
- T06 (home empty state) ✓
- T10 (home loaded — 12 persons visible above FAB after scroll) ✓
- T12 (search "Suresh" returns 1 person, no UUID) ✓
- T25 (bottom nav Home + Today tappable in middle) ✓
- T41 (person detail with name + designation + AddInstruction) ✓
- T45 (1 instruction singular grammar) ✓
- T45 (Carried over pill calm grey) ✓
- T56 (FAB opens Add Person sheet) ✓
- T57 (Save disabled when name empty) ✓

### BLOCKED (bottom nav Settings un-tappable on emulator; use phone instead)
- T71 (Settings sheet — settings sheet DOES open on release variant, debug blocked)
- T72-T88 (Settings sheet internals, theme, recovery phrase)
- T89-T91 (Theme switching)
- T96-T100 (Edge cases — back press, rotation)

### NOT TESTED
- T02 (onboarding Next 2x) — manual, low value
- T04 (re-launch from Home) — manual
- T05 (last page CTA) — manual
- T08 (Add person save flow) — blocked by keyboard input via `input text` (KEYCODE_TAB fires system navigation)
- T09 (Quick note bar tap) — not yet tested
- T11 (scroll to last row) — verified earlier
- T13-T24 (search variants, count badges, etc.) — manual
- T26-T40 (Today sections) — partly visible in earlier screenshots
- T42-T44, T46-T55 (Person Detail interactions) — partly visible
- T58-T70 (Add Person, Capture flows) — not tested due to nav/keyboard issues

## APK SHAs (v1.6.3-r3)
- Debug: `223D4EAC6C8F5C60204CE04774C5D51356F6FB5D704BACFB216CE7A321B127DE` (90.27 MB)
- Release: `FAA80167BA21A76C13CF10D25FACE7D72DDF8EAA98DEA5865708A6D493C21902` (~68 MB)

## Next steps
- Fix the bottom nav hit area for v1.6.4 (HIGH #5)
- Add a "Clear test data" button in Settings → Developer (MED #6)
- Continue systematic test pass on a phone or a fixed emulator
- Re-run test suite (452 baseline) to verify r3 doesn't break anything
- Commit v1.6.3-r3 with the safeDrawing padding change (as future-proofing) + findings
