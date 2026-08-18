## v1.6.0.1 — LLM graceful fallback + Room FTS migration fix

### What's in this release

- **LLM graceful fallback**: When the build is missing the on-device LLM
  JNI library (the v1.6.0 release APK excludes `vendorLlamaCpp` for fast
  CI builds), the capture sheet now shows a clear **"AI extraction off"**
  card with a "Save as plain note" button. The previous build silently
  returned "No instruction found. Try rephrasing." -- misleading because
  the user's text was never even read.

- **Room FTS4 migration fix**: The v1.5.7 -> v1.6.0 upgrade was a brick
  on real devices. The migration wrote the FTS4 table with the wrong
  column order and backticks around the tokenizer name. Fresh installs
  were fine (Room generates the schema from the `@Fts4` entity), so CI
  never caught the regression. Fixed by aligning the migration SQL with
  what Room generates: alphabetical column order (`capturedAt, personId,
  title, rawText`) and the unquoted `tokenize=porter` option.

- **Test surface**: 490 / 0 failed / 7 skipped (was 479; 11 new tests).
  The 8 v1.6.0 release failures are all addressed -- the 3
  `RecoveryPhraseHoldToRevealTest` cases now read `strings.xml` directly
  (the `ApplicationProvider` path required `testOptions.unitTests
  .isIncludeAndroidResources = true`, which breaks 270+ other
  Robolectric tests by enabling the AndroidKeyStore provider), and
  the 5 `V156QaTest` cases use a `TestLlamaBridge` so the QA scenarios
  don't accidentally short-circuit on `mockk(relaxed = true)`'s `false`
  default.

- **On-device verified** on `ZD2232FCR5` (Motorola signature, Android 17):
  capture sheet shows the new "AI extraction off" card with the
  honest copy. Save-as-plain still works. Migration v10 -> v13 runs
  cleanly on a v1.5.7 install.

### What's NOT in this release (parked per user direction)

- **On-device LLM** (Tier 4). The v1.6.0 release build excludes
  `vendorLlamaCpp` and `vendorWhisperCpp` (per the v1.6.0 ship plan's
  "Tier 4 un-park in v2.0" rule). The LLM library is built and bundled
  in v2.0 -- this release only fixes the UX so the user knows the LLM
  is intentionally off in this build.
- The rest of the v1.6.0 ship (Tier 0/1 features, design rules,
  DPDPA, visual identity) is in the integration commit chain
  `f051524 -> 4482517`.

### SHA-256

```
58AE372734BAF3201F85BCA97FEE4F5C3DFE5575924D0F297BB3E942F9493147
```

### Test summary

| Suite | Count | Status |
|-------|-------|--------|
| Pre-integration baseline (v1.5.7) | 307 + 7 skipped | GREEN |
| Tier 0 (cleanup) | 23 new | GREEN |
| Tier 1 (survival) | 68 new | GREEN |
| Tier 2 (moat) | 42 new | GREEN |
| Tier 3 (privacy) | 43 new | GREEN |
| v1.6.0 design rules | 5 new | GREEN |
| v1.6.0 recovery-phrase hold-to-reveal | 3 new | GREEN |
| v1.6.0.1 LLM graceful fallback | 2 new | GREEN |
| **v1.6.0.1 integration HEAD** | **490 + 7 skipped** | **GREEN** |
