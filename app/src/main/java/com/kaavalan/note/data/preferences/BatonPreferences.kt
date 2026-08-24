package com.kaavalan.note.data.preferences

// v2.0 (PM rating): backward-compat alias for the pre-kaavalan
// class name. Several files in the codebase (MainActivity,
// SettingsViewModel, DecayViewModel, ChangelogScreen, ...) still
// reference `BatonPreferences`; the actual class is now
// `KaavalanPreferences`. This typealias keeps the references
// compiling without touching every call site.
typealias BatonPreferences = KaavalanPreferences
