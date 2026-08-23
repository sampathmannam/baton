# Real-device test plan — Baton v1.9.6 on Motorola signature (Android 17, API 37)

> Date: 2026-08-23
> Device: Motorola signature (ZD2232FCR5)
> Build: app-arm64-v8a-debug.apk (45 MB), built from origin/m0/skeleton-v1.7.0
> Test mode: real Supabase not configured (using `https://placeholder.supabase.co` in local.properties)
> Tester: Mavis (AI assistant) + Sampath (real-device owner)

## Test matrix

| # | Scenario | Goal | Verifies |
|---|---|---|---|
| 1 | Cold launch | App starts without crash, shows onboarding | App stability on Android 17 |
| 2 | Skip onboarding → Home | Main screen renders | "3 tabs", "1-tap capture" rules |
| 3 | Add Person | Form opens, accepts input, saves locally | Capture without network |
| 4 | Quick note text capture | Type instruction, save, verify lands in person / today | "Capture < 5s" rule |
| 5 | Person detail | Drill-down works, shows instruction | "Drill-down only" rule |
| 6 | Today tab | Renders, swipe-right gesture on quiet card | v1.9.5 swipe feature |
| 7 | Settings → Vault mode toggle | Switch to Hidden, set PIN | "Argon2id + AES-GCM" privacy |
| 8 | Theme switcher | Light / Dark / System | "Energy-aware" plumbing exists |
| 9 | Network state recovery | Toggle airplane mode, see if app recovers | "Forgive inconsistency" rule |
| 10 | Battery / heat / stability | Let it run, monitor for ANR / FATAL | Production stability |

## Findings log

(filled in as tests run)
