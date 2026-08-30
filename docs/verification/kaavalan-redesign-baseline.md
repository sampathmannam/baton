# Kaavalan Note redesign baseline

Date: 2026-08-30 (Asia/Calcutta)

## Source baseline

- Branch: `qwen/kaavalan-redesign`
- Starting commit: `20ad9083fc6c1a2d250ecb9e713103b5b8c431eb`
- Upstream: `origin/qwen/kaavalan-redesign`
- Working tree before verification: clean
- Product changes made during Stage 0: none

## Environment

- OS: Windows 11, amd64
- Java: OpenJDK 17.0.13 (`JAVA_HOME=C:\ProgramData\jdk-17\jdk-17.0.13`)
- Gradle wrapper: 8.10.2
- Android SDK: `C:\Android\Sdk`
- Verified SDK components: `platform-tools`, `platforms/android-35`, `build-tools/35.0.0`, and `ndk/27.3.13750724`
- Tracked secret-file candidates matching `local.properties`, `google-services.json`, `*.jks`, `*.keystore`, `*.p8`, or `*.pem`: none

The Android tooling emitted a non-blocking warning that its SDK XML parser supports version 3 while one installed SDK XML file uses version 4. This did not prevent either Android build gate from succeeding.

## Verification results

### Unit tests — passed after flaky-test repair

Command used for an uncached result:

```powershell
.\gradlew.bat testDebugUnitTest --rerun-tasks --no-daemon --max-workers=1 --no-parallel
```

Observed result:

- 578 tests executed
- 566 passed
- 0 failed
- 12 skipped
- 0 errors
- Gradle result: `BUILD SUCCESSFUL` in 8m 2s; 31 actionable tasks executed

Root-cause diagnosis: the original test was probabilistically invalid. It required every corresponding byte across the 44 random salt/nonce bytes to differ. Independent random byte arrays are expected to share at least one corresponding byte about 15.8% of the time (`1 - (255/256)^44`). The repaired assertion verifies that the complete 44-byte random section differs, while the existing assertion separately verifies that the full encrypted blobs differ. Production code remains unchanged; it continues to generate a fresh 32-byte salt and 12-byte nonce with `SecureRandom`.

An earlier non-rerun invocation reported success because all test tasks were `UP-TO-DATE`; that cached result is not accepted as baseline evidence. The result above is the new uncached verification.

### Debug APK assembly — passed

Command:

```powershell
.\gradlew.bat assembleDebug --no-daemon --max-workers=1 --no-parallel
```

Observed result: `BUILD SUCCESSFUL` in 5m 46s; 41 actionable tasks (12 executed, 3 from cache, 26 up-to-date).

Generated APKs:

| APK | Bytes | SHA-256 |
| --- | ---: | --- |
| `app/build/outputs/apk/debug/app-universal-debug.apk` | 94,067,095 | `DD2D2049FE1D169F8E862B61E37C27C428E023F872447BF13AA3E1F792338CC0` |
| `app/build/outputs/apk/debug/app-arm64-v8a-debug.apk` | 47,319,828 | `C36A88E7EC19230A0E09126C6F89EB932DF4E7046E081B88E5610B8333F18D07` |
| `app/build/outputs/apk/debug/app-armeabi-v7a-debug.apk` | 41,183,426 | `9776D112565ACFED7082E4FEC56A1AEBF7E0618912E672C25F4DBCCE10964758` |
| `app/build/outputs/apk/debug/app-x86_64-debug.apk` | 48,501,455 | `BB9394181E07006FF89DE554473427CB7D10D9BCD82F4353F1B9DF8B56E71D5B` |
| `app/build/outputs/apk/debug/app-x86-debug.apk` | 47,449,682 | `9CB620889B3468263ADB74136EC1B8DF8E2098A7D3B100F15DB94A8EAAC1BD68` |

### Instrumentation-test Kotlin compilation — passed

Command:

```powershell
.\gradlew.bat compileDebugAndroidTestKotlin --no-daemon --max-workers=1 --no-parallel
```

Observed result: `BUILD SUCCESSFUL` in 2m 25s; 31 actionable tasks (9 executed, 1 from cache, 21 up-to-date).

This gate compiles the currently configured Android-test source set. The existing Gradle source-exclusion workaround remains unchanged for later repair/removal as required by the implementation plan.

## Stage 0 conclusion

The branch has a green uncached unit-test baseline, can build debug APKs, and can compile its configured instrumentation tests. The only code change is the test-only repair for the probabilistic assertion; production behavior is unchanged. Stage 1 can proceed.
