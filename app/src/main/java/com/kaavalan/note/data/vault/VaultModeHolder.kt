package com.kaavalan.note.data.vault

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * v2.0 T3-1 (deniable vault): a process-singleton holder for the
 * currently-active vault mode. The [VaultMode] value is a UI-level
 * filter only — switching modes does NOT delete or re-encrypt any
 * data. The data layer just observes
 * `PersonDao.observeAllInMode(mode)` instead of `observeAll()`.
 *
 * **Threat-model note (mandatory read for code review).** This is
 * a *behavioural* deniability model. The Room database file on
 * disk still contains BOTH a `vaultMode` column and the rows it
 * describes; a forensic adversary can see the hidden rows in the
 * raw SQLite file. The settings copy in Settings -> Threat model
 * spells this out to the user.
 *
 * The default mode on first launch (and after [reset] is called
 * from the sign-out / "Erase all data" path) is
 * [VaultMode.Visible]. No persistence — the user opts back in
 * each session.
 */
@Singleton
class VaultModeHolder @Inject constructor() {

    private val _mode = MutableStateFlow(VaultMode.Visible)
    val mode: StateFlow<VaultMode> = _mode.asStateFlow()

    /**
     * Switch the active mode. The caller is responsible for the
     * auth gate (PIN / biometric) per the spec — this method is
     * the unconditional write. Pass [VaultMode.Visible] to show
     * the default list; [VaultMode.Hidden] to filter to hidden
     * rows.
     */
    fun setMode(mode: VaultMode) {
        _mode.value = mode
    }

    /**
     * Reset to visible. Called by the "Erase all data" path in
     * [com.kaavalan.note.data.local.AppInitializer.runOnSignOut]
     * via Hilt; the singleton then re-emits Visible to every
     * observer so a stale `Hidden` doesn't leak across the
     * sign-out.
     */
    fun reset() {
        _mode.value = VaultMode.Visible
    }

    /**
     * The string form used by the SQL `WHERE vaultMode = :mode`
     * filter. The DAO query parameter is a String (Room does
     * not model enums); this helper keeps the two in sync.
     */
    val modeString: String
        get() = _mode.value.storageKey

    /** The other mode — used by the UI affordance that says
     *  "X items in vault" so the user can switch to it. */
    fun otherMode(): VaultMode = when (_mode.value) {
        VaultMode.Visible -> VaultMode.Hidden
        VaultMode.Hidden -> VaultMode.Visible
    }
}

/**
 * v2.0 T3-1: the two vault modes. Default is
 * [Visible]. The companion's [storageKey] is the canonical SQL
 * string the DAO uses in the `WHERE vaultMode = :mode` filter —
 * keeping it here avoids the magic-string footgun in the DAO
 * query and in any test that asserts on the value.
 */
enum class VaultMode(val storageKey: String) {
    Visible("visible"),
    Hidden("hidden"),
}
